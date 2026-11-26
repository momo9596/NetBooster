package com.netbooster.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.http.SslError
import android.webkit.HttpAuthHandler
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

private val Bg = Color(0xFF0E1621)
private val CardBg = Color(0xFF172433)
private val Accent = Color(0xFF00D1B2)
private val Good = Color(0xFF4ADE80)
private val Warn = Color(0xFFFBBF24)
private val Bad = Color(0xFFF87171)
private val Muted = Color(0xFF93A4B8)

@Composable
fun App(vm: MainViewModel) {
    val s = vm.state
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Color.Black,
            background = Bg, surface = Bg, onSurface = Color.White, onBackground = Color.White
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            BackHandler(enabled = s.screen != Screen.SETUP) { vm.back() }
            Surface(Modifier.fillMaxSize(), color = Bg) {
                when (s.screen) {
                    Screen.SETUP -> SetupScreen(vm)
                    Screen.ANALYSIS -> AnalysisScreen(vm)
                    Screen.WEB -> WebScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Accent)
            content()
        }
    }
}

@Composable
private fun LtrField(value: String, label: String, onChange: (String) -> Unit, password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(textDirection = TextDirection.Ltr, fontSize = 18.sp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None
        )
    }
}

@Composable
private fun SetupScreen(vm: MainViewModel) {
    val s = vm.state
    val ctx = LocalContext.current
    var showPass by remember { mutableStateOf(false) }

    fun hasLocation() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.start(granted)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("NetBooster", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Accent)
        Text("محسّن وفاحص الشبكة والراوتر", color = Muted)

        Section("1) عنوان الراوتر") {
            LtrField(s.routerIp, "Router IP", vm::onIp, keyboard = KeyboardType.Uri)
            OutlinedButton(onClick = { vm.detectRouter() }, modifier = Modifier.fillMaxWidth()) {
                Text("اكتشاف الراوتر تلقائياً")
            }
        }
        Section("2) بيانات دخول الراوتر") {
            LtrField(s.user, "اسم المستخدم (غالباً admin)", vm::onUser)
            LtrField(s.pass, "كلمة المرور", vm::onPass, password = !showPass)
            TextButton(onClick = { showPass = !showPass }) { Text(if (showPass) "إخفاء كلمة المرور" else "إظهار كلمة المرور") }
        }
        if (s.error.isNotEmpty()) Text(s.error, color = Bad)

        Section("3) الوضع التلقائي") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("تطبيق وحفظ التغييرات تلقائياً", fontWeight = FontWeight.SemiBold)
                    Text(
                        "بعد التحليل يدخل الراوتر ويغيّر DNS والقناة ويحفظ ويعيد التشغيل ثم يقيس نسبة التحسين، بدون أي ضغطة منك.",
                        color = Muted, fontSize = 12.sp
                    )
                }
                Switch(checked = s.auto, onCheckedChange = vm::setAuto)
            }
        }

        Button(
            onClick = {
                if (hasLocation()) vm.start(true)
                else launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("ابدأ التحليل والإصلاح", fontSize = 17.sp) }

        Text(
            "ملاحظة: إذن الموقع مطلوب فقط لأن أندرويد يشترطه لقراءة شبكات الواي فاي المجاورة وتحديد أفضل قناة. لا يتم إرسال أي بيانات لأي جهة.",
            color = Muted, fontSize = 12.sp
        )
    }
}

@Composable
private fun StepRow(st: Step) {
    val (icon, color) = when (st.status) {
        Status.WAITING -> "○" to Muted
        Status.RUNNING -> "⏳" to Warn
        Status.DONE -> "✔" to Good
        Status.FAILED -> "✖" to Bad
        Status.SKIPPED -> "–" to Muted
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(icon, color = color, fontSize = 18.sp)
        Column {
            Text(st.title, fontWeight = FontWeight.SemiBold)
            if (st.detail.isNotEmpty()) Text(st.detail, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AnalysisScreen(vm: MainViewModel) {
    val s = vm.state
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("تحليل الشبكة", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Accent)

        Section("خطوات الفحص") { s.steps.forEach { StepRow(it) } }

        s.speed?.let { sp ->
            Section("السرعة الحالية") {
                Text("التحميل: ${fmt(sp.downMbps, 1)} Mbps")
                Text("الرفع: ${fmt(sp.upMbps, 1)} Mbps")
                Text("زمن الاستجابة: ${fmt(sp.pingMs, 0)} ms")
            }
        }

        s.dns?.let { d ->
            Section("أفضل DNS (نتائج اختبار حقيقي)") {
                if (d.primary != null) {
                    Text("Primary: ${ltr(d.primary.ip)}  ${d.primary.name}", fontWeight = FontWeight.Bold, color = Good)
                    if (d.secondary != null)
                        Text("Secondary: ${ltr(d.secondary.ip)}  ${d.secondary.name}", fontWeight = FontWeight.Bold, color = Good)
                }
                if (d.current.isNotEmpty()) {
                    val cur = d.currentMedian?.let { "${fmt(it, 0)} ms" } ?: "لا يستجيب"
                    Text("DNS الحالي ${d.current.joinToString(" , ") { ltr(it) }}: $cur", color = Muted, fontSize = 13.sp)
                }
                d.ranked.take(10).forEachIndexed { i, r ->
                    Text(
                        "${i + 1}. ${r.name} ${ltr(r.ip)} — ${fmt(r.medianMs, 0)} ms • نجاح ${r.successPct}%",
                        fontSize = 13.sp, color = Muted
                    )
                }
            }
        }

        if (s.pings.isNotEmpty()) {
            Section("Ping") {
                s.pings.forEach { p ->
                    if (p.lossPct >= 100) Text("${p.target}: لا استجابة", color = Bad)
                    else Text(
                        "${p.target}: ${fmt(p.avg, 0)} ms (أقل ${fmt(p.min, 0)} / أعلى ${fmt(p.max, 0)}) • تذبذب ${fmt(p.jitter, 0)} • فقد ${p.lossPct}%",
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (s.ports.isNotEmpty()) {
            Section("منافذ الراوتر") {
                val open = s.ports.filter { it.open }
                if (open.isEmpty()) Text("كل المنافذ المفحوصة مغلقة", color = Good)
                open.forEach {
                    Text(
                        "${it.port} — ${it.service}" + if (it.risky) "  ⚠ خطر" else "",
                        color = if (it.risky) Bad else Color.White, fontSize = 13.sp
                    )
                }
                Text("الفحص من داخل الشبكة المحلية فقط.", color = Muted, fontSize = 11.sp)
            }
        }

        s.channels?.let { c ->
            Section("قنوات الواي فاي (2.4 GHz)") {
                Text("أفضل قناة: ${c.best}", fontWeight = FontWeight.Bold, color = Good)
                if (c.ownChannel != null) Text("قناة شبكتك الحالية: ${c.ownChannel}", fontSize = 13.sp)
                c.scores.forEach { sc ->
                    val bar = "█".repeat((sc.congestion * 4).toInt().coerceIn(0, 20))
                    Text(
                        "قناة ${sc.channel.toString().padStart(2, ' ')}  $bar  ${fmt(sc.congestion, 1)}",
                        fontSize = 12.sp, color = if (sc.channel == c.best) Good else Muted
                    )
                }
                Text("القيمة الأقل = تشويش أقل. غيّر قناة الراوتر من إعدادات الواي فاي.", color = Muted, fontSize = 11.sp)
            }
        }

        if (!s.running && s.issues.isNotEmpty()) {
            Section("المشاكل المكتشفة والتوصيات") {
                s.issues.forEach {
                    val (mark, col) = when (it.severity) {
                        2 -> "⛔" to Bad
                        1 -> "⚠" to Warn
                        else -> "✔" to Good
                    }
                    Text("$mark ${it.text}", color = col, fontSize = 14.sp)
                }
            }
        }

        if (s.retesting) Text("⏳ جاري إعادة القياس... (حوالي 30 ثانية)", color = Warn)
        if (s.error.isNotEmpty()) Text(s.error, color = Bad)

        s.comparison?.let { cmp ->
            Section("نسبة تحسين الشبكة") {
                val pct = cmp.overallPct
                val col = if (pct >= 0) Good else Bad
                val label = if (pct >= 0) "تحسّن +${fmt(pct, 0)}%" else "تراجع ${fmt(-pct, 0)}%"
                Text(label, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = col)
                cmp.rows.forEach { r ->
                    val sign = if (r.changePct >= 0) "+" else "-"
                    Text(
                        "${r.name}: ${fmt(r.before, 1)} ← ${fmt(r.after, 1)} ${r.unit}  ($sign${fmt(kotlin.math.abs(r.changePct), 0)}%)",
                        fontSize = 13.sp, color = if (r.changePct >= 0) Good else Bad
                    )
                }
                Text(
                    "القياس يتأثر بحالة الإنترنت لحظة الاختبار. أعد القياس أكثر من مرة للحصول على رقم أدق.",
                    color = Muted, fontSize = 11.sp
                )
            }
        }

        if (!s.running) {
            Button(onClick = { vm.goto(Screen.WEB) }, modifier = Modifier.fillMaxWidth()) {
                Text("فتح صفحة الراوتر وتطبيق DNS و القناة")
            }
            if (s.before != null) {
                Button(
                    onClick = { vm.retest() }, enabled = !s.retesting,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("إعادة الاختبار وحساب نسبة التحسين") }
            }
            OutlinedButton(onClick = { vm.back() }, modifier = Modifier.fillMaxWidth()) {
                Text("رجوع / إعادة الفحص")
            }
        }
    }
}

@Composable
private fun WebScreen(vm: MainViewModel) {
    val s = vm.state
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var isPageLoading by remember { mutableStateOf(true) }
    val busyFlag = remember { booleanArrayOf(false) }
    val logs = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    val p = s.dns?.primary?.ip ?: ""
    val sec = s.dns?.secondary?.ip ?: ""
    val ip = s.routerIp
    val bestCh = s.channels?.best

    fun applyNow() {
        val w = web ?: return
        if (busyFlag[0]) return
        scope.launch {
            busy = true; busyFlag[0] = true; logs.clear()
            var ok = false
            for (attempt in 1..2) {
                ok = try {
                    val clean = (s.channels?.scores?.firstOrNull { it.channel == bestCh }?.congestion ?: 9.0) < 0.4
                    AutoApply(w, ip, p, sec, bestCh, clean) { logs.add(it) }.execute()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logs.add("❌ خطأ: ${e.message}")
                    false
                }
                if (ok || attempt == 2) break
                logs.add("↻ إعادة المحاولة...")
                w.loadUrl("http://$ip/")
                kotlinx.coroutines.delay(6000)
            }
            busyFlag[0] = false; busy = false
            if (ok) {
                vm.retest()
                vm.goto(Screen.ANALYSIS)
            }
        }
    }

    LaunchedEffect(web) {
        if (web != null && vm.state.autoPending && p.isNotEmpty() && sec.isNotEmpty()) {
            vm.consumeAuto()
            logs.add("▶ بدء التطبيق التلقائي...")
            applyNow()
        }
    }

    LaunchedEffect(busy) { web?.keepScreenOn = busy }
    BackHandler(enabled = busy) { }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("تطبيق التغييرات تلقائياً") },
            text = {
                Text(
                    "سيتم ضبط DNS على ${ltr(p)} و ${ltr(sec)}" +
                            (if (bestCh != null) " وقناة الواي فاي على $bestCh" else "") +
                            " ثم إعادة تشغيل الراوتر. سينقطع الإنترنت حوالي دقيقة أو دقيقتين. لا تغلق التطبيق أثناء ذلك."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirm = false; applyNow() }) { Text("متابعة") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("إلغاء") } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = { confirm = true },
                enabled = !busy && p.isNotEmpty() && sec.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "جاري التطبيق..." else "تطبيق التغييرات تلقائياً + إعادة التشغيل") }
            logs.takeLast(6).forEach { Text(it, fontSize = 12.sp, color = Muted) }
            Text(
                "DNS المقترح: Primary ${ltr(p)}  |  Secondary ${ltr(sec)}",
                fontWeight = FontWeight.Bold, color = Good, fontSize = 14.sp
            )
            s.channels?.let { Text("أفضل قناة واي فاي: ${it.best}", color = Good, fontSize = 14.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(p)) }, modifier = Modifier.weight(1f)) {
                    Text("نسخ Primary", fontSize = 12.sp)
                }
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(sec)) }, modifier = Modifier.weight(1f)) {
                    Text("نسخ Secondary", fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        web?.evaluateJavascript(RouterJs.fillDns(p, sec)) { r ->
                            val msg = when (r?.trim('"')) {
                                "ok", "octets" -> "تمت تعبئة DNS، اضغط حفظ/Apply في صفحة الراوتر"
                                "one" -> "تمت تعبئة حقل واحد فقط، أكمل الثاني يدوياً ثم احفظ"
                                else -> "لم أجد حقول DNS. افتح صفحة إعدادات الإنترنت/WAN/DHCP واختر DNS يدوي ثم اضغط مرة أخرى"
                            }
                            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("تعبئة DNS تلقائياً", fontSize = 12.sp) }
                OutlinedButton(
                    onClick = { web?.evaluateJavascript(RouterJs.login(s.user, s.pass), null) },
                    modifier = Modifier.weight(1f)
                ) { Text("إعادة تعبئة الدخول", fontSize = 12.sp) }
            }
            OutlinedButton(
                onClick = {
                    web?.evaluateJavascript(RouterJs.dump()) { r ->
                        val txt = try { org.json.JSONTokener(r).nextValue() as? String } catch (_: Exception) { null }
                        if (txt.isNullOrEmpty()) {
                            Toast.makeText(ctx, "تعذر قراءة الصفحة", Toast.LENGTH_SHORT).show()
                        } else {
                            clipboard.setText(AnnotatedString(txt))
                            Toast.makeText(ctx, "تم نسخ هيكل الصفحة، الصقه في المحادثة", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("نسخ هيكل الصفحة الحالية (للتطوير)", fontSize = 12.sp) }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        var authTries = 0
                        webChromeClient = object : WebChromeClient() {
                            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult): Boolean {
                                if (!busyFlag[0]) return super.onJsAlert(view, url, message, result)
                                logs.add("رسالة الراوتر: $message")
                                result.confirm()
                                return true
                            }

                            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult): Boolean {
                                if (!busyFlag[0]) return super.onJsConfirm(view, url, message, result)
                                result.confirm()
                                return true
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isPageLoading = true
                            }

                            override fun onReceivedHttpAuthRequest(
                                view: WebView, handler: HttpAuthHandler, host: String?, realm: String?
                            ) {
                                if (authTries < 3) {
                                    authTries++
                                    handler.proceed(s.user, s.pass)
                                } else handler.cancel()
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                isPageLoading = false
                                view.evaluateJavascript(RouterJs.login(s.user, s.pass), null)
                            }

                            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                                if (error.url.contains(ip)) handler.proceed() else handler.cancel()
                            }
                        }
                        loadUrl("http://$ip/")
                        web = this
                    }
                }
            )
            if (isPageLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Accent
                )
            }
        }
    }
}
