// أضف هذا المتغير مع باقي المتغيرات
var isPageLoading by remember { mutableStateOf(true) }

// ثم في AndroidView وداخل WebViewClient:
AndroidView(
    modifier = Modifier.weight(1f).fillMaxWidth(),
    factory = { c ->
        WebView(c).apply {
            // ... (باقي الإعدادات كما هي) ...
            
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    isPageLoading = true // بدء التحميل
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    isPageLoading = false // انتهاء التحميل
                    view.evaluateJavascript(RouterJs.login(s.user, s.pass), null)
                }

                override fun onReceivedHttpAuthRequest(
                    view: WebView, handler: HttpAuthHandler, host: String?, realm: String?
                ) {
                    if (authTries < 3) {
                        authTries++
                        handler.proceed(s.user, s.pass)
                    } else handler.cancel()
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

// وأضف هذا الجزء فوق الـ AndroidView أو داخل Column الخاص بالشاشة (ليغطي الشاشة البيضاء)
if (isPageLoading) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator(color = Accent)
    }
}
