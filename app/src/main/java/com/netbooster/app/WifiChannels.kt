package com.netbooster.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs

object WifiChannels {

    private fun channelOf(freq: Int): Int? = when {
        freq in 2412..2472 -> (freq - 2407) / 5
        freq == 2484 -> 14
        else -> null
    }

    private fun weight(rssi: Int): Double = ((rssi + 95) / 65.0).coerceIn(0.05, 1.0)

    @Suppress("DEPRECATION")
    suspend fun scan(ctx: Context): ChannelReport? {
        val app = ctx.applicationContext
        val wm = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val results: List<ScanResult> = try {
            withTimeoutOrNull(6000) {
                suspendCancellableCoroutine<List<ScanResult>> { cont ->
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(c: Context?, i: Intent?) {
                            try { app.unregisterReceiver(this) } catch (_: Exception) {}
                            if (cont.isActive) cont.resume(wm.scanResults)
                        }
                    }
                    app.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
                    cont.invokeOnCancellation {
                        try { app.unregisterReceiver(receiver) } catch (_: Exception) {}
                    }
                    if (!wm.startScan()) {
                        try { app.unregisterReceiver(receiver) } catch (_: Exception) {}
                        if (cont.isActive) cont.resume(wm.scanResults)
                    }
                }
            } ?: wm.scanResults
        } catch (_: SecurityException) {
            return null
        } catch (_: Exception) {
            return null
        }
        if (results.isEmpty()) return null

        val info = wm.connectionInfo
        val ownBssid = info?.bssid
        val ownSsid = info?.ssid?.trim('"')?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
        val ownRssi = info?.rssi?.takeIf { it > -127 }
        val ownFreq = info?.frequency ?: 0
        val ownChannel = channelOf(ownFreq)

        val others = results.mapNotNull { r ->
            val ch = channelOf(r.frequency) ?: return@mapNotNull null
            if (ownBssid != null && r.BSSID.equals(ownBssid, ignoreCase = true)) return@mapNotNull null
            ch to r.level
        }.filter { it.first in 1..13 }

        val scores = (1..13).map { c ->
            var cong = 0.0
            var n = 0
            for ((ch, rssi) in others) {
                val d = abs(c - ch)
                if (d < 5) {
                    cong += (1.0 - d / 5.0) * weight(rssi)
                    if (d == 0) n++
                }
            }
            ChannelScore(c, cong, n)
        }
        val best = scores.minByOrNull { it.congestion + if (it.channel in setOf(1, 6, 11)) 0.0 else 0.15 }!!.channel
        return ChannelReport(scores, best, ownChannel, ownSsid, ownRssi)
    }
}
