package com.example.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.DecimalFormat
import java.util.Collections
import kotlin.math.log10
import kotlin.math.pow

object NetworkUtils {

    data class NetworkInfo(
        val ipAddress: String?,
        val interfaceName: String?,
        val isWifi: Boolean,
        val isHotspotOrOther: Boolean,
        val ssid: String?
    )

    /**
     * Finds the most likely active local LAN IPv4 address (e.g., 192.168.x.x, 10.x.x.x, 172.x.x.x).
     * Prefers wlan0, ap0, or interfaces that start with wlan/ap.
     */
    fun getLocalIpAddress(context: Context): NetworkInfo {
        var wifiIp: String? = null
        var apIp: String? = null
        var fallbackIp: String? = null
        var activeInterface = "wlan0"

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("127.")) continue

                        val name = intf.name.lowercase()
                        when {
                            name.startsWith("wlan") -> {
                                wifiIp = host
                                activeInterface = intf.name
                            }
                            name.startsWith("ap") || name.startsWith("softap") || name.startsWith("rndis") -> {
                                apIp = host
                                activeInterface = intf.name
                            }
                            fallbackIp == null -> {
                                fallbackIp = host
                                activeInterface = intf.name
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val ip = wifiIp ?: apIp ?: fallbackIp

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNetwork)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        var ssid: String? = null
        if (isWifi) {
            try {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val info = wm?.connectionInfo
                val rawSsid = info?.ssid
                if (!rawSsid.isNullOrBlank() && rawSsid != "<unknown ssid>") {
                    ssid = rawSsid.removeSurrounding("\"")
                }
            } catch (_: Exception) {}
        }

        return NetworkInfo(
            ipAddress = ip,
            interfaceName = activeInterface,
            isWifi = isWifi,
            isHotspotOrOther = !isWifi && ip != null,
            ssid = ssid
        )
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return DecimalFormat("#,##0.#").format(value) + " " + units[digitGroups]
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        return "${formatBytes(bytesPerSec)}/s"
    }
}
