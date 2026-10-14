package com.orgista.openqr.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkRouterTest {
    @Test
    fun `accepts https urls`() {
        val value = LinkRouter.parse("https://example.com/path?q=1")

        assertEquals(LinkRouter.ParsedLink.Web("https://example.com/path?q=1"), value)
    }

    @Test
    fun `accepts http urls and trims whitespace`() {
        val value = LinkRouter.parse("  http://example.com/  \n")

        assertEquals(LinkRouter.ParsedLink.Web("http://example.com/"), value)
    }

    @Test
    fun `rejects unsupported schemes`() {
        assertNull(LinkRouter.parse("javascript:alert(1)"))
        assertNull(LinkRouter.parse("intent://example.com"))
        assertNull(LinkRouter.parse("file:///tmp/test"))
    }

    @Test
    fun `rejects every blocked scheme regardless of case`() {
        val blocked = listOf(
            "intent://scan/#Intent;scheme=zxing;end",
            "javascript:alert(1)",
            "file:///sdcard/x",
            "content://com.example/x",
            "data:text/html,hi",
            "about:blank",
            "blob:https://example.com/uuid",
            "chrome://settings",
            "android-app://com.example/https/example.com",
            "vbscript:msgbox",
            "filesystem:https://example.com/temporary/x",
            "ws://example.com/socket",
            "wss://example.com/socket"
        )
        blocked.forEach { payload ->
            assertNull(payload, LinkRouter.parse(payload))
            assertNull(payload, LinkRouter.parse(payload.uppercase()))
        }
        assertNull(LinkRouter.parse("JavaScript:alert(1)"))
        assertNull(LinkRouter.parse("InTeNt://example.com"))
    }

    @Test
    fun `rejects urls without hosts`() {
        assertNull(LinkRouter.parse("https:///missing-host"))
        assertNull(LinkRouter.parse("https://user@example.com"))
        assertNull(LinkRouter.parse("not-a-url"))
        assertNull(LinkRouter.parse(""))
        assertNull(LinkRouter.parse("   "))
    }

    @Test
    fun `accepts custom app schemes and lowercases the scheme`() {
        assertEquals(
            LinkRouter.ParsedLink.App("myapp://open/item?id=7"),
            LinkRouter.parse("MyApp://open/item?id=7")
        )
        assertEquals(
            LinkRouter.ParsedLink.App("x-app.v2+beta://go"),
            LinkRouter.parse("x-app.v2+beta://go")
        )
    }

    @Test
    fun `rejects malformed custom schemes`() {
        assertNull(LinkRouter.parse("1app://go"))
        assertNull(LinkRouter.parse("my app://go"))
        assertNull(LinkRouter.parse("myapp:"))
    }

    @Test
    fun `rejects data payloads that only look like a scheme`() {
        assertNull(LinkRouter.parse("WIFI:T:WPA;S:Guest;P:welcome123;;"))
        assertNull(LinkRouter.parse("BEGIN:VCARD\nFN:Alex\nEND:VCARD"))
        assertNull(LinkRouter.parse("MECARD:N:Alex;TEL:5551234;;"))
        assertNull(LinkRouter.parse("spotify:track:123"))
        assertNull(LinkRouter.parse("example.com:8080"))
    }

    @Test
    fun `detects only the four youtube hosts`() {
        assertTrue(LinkRouter.isYouTubeUrl("https://www.youtube.com/watch?v=abc"))
        assertTrue(LinkRouter.isYouTubeUrl("https://youtube.com/watch?v=abc"))
        assertTrue(LinkRouter.isYouTubeUrl("https://m.youtube.com/watch?v=abc"))
        assertTrue(LinkRouter.isYouTubeUrl("https://youtu.be/abc"))
        assertTrue(LinkRouter.isYouTubeUrl("http://WWW.YouTube.com/watch?v=abc"))
        assertFalse(LinkRouter.isYouTubeUrl("https://music.youtube.com/watch?v=abc"))
        assertFalse(LinkRouter.isYouTubeUrl("https://youtube.com.evil.example/watch"))
        assertFalse(LinkRouter.isYouTubeUrl("https://example.com/youtube.com"))
        assertFalse(LinkRouter.isYouTubeHost(null))
    }

    @Test
    fun `rewrites matterport discover links to the vr showcase`() {
        assertEquals(
            "https://my.matterport.com/show/?m=AbC123xyz&play=1&qs=1&vr=1",
            LinkRouter.rewrite("https://discover.matterport.com/space/AbC123xyz")
        )
        assertEquals(
            "https://my.matterport.com/show/?m=AbC123xyz&play=1&qs=1&vr=1",
            LinkRouter.rewrite("http://discover.matterport.com/space/AbC123xyz/")
        )
    }

    @Test
    fun `rewrites matterport show links and overrides forced params`() {
        assertEquals(
            "https://my.matterport.com/show/?m=AbC123xyz&play=1&qs=1&vr=1",
            LinkRouter.rewrite("https://my.matterport.com/show/?m=AbC123xyz")
        )
        assertEquals(
            "https://my.matterport.com/show/?m=AbC123xyz&brand=0&lang=en&play=1&qs=1&vr=1",
            LinkRouter.rewrite("https://my.matterport.com/show/?play=0&m=AbC123xyz&brand=0&vr=0&lang=en")
        )
        assertEquals(
            "https://my.matterport.com/show/?m=AbC123xyz&play=1&qs=1&vr=1",
            LinkRouter.rewrite("https://my.matterport.com/show?m=AbC123xyz&qs=0")
        )
    }

    @Test
    fun `leaves other links unchanged`() {
        val unchanged = listOf(
            "https://example.com/space/abc",
            "https://my.matterport.com/show/",
            "https://my.matterport.com/other?m=abc",
            "https://discover.matterport.com/",
            "https://discover.matterport.com/collections/abc",
            "https://www.youtube.com/watch?v=abc"
        )
        unchanged.forEach { url -> assertEquals(url, LinkRouter.rewrite(url)) }
    }

    @Test
    fun `lowercases scheme and host of all-caps web links`() {
        assertEquals(
            LinkRouter.ParsedLink.Web("https://example.com/PATH?Q=1"),
            LinkRouter.parse("HTTPS://EXAMPLE.COM/PATH?Q=1")
        )
    }

    @Test
    fun `flags matterport links as needing a vr tap`() {
        assertTrue(LinkRouter.needsVrTap("https://discover.matterport.com/space/xwwpd5uSw2B"))
        assertTrue(LinkRouter.needsVrTap("https://my.matterport.com/show/?m=xwwpd5uSw2B"))
        assertFalse(LinkRouter.needsVrTap("https://www.youtube.com/watch?v=O_gVu8IH6p4"))
        assertFalse(LinkRouter.needsVrTap("WIFI:S:x;;"))
    }

    @Test
    fun `normalizes meta store links to the form the store app claims`() {
        val canonical = "https://www.meta.com/experiences/5159227390778296/"
        assertEquals(canonical, LinkRouter.rewrite("https://www.meta.com/experiences/5159227390778296/"))
        assertEquals(canonical, LinkRouter.rewrite("https://www.meta.com/en-us/experiences/5159227390778296/"))
        assertEquals(canonical, LinkRouter.rewrite("https://meta.com/experiences/5159227390778296"))
        assertEquals(canonical, LinkRouter.rewrite("https://www.oculus.com/experiences/5159227390778296"))
        assertEquals("https://www.meta.com/experiences/app/123/", LinkRouter.rewrite("https://www.meta.com/experiences/app/123"))
        assertEquals("https://www.meta.com/quest/", LinkRouter.rewrite("https://www.meta.com/quest/"))
        assertTrue(LinkRouter.isMetaStoreUrl(canonical))
        assertFalse(LinkRouter.isMetaStoreUrl("https://www.meta.com/quest/"))
        assertFalse(LinkRouter.isMetaStoreUrl("https://evil.example/experiences/1"))
    }

    @Test
    fun `drops legacy platform segment and ignores case in store paths`() {
        val canonical = "https://www.meta.com/experiences/2532035600194083/"
        assertEquals(canonical, LinkRouter.rewrite("https://www.oculus.com/experiences/quest/2532035600194083/"))
        assertEquals(canonical, LinkRouter.rewrite("https://www.meta.com/EXPERIENCES/2532035600194083"))
        assertEquals("https://www.meta.com/experiences/app/123/", LinkRouter.rewrite("https://www.meta.com/experiences/app/123/"))
        assertEquals(canonical, LinkRouter.rewrite("https://www.meta.com/experiences/2532035600194083/#reviews"))
    }

    @Test
    fun `accepts unicode hosts by punycoding them`() {
        assertEquals(LinkRouter.ParsedLink.Web("https://xn--mnchen-3ya.de/"), LinkRouter.parse("https://münchen.de/"))
        assertEquals(LinkRouter.ParsedLink.Web("https://xn--mnchen-3ya.de:8443/a"), LinkRouter.parse("https://münchen.de:8443/a"))
        assertNull(LinkRouter.parse("https://user@münchen.de/"))
    }

    @Test
    fun `encodes the matterport id`() {
        assertEquals(
            "https://my.matterport.com/show/?m=abc%26vr%3D0&play=1&qs=1&vr=1",
            LinkRouter.rewrite("https://discover.matterport.com/space/abc&vr=0")
        )
    }
}
