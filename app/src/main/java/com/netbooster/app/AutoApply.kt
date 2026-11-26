package com.netbooster.app

import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.resume

private val NB_LIB = """
var NB={
 docs:function(){var r=[];(function w(x){try{r.push(x.document);for(var i=0;i<x.frames.length;i++)w(x.frames[i]);}catch(e){}})(window);return r;},
 vis:function(e){return !!(e.offsetWidth||e.offsetHeight||(e.getClientRects&&e.getClientRects().length));},
 setv:function(el,v){try{var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d.set.call(el,v);}catch(e){el.value=v;}['input','change','blur','keyup'].forEach(function(n){el.dispatchEvent(new Event(n,{bubbles:true}));});},
 // دالة مخصصة لراوترات TP-Link لتعبئة الـ DNS
 setTpLinkDns: function(p, s) {
    var found = 0;
    NB.docs().forEach(function(d) {
        var in1 = d.querySelector('input[name="dnsserver1"], input[name="dns1"], input[id="dnsserver1"]');
        var in2 = d.querySelector('input[name="dnsserver2"], input[name="dns2"], input[id="dnsserver2"]');
        // إذا لم نجدها بالاسم، نبحث عنها بجوار كلمة DNS
        if(!in1) {
            var ins = d.querySelectorAll('input');
            for(var i=0; i<ins.length; i++){
                var prev = ins[i].previousElementSibling;
                if(prev && prev.innerText && prev.innerText.indexOf('DNS Server') !== -1 && prev.innerText.indexOf('Secondary') === -1) in1 = ins[i];
                if(prev && prev.innerText && prev.innerText.indexOf('Secondary DNS') !== -1) in2 = ins[i];
            }
        }
        if (in1) { NB.setv(in1, p); found++; }
        if (in2) { NB.setv(in2, s); found++; }
    });
    return found;
 },
 // دالة مخصصة لتغيير القناة
 setTpLinkChannel: function(n) {
    var found = 0;
    NB.docs().forEach(function(d) {
        var sel = d.querySelector('select[name="channel"], select[id="channel"], select[name="Channel"]');
        if (sel) {
            for(var i=0; i<sel.options.length; i++) {
                if(sel.options[i].value == String(n) || sel.options[i].text == String(n)) {
                    sel.selectedIndex = i;
                    sel.dispatchEvent(new Event('change', {bubbles:true}));
                    found++;
                    break;
                }
            }
        }
    });
    return found;
 },
 // دالة ذكية للبحث عن زر الحفظ والضغط عليه
 clickSave: function() {
    var clicked = 0;
    NB.docs().forEach(function(d) {
        var btns = d.querySelectorAll('input[type="button"], input[type="submit"], button, a');
        for(var i=0; i<btns.length; i++) {
            var txt = (btns[i].value || btns[i].innerText || '').toLowerCase().trim();
            if(txt === 'save' || txt === 'حفظ' || txt === 'apply') {
                btns[i].click();
                clicked++;
            }
        }
    });
    return clicked;
 },
 clickReboot: function() {
    var clicked = 0;
    NB.docs().forEach(function(d) {
        var btns = d.querySelectorAll('input[type="button"], input[type="submit"], button, a');
        for(var i=0; i<btns.length; i++) {
            var txt = (btns[i].value || btns[i].innerText || '').toLowerCase().trim();
            if(txt === 'reboot' || txt === 'إعادة تشغيل' || txt === 'restart') {
                btns[i].click();
                clicked++;
                return; // نكتفي بضغطة واحدة
            }
        }
    });
    return clicked;
 }
};
""".trimIndent()

class AutoApply(
    private val web: WebView,
    private val routerIp: String,
    private val dnsP: String,
    private val dnsS: String,
    private val channel: Int?,
    private val cleanAir: Boolean,
    private val log: (String) -> Unit
) {

    private suspend fun js(expr: String): String = suspendCancellableCoroutine { cont ->
        val code = "(function(){" + NB_LIB + " try{return String(" + expr + ");}catch(e){return 'err:'+e;}})()"
        web.post {
            web.evaluateJavascript(code) { r ->
                val v = try { (JSONTokener(r).nextValue() as? String) ?: "" } catch (_: Exception) { "" }
                if (cont.isActive) cont.resume(v)
            }
        }
    }

    private suspend fun until(expr: String, tries: Int = 15, gap: Long = 1000, ok: (String) -> Boolean): String {
        var r = ""
        for (i in 0 until tries) {
            r = js(expr)
            if (ok(r)) return r
            delay(gap)
        }
        return r
    }

    private suspend fun tcpOk(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { it.connect(InetSocketAddress(host, port), 1500); true }
        } catch (_: Exception) { false }
    }

    private suspend fun waitState(host: String, port: Int, up: Boolean, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (tcpOk(host, port) == up) return true
            delay(2000)
        }
        return false
    }

    suspend fun execute(): Boolean {
        log("انتظار تحميل صفحة الراوتر...")
        // التأكد من وجود كلمة Logout لضمان تسجيل الدخول
        val inn = until("NB.find('logout','left')?'yes':'no'", 20, 1000) { it == "yes" }
        if (inn != "yes") {
            log("❌ لم تظهر صفحة الراوتر. تأكد من كلمة المرور.")
            return false
        }

        var channelSaved = false
        var dnsSaved = false

        // ---------- 1. تعديل DNS (الانتقال المباشر لصفحة DHCP) ----------
        log("▶ الدخول المباشر لصفحة DHCP لتعديل DNS...")
        withContext(Dispatchers.Main) { web.loadUrl("http://$routerIp/LanDhcpServerRpm.htm") }
        delay(3000) // انتظار تحميل الإطارات (Frames)
        
        log("جاري تعبئة حقول DNS...")
        val dnsRes = until("NB.setTpLinkDns('$dnsP', '$dnsS')", 10) { it != "0" && it != "err" }
        if (dnsRes != "0" && dnsRes != "err") {
            log("✔ تم تعبئة الحقول. جاري الضغط على Save...")
            delay(1000)
            val saveRes = js("NB.clickSave()")
            if (saveRes != "0") {
                delay(2000)
                dnsSaved = true
                log("✔ تم حفظ إعدادات الـ DNS بنجاح.")
            } else {
                log("⚠ لم أتمكن من العثور على زر الحفظ (Save) في صفحة DHCP.")
            }
        } else {
            log("⚠ لم أتمكن من العثور على حقول DNS في صفحة DHCP.")
        }

        // ---------- 2. تعديل القناة (الانتقال المباشر لصفحة الواي فاي) ----------
        if (channel != null) {
            log("▶ الدخول المباشر لصفحة الواي فاي لتعديل القناة إلى $channel...")
            withContext(Dispatchers.Main) { web.loadUrl("http://$routerIp/WlanBasicRpm.htm") }
            delay(3000)
            
            log("جاري تحديد القناة...")
            val chRes = until("NB.setTpLinkChannel($channel)", 10) { it != "0" && it != "err" }
            if (chRes != "0" && chRes != "err") {
                log("✔ تم تحديد القناة. جاري الضغط على Save...")
                delay(1000)
                val saveRes = js("NB.clickSave()")
                if (saveRes != "0") {
                    delay(2000)
                    channelSaved = true
                    log("✔ تم حفظ القناة بنجاح.")
                } else {
                    log("⚠ لم أتمكن من العثور على زر الحفظ (Save) في صفحة الواي فاي.")
                }
            } else {
                log("⚠ لم أتمكن من العثور على قائمة القنوات في الصفحة.")
            }
        }

        if (!channelSaved && !dnsSaved) {
            log("❌ فشل التطبيق في حفظ أي إعدادات. يرجى المحاولة يدوياً.")
            return false
        }

        // ---------- 3. إعادة التشغيل ----------
        log("▶ الدخول لصفحة إعادة التشغيل...")
        withContext(Dispatchers.Main) { web.loadUrl("http://$routerIp/SysRebootRpm.htm") }
        delay(3000)
        
        log("جاري إرسال أمر إعادة التشغيل...")
        val rebootRes = js("NB.clickReboot()")
        if (rebootRes != "0") {
            log("⏳ تم إرسال أمر إعادة التشغيل. الراوتر سيفصل الآن...")
        } else {
            log("⚠ لم أجد زر Reboot. قد تحتاج لإعادة تشغيله يدوياً.")
            return true
        }

        // انتظار فصل الراوتر
        delay(5000)
        waitState(routerIp, 80, up = false, timeoutMs = 30_000)
        log("⏳ بانتظار عودة الراوتر للعمل (قد تستغرق دقيقتين)...")
        
        if (!waitState(routerIp, 80, up = true, timeoutMs = 180_000)) {
            log("⚠ الراوتر لم يعد في الوقت المتوقع. تأكد من اتصالك.")
            return true
        }
        
        log("✔ عاد الراوتر للعمل. جاري فحص سرعة الإنترنت الجديدة...")
        waitState("1.1.1.1", 443, up = true, timeoutMs = 120_000)
        return true
    }
}
