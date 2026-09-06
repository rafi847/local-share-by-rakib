package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ServerConfig
import com.example.utils.FileUtils
import com.example.utils.NetworkUtils
import com.example.utils.QrCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LocalDrop", appName)
    }

    @Test
    fun `format bytes test`() {
        assertEquals("0 B", NetworkUtils.formatBytes(0))
        assertEquals("100 B", NetworkUtils.formatBytes(100))
        assertEquals("1 KB", NetworkUtils.formatBytes(1024))
        assertEquals("1.5 MB", NetworkUtils.formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("2 GB", NetworkUtils.formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `format speed test`() {
        assertEquals("0 KB/s", NetworkUtils.formatSpeed(0))
        assertEquals("500 KB/s", NetworkUtils.formatSpeed(500 * 1024))
        assertEquals("12.5 MB/s", NetworkUtils.formatSpeed((12.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun `path traversal prevention test`() {
        val baseDir = File("/data/user/0/com.example/files/LocalDrop")
        val safeFile = FileUtils.resolveSafeFile(baseDir, "photos/vacation.jpg")
        assertTrue(safeFile.canonicalPath.startsWith(baseDir.canonicalPath))

        // Malicious traversal attempt
        val maliciousAttempt = FileUtils.resolveSafeFile(baseDir, "../../etc/passwd")
        // Must stay inside baseDir
        assertTrue(maliciousAttempt.canonicalPath.startsWith(baseDir.canonicalPath))
    }

    @Test
    fun `qr code generation test`() {
        val bitmap = QrCodeGenerator.generateQrBitmap("http://192.168.1.100:8080", 200)
        assertNotNull(bitmap)
        assertEquals(200, bitmap?.width)
        assertEquals(200, bitmap?.height)
    }

    @Test
    fun `server config defaults test`() {
        val config = ServerConfig()
        assertEquals(8080, config.port)
        assertTrue(config.pinRequired)
        assertEquals(4, config.pinCode.length)
    }
}
