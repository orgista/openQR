package com.orgista.openqr

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.res.Configuration
import android.content.Intent
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.orgista.openqr.browser.LinkRouter
import com.orgista.openqr.browser.RouteResult
import com.orgista.openqr.camera.QuestCameraSession
import com.orgista.openqr.logging.AppLogger
import com.orgista.openqr.databinding.ActivityMainBinding
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraSession: QuestCameraSession

    private val mainHandler = Handler(Looper.getMainLooper())
    private var shouldStartWhenReady = false
    private var permanentDenial = false
    private var browserHandoffComplete = false
    private var scanRequested = false
    private var cameraModeEnabled = false
    private var pendingBrowserHandoff: Runnable? = null
    private var pendingDebugPayload: String? = null
    private var pendingScanTimeout: Runnable? = null
    private var pulseAnimator: AnimatorSet? = null
    private var bracketsAnimator: ObjectAnimator? = null
    private var sweepAnimator: ObjectAnimator? = null
    private var edgeGlowAnimator: ObjectAnimator? = null
    private var stateIconAnimator: AnimatorSet? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        AppLogger.info("Permission result received: allGranted=${hasAllPermissions()}")
        permanentDenial = requiredPermissions.any { permission ->
            !shouldShowRequestPermissionRationale(permission) && !hasPermission(permission)
        }
        if (hasAllPermissions()) {
            permanentDenial = false
            renderReadyState()
        } else {
            renderPermissionState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_OpenQR)
        super.onCreate(savedInstanceState)
        AppLogger.info("MainActivity created")
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyPreviewMode()
        binding.scannerShell.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyResponsiveSizing()
        }
        binding.scannerShell.post { applyResponsiveSizing() }

        cameraSession = QuestCameraSession(
            context = this,
            onQrDetected = { value -> runOnUiThread { handleQrCode(value) } },
            onStatusChanged = { messageId -> runOnUiThread { handleCameraStatus(messageId) } },
            onPreviewFrame = ::renderPreviewFrame
        )

        binding.actionButton.setOnClickListener {
            if (scanRequested) {
                stopScannerFromActionButton()
            } else if (hasAllPermissions()) {
                scanRequested = true
                startScanner()
            } else if (permanentDenial) {
                openAppSettings()
            } else {
                requestPermissions()
            }
        }
        binding.cameraModeButton.setOnClickListener {
            if (hasAllPermissions()) {
                cameraModeEnabled = !cameraModeEnabled
                if (!cameraModeEnabled) {
                    cameraSession.pauseScanning()
                    if (!scanRequested) {
                        cameraSession.stop()
                    }
                    clearPreviewFrame()
                    renderReadyState()
                } else {
                    startPreviewMode()
                }
                applyPreviewMode()
            } else if (permanentDenial) {
                openAppSettings()
            } else {
                requestPermissions()
            }
        }

        renderPermissionState()
        if (savedInstanceState == null) {
            captureDebugPayload(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureDebugPayload(intent)
    }

    override fun onResume() {
        super.onResume()
        AppLogger.debug("MainActivity resumed")
        applyResponsiveSizing()
        resetAfterHandoff()
        pendingDebugPayload?.let { payload ->
            pendingDebugPayload = null
            handleQrCode(payload)
            return
        }
        if (!browserHandoffComplete && hasAllPermissions()) {
            if (scanRequested) {
                shouldStartWhenReady = true
                startScanner()
            } else if (cameraModeEnabled) {
                startPreviewMode()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.scannerShell.post { applyResponsiveSizing() }
    }

    override fun onPause() {
        super.onPause()
        AppLogger.debug("MainActivity paused")
        clearPendingBrowserHandoff()
        clearPendingScanTimeout()
        stopVisualAnimations()
        cameraSession.stop()
        clearPreviewFrame()
        scanRequested = false
    }

    override fun onStop() {
        super.onStop()
        AppLogger.debug("MainActivity stopped")
        clearPendingBrowserHandoff()
        clearPendingScanTimeout()
        stopVisualAnimations()
        if (::cameraSession.isInitialized) {
            cameraSession.stop()
        }
        clearPreviewFrame()
        scanRequested = false
    }

    override fun onDestroy() {
        clearPendingBrowserHandoff()
        mainHandler.removeCallbacks(handoffResetFallback)
        if (::cameraSession.isInitialized) {
            cameraSession.release()
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!::cameraSession.isInitialized) {
            return
        }

        if (!hasFocus) {
            AppLogger.debug("Window focus lost; stopping scanner early")
            clearPendingBrowserHandoff()
            clearPendingScanTimeout()
            stopVisualAnimations()
            cameraSession.stop()
            clearPreviewFrame()
            scanRequested = false
            return
        }

        // A panel opening beside the reader bounces focus away and straight back;
        // let the fallback timer end the handoff state instead of that bounce.
        if (!mainHandler.hasCallbacks(handoffResetFallback)) {
            resetAfterHandoff()
        }
        if (!browserHandoffComplete && scanRequested && hasAllPermissions()) {
            AppLogger.debug("Window focus regained; resuming scanner")
            shouldStartWhenReady = true
            startScanner()
        } else if (!browserHandoffComplete && cameraModeEnabled && hasAllPermissions()) {
            AppLogger.debug("Window focus regained; resuming preview")
            startPreviewMode()
        }
    }

    private fun renderPermissionState() {
        if (hasAllPermissions()) {
            renderReadyState()
            return
        }

        renderState(
            statusRes = if (permanentDenial) R.string.status_permission_denied else R.string.status_permission_needed,
            buttonTextRes = if (permanentDenial) R.string.action_open_settings else R.string.action_grant_access,
            iconRes = R.drawable.ic_state_lock
        )
    }

    private fun requestPermissions() {
        AppLogger.info("Requesting camera permissions")
        permissionLauncher.launch(requiredPermissions)
    }

    private fun renderReadyState() {
        renderState(
            statusRes = null,
            buttonTextRes = R.string.action_start_scan,
            iconRes = null
        )
        startIdleAnimation()
    }

    private fun startScanner() {
        clearPendingBrowserHandoff()
        clearPendingScanTimeout()
        if (!cameraSession.hasUsableCamera()) {
            AppLogger.warn("Passthrough camera not available on this device")
            renderState(
                statusRes = unsupportedDeviceStatusRes(),
                buttonTextRes = R.string.action_unavailable,
                iconRes = R.drawable.ic_state_warning,
                buttonEnabled = false
            )
            scanRequested = false
            return
        }
        if (!hasAllPermissions()) {
            AppLogger.debug(
                "Scanner start deferred: allPermissions=${hasAllPermissions()}"
            )
            shouldStartWhenReady = true
            return
        }

        AppLogger.info("Starting scanner session")
        shouldStartWhenReady = false
        renderScanningState()
        cameraSession.resumeScanning()
        cameraSession.start()
        pendingScanTimeout = Runnable {
            AppLogger.info("Scan session timed out; releasing camera")
            cameraSession.stop()
            clearPreviewFrame()
            scanRequested = false
            renderReadyState()
        }
        mainHandler.postDelayed(pendingScanTimeout!!, SCAN_SESSION_TIMEOUT_MS)
    }

    private fun startPreviewMode() {
        clearPendingBrowserHandoff()
        clearPendingScanTimeout()
        if (!cameraSession.hasUsableCamera()) {
            AppLogger.warn("Preview mode unavailable because passthrough camera is missing")
            renderState(
                statusRes = unsupportedDeviceStatusRes(),
                buttonTextRes = R.string.action_unavailable,
                iconRes = R.drawable.ic_state_warning,
                buttonEnabled = false
            )
            cameraModeEnabled = false
            applyPreviewMode()
            return
        }
        AppLogger.info("Starting preview-only camera session")
        cameraSession.pauseScanning()
        cameraSession.start()
        renderReadyState()
    }

    private fun renderScanningState() {
        stopVisualAnimations()
        binding.statusText.visibility = View.GONE
        binding.stateIcon.visibility = View.GONE
        binding.actionButton.visibility = View.VISIBLE
        binding.actionButton.isEnabled = true
        binding.actionButton.isActivated = true
        binding.actionButton.setText(R.string.action_scanning)
        binding.cameraModeButton.visibility = View.VISIBLE
        binding.cameraModeButton.isEnabled = true
        binding.cameraModeButton.isActivated = cameraModeEnabled
        binding.pulseRing.visibility = View.VISIBLE
        binding.bracketsView.visibility = View.VISIBLE
        binding.sweepView.visibility = View.VISIBLE
        binding.centerDot.visibility = View.VISIBLE
        applyPreviewMode()
        startScanAnimation()
    }

    private fun stopScannerFromActionButton() {
        AppLogger.info("Stopping scanner session from action button")
        clearPendingBrowserHandoff()
        clearPendingScanTimeout()
        scanRequested = false
        cameraSession.pauseScanning()

        if (cameraModeEnabled) {
            startPreviewMode()
        } else {
            cameraSession.stop()
            clearPreviewFrame()
            renderReadyState()
        }
    }

    // Debug builds only: lets e2e tests inject a payload as if it had been scanned.
    private fun captureDebugPayload(intent: Intent?) {
        if (!BuildConfig.DEBUG) {
            return
        }
        val payload = intent?.getStringExtra(DEBUG_PAYLOAD_EXTRA) ?: return
        intent.removeExtra(DEBUG_PAYLOAD_EXTRA)
        AppLogger.debug("Debug payload received")
        pendingDebugPayload = payload
    }

    // The reader stays alive after a handoff so the user comes back to it when
    // the other app closes; the first resume or focus return resets the UI.
    private val handoffResetFallback = Runnable { resetAfterHandoff() }

    private fun resetAfterHandoff() {
        mainHandler.removeCallbacks(handoffResetFallback)
        if (!browserHandoffComplete) {
            return
        }
        browserHandoffComplete = false
        renderReadyState()
    }

    // A camera callback can land after onStop; never hand off from the background.
    private fun canHandOff(): Boolean =
        !isFinishing && !isDestroyed && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun handleQrCode(rawValue: String) {
        if (!canHandOff()) {
            return
        }
        AppLogger.info("QR candidate detected")
        if (LinkRouter.parse(rawValue) == null) {
            AppLogger.warn("Rejected QR content because it is not a supported link")
            clearPendingScanTimeout()
            cameraSession.stop()
            scanRequested = false
            renderState(
                statusRes = R.string.status_invalid_code,
                buttonTextRes = R.string.action_rescan,
                iconRes = R.drawable.ic_state_warning
            )
            clearPreviewFrame()
            return
        }

        clearPendingScanTimeout()
        cameraSession.stop()
        scanRequested = false
        renderState(
            statusRes = null,
            buttonTextRes = R.string.action_opening,
            iconRes = R.drawable.ic_state_check,
            buttonEnabled = false
        )

        pendingBrowserHandoff = Runnable {
            if (!canHandOff()) {
                return@Runnable
            }
            val route = runCatching { LinkRouter.open(this, rawValue) }.getOrElse { error ->
                AppLogger.error("Link routing threw", error)
                RouteResult.NoHandler
            }
            when (val result = route) {
                is RouteResult.Launched -> {
                    AppLogger.info("External handoff succeeded: target=${result.target}")
                    browserHandoffComplete = true
                    val vrTap = LinkRouter.needsVrTap(rawValue)
                    // A browser panel can open beside the reader without taking its
                    // focus, so don't wait on resume/focus alone to leave "Opening".
                    // WebXR spaces keep the "tap VR" hint up long enough to read.
                    mainHandler.postDelayed(
                        handoffResetFallback,
                        if (vrTap) VR_TAP_HINT_MS else HANDOFF_RESET_FALLBACK_MS
                    )
                    renderState(
                        statusRes = if (vrTap) R.string.status_tap_vr else null,
                        buttonTextRes = R.string.action_opening,
                        iconRes = R.drawable.ic_state_check,
                        buttonEnabled = false
                    )
                }

                RouteResult.Invalid -> {
                    AppLogger.warn("Supported link became invalid before handoff")
                    scanRequested = false
                    clearPreviewFrame()
                    renderState(
                        statusRes = R.string.status_invalid_code,
                        buttonTextRes = R.string.action_rescan,
                        iconRes = R.drawable.ic_state_warning
                    )
                }

                RouteResult.NoHandler -> {
                    AppLogger.error("External handoff failed because no app resolved")
                    scanRequested = false
                    clearPreviewFrame()
                    renderState(
                        statusRes = R.string.status_browser_missing,
                        buttonTextRes = R.string.action_rescan,
                        iconRes = R.drawable.ic_state_warning
                    )
                }
            }
            pendingBrowserHandoff = null
        }
        mainHandler.postDelayed(pendingBrowserHandoff!!, BROWSER_HANDOFF_DELAY_MS)
    }

    private fun openAppSettings() {
        AppLogger.info("Opening app settings for manual permission grant")
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }

    // The headset passthrough permission only exists on Horizon OS. Requiring
    // it unconditionally left every phone/tablet permanently "Camera blocked":
    // platforms auto-deny permissions they don't define, so hasAllPermissions()
    // could never become true off-headset.
    private val requiredPermissions: Array<String> by lazy {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        val headsetCameraKnown = runCatching {
            packageManager.getPermissionInfo(HEADSET_CAMERA_PERMISSION, 0)
        }.isSuccess
        if (headsetCameraKnown) permissions += HEADSET_CAMERA_PERMISSION
        permissions.toTypedArray()
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions.all(::hasPermission)

    // "Quest 3 or 3S" is real guidance on a headset (Quest 2 lacks the
    // passthrough camera API); on a phone or tablet it is nonsense — there the
    // only unsupported case is hardware with no usable camera at all.
    private fun unsupportedDeviceStatusRes(): Int =
        if (requiredPermissions.contains(HEADSET_CAMERA_PERMISSION)) {
            R.string.status_unsupported_device
        } else {
            R.string.status_no_camera
        }

    private fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun handleCameraStatus(messageId: Int) {
        when (messageId) {
            R.string.status_camera_unavailable -> renderState(
                statusRes = R.string.status_camera_unavailable,
                buttonTextRes = R.string.action_rescan,
                iconRes = R.drawable.ic_state_warning
            )

            R.string.status_unsupported_device, R.string.status_no_camera -> renderState(
                statusRes = unsupportedDeviceStatusRes(),
                buttonTextRes = R.string.action_unavailable,
                iconRes = R.drawable.ic_state_warning,
                buttonEnabled = false
            )
        }
    }

    private fun renderState(
        statusRes: Int?,
        buttonTextRes: Int,
        iconRes: Int?,
        buttonEnabled: Boolean = true
    ) {
        stopVisualAnimations()

        binding.pulseRing.visibility = View.GONE
        binding.bracketsView.visibility = View.GONE
        binding.sweepView.visibility = View.GONE
        binding.centerDot.visibility = View.GONE

        if (statusRes == null) {
            binding.statusText.visibility = View.GONE
        } else {
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.setText(statusRes)
        }

        if (iconRes == null) {
            binding.stateIcon.visibility = View.GONE
        } else {
            binding.stateIcon.visibility = View.VISIBLE
            binding.stateIcon.setImageResource(iconRes)
            startIconPulse()
        }

        binding.actionButton.visibility = View.VISIBLE
        binding.actionButton.isEnabled = buttonEnabled
        binding.actionButton.isActivated = scanRequested
        binding.actionButton.setText(buttonTextRes)
        binding.cameraModeButton.visibility = View.VISIBLE
        binding.cameraModeButton.isEnabled = true
        binding.cameraModeButton.isActivated = cameraModeEnabled
        applyPreviewMode()
    }

    private fun startIdleAnimation() {
        binding.pulseRing.visibility = View.VISIBLE
        binding.bracketsView.visibility = View.VISIBLE
        binding.sweepView.visibility = View.VISIBLE
        binding.centerDot.visibility = View.VISIBLE

        binding.pulseRing.scaleX = 1f
        binding.pulseRing.scaleY = 1f
        binding.pulseRing.alpha = 0.14f
        binding.bracketsView.rotation = 0f
        binding.bracketsView.alpha = 0.44f
        binding.sweepView.rotation = 0f
        binding.sweepView.alpha = 0.16f
        binding.centerDot.alpha = 0.55f

        pulseAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.pulseRing, View.SCALE_X, 1f, 1.12f, 1f).apply {
                    duration = 3600L
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.RESTART
                    interpolator = FastOutSlowInInterpolator()
                },
                ObjectAnimator.ofFloat(binding.pulseRing, View.SCALE_Y, 1f, 1.12f, 1f).apply {
                    duration = 3600L
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.RESTART
                    interpolator = FastOutSlowInInterpolator()
                },
                ObjectAnimator.ofFloat(binding.pulseRing, View.ALPHA, 0.12f, 0.2f, 0.12f).apply {
                    duration = 3600L
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.RESTART
                    interpolator = FastOutSlowInInterpolator()
                }
            )
            start()
        }

        bracketsAnimator = ObjectAnimator.ofFloat(binding.bracketsView, View.ROTATION, 0f, 360f).apply {
            duration = 22000L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = LinearInterpolator()
            start()
        }

        sweepAnimator = ObjectAnimator.ofFloat(binding.sweepView, View.ROTATION, 0f, 360f).apply {
            duration = 9000L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = LinearInterpolator()
            start()
        }

        edgeGlowAnimator = ObjectAnimator.ofFloat(
            binding.scannerEdgeGlow,
            View.ALPHA,
            if (cameraModeEnabled) 0.2f else 0.12f,
            if (cameraModeEnabled) 0.3f else 0.18f,
            if (cameraModeEnabled) 0.2f else 0.12f
        ).apply {
            duration = 2600L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = FastOutSlowInInterpolator()
            start()
        }
    }

    private fun applyPreviewMode() {
        val previewAlpha = if (cameraModeEnabled) 0.98f else 0f
        val scrimAlpha = if (cameraModeEnabled) 0.08f else 0f
        val glowAlpha = if (cameraModeEnabled) 0.22f else 0.12f

        binding.cameraPreview.setRenderEffect(
            if (cameraModeEnabled) {
                RenderEffect.createBlurEffect(1.5f, 1.5f, Shader.TileMode.CLAMP)
            } else {
                null
            }
        )
        binding.cameraPreview.alpha = previewAlpha
        binding.previewScrim.alpha = scrimAlpha
        binding.scannerEdgeGlow.alpha = glowAlpha
        binding.cameraModeButton.isActivated = cameraModeEnabled
    }

    private fun renderPreviewFrame(bitmap: Bitmap) {
        binding.cameraPreview.post {
            binding.cameraPreview.setImageBitmap(bitmap)
        }
    }

    private fun clearPreviewFrame() {
        binding.cameraPreview.setImageDrawable(null)
    }

    private fun applyResponsiveSizing() {
        val shellWidth = binding.scannerShell.width
        val shellHeight = binding.scannerShell.height
        if (shellWidth == 0 || shellHeight == 0) {
            return
        }

        val shellSize = min(shellWidth, shellHeight).toFloat()
        resizeSquareView(binding.sweepView, (shellSize * 0.38f).roundToInt())
        resizeSquareView(binding.pulseRing, (shellSize * 0.30f).roundToInt())
        resizeSquareView(binding.bracketsView, (shellSize * 0.24f).roundToInt())
        resizeSquareView(binding.stateIcon, (shellSize * 0.34f).roundToInt())
        resizeSquareView(binding.centerDot, (shellSize * 0.03f).roundToInt())
    }

    private fun resizeSquareView(view: View, sizePx: Int) {
        val clamped = sizePx.coerceAtLeast(6)
        view.updateLayoutParams {
            width = clamped
            height = clamped
        }
    }

    private fun startScanAnimation() {
        binding.pulseRing.scaleX = 1f
        binding.pulseRing.scaleY = 1f
        binding.pulseRing.alpha = 0.3f
        binding.bracketsView.rotation = 0f
        binding.sweepView.rotation = 0f

        pulseAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.pulseRing, View.SCALE_X, 1f, 1.35f, 1f).apply {
                    duration = 3000L
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.RESTART
                    interpolator = FastOutSlowInInterpolator()
                },
                ObjectAnimator.ofFloat(binding.pulseRing, View.SCALE_Y, 1f, 1.35f, 1f).apply {
                    duration = 3000L
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.RESTART
                    interpolator = FastOutSlowInInterpolator()
                },
                ObjectAnimator.ofFloat(binding.pulseRing, View.ALPHA, 0.32f, 0.1f, 0.32f).apply {
                    duration = 3000L
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.RESTART
                    interpolator = FastOutSlowInInterpolator()
                }
            )
            start()
        }

        bracketsAnimator = ObjectAnimator.ofFloat(binding.bracketsView, View.ROTATION, 0f, 360f).apply {
            duration = 10000L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = LinearInterpolator()
            start()
        }

        sweepAnimator = ObjectAnimator.ofFloat(binding.sweepView, View.ROTATION, 0f, 360f).apply {
            duration = 4000L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = LinearInterpolator()
            start()
        }

        edgeGlowAnimator = ObjectAnimator.ofFloat(binding.scannerEdgeGlow, View.ALPHA, 0.72f, 1f, 0.72f).apply {
            duration = 1800L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = FastOutSlowInInterpolator()
            start()
        }
    }

    private fun startIconPulse() {
        binding.stateIcon.scaleX = 0.92f
        binding.stateIcon.scaleY = 0.92f
        binding.stateIcon.alpha = 0.85f
        val scaleX = ObjectAnimator.ofFloat(binding.stateIcon, View.SCALE_X, 0.92f, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.stateIcon, View.SCALE_Y, 0.92f, 1f)
        val alpha = ObjectAnimator.ofFloat(binding.stateIcon, View.ALPHA, 0.85f, 1f)
        stateIconAnimator = AnimatorSet().apply {
            duration = 240L
            interpolator = FastOutSlowInInterpolator()
            playTogether(scaleX, scaleY, alpha)
            start()
        }
    }

    private fun stopVisualAnimations() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        bracketsAnimator?.cancel()
        bracketsAnimator = null
        sweepAnimator?.cancel()
        sweepAnimator = null
        edgeGlowAnimator?.cancel()
        edgeGlowAnimator = null
        stateIconAnimator?.cancel()
        stateIconAnimator = null
        binding.scannerEdgeGlow.alpha = if (cameraModeEnabled) 0.22f else 0.12f
    }

    private fun clearPendingBrowserHandoff() {
        val cancelled = pendingBrowserHandoff
        cancelled?.let(mainHandler::removeCallbacks)
        pendingBrowserHandoff = null
        if (cancelled != null) {
            // The launch never happened; don't leave "Opening" on screen.
            renderReadyState()
        }
    }

    private fun clearPendingScanTimeout() {
        pendingScanTimeout?.let(mainHandler::removeCallbacks)
        pendingScanTimeout = null
    }

    private companion object {
        const val BROWSER_HANDOFF_DELAY_MS = 550L
        const val HANDOFF_RESET_FALLBACK_MS = 3000L
        const val VR_TAP_HINT_MS = 8000L
        const val DEBUG_PAYLOAD_EXTRA = "openqr.debug.payload"
        const val SCAN_SESSION_TIMEOUT_MS = 10000L
        const val HEADSET_CAMERA_PERMISSION = "horizonos.permission.HEADSET_CAMERA"
    }
}
