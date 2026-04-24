package com.beckersuite.box

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.JavascriptInterface

class VibrateBridge(context: Context) {

    private val vibrator: Vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /**
     * Called from JS via window.AndroidVibrate.vibrate(pattern)
     *
     * pattern is a JSON string — either a number "50" or an array "[100,50,100]"
     * matching the exact shape of the navigator.vibrate() API.
     */
    @JavascriptInterface
    fun vibrate(patternJson: String) {
        try {
            val trimmed = patternJson.trim()
            if (trimmed.startsWith("[")) {
                // Array pattern: [vibrate, pause, vibrate, ...]
                val values = trimmed
                    .removePrefix("[").removeSuffix("]")
                    .split(",")
                    .map { it.trim().toLong() }
                    .toLongArray()
                vibrator.vibrate(VibrationEffect.createWaveform(values, -1))
            } else {
                // Single duration
                val ms = trimmed.toLong()
                if (ms > 0) vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
            // Malformed input — silently ignore, same behaviour as browser
        }
    }
}