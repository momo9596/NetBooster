// (أعلى الملف كما هو، فقط تأكد من الاستيراد)
package com.netbooster.app
// ... الاستيرادات ...

@Composable
fun App(vm: MainViewModel) { /* ... كما هو ... */ }

@Composable
private fun SetupScreen(vm: MainViewModel) {
    // ... الكود السابق ...
}

// أضف هذا السطر في شاشة التحليل (AnalysisScreen) أو الإعدادات
// (اختياري: يمكنك وضعه كـ Switch في شاشة البداية، وسأضعه في شاشة الويب ليكون تحت السيطرة)
