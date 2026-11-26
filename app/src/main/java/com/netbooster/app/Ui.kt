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
import androidx.compose.foundation.background
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

    fun hasLocation() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.start(granted)
    }

    // البدء التلقائي بمجرد فتح الشاشة إذا كان هناك IP وإذن
    LaunchedEffect(Unit) {
        if (s.routerIp.isNotEmpty()) {
            if (hasLocation()) {
                vm.start(true)
            } else {
                launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        } else {
            vm.detectRouter()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("NetBooster", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Accent)
        Text("محسّن وفاحص الشبكة والراوتر", color = Muted)
        
        CircularProgressIndicator(color = Accent, modifier = Modifier.padding(32.dp))
        Text("جاري تجهيز الفحص التلقائي...", color = Good, fontWeight = FontWeight.Bold)

        Section("معلومات الاتصال") {
            LtrField(s.routerIp, "Router IP", vm::onIp, keyboard = KeyboardType.Uri)
            LtrField(s.user, "اسم المستخدم", vm::onUser)
            LtrField(s.pass, "كلمة المرور", vm::onPass, password = true)
        }

        if (s.error.isNotEmpty()) Text(s.error, color = Bad)

        Button(
            onClick = {
                if (hasLocation()) vm.start(true)
                else launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("بدء الفحص يدوياً", fontSize = 17.sp) }
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

        // عرض النتيجة النهائية بشكل ضخم وواضح جداً
        s.comparison?.let { cmp ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                colors = CardDefaults.cardColors(containerColor = if (cmp.overallPct >= 0) Color(0xFF1E3A2B) else Color(0xFF3A1E1E))
            ) {
                Column(
                    Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val pct = cmp.overallPct
                    val label = if (pct >= 0) "تحسّن الشبكة" else "تراجع الشبكة"
                    Text(label, color = Color.White, fontSize = 18.sp)
                    Text(
                        if (pct >= 0) "+${fmt(pct, 0)}%" else "${fmt(pct, 0)}%",
                        fontSize = 50.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (pct >= 0) Good else Bad
                    )
                    Text("الإنترنت الآن أسرع وأكثر استقراراً", color = Muted, fontSize = 12.sp)
                }
            }
            
            Section("تفاصيل التحسين") {
                cmp.rows.forEach { r ->
                    val sign = if (r.changePct >= 0) "+" else "-"
                    Text(
                        "${r.name}: ${fmt(r.before, 1)} ← ${fmt(r.after, 1)} ${r.unit}  ($sign${fmt(kotlin.math.abs(r.changePct), 0)}%)",
                        fontSize = 14.sp, color = if (r.changePct >= 0) Good else Bad
                    )
                }
            }
        }

        Section("خطوات الفحص") { s.steps.forEach { StepRow(it) } }

        if (s.retesting) {
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(color = Accent, modifier = Modifier.padding(end = 10.dp))
                Text("جاري إعادة القياس للتأكد من التحسين...", color = Warn, fontWeight = FontWeight.Bold)
            }
        }

        if (!s.running && s.comparison == null && s.before != null && !s.retesting) {
            Button(
                onClick = { vm.retest() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("إعادة الاختبار وحساب نسبة التحسين") }
        }
    }
}

@Composable
private fun WebScreen(vm: MainViewModel) {
    val s = vm.state
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var web by remember { mutableStateOf<WebView?>(null) }
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
                // العودة التلقائية لشاشة النتائج لبدء الفحص بعد التعديل
                vm.retest()
                vm.goto(Screen.ANALYSIS)
            }
        }
    }

    LaunchedEffect(web) {
        if (web != null && vm.state.autoPending && p.isNotEmpty() && sec.isNotEmpty()) {
            vm.consumeAuto()
            logs.add("▶ بدء التطبيق التلقائي...")
            // ننتظر 5 ثوانٍ لضمان اكتمال الدخول قبل البدء
            kotlinx.coroutines.delay(5000)
            applyNow()
        }
    }

    LaunchedEffect(busy) { web?.keepScreenOn = busy }
    BackHandler(enabled = busy) { }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = { applyNow() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "جاري التعديل وإعادة التشغيل (لا تغلق)..." else "تطبيق التغييرات يدوياً") }
            logs.takeLast(4).forEach { Text(it, fontSize = 12.sp, color = if (it.contains("✔")) Good else Muted) }
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
                        var authTries = 0
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
