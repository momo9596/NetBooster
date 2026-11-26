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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlin.math.abs

private val Bg = Color(0xFF0B131E)
private val CardBg = Color(0xFF162230)
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
private fun SetupScreen(vm: MainViewModel) {
    val s = vm.state
    val ctx = LocalContext.current
    fun hasLocation() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.start(it) }

    LaunchedEffect(Unit) {
        if (s.routerIp.isNotEmpty()) {
            if (hasLocation()) vm.start(true) else launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            vm.detectRouter()
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("NetBooster", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Accent)
        Spacer(modifier = Modifier.height(20.dp))
        CircularProgressIndicator(color = Accent, strokeWidth = 4.dp, modifier = Modifier.size(60.dp))
        Text("جاري التحقق من الراوتر والبدء...", color = Good, modifier = Modifier.padding(top = 24.dp))
    }
}

@Composable
private fun AnalysisScreen(vm: MainViewModel) {
    val s = vm.state
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        
        AnimatedVisibility(visible = s.comparison != null) {
            s.comparison?.let { cmp ->
                val pct = cmp.overallPct
                val isGood = pct >= 0
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (isGood) Color(0xFF132D24) else Color(0xFF2D1313)),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, if(isGood) Good else Bad)
                ) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (isGood) "تم تحسين الشبكة!" else "لم يحدث تحسن", color = if(isGood) Good else Warn, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(if (isGood) "+${fmt(pct, 0)}%" else "${fmt(pct, 0)}%", fontSize = 64.sp, fontWeight = FontWeight.Black, color = if(isGood) Good else Bad)
                    }
                }
            }
        }

        if (s.running || s.retesting) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Accent)
                    Text(if (s.retesting) "جاري القياس بعد التعديل..." else "جاري الفحص...", color = Accent, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }

        if (!s.running && s.comparison == null) {
            Button(onClick = { vm.retest() }, modifier = Modifier.fillMaxWidth()) { Text("إعادة القياس") }
        }
    }
}

@Composable
private fun WebScreen(vm: MainViewModel) {
    val s = vm.state
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
    val profile = s.profile ?: knownRouters.last() // هنا نستخدم ملف الراوتر المكتشف

    fun applyNow() {
        val w = web ?: return
        if (busyFlag[0]) return
        scope.launch {
            busy = true; busyFlag[0] = true; logs.clear()
            var ok = try {
                AutoApply(w, ip, p, sec, bestCh, profile) { logs.add(it) }.execute()
            } catch (e: Exception) { false }
            
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
            delay(4000)
            applyNow()
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("جاري التطبيق التلقائي (${profile.brand})", fontSize = 18.sp, color = Accent)
            Card(colors = CardDefaults.cardColors(containerColor = Color.Black)) {
                Column(Modifier.padding(12.dp).fillMaxWidth().height(80.dp)) {
                    logs.takeLast(3).forEach { Text(it, fontSize = 12.sp, color = if (it.contains("✔")) Good else Muted) }
                }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        var authTries = 0
                        webViewClient = object : WebViewClient() {
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
            if (busy) {
                Box(Modifier.fillMaxSize().background(Bg.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }
        }
    }
}
