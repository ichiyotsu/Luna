package com.m3browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {
    @Test fun urls() {
        assertEquals("https://example.com", UrlUtils.toUrl("example.com", SearchEngine.BING))
        assertEquals("https://a.b.cn/x?y=1", UrlUtils.toUrl(" a.b.cn/x?y=1 ", SearchEngine.BING))
        assertEquals("http://x.org", UrlUtils.toUrl("http://x.org", SearchEngine.GOOGLE))
        assertEquals("https://localhost:8080", UrlUtils.toUrl("localhost:8080", SearchEngine.GOOGLE))
    }
    @Test fun search() {
        assertTrue(UrlUtils.toUrl("hello world", SearchEngine.BING).startsWith("https://www.bing.com/search?q=hello+world"))
        assertTrue(UrlUtils.toUrl("天气", SearchEngine.GOOGLE).startsWith("https://www.google.com/search?q=%E5"))
        assertTrue(UrlUtils.toUrl("kotlin", SearchEngine.BAIDU).startsWith("https://www.baidu.com/s?wd=kotlin"))
    }
    @Test fun schemes() {
        assertTrue(UrlUtils.isWebScheme("https://a"))
        assertTrue(!UrlUtils.isWebScheme("intent://x#Intent;end"))
        assertEquals("bing.com", UrlUtils.displayHost("https://www.bing.com/search"))
    }
}
