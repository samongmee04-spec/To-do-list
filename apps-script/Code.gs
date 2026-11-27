// ===== Monster Farm backend (Google Apps Script bound to sheet "Pure_Life") =====
// วิธีตั้งค่า (ทำครั้งเดียว):
// 1) เปิดชีท Pure_Life → Extensions → Apps Script → วางโค้ดนี้ทั้งหมดแทนของเดิม
// 2) แก้ค่า SECRET ด้านล่างเป็นรหัสลับของคุณเอง
// 3) Deploy → New deployment → Web app → Execute as: Me, Who has access: Anyone → Deploy
// 4) คัดลอก Web app URL ไปวางในไฟล์ monster-farm.html ที่ตัวแปร API_URL
// 5) รัน setupSheets() หนึ่งครั้งจากเมนู Run เพื่อสร้างหัวตารางให้ครบ (ถ้ายังไม่มี)

const SECRET = 'CHANGE_ME';
const PROJECT_SHEET = 'Project';
const MONSTERS = 'Monsters';
const IDEAS = 'Ideas';
const HISTORY = 'History';
const PHASE_NAMES = ['Egg', 'Baby', 'Child', 'Adult', 'Super'];
const MON_HEADERS = ['Code', 'Name', 'Type', 'Species', 'IconFileId', 'Outfit', 'Color', 'Phase', 'Note', 'Url'];
const IDEA_HEADERS = ['ID', 'Name', 'Type', 'Note', 'Created', 'Status'];
const HIST_HEADERS = ['Time', 'Code', 'Action', 'From', 'To', 'Note'];

function setupSheets() {
  const ss = SpreadsheetApp.getActive();
  [[MONSTERS, MON_HEADERS], [IDEAS, IDEA_HEADERS], [HISTORY, HIST_HEADERS]].forEach(([name, headers]) => {
    let sh = ss.getSheetByName(name) || ss.insertSheet(name);
    sh.getRange(1, 1, 1, headers.length).setValues([headers]);
    sh.setFrozenRows(1);
  });
}

function doGet(e) {
  return json_(readAll_());
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    const body = JSON.parse(e.postData.contents || '{}');
    if (body.token !== SECRET) return json_({ ok: false, error: 'bad token' });
    let result;
    switch (body.action) {
      case 'setPhase':   result = setPhase_(body); break;
      case 'setStyle':   result = setStyle_(body); break;
      case 'setUrl':     result = setStyle_(body); break;
      case 'addIdea':    result = addIdea_(body); break;
      case 'updateIdea': result = updateIdea_(body); break;
      case 'hatchIdea':  result = hatchIdea_(body); break;
      default: return json_({ ok: false, error: 'unknown action' });
    }
    return json_(Object.assign({ ok: true }, result, readAll_()));
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  } finally {
    lock.releaseLock();
  }
}

// ---------- อ่านข้อมูลทั้งหมด ----------
function readAll_() {
  const ss = SpreadsheetApp.getActive();
  const proj = ss.getSheetByName(PROJECT_SHEET).getDataRange().getValues();
  const projects = [];
  proj.slice(3).forEach(r => {           // แถว 4 ลงไปคือข้อมูลโปรเจกต์
    const code = String(r[1] || '').trim();
    if (!code) return;
    projects.push({ no: r[0], code, name: String(r[2] || ''), type: String(r[4] || ''), desc: String(r[6] || ''), birthday: fmtDate_(r[7]) });
  });
  return { projects, monsters: rows_(MONSTERS, MON_HEADERS), ideas: rows_(IDEAS, IDEA_HEADERS), history: rows_(HISTORY, HIST_HEADERS).slice(-50) };
}

function rows_(name, headers) {
  const sh = SpreadsheetApp.getActive().getSheetByName(name);
  if (!sh || sh.getLastRow() < 2) return [];
  return sh.getRange(2, 1, sh.getLastRow() - 1, headers.length).getValues()
    .filter(r => r.join('') !== '')
    .map(r => { const o = {}; headers.forEach((h, i) => o[h] = r[i]); return o; });
}

// ---------- เขียนข้อมูล ----------
function upsertMonster_(code, patch) {
  const sh = SpreadsheetApp.getActive().getSheetByName(MONSTERS);
  const data = sh.getDataRange().getValues();
  let row = -1;
  for (let i = 1; i < data.length; i++) if (String(data[i][0]) === code) { row = i + 1; break; }
  if (row < 0) { row = sh.getLastRow() + 1; sh.getRange(row, 1).setValue(code); }
  const cur = sh.getRange(row, 1, 1, MON_HEADERS.length).getValues()[0];
  const obj = {}; MON_HEADERS.forEach((h, i) => obj[h] = cur[i]);
  Object.assign(obj, patch);
  sh.getRange(row, 1, 1, MON_HEADERS.length).setValues([MON_HEADERS.map(h => obj[h] === undefined ? '' : obj[h])]);
  return obj;
}

function log_(code, action, from, to, note) {
  SpreadsheetApp.getActive().getSheetByName(HISTORY)
    .appendRow([new Date(), code, action, from || '', to || '', note || '']);
}

function setPhase_(b) {
  const cur = findMonster_(b.code);
  const phase = Math.max(0, Math.min(4, Number(b.phase)));
  upsertMonster_(b.code, { Phase: phase, Note: b.note !== undefined ? b.note : (cur ? cur.Note : '') });
  log_(b.code, 'phase', cur ? cur.Phase : '', phase, b.note || '');
  return {};
}

function setStyle_(b) {
  const patch = {};
  ['Outfit', 'Color', 'IconFileId', 'Species', 'Url', 'Note'].forEach(k => {
    const key = k.charAt(0).toLowerCase() + k.slice(1);
    if (b[key] !== undefined) patch[k] = b[key];
  });
  upsertMonster_(b.code, patch);
  log_(b.code, 'style', '', '', Object.keys(patch).join(','));
  return {};
}

function findMonster_(code) {
  return rows_(MONSTERS, MON_HEADERS).find(m => String(m.Code) === code);
}

function addIdea_(b) {
  const id = 'I' + Date.now();
  SpreadsheetApp.getActive().getSheetByName(IDEAS)
    .appendRow([id, b.name || '', b.type || 'App', b.note || '', new Date(), 'egg']);
  log_(id, 'idea', '', 'egg', b.name || '');
  return { id };
}

function updateIdea_(b) {
  const sh = SpreadsheetApp.getActive().getSheetByName(IDEAS);
  const data = sh.getDataRange().getValues();
  for (let i = 1; i < data.length; i++) {
    if (String(data[i][0]) === b.id) {
      if (b.name !== undefined) sh.getRange(i + 1, 2).setValue(b.name);
      if (b.type !== undefined) sh.getRange(i + 1, 3).setValue(b.type);
      if (b.note !== undefined) sh.getRange(i + 1, 4).setValue(b.note);
      return {};
    }
  }
  throw new Error('ไม่พบไข่ ' + b.id);
}

// ฟักไข่ = สร้างรหัสโปรเจกต์ใหม่ต่อจากเดิม, เพิ่มแถวในแท็บ Project และตั้งเป็น Baby
function hatchIdea_(b) {
  const ss = SpreadsheetApp.getActive();
  const ideas = ss.getSheetByName(IDEAS);
  const data = ideas.getDataRange().getValues();
  let idx = -1;
  for (let i = 1; i < data.length; i++) if (String(data[i][0]) === b.id) { idx = i; break; }
  if (idx < 0) throw new Error('ไม่พบไข่ ' + b.id);
  const idea = data[idx];

  const proj = ss.getSheetByName(PROJECT_SHEET);
  const lastNo = proj.getRange('A4:A').getValues().filter(r => r[0] !== '').length;
  const no = lastNo + 1;
  const code = 'P' + no;
  proj.getRange(3 + no, 1, 1, 8).setValues([[no, code, idea[1], '', idea[2], '', idea[3], new Date()]]);
  ideas.getRange(idx + 1, 6).setValue('hatched ' + code);
  upsertMonster_(code, { Name: idea[1], Type: idea[2], Phase: 1, Note: idea[3] });
  log_(code, 'hatch', 'egg', 'Baby', b.id);
  return { code };
}

function fmtDate_(v) {
  return v instanceof Date ? Utilities.formatDate(v, 'Asia/Bangkok', 'dd/MM/yyyy') : String(v || '');
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
