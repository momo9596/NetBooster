package com.netbooster.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

object SpeedTest {
    private const val DOWN = "https://speed.cloudflare.com/__down?bytes=50000000"
    private const val UP = "https://speed.cloudflare.com/__up"

    suspend fun download(durationMs: Long = 8000, threads: Int = 4): Double = coroutineScope {
        val total = AtomicLong(0)
        val start = System.nanoTime()
        val end = start + durationMs * 1_000_000L
        (1..threads).map {
            async(Dispatchers.IO) {
                val buf = ByteArray(64 * 1024)
                try {
                    while (System.nanoTime() < end) {
                        val c = URL(DOWN).openConnection() as HttpURLConnection
                        c.connectTimeout = 5000
                        c.readTimeout = 5000
                        try {
                            c.inputStream.use { ins ->
                                while (System.nanoTime() < end) {
                                    val n = ins.read(buf)
                                    if (n < 0) break
                                    total.addAndGet(n.toLong())
                                }
                            }
                        } finally {
                            c.disconnect()
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }.awaitAll()
        val sec = (System.nanoTime() - start) / 1e9
        total.get() * 8 / 1e6 / sec
    }

    suspend fun upload(durationMs: Long = 6000, threads: Int = 3): Double = coroutineScope {
        val total = AtomicLong(0)
        val start = System.nanoTime()
        val end = start + durationMs * 1_000_000L
        (1..threads).map {
            async(Dispatchers.IO) {
                val chunk = ByteArray(1_000_000)
                try {
                    while (System.nanoTime() < end) {
                        val c = URL(UP).openConnection() as HttpURLConnection
                        c.requestMethod = "POST"
                        c.doOutput = true
                        c.connectTimeout = 5000
                        c.readTimeout = 8000
                        c.setFixedLengthStreamingMode(chunk.size)
                        try {
                            c.outputStream.use { it.write(chunk) }
                            c.responseCode
                            total.addAndGet(chunk.size.toLong())
                        } finally {
                            c.disconnect()
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }.awaitAll()
        val sec = (System.nanoTime() - start) / 1e9
        total.get() * 8 / 1e6 / sec
    }
}
