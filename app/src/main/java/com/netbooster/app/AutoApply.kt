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
 
 clickMenu: function(id) {
    var c = 0;
    NB.docs().forEach(function(d) {
        var el = d.getElementById(id);
        if(el) { el.click(); c++; }
    });
    return c;
 },
 
 checkDnsExist: function() {
    var found = 0;
    NB.docs().forEach(function(d) { if(d.querySelector('input[name="dnsserver1"]')) found++; });
    return found;
 },
 
 fillTpLinkDns: function(p, s) {
    var c = 0;
    NB.docs().forEach(function(d) {
        var in1 = d.querySelector('input[name="dnsserver1"], input[id="dnsserver1"]');
        var in2 = d.querySelector('input[name="dnsserver2"], input[id="dnsserver2"]');
        if (in1) { NB.setv(in1, p); c++; }
        if (in2) { NB.setv(in2, s); c++; }
    });
    return c;
 },
 
 checkChanExist: function() {
    var found = 0;
    NB.docs().forEach(function(d) { if(d.querySelector('select[name="channel"]')) found++; });
    return found;
 },

 setTpLinkChannel: function(n) {
    var c = 0;
    NB.docs().forEach(function(d) {
        var sel = d.querySelector('select[name="channel"], select[id="channel"]');
        if (sel) {
            for(var i=0; i<sel.options.length; i++) {
                if(sel.options[i].value == String(n)) {
                    sel.selectedIndex = i;
                    sel.dispatchEvent(new Event('change', {bubbles:true}));
                    c++; break;
                }
            }
        }
    });
    return c;
 },

 // دالة جديدة لتفعيل أقصى مدى (Max Range)
 setMaxRange: function() {
    var changed = 0;
    NB.docs().forEach(function(d) {
        // 1. تثبيت عرض القناة على 20MHz (يعطي مدى أطول)
        var selWidth = d.querySelector('select[name="chanWidth"]');
        if(selWidth) {
            for(var i=0; i<selWidth.options.length; i++) {
                if(selWidth.options[i].text.indexOf('20') !== -1) {
                    selWidth.selectedIndex = i; selWidth.dispatchEvent(new Event('change', {bubbles:true})); changed++;
                    break;
                }
            }
        }
        // 2. رفع قوة الإرسال لأقصى درجة (High)
        var selPower = d.querySelector('select[name="txpower"]') || d.querySelector('select[name="txPower"]');
        if(selPower) {
            for(var i=0; i<selPower.options.length; i++) {
                if(selPower.options[i].value.toLowerCase() === 'high' || selPower.options[i].text.indexOf('High') !== -1) {
                    selPower.selectedIndex = i; selPower.dispatchEvent(new Event('change', {bubbles:true})); changed++;
                    break;
                }
            }
        }
    });
    return changed;
 },
 
 clickSave: function() {
    var c = 0;
    NB.docs().forEach(function(d) {
        var btns = d.querySelectorAll('input[type="button"], input[type="submit"], button');
        for(var i=0; i<btns.length; i++) {
            var txt = (btns[i].value || btns[i].innerText || '').toLowerCase().trim();
            if(txt === 'save' || txt === 'apply') { btns[i].click(); c++; return; }
        }
    });
    return c;
 },
 
 clickReboot: function() {
    var c = 0;
    NB.docs().forEach(function(d) {
        var btns = d.querySelectorAll('input[type="button"], input[type="submit"], button');
        for(var i=0; i<btns.length; i++) {
            var txt = (btns[i].value || btns[i].innerText || '').toLowerCase().trim();
            if(txt === 'reboot' || txt === 'restart') { btns[i].click(); c++; return; }
        }
    });
    return c;
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
    private val maxRange: Boolean, // تم إضافة هذا المتغير
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

    private suspend fun waitForElement(expr: String, tries: Int = 20, gap: Long = 1000): Boolean {
        for (i in 0 until tries) {
            val r = js(expr)
            if (r != "0" && r != "err" && r.isNotEmpty()) return true
            delay(gap)
        }
        return false
    }

    private suspend fun tcpOk(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try { Socket().use { it.connect(InetSocketAddress(host, port), 1500); true } } catch (_: Exception) { false }
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
        log("التحقق من الاتصال...")
        if (!waitForElement("NB.clickMenu('menu_logout')", 15)) return false

        var channelSaved = false
        var dnsSaved = false
        var maxRangeApplied = false

        // 1. تعديل DNS
        log("▶ تعديل DNS...")
        js("NB.clickMenu('menu_dhcpserver')")
        if (waitForElement("NB.checkDnsExist()", 10)) {
            js("NB.fillTpLinkDns('$dnsP', '$dnsS')")
            delay(500)
            if (js("NB.clickSave()") != "0") { delay(3000); dnsSaved = true; log("✔ تم حفظ DNS") }
        }

        // 2. تعديل القناة (الأساسية)
        if (channel != null) {
            log("▶ تعديل القناة...")
            js("NB.clickMenu('menu_wlbasic')")
            if (waitForElement("NB.checkChanExist()", 10)) {
                js("NB.setTpLinkChannel($channel)")
                delay(500)
                if (js("NB.clickSave()") != "0") { delay(3000); channelSaved = true; log("✔ تم حفظ القناة") }
            }
        }

        // 3. تفعيل وضع التغطية القصوى (أقصى مدى)
        if (maxRange) {
            log("▶ تفعيل وضع أقصى مدى (Max Range)...")
            js("NB.clickMenu('menu_wladv')") // صفحة الإعدادات المتقدمة
            delay(3000)
            if (js("NB.setMaxRange()") != "0") {
                delay(1000)
                if (js("NB.clickSave()") != "0") {
                    delay(3000)
                    maxRangeApplied = true
                    log("✔ تم رفع قوة الإرسال وتوسيع التغطية")
                }
            } else {
                log("⚠ لم أجد إعدادات الطاقة المتقدمة")
            }
        }

        if (!channelSaved && !dnsSaved && !maxRangeApplied) {
            log("❌ فشل حفظ الإعدادات.")
            return false
        }

        // 4. إعادة التشغيل
        log("▶ إعادة التشغيل...")
        js("NB.clickMenu('menu_restart')")
        if (waitForElement("NB.clickReboot()", 10)) {
            log("⏳ الراوتر يعيد التشغيل...")
        } else return true

        delay(5000)
        waitState(routerIp, 80, up = false, timeoutMs = 30_000)
        log("⏳ بانتظار عودة الراوتر...")
        if (!waitState(routerIp, 80, up = true, timeoutMs = 120_000)) return true
        
        log("✔ عاد الراوتر للعمل.")
        waitState("1.1.1.1", 443, up = true, timeoutMs = 60_000)
        return true
    }
}
