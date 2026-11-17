/* ใช้เฉพาะตอนเปิดผ่านแอป Launcher บน Android (มี window.HomeBridge)
   1) ไมค์: WebView ไม่มี Web Speech ให้ใช้ จึงส่งไปฟังด้วยระบบของ Android แทน
   2) ดาวน์โหลด: ลิงก์ blob:/data: ที่มี download (.ics, รูป) ส่งไปบันทึกลงโฟลเดอร์ Download
   ในเบราว์เซอร์ปกติไฟล์นี้ไม่ทำอะไรเลย */
(function(){
  if(!window.HomeBridge) return;
  document.documentElement.classList.add('inLauncher');

  const live = {};
  let seq = 0;
  function NativeSR(){
    this.lang = 'th-TH'; this.interimResults = false; this.continuous = false; this.maxAlternatives = 1;
    this.onstart = this.onresult = this.onerror = this.onend = null;
    this._id = 'sr' + (++seq) + '-' + Date.now();
  }
  NativeSR.prototype.start = function(){ live[this._id] = this; HomeBridge.listen(this._id, this.lang || 'th-TH'); };
  NativeSR.prototype.stop  = function(){ HomeBridge.stopListen(this._id); };
  NativeSR.prototype.abort = function(){ HomeBridge.abortListen(this._id); delete live[this._id]; };
  NativeSR.prototype.addEventListener = function(t, fn){ this['on' + t] = fn; };

  window.__nsr = function(id, type, text){
    const r = live[id]; if(!r) return;
    if(type === 'start'){ r.onstart && r.onstart({type:'start'}); }
    else if(type === 'partial' || type === 'final'){
      if(type === 'partial' && !r.interimResults) return;
      const alt = [{transcript:text, confidence:1}];
      alt.isFinal = (type === 'final');
      r.onresult && r.onresult({type:'result', resultIndex:0, results:[alt]});
    }
    else if(type === 'error'){ r.onerror && r.onerror({type:'error', error:text}); }
    else if(type === 'end'){ delete live[id]; r.onend && r.onend({type:'end'}); }
  };
  try{ Object.defineProperty(window, 'SpeechRecognition', {value:NativeSR, configurable:true, writable:true}); }catch(e){ window.SpeechRecognition = NativeSR; }
  try{ Object.defineProperty(window, 'webkitSpeechRecognition', {value:NativeSR, configurable:true, writable:true}); }catch(e){ window.webkitSpeechRecognition = NativeSR; }

  document.addEventListener('click', function(ev){
    const a = ev.target && ev.target.closest && ev.target.closest('a[download]');
    if(!a) return;
    const href = a.href || '';
    if(!/^(blob|data):/.test(href)) return;
    ev.preventDefault();
    const name = a.getAttribute('download') || 'file';
    fetch(href).then(r => r.blob()).then(b => new Promise((ok, bad) => {
      const fr = new FileReader();
      fr.onload = () => ok({mime:b.type, b64:String(fr.result).split(',')[1] || ''});
      fr.onerror = bad;
      fr.readAsDataURL(b);
    })).then(f => HomeBridge.saveFile(name, f.mime, f.b64))
      .catch(() => alert('บันทึกไฟล์ไม่สำเร็จ'));
  }, true);
})();
