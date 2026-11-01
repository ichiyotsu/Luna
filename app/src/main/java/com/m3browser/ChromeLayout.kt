package com.m3browser

/**
 * How the fullscreen WebView shares the screen with the unified bar.
 *
 * The WebView itself stays edge-to-edge (so the page can draw behind the status bar,
 * the gesture area, and the frosted bar). [jsTop] / [jsBottom] are Android px injected
 * into the page as padding, plus a fixed/sticky-element lift, so the last content and
 * bottom-fixed footers can scroll clear of the bar. [viewPadBottom] is a real view
 * resize used only for the IME, which WebView cannot see as a CSS inset by itself.
 */
data class ChromeInsets(
    val viewPadBottom: Int,
    val jsTop: Int,
    val jsBottom: Int,
)

fun chromeInsets(
    barAtTop: Boolean,
    expanded: Boolean,
    barPx: Int,
    statusPx: Int,
    navPx: Int,
    imePx: Int,
    pillOverlapPx: Int,
): ChromeInsets {
    val imePad = if (imePx > 0) imePx else 0
    // When the keyboard is open the view ends above it, so the nav inset is not inside the view.
    val navInView = if (imePad > 0) 0 else navPx.coerceAtLeast(0)
    val chrome = if (expanded) barPx.coerceAtLeast(0) else pillOverlapPx.coerceAtLeast(0)
    return if (barAtTop) {
        ChromeInsets(
            viewPadBottom = imePad,
            jsTop = statusPx.coerceAtLeast(0) + chrome,
            jsBottom = navInView,
        )
    } else {
        ChromeInsets(
            viewPadBottom = imePad,
            jsTop = statusPx.coerceAtLeast(0),
            jsBottom = navInView + chrome,
        )
    }
}

/**
 * Collapse while scrolling down through the page. Stay expanded at the top, at the
 * very bottom, while editing, or when a menu/keyboard needs the full bar.
 * Small deltas keep the current state so the bar does not flicker.
 */
fun shouldExpandBar(
    expanded: Boolean,
    dy: Int,
    atTop: Boolean,
    atBottom: Boolean,
    forceExpand: Boolean,
): Boolean {
    if (forceExpand || atTop || atBottom) return true
    if (dy <= -16) return true
    if (dy >= 24) return false
    return expanded
}

/** How far a collapsed pill of [pillPx] should overlap page content, given the nav inset. */
fun pillOverlapPx(pillPx: Int, navPx: Int): Int {
    val shiftIntoNav = minOf((pillPx - 8).coerceAtLeast(0), navPx.coerceAtLeast(0))
    return (pillPx - shiftIntoNav).coerceIn(8, pillPx.coerceAtLeast(8))
}
