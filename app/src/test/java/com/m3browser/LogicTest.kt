package com.m3browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    @Test fun semver() {
        assertTrue(SemVer.isNewer("v1.0.2", "1.0.1"))
        assertTrue(SemVer.isNewer("1.0.10", "v1.0.2"))
        assertFalse(SemVer.isNewer("v1.0.2", "1.0.2"))
        assertFalse(SemVer.isNewer("v1.0.1", "1.0.2"))
        assertTrue(SemVer.same("v1.0.2", "1.0.2"))
    }

    @Test fun chromeInsetsKeepContentClearOfExpandedBar() {
        val expanded = chromeInsets(false, true, 200, 80, 48, 0, 16)
        assertEquals(0, expanded.viewPadBottom)
        assertEquals(80, expanded.jsTop)
        assertEquals(248, expanded.jsBottom)
        val collapsed = chromeInsets(false, false, 200, 80, 48, 0, 16)
        assertEquals(48 + 16, collapsed.jsBottom)
        assertTrue(collapsed.jsBottom < expanded.jsBottom)
    }

    @Test fun imeResizesTheViewInsteadOfDoublePadding() {
        val ime = chromeInsets(false, true, 180, 60, 40, 700, 12)
        assertEquals(700, ime.viewPadBottom)
        assertEquals(180, ime.jsBottom)
    }

    @Test fun topBarPadsTheTop() {
        val top = chromeInsets(true, true, 160, 50, 30, 0, 10)
        assertEquals(210, top.jsTop)
        assertEquals(30, top.jsBottom)
    }

    @Test fun expandRules() {
        assertTrue(shouldExpandBar(false, 40, atTop = false, atBottom = true, forceExpand = false))
        assertFalse(shouldExpandBar(true, 40, atTop = false, atBottom = false, forceExpand = false))
        assertTrue(shouldExpandBar(false, -20, atTop = false, atBottom = false, forceExpand = false))
        assertTrue(shouldExpandBar(false, 4, atTop = false, atBottom = false, forceExpand = true))
        assertTrue(shouldExpandBar(false, 30, atTop = true, atBottom = false, forceExpand = false))
        assertEquals(8, pillOverlapPx(40, 48))
        assertEquals(24, pillOverlapPx(40, 16))
    }

    @Test fun netscapeRoundTrip() {
        val html = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <DL><p>
              <DT><A HREF="https://example.com/">Example</A>
              <DT><H3>新闻</H3>
              <DL><p>
                <DT><A HREF="https://news.example/a">News &amp; Notes</A>
              </DL><p>
            </DL>
        """.trimIndent()
        val parsed = BookmarkHtml.parse(html)
        assertEquals(2, parsed.size)
        assertEquals("https://example.com/", parsed[0].url)
        assertEquals("", parsed[0].folder)
        assertEquals("新闻", parsed[1].folder)
        assertEquals("News & Notes", parsed[1].title)
        val again = BookmarkHtml.parse(BookmarkHtml.export(parsed))
        assertEquals(setOf("https://example.com/", "https://news.example/a"), again.map { it.url }.toSet())
    }

    @Test fun syncMergeLastWriterAndTombstone() {
        val older = PageEntry("A", "https://a", updatedAt = 1)
        val newer = PageEntry("B", "https://a", updatedAt = 2)
        val tomb = PageEntry("B", "https://a", updatedAt = 3, deleted = true)
        assertEquals("B", SyncMerge.mergeEntries(listOf(older), listOf(newer)).single().title)
        assertTrue(SyncMerge.mergeEntries(listOf(newer), listOf(tomb)).single().deleted)
        val tiedDelete = PageEntry("A", "https://a", updatedAt = 5, deleted = true)
        val tiedLive = PageEntry("A", "https://a", updatedAt = 5, deleted = false)
        assertTrue(SyncMerge.mergeEntries(listOf(tiedLive), listOf(tiedDelete)).single().deleted)
        val local = AppSettings(updatedAt = 10, blur = true)
        val remote = AppSettings(updatedAt = 11, blur = false)
        assertFalse(SyncMerge.mergeSettings(local, remote).blur)
        assertFalse(SyncMerge.mergeSettings(remote, local).blur)
        assertTrue(SyncMerge.mergeSettings(remote, local.copy(updatedAt = 20, blur = true)).blur)
    }

    @Test fun releaseApkName() {
        assertEquals(
            "Luna-v1.0.3.apk",
            pickReleaseApk(listOf("M3Browser-v1.0.2.apk", "Luna-v1.0.3.apk")),
        )
        assertEquals("M3Browser-v1.0.2.apk", pickReleaseApk(listOf("notes.txt", "M3Browser-v1.0.2.apk")))
    }

    @Test fun toolbarCap() {
        val many = ToolbarAction.CATALOG
        assertEquals(ToolbarAction.MAX_SLOTS, sanitizeToolbar(many).size)
        assertFalse(sanitizeToolbar(listOf(ToolbarAction.MENU, ToolbarAction.BACK)).contains(ToolbarAction.MENU))
        assertEquals(ToolbarAction.DEFAULT, sanitizeToolbar(emptyList()))
    }
}
