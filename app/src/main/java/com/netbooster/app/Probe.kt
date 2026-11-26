package com.netbooster.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.math.abs

object Probe {

    private fun tcpPing(host: String, port: Int, timeout: Int = 1500): Double? {
        return try {
            Socket().use { s ->
                val t0 = System.nanoTime()
                s.connect(InetSocketAddress(host, port), timeout)
                (System.nanoTime() - t0) / 1e6
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun ping(label: String, host: String, port: Int, count: Int = 10): PingResult =
        withContext(Dispatchers.IO) {
            val v = mutableListOf<Double>()
            repeat(count) {
                tcpPing(host, port)?.let { v.add(it) }
                Thread.sleep(80)
            }
            summarize(label, v, count)
        }

    suspend fun pingGateway(ip: String, openPorts: List<Int>, count: Int = 10): PingResult =
        withContext(Dispatchers.IO) {
            val port = listOf(80, 443, 53, 8080).firstOrNull { it in openPorts }
            val v = mutableListOf<Double>()
            repeat(count) {
                if (port != null) {
                    tcpPing(ip, port, 1000)?.let { v.add(it) }
                } else {
                    try {
                        val t0 = System.nanoTime()
                        if (InetAddress.getByName(ip).isReachable(1000)) v.add((System.nanoTime() - t0) / 1e6)
                    } catch (_: Exception) {
                    }
                }
                Thread.sleep(80)
            }
            summarize("الراوتر", v, count)
        }

    private fun summarize(label: String, v: List<Double>, count: Int): PingResult {
        if (v.isEmpty()) return PingResult(label, 0.0, 0.0, 0.0, 0.0, 100)
        val jitter = if (v.size > 1) v.zipWithNext { a, b -> abs(a - b) }.average() else 0.0
        return PingResult(label, v.average(), v.min(), v.max(), jitter, (count - v.size) * 100 / count)
    }

    private val ports = listOf(
        21 to "FTP", 22 to "SSH", 23 to "Telnet", 53 to "DNS", 80 to "HTTP",
        443 to "HTTPS", 1723 to "PPTP", 7547 to "TR-069", 8080 to "HTTP-Alt",
        8443 to "HTTPS-Alt", 8291 to "Winbox", 5555 to "ADB", 32764 to "Backdoor"
    )
    private val risky = setOf(21, 23, 5555, 32764)

    suspend fun scanPorts(ip: String): List<PortResult> = coroutineScope {
        ports.map { (p, name) ->
            async(Dispatchers.IO) {
                val open = try {
                    Socket().use { it.connect(InetSocketAddress(ip, p), 900); true }
                } catch (_: Exception) {
                    false
                }
                PortResult(p, name, open, p in risky)
            }
        }.awaitAll()
    }

    private val defaultCreds = setOf(
        "admin:admin", "admin:1234", "admin:password", "admin:", "user:user",
        "root:admin", "admin:12345", "admin:admin123"
    )

    private val vendors = listOf(
        "tp-link", "huawei", "zte", "tenda", "d-link", "netgear", "mikrotik", "asus",
        "xiaomi", "nokia", "zyxel", "fiberhome", "tplink", "linksys", "totolink", "mercusys"
    )

    private fun detectVendor(text: String): String {
        val t = text.lowercase()
        val v = vendors.firstOrNull { t.contains(it) }
        return v?.uppercase() ?: "غير معروف"
    }

    suspend fun probeRouter(ip: String, user: String, pass: String): RouterInfo =
        withContext(Dispatchers.IO) {
            fun request(auth: Boolean): Triple<Int, String, Long>? {
                return try {
                    val c = URL("http://$ip/").openConnection() as HttpURLConnection
                    c.connectTimeout = 3000
                    c.readTimeout = 4000
                    c.instanceFollowRedirects = true
                    if (auth) {
                        val token = android.util.Base64.encodeToString(
                            "$user:$pass".toByteArray(), android.util.Base64.NO_WRAP
                        )
                        c.setRequestProperty("Authorization", "Basic $token")
                    }
                    val t0 = System.nanoTime()
                    val code = c.responseCode
                    val ms = (System.nanoTime() - t0) / 1_000_000
                    val head = (c.getHeaderField("Server") ?: "") + " " + (c.getHeaderField("WWW-Authenticate") ?: "")
                    val body = try {
                        (if (code >= 400) c.errorStream else c.inputStream)
                            ?.bufferedReader()?.use { r ->
                                val buf = CharArray(20000)
                                val n = r.read(buf)
                                if (n > 0) String(buf, 0, n) else ""
                            } ?: ""
                    } catch (_: Exception) {
                        ""
                    }
                    c.disconnect()
                    Triple(code, "$head $body", ms)
                } catch (_: Exception) {
                    null
                }
            }

            val isDefault = "$user:$pass".lowercase() in defaultCreds
            val first = request(false)
                ?: return@withContext RouterInfo(
                    false, "-", null, isDefault, null,
                    "تعذر الوصول لصفحة الراوتر. تأكد أنك متصل بشبكة الواي فاي الخاصة بالراوتر."
                )

            val vendor = detectVendor(first.second)
            if (first.first == 401) {
                val second = request(true)
                val ok = second != null && second.first in 200..399
                return@withContext RouterInfo(
                    true, vendor, ok, isDefault, first.third,
                    if (ok) "تم تسجيل الدخول بنجاح (Basic Auth)" else "اسم المستخدم أو كلمة المرور غير صحيحة"
                )
            }
            RouterInfo(
                true, vendor, null, isDefault, first.third,
                "الراوتر يستخدم صفحة تسجيل دخول، سيتم تعبئة البيانات تلقائياً داخل التطبيق"
            )
        }
}
