package com.goatfutbol.tv

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    private var onLogReceived: ((String) -> Unit)? = null
    private val logHistory = StringBuilder()

    fun setListener(listener: (String) -> Unit) {
        onLogReceived = listener
        // Send previous logs to new listener
        if (logHistory.isNotEmpty()) {
            listener(logHistory.toString())
        }
    }

    fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val formattedLog = "[$timestamp] $message\n"
        
        logHistory.insert(0, formattedLog)
        
        Handler(Looper.getMainLooper()).post {
            onLogReceived?.invoke(formattedLog)
        }
    }

    fun getLogs(): String = logHistory.toString()
}
