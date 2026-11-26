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

/** مكتبة JavaScript صغيرة تبحث عن العناصر بنصها الظاهر (وليس بأرقام ثابتة) داخل كل الإطارات. */
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
  return 'nolabel';
 },
 checkManual:function(){
  var ls=NB.labels(/^(use the following dns|use following dns|manual dns|use these dns)/i);
  for(var i=0;i<ls.length;i++){
   var l=ls[i],cb=null,d=l.ownerDocument;
   if(l.htmlFor)cb=d.getElementById(l.htmlFor);
   if(!cb)cb=l.querySelector('input[type=checkbox],input[type=radio]');
   if(!cb&&l.parentElement){var q=l.parentElement.querySelectorAll('input[type=checkbox],input[type=radio]');if(q.length===1)cb=q[0];}
   if(cb){if(!cb.checked)cb.click();return cb.checked?'set':'clicked';}
  }
  return 'none';
 },
 setChannel:function(n){
  n=String(n);
  function pick(s){var hit=[].slice.call(s.options).filter(function(o){return o.text.trim()===n||o.value===n;})[0];if(!hit)return false;s.value=hit.value;s.dispatchEvent(new Event('change',{bubbles:true}));return true;}
  var ls=NB.labels(/^channel:?$/i);
  for(var i=0;i<ls.length;i++){var ss=NB.following(ls[i],'select');if(ss.length&&pick(ss[0]))return 'set';}
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
 readIp:function(re){
  var ls=NB.labels(re);
  for(var i=0;i<ls.length;i++){
   var ins=NB.textIns(ls[i]);
   if(!ins.length)continue;
   if(ins[0].maxLength>0&&ins[0].maxLength<=3&&ins.length>=4)return ins.slice(0,4).map(function(x){return x.value;}).join('.');
   return ins[0].value;
  }
  return '';
 },
 page:function(){var t=NB.docs().map(function(d){return d.body?d.body.innerText:'';}).join('\n');return t.length>30000?t.slice(0,30000):t;}
};
""".trimIndent()

/**
 * يطبق التغييرات داخل صفحة الراوتر عبر WebView بالبحث عن العناصر بنصوصها (Wireless, WAN, Save, Reboot...).
 * مصمم بحيث يتوقف بأمان لو لم يجد العناصر المتوقعة (لا يضغط حفظ إلا بعد تعبئة الحقول بنجاح).
 */
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
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun waitState(host: String, port: Int, up: Boolean, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (tcpOk(host, port) == up) return true
            delay(2000)
        }
        return false
    }

    /**
     * يضبط DNS من قسم DHCP فقط (بدون WAN): يضغط على "DHCP" فتظهر حقول Primary/Secondary DNS،
     * يملؤها، يحفظ، ثم يعيد قراءة القيمة للتأكد. يرجع true فقط لو القيمة اتحفظت فعلاً.
     */
    private suspend fun setDnsOnDhcp(): Boolean {
        if (!click(listOf("dhcp"), "left", tries = 6)) {
            log("⚠ لم أجد قسم DHCP، تم تخطي DNS.")
            return false
        }
        delay(1500)
        var a = until("NB.fillIp(/^(primary|preferred) dns/i,'$dnsP')", 4) { it == "ok" }
        if (a != "ok") {
            // بعض الموديلات تفتح صفحة فرعية اسمها DHCP Settings؛ نجربها كخطوة احتياطية فقط
            if (click(listOf("dhcp settings"), "left", tries = 3)) delay(1500)
            a = until("NB.fillIp(/^(primary|preferred) dns/i,'$dnsP')", 8) { it == "ok" }
        }
        if (a != "ok") {
            log("⚠ لم أجد حقول DNS في صفحة DHCP (لم يتم تغيير شيء).")
            return false
        }
        val b = until("NB.fillIp(/^(secondary|alternate) dns/i,'$dnsS')", 4) { it == "ok" }
        if (b != "ok") log("⚠ تم ملء الـ DNS الأساسي فقط")
        if (!click(listOf("save"), "last", tries = 6)) {
            log("⚠ لم أجد زر الحفظ في صفحة DHCP، لم يتم تغيير الـ DNS.")
            return false
        }
        delay(3500)
        // إعادة فتح صفحة DHCP وقراءة القيمة للتأكد أنها اتحفظت
        click(listOf("dhcp"), "left", tries = 3)
        delay(1500)
        var v = until("NB.readIp(/^(primary|preferred) dns/i)", 4) { it.isNotEmpty() }
        if (v.isEmpty() && click(listOf("dhcp settings"), "left", tries = 2)) {
            delay(1500)
            v = until("NB.readIp(/^(primary|preferred) dns/i)", 4) { it.isNotEmpty() }
        }
        return if (v == dnsP) {
            log("✔ تم حفظ DNS في DHCP وتأكيده: $dnsP / $dnsS")
            true
        } else {
            log("⚠ لم يتأكد حفظ الـ DNS (قرأت: ${v.ifEmpty { "فارغ" }}).")
            false
        }
    }

    suspend fun execute(): Boolean {
        log("التحقق من تسجيل الدخول...")
        val inn = until("NB.find('logout','left')?'yes':'no'", 45, 800) { it == "yes" }
        if (inn != "yes") {
            log("❌ لم تظهر صفحة الراوتر بعد تسجيل الدخول. تأكد من اسم المستخدم وكلمة المرور ثم أعد المحاولة.")
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
            if (!nav) {
                log("⚠ لم أجد صفحة إعدادات الواي فاي، تم تخطي القناة.")
            } else {
                delay(1200)
                val r = until("NB.setChannel($channel)", 12, 700) { it == "set" }
                if (r == "set") {
                    // تحسينات الأداء في نفس الصفحة: وضع الواي فاي (b/g/n) وعرض القناة
                    // 40MHz (أسرع) فقط لو الجو نظيف، وإلا 20MHz لأن 40 يزيد التداخل في الأماكن المزدحمة
                    val m = js("NB.setSel('mode','11bgn')")
                    if (m == "set") log("✔ وضع الواي فاي: 11bgn")
                    val w = js("NB.setSel('channel width', '" + (if (cleanAir) "auto" else "^20") + "')")
                    if (w == "set") log("✔ عرض القناة: " + (if (cleanAir) "Auto 20/40MHz (الجو نظيف)" else "20MHz (لتقليل التداخل)"))
                    delay(500)
                }
                if (r == "set" && click(listOf("save"), "last")) {
                    delay(4000)
                    channelSaved = true
                    log("✔ تم حفظ القناة $channel")
                    // قوة الإرسال العالية (صفحة Wireless Advanced) - اختيارية، تُتخطى لو غير موجودة
                    if (click(listOf("wireless"), "left", tries = 3)) {
                        delay(900)
                        if (click(listOf("wireless advanced", "advanced"), "left", tries = 3)) {
                            delay(1200)
                            val t = until("NB.setSel('transmit power','high')", 4) { it == "set" || it == "already" }
                            if (t == "set" && click(listOf("save"), "last", tries = 3)) {
                                delay(3000)
                                log("✔ قوة الإرسال: High")
                            } else if (t != "already") log("ℹ تم تخطي قوة الإرسال (غير متاحة في هذا الموديل)")
                        }
                    }
                } else {
                    log("⚠ لم أجد قائمة القناة، تم تخطي القناة (لم يتم تغيير شيء).")
                }
            }
        }

        // ---------- DNS (من صفحة DHCP) ----------
        log("ضبط DNS من صفحة DHCP: $dnsP / $dnsS ...")
        dnsSaved = setDnsOnDhcp()

        if (!channelSaved && !dnsSaved) {
            log("❌ لم يتم تطبيق أي تغيير، لذلك لن يتم إعادة تشغيل الراوتر.")
            return false
        }

        // ---------- التحقق من صفحة Status ----------
        log("التحقق من الإعدادات الجديدة...")
        click(listOf("status"), "left", tries = 6)
        val chRegex = channel?.let { Regex("Channel:\\s*$it\\b") }
        val page = until("NB.page()", 12, 4000) { p ->
            (!channelSaved || chRegex == null || chRegex.containsMatchIn(p))
        }
        if (channelSaved && chRegex != null) log(if (chRegex.containsMatchIn(page)) "✔ تأكيد القناة: $channel" else "⚠ لم أتمكن من تأكيد القناة من صفحة الحالة")

        // ---------- إعادة التشغيل ----------
        log("إعادة تشغيل الراوتر...")
        val navRb = click(listOf("system tools"), "left") && run {
            delay(900)
            click(listOf("reboot"), "left")
        }
        if (!navRb) {
            log("⚠ لم أجد صفحة Reboot. أعد تشغيل الراوتر يدوياً.")
            return true
        }
        delay(1200)
        if (!click(listOf("reboot"), "last", tries = 6)) {
            log("⚠ لم أجد زر Reboot. أعد تشغيل الراوتر يدوياً.")
            return true
        }
        delay(3000)
        log("⏳ بانتظار إغلاق الراوتر ثم عودته (قد تستغرق دقيقة أو أكثر)...")
        waitState(routerIp, 80, up = false, timeoutMs = 40_000)
        if (!waitState(routerIp, 80, up = true, timeoutMs = 240_000)) {
            log("⚠ الراوتر لم يعد في الوقت المتوقع. تأكد من اتصالك بالواي فاي.")
            return true
        }
        log("الراوتر عاد للعمل، بانتظار الإنترنت...")
        waitState("1.1.1.1", 443, up = true, timeoutMs = 180_000)
        log("✔ اكتمل. جاري القياس بعد التحسين...")
        return true
    }
}
