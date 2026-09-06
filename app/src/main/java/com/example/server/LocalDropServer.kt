package com.example.server

import android.content.Context
import android.os.Build
import com.example.model.ClientInfo
import com.example.model.ServerConfig
import com.example.model.SharedFile
import com.example.model.TransferItem
import com.example.model.TransferStatus
import com.example.model.TransferType
import com.example.utils.FileUtils
import com.example.utils.NetworkUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LocalDropServer(
    private val context: Context,
    var config: ServerConfig,
    private val listener: ServerListener
) {

    interface ServerListener {
        fun onServerStarted(port: Int)
        fun onServerStopped()
        fun onServerError(error: String)
        fun onClientConnected(client: ClientInfo)
        fun onTransferStarted(item: TransferItem)
        fun onTransferProgress(item: TransferItem)
        fun onTransferCompleted(item: TransferItem)
        fun onTransferFailed(item: TransferItem)
    }

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()
    private val validTokens = ConcurrentHashMap.newKeySet<String>()
    private val knownClients = ConcurrentHashMap<String, ClientInfo>()

    val port: Int
        get() = config.port

    fun isServerRunning(): Boolean = isRunning.get()

    fun start() {
        if (isRunning.get()) return

        executor.execute {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(config.port))
                }
                isRunning.set(true)
                listener.onServerStarted(config.port)

                while (isRunning.get() && serverSocket?.isClosed == false) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        executor.execute {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isRunning.set(false)
                listener.onServerError("Failed to start server on port ${config.port}: ${e.message}")
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        listener.onServerStopped()
    }

    private fun handleClient(socket: Socket) {
        val clientIp = socket.inetAddress?.hostAddress ?: "unknown"
        try {
            socket.soTimeout = 30000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val rawUri = parts[1]

            // Parse headers
            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null && !line.isNullOrEmpty()) {
                val idx = line!!.indexOf(':')
                if (idx > 0) {
                    val key = line!!.substring(0, idx).trim().lowercase()
                    val value = line!!.substring(idx + 1).trim()
                    headers[key] = value
                }
            }

            // Client tracking
            val userAgent = headers["user-agent"] ?: "Unknown Browser"
            trackClient(clientIp, userAgent)

            // URI & Query parsing
            val queryIdx = rawUri.indexOf('?')
            val path = if (queryIdx >= 0) rawUri.substring(0, queryIdx) else rawUri
            val queryString = if (queryIdx >= 0) rawUri.substring(queryIdx + 1) else ""
            val queryParams = parseQueryParams(queryString)

            // PIN Authentication check
            if (config.pinRequired && path.startsWith("/api/") && path != "/api/auth" && path != "/api/status") {
                val token = headers["x-auth-token"] ?: queryParams["token"]
                val pin = queryParams["pin"]
                val isAuthed = (token != null && validTokens.contains(token)) || (pin != null && pin == config.pinCode)
                if (!isAuthed) {
                    sendJsonResponse(output, 401, JSONObject().put("error", "Unauthorized").put("message", "PIN required"))
                    return
                }
            }

            when {
                // Serve Web Client Single Page App
                method == "GET" && (path == "/" || path == "/index.html") -> {
                    val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                    val html = WebClientHtml.buildHtml(context, deviceName, config.port, config.pinRequired)
                    sendHtmlResponse(output, 200, html)
                }

                // Auth endpoint
                method == "POST" && path == "/api/auth" -> {
                    handleAuthRequest(reader, headers, output)
                }

                // Server Status endpoint
                method == "GET" && path == "/api/status" -> {
                    handleStatusRequest(output)
                }

                // Files Listing
                method == "GET" && path == "/api/files" -> {
                    val folder = queryParams["folder"] ?: ""
                    handleListFiles(folder, output)
                }

                // Download Single File
                method == "GET" && path == "/api/download" -> {
                    val relPath = queryParams["file"] ?: ""
                    handleDownloadFile(relPath, clientIp, headers, output, asAttachment = true)
                }

                // Preview File
                method == "GET" && path == "/api/preview" -> {
                    val relPath = queryParams["file"] ?: ""
                    handleDownloadFile(relPath, clientIp, headers, output, asAttachment = false)
                }

                // Download ZIP (folder or multiple files)
                method == "GET" && path == "/api/download-zip" -> {
                    handleDownloadZip(queryParams, clientIp, output)
                }

                // Upload File
                method == "POST" && path == "/api/upload" -> {
                    val uploadPath = queryParams["path"] ?: "upload_${System.currentTimeMillis()}"
                    val contentLength = headers["content-length"]?.toLongOrNull() ?: -1L
                    handleUploadFile(uploadPath, contentLength, clientIp, input, output)
                }

                // Delete File or Folder
                method == "POST" && path == "/api/delete" -> {
                    handleDelete(reader, headers, output)
                }

                // Create Directory
                method == "POST" && path == "/api/mkdir" -> {
                    handleMkdir(reader, headers, output)
                }

                // Favicon
                method == "GET" && path == "/favicon.ico" -> {
                    val emptySvg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><text y='20' font-size='20'>📦</text></svg>"
                    sendResponse(output, 200, "image/svg+xml", emptySvg.toByteArray(StandardCharsets.UTF_8))
                }

                else -> {
                    sendJsonResponse(output, 404, JSONObject().put("error", "Not Found"))
                }
            }
        } catch (e: Exception) {
            // Socket or connection closed
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun trackClient(clientIp: String, userAgent: String) {
        val now = System.currentTimeMillis()
        val deviceType = when {
            userAgent.contains("iPhone", true) -> "iPhone"
            userAgent.contains("iPad", true) -> "iPad"
            userAgent.contains("Android", true) -> "Android Device"
            userAgent.contains("Windows", true) -> "Windows PC"
            userAgent.contains("Macintosh", true) -> "Mac"
            userAgent.contains("Linux", true) -> "Linux PC"
            userAgent.contains("CrOS", true) -> "Chromebook"
            else -> "Web Browser"
        }

        val existing = knownClients[clientIp]
        val updated = if (existing != null) {
            existing.copy(lastSeen = now, requestsCount = existing.requestsCount + 1, userAgent = userAgent)
        } else {
            ClientInfo(
                ip = clientIp,
                userAgent = userAgent,
                deviceName = deviceType,
                firstSeen = now,
                lastSeen = now,
                requestsCount = 1
            )
        }
        knownClients[clientIp] = updated
        listener.onClientConnected(updated)
    }

    private fun handleAuthRequest(reader: BufferedReader, headers: Map<String, String>, output: OutputStream) {
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val body = readBody(reader, contentLength)
        val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
        val enteredPin = json.optString("pin", "")

        if (!config.pinRequired || enteredPin == config.pinCode) {
            val token = UUID.randomUUID().toString()
            validTokens.add(token)
            sendJsonResponse(output, 200, JSONObject().put("success", true).put("token", token))
        } else {
            sendJsonResponse(output, 403, JSONObject().put("success", false).put("message", "Incorrect PIN code"))
        }
    }

    private fun handleStatusRequest(output: OutputStream) {
        val storageDir = FileUtils.getStorageDir(context)
        val freeBytes = storageDir.freeSpace
        val totalBytes = storageDir.totalSpace
        val usedByLocalDrop = FileUtils.getFolderSize(storageDir)

        val json = JSONObject().apply {
            put("status", "running")
            put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("pinRequired", config.pinRequired)
            put("freeSpace", freeBytes)
            put("totalSpace", totalBytes)
            put("localDropUsed", usedByLocalDrop)
            put("connectedClients", knownClients.size)
        }
        sendJsonResponse(output, 200, json)
    }

    private fun handleListFiles(folder: String, output: OutputStream) {
        val rootDir = FileUtils.getStorageDir(context)
        val files = FileUtils.listSharedFiles(rootDir, folder)

        val array = JSONArray()
        for (f in files) {
            val item = JSONObject().apply {
                put("name", f.name)
                put("relativePath", f.relativePath)
                put("size", f.size)
                put("lastModified", f.lastModified)
                put("isDirectory", f.isDirectory)
                put("extension", f.extension)
                put("mimeType", f.mimeType)
            }
            array.put(item)
        }
        val bytes = array.toString().toByteArray(StandardCharsets.UTF_8)
        sendResponse(output, 200, "application/json; charset=utf-8", bytes)
    }

    private fun handleDownloadFile(
        relPath: String,
        clientIp: String,
        headers: Map<String, String>,
        output: OutputStream,
        asAttachment: Boolean
    ) {
        val rootDir = FileUtils.getStorageDir(context)
        val file = FileUtils.resolveSafeFile(rootDir, relPath)

        if (!file.exists() || file.isDirectory) {
            sendJsonResponse(output, 404, JSONObject().put("error", "File not found"))
            return
        }

        val totalLength = file.length()
        val rangeHeader = headers["range"]
        var start = 0L
        var end = totalLength - 1
        var isPartial = false

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val range = rangeHeader.substring(6).split("-")
            val rStart = range.getOrNull(0)?.toLongOrNull()
            val rEnd = range.getOrNull(1)?.toLongOrNull()
            if (rStart != null) {
                start = rStart
                if (rEnd != null && rEnd < totalLength) {
                    end = rEnd
                }
                isPartial = true
            }
        }

        val contentLength = end - start + 1
        val mimeType = FileUtils.getMimeType(file)
        val transferId = UUID.randomUUID().toString()
        var transferItem = TransferItem(
            id = transferId,
            fileName = file.name,
            fileSize = totalLength,
            bytesTransferred = start,
            type = TransferType.DOWNLOAD,
            status = TransferStatus.IN_PROGRESS,
            clientIp = clientIp
        )
        listener.onTransferStarted(transferItem)

        // Write HTTP Headers
        val headerBuilder = StringBuilder()
        headerBuilder.append(if (isPartial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
        headerBuilder.append("Content-Type: $mimeType\r\n")
        headerBuilder.append("Content-Length: $contentLength\r\n")
        headerBuilder.append("Accept-Ranges: bytes\r\n")
        headerBuilder.append("Access-Control-Allow-Origin: *\r\n")
        if (asAttachment) {
            headerBuilder.append("Content-Disposition: attachment; filename=\"${file.name}\"\r\n")
        } else {
            headerBuilder.append("Content-Disposition: inline; filename=\"${file.name}\"\r\n")
        }
        if (isPartial) {
            headerBuilder.append("Content-Range: bytes $start-$end/$totalLength\r\n")
        }
        headerBuilder.append("Connection: close\r\n\r\n")

        output.write(headerBuilder.toString().toByteArray(StandardCharsets.UTF_8))
        output.flush()

        // Stream file content
        var transferred = start
        val buffer = ByteArray(64 * 1024)
        var lastUpdate = System.currentTimeMillis()
        var lastTransferred = transferred

        try {
            FileInputStream(file).use { fis ->
                if (start > 0) {
                    fis.skip(start)
                }
                var bytesToRead = contentLength
                while (bytesToRead > 0) {
                    val readSize = minOf(buffer.size.toLong(), bytesToRead).toInt()
                    val count = fis.read(buffer, 0, readSize)
                    if (count == -1) break
                    output.write(buffer, 0, count)
                    transferred += count
                    bytesToRead -= count

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate >= 300) {
                        val diffTime = (now - lastUpdate) / 1000.0
                        val diffBytes = transferred - lastTransferred
                        val speed = if (diffTime > 0) (diffBytes / diffTime).toLong() else 0L

                        transferItem = transferItem.copy(
                            bytesTransferred = transferred,
                            speedBytesPerSec = speed
                        )
                        listener.onTransferProgress(transferItem)
                        lastUpdate = now
                        lastTransferred = transferred
                    }
                }
            }
            output.flush()
            transferItem = transferItem.copy(
                bytesTransferred = transferred,
                status = TransferStatus.COMPLETED
            )
            listener.onTransferCompleted(transferItem)
        } catch (e: Exception) {
            transferItem = transferItem.copy(
                status = TransferStatus.FAILED,
                errorMessage = e.message
            )
            listener.onTransferFailed(transferItem)
        }
    }

    private fun handleDownloadZip(queryParams: Map<String, String>, clientIp: String, output: OutputStream) {
        val rootDir = FileUtils.getStorageDir(context)
        val folderParam = queryParams["folder"]
        val filesParam = queryParams["files"]

        val filesToZip = mutableListOf<File>()
        val zipName: String

        if (!folderParam.isNullOrBlank()) {
            val targetFolder = FileUtils.resolveSafeFile(rootDir, folderParam)
            if (targetFolder.exists()) {
                filesToZip.add(targetFolder)
                zipName = "${targetFolder.name}.zip"
            } else {
                sendJsonResponse(output, 404, JSONObject().put("error", "Folder not found"))
                return
            }
        } else if (!filesParam.isNullOrBlank()) {
            val paths = filesParam.split(",")
            for (p in paths) {
                val f = FileUtils.resolveSafeFile(rootDir, p.trim())
                if (f.exists()) filesToZip.add(f)
            }
            zipName = "LocalDrop_Bundle_${System.currentTimeMillis() / 1000}.zip"
        } else {
            sendJsonResponse(output, 400, JSONObject().put("error", "No files specified"))
            return
        }

        val transferId = UUID.randomUUID().toString()
        var transferItem = TransferItem(
            id = transferId,
            fileName = zipName,
            fileSize = 0L,
            bytesTransferred = 0L,
            type = TransferType.DOWNLOAD,
            status = TransferStatus.IN_PROGRESS,
            clientIp = clientIp
        )
        listener.onTransferStarted(transferItem)

        val headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/zip\r\n" +
                "Content-Disposition: attachment; filename=\"$zipName\"\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(StandardCharsets.UTF_8))
        output.flush()

        try {
            // Write ZIP streamed
            val chunkedOutput = ChunkedOutputStream(output)
            var lastUpdate = System.currentTimeMillis()
            var lastBytes = 0L

            FileUtils.writeZipStream(filesToZip, chunkedOutput) { written ->
                val now = System.currentTimeMillis()
                if (now - lastUpdate >= 300) {
                    val diff = (now - lastUpdate) / 1000.0
                    val speed = if (diff > 0) ((written - lastBytes) / diff).toLong() else 0L
                    transferItem = transferItem.copy(
                        bytesTransferred = written,
                        speedBytesPerSec = speed
                    )
                    listener.onTransferProgress(transferItem)
                    lastUpdate = now
                    lastBytes = written
                }
            }
            chunkedOutput.finish()
            output.flush()

            transferItem = transferItem.copy(status = TransferStatus.COMPLETED)
            listener.onTransferCompleted(transferItem)
        } catch (e: Exception) {
            transferItem = transferItem.copy(status = TransferStatus.FAILED, errorMessage = e.message)
            listener.onTransferFailed(transferItem)
        }
    }

    private fun handleUploadFile(
        uploadPath: String,
        contentLength: Long,
        clientIp: String,
        input: InputStream,
        output: OutputStream
    ) {
        val rootDir = FileUtils.getStorageDir(context)
        val targetFile = FileUtils.resolveSafeFile(rootDir, uploadPath)
        targetFile.parentFile?.mkdirs()

        val transferId = UUID.randomUUID().toString()
        var transferItem = TransferItem(
            id = transferId,
            fileName = targetFile.name,
            fileSize = contentLength,
            bytesTransferred = 0L,
            type = TransferType.UPLOAD,
            status = TransferStatus.IN_PROGRESS,
            clientIp = clientIp
        )
        listener.onTransferStarted(transferItem)

        var transferred = 0L
        val buffer = ByteArray(64 * 1024)
        var lastUpdate = System.currentTimeMillis()
        var lastTransferred = 0L

        try {
            FileOutputStream(targetFile).use { fos ->
                var remaining = if (contentLength > 0) contentLength else Long.MAX_VALUE
                while (remaining > 0) {
                    val toRead = if (contentLength > 0) minOf(buffer.size.toLong(), remaining).toInt() else buffer.size
                    val count = input.read(buffer, 0, toRead)
                    if (count == -1) break
                    fos.write(buffer, 0, count)
                    transferred += count
                    if (contentLength > 0) remaining -= count

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate >= 300) {
                        val diff = (now - lastUpdate) / 1000.0
                        val speed = if (diff > 0) ((transferred - lastTransferred) / diff).toLong() else 0L
                        transferItem = transferItem.copy(
                            bytesTransferred = transferred,
                            speedBytesPerSec = speed
                        )
                        listener.onTransferProgress(transferItem)
                        lastUpdate = now
                        lastTransferred = transferred
                    }
                }
            }

            transferItem = transferItem.copy(
                fileSize = transferred,
                bytesTransferred = transferred,
                status = TransferStatus.COMPLETED
            )
            listener.onTransferCompleted(transferItem)

            sendJsonResponse(output, 200, JSONObject().put("success", true).put("fileName", targetFile.name))
        } catch (e: Exception) {
            transferItem = transferItem.copy(status = TransferStatus.FAILED, errorMessage = e.message)
            listener.onTransferFailed(transferItem)
            sendJsonResponse(output, 500, JSONObject().put("success", false).put("error", e.message))
        }
    }

    private fun handleDelete(reader: BufferedReader, headers: Map<String, String>, output: OutputStream) {
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val body = readBody(reader, contentLength)
        val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
        val path = json.optString("path", "")

        val rootDir = FileUtils.getStorageDir(context)
        val file = FileUtils.resolveSafeFile(rootDir, path)

        if (file == rootDir || !file.exists()) {
            sendJsonResponse(output, 400, JSONObject().put("success", false).put("message", "Cannot delete root or non-existent file"))
            return
        }

        val success = file.deleteRecursively()
        sendJsonResponse(output, 200, JSONObject().put("success", success))
    }

    private fun handleMkdir(reader: BufferedReader, headers: Map<String, String>, output: OutputStream) {
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val body = readBody(reader, contentLength)
        val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
        val folder = json.optString("folder", "")
        val name = json.optString("name", "").trim()

        if (name.isEmpty() || name.contains("/") || name.contains("\\")) {
            sendJsonResponse(output, 400, JSONObject().put("success", false).put("message", "Invalid folder name"))
            return
        }

        val rootDir = FileUtils.getStorageDir(context)
        val parent = FileUtils.resolveSafeFile(rootDir, folder)
        val newFolder = File(parent, name)
        val created = newFolder.mkdirs()
        sendJsonResponse(output, 200, JSONObject().put("success", created))
    }

    private fun readBody(reader: BufferedReader, length: Int): String {
        if (length <= 0) return ""
        val chars = CharArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val read = reader.read(chars, totalRead, length - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return String(chars, 0, totalRead)
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                result[k] = v
            }
        }
        return result
    }

    private fun sendHtmlResponse(output: OutputStream, statusCode: Int, html: String) {
        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        sendResponse(output, statusCode, "text/html; charset=utf-8", bytes)
    }

    private fun sendJsonResponse(output: OutputStream, statusCode: Int, json: JSONObject) {
        val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
        sendResponse(output, statusCode, "application/json; charset=utf-8", bytes)
    }

    private fun sendResponse(output: OutputStream, statusCode: Int, contentType: String, body: ByteArray) {
        val statusMsg = when (statusCode) {
            200 -> "OK"
            206 -> "Partial Content"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            else -> "OK"
        }
        val header = "HTTP/1.1 $statusCode $statusMsg\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(body)
        output.flush()
    }

    /**
     * Chunked transfer output stream for dynamic zip streaming without Content-Length
     */
    private class ChunkedOutputStream(private val out: OutputStream) : OutputStream() {
        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()), 0, 1)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (len <= 0) return
            val hexLen = Integer.toHexString(len) + "\r\n"
            out.write(hexLen.toByteArray(StandardCharsets.US_ASCII))
            out.write(b, off, len)
            out.write("\r\n".toByteArray(StandardCharsets.US_ASCII))
        }

        fun finish() {
            out.write("0\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
            out.flush()
        }
    }
}
