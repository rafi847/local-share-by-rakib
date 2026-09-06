package com.example.utils

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.example.model.SharedFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileUtils {

    const val LOCAL_DROP_DIR_NAME = "LocalDrop"

    fun getStorageDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dropDir = File(base, LOCAL_DROP_DIR_NAME)
        if (!dropDir.exists()) {
            dropDir.mkdirs()
            createDefaultWelcomeFiles(dropDir)
        }
        return dropDir
    }

    private fun createDefaultWelcomeFiles(dir: File) {
        try {
            val welcomeFile = File(dir, "Welcome_to_LocalDrop.txt")
            if (!welcomeFile.exists()) {
                welcomeFile.writeText(
                    """
                    ========================================
                          Welcome to LocalDrop File Server!
                    ========================================
                    
                    LocalDrop allows instant, blazing-fast Wi-Fi
                    file transfers between your Android phone and PC,
                    Mac, Linux, tablet, or another phone.
                    
                    Key Features:
                    - 100% Offline (No Internet access required)
                    - High-speed direct LAN transfer over Wi-Fi
                    - Drag-and-drop file upload from PC browser
                    - Instant folder download as ZIP
                    - PIN authentication and QR code pairing
                    
                    You can drag and drop any file from your computer
                    right onto the web page to upload it to this phone!
                    
                    Enjoy seamless local file sharing!
                    """.trimIndent()
                )
            }

            val notesFolder = File(dir, "Sample_Documents")
            if (!notesFolder.exists()) {
                notesFolder.mkdirs()
                File(notesFolder, "Quick_Tips.txt").writeText(
                    """
                    LocalDrop Quick Tips:
                    1. Connect your PC and Phone to the same Wi-Fi network.
                    2. If no Wi-Fi router is available, turn on your Phone's
                       Portable Hotspot and connect your PC to it.
                    3. Open the displayed URL (e.g. http://192.168.x.x:8080)
                       in any browser (Chrome, Edge, Firefox, Safari).
                    4. Transfer large videos, music, photos, or archives with
                       maximum local network speed!
                    """.trimIndent()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun listSharedFiles(rootDir: File, subPath: String = ""): List<SharedFile> {
        val targetDir = resolveSafeFile(rootDir, subPath)
        if (!targetDir.exists() || !targetDir.isDirectory) return emptyList()

        val files = targetDir.listFiles() ?: return emptyList()
        return files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).map { file ->
            val relPath = file.relativeTo(rootDir).path.replace('\\', '/')
            val ext = if (file.isDirectory) "" else file.extension.lowercase()
            val mime = getMimeType(file)

            SharedFile(
                name = file.name,
                relativePath = relPath,
                size = if (file.isDirectory) getFolderSize(file) else file.length(),
                lastModified = file.lastModified(),
                isDirectory = file.isDirectory,
                extension = ext,
                mimeType = mime
            )
        }
    }

    fun resolveSafeFile(rootDir: File, relativePath: String): File {
        val cleanPath = relativePath.trim().replace('\\', '/').trimStart('/')
        val file = File(rootDir, cleanPath).canonicalFile
        val rootCanonical = rootDir.canonicalFile

        // Path traversal guard
        if (!file.path.startsWith(rootCanonical.path)) {
            return rootCanonical
        }
        return file
    }

    fun getFolderSize(folder: File): Long {
        var size = 0L
        val files = folder.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) getFolderSize(f) else f.length()
        }
        return size
    }

    fun getMimeType(file: File): String {
        if (file.isDirectory) return "inode/directory"
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "json" -> "application/json"
            "md" -> "text/markdown"
            "txt" -> "text/plain"
            "log" -> "text/plain"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "tar", "gz" -> "application/gzip"
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }
    }

    fun writeZipStream(filesToZip: List<File>, outputStream: OutputStream, onProgress: ((bytesWritten: Long) -> Unit)? = null) {
        val zipOut = ZipOutputStream(outputStream)
        val buffer = ByteArray(64 * 1024)
        var totalBytes = 0L

        fun addFileToZip(file: File, baseName: String) {
            if (file.isDirectory) {
                val children = file.listFiles() ?: return
                val dirEntryName = if (baseName.endsWith("/")) baseName else "$baseName/"
                zipOut.putNextEntry(ZipEntry(dirEntryName))
                zipOut.closeEntry()
                for (child in children) {
                    addFileToZip(child, "$dirEntryName${child.name}")
                }
            } else {
                val entry = ZipEntry(baseName)
                zipOut.putNextEntry(entry)
                FileInputStream(file).use { input ->
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        zipOut.write(buffer, 0, count)
                        totalBytes += count
                        onProgress?.invoke(totalBytes)
                    }
                }
                zipOut.closeEntry()
            }
        }

        for (file in filesToZip) {
            if (file.exists()) {
                addFileToZip(file, file.name)
            }
        }
        zipOut.finish()
        zipOut.flush()
    }

    fun copyUriToLocalDrop(context: Context, uri: Uri, targetFolder: File): File? {
        return try {
            val contentResolver = context.contentResolver
            var fileName = "shared_${System.currentTimeMillis()}"

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val displayName = cursor.getString(nameIndex)
                    if (!displayName.isNullOrBlank()) {
                        fileName = displayName
                    }
                }
            }

            var dest = File(targetFolder, fileName)
            var counter = 1
            val baseName = dest.nameWithoutExtension
            val ext = if (dest.extension.isNotEmpty()) ".${dest.extension}" else ""
            while (dest.exists()) {
                dest = File(targetFolder, "${baseName}_$counter$ext")
                counter++
            }

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            dest
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
