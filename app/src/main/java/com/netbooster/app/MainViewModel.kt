package com.netbooster.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val autoPending: Boolean = false
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("nb", android.content.Context.MODE_PRIVATE)

    var state by mutableStateOf(
        UiState(
            routerIp = Gateway.gatewayIp(app) ?: prefs.getString("ip", "") ?: "",
            user = "admin", // فرض اسم المستخدم admin
            pass = "admin", // فرض كلمة المرور admin
            auto = true
        )
    )
        private set

    fun setAuto(v: Boolean) {
        prefs.edit().putBoolean("auto", v).apply()
        state = state.copy(auto = v)
    }

    fun consumeAuto() { state = state.copy(autoPending = false) }

    private var job: Job? = null

    fun detectRouter() {
        val ip = Gateway.gatewayIp(getApplication())
        state = state.copy(
            routerIp = ip ?: state.routerIp,
            error = if (ip == null) "لم يتم العثور على الراوتر. اتصل بشبكة الواي فاي ثم حاول مرة أخرى." else ""
        )
    }

    fun onIp(s: String) {
        val clean = s.toEnglishDigits().filter { it.isDigit() || it == '.' }
        state = state.copy(routerIp = clean, error = "")
    }

    fun onUser(s: String) { state = state.copy(user = s) }
    fun onPass(s: String) { state = state.copy(pass = s) }
    fun goto(s: Screen) { state = state.copy(screen = s) }

    fun back() {
        state = when (state.screen) {
            Screen.WEB -> state.copy(screen = Screen.ANALYSIS)
            Screen.ANALYSIS -> { job?.cancel(); state.copy(screen = Screen.SETUP, running = false) }
            else -> state
        }
    }

    private fun validIp(ip: String): Boolean {
        val parts = ip.split(".")
        return parts.size == 4 && parts.all { it.isNotEmpty() && it.length <= 3 && it.toInt() in 0..255 }
    }

    fun start(hasLocation: Boolean) {
        val ip = state.routerIp.trim()
        if (!validIp(ip)) {
            state = state.copy(error = "عنوان IP غير صحيح، تأكد من اتصالك بالواي فاي")
            return
        }
        prefs.edit().putString("ip", ip).putString("user", state.user).putString("pass", state.pass).apply()
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
            setStep(id, Status.FAILED, e.message ?: "حدث خطأ")
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
                Step("wifi", "فحص قنوات الواي فاي 1-13")
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
        if (router != null) {
            if (!router.reachable) issues.add(Issue(2, "تعذر الوصول إلى الراوتر على ${ltr(ip)}"))
            if (router.authOk == false) issues.add(Issue(2, "اسم المستخدم أو كلمة المرور غير صحيحة"))
        }

        val speed = step("speed") {
            val down = SpeedTest.download()
            val up = SpeedTest.upload()
            val pg = Probe.ping("1.1.1.1", "1.1.1.1", 443, 5).avg
            SpeedResult(down, up, pg) to "تحميل ${fmt(down, 1)} • رفع ${fmt(up, 1)} Mbps"
        }
        state = state.copy(speed = speed)

        val currentDns = Gateway.currentDns(ctx)
        val dns = step("dns") {
            val rep = DnsBenchmark.run(currentDns) { done, total ->
                setStep("dns", Status.RUNNING, "$done / $total")
            }
            val p = rep.primary
            rep to (if (p != null) "الأفضل: ${p.name} (${fmt(p.medianMs, 0)} ms)" else "لم يستجب أي سيرفر")
        }
        state = state.copy(dns = dns)

        val ports = step("ports") {
            val r = Probe.scanPorts(ip)
            val open = r.filter { it.open }
            r to (if (open.isEmpty()) "لا توجد منافذ مفتوحة" else "مفتوح: " + open.joinToString(" ") { it.port.toString() })
        }
        state = state.copy(ports = ports ?: emptyList())

        val pings = step("ping") {
            val openPorts = ports?.filter { it.open }?.map { it.port } ?: emptyList()
            val list = listOf(
                Probe.pingGateway(ip, openPorts),
                Probe.ping("Cloudflare 1.1.1.1", "1.1.1.1", 443),
                Probe.ping("Google 8.8.8.8", "8.8.8.8", 443)
            )
            list to "متوسط ${fmt(list.drop(1).map { it.avg }.average(), 0)} ms"
        }
        state = state.copy(pings = pings ?: emptyList())

        if (!hasLocation) {
            setStep("wifi", Status.SKIPPED, "يحتاج إذن الموقع لقراءة الشبكات القريبة")
        } else {
            val ch = step("wifi") {
                val r = WifiChannels.scan(ctx) ?: throw IllegalStateException("تعذر الفحص")
                r to "أفضل قناة: ${r.best}"
            }
            state = state.copy(channels = ch)
        }

        if (issues.isEmpty()) issues.add(Issue(0, "لم يتم اكتشاف مشاكل واضحة، لكن التطبيق سيطبق أفضل الإعدادات لزيادة الاستقرار"))

        val baseline = if (speed != null && pings != null) MetricsTool.from(speed, pings, MetricsTool.systemDnsMs()) else null
        state = state.copy(
            issues = issues.sortedByDescending { it.severity }, running = false,
            before = baseline, after = null, comparison = null
        )

        // التوجه التلقائي المباشر لصفحة الراوتر إذا كان يعمل
        val d = state.dns
        val ok = router != null && router.reachable && router.authOk != false && d?.primary != null && d.secondary != null
        if (state.auto && ok) {
            state = state.copy(autoPending = true, screen = Screen.WEB)
        }
    }

    fun retest() {
        val b = state.before ?: return
        if (state.retesting || state.running) return
        job?.cancel()
        job = viewModelScope.launch {
            state = state.copy(retesting = true, comparison = null, after = null)
            val m = try {
                MetricsTool.collect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            state = state.copy(
                retesting = false, after = m,
                comparison = if (m != null) MetricsTool.compare(b, m) else null,
                error = if (m == null) "تعذر إعادة القياس، تأكد من اتصال الإنترنت" else ""
            )
        }
    }
}
