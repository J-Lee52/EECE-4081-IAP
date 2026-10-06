package com.example.eece4081_iap.ui

import kotlin.time.Duration

/**
 * Shows a duration in seconds to two decimals, for example "1.35 s".
 * Two decimals is the 0.01 s precision of NFR 01. Extra precision is cut
 * off, not rounded, so the screen never shows a time later than the real one.
 */
fun Duration.toSecondsText(): String {
    val hundredths = inWholeMilliseconds / 10
    val whole = hundredths / 100
    val fraction = (hundredths % 100).toString().padStart(2, '0')
    return "$whole.$fraction s"
}
