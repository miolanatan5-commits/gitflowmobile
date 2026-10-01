package com.natam.gitflowmobile

import android.util.Log

/**
 * The app's single logger.
 *
 * Every event is one line under one tag: a constant event name followed by
 * `key=value` fields, so a session can be filtered with
 * `adb logcat -s GitFlowMobile` and grouped by event name.
 *
 * Dynamic values go in the fields, never interpolated into the event name.
 * Never pass a token, a personal access token, or a search term as a field.
 */
object AppLog {

    const val TAG = "GitFlowMobile"

    fun info(event: String, vararg fields: Pair<String, Any?>) {
        Log.i(TAG, line(event, fields))
    }

    fun error(event: String, error: Throwable? = null, vararg fields: Pair<String, Any?>) {
        Log.e(TAG, line(event, fields), error)
    }

    private fun line(event: String, fields: Array<out Pair<String, Any?>>): String {
        val builder = StringBuilder(event)
        for ((key, value) in fields) {
            val text = value?.toString().orEmpty()
            if (text.isEmpty()) continue
            builder.append(' ').append(key).append('=').append(text)
        }
        return builder.toString()
    }
}
