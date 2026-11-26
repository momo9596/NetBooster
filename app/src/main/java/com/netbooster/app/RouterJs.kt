package com.netbooster.app

import org.json.JSONObject

/** سكربتات JavaScript تُحقن في صفحة الراوتر لتعبئة الدخول وحقول DNS. */
object RouterJs {

    /** يملأ اسم المستخدم والباسورد ويضغط زر الدخول مرة واحدة فقط. */
    fun login(user: String, pass: String): String {
        val u = JSONObject.quote(user)
        val p = JSONObject.quote(pass)
        return """
(function(u,p){
  var n=0;
  function setv(el,v){
    try{ var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value'); d.set.call(el,v); }catch(e){ el.value=v; }
    el.dispatchEvent(new Event('input',{bubbles:true}));
    el.dispatchEvent(new Event('change',{bubbles:true}));
  }
  var iv=setInterval(function(){
    n++;
    var pw=document.querySelector('input[type=password]');
    if(pw){
      clearInterval(iv);
      var inputs=[].slice.call(document.querySelectorAll('input'));
      var idx=inputs.indexOf(pw), us=null;
      for(var i=idx-1;i>=0;i--){
        var t=(inputs[i].type||'text').toLowerCase();
        if(t==='text'||t==='email'||t===''){us=inputs[i];break;}
      }
      if(us) setv(us,u);
      setv(pw,p);
      setTimeout(function(){
        var btns=[].slice.call(document.querySelectorAll('button,input[type=submit],input[type=button],a'));
        var re=/log|sign|submit|ok|enter|دخول/i;
        var b=btns.filter(function(x){return re.test((x.id||'')+' '+(x.value||'')+' '+(x.innerText||'')+' '+(x.className||''));})[0];
        if(b){ b.click(); }
        else if(pw.form){ try{ pw.form.submit(); }catch(e){} }
      },500);
    } else if(n>14){ clearInterval(iv); }
  },500);
})($u,$p);
""".trimIndent()
    }

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

    /**
     * يستخرج هيكل الصفحة المفتوحة (الحقول والأزرار والروابط) من كل الإطارات، بدون كلمات المرور
     */
    fun dump(): String = """
(function(){
  var out=[];
  function walk(w,path){
    try{
      var d=w.document;
      var o={frame:path,url:w.location.href,title:d.title,inputs:[],selects:[],buttons:[],links:[],clickable:[],forms:[]};
      [].slice.call(d.querySelectorAll('input')).forEach(function(i){
        var t=(i.type||'text').toLowerCase();
        if(t==='password') return;
        var k=((i.id||'')+(i.name||'')).toLowerCase();
        var e={t:t,id:i.id,name:i.name,cls:i.className};
        if(i.maxLength>0 && i.maxLength<1000) e.max=i.maxLength;
        if(t==='radio'||t==='checkbox'){ e.checked=i.checked; e.value=i.value; }
        else if(k.indexOf('dns')>=0){ e.value=i.value; }
        o.inputs.push(e);
      });
      [].slice.call(d.querySelectorAll('select')).forEach(function(s){
        var opts=[].slice.call(s.options).slice(0,40).map(function(x){return x.value+'='+x.text;});
        o.selects.push({id:s.id,name:s.name,selected:s.value,options:opts});
      });
      [].slice.call(d.querySelectorAll('button,input[type=button],input[type=submit]')).forEach(function(b){
        o.buttons.push({id:b.id,name:b.name,text:(b.innerText||b.value||'').slice(0,40),onclick:(b.getAttribute('onclick')||'').slice(0,120)});
      });
      [].slice.call(d.querySelectorAll('a')).slice(0,80).forEach(function(a){
        o.links.push({id:a.id,text:(a.innerText||'').slice(0,40),href:(a.getAttribute('href')||'').slice(0,100),onclick:(a.getAttribute('onclick')||'').slice(0,80)});
      });
      [].slice.call(d.querySelectorAll('[onclick]:not(a):not(button):not(input)')).slice(0,60).forEach(function(x){
        o.clickable.push({tag:x.tagName,id:x.id,cls:x.className,text:(x.innerText||'').slice(0,30),onclick:(x.getAttribute('onclick')||'').slice(0,100)});
      });
      [].slice.call(d.forms).forEach(function(f){ o.forms.push({id:f.id,action:f.getAttribute('action'),method:f.method}); });
      out.push(o);
      for(var i=0;i<w.frames.length;i++) walk(w.frames[i],path+'/'+i);
    }catch(e){ out.push({frame:path,error:String(e)}); }
  }
  walk(window,'top');
  var s=JSON.stringify(out);
  return s.length>40000?s.slice(0,40000):s;
})();
""".trimIndent()
}
