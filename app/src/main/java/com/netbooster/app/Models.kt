package com.netbooster.app

enum class Status { WAITING, RUNNING, DONE, FAILED, SKIPPED }
enum class Screen { SETUP, ANALYSIS, WEB }

data class Step(val id: String, val title: String, val status: Status = Status.WAITING, val detail: String = "")

data class RouterInfo(
    val reachable: Boolean,
    val vendor: String,
    val authOk: Boolean?,
    val defaultCreds: Boolean,
    val httpMs: Long?,
    val note: String
)

data class SpeedResult(val downMbps: Double, val upMbps: Double, val pingMs: Double)

data class DnsResult(
    val name: String,
    val ip: String,
    val medianMs: Double,
    val jitterMs: Double,
    val successPct: Int,
    val score: Double
)

data class DnsReport(
    val ranked: List<DnsResult>,
    val primary: DnsResult?,
    val secondary: DnsResult?,
    val current: List<String>,
    val currentMedian: Double?
)

data class PingResult(
    val target: String,
    val avg: Double,
    val min: Double,
    val max: Double,
    val jitter: Double,
    val lossPct: Int
)

data class PortResult(val port: Int, val service: String, val open: Boolean, val risky: Boolean)

data class ChannelScore(val channel: Int, val congestion: Double, val apCount: Int)

data class ChannelReport(
    val scores: List<ChannelScore>,
    val best: Int,
    val ownChannel: Int?,
    val ownSsid: String?,
    val ownRssi: Int?
)

data class Issue(val severity: Int, val text: String)
