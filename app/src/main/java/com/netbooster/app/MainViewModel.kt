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
            user = prefs.getString("user", "admin") ?: "admin",
            pass = prefs.getString("pass", "admin") ?: "admin",
            auto = prefs.getBoolean("auto", true)
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
            state = state.copy(error = "عنوان IP غير صحيح، مثال: 192.168.1.1")
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
            if (router.defaultCreds) issues.add(Issue(1, "الراوتر يستخدم بيانات دخول افتراضية، غيّرها لحماية شبكتك من الاختراق"))
        }

        val speed = step("speed") {
            val down = SpeedTest.download()
            val up = SpeedTest.upload()
            val pg = Probe.ping("1.1.1.1", "1.1.1.1", 443, 5).avg
            SpeedResult(down, up, pg) to "تحميل ${fmt(down, 1)} • رفع ${fmt(up, 1)} Mbps"
        }
        state = state.copy(speed = speed)
        if (speed != null) {
            if (speed.downMbps < 5) issues.add(Issue(2, "سرعة التحميل ضعيفة جداً (${fmt(speed.downMbps, 1)} Mbps)"))
            else if (speed.downMbps < 15) issues.add(Issue(1, "سرعة التحميل منخفضة (${fmt(speed.downMbps, 1)} Mbps)"))
        }

        val currentDns = Gateway.currentDns(ctx)
        val dns = step("dns") {
            val rep = DnsBenchmark.run(currentDns) { done, total ->
                setStep("dns", Status.RUNNING, "$done / $total")
            }
            val p = rep.primary
            rep to (if (p != null) "الأفضل: ${p.name} (${fmt(p.medianMs, 0)} ms)" else "لم يستجب أي سيرفر")
        }
        state = state.copy(dns = dns)
        if (dns != null) {
            val best = dns.primary
            val cur = dns.currentMedian
            if (dns.current.isNotEmpty() && cur == null) {
                issues.add(Issue(2, "الـ DNS الحالي لا يستجيب بشكل صحيح"))
            } else if (best != null && cur != null && cur > best.medianMs * 1.3 && cur - best.medianMs > 10) {
                issues.add(Issue(1, "الـ DNS الحالي أبطأ بحوالي ${fmt(cur - best.medianMs, 0)} ms من الأفضل (${best.name})"))
            }
        }

        val ports = step("ports") {
            val r = Probe.scanPorts(ip)
            val open = r.filter { it.open }
            r to (if (open.isEmpty()) "لا توجد منافذ مفتوحة" else "مفتوح: " + open.joinToString(" ") { it.port.toString() })
        }
        state = state.copy(ports = ports ?: emptyList())
        ports?.filter { it.open && it.risky }?.forEach {
            issues.add(Issue(2, "المنفذ ${it.port} (${it.service}) مفتوح وهو خطر أمني، يفضل إغلاقه من إعدادات الراوتر"))
        }

        val pings = step("ping") {
            val openPorts = ports?.filter { it.open }?.map { it.port } ?: emptyList()
            val list = listOf(
                Probe.pingGateway(ip, openPorts),
                Probe.ping("Cloudflare 1.1.1.1", "1.1.1.1", 443),
                Probe.ping("Google 8.8.8.8", "8.8.8.8", 443)
            )
            val net = list.drop(1).filter { it.lossPct < 100 }
            list to (if (net.isEmpty()) "لا يوجد اتصال بالإنترنت" else "متوسط ${fmt(net.map { it.avg }.average(), 0)} ms")
        }
        state = state.copy(pings = pings ?: emptyList())
        pings?.forEach { p ->
            if (p.lossPct >= 100) issues.add(Issue(2, "لا توجد استجابة من ${p.target}"))
            else {
                if (p.lossPct >= 10) issues.add(Issue(2, "فقدان حزم ${p.lossPct}% مع ${p.target}"))
                else if (p.lossPct >= 3) issues.add(Issue(1, "فقدان حزم ${p.lossPct}% مع ${p.target}"))
                if (p.jitter > 30) issues.add(Issue(1, "تذبذب عالٍ في الاستجابة مع ${p.target} (${fmt(p.jitter, 0)} ms)"))
            }
        }
        pings?.firstOrNull { it.target == "الراوتر" }?.let { g ->
            if (g.lossPct < 100 && g.avg > 15) issues.add(Issue(1, "الاتصال بالراوتر بطيء (${fmt(g.avg, 0)} ms)، الإشارة ضعيفة أو الشبكة مزدحمة"))
        }
        pings?.filter { it.target != "الراوتر" && it.lossPct < 100 }?.forEach {
            if (it.avg > 120) issues.add(Issue(1, "زمن الاستجابة مرتفع مع ${it.target} (${fmt(it.avg, 0)} ms)"))
        }

        if (!hasLocation) {
            setStep("wifi", Status.SKIPPED, "يحتاج إذن الموقع لقراءة الشبكات القريبة")
        } else {
            val ch = step("wifi") {
                val r = WifiChannels.scan(ctx) ?: throw IllegalStateException("تعذر الفحص: فعّل الموقع (GPS) وأعد المحاولة")
                r to "أفضل قناة: ${r.best}"
            }
            state = state.copy(channels = ch)
            if (ch != null) {
                val own = ch.ownChannel
                val ownScore = ch.scores.firstOrNull { it.channel == own }?.congestion
                val bestScore = ch.scores.first { it.channel == ch.best }.congestion
                if (own != null && own != ch.best && ownScore != null && ownScore > bestScore + 0.3) {
                    issues.add(Issue(1, "قناة الراوتر الحالية $own مزدحمة. غيّرها إلى القناة ${ch.best}"))
                }
                val rssi = ch.ownRssi
                if (rssi != null && rssi < -75) issues.add(Issue(1, "إشارة الواي فاي ضعيفة ($rssi dBm)، قرّب من الراوتر"))
            }
        }

        if (issues.isEmpty()) issues.add(Issue(0, "لم يتم اكتشاف مشاكل واضحة في الشبكة"))

        val baseline = if (speed != null && pings != null) MetricsTool.from(speed, pings, MetricsTool.systemDnsMs()) else null
        state = state.copy(
            issues = issues.sortedByDescending { it.severity }, running = false,
            before = baseline, after = null, comparison = null
        )

        val d = state.dns
        val ok = router != null && router.reachable && router.authOk != false &&
                d?.primary != null && d.secondary != null
        if (state.auto && ok) {
            state = state.copy(autoPending = true, screen = Screen.WEB)
        } else if (state.auto && !ok) {
            state = state.copy(error = "لم يتم التطبيق التلقائي: تحقق من الراوتر وبيانات الدخول ونتائج DNS ثم أعد المحاولة")
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
