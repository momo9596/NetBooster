package com.netbooster.app

import org.json.JSONObject

object RouterJs {
    // ... (اترك دالة login كما هي) ...

    /** يبحث عن حقول DNS في الصفحة المفتوحة ويملأها. يرجع: none / ok / octets / one */
    fun fillDns(primary: String, secondary: String): String {
        val a = JSONObject.quote(primary)
        val b = JSONObject.quote(secondary)
        return """
(function(a,b){
  function setv(el,v){
    try{ var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value'); d.set.call(el,v); }catch(e){ el.value=v; }
    el.dispatchEvent(new Event('input',{bubbles:true}));
    el.dispatchEvent(new Event('change',{bubbles:true}));
    el.dispatchEvent(new Event('blur',{bubbles:true}));
  }
  var all=[].slice.call(document.querySelectorAll('input')).filter(function(i){
    var k=((i.id||'')+(i.name||'')+(i.placeholder||'')).toLowerCase();
    var t=(i.type||'text').toLowerCase();
    return (k.indexOf('dns')>=0 || k === 'dnsserver1' || k === 'dnsserver2') && t!=='hidden' && t!=='radio' && t!=='checkbox';
  });
  if(all.length===0) return 'none';
  
  // دعم الحقول المكونة من 4 خانات (Octets)
  var small=all.filter(function(i){return i.maxLength>0 && i.maxLength<=3;});
  if(small.length>=8){
    var A=a.split('.'),B=b.split('.');
    for(var i=0;i<4;i++){ setv(small[i],A[i]); setv(small[4+i],B[i]); }
    return 'octets';
  }
  
  var p=null,s=null;
  // البحث بالاسم أو المعرف المعتاد لراوترات TP-Link
  var in1 = document.querySelector('input[name="dnsserver1"], input[id="dnsserver1"]');
  var in2 = document.querySelector('input[name="dnsserver2"], input[id="dnsserver2"]');
  if(in1) p = in1;
  if(in2) s = in2;

  // إذا لم نجد، نبحث بالكلمات المفتاحية
  if(!p || !s){
    all.forEach(function(i){
      var k=((i.id||'')+(i.name||'')+(i.placeholder||'')+(i.value||'')).toLowerCase();
      var label = i.previousElementSibling ? (i.previousElementSibling.innerText||'') : '';
      if(!p && (k.indexOf('dns')>=0 && k.indexOf('2')<0)) p=i;
      else if(!s && (k.indexOf('dns')>=0 && k.indexOf('2')>=0)) s=i;
    });
  }
  
  if(!p) p=all[0];
  if(!s) s=all.filter(function(x){return x!==p;})[0];
  
  if(p) setv(p,a);
  if(s){ setv(s,b); return 'ok'; }
  return 'one';
})($a,$b);
""".trimIndent()
    }
    // ... (اترك باقي دوال RouterJs كما هي) ...
}
