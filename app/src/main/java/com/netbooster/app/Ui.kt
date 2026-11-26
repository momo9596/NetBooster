package com.netbooster.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.http.SslError
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BgGradient = Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF020617)))
private val CardBg = Color(0xFF1E293B)
private val Accent = Color(0xFF38BDF8)
private val Good = Color(0xFF10B981)
private val Warn = Color(0xFFF59E0B)
private val Bad = Color(0xFFEF4444)
private val Muted = Color(0xFF94A3B8)

@Composable
fun App(vm: MainViewModel) {
    val s = vm.state
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Color.Black,
            background = Color(0xFF020617), surface = Color(0xFF0F172A), onSurface = Color.White
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            BackHandler(enabled = s.screen != Screen.SETUP) { vm.back() }
            Box(modifier = Modifier.fillMaxSize().background(BgGradient)) {
                Column(Modifier.fillMaxSize()) {
                    // الهيدر
                    Surface(
                        color = Color(0xFF0F172A).copy(alpha = 0.95f),
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("NetBooster Pro", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Accent, letterSpacing = 1.sp)
                            Text("المطور: محمد إبراهيم", fontSize = 13.sp, color = Muted, fontWeight = FontWeight.Medium)
                        }
                    }

                    when (s.screen) {
                        Screen.SETUP -> SetupScreen(vm)
                        Screen.ANALYSIS -> AnalysisScreen(vm)
                        Screen.WEB -> WebScreen(vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(vm: MainViewModel) {
    val s = vm.state
    val ctx = LocalContext.current
    var showManualInput by remember { mutableStateOf(false) }
    
    fun hasLocation() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { 
        if (s.routerIp.isNotEmpty()) vm.start(it)
    }

    LaunchedEffect(Unit) {
        delay(8000)
        if (s.screen == Screen.SETUP) showManualInput = true
    }

    LaunchedEffect(Unit) {
        if (s.routerIp.isNotEmpty() && !showManualInput) {
            if (hasLocation()) vm.start(true) else launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        
        if (!showManualInput) {
            val infiniteTransition = rememberInfiniteTransition(label = "")
            val alpha by infiniteTransition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "")
            
            CircularProgressIndicator(color = Accent, strokeWidth = 5.dp, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(24.dp))
            Text("جاري التهيئة وفحص الشبكة...", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alpha(alpha))
            Text("يرجى الانتظار بينما نقوم بقياس سرعة الشبكة", color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top=8.dp))
        } else {
            Text("تأخر الاتصال بالراوتر التلقائي", color = Warn, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = s.routerIp,
                onValueChange = { vm.onIp(it) },
                label = { Text("Router IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent)
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { 
                    if (hasLocation()) vm.start(true) else launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("بدء الفحص الآن", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AnalysisScreen(vm: MainViewModel) {
    val s = vm.state
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        
        AnimatedVisibility(visible = s.comparison != null) {
            s.comparison?.let { cmp ->
                val pct = cmp.overallPct
                val isGood = pct >= 0
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (isGood) Color(0xFF064E3B) else Color(0xFF7F1D1D)),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if(isGood) Good else Bad)
                ) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (isGood) "تم تحسين الشبكة بنجاح!" else "لم يحدث تحسن", color = if(isGood) Good else Warn, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(if (isGood) "+${fmt(pct, 0)}%" else "${fmt(pct, 0)}%", fontSize = 72.sp, fontWeight = FontWeight.Black, color = if(isGood) Good else Bad)
                        Text("معدل التحسين الكلي", color = Muted, fontSize = 14.sp)
                    }
                }
            }
        }

        if (s.running || s.retesting) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Accent, strokeWidth = 4.dp)
                    Text(if (s.retesting) "جاري إعادة القياس بعد التعديل..." else "جاري الفحص الشامل...", color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("سجل الفحص", color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                s.steps.forEach { StepRow(it) }
            }
        }

        if (!s.running && s.comparison == null) {
            Button(onClick = { vm.retest() }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) { 
                Text("إعادة القياس", fontSize = 16.sp) 
            }
        }
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
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = color, fontSize = 16.sp)
        Column(Modifier.weight(1f)) {
            Text(st.title, fontWeight = FontWeight.Medium, color = if (st.status == Status.RUNNING) Warn else Color.White, fontSize = 14.sp)
            if (st.detail.isNotEmpty()) Text(st.detail, color = Muted, fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------
// الشاشة الاحترافية الجديدة (الراوتر المباشر + لوحة التحكم)
// ---------------------------------------------------------
@Composable
private fun WebScreen(vm: MainViewModel) {
    val s = vm.state
    var web by remember { mutableStateOf<WebView?>(null) }
    var busy by remember { mutableStateOf(false) }
    var isPageLoading by remember { mutableStateOf(true) }
    val busyFlag = remember { booleanArrayOf(false) }
    val logs = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    val p = s.dns?.primary?.ip ?: "1.1.1.1"
    val sec = s.dns?.secondary?.ip ?: "8.8.8.8"
    val ip = s.routerIp
    val bestCh = s.channels?.best
    val profile = s.profile ?: knownRouters.firstOrNull { it.brand == "TP-Link" } ?: knownRouters.last()

    fun applyNow() {
        val w = web ?: return
        if (busyFlag[0]) return
        scope.launch {
            busy = true; busyFlag[0] = true; logs.clear()
            logs.add("⏳ جاري الاتصال بالراوتر...")
            
            var ok = try {
                AutoApply(w, ip, p, sec, bestCh, profile) { logs.add(it) }.execute()
            } catch (e: Exception) { false }
            
            busyFlag[0] = false; busy = false
            if (ok) {
                logs.add("✅ تم التعديل بنجاح!")
                delay(1500)
                vm.retest()
                vm.goto(Screen.ANALYSIS)
            } else {
                logs.add("❌ فشل التعديل التلقائي.")
            }
        }
    }

    LaunchedEffect(web) {
        if (web != null && vm.state.autoPending && p.isNotEmpty() && sec.isNotEmpty()) {
            vm.consumeAuto()
            delay(3000) // وقت بسيط لتحميل الصفحة الأولى
            applyNow()
        }
    }

    // هنا التصميم الجديد: صفحة الراوتر في الخلفية، ولوحة التحكم تطفو فوقها
    Box(modifier = Modifier.fillMaxSize()) {
        
        // 1. صفحة الراوتر (WebView)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    var authTries = 0
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            isPageLoading = true
                        }
                        override fun onPageFinished(view: WebView, url: String?) {
                            isPageLoading = false
                            view.evaluateJavascript(RouterJs.login(s.user, s.pass), null)
                        }
                        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String?, realm: String?) {
                            if (authTries < 3) { authTries++; handler.proceed(s.user, s.pass) } else handler.cancel()
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

        // 2. شاشة تحميل شفافة (تظهر فقط عندما يبدأ المتصفح في التحميل لأول مرة)
        if (isPageLoading && !busy) {
            Box(Modifier.fillMaxSize().background(Color(0xFF020617).copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
        }

        // 3. لوحة التحكم الزجاجية (HUD) التي تطفو فوق صفحة الراوتر
        if (busy) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xEE0F172A)) // لون داكن شبه شفاف
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // معلومات الإعدادات الجديدة
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("DNS الأساسي", color = Muted, fontSize = 12.sp)
                        Text(p, color = Good, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("القناة الجديدة", color = Muted, fontSize = 12.sp)
                        Text(if (bestCh != null) bestCh.toString() else "تلقائي", color = Good, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                
                Divider(color = Muted.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))
                
                // شريط التحميل والرسالة
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Accent, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = logs.lastOrNull() ?: "جاري المعالجة...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
