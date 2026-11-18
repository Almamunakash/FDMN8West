// Excel (.xlsx) and CSV reading/writing for the web app — no outside libraries.
// Works in the browser; also loadable in Node for testing.
(function (root) {
  'use strict';

  // ---------------- dates ----------------
  const MONTHS = { jan: 1, feb: 2, mar: 3, apr: 4, may: 5, jun: 6, jul: 7, aug: 8, sep: 9, sept: 9, oct: 10, nov: 11, dec: 12 };
  const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  const pad = (n) => String(n).padStart(2, '0');
  const thisYear = () => new Date().getFullYear();

  function makeIso(y, m, d) {
    if (!(y >= 1880 && y <= thisYear() + 1) || !(m >= 1 && m <= 12) || !(d >= 1 && d <= 31)) return null;
    const dt = new Date(Date.UTC(y, m - 1, d));
    if (dt.getUTCFullYear() !== y || dt.getUTCMonth() !== m - 1 || dt.getUTCDate() !== d) return null;
    return `${y}-${pad(m)}-${pad(d)}`;
  }
  function fixYear(y, len) {
    if (len <= 2) {
      const yy = thisYear() % 100;
      return y > yy + 1 ? 1900 + y : 2000 + y;
    }
    return y;
  }
  function monthOf(t) {
    const k = t.toLowerCase();
    if (MONTHS[k]) return MONTHS[k];
    const k3 = k.slice(0, 3);
    return k.length >= 3 && MONTHS[k3] && 'january february march april may june july august september october november december'.includes(k) ? MONTHS[k3] : 0;
  }

  /** Reads many common date styles. Returns ISO (yyyy-mm-dd) or null. */
  function parseDate(raw) {
    const s = String(raw == null ? '' : raw).trim();
    if (!s) return null;
    let m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})(?:[T\s].*)?$/);
    if (m) return makeIso(+m[1], +m[2], +m[3]);
    if (/^\d{4}$/.test(s)) return +s >= 1880 && +s <= thisYear() ? `${s}-01-01` : null;
    const t = s.split(/[\s\-\/.,]+/).filter(Boolean);
    if (t.length !== 3) return null;
    const isNum = (x) => /^\d+$/.test(x);
    if (!isNum(t[0]) && isNum(t[1]) && isNum(t[2])) { // Jan 1, 1992
      const mo = monthOf(t[0]); if (!mo) return null;
      return makeIso(fixYear(+t[2], t[2].length), mo, +t[1]);
    }
    if (isNum(t[0]) && !isNum(t[1]) && isNum(t[2])) { // 1 Jan 1992
      const mo = monthOf(t[1]); if (!mo) return null;
      return makeIso(fixYear(+t[2], t[2].length), mo, +t[0]);
    }
    if (t.every(isNum)) {
      if (t[0].length === 4) return makeIso(+t[0], +t[1], +t[2]); // 1992/1/1
      return makeIso(fixYear(+t[2], t[2].length), +t[1], +t[0]); // 1/1/1992 (day first)
    }
    return null;
  }
  /** Excel stores dates as day numbers counted from 30 Dec 1899. */
  function fromSerial(n) {
    if (!(n >= 1 && n < 80000)) return null;
    const dt = new Date(Date.UTC(1899, 11, 30) + Math.floor(n) * 86400000);
    return makeIso(dt.getUTCFullYear(), dt.getUTCMonth() + 1, dt.getUTCDate());
  }
  function displayDate(iso) {
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso || '');
    return m ? `${+m[3]} ${MONTH_NAMES[+m[2] - 1]} ${m[1]}` : '';
  }
  function ageText(iso) {
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso || '');
    if (!m) return '';
    const now = new Date();
    let y = now.getFullYear() - +m[1], mo = now.getMonth() + 1 - +m[2];
    if (now.getDate() < +m[3]) mo--;
    if (mo < 0) { y--; mo += 12; }
    if (y < 0) return '';
    if (y >= 1) return `${y} yrs`;
    if (mo >= 1) return `${mo} mo`;
    const days = Math.floor((now - new Date(+m[1], +m[2] - 1, +m[3])) / 86400000);
    return days >= 0 ? `${days} days` : '';
  }

  // ---------------- zip reading ----------------
  async function inflateRaw(bytes) {
    const ds = new DecompressionStream('deflate-raw');
    const stream = new Blob([bytes]).stream().pipeThrough(ds);
    return new Uint8Array(await new Response(stream).arrayBuffer());
  }
  async function unzip(buf, wanted) {
    const u8 = new Uint8Array(buf);
    const dv = new DataView(buf);
    let eocd = -1;
    for (let i = u8.length - 22; i >= Math.max(0, u8.length - 70000); i--) {
      if (dv.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
    }
    if (eocd < 0) throw new Error('This file is not a valid Excel (.xlsx) file.');
    const count = dv.getUint16(eocd + 10, true);
    let p = dv.getUint32(eocd + 16, true);
    const dec = new TextDecoder();
    const out = {};
    for (let i = 0; i < count; i++) {
      if (dv.getUint32(p, true) !== 0x02014b50) break;
      const method = dv.getUint16(p + 10, true);
      const csize = dv.getUint32(p + 20, true);
      const nlen = dv.getUint16(p + 28, true), xlen = dv.getUint16(p + 30, true), clen = dv.getUint16(p + 32, true);
      const lho = dv.getUint32(p + 42, true);
      const name = dec.decode(u8.subarray(p + 46, p + 46 + nlen)).replace(/^\//, '');
      p += 46 + nlen + xlen + clen;
      if (!wanted(name)) continue;
      const lnlen = dv.getUint16(lho + 26, true), lxlen = dv.getUint16(lho + 28, true);
      const start = lho + 30 + lnlen + lxlen;
      const data = u8.subarray(start, start + csize);
      const raw = method === 0 ? data : method === 8 ? await inflateRaw(data) : null;
      if (raw) out[name] = dec.decode(raw);
    }
    return out;
  }

  // ---------------- xml helpers ----------------
  const unesc = (s) => s.replace(/&(#x[0-9a-f]+|#\d+|amp|lt|gt|quot|apos);/gi, (m, e) => {
    const k = e.toLowerCase();
    if (k === 'amp') return '&'; if (k === 'lt') return '<'; if (k === 'gt') return '>';
    if (k === 'quot') return '"'; if (k === 'apos') return "'";
    return String.fromCodePoint(k[1] === 'x' ? parseInt(k.slice(2), 16) : parseInt(k.slice(1), 10));
  });
  const stripNs = (xml) => xml.replace(/<(\/?)[A-Za-z_][\w.-]*:/g, '<$1');
  function attr(tag, name) {
    const m = new RegExp('(?:^|\\s)(?:[\\w.-]+:)?' + name + '="([^"]*)"').exec(tag);
    return m ? unesc(m[1]) : null;
  }
  function texts(xml) {
    let s = '';
    const re = /<t(?:\s[^>]*)?>([\s\S]*?)<\/t>|<t\s*\/>/g;
    let m;
    while ((m = re.exec(xml))) s += m[1] ? unesc(m[1]) : '';
    return s;
  }
  function colIndex(ref) {
    let n = 0;
    for (const c of ref.toUpperCase()) { const k = c.charCodeAt(0); if (k < 65 || k > 90) break; n = n * 26 + (k - 64); }
    return n - 1;
  }

  function parseSheet(xml, shared) {
    xml = stripNs(xml);
    const rows = [];
    const rowRe = /<row\b[^>]*?(?:\/>|>([\s\S]*?)<\/row>)/g;
    let rm;
    while ((rm = rowRe.exec(xml))) {
      const body = rm[1] || '';
      const cells = [];
      const cRe = /<c\b([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g;
      let cm, next = 0;
      while ((cm = cRe.exec(body))) {
        const a = cm[1], inner = cm[2] || '';
        const r = attr(a, 'r');
        let col = r ? colIndex(r) : next;
        if (col < 0) col = next;
        next = col + 1;
        const t = attr(a, 't');
        const vm = /<v>([\s\S]*?)<\/v>/.exec(inner);
        const v = vm ? unesc(vm[1]) : '';
        let cell;
        if (t === 's') cell = { text: shared[parseInt(v, 10)] || '', num: false };
        else if (t === 'inlineStr') cell = { text: texts(inner.replace(/<rPh\b[\s\S]*?<\/rPh>/g, '')), num: false };
        else if (t === 'str') cell = { text: v, num: false };
        else if (t === 'b') cell = { text: v === '1' ? 'TRUE' : 'FALSE', num: false };
        else if (t === 'e') cell = { text: '', num: false };
        else cell = { text: v.trim(), num: v.trim() !== '' };
        if (col <= 200) cells[col] = cell;
      }
      if (cells.length) {
        for (let i = 0; i < cells.length; i++) if (!cells[i]) cells[i] = { text: '', num: false };
        rows.push(cells);
      }
    }
    return rows;
  }

  async function readXlsx(buf) {
    const files = await unzip(buf, (n) => n === 'xl/workbook.xml' || n === 'xl/_rels/workbook.xml.rels' || n === 'xl/sharedStrings.xml' || n.startsWith('xl/worksheets/'));
    const shared = [];
    if (files['xl/sharedStrings.xml']) {
      const x = stripNs(files['xl/sharedStrings.xml']);
      const re = /<si\b[^>]*>([\s\S]*?)<\/si>|<si\s*\/>/g;
      let m;
      while ((m = re.exec(x))) shared.push(texts((m[1] || '').replace(/<rPh\b[\s\S]*?<\/rPh>/g, '')));
    }
    const rels = {};
    if (files['xl/_rels/workbook.xml.rels']) {
      const re = /<Relationship\b[^>]*>/g; let m;
      while ((m = re.exec(stripNs(files['xl/_rels/workbook.xml.rels'])))) rels[attr(m[0], 'Id')] = attr(m[0], 'Target');
    }
    const sheets = [];
    const used = new Set();
    if (files['xl/workbook.xml']) {
      const re = /<sheet\b[^>]*>/g; let m;
      while ((m = re.exec(stripNs(files['xl/workbook.xml'])))) {
        const name = attr(m[0], 'name') || 'Sheet';
        const target = rels[attr(m[0], 'id')];
        if (!target) continue;
        const path = target.startsWith('/') ? target.slice(1) : 'xl/' + target.replace(/^\.\//, '');
        if (!files[path]) continue;
        used.add(path);
        sheets.push([name, parseSheet(files[path], shared)]);
      }
    }
    if (!sheets.length) {
      Object.keys(files).filter((k) => k.startsWith('xl/worksheets/') && k.endsWith('.xml') && !used.has(k)).sort()
        .forEach((k) => sheets.push([k.split('/').pop().replace('.xml', ''), parseSheet(files[k], shared)]));
    }
    if (!sheets.length) throw new Error('No sheets were found in this Excel file.');
    return sheets;
  }

  function readCsv(text) {
    text = text.replace(/^﻿/, '');
    const first = text.split(/\r?\n/, 1)[0] || '';
    const delim = (first.split(';').length > first.split(',').length) ? ';' : ',';
    const rows = []; let row = []; let cur = ''; let q = false;
    text += '\n';
    for (let i = 0; i < text.length; i++) {
      const c = text[i];
      if (q) {
        if (c === '"') { if (text[i + 1] === '"') { cur += '"'; i++; } else q = false; } else cur += c;
      } else if (c === '"') q = true;
      else if (c === delim) { row.push({ text: cur, num: false }); cur = ''; }
      else if (c === '\r') { /* skip */ }
      else if (c === '\n') { row.push({ text: cur, num: false }); cur = ''; if (row.some((x) => x.text.trim())) rows.push(row); row = []; }
      else cur += c;
    }
    return [['CSV', rows]];
  }

  // ---------------- mapping to records ----------------
  const LABELS = { name: 'Name', sex: 'Sex', fcn: 'FCN', dob: 'Date of birth', relation: 'Relation', main: 'Main Block', sub: 'Sub-Block', majhi: 'Majhi', remarks: 'Remarks' };
  const FIELDS = Object.keys(LABELS);
  const norm = (s) => String(s || '').toLowerCase().replace(/[^a-z0-9]/g, '');
  function fieldFor(header) {
    const h = norm(header);
    if (!h) return null;
    if (h.includes('majh') || h.includes('majee') || h.includes('mahjee')) return 'majhi';
    if (h.includes('fcn') || h === 'familycountingnumber' || h === 'familycount') return 'fcn';
    if (h.includes('birth') || h === 'dob') return 'dob';
    if (h.includes('relation')) return 'relation';
    if (h.startsWith('main')) return 'main';
    if (h.startsWith('sub')) return 'sub';
    if (h.includes('remark') || ['note', 'notes', 'comment', 'comments'].includes(h)) return 'remarks';
    if (h === 'sex' || h === 'gender') return 'sex';
    if (h === 'name' || h.endsWith('name') || h.startsWith('name')) return 'name';
    return null;
  }
  function numText(c) {
    if (!c) return '';
    const t = c.text.trim();
    if (!c.num) return t;
    const d = Number(t);
    return Number.isFinite(d) && Number.isInteger(d) && Math.abs(d) < 1e15 ? String(d) : t;
  }
  function buildRows(sheets) {
    const out = []; const used = []; const skipped = []; const found = new Set();
    let empty = 0, badDates = 0;
    for (const [sheetName, rows] of sheets) {
      let hr = -1, map = null;
      for (let r = 0; r < Math.min(rows.length, 20); r++) {
        const m = {};
        rows[r].forEach((c, i) => { const f = fieldFor(c.text); if (f && !(f in m)) m[f] = i; });
        if ('name' in m && Object.keys(m).length >= 3) { hr = r; map = m; break; }
      }
      if (hr < 0) { if (rows.length) skipped.push(sheetName); continue; }
      used.push(sheetName);
      Object.keys(map).forEach((k) => found.add(k));
      const get = (row, f) => (f in map ? row[map[f]] : undefined);
      for (let r = hr + 1; r < rows.length; r++) {
        const row = rows[r];
        const name = (get(row, 'name')?.text || '').trim();
        const fcn = numText(get(row, 'fcn'));
        if (!name && !fcn) { empty++; continue; }
        if (norm(name) === 'name') continue;
        if (!name) { empty++; continue; }
        let remarks = numText(get(row, 'remarks'));
        let dob = '';
        const dc = get(row, 'dob');
        if (dc && dc.text.trim()) {
          const d = Number(dc.text.trim());
          dob = (dc.num && Number.isFinite(d) ? fromSerial(d) : parseDate(dc.text)) || '';
          if (!dob) { badDates++; remarks = [remarks, 'DOB: ' + dc.text.trim()].filter(Boolean).join('; '); }
        }
        let sex = (get(row, 'sex')?.text || '').trim().toUpperCase();
        if (sex === 'FEMALE') sex = 'F'; else if (sex === 'MALE') sex = 'M';
        out.push({
          name, sex, fcn, dob,
          relation: (get(row, 'relation')?.text || '').trim().toUpperCase(),
          main_block: numText(get(row, 'main')), sub_block: numText(get(row, 'sub')),
          majhi: (get(row, 'majhi')?.text || '').trim(), remarks,
        });
      }
    }
    if (!used.length) throw new Error('Could not find the heading row. The sheet needs a row with column names like SL, Name, Sex, FCN, Date of birth, Relation, Main Block, Sub-Block, Majhi, Remarks.');
    return {
      rows: out, sheetsUsed: used, sheetsSkipped: skipped,
      columnsFound: FIELDS.filter((f) => found.has(f)).map((f) => LABELS[f]),
      columnsMissing: FIELDS.filter((f) => !found.has(f)).map((f) => LABELS[f]),
      emptyRowsSkipped: empty, badDates,
    };
  }

  async function readFile(buf) {
    const u8 = new Uint8Array(buf.slice(0, 4));
    if (u8[0] === 0xD0 && u8[1] === 0xCF) throw new Error('This is an old Excel file (.xls). Open it in Excel and use "Save As" → Excel Workbook (.xlsx), then import again.');
    const sheets = (u8[0] === 0x50 && u8[1] === 0x4B) ? await readXlsx(buf) : readCsv(new TextDecoder().decode(buf));
    return buildRows(sheets);
  }

  // ---------------- xlsx writing (stored zip) ----------------
  const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
  function crc32(u8) { let c = 0xFFFFFFFF; for (let i = 0; i < u8.length; i++) c = CRC[(c ^ u8[i]) & 0xFF] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; }
  function zip(files) {
    const enc = new TextEncoder();
    const parts = []; const central = []; let offset = 0;
    for (const [name, content] of files) {
      const nameB = enc.encode(name);
      const data = typeof content === 'string' ? enc.encode(content) : content;
      const crc = crc32(data);
      const lh = new DataView(new ArrayBuffer(30));
      lh.setUint32(0, 0x04034b50, true); lh.setUint16(4, 20, true); lh.setUint16(6, 0x0800, true); lh.setUint16(8, 0, true);
      lh.setUint32(14, crc, true); lh.setUint32(18, data.length, true); lh.setUint32(22, data.length, true); lh.setUint16(26, nameB.length, true);
      parts.push(new Uint8Array(lh.buffer), nameB, data);
      const ch = new DataView(new ArrayBuffer(46));
      ch.setUint32(0, 0x02014b50, true); ch.setUint16(4, 20, true); ch.setUint16(6, 20, true); ch.setUint16(8, 0x0800, true);
      ch.setUint32(16, crc, true); ch.setUint32(20, data.length, true); ch.setUint32(24, data.length, true); ch.setUint16(28, nameB.length, true);
      ch.setUint32(42, offset, true);
      central.push(new Uint8Array(ch.buffer), nameB);
      offset += 30 + nameB.length + data.length;
    }
    const csize = central.reduce((a, b) => a + b.length, 0);
    const end = new DataView(new ArrayBuffer(22));
    end.setUint32(0, 0x06054b50, true); end.setUint16(8, files.length, true); end.setUint16(10, files.length, true);
    end.setUint32(12, csize, true); end.setUint32(16, offset, true);
    return new Blob([...parts, ...central, new Uint8Array(end.buffer)], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
  }
  const xesc = (s) => String(s == null ? '' : s).replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  function colName(i) { let n = i + 1, s = ''; while (n > 0) { const r = (n - 1) % 26; s = String.fromCharCode(65 + r) + s; n = Math.floor((n - 1) / 26); } return s; }

  /** headers: array of titles; rows: array of arrays (numbers stay numbers). */
  function writeXlsx(headers, rows, widths) {
    const NS = 'http://schemas.openxmlformats.org';
    const sheet = [];
    sheet.push(`<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<worksheet xmlns="${NS}/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols>`);
    headers.forEach((h, i) => sheet.push(`<col min="${i + 1}" max="${i + 1}" width="${(widths && widths[i]) || 14}" customWidth="1"/>`));
    sheet.push('</cols><sheetData><row r="1">');
    headers.forEach((h, i) => sheet.push(`<c r="${colName(i)}1" t="inlineStr" s="1"><is><t>${xesc(h)}</t></is></c>`));
    sheet.push('</row>');
    rows.forEach((row, ri) => {
      const r = ri + 2;
      let s = `<row r="${r}">`;
      row.forEach((v, ci) => {
        const ref = colName(ci) + r;
        s += typeof v === 'number' ? `<c r="${ref}" s="2"><v>${v}</v></c>` : `<c r="${ref}" t="inlineStr" s="2"><is><t xml:space="preserve">${xesc(v)}</t></is></c>`;
      });
      sheet.push(s + '</row>');
    });
    sheet.push('</sheetData></worksheet>');
    const files = [
      ['[Content_Types].xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<Types xmlns="${NS}/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>`],
      ['_rels/.rels', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<Relationships xmlns="${NS}/package/2006/relationships"><Relationship Id="rId1" Type="${NS}/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>`],
      ['xl/workbook.xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<workbook xmlns="${NS}/spreadsheetml/2006/main" xmlns:r="${NS}/officeDocument/2006/relationships"><sheets><sheet name="Beneficiaries" sheetId="1" r:id="rId1"/></sheets></workbook>`],
      ['xl/_rels/workbook.xml.rels', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<Relationships xmlns="${NS}/package/2006/relationships"><Relationship Id="rId1" Type="${NS}/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="${NS}/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>`],
      ['xl/styles.xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<styleSheet xmlns="${NS}/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFCDEBE1"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"><color auto="1"/></left><right style="thin"><color auto="1"/></right><top style="thin"><color auto="1"/></top><bottom style="thin"><color auto="1"/></bottom><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/><xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>`],
      ['xl/worksheets/sheet1.xml', sheet.join('')],
    ];
    return zip(files);
  }

  root.XL = { readFile, writeXlsx, parseDate, fromSerial, displayDate, ageText };
})(typeof window !== 'undefined' ? window : globalThis);
