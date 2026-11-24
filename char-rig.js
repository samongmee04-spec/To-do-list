/* ============================================================
 * char-rig.js — ตัวละครแยกชิ้น (หัว ลำตัว แขน×2 ขา×2) สำหรับฉากบ้านหน้าแรก
 *  - ไม่ยุ่งกับตำแหน่ง/การเดินของแอป (walkChar ยังคุมเอง) แค่ "ท่าทาง" ของแขนขา
 *  - ถ้ารูปชิ้นส่วนโหลดไม่ครบ จะไม่เปิดใช้ แล้วแอปใช้รูปตัวละครเดิม (char.webp) ต่อ
 *  - ไม่พลิกภาพซ้าย-ขวา เพื่อไม่ให้ตัวหนังสือบนเสื้อกลับด้าน ใช้เอียงหัว/โน้มตัวแทน
 * ============================================================ */
(function (global) {
  'use strict';

  /* ตำแหน่ง/จุดหมุนของแต่ละชิ้น ในกล่องตัวละคร 220x290 (หัวบนสุด y≈12, เท้า y=272) */
  const SPEC = {
    head:  { x: 41.15, y: 12.22,  w: 137.25, h: 141.25, px: 68.75, py: 130.0 },
    torso: { x: 62.7,  y: 134.22, w: 94.6,   h: 94.4,   px: 47.2,  py: 88.0 },
    armL:  { x: 58.25, y: 164.72, w: 22.35,  h: 53.85,  px: 11.25, py: 7.5 },
    armR:  { x: 139.45, y: 164.72, w: 22.05, h: 53.85,  px: 11.25, py: 7.5 },
    legL:  { x: 78.67, y: 218.1,  w: 23.93,  h: 53.9,   px: 14.03, py: 4.12 },
    legR:  { x: 113.67, y: 218.1, w: 23.79,  h: 53.9,   px: 14.03, py: 4.12 }
  };
  const UPPER_PIVOT = { x: 110, y: 222.22 };
  const ORDER = ['legL', 'legR', 'armL', 'armR', 'torso', 'head'];
  const UPPER = ['armL', 'armR', 'torso', 'head'];
  const TOP_Y = 12.2, FOOT_Y = 272;                 // ส่วนที่มองเห็น: หัวสุด → เท้า
  const cl = (v, a, b) => Math.min(b, Math.max(a, v));

  function init(charBox, opts) {
    opts = Object.assign({ base: 'assets/ch-' }, opts || {});
    const rigEl = document.createElement('div');
    rigEl.className = 'charrig';
    rigEl.style.cssText =
      'position:absolute;left:0;top:0;width:220px;height:290px;transform-origin:0 0;display:none;pointer-events:none;';
    const wrapBody = document.createElement('div');
    wrapBody.style.cssText = 'position:absolute;inset:0;transform-origin:110px 272px;will-change:transform;';
    const upper = document.createElement('div');
    upper.style.cssText =
      `position:absolute;inset:0;transform-origin:${UPPER_PIVOT.x}px ${UPPER_PIVOT.y}px;will-change:transform;`;
    rigEl.appendChild(wrapBody);

    const el = {};
    ORDER.forEach((k, i) => {
      const p = SPEC[k];
      const d = document.createElement('div');
      d.style.cssText =
        `position:absolute;left:${p.x}px;top:${p.y}px;width:${p.w}px;height:${p.h}px;` +
        `transform-origin:${p.px}px ${p.py}px;will-change:transform;z-index:${i};` +
        `background:center/100% 100% no-repeat url("${opts.base}${k}.webp");`;
      el[k] = d;
    });
    wrapBody.appendChild(el.legL);
    wrapBody.appendChild(el.legR);
    wrapBody.appendChild(upper);
    UPPER.forEach(k => upper.appendChild(el[k]));
    charBox.appendChild(rigEl);

    const rig = { on: false, el: rigEl };
    let lastH = 0;

    function fit() {
      const H = charBox.offsetHeight, W = charBox.offsetWidth;
      if (!H || H === lastH) return;
      lastH = H;
      const sc = H / (FOOT_Y - TOP_Y);
      rigEl.style.transform = `scale(${sc})`;
      rigEl.style.left = (W / 2 - 110 * sc).toFixed(2) + 'px';
      rigEl.style.top = (-TOP_Y * sc).toFixed(2) + 'px';
    }

    /* p: {phase, go, lean, mode, vy, time, swing, dvx, ptrDx, squash, mt}  */
    rig.pose = function (p) {
      fit();
      const amp = cl(p.go, 0, 1);
      const s = Math.sin(p.phase), c = Math.cos(p.phase);
      const m = p.mode;

      let legL = s * 27 * amp, legR = -s * 27 * amp;
      let armL = -s * 30 * amp, armR = s * 30 * amp;
      let liftL = Math.max(0, c) * 9 * amp, liftR = Math.max(0, -c) * 9 * amp;
      let bob = -Math.abs(c) * 4 * amp;
      let tilt = s * 1.2 * amp + p.lean * 2.5 * amp;
      let head = p.lean * 7 + s * 2.2 * amp;
      let sy = 1, sx = 1;

      const breathe = Math.sin(p.time * 2.6) * 0.010 * (1 - amp);
      sy += breathe; sx -= breathe * 0.5;

      if (m === 'hold') {
        armL = 5; armR = -5; tilt = 0;
        head = cl(p.ptrDx / 28, -1, 1) * 10;              // เอียงหัวมองนิ้ว
      }
      if (m === 'jump') {
        const up = p.vy > 0;
        legL = up ? 18 : -10; legR = up ? -26 : 10;
        armL = 150 + Math.sin(p.time * 26) * 12;           // โบกมือ
        armR = -28; liftL = liftR = up ? 8 : 2;
        head = p.lean * 4; tilt = 0; bob = 0;
      }
      if (m === 'land') { legL = 6; legR = -6; }
      if (m === 'drag' || m === 'fall') {
        const sw = p.swing;
        legL = 8 + sw * 0.9 + Math.sin(p.time * 9) * 5;
        legR = -8 + sw * 0.9 - Math.sin(p.time * 9) * 5;
        armL = 155 + Math.sin(p.time * 10) * 14;           // ชูแขนเหมือนถูกยก
        armR = -155 - Math.sin(p.time * 10 + 1) * 14;
        tilt = cl(-p.dvx * 0.03, -10, 10) + sw * 0.15;
        head = cl(-p.dvx * 0.05, -14, 14);
        bob = 0; liftL = liftR = 0;
      }

      const raised = Math.abs(armL) > 90;
      el.armL.style.zIndex = el.armR.style.zIndex = raised ? 9 : 2;

      const q = p.squash || 0;
      if (q > 0) { sy *= 1 + q * 0.10; sx *= 1 - q * 0.07; }
      else if (q < 0) { sy *= 1 + q * 0.14; sx *= 1 - q * 0.10; }

      el.legL.style.transform = `translateY(${-liftL}px) rotate(${legL}deg)`;
      el.legR.style.transform = `translateY(${-liftR}px) rotate(${legR}deg)`;
      el.armL.style.transform = `rotate(${armL}deg)`;
      el.armR.style.transform = `rotate(${armR}deg)`;
      el.head.style.transform = `rotate(${head}deg)`;
      upper.style.transform = `translateY(${bob}px) rotate(${tilt}deg)`;
      wrapBody.style.transform = `scale(${sx},${sy})`;
    };

    /* โหลดรูปให้ครบก่อนค่อยเปิดใช้ ถ้าขาดชิ้นไหนก็ไม่เปิด (แอปใช้รูปเดิมต่อ) */
    rig.ready = Promise.all(ORDER.map(k => new Promise((res, rej) => {
      const i = new Image();
      i.onload = res; i.onerror = () => rej(new Error('missing ' + k));
      i.src = el[k].style.backgroundImage.replace(/^url\("?|"?\)$/g, '');
    }))).then(() => {
      rig.on = true; rigEl.style.display = 'block'; charBox.classList.add('rigon'); lastH = 0; fit();
      return true;
    }).catch(() => false);

    return rig;
  }

  global.CharRig = { init: init };
})(window);
