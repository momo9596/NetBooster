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
 setv:function(el,v){try{var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d.set.call(el,v);}catch(e){el.value=v;}['input','change','blur','keyup'].forEach(function(n){el.dispatchEvent(new Event(n,{bubbles:true}));});},
 
 clickMenu: function(id) {
    var c = 0;
    NB.docs().forEach(function(d) {
        var el = d.getElementById(id);
        if(el) { el.click(); c++; }
    });
    return c;
 },
 
 checkDnsExist: function(id1) {
    var found = 0;
    NB.docs().forEach(function(d) { 
        if(d.querySelector('input[name="'+id1+'"], input[id="'+id1+'"]')) found++; 
    });
    return found;
 },
 
 fillDns: function(p, s, id1, id2) {
    var c = 0;
    NB.docs().forEach(function(d) {
        var in1 = d.querySelector('input[name="'+id1+'"], input[id="'+id1+'"]');
        var in2 = d.querySelector('input[name="'+id2+'"], input[id="'+id2+'"]');
        if (in1) { NB.setv(in1, p); c++; }
        if (in2) { NB.setv(in2, s); c++; }
    });
    return c;
 },
 
 checkChanExist: function(id) {
    var found = 0;
    NB.docs().forEach(function(d) { 
        if(d.querySelector('select[name="'+id+'"], select[id="'+id+'"]')) found++; 
    });
    return found;
 },

 setChannel: function(n, id) {
    var c = 0;
    NB.docs().forEach(function(d) {
        var sel = d.querySelector('select[name="'+id+'"], select[id="'+id+'"]');
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
 
 clickSave: function(txt) {
    var c = 0;
    NB.docs().forEach(function(d) {
        var btns = d.querySelectorAll('input[type="button"], input[type="submit"], button');
        for(var i=0; i<btns.length; i++) {
            var bTxt = (btns[i].value || btns[i].innerText || '').toLowerCase().trim();
            if(bTxt === txt.toLowerCase()) { btns[i].click(); c++; return; }
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
    private val profile: RouterProfile, // استقبال ملف الراوتر هنا
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

    private suspend fun waitForElement(expr: String, tries: Int = 15, gap: Long = 1000): Boolean {
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

        // 1. DNS
        log("▶ تعديل DNS (${profile.brand})...")
        js("NB.clickMenu('${profile.dnsMenuId}')")
        if (waitForElement("NB.checkDnsExist('${profile.dnsField1}')", 10)) {
            js("NB.fillDns('$dnsP', '$dnsS', '${profile.dnsField1}', '${profile.dnsField2}')")
            delay(500)
            if (js("NB.clickSave('${profile.saveButton}')") != "0") { 
                delay(3000); dnsSaved = true; log("✔ تم حفظ DNS") 
            }
        }

        // 2. Channel
        if (channel != null) {
            log("▶ تعديل القناة...")
            js("NB.clickMenu('${profile.wifiMenuId}')")
            if (waitForElement("NB.checkChanExist('${profile.channelField}')", 10)) {
                js("NB.setChannel($channel, '${profile.channelField}')")
                delay(500)
                if (js("NB.clickSave('${profile.saveButton}')") != "0") { 
                    delay(3000); channelSaved = true; log("✔ تم حفظ القناة") 
                }
            }
        }

        if (!channelSaved && !dnsSaved) return false

        // 3. Reboot
        log("▶ إعادة التشغيل...")
        js("NB.clickMenu('menu_restart')")
        if (waitForElement("NB.clickReboot()", 10)) log("⏳ الراوتر يعيد التشغيل...")
        else return true

        delay(5000)
        waitState(routerIp, 80, up = false, timeoutMs = 30_000)
        if (!waitState(routerIp, 80, up = true, timeoutMs = 120_000)) return true
        
        waitState("1.1.1.1", 443, up = true, timeoutMs = 60_000)
        return true
    }
}
