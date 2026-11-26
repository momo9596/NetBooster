package com.netbooster.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.util.Locale

fun String.toEnglishDigits(): String {
    val sb = StringBuilder(length)
    for (c in this) {
        when (c) {
            in '\u0660'..'\u0669' -> sb.append('0' + (c - '\u0660'))
            in '\u06F0'..'\u06F9' -> sb.append('0' + (c - '\u06F0'))
            '\u066B', '\u060C', '\u066C', ',' -> sb.append('.')
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

fun fmt(d: Double, decimals: Int = 0): String = String.format(Locale.US, "%.${decimals}f", d)

fun ltr(s: String): String = "\u202A$s\u202C"

object Gateway {

    fun gatewayIp(ctx: Context): String? {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val lp = if (net != null) cm.getLinkProperties(net) else null
            val gw = lp?.routes
                ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
                ?.gateway?.hostAddress
            if (!gw.isNullOrEmpty()) return gw
        } catch (_: Exception) {
        }
        return try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val g = wm.dhcpInfo?.gateway ?: 0
            if (g == 0) null
            else "${g and 0xff}.${(g shr 8) and 0xff}.${(g shr 16) and 0xff}.${(g shr 24) and 0xff}"
        } catch (_: Exception) {
            null
        }
    }

    fun currentDns(ctx: Context): List<String> {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return emptyList()
            cm.getLinkProperties(net)?.dnsServers
                ?.filter { it is Inet4Address }
                ?.mapNotNull { it.hostAddress }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
