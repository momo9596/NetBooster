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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val profile: RouterProfile? = null
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

    // هاتان الدالتان كانتا ناقصتين وتم إضافتهما الآن
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
            error = if (ip == null) "لم يتم العثور على الراوتر تلقائياً، يرجى كتابة الـ IP يدوياً." else ""
        )
    }

    fun onIp(s: String) {
        val clean = s.toEnglishDigits().filter { it.isDigit() || it == '.' }
        state = state.copy(routerIp = clean, error = "")
    }

    fun start(hasLocation: Boolean) {
        val ip = state.routerIp.trim()
        if (ip.isEmpty() || !ip.contains(".")) {
            state = state.copy(error = "عنوان IP غير صحيح، يرجى كتابته يدوياً (مثال: 192.168.1.1)")
            return
        }
        prefs.edit().putString("ip", ip).apply()
        job?.cancel()
        job = viewModelScope.launch { runAll(ip, hasLocation) }
    }

    private fun setStep(id: String, status: Status, detail: String = "") {
        state = state.copy(steps = state.steps.map { if (it.id == id) it.copy(status = status, detail = detail) else it })
    }

    private suspend fun <T> step(id: String, block: suspend () -> Pair<T, String>): T? {
        setStep(id, Status.RUNNING)
        return try {
            val (v, d) = block()
            setStep(id, Status.DONE, d)
            v
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setStep(id, Status.FAILED, e.message ?: "خطأ")
            null
        }
    }

    private suspend fun runAll(ip: String, hasLocation: Boolean) {
        val ctx = getApplication<Application>()
        state = state.copy(
            screen = Screen.ANALYSIS, error = "", running = true,
            steps = listOf(
                Step("router", "فحص الراوتر وتسجيل الدخول"),
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

        val router = step("router") {
            val r = Probe.probeRouter(ip, state.user, state.pass)
            r to (if (r.reachable) "${r.vendor} • ${r.note}" else r.note)
        }
        state = state.copy(router = router)

        val vendorName = router?.vendor ?: ""
        val matchedProfile = knownRouters.firstOrNull { it.brand.contains(vendorName, ignoreCase = true) } ?: knownRouters.last()
        state = state.copy(profile = matchedProfile)

        val speed = step("speed") {
            val down = SpeedTest.download()
            val up = SpeedTest.upload()
            val pg = Probe.ping("1.1.1.1", "1.1.1.1", 443, 5).avg
            SpeedResult(down, up, pg) to "تحميل ${fmt(down, 1)} • رفع ${fmt(up, 1)} Mbps"
        }
        state = state.copy(speed = speed)

        val currentDns = Gateway.currentDns(ctx)
        val dns = step("dns") {
            val rep = DnsBenchmark.run(currentDns) { done, total -> setStep("dns", Status.RUNNING, "$done / $total") }
            rep to (if (rep.primary != null) "الأفضل: ${rep.primary.name}" else "لا يوجد")
        }
        state = state.copy(dns = dns)

        val ports = step("ports") {
            val r = Probe.scanPorts(ip)
            r to (if (r.none { it.open }) "لا توجد منافذ مفتوحة" else "يوجد منافذ مفتوحة")
        }
        state = state.copy(ports = ports ?: emptyList())

        val pings = step("ping") {
            val list = listOf(Probe.pingGateway(ip, emptyList()), Probe.ping("Cloudflare", "1.1.1.1", 443), Probe.ping("Google", "8.8.8.8", 443))
            list to "متوسط ${fmt(list.drop(1).map { it.avg }.average(), 0)} ms"
        }
        state = state.copy(pings = pings ?: emptyList())

        if (!hasLocation) {
            setStep("wifi", Status.SKIPPED, "يحتاج إذن الموقع")
        } else {
            val ch = step("wifi") {
                val r = WifiChannels.scan(ctx) ?: throw IllegalStateException("تعذر الفحص")
                r to "أفضل قناة: ${r.best}"
            }
            state = state.copy(channels = ch)
        }

        val baseline = if (speed != null && pings != null) MetricsTool.from(speed, pings, MetricsTool.systemDnsMs()) else null
        state = state.copy(issues = issues, running = false, before = baseline, after = null, comparison = null)

        val d = state.dns
        val ok = router != null && router.reachable && router.authOk != false && d?.primary != null && d.secondary != null
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
