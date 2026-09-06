package com.example.server

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object WebClientHtml {

    private var cachedTemplate: String? = null

    fun buildHtml(context: Context, deviceName: String, port: Int, pinRequired: Boolean): String {
        val template = cachedTemplate ?: try {
            context.assets.open("web/index.html").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }.also { cachedTemplate = it }
        } catch (e: Exception) {
            fallbackHtml(deviceName, port, pinRequired)
        }

        return template
            .replace("{{DEVICE_NAME}}", deviceName)
            .replace("{{PORT}}", port.toString())
            .replace("{{PIN_REQUIRED}}", pinRequired.toString())
    }

    private fun fallbackHtml(deviceName: String, port: Int, pinRequired: Boolean): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <title>LocalDrop</title>
              <style>
                body { background: #0b132b; color: #fff; font-family: sans-serif; text-align: center; padding: 50px; }
                h1 { color: #00e5ff; }
              </style>
            </head>
            <body>
              <h1>LocalDrop</h1>
              <p>Device: $deviceName</p>
              <p>Port: $port</p>
              <p>Server is running!</p>
            </body>
            </html>
        """.trimIndent()
    }
}
