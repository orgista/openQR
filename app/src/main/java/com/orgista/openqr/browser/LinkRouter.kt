package com.orgista.openqr.browser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.net.toUri
import com.orgista.openqr.logging.AppLogger
import java.net.IDN
import java.net.URI
import java.net.URLEncoder

/**
 * Turns a scanned QR payload into an outbound VIEW intent.
 *
 * Web links (http/https) go to the app that claims the domain when exactly one
 * does, otherwise to the system browser or chooser. Custom-scheme deep links
 * are only launched when an installed app declares them BROWSABLE. Payloads,
 * URLs and hosts are never logged.
 */
object LinkRouter {
    private val webSchemes = setOf("http", "https")

    private val blockedSchemes = setOf(
        "intent", "javascript", "file", "content", "data", "about", "blob",
        "chrome", "android-app", "vbscript", "filesystem", "ws", "wss"
    )

    // RFC 3986: scheme = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )
    private val schemePattern = Regex("^[A-Za-z][A-Za-z0-9+.-]*$")

    private val youTubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")

    const val YOUTUBE_VR_PACKAGE = "com.google.android.apps.youtube.vr.oculus"
    private const val YOUTUBE_VR_IMMERSIVE_ACTIVITY =
        "com.google.android.apps.youtube.vr.activities.YouTubeVrActivity"
    private const val YOUTUBE_VR_PANEL_ACTIVITY =
        "com.google.android.apps.youtube.vr.activities.YouTubeVrPanel2Activity"

    private const val BROWSER_PROBE_URL = "https://example.invalid/"

    const val META_STORE_PACKAGE = "com.oculus.store"
    private val metaStoreHosts = setOf("meta.com", "www.meta.com", "oculus.com", "www.oculus.com")
    private val metaStorePathPrefixes = listOf("/experiences/")
    private val localeSegment = Regex("^[a-z]{2}(-[a-z]{2})?$")
    // Legacy oculus.com links carry a platform segment: /experiences/quest/<id>/
    private val legacyStorePlatforms = setOf("quest", "rift", "go", "gear-vr", "pcvr")

    private const val MATTERPORT_SHOW_HOST = "my.matterport.com"
    private const val MATTERPORT_DISCOVER_HOST = "discover.matterport.com"
    private val matterportForcedParams = listOf("play" to "1", "qs" to "1", "vr" to "1")

    sealed interface ParsedLink {
        val value: String

        data class Web(override val value: String) : ParsedLink
        data class App(override val value: String) : ParsedLink
    }

    /** Pure payload validation. Returns null for anything that must not be launched. */
    fun parse(rawValue: String): ParsedLink? {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        val uri = parseUri(trimmed) ?: return null
        val rawScheme = uri.scheme ?: return null
        if (!schemePattern.matches(rawScheme)) {
            return null
        }
        val scheme = rawScheme.lowercase()
        if (scheme in blockedSchemes) {
            return null
        }

        if (scheme in webSchemes) {
            val host = uri.host?.trim().orEmpty()
            if (host.isEmpty() || uri.userInfo != null) {
                return null
            }
            // Intent filters match scheme and host case-sensitively, and QR codes in
            // alphanumeric mode are often all-caps (HTTPS://EXAMPLE.COM/…).
            val normalized = uri.normalize().toASCIIString()
            val authority = uri.rawAuthority
            val lowered = scheme + normalized.substring(rawScheme.length)
                .replaceFirst("//$authority", "//${authority.lowercase()}")
            return ParsedLink.Web(lowered)
        }

        // App deep links use the hierarchical `scheme://` form. Requiring it keeps
        // data payloads that merely look like a scheme — WIFI:…;P:password;;,
        // BEGIN:VCARD, MECARD:, host:port — from ever reaching another app.
        if (uri.isOpaque || uri.rawSchemeSpecificPart.isNullOrEmpty() || !trimmed.substring(rawScheme.length).startsWith("://")) {
            return null
        }
        // Intent filters match schemes case-sensitively and apps declare them in lowercase.
        return ParsedLink.App(scheme + uri.toASCIIString().substring(rawScheme.length))
    }

    /** java.net.URI leaves Unicode (IDN) hosts null; retry with the host punycoded. */
    private fun parseUri(value: String): URI? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val authority = uri.rawAuthority
        if (uri.host != null || authority == null || '@' in authority) {
            return uri
        }
        val colon = authority.lastIndexOf(':').takeIf { it > authority.lastIndexOf(']') } ?: -1
        val host = if (colon >= 0) authority.substring(0, colon) else authority
        val port = if (colon >= 0) authority.substring(colon) else ""
        val ascii = runCatching { IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES) }.getOrNull() ?: return uri
        if (ascii == host) {
            return uri
        }
        val start = value.indexOf(authority)
        if (start < 0) {
            return uri
        }
        val rebuilt = value.substring(0, start) + ascii + port + value.substring(start + authority.length)
        return runCatching { URI(rebuilt) }.getOrNull()?.takeIf { it.host != null } ?: uri
    }

    fun isYouTubeHost(host: String?): Boolean =
        host != null && host.lowercase() in youTubeHosts

    fun isYouTubeUrl(url: String): Boolean =
        isYouTubeHost(runCatching { URI(url).host }.getOrNull())

    /**
     * Pure URL rewrites applied to web links before routing. Matterport spaces
     * are sent to the showcase player with auto-play, no fly-in, and the VR button.
     */
    fun rewrite(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val host = uri.host?.lowercase() ?: return url
        return when (host) {
            in metaStoreHosts -> rewriteMetaStore(uri) ?: url
            MATTERPORT_DISCOVER_HOST -> rewriteMatterportDiscover(uri) ?: url
            MATTERPORT_SHOW_HOST -> rewriteMatterportShow(uri) ?: url
            else -> url
        }
    }

    /**
     * The Store app only claims www.meta.com/experiences/…, so localized
     * (/en-us/experiences/…) and legacy oculus.com links are normalized to that form.
     */
    private fun rewriteMetaStore(uri: URI): String? {
        val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }.toMutableList()
        if (segments.size >= 2 && localeSegment.matches(segments[0].lowercase()) &&
            segments[1].lowercase() == "experiences"
        ) {
            segments.removeAt(0)
        }
        if (segments.size < 2 || segments[0].lowercase() != "experiences") {
            return null
        }
        segments[0] = "experiences"
        if (segments.size >= 3 && segments[1].lowercase() in legacyStorePlatforms && segments[2].all { it.isDigit() }) {
            segments.removeAt(1)
        }
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return "https://www.meta.com/${segments.joinToString("/")}/$query"
    }

    fun isMetaStoreUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.host?.lowercase() == "www.meta.com" &&
            metaStorePathPrefixes.any { uri.rawPath.orEmpty().startsWith(it) }
    }

    private fun rewriteMatterportDiscover(uri: URI): String? {
        val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
        if (segments.size < 2 || segments[0] != "space") {
            return null
        }
        val id = URLEncoder.encode(segments[1], "UTF-8")
        return buildMatterportShowUrl(id, queryPairs(uri.rawQuery), uri.rawFragment)
    }

    private fun rewriteMatterportShow(uri: URI): String? {
        val path = uri.rawPath.orEmpty()
        if (path != "/show" && path != "/show/") {
            return null
        }
        val pairs = queryPairs(uri.rawQuery)
        val id = pairs.firstOrNull { it.first == "m" }?.second
        if (id.isNullOrEmpty()) {
            return null
        }
        return buildMatterportShowUrl(id, pairs, uri.rawFragment)
    }

    private fun buildMatterportShowUrl(
        id: String,
        existing: List<Pair<String, String?>>,
        rawFragment: String?
    ): String {
        val overridden = matterportForcedParams.map { it.first }.toSet() + "m"
        val params = buildList {
            add("m=$id")
            existing.filter { it.first !in overridden }.forEach { (key, value) ->
                add(if (value == null) key else "$key=$value")
            }
            matterportForcedParams.forEach { (key, value) -> add("$key=$value") }
        }
        val fragment = rawFragment?.let { "#$it" }.orEmpty()
        return "https://$MATTERPORT_SHOW_HOST/show/?${params.joinToString("&")}$fragment"
    }

    private fun queryPairs(rawQuery: String?): List<Pair<String, String?>> =
        rawQuery.orEmpty()
            .split('&')
            .filter { it.isNotEmpty() }
            .map { part ->
                val index = part.indexOf('=')
                if (index < 0) part to null else part.substring(0, index) to part.substring(index + 1)
            }

    /**
     * WebXR spaces (Matterport) load straight into the viewer, but browsers only
     * enter immersive mode from a user gesture, so the user still has to tap VR.
     */
    fun needsVrTap(rawValue: String): Boolean {
        val link = parse(rawValue) as? ParsedLink.Web ?: return false
        val host = runCatching { URI(link.value).host }.getOrNull()?.lowercase() ?: return false
        return host == MATTERPORT_SHOW_HOST || host == MATTERPORT_DISCOVER_HOST
    }

    fun open(activity: Activity, rawValue: String): RouteResult {
        return when (val link = parse(rawValue)) {
            null -> RouteResult.Invalid
            is ParsedLink.Web -> openWeb(activity, rewrite(link.value))
            is ParsedLink.App -> openApp(activity, link.value.toUri())
        }
    }

    private fun openWeb(activity: Activity, url: String): RouteResult {
        val uri = url.toUri()
        if (isYouTubeUrl(url) && isInstalled(activity.packageManager, YOUTUBE_VR_PACKAGE)) {
            for (component in listOf(YOUTUBE_VR_IMMERSIVE_ACTIVITY, YOUTUBE_VR_PANEL_ACTIVITY)) {
                val intent = viewIntent(uri).setClassName(YOUTUBE_VR_PACKAGE, component)
                if (tryStart(activity, intent)) {
                    AppLogger.info("Opened link in YouTube VR")
                    return RouteResult.Launched(LaunchTarget.YouTubeVr)
                }
            }
            AppLogger.warn("YouTube VR handoff failed; falling back to generic routing")
        }
        if (isMetaStoreUrl(url)) {
            // The Store app does not verify meta.com, so Android 12+ would hand the
            // link to the browser; target it explicitly instead.
            if (tryStart(activity, viewIntent(uri).setPackage(META_STORE_PACKAGE))) {
                AppLogger.info("Opened link in the Meta Horizon Store")
                return RouteResult.Launched(LaunchTarget.App)
            }
            AppLogger.warn("Store handoff failed; falling back to generic routing")
        }
        return openWebGeneric(activity, uri)
    }

    private fun openWebGeneric(activity: Activity, uri: Uri): RouteResult {
        val packageManager = activity.packageManager
        val handlers = handlerPackages(packageManager, viewIntent(uri))
        val browsers = handlerPackages(packageManager, viewIntent(BROWSER_PROBE_URL.toUri()))
        val apps = handlers - browsers - activity.packageName

        if (apps.size == 1) {
            val intent = viewIntent(uri).setPackage(apps.first())
            if (tryStart(activity, intent)) {
                AppLogger.info("Opened web link in the app that claims it")
                return RouteResult.Launched(LaunchTarget.App)
            }
        }

        val intent = viewIntent(uri)
        if (runCatching { intent.resolveActivity(packageManager) }.getOrNull() == null) {
            AppLogger.warn("No activity resolved for web link")
            return RouteResult.NoHandler
        }
        if (!tryStart(activity, intent)) {
            AppLogger.error("Web link handoff failed")
            return RouteResult.NoHandler
        }
        val target = if (apps.size > 1) LaunchTarget.Chooser else LaunchTarget.Browser
        AppLogger.info("Opened web link via default resolution: target=$target")
        return RouteResult.Launched(target)
    }

    private fun openApp(activity: Activity, uri: Uri): RouteResult {
        // No resolveActivity() pre-check: package visibility hides apps that only
        // declare a custom scheme, but implicit starts are exempt from that filter.
        val intent = viewIntent(uri)
        if (!tryStart(activity, intent)) {
            AppLogger.warn("No installed app declares this deep link browsable")
            return RouteResult.NoHandler
        }
        AppLogger.info("Opened deep link in installed app")
        return RouteResult.Launched(LaunchTarget.App)
    }

    private fun viewIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { selector = null }

    private fun tryStart(activity: Activity, intent: Intent): Boolean =
        try {
            activity.startActivity(intent)
            true
        } catch (error: ActivityNotFoundException) {
            false
        } catch (error: SecurityException) {
            false
        } catch (error: RuntimeException) {
            AppLogger.error("Handoff threw", error)
            false
        }

    private fun handlerPackages(packageManager: PackageManager, intent: Intent): Set<String> =
        runCatching {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            ).mapNotNull { it.activityInfo?.packageName }.toSet()
        }.getOrDefault(emptySet())

    private fun isInstalled(packageManager: PackageManager, packageName: String): Boolean =
        try {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            true
        } catch (error: PackageManager.NameNotFoundException) {
            false
        } catch (error: RuntimeException) {
            false
        }
}

enum class LaunchTarget {
    YouTubeVr,
    App,
    Browser,
    Chooser
}

sealed interface RouteResult {
    data class Launched(val target: LaunchTarget) : RouteResult
    data object Invalid : RouteResult
    data object NoHandler : RouteResult
}
