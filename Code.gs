/* =====================================================================
   ITN SMS Gateway backend (Google Apps Script)
   Informatics Consultancy Firm, Sierra Leone (ICF-SL)

   Receives records from two places and writes them to the Records sheet:
     1. The field app, when it has internet      {type:'records', records:[...]}
     2. The gateway phone, which forwards SMS    {type:'sms', items:[{key, from, body, received}]}
   Every record carries its own ID, so a record that arrives by SMS and
   again over the internet is saved only once.

   Setup: open a Google Sheet, Extensions > Apps Script, paste this file,
   then Deploy > New deployment > Web app, Execute as: Me,
   Who has access: Anyone. Copy the web app address into the field app
   and the gateway app.
   ===================================================================== */

const SHEET_RECORDS = 'Records';
const SHEET_SMS = 'SMS Log';
const SMS_PREFIX = 'ITN1';
const DISTRICTS = ['Bo', 'Bombali', 'Bonthe', 'Falaba', 'Kailahun', 'Kambia', 'Karene', 'Kenema', 'Koinadugu', 'Kono',
  'Moyamba', 'Port Loko', 'Pujehun', 'Tonkolili', 'Western Area Rural', 'Western Area Urban'];
const HEADERS = ['Record ID', 'Received via', 'Distribution date', 'District', 'PHU', 'Community', 'Head of household',
  'E-token', 'Household size', 'Pregnant women', 'Children under five', 'ITNs issued', 'Distributor ID',
  'Recorded on device', 'SMS sender', 'Gateway received', 'Saved on server'];
const SMS_HEADERS = ['Gateway received', 'Sender', 'Message', 'Result', 'Logged on server'];

function doGet() {
  return json_({ ok: true, app: 'ITN SMS Gateway backend', time: new Date().toISOString() });
}

function doPost(e) {
  let req;
  try { req = JSON.parse(e.postData.contents); }
  catch (err) { return json_({ ok: false, error: 'Request is not JSON' }); }

  const lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    const ss = SpreadsheetApp.getActiveSpreadsheet();
    const sh = sheet_(ss, SHEET_RECORDS, HEADERS);
    const ids = existingIds_(sh);
    const rows = [], results = [];
    const now = new Date();

    if (req.type === 'records') {
      (req.records || []).forEach(function (r) {
        const rec = fromApp_(r);
        const problem = validate_(rec);
        if (problem) { results.push({ key: String(r.rid || ''), status: 'invalid', reason: problem }); return; }
        if (ids.has(rec.rid)) { results.push({ key: rec.rid, status: 'duplicate' }); return; }
        ids.add(rec.rid);
        rows.push(row_(rec, 'Internet', '', '', now));
        results.push({ key: rec.rid, status: 'saved' });
      });
    } else if (req.type === 'sms') {
      const log = sheet_(ss, SHEET_SMS, SMS_HEADERS);
      const logRows = [];
      (req.items || []).forEach(function (it) {
        const body = String(it.body || '').trim();
        const rec = parseSms_(body);
        let status, reason = '';
        if (!rec) { status = 'invalid'; reason = 'Not an ' + SMS_PREFIX + ' message'; }
        else {
          const problem = validate_(rec);
          if (problem) { status = 'invalid'; reason = problem; }
          else if (ids.has(rec.rid)) status = 'duplicate';
          else { ids.add(rec.rid); rows.push(row_(rec, 'SMS', it.from, it.received, now)); status = 'saved'; }
        }
        results.push({ key: String(it.key), status: status, reason: reason });
        logRows.push([toDate_(it.received), String(it.from || ''), body, status + (reason ? ': ' + reason : ''), now]);
      });
      if (logRows.length) log.getRange(log.getLastRow() + 1, 1, logRows.length, SMS_HEADERS.length).setValues(logRows);
    } else {
      return json_({ ok: false, error: 'Unknown request type' });
    }

    if (rows.length) sh.getRange(sh.getLastRow() + 1, 1, rows.length, HEADERS.length).setValues(rows);
    return json_({ ok: true, results: results });
  } catch (err) {
    return json_({ ok: false, error: String(err && err.message || err) });
  } finally {
    lock.releaseLock();
  }
}

/* ---------- SMS decoding ----------
   ITN1*id*yymmdd*district code*PHU*community*head*e-token*size*pregnant*under five*ITNs*distributor */
function parseSms_(body) {
  const p = body.split('*').map(function (x) { return x.trim(); });
  if (p.length !== 13 || p[0].toUpperCase() !== SMS_PREFIX) return null;
  const d = p[2];
  const date = /^\d{6}$/.test(d) ? '20' + d.slice(0, 2) + '-' + d.slice(2, 4) + '-' + d.slice(4, 6) : '';
  return {
    rid: p[1].toLowerCase(), date: date, district: DISTRICTS[parseInt(p[3], 10) - 1] || '',
    phu: p[4], community: p[5], head: p[6], etoken: p[7],
    size: num_(p[8]), preg: num_(p[9]), u5: num_(p[10]), itns: num_(p[11]), distributor: p[12],
    deviceTime: timeFromId_(p[1])
  };
}

function fromApp_(r) {
  return {
    rid: String(r.rid || '').toLowerCase(), date: String(r.date || ''), district: String(r.district || ''),
    phu: String(r.phu || ''), community: String(r.community || ''), head: String(r.head || ''), etoken: String(r.etoken || ''),
    size: num_(r.size), preg: num_(r.preg), u5: num_(r.u5), itns: num_(r.itns), distributor: String(r.distributor || ''),
    deviceTime: r.deviceTime ? new Date(Number(r.deviceTime)) : timeFromId_(r.rid)
  };
}

function validate_(r) {
  if (!/^[0-9a-z]{6,14}$/.test(r.rid)) return 'Record ID is missing or wrong';
  if (!/^\d{4}-\d{2}-\d{2}$/.test(r.date) || isNaN(new Date(r.date + 'T00:00:00').getTime())) return 'Distribution date is wrong';
  if (DISTRICTS.indexOf(r.district) < 0) return 'District is wrong';
  if (!r.phu || !r.community || !r.head || !r.etoken || !r.distributor) return 'A required field is empty';
  if (!(r.size >= 1 && r.size <= 50)) return 'Household size is out of range';
  if (!(r.itns >= 0 && r.itns <= 25)) return 'ITNs issued is out of range';
  if (!(r.preg >= 0 && r.u5 >= 0)) return 'Pregnant women or under five is wrong';
  return '';
}

function row_(r, via, from, received, now) {
  const parts = r.date.split('-');
  return [r.rid, via, new Date(+parts[0], +parts[1] - 1, +parts[2]), r.district, r.phu, r.community, r.head,
    r.etoken, r.size, r.preg, r.u5, r.itns, r.distributor, r.deviceTime || '', String(from || ''),
    received ? toDate_(received) : '', now];
}

/* ---------- helpers ---------- */
function sheet_(ss, name, headers) {
  let sh = ss.getSheetByName(name);
  if (!sh) {
    sh = ss.insertSheet(name);
    sh.getRange(1, 1, 1, headers.length).setValues([headers]).setFontWeight('bold').setBackground('#004080').setFontColor('#ffffff');
    sh.setFrozenRows(1);
    if (name === SHEET_RECORDS) {
      sh.getRange('A:A').setNumberFormat('@');
      sh.getRange('H:H').setNumberFormat('@');
    }
  }
  return sh;
}

function existingIds_(sh) {
  const n = sh.getLastRow() - 1;
  const set = new Set();
  if (n > 0) sh.getRange(2, 1, n, 1).getValues().forEach(function (v) { if (v[0]) set.add(String(v[0]).toLowerCase()); });
  return set;
}

function timeFromId_(rid) {
  const t = parseInt(String(rid || '').slice(0, 8), 36);
  return t > 1.6e12 && t < 4e12 ? new Date(t) : '';
}

function num_(v) { const n = Number(v); return isFinite(n) ? Math.round(n) : NaN; }
function toDate_(v) { const d = new Date(Number(v) || v); return isNaN(d.getTime()) ? '' : d; }
function json_(o) { return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON); }
