package com.netbooster.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlin.math.roundToInt

data class UiState(
    val screen: Screen = Screen.SETUP,
    val routerIp: String = "",
    val user: String = "admin",
    val pass: String = "admin",
    val error: String = "",
    val steps: List<Step> = emptyList(),
    val running: Boolean = false,
    val router: RouterInfo? = null,
    val speed: SpeedResult? = null,
    val dns: DnsReport? = null,
    val pings: List<PingResult> = emptyList(),
    val ports: List<PortResult> = emptyList(),
    val channels: ChannelReport? = null,
    val issues: List<Issue> = emptyList(),
    val before: Metrics? = null,
    val after: Metrics? = null,
    val comparison: Comparison? = null,
    val retesting: Boolean = false,
    val auto: Boolean = true,
    val autoPending: Boolean = false,
    val profile: RouterProfile? = null,
    val healthScore: Int = 0 // مؤشر صحة الشبكة الجديد (من 100)
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("nb", Context.MODE_PRIVATE)
    
    var state by mutableStateOf(
        UiState(
            routerIp = Gateway.gatewayIp(app) ?: prefs.getString("ip", "") ?: "",
            user = "admin",
            pass = "admin",
            auto = true
        )
    )
        private set

    fun setAuto(v: Boolean) { prefs.edit().putBoolean("auto", v).apply(); state = state.copy(auto = v) }
    fun consumeAuto() { state = state.copy(autoPending = false) }
    private var job: Job? = null

    fun goto(s: Screen) { state = state.copy(screen = s) }
    fun back() {
        state = when (state.screen) {
            Screen.WEB -> state.copy(screen = Screen.ANALYSIS)
            Screen.ANALYSIS -> { job?.cancel(); state.copy(screen = Screen.SETUP, running = false) }
            else -> state
        }
    }

    fun detectRouter() {
        val ip = Gateway.gatewayIp(getApplication())
        state = state.copy(
            routerIp = ip ?: state.routerIp,
            error = if (ip == null) "لم يتم العثور على الراوتر تلقائياً." else ""
        )
    }

    fun onIp(s: String) {
        val clean = s.toEnglishDigits().filter { it.isDigit() || it == '.' }
        state = state.copy(routerIp = clean, error = "")
    }

    fun start(hasLocation: Boolean) {
        val ip = state.routerIp.trim()
        if (ip.isEmpty() || !ip.contains(".")) {
            state = state.copy(error = "عنوان IP غير صحيح")
            return
        }
        prefs.edit().putString("ip", ip).apply()
        job?.cancel()
        job = viewModelScope.launch { runAllFast(ip, hasLocation) }
    }

    private fun setStep(id: String, status: Status, detail: String = "") {
        state = state.copy(steps = state.steps.map { if (it.id == id) it.copy(status = status, detail = detail) else it })
    }

    // دالة الفحص السريع (تستخدم التزامن لتقليل الوقت)
    private suspend fun runAllFast(ip: String, hasLocation: Boolean) = coroutineScope {
        val ctx = getApplication<Application>()
        state = state.copy(
            screen = Screen.ANALYSIS, error = "", running = true,
            steps = listOf(
                Step("router", "فحص الراوتر"),
                Step("speed", "قياس السرعة الحالية"),
                Step("dns", "اختبار أسرع سيرفرات DNS"),
                Step("ports", "فحص منافذ الراوتر"),
                Step("ping", "فحص Ping والاستقرار"),
                Step("wifi", "فحص قنوات الواي فاي")
            ),
            router = null, speed = null, dns = null, pings = emptyList(),
            ports = emptyList(), channels = null, issues = emptyList()
        )
        val issues = mutableListOf<Issue>()

        // إطلاق الفحوصات معاً في الخلفية
        val routerDeferred = async {
            setStep("router", Status.RUNNING)
            val r = Probe.probeRouter(ip, state.user, state.pass)
            setStep("router", Status.DONE, if (r.reachable) r.vendor else r.note)
            r
        }

        val portsDeferred = async {
            setStep("ports", Status.RUNNING)
            val r = Probe.scanPorts(ip)
            setStep("ports", Status.DONE, if (r.none { it.open }) "آمن" else "منافذ مفتوحة")
            r
        }

        val wifiDeferred = async {
            if (!hasLocation) {
                setStep("wifi", Status.SKIPPED, "يحتاج إذن الموقع")
                null
            } else {
                setStep("wifi", Status.RUNNING)
                val r = WifiChannels.scan(ctx)
                setStep("wifi", Status.DONE, if(r != null) "أفضل قناة: ${r.best}" else "تعذر")
                r
            }
        }

        // فحوصات الإنترنت (تعمل معاً)
        val speedDeferred = async {
            setStep("speed", Status.RUNNING)
            val down = SpeedTest.download()
            val up = SpeedTest.upload()
            val pg = Probe.ping("1.1.1.1", "1.1.1.1", 443, 5).avg
            val r = SpeedResult(down, up, pg)
            setStep("speed", Status.DONE, "تحميل ${fmt(down, 1)} • رفع ${fmt(up, 1)}")
            r
        }

        val dnsDeferred = async {
            setStep("dns", Status.RUNNING)
            val currentDns = Gateway.currentDns(ctx)
            val rep = DnsBenchmark.run(currentDns) { done, total -> setStep("dns", Status.RUNNING, "$done / $total") }
            setStep("dns", Status.DONE, if (rep.primary != null) "الأفضل: ${rep.primary.name}" else "لا يوجد")
            rep
        }

        val pingDeferred = async {
            setStep("ping", Status.RUNNING)
            val list = listOf(Probe.pingGateway(ip, emptyList()), Probe.ping("Cloudflare", "1.1.1.1", 443), Probe.ping("Google", "8.8.8.8", 443))
            setStep("ping", Status.DONE, "متوسط ${fmt(list.drop(1).map { it.avg }.average(), 0)} ms")
            list
        }

        // جمع النتائج
        val router = routerDeferred.await()
        val ports = portsDeferred.await()
        val channels = wifiDeferred.await()
        val speed = speedDeferred.await()
        val dns = dnsDeferred.await()
        val pings = pingDeferred.await()

        state = state.copy(
            router = router, ports = ports, channels = channels,
            speed = speed, dns = dns, pings = pings
        )

        val vendorName = router?.vendor ?: ""
        val matchedProfile = knownRouters.firstOrNull { it.brand.contains(vendorName, ignoreCase = true) } ?: knownRouters.firstOrNull { it.brand == "TP-Link" } ?: knownRouters.last()
        
        // حساب مؤشر صحة الشبكة (0-100)
        val speedScore = ((speed?.downMbps ?: 0.0) / 50.0 * 100).coerceIn(0.0, 100.0) // 50Mbps = 100%
        val pingScore = (100 - ((pings?.drop(1)?.map { it.avg }?.average() ?: 100.0) / 2.0)).coerceIn(0.0, 100.0) // كل 2ms يخصم 1
        val health = ((speedScore * 0.6) + (pingScore * 0.4)).roundToInt()

        val baseline = if (speed != null && pings.isNotEmpty()) MetricsTool.from(speed, pings, MetricsTool.systemDnsMs()) else null
        state = state.copy(
            issues = issues, running = false, before = baseline, after = null, comparison = null,
            profile = matchedProfile, healthScore = health
        )

        val ok = router != null && router.reachable && router.authOk != false && dns?.primary != null && dns.secondary != null
        if (state.auto && ok) {
            state = state.copy(autoPending = true, screen = Screen.WEB)
        }
    }

    private fun isConnectedToWifi(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun retest() {
        val b = state.before ?: return
        if (state.retesting || state.running) return
        job?.cancel()
        job = viewModelScope.launch {
            state = state.copy(retesting = true, comparison = null, after = null, error = "")
            var attempts = 0
            while (!isConnectedToWifi() && attempts < 15) { delay(2000); attempts++ }
            if (!isConnectedToWifi()) {
                state = state.copy(retesting = false, error = "الرجاء التأكد من اتصال الهاتف بشبكة الواي فاي.")
                return@launch
            }
            delay(3000)
            val m = try { MetricsTool.collect() } catch (e: Exception) { null }
            state = state.copy(retesting = false, after = m, comparison = if (m != null) MetricsTool.compare(b, m) else null)
        }
    }
}
