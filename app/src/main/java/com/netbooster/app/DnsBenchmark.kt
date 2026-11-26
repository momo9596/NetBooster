package com.netbooster.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.sqrt
import kotlin.random.Random

object DnsBenchmark {

    data class Server(val name: String, val primary: String, val secondary: String)

    val servers = listOf(
        Server("Cloudflare", "1.1.1.1", "1.0.0.1"),
        Server("Google", "8.8.8.8", "8.8.4.4"),
        Server("Quad9", "9.9.9.9", "149.112.112.112"),
        Server("OpenDNS", "208.67.222.222", "208.67.220.220"),
        Server("AdGuard", "94.140.14.14", "94.140.15.15"),
        Server("CleanBrowsing", "185.228.168.9", "185.228.169.9"),
        Server("Comodo", "8.26.56.26", "8.20.247.20"),
        Server("Level3", "4.2.2.1", "4.2.2.2"),
        Server("Verisign", "64.6.64.6", "64.6.65.6"),
        Server("NextDNS", "45.90.28.0", "45.90.30.0"),
        Server("Yandex", "77.88.8.8", "77.88.8.1"),
        Server("ControlD", "76.76.2.0", "76.76.10.0"),
        Server("DNS.WATCH", "84.200.69.80", "84.200.70.40"),
        Server("Neustar", "156.154.70.1", "156.154.71.1")
    )

    private val domains = listOf(
        "google.com", "facebook.com", "youtube.com", "whatsapp.com",
        "tiktok.com", "microsoft.com", "wikipedia.org", "amazon.com"
    )

    private fun buildQuery(domain: String, id: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id shr 8); out.write(id and 0xff)
        out.write(0x01); out.write(0x00)
        out.write(0); out.write(1)
        repeat(6) { out.write(0) }
        for (label in domain.split('.')) {
            val b = label.toByteArray(Charsets.US_ASCII)
            out.write(b.size); out.write(b)
        }
        out.write(0)
        out.write(0); out.write(1)
        out.write(0); out.write(1)
        return out.toByteArray()
    }

    private suspend fun queryOnce(server: String, domain: String, timeoutMs: Int = 1500): Double? =
        withContext(Dispatchers.IO) {
            try {
                DatagramSocket().use { s ->
                    s.soTimeout = timeoutMs
                    val id = Random.nextInt(0, 0xFFFF)
                    val q = buildQuery(domain, id)
                    val addr = InetAddress.getByName(server)
                    val buf = ByteArray(512)
                    val p = DatagramPacket(buf, buf.size)
                    val t0 = System.nanoTime()
                    s.send(DatagramPacket(q, q.size, addr, 53))
                    val deadline = t0 + timeoutMs * 1_000_000L
                    var result: Double? = null
                    while (result == null && System.nanoTime() < deadline) {
                        p.length = buf.size
                        s.receive(p)
                        val rid = ((buf[0].toInt() and 0xff) shl 8) or (buf[1].toInt() and 0xff)
                        if (p.length >= 12 && rid == id) {
                            val ms = (System.nanoTime() - t0) / 1e6
                            val rcode = buf[3].toInt() and 0x0f
                            result = if (rcode == 0 || rcode == 3) ms else -1.0
                        }
                    }
                    if (result != null && result >= 0) result else null
                }
            } catch (_: Exception) {
                null
            }
        }

    private fun median(v: List<Double>): Double {
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    private suspend fun measure(name: String, ip: String): DnsResult {
        val samples = mutableListOf<Double>()
        var total = 0
        for (round in 0 until 2) {
            for (d in domains.take(6)) {
                total++
                queryOnce(ip, d)?.let { samples.add(it) }
            }
        }
        for (i in 0 until 3) {
            total++
            queryOnce(ip, "nb${Random.nextInt(1_000_000_000)}.com")?.let { samples.add(it) }
        }
        if (samples.isEmpty()) return DnsResult(name, ip, 9999.0, 0.0, 0, 99999.0)
        val med = median(samples)
        val mean = samples.average()
        val jitter = sqrt(samples.map { (it - mean) * (it - mean) }.average())
        val success = samples.size * 100 / total
        val failPenalty = (total - samples.size).toDouble() / total * 1000.0
        return DnsResult(name, ip, med, jitter, success, med + jitter * 0.5 + failPenalty)
    }

    suspend fun run(current: List<String>, onProgress: (Int, Int) -> Unit): DnsReport {
        val targets = servers.flatMap { listOf(it.name to it.primary, it.name to it.secondary) }
        val sem = Semaphore(4)
        var done = 0
        val results = coroutineScope {
            targets.map { (name, ip) ->
                async {
                    val r = sem.withPermit { measure(name, ip) }
                    done++
                    onProgress(done, targets.size)
                    r
                }
            }.awaitAll()
        }
        val ranked = results.filter { it.successPct > 0 }.sortedBy { it.score }
        val primary = ranked.firstOrNull()
        val secondary = ranked.firstOrNull { it.name != primary?.name } ?: ranked.getOrNull(1)

        val curResult = current.firstOrNull()?.let { measure("الحالي", it) }
        val curMedian = curResult?.takeIf { it.successPct > 0 }?.medianMs
        return DnsReport(ranked, primary, secondary, current, curMedian)
    }
}
