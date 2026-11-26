package com.netbooster.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.random.Random

data class Metrics(
    val down: Double,
    val up: Double,
    val ping: Double,
    val jitter: Double,
    val loss: Double,
    val dnsMs: Double?
)

data class CompareRow(val name: String, val before: Double, val after: Double, val unit: String, val changePct: Double)

data class Comparison(val rows: List<CompareRow>, val overallPct: Double)

object MetricsTool {

    suspend fun systemDnsMs(): Double? = withContext(Dispatchers.IO) {
        val v = mutableListOf<Double>()
        repeat(6) {
            val t0 = System.nanoTime()
            try {
                InetAddress.getByName("nb${Random.nextInt(1_000_000_000)}.com")
            } catch (_: UnknownHostException) {
            }
            v.add((System.nanoTime() - t0) / 1e6)
        }
        if (v.isEmpty()) null else v.sorted()[v.size / 2]
    }

    fun from(speed: SpeedResult, pings: List<PingResult>, dnsMs: Double?): Metrics? {
        val net = pings.filter { it.target != "الراوتر" && it.lossPct < 100 }
        if (net.isEmpty()) return null
        return Metrics(
            speed.downMbps, speed.upMbps,
            net.map { it.avg }.average(),
            net.map { it.jitter }.average(),
            net.map { it.lossPct.toDouble() }.average(),
            dnsMs
        )
    }

    suspend fun collect(): Metrics? {
        val down = SpeedTest.download()
        val up = SpeedTest.upload()
        val pings = listOf(
            Probe.ping("Cloudflare 1.1.1.1", "1.1.1.1", 443),
            Probe.ping("Google 8.8.8.8", "8.8.8.8", 443)
        )
        val dns = systemDnsMs()
        return from(SpeedResult(down, up, 0.0), pings, dns)
    }

    private data class Def(val name: String, val unit: String, val higherBetter: Boolean, val weight: Double)

    fun compare(b: Metrics, a: Metrics): Comparison {
        val rows = mutableListOf<CompareRow>()
        var sum = 0.0
        var wSum = 0.0

        fun add(d: Def, before: Double?, after: Double?) {
            if (before == null || after == null) return
            val change: Double = if (before <= 0.01) {
                if (d.higherBetter || after <= 0.01) return else -100.0
            } else {
                val raw = if (d.higherBetter) (after - before) / before else (before - after) / before
                (raw * 100).coerceIn(-100.0, 100.0)
            }
            rows.add(CompareRow(d.name, before, after, d.unit, change))
            sum += change * d.weight
            wSum += d.weight
        }

        add(Def("سرعة التحميل", "Mbps", true, 25.0), b.down, a.down)
        add(Def("سرعة الرفع", "Mbps", true, 10.0), b.up, a.up)
        add(Def("زمن الاستجابة Ping", "ms", false, 20.0), b.ping, a.ping)
        add(Def("التذبذب Jitter", "ms", false, 15.0), b.jitter, a.jitter)
        add(Def("استجابة DNS", "ms", false, 20.0), b.dnsMs, a.dnsMs)
        add(Def("فقدان الحزم", "%", false, 10.0), b.loss, a.loss)

        return Comparison(rows, if (wSum > 0) sum / wSum else 0.0)
    }
}
