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
 absLeft:function(e){var x=e.getBoundingClientRect().left,w=e.ownerDocument.defaultView;try{while(w&&w!==w.top&&w.frameElement){x+=w.frameElement.getBoundingClientRect().left;w=w.parent;}}catch(er){}return x;},
 find:function(text,pick){
  var c=[];
  NB.docs().forEach(function(d){
   [].slice.call(d.querySelectorAll('a,button,input[type=button],input[type=submit],li,span,div,td,label,p,b')).forEach(function(e){
    if(!NB.vis(e)||NB.txt(e)!==text)return;
    var deeper=[].slice.call(e.children).some(function(k){return NB.txt(k)===text;});
    if(!deeper)c.push(e);
   });
  });
  if(!c.length)return null;
  if(pick==='left')c.sort(function(a,b){return NB.absLeft(a)-NB.absLeft(b);});
  return pick==='last'?c[c.length-1]:c[0];
 },
 clickAny:function(list,pick){for(var i=0;i<list.length;i++){var e=NB.find(list[i],pick);if(e){e.click();return 'clicked:'+list[i];}}return 'notfound';},
 setv:function(el,v){try{var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d.set.call(el,v);}catch(e){el.value=v;}['input','change','blur','keyup'].forEach(function(n){el.dispatchEvent(new Event(n,{bubbles:true}));});},
 following:function(l,sel){return [].slice.call(l.ownerDocument.querySelectorAll(sel)).filter(function(i){return NB.vis(i)&&!i.disabled&&(l.compareDocumentPosition(i)&4);});},
 textIns:function(l){return NB.following(l,'input').filter(function(i){var t=(i.type||'text').toLowerCase();return t==='text'||t==='tel'||t==='';});},
 labels:function(re){
  var r=[];
  NB.docs().forEach(function(d){
   [].slice.call(d.querySelectorAll('label,td,th,span,div,p,dt,b')).forEach(function(l){
    if(!NB.vis(l))return;
    var t=(l.innerText||'').replace(/\s+/g,' ').trim();
    if(t.length>45||!re.test(t))return;
    var deeper=[].slice.call(l.children).some(function(k){return re.test((k.innerText||'').replace(/\s+/g,' ').trim());});
    if(!deeper)r.push(l);
   });
  });
  return r;
 },
 fillIp:function(re,ip){
  var ls=NB.labels(re);
  for(var i=0;i<ls.length;i++){
   var ins=NB.textIns(ls[i]);
   if(!ins.length)continue;
   if(ins[0].maxLength>0&&ins[0].maxLength<=3&&ins.length>=4){var o=ip.split('.');for(var k=0;k<4;k++)NB.setv(ins[k],o[k]);return 'ok';}
   NB.setv(ins[0],ip);return 'ok';
  }
  // خطة بديلة (Fallback) لراوتر TP-Link
  var target = /primary|preferred|dns server/i.test(re.source) && !/secondary/i.test(re.source) ? 'dnsserver1' : 'dnsserver2';
  var inputs = document.querySelectorAll('input[name="'+target+'"], input[id="'+target+'"]');
  if(inputs.length > 0){ NB.setv(inputs[0], ip); return 'ok'; }
  return 'nolabel';
 },
 setChannel:function(n){
  n=String(n);
  function pick(s){var hit=[].slice.call(s.options).filter(function(o){return o.text.trim()===n||o.value===n;})[0];if(!hit)return false;s.value=hit.value;s.dispatchEvent(new Event('change',{bubbles:true}));return true;}
  var ls=NB.labels(/^channel:?$/i);
  for(var i=0;i<ls.length;i++){var ss=NB.following(ls[i],'select');if(ss.length&&pick(ss[0]))return 'set';}
  var ss = document.querySelectorAll('select[name="channel"], select[id="channel"]');
  if(ss.length > 0 && pick(ss[0])) return 'set';
  var r='notfound';
  NB.docs().forEach(function(d){[].slice.call(d.querySelectorAll('select')).forEach(function(s){var k=((s.id||'')+(s.name||'')).toLowerCase();if(r==='notfound'&&NB.vis(s)&&/chan/.test(k)&&!/width|bandwidth/.test(k)&&pick(s))r='set';});});
  return r;
 },
 setSel:function(label,optRe){
  var re=new RegExp('^'+label+':?$','i'),o=new RegExp(optRe,'i');
  var ls=NB.labels(re);
  for(var i=0;i<ls.length;i++){
   var ss=NB.following(ls[i],'select');
   if(!ss.length)continue;
   var s=ss[0],hit=[].slice.call(s.options).filter(function(x){return o.test(x.text.trim());})[0];
   if(!hit)return 'nooption';
   if(s.value===hit.value)return 'already';
   s.value=hit.value;s.dispatchEvent(new Event('change',{bubbles:true}));return 'set';
  }
  return 'nolabel';
 },
 page:function(){var t=NB.docs().map(function(d){return d.body?d.body.innerText:'';}).join('\n');return t.length>30000?t.slice(0,30000):t;}
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

    private suspend fun until(expr: String, tries: Int = 12, gap: Long = 700, ok: (String) -> Boolean): String {
        var r = ""
        for (i in 0 until tries) {
            r = js(expr)
            if (ok(r)) return r
            delay(gap)
        }
        return r
    }

    private suspend fun click(names: List<String>, pick: String, tries: Int = 12): Boolean {
        val arr = names.joinToString(",") { "'$it'" }
        val r = until("NB.clickAny([$arr],'$pick')", tries) { it.startsWith("clicked") }
        return r.startsWith("clicked")
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

    private suspend fun setDnsOnDhcp(): Boolean {
        // محاولة الدخول من القائمة
        val nav = click(listOf("dhcp"), "left", tries = 4) && run {
            delay(900)
            click(listOf("dhcp settings"), "left", tries = 4)
        }
        // إذا فشل الدخول من القائمة، ندخل بالرابط المباشر لراوتر TP-Link
        if (!nav) {
            log("ℹ الانتقال المباشر إلى إعدادات DHCP...")
            withContext(Dispatchers.Main) { web.loadUrl("http://$routerIp/LanDhcpServerRpm.htm") }
            delay(2500)
        }
        delay(1500)
        
        // تحديث خوارزمية البحث عن DNS
        val a = until("NB.fillIp(/^(primary|preferred|dns server)/i,'$dnsP')", 10) { it == "ok" }
        if (a != "ok") {
            log("ℹ لم أجد حقول DNS في صفحة DHCP")
            return false
        }
        val b = until("NB.fillIp(/^(secondary|alternate|secondary dns server)/i,'$dnsS')", 4) { it == "ok" }
        if (b != "ok") log("⚠ تم ملء الـ DNS الأساسي فقط")
        
        if (!click(listOf("save"), "last", tries = 6)) {
            log("⚠ لم أجد زر الحفظ في صفحة DHCP")
            return false
        }
        delay(3500)
        click(listOf("dhcp settings"), "left", tries = 3)
        delay(1500)
        
        val v = until("NB.readIp(/^(primary|preferred|dns server)/i)", 6) { it.isNotEmpty() }
        if (v == dnsP) log("✔ تم حفظ DNS في DHCP وتأكيده: $dnsP")
        else log("⚠ تم الحفظ لكن لم أتمكن من تأكيد القيمة (قرأت: $v)")
        return true
    }

    suspend fun execute(): Boolean {
        log("التحقق من تسجيل الدخول...")
        val inn = until("NB.find('logout','left')?'yes':'no'", 45, 800) { it == "yes" }
        if (inn != "yes") {
            log("❌ لم تظهر صفحة الراوتر بعد تسجيل الدخول. تأكد من اسم المستخدم وكلمة المرور.")
            return false
        }

        var channelSaved = false
        var dnsSaved = false

        // ---------- القناة ----------
        if (channel != null) {
            log("ضبط قناة الواي فاي إلى $channel ...")
            val nav = click(listOf("wireless"), "left") && run {
                delay(900)
                click(listOf("basic settings", "wireless settings"), "left")
            }
            // إذا فشل الدخول من القائمة، ندخل بالرابط المباشر
            if (!nav) {
                log("ℹ الانتقال المباشر إلى إعدادات الواي فاي...")
                withContext(Dispatchers.Main) { web.loadUrl("http://$routerIp/WlanBasicRpm.htm") }
                delay(2500)
            }
            delay(1200)
            val r = until("NB.setChannel($channel)", 12, 700) { it == "set" }
            if (r == "set") {
                val m = js("NB.setSel('mode','11bgn')")
                if (m == "set") log("✔ وضع الواي فاي: 11bgn")
                val w = js("NB.setSel('channel width', '" + (if (cleanAir) "auto" else "^20") + "')")
                if (w == "set") log("✔ عرض القناة: " + (if (cleanAir) "Auto 20/40MHz" else "20MHz"))
                delay(500)
            }
            if (r == "set" && click(listOf("save"), "last")) {
                delay(4000)
                channelSaved = true
                log("✔ تم حفظ القناة $channel")
            } else {
                log("⚠ لم أجد قائمة القناة، تم تخطي القناة.")
            }
        }

        // ---------- DNS ----------
        log("ضبط DNS: $dnsP / $dnsS ...")
        dnsSaved = setDnsOnDhcp()

        if (!channelSaved && !dnsSaved) {
            log("❌ لم يتم تطبيق أي تغيير، لذلك لن يتم إعادة تشغيل الراوتر.")
            return false
        }

        // ---------- إعادة التشغيل ----------
        log("إعادة تشغيل الراوتر...")
        val navRb = click(listOf("system tools"), "left") && run {
            delay(900)
            click(listOf("reboot"), "left")
        }
        if (!navRb) {
            log("ℹ الانتقال المباشر لصفحة إعادة التشغيل...")
            withContext(Dispatchers.Main) { web.loadUrl("http://$routerIp/SysRebootRpm.htm") }
            delay(2500)
        }
        delay(1200)
        if (!click(listOf("reboot"), "last", tries = 6)) {
            log("⚠ لم أجد زر Reboot. أعد تشغيل الراوتر يدوياً.")
            return true
        }
        delay(3000)
        log("⏳ بانتظار إغلاق الراوتر ثم عودته (قد تستغرق دقيقة)...")
        waitState(routerIp, 80, up = false, timeoutMs = 40_000)
        if (!waitState(routerIp, 80, up = true, timeoutMs = 240_000)) {
            log("⚠ الراوتر لم يعد في الوقت المتوقع.")
            return true
        }
        log("الراوتر عاد للعمل، بانتظار الإنترنت...")
        waitState("1.1.1.1", 443, up = true, timeoutMs = 180_000)
        log("✔ اكتمل. جاري القياس بعد التحسين...")
        return true
    }
}
