package com.orgista.openqr.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Size
import com.orgista.openqr.R
import com.orgista.openqr.logging.AppLogger
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

class QuestCameraSession(
    context: Context,
    private val onQrDetected: (String) -> Unit,
    private val onStatusChanged: (Int) -> Unit,
    private val onPreviewFrame: (Bitmap) -> Unit
) {
    private val appContext = context.applicationContext
    private val cameraManager = appContext.getSystemService(CameraManager::class.java)
    private val decoder = QrCodeDecoder()
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    // Camera2 keeps dispatching close callbacks (device/session onClosed) after
    // release() shuts the executor down; dropping those is harmless, while a
    // RejectedExecutionException thrown on a framework thread would kill the app.
    private val camera2Executor = Executor { task ->
        try {
            cameraExecutor.execute(task)
        } catch (_: RejectedExecutionException) {
            AppLogger.debug("Dropped Camera2 callback after release")
        }
    }
    private val stateLock = Any()
    private val decodeEnabled = AtomicBoolean(false)
    private val scanInProgress = AtomicBoolean(false)
    private val frameInFlight = AtomicBoolean(false)
    private val previewFrameInFlight = AtomicBoolean(false)

    @Volatile
    private var backgroundThread: HandlerThread? = null
    @Volatile
    private var backgroundHandler: Handler? = null
    @Volatile
    private var imageReader: ImageReader? = null
    @Volatile
    private var cameraDevice: CameraDevice? = null
    @Volatile
    private var captureSession: CameraCaptureSession? = null
    @Volatile
    private var lastPreviewFrameAtMs: Long = 0L
    @Volatile
    private var activeSensorOrientation: Int = 0
    private var sessionGeneration = 0L
    private var cameraOpening = false
    private var pendingCameraOpens = 0
    private var released = false
    private var cameraExecutorShutdown = false

    fun resumeScanning() {
        AppLogger.debug("Scanner resumed")
        decodeEnabled.set(true)
        scanInProgress.set(false)
    }

    fun pauseScanning() {
        AppLogger.debug("Scanner paused")
        decodeEnabled.set(false)
        scanInProgress.set(false)
    }

    // Quest passthrough when the Meta camera metadata exists, otherwise the
    // device's ordinary back camera — the rest of the pipeline is plain
    // Camera2 and doesn't care which one feeds it.
    fun hasUsableCamera(): Boolean = findCameraConfig() != null

    private fun findCameraConfig(): CameraConfig? =
        findPassthroughConfig() ?: findStandardConfig()

    @SuppressLint("MissingPermission")
    fun start() {
        val generation = beginSession() ?: return

        val config = findCameraConfig()
        if (config == null) {
            finishOpening(generation)
            AppLogger.warn("No usable Camera2 configuration found")
            dispatchStatus(R.string.status_unsupported_device)
            return
        }

        val analysisSize = chooseAnalysisSize(config.analysisOutputSizes) ?: run {
            finishOpening(generation)
            AppLogger.error("Unable to select a passthrough camera output size")
            dispatchStatus(R.string.status_camera_unavailable)
            return
        }
        val handler = startBackgroundThread(generation) ?: return
        AppLogger.info(
            "Opening camera ${config.cameraId} analysis=${analysisSize.width}x${analysisSize.height} " +
                "orientation=${config.sensorOrientation}"
        )

        val reader = ImageReader.newInstance(analysisSize.width, analysisSize.height, ImageFormat.YUV_420_888, 2).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                if (!frameInFlight.compareAndSet(false, true)) {
                    image.close()
                    return@setOnImageAvailableListener
                }

                try {
                    cameraExecutor.execute {
                        try {
                            image.use { latest ->
                                if (!isCurrentSession(generation)) {
                                    return@use
                                }
                                maybeDispatchPreviewFrame(latest)
                                if (!decodeEnabled.get()) {
                                    return@use
                                }
                                val result = decoder.decode(latest)
                                if (result != null && scanInProgress.compareAndSet(false, true)) {
                                    AppLogger.info("QR decode succeeded")
                                    onQrDetected(result.text)
                                }
                            }
                        } catch (t: Throwable) {
                            AppLogger.error("QR decode pipeline failed", t)
                        } finally {
                            frameInFlight.set(false)
                        }
                    }
                } catch (_: RejectedExecutionException) {
                    image.close()
                    frameInFlight.set(false)
                }
            }, handler)
        }

        val readerInstalled = synchronized(stateLock) {
            if (!isCurrentSessionLocked(generation)) {
                false
            } else {
                imageReader = reader
                activeSensorOrientation = config.sensorOrientation
                true
            }
        }
        if (!readerInstalled) {
            reader.close()
            return
        }

        val openResolved = AtomicBoolean(false)
        if (!registerCameraOpen(generation)) {
            return
        }

        try {
            cameraManager.openCamera(config.cameraId, camera2Executor, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        val accepted = synchronized(stateLock) {
                            if (!isCurrentSessionLocked(generation)) {
                                false
                            } else {
                                cameraOpening = false
                                cameraDevice = camera
                                true
                            }
                        }
                        if (!accepted) {
                            camera.close()
                            return
                        }

                        AppLogger.info("Passthrough camera opened")
                        createSession(camera, reader.surface, handler, generation)
                    } finally {
                        resolveCameraOpen(openResolved)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    try {
                        AppLogger.warn("Passthrough camera disconnected")
                        camera.close()
                        if (stop(generation)) {
                            dispatchStatus(R.string.status_camera_unavailable)
                        }
                    } finally {
                        resolveCameraOpen(openResolved)
                    }
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    try {
                        AppLogger.error("Passthrough camera error code=$error")
                        camera.close()
                        if (stop(generation)) {
                            dispatchStatus(R.string.status_camera_unavailable)
                        }
                    } finally {
                        resolveCameraOpen(openResolved)
                    }
                }
            })
        } catch (e: CameraAccessException) {
            handleCameraOpenFailure(generation, openResolved, e)
        } catch (e: IllegalArgumentException) {
            handleCameraOpenFailure(generation, openResolved, e)
        } catch (e: SecurityException) {
            handleCameraOpenFailure(generation, openResolved, e)
        }
    }

    fun stop() {
        stopInternal(null)
    }

    fun release() {
        synchronized(stateLock) {
            if (released) {
                return
            }
            released = true
        }
        stop()
        maybeShutdownCameraExecutor()
    }

    private fun stop(generation: Long): Boolean = stopInternal(generation)

    private fun stopInternal(expectedGeneration: Long?): Boolean {
        AppLogger.debug("Stopping scanner session")
        var sessionToClose: CameraCaptureSession? = null
        var cameraToClose: CameraDevice? = null
        var readerToClose: ImageReader? = null
        var threadToStop: HandlerThread? = null
        synchronized(stateLock) {
            if (expectedGeneration != null && sessionGeneration != expectedGeneration) {
                return false
            }
            sessionGeneration += 1
            cameraOpening = false
            sessionToClose = captureSession
            captureSession = null
            cameraToClose = cameraDevice
            cameraDevice = null
            readerToClose = imageReader
            imageReader = null
            threadToStop = backgroundThread
            backgroundThread = null
            backgroundHandler = null
        }
        sessionToClose?.close()
        cameraToClose?.close()
        readerToClose?.close()
        decodeEnabled.set(false)
        scanInProgress.set(false)
        frameInFlight.set(false)
        previewFrameInFlight.set(false)
        lastPreviewFrameAtMs = 0L
        stopBackgroundThread(threadToStop)
        return true
    }

    private fun createSession(
        camera: CameraDevice,
        imageSurface: android.view.Surface,
        handler: Handler,
        generation: Long
    ) {
        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                var configurationError: Exception? = null
                val configured = synchronized(stateLock) {
                    if (!isCurrentSessionLocked(generation) || cameraDevice !== camera) {
                        false
                    } else {
                        try {
                            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(imageSurface)
                                set(
                                    CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                )
                                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            }.build()
                            session.setRepeatingRequest(request, null, handler)
                            captureSession = session
                            true
                        } catch (e: CameraAccessException) {
                            configurationError = e
                            false
                        } catch (e: IllegalStateException) {
                            configurationError = e
                            false
                        }
                    }
                }
                if (!configured) {
                    session.close()
                    if (configurationError != null) {
                        AppLogger.error("Unable to start camera capture session", configurationError)
                        if (stop(generation)) {
                            dispatchStatus(R.string.status_camera_unavailable)
                        }
                    }
                    return
                }

                AppLogger.info("Camera capture session configured")
                if (isCurrentSession(generation)) {
                    dispatchStatus(R.string.status_scanning)
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                AppLogger.error("Camera capture session configuration failed")
                session.close()
                if (stop(generation)) {
                    dispatchStatus(R.string.status_camera_unavailable)
                }
            }
        }

        var creationError: Exception? = null
        synchronized(stateLock) {
            if (!isCurrentSessionLocked(generation) || cameraDevice !== camera) {
                return
            }
            try {
                camera.createCaptureSession(
                    SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        listOf(OutputConfiguration(imageSurface)),
                        camera2Executor,
                        callback
                    )
                )
            } catch (e: CameraAccessException) {
                creationError = e
            } catch (e: IllegalStateException) {
                creationError = e
            }
        }
        if (creationError != null) {
            AppLogger.error("Unable to create camera capture session", creationError)
            if (stop(generation)) {
                dispatchStatus(R.string.status_camera_unavailable)
            }
        }
    }

    private fun beginSession(): Long? = synchronized(stateLock) {
        if (released) {
            AppLogger.warn("Ignoring scanner start because the session has been released")
            return@synchronized null
        }
        if (cameraOpening || captureSession != null || cameraDevice != null) {
            AppLogger.debug("Ignoring scanner start because a session is already active")
            return@synchronized null
        }
        sessionGeneration += 1
        cameraOpening = true
        sessionGeneration
    }

    private fun finishOpening(generation: Long) {
        synchronized(stateLock) {
            if (sessionGeneration == generation) {
                cameraOpening = false
            }
        }
    }

    private fun isCurrentSession(generation: Long): Boolean = synchronized(stateLock) {
        isCurrentSessionLocked(generation)
    }

    private fun isCurrentSessionLocked(generation: Long): Boolean =
        !released && sessionGeneration == generation

    private fun startBackgroundThread(generation: Long): Handler? = synchronized(stateLock) {
        if (!isCurrentSessionLocked(generation)) {
            return@synchronized null
        }
        backgroundHandler?.let { return@synchronized it }
        AppLogger.debug("Starting camera background thread")
        val thread = HandlerThread("quest-camera").also { it.start() }
        Handler(thread.looper).also { handler ->
            backgroundThread = thread
            backgroundHandler = handler
        }
    }

    private fun stopBackgroundThread(thread: HandlerThread?) {
        AppLogger.debug("Stopping camera background thread")
        thread?.quitSafely()
        if (thread != null && thread !== Thread.currentThread()) {
            try {
                thread.join()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun registerCameraOpen(generation: Long): Boolean = synchronized(stateLock) {
        if (!isCurrentSessionLocked(generation) || !cameraOpening) {
            return@synchronized false
        }
        pendingCameraOpens += 1
        true
    }

    private fun resolveCameraOpen(openResolved: AtomicBoolean) {
        if (!openResolved.compareAndSet(false, true)) {
            return
        }
        synchronized(stateLock) {
            pendingCameraOpens -= 1
        }
        maybeShutdownCameraExecutor()
    }

    private fun handleCameraOpenFailure(
        generation: Long,
        openResolved: AtomicBoolean,
        error: Exception
    ) {
        AppLogger.error("Unable to open camera", error)
        resolveCameraOpen(openResolved)
        if (stop(generation)) {
            dispatchStatus(R.string.status_camera_unavailable)
        }
    }

    private fun maybeShutdownCameraExecutor() {
        val shouldShutdown = synchronized(stateLock) {
            if (released && pendingCameraOpens == 0 && !cameraExecutorShutdown) {
                cameraExecutorShutdown = true
                true
            } else {
                false
            }
        }
        if (shouldShutdown) {
            cameraExecutor.shutdown()
        }
    }

    private fun chooseAnalysisSize(sizes: Array<Size>): Size? {
        return sizes
            .sortedByDescending { it.width * it.height }
            .firstOrNull { it.width == 1280 && it.height == 1280 }
            ?: sizes.firstOrNull { it.width <= 1280 && it.height <= 1280 }
            ?: sizes.minByOrNull { it.width * it.height }
    }

    private fun maybeDispatchPreviewFrame(image: android.media.Image) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPreviewFrameAtMs < PREVIEW_FRAME_INTERVAL_MS) {
            return
        }
        if (!previewFrameInFlight.compareAndSet(false, true)) {
            return
        }

        if (image.planes.size < 3) {
            previewFrameInFlight.set(false)
            return
        }
        val previewBitmap = image.toPreviewBitmap().let { bitmap ->
            // Passthrough cameras feed upright frames; phone sensors are
            // usually mounted rotated and report it via SENSOR_ORIENTATION.
            if (activeSensorOrientation == 0) bitmap
            else Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height,
                android.graphics.Matrix().apply { postRotate(activeSensorOrientation.toFloat()) },
                true
            )
        }
        lastPreviewFrameAtMs = now
        onPreviewFrame(previewBitmap)
        previewFrameInFlight.set(false)
    }

    private fun android.media.Image.toPreviewBitmap(): Bitmap {
        val sample = PREVIEW_SAMPLE_STEP
        val width = width
        val height = height
        val outWidth = width / sample
        val outHeight = height / sample
        val pixels = IntArray(outWidth * outHeight)
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val yBuffer = yPlane.buffer.duplicate()
        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()
        val yRowData = ByteArray(yPlane.rowStride)
        val uvRowDataU = ByteArray(uPlane.rowStride)
        val uvRowDataV = ByteArray(vPlane.rowStride)
        var index = 0

        for (row in 0 until outHeight) {
            val sourceRow = row * sample
            val uvSourceRow = sourceRow / 2
            yBuffer.position(sourceRow * yPlane.rowStride)
            yBuffer.get(yRowData, 0, yPlane.rowStride)
            uBuffer.position(uvSourceRow * uPlane.rowStride)
            uBuffer.get(uvRowDataU, 0, uPlane.rowStride)
            vBuffer.position(uvSourceRow * vPlane.rowStride)
            vBuffer.get(uvRowDataV, 0, vPlane.rowStride)

            for (col in 0 until outWidth) {
                val sourceCol = col * sample
                val uvCol = sourceCol / 2
                val y = yRowData[sourceCol * yPlane.pixelStride].toInt() and 0xFF
                val u = (uvRowDataU[uvCol * uPlane.pixelStride].toInt() and 0xFF) - 128
                val v = (uvRowDataV[uvCol * vPlane.pixelStride].toInt() and 0xFF) - 128

                val r = (y + 1.402f * v).toInt().coerceIn(0, 255)
                val g = (y - 0.344136f * u - 0.714136f * v).toInt().coerceIn(0, 255)
                val b = (y + 1.772f * u).toInt().coerceIn(0, 255)
                pixels[index++] = -0x1000000 or (r shl 16) or (g shl 8) or b
            }
        }

        return Bitmap.createBitmap(pixels, outWidth, outHeight, Bitmap.Config.ARGB_8888)
    }

    private fun findPassthroughConfig(): CameraConfig? {
        val sourceKey = CameraCharacteristics.Key<Int>(META_CAMERA_SOURCE, Int::class.javaObjectType)
        val positionKey = CameraCharacteristics.Key<Int>(META_CAMERA_POSITION, Int::class.javaObjectType)

        return cameraManager.cameraIdList
            .mapNotNull { cameraId ->
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                // Non-Meta platforms THROW IllegalArgumentException for unknown
                // vendor keys instead of returning null — treat that as "not a
                // passthrough camera" so the standard-camera fallback can run.
                val source = runCatching { characteristics.get(sourceKey) }.getOrNull()
                if (source != PASSTHROUGH_SOURCE) {
                    return@mapNotNull null
                }

                val scalerMap = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: return@mapNotNull null

                val analysisOutputSizes = scalerMap.getOutputSizes(ImageFormat.YUV_420_888)
                    ?: return@mapNotNull null

                CameraConfig(
                    cameraId = cameraId,
                    position = runCatching { characteristics.get(positionKey) }.getOrNull(),
                    analysisOutputSizes = analysisOutputSizes
                )
            }
            .sortedWith(compareBy<CameraConfig> { it.position ?: Int.MAX_VALUE }.thenBy { it.cameraId })
            .firstOrNull()
    }

    /** Ordinary device camera for non-headset hardware: back-facing preferred. */
    private fun findStandardConfig(): CameraConfig? {
        val candidates = cameraManager.cameraIdList.mapNotNull { cameraId ->
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val scalerMap = characteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return@mapNotNull null
            val analysisOutputSizes = scalerMap.getOutputSizes(ImageFormat.YUV_420_888)
                ?: return@mapNotNull null
            Triple(
                CameraConfig(
                    cameraId = cameraId,
                    position = null,
                    analysisOutputSizes = analysisOutputSizes,
                    sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                ),
                characteristics.get(CameraCharacteristics.LENS_FACING),
                cameraId
            )
        }
        return (candidates.firstOrNull { it.second == CameraCharacteristics.LENS_FACING_BACK }
            ?: candidates.minByOrNull { it.third })?.first
    }

    private fun dispatchStatus(messageId: Int) {
        onStatusChanged(messageId)
    }

    private data class CameraConfig(
        val cameraId: String,
        val position: Int?,
        val analysisOutputSizes: Array<Size>,
        val sensorOrientation: Int = 0
    )

    private companion object {
        const val META_CAMERA_SOURCE = "com.meta.extra_metadata.camera_source"
        const val META_CAMERA_POSITION = "com.meta.extra_metadata.position"
        const val PASSTHROUGH_SOURCE = 0
        const val PREVIEW_FRAME_INTERVAL_MS = 120L
        const val PREVIEW_SAMPLE_STEP = 4
    }
}
