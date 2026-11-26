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
 txt:function(e){return (e.innerText||e.value||'').replace(/\s+/g,' ').trim().toLowerCase();},
 
 // البحث عن زر القائمة الجانبية بالـ ID والضغط عليه (خاص بـ TP-Link)
 clickMenu: function(id) {
    var c = 0;
    NB.docs().forEach(function(d) {
        var el = d.getElementById(id);
        if(el) { el.click(); c++; }
    });
    return c;
 },
 
 // تعبئة حقول DNS في راوتر TP-Link
 fillTpLinkDns: function(p, s) {
    var c = 0;
    NB.docs().forEach(function(d) {
        var in1 = d.querySelector('input[name="dnsserver1"], input[id="dnsserver1"], input[name="dns1"]');
        var in2 = d.querySelector('input[name="dnsserver2"], input[id="dnsserver2"], input[name="dns2"]');
        if (in1) { NB.setv(in1, p); c++; }
        if (in2) { NB.setv(in2, s); c++; }
    });
    return c;
 },
 
 // تغيير القناة في راوتر TP-Link
 setTpLinkChannel: function(n) {
    var c = 0;
    NB.docs().forEach(function(d) {
        var sel = d.querySelector('select[name="channel"], select[id="channel"]');
        if (sel) {
            for(var i=0; i<sel.options.length; i++) {
                if(sel.options[i].value == String(n) || sel.options[i].text == String(n)) {
                    sel.selectedIndex = i;
                    sel.dispatchEvent(new Event('change', {bubbles:true}));
                    c++;
                    break;
                }
            }
        }
    });
    return c;
 },
 
 // الضغط على زر الحفظ (Save)
 clickSave: function() {
    var c = 0;
    NB.docs().forEach(function(d) {
        var btns = d.querySelectorAll('input[type="button"], input[type="submit"], button');
        for(var i=0; i<btns.length; i++) {
            var txt = (btns[i].value || btns[i].innerText || '').toLowerCase().trim();
            if(txt === 'save' || txt === 'apply' || txt === 'حفظ') {
                btns[i].click();
                c++;
                return;
            }
        }
    });
    return c;
 },
 
 // الضغط على زر إعادة التشغيل (Reboot)
 clickReboot: function() {
    var c = 0;
    NB.docs().forEach(function(d) {
        var btns = d.querySelectorAll('input[type="button"], input[type="submit"], button');
        for(var i=0; i<btns.length; i++) {
            var txt = (btns[i].value || btns[i].innerText || '').toLowerCase().trim();
            if(txt === 'reboot' || txt === 'restart' || txt === 'إعادة تشغيل') {
                btns[i].click();
                c++;
                return;
            }
        }
    });
    return c;
 },

 setv:function(el,v){try{var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d.set.call(el,v);}catch(e){el.value=v;}['input','change','blur','keyup'].forEach(function(n){el.dispatchEvent(new Event(n,{bubbles:true}));});}
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
        // التأكد من وجود زر الخروج (Logout) لضمان أننا داخل الصفحة
        var inn = "0"
        for (i in 0..20) {
            inn = js("NB.clickMenu('menu_logout')")
            if (inn != "0") { // وجدنا زر الخروج، إذن نحن داخل الصفحة
                break
            }
            delay(1000)
        }
        
        if (inn == "0") {
            log("❌ لم تظهر صفحة الراوتر أو لم يتم تسجيل الدخول.")
            return false
        }

        var channelSaved = false
        var dnsSaved = false

        // ---------- 1. تعديل DNS ----------
        log("▶ الانتقال إلى إعدادات DHCP...")
        js("NB.clickMenu('menu_dhcpserver')")
        delay(4000) // انتظار تحميل الصفحة في الإطار الداخلي

        log("جاري تعبئة حقول DNS...")
        val dnsRes = js("NB.fillTpLinkDns('$dnsP', '$dnsS')")
        if (dnsRes != "0" && dnsRes != "err") {
            log("✔ تم تعبئة الحقول. جاري الحفظ...")
            delay(1000)
            val saveRes = js("NB.clickSave()")
            if (saveRes != "0") {
                delay(3000)
                dnsSaved = true
                log("✔ تم حفظ إعدادات الـ DNS بنجاح.")
            } else {
                log("⚠ لم أتمكن من العثور على زر الحفظ (Save).")
            }
        } else {
            log("⚠ لم أتمكن من العثور على حقول DNS.")
        }

        // ---------- 2. تعديل القناة ----------
        if (channel != null) {
            log("▶ الانتقال إلى إعدادات الواي فاي...")
            js("NB.clickMenu('menu_wlbasic')")
            delay(4000)
            
            log("جاري تحديد القناة $channel...")
            val chRes = js("NB.setTpLinkChannel($channel)")
            if (chRes != "0" && chRes != "err") {
                log("✔ تم تحديد القناة. جاري الحفظ...")
                delay(1000)
                val saveRes = js("NB.clickSave()")
                if (saveRes != "0") {
                    delay(3000)
                    channelSaved = true
                    log("✔ تم حفظ القناة بنجاح.")
                } else {
                    log("⚠ لم أتمكن من العثور على زر الحفظ (Save).")
                }
            } else {
                log("⚠ لم أتمكن من العثور على قائمة القنوات.")
            }
        }

        if (!channelSaved && !dnsSaved) {
            log("❌ فشل التطبيق في حفظ أي إعدادات. يرجى المحاولة يدوياً.")
            return false
        }

        // ---------- 3. إعادة التشغيل ----------
        log("▶ الانتقال لصفحة إعادة التشغيل...")
        js("NB.clickMenu('menu_restart')")
        delay(4000)
        
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
        log("⏳ بانتظار عودة الراوتر للعمل (قد تستغرق دقيقة)...")
        
        if (!waitState(routerIp, 80, up = true, timeoutMs = 180_000)) {
            log("⚠ الراوتر لم يعد في الوقت المتوقع. تأكد من اتصالك.")
            return true
        }
        
        log("✔ عاد الراوتر للعمل. جاري فحص سرعة الإنترنت الجديدة...")
        waitState("1.1.1.1", 443, up = true, timeoutMs = 120_000)
        return true
    }
}
