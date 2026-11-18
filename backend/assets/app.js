/* Camp- 8 West · FDMN Database — web app (works with the same API as the Android app) */
'use strict';

const API_URL = 'api/index.php';
const APP_VERSION = '2.0';
const AGE_GROUPS = [['u5', '0–5 yrs'], ['c6', '6–12 yrs'], ['c13', '13–17 yrs'], ['a18', '18–59 yrs'], ['e60', '60+ yrs']];
const BLOCK_TONES = ['green', 'blue', 'purple', 'orange', 'pink', 'teal', 'red', 'indigo'];

// ---------------- small helpers ----------------
const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => Array.from(el.querySelectorAll(s));
const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmt = (n) => Number(n || 0).toLocaleString('en-US');
const store = {
  get(k) { try { return localStorage.getItem(k) || ''; } catch (e) { return ''; } },
  set(k, v) { try { if (v) localStorage.setItem(k, v); else localStorage.removeItem(k); } catch (e) { /* private mode */ } },
};
const ageLabel = (code) => (AGE_GROUPS.find((a) => a[0] === code) || [code, code])[1];
const sexText = (s) => (s === 'M' ? 'Male' : s === 'F' ? 'Female' : s || '');
const sexTone = (s) => (s === 'F' ? 'pink' : s === 'M' ? 'blue' : 'green');
const wave = () => '<svg class="wave" viewBox="0 0 100 100" preserveAspectRatio="none"><path d="M42 100C68 100 78 55 100 42V100Z" fill="currentColor" opacity=".22"/><path d="M58 100C80 100 88 76 100 66V100Z" fill="currentColor" opacity=".55"/></svg>';
const bigWave = (c) => `<svg class="wave" viewBox="0 0 100 100" preserveAspectRatio="none"><defs><linearGradient id="bw${c}" x1="0" x2="1"><stop offset="0" stop-color="#${c}" stop-opacity="0"/><stop offset="1" stop-color="#${c}" stop-opacity=".6"/></linearGradient></defs><path d="M35 100C60 95 75 55 100 35V100Z" fill="url(#bw${c})"/></svg>`;

// ---------------- state ----------------
const S = {
  token: store.get('fdmn_token'),
  user: (() => { try { return JSON.parse(store.get('fdmn_user') || 'null'); } catch (e) { return null; } })(),
  stats: {},
  meta: { blocks: [], relations: [], majhis: [] },
  version: 0,
  openBlocks: new Set(),
  rec: { filters: {}, items: [], total: 0, loading: false, error: null, key: null, gen: 0, scroll: 0 },
  preset: null,
  settings: null,
  lastRoute: '',
};
const isAdmin = () => S.user && S.user.role === 'admin';

// ---------------- API ----------------
async function api(action, body = {}) {
  let res;
  const payload = S.token ? Object.assign({}, body, { token: S.token }) : body;
  try {
    res = await fetch(API_URL + '?a=' + encodeURIComponent(action), {
      method: 'POST',
      headers: Object.assign({ 'Content-Type': 'application/json' }, S.token ? { 'X-Auth-Token': S.token } : {}),
      body: JSON.stringify(payload),
    });
  } catch (e) {
    throw new Error('No internet connection, or the server could not be reached.');
  }
  let j;
  try { j = await res.json(); } catch (e) { throw new Error(`The server sent an unexpected reply (code ${res.status}).`); }
  if (!j.ok) {
    if (j.code === 'auth' && S.token) { signOut(false); toast('Please sign in again.'); }
    const err = new Error(j.error || 'Something went wrong.');
    err.code = j.code;
    throw err;
  }
  return j;
}

async function loadStats() {
  try { S.stats = (await api('stats')).stats || {}; } catch (e) { /* keep old */ }
  updateNav();
}
async function loadMeta() {
  try {
    const [b, o] = await Promise.all([api('blocks'), api('options')]);
    S.meta = { blocks: b.blocks || [], relations: o.relations || [], majhis: o.majhis || [] };
  } catch (e) { /* keep old */ }
}
async function loadSettings() {
  try { S.settings = (await api('settings_get')).settings; } catch (e) { /* keep old */ }
}
function changed() { S.version++; loadMeta(); loadStats(); }
const mainNames = () => S.meta.blocks.map((b) => b.name).filter(Boolean);
const subNames = (main) => [...new Set((main ? S.meta.blocks.filter((b) => b.name === main) : S.meta.blocks).flatMap((b) => b.subs.map((s) => s.name)).filter(Boolean))];

function describe(f) {
  const p = [];
  if (f.main) p.push('Main Block ' + f.main);
  if (f.sub) p.push('Sub-Block ' + f.sub);
  if (f.sex) p.push(sexText(f.sex));
  if (f.age) p.push(f.age === 'e60' ? 'Elderly 60+' : f.age === 'a18' ? 'Adults 18–59' : 'Children ' + ageLabel(f.age));
  if (f.relation) p.push('Relation ' + f.relation);
  if (f.majhi) p.push('Majhi ' + f.majhi);
  if (f.q) p.push(`Search "${f.q}"`);
  return p.length ? p.join(' · ') : 'All records';
}
const filtersActive = (f) => Object.values(f).some((v) => v);

// ---------------- UI helpers ----------------
function toast(msg) {
  const old = $('.toast'); if (old) old.remove();
  const t = document.createElement('div'); t.className = 'toast'; t.textContent = msg;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 3200);
}
function modal(html, onMount) {
  const bg = document.createElement('div');
  bg.className = 'modal-bg';
  bg.innerHTML = `<div class="modal">${html}</div>`;
  document.body.appendChild(bg);
  const close = () => bg.remove();
  bg.addEventListener('click', (e) => { if (e.target === bg && !bg.dataset.busy) close(); });
  if (onMount) onMount(bg.firstElementChild, close, bg);
  return close;
}
function confirmBox(title, text, ok = 'Yes', danger = false) {
  return new Promise((resolve) => {
    modal(`<h3>${esc(title)}</h3><p>${esc(text)}</p><div class="actions"><button class="btn ghost" data-no>Cancel</button><button class="btn ${danger ? 'danger' : ''}" data-yes>${esc(ok)}</button></div>`, (m, close) => {
      $('[data-no]', m).onclick = () => { close(); resolve(false); };
      $('[data-yes]', m).onclick = () => { close(); resolve(true); };
    });
  });
}
function topbar(title, sub, opts = {}) {
  return `<header class="topbar">
    ${opts.back ? `<button class="iconbtn" data-back title="Back">${ic('ArrowBack')}</button>` : `<button class="iconbtn menubtn" data-menu-open title="Menu">${ic('Menu')}</button>`}
    <div class="titles"><h1>${esc(title)}</h1>${sub != null ? `<div class="sub" id="topSub">${esc(sub)}</div>` : ''}</div>
    ${opts.actions || ''}
  </header>`;
}
let navCount = 0;
function back(fallback) {
  if (navCount > 1) history.back(); else go(fallback || 'home');
}
function ageYears(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso || '');
  if (!m) return null;
  const n = new Date();
  let y = n.getFullYear() - +m[1];
  if (n.getMonth() + 1 < +m[2] || (n.getMonth() + 1 === +m[2] && n.getDate() < +m[3])) y--;
  return y;
}
const go = (path) => { location.hash = '#/' + path; };
const loading = () => '<div class="center"><span class="spinner"></span></div>';
const errorBox = (msg) => `<div class="center"><p class="muted">${esc(msg)}</p><button class="btn" data-retry>Try again</button></div>`;
const emptyBox = (t, s) => `<div class="center empty"><h3>${esc(t)}</h3><p class="muted">${esc(s)}</p></div>`;
function busyBtn(btn, busy, text) {
  if (!btn) return;
  if (busy) { btn.dataset.label = btn.innerHTML; btn.disabled = true; btn.innerHTML = '<span class="spinner" style="width:20px;height:20px;border-width:2px;border-color:rgba(255,255,255,.4);border-top-color:#fff"></span>' + (text ? ' ' + esc(text) : ''); }
  else { btn.disabled = false; if (btn.dataset.label) btn.innerHTML = btn.dataset.label; }
}

// ---------------- routing ----------------
const ROOT = { home: 'home', camp: 'camp', records: 'records', record: 'records', family: 'records', edit: 'records', new: 'records', reports: 'reports', messages: 'messages', users: 'users', settings: 'settings', more: 'settings', import: 'settings', password: 'settings' };
function route() {
  const h = location.hash.replace(/^#\/?/, '');
  const i = h.indexOf('/');
  const name = (i < 0 ? h : h.slice(0, i)) || 'home';
  const arg = i < 0 ? '' : decodeURIComponent(h.slice(i + 1));
  return { name: ROOT[name] ? name : 'home', arg };
}
window.addEventListener('hashchange', render);

function render() {
  const r = route();
  if (S.lastRoute === 'records') S.rec.scroll = window.scrollY;
  navCount++;
  if (!S.token || !S.user) { renderAuth(); S.lastRoute = ''; return; }
  ensureShell();
  updateNav(ROOT[r.name]);
  const main = $('#main');
  main.innerHTML = '';
  window.scrollTo(0, 0);
  closeDrawer();
  closePopover();
  const pages = { home: pageHome, camp: pageCamp, records: pageRecords, record: pageDetail, family: pageFamily, edit: pageEdit, new: pageEdit, reports: pageReports, messages: pageMessages, users: pageUsers, settings: pageSettings, more: pageSettings, import: pageImport, password: pagePassword };
  S.lastRoute = r.name;
  (pages[r.name] || pageHome)(main, r.arg);
}

// ---------------- shell (side menu on PC, drawer on phone) ----------------
const NAV = [['home', 'Dashboard', 'Dashboard'], ['camp', 'Camp Profile', 'AccountBox'], ['records', 'Records', 'Storage'], ['reports', 'Reports', 'BarChart'], ['messages', 'Messages', 'MailOutline'], ['users', 'Users', 'PeopleOutline', true], ['settings', 'Settings', 'Settings']];
function ensureShell() {
  if ($('#main')) return;
  $('#app').innerHTML = `
    <div class="scrim" id="scrim"></div>
    <aside class="sidebar" id="sidebar">
      <div class="brand">
        <div class="em"><img src="assets/emblem.png" alt="Government of Bangladesh emblem"></div>
        <div class="rrrc">RRRC</div>
        <div class="gov">Government of the<br>People's Republic of Bangladesh</div>
        <hr>
        <div class="office">Office of the<br>Camp-in-Charge</div>
        <div class="camp">Camp-8 West</div>
        <div class="loc">Ukhiya, Cox's Bazar</div>
        <hr>
      </div>
      <div class="menu-items" id="menuItems"></div>
      <div class="who-space"></div>
      <div class="who" id="who" title="Settings"></div>
    </aside>
    <main class="main" id="main"></main>`;
  $('#scrim').onclick = closeDrawer;
  $('#who').onclick = () => go('settings');
  $('#main').addEventListener('click', (e) => {
    if (e.target.closest('[data-back]')) back(ROOT[route().name]);
    else if (e.target.closest('[data-menu-open]')) openDrawer();
  });
}
function openDrawer() { const sb = $('#sidebar'); if (sb) { sb.classList.add('open'); $('#scrim').classList.add('open'); } }
function closeDrawer() { const sb = $('#sidebar'); if (sb) { sb.classList.remove('open'); $('#scrim').classList.remove('open'); } }
function updateNav(active) {
  const box = $('#menuItems');
  if (!box) return;
  const st = S.stats || {};
  const counts = { messages: isAdmin() ? st.open_messages : st.unread_replies, users: isAdmin() ? st.pending_users : 0 };
  const cur = active || (box.querySelector('.on') || {}).dataset?.nav;
  box.innerHTML = NAV.filter((n) => !n[3] || isAdmin()).map(([k, label, icon]) => {
    const n = counts[k] || 0;
    return `<button class="navbtn ${k === cur ? 'on' : ''}" data-nav="${k}">${ic(icon)}<span>${label}</span>${n ? `<i class="badge">${n > 99 ? '99+' : n}</i>` : ''}</button>`;
  }).join('');
  $$('[data-nav]', box).forEach((b) => { b.onclick = () => { closeDrawer(); go(b.dataset.nav); }; });
  const who = $('#who');
  if (who && S.user) who.innerHTML = `${ic('AccountCircle')}<div style="min-width:0"><b>${esc(S.user.name)}</b><small>${isAdmin() ? 'Admin' : 'Viewer'}</small><small>${esc(S.user.email)}</small></div>`;
  const bell = $('#bellDot');
  if (bell) { const n = bellCount(); bell.textContent = n > 99 ? '99+' : n; bell.classList.toggle('hidden', !n); }
}
function bellCount() {
  const st = S.stats || {};
  return isAdmin() ? (st.pending_users || 0) + (st.open_messages || 0) : (st.unread_replies || 0);
}

// ---------------- popovers: notifications and calendar ----------------
function closePopover() { $$('.popover').forEach((p) => p.remove()); }
function openPopover(anchor, html, onMount) {
  closePopover();
  const p = document.createElement('div');
  p.className = 'popover';
  p.innerHTML = html;
  ($('#tools') || document.body).appendChild(p);
  if (onMount) onMount(p);
  setTimeout(() => {
    const off = (e) => { if (!p.contains(e.target) && !anchor.contains(e.target)) { p.remove(); document.removeEventListener('click', off, true); } };
    document.addEventListener('click', off, true);
  }, 0);
  return p;
}
async function openBell(anchor) {
  const p = openPopover(anchor, `<h4>Notifications</h4><div class="none">Loading…</div>`);
  const items = [];
  try {
    if (isAdmin()) {
      const [u, m] = await Promise.all([api('users'), api('msg_all', { status: 'open' })]);
      (u.users || []).filter((x) => x.status === 'pending').forEach((x) => items.push({ icon: 'PeopleOutline', t: `${x.name} wants to join`, s: `${x.email} · waiting for approval`, go: 'users' }));
      (m.messages || []).forEach((x) => items.push({ icon: 'MailOutline', t: x.subject || 'Request', s: `${x.user_name} · ${x.created_at}`, go: 'messages' }));
    } else {
      const m = await api('msg_mine');
      (m.messages || []).filter((x) => !x.user_seen && x.reply).forEach((x) => items.push({ icon: 'MailOutline', t: 'Admin replied: ' + (x.subject || 'Request'), s: x.reply.slice(0, 80), go: 'messages' }));
      loadStats();
    }
  } catch (e) { items.length = 0; }
  if (!document.body.contains(p)) return;
  p.innerHTML = `<h4>Notifications</h4>` + (items.length ? items.map((x, i) => `<div class="item" data-i="${i}">${ic(x.icon)}<div><b>${esc(x.t)}</b><small>${esc(x.s)}</small></div></div>`).join('') : '<div class="none">No new notifications.</div>');
  $$('[data-i]', p).forEach((el) => { el.onclick = () => { p.remove(); go(items[+el.dataset.i].go); }; });
}
const MONTHS_LONG = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
function openCalendar(anchor) {
  const today = new Date();
  let y = today.getFullYear(), m = today.getMonth();
  const draw = (p) => {
    const first = new Date(y, m, 1).getDay();
    const days = new Date(y, m + 1, 0).getDate();
    let cells = '';
    for (let i = 0; i < first; i++) cells += '<td></td>';
    for (let d = 1; d <= days; d++) {
      const wd = (first + d - 1) % 7;
      const isToday = d === today.getDate() && m === today.getMonth() && y === today.getFullYear();
      cells += `<td class="${isToday ? 'today' : ''} ${wd === 5 ? 'fri' : ''}"><span>${d}</span></td>`;
      if (wd === 6 && d < days) cells += '</tr><tr>';
    }
    p.innerHTML = `<div class="cal"><div class="head"><button data-m="-1" aria-label="Previous month">${ic('ChevronLeft')}</button><b>${MONTHS_LONG[m]} ${y}</b><button data-m="1" aria-label="Next month">${ic('ChevronRight')}</button></div>
      <table><thead><tr>${['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'].map((d) => `<th>${d}</th>`).join('')}</tr></thead><tbody><tr>${cells}</tr></tbody></table>
      <button class="btn outline small todaybtn" data-today>Today: ${today.getDate()} ${MONTHS_LONG[today.getMonth()]} ${today.getFullYear()}</button></div>`;
    $$('[data-m]', p).forEach((b) => { b.onclick = (e) => { e.stopPropagation(); m += +b.dataset.m; if (m < 0) { m = 11; y--; } if (m > 11) { m = 0; y++; } draw(p); }; });
    $('[data-today]', p).onclick = (e) => { e.stopPropagation(); y = today.getFullYear(); m = today.getMonth(); draw(p); };
  };
  openPopover(anchor, '', draw);
}

// ---------------- sign in / register ----------------
function renderAuth(mode = 'login', notice = '') {
  $('#app').innerHTML = `
  <div class="auth"><div class="box">
    <img class="logo" src="assets/logo_full.png" alt="Office of the Camp-in-Charge, Camp-8 West">
    <h1>Camp- 8 West</h1>
    <p class="tagline">About the Camp, FDMN and Activities.</p>
    <form class="form" id="authForm" autocomplete="on">
      ${mode === 'login' ? `
        <h2>Sign in</h2>
        ${notice ? `<div class="note">${esc(notice)}</div>` : ''}
        <label class="field"><span>Email</span><input class="input" name="email" type="email" autocomplete="username" value="${esc(store.get('fdmn_email'))}" required></label>
        <label class="field"><span>Password</span><div class="pw"><input class="input" name="password" type="password" autocomplete="current-password" required><button type="button" data-show>Show</button></div></label>
        <div class="err" id="authErr"></div>
        <button class="btn block" type="submit">Sign in</button>
        <p style="text-align:center;margin:14px 0 0"><button type="button" class="btn ghost" data-mode="register">New user?&nbsp;<b>Create an account</b></button></p>
      ` : `
        <h2>Create account</h2>
        <p class="muted" style="margin:-6px 0 12px;font-size:13px">An Admin must approve your account before you can sign in.</p>
        <label class="field"><span>Full name</span><input class="input" name="name" autocomplete="name" required></label>
        <label class="field"><span>Email</span><input class="input" name="email" type="email" autocomplete="email" required></label>
        <label class="field"><span>Password (min 6)</span><input class="input" name="password" type="password" autocomplete="new-password" required></label>
        <label class="field"><span>Confirm password</span><input class="input" name="password2" type="password" autocomplete="new-password" required></label>
        <div class="err" id="authErr"></div>
        <button class="btn block" type="submit">Create account</button>
        <p style="text-align:center;margin:14px 0 0"><button type="button" class="btn ghost" data-mode="login">Already have an account?&nbsp;<b>Sign in</b></button></p>
      `}
    </form>
    <p class="foot">Forgot your password? Ask an Admin to reset it.</p>
  </div></div>`;
  const f = $('#authForm');
  $$('[data-mode]').forEach((b) => { b.onclick = () => renderAuth(b.dataset.mode); });
  const sh = $('[data-show]');
  if (sh) sh.onclick = () => { const p = f.password; p.type = p.type === 'password' ? 'text' : 'password'; sh.textContent = p.type === 'password' ? 'Show' : 'Hide'; };
  f.onsubmit = async (e) => {
    e.preventDefault();
    const btn = $('button[type=submit]', f);
    const err = $('#authErr');
    err.textContent = '';
    const v = Object.fromEntries(new FormData(f));
    if (mode === 'register') {
      if ((v.name || '').trim().length < 2) { err.textContent = 'Please enter your full name.'; return; }
      if ((v.password || '').length < 6) { err.textContent = 'Password must be at least 6 characters.'; return; }
      if (v.password !== v.password2) { err.textContent = 'The two passwords do not match.'; return; }
    }
    busyBtn(btn, true);
    try {
      if (mode === 'login') {
        const r = await api('login', { email: v.email.trim(), password: v.password });
        signedIn(r.token, r.user);
      } else {
        const r = await api('register', { name: v.name.trim(), email: v.email.trim(), password: v.password });
        store.set('fdmn_email', v.email.trim());
        renderAuth('login', r.message || 'Account created. Wait for Admin approval.');
      }
    } catch (ex) {
      err.textContent = ex.message;
      busyBtn(btn, false);
    }
  };
}

function signedIn(token, user) {
  S.token = token; S.user = user;
  store.set('fdmn_token', token); store.set('fdmn_user', JSON.stringify(user)); store.set('fdmn_email', user.email);
  S.rec = { filters: {}, items: [], total: 0, loading: false, error: null, key: null, gen: 0, scroll: 0 };
  $('#app').innerHTML = '';
  if (location.hash && location.hash !== '#/' && location.hash !== '#/home') go('home'); else render();
  loadMeta().then(() => { if (route().name === 'home') render(); });
  loadStats().then(() => { if (route().name === 'home') render(); });
  loadSettings().then(() => { if (route().name === 'home') render(); });
  showWelcome();
}
function signOut(callServer = true) {
  if (callServer && S.token) api('logout').catch(() => {});
  S.token = ''; S.user = null; S.stats = {};
  store.set('fdmn_token', ''); store.set('fdmn_user', '');
  $('#app').innerHTML = '';
  renderAuth();
}

// ---------------- welcome popup (5 seconds, X to close) ----------------
function welcomeCard() {
  const u = S.user;
  return `<div class="bigcard welcomecard">${bigWave('49D17D')}
    <div class="avatar"><div class="circle">${ic('Person')}</div><div class="role">${isAdmin() ? '👑 Admin' : '👤 Viewer'}</div></div>
    <div class="vline"></div>
    <div style="flex:1;min-width:0"><h3>Welcome, ${esc(u.name)}</h3><p>${isAdmin() ? 'Admin · You can add, edit and delete records.' : 'Viewer · You can search and view records.'}</p></div>
  </div>`;
}
function showWelcome() {
  if (!S.user || $('.welcome-pop')) return;
  const bg = document.createElement('div');
  bg.className = 'modal-bg';
  bg.innerHTML = `<div class="welcome-pop"><button class="x" aria-label="Close">${ic('Close')}</button>${welcomeCard()}</div>`;
  document.body.appendChild(bg);
  const close = () => bg.remove();
  $('.x', bg).onclick = close;
  bg.onclick = (e) => { if (e.target === bg) close(); };
  setTimeout(close, 5000);
}

// ---------------- DASHBOARD ----------------
const AGE_COLORS = { u5: '#A855F7', c6: '#2196F3', c13: '#22C4C4', a18: '#FFB020', e60: '#EC4899' };
function donut(parts, total) {
  const r = 70, c = 2 * Math.PI * r;
  let off = 0;
  const segs = parts.filter((x) => x.v > 0).map((x) => {
    const len = total ? (x.v / total) * c : 0;
    const seg = `<circle r="${r}" cx="95" cy="95" fill="none" stroke="${x.color}" stroke-width="36" stroke-dasharray="${len} ${c - len}" stroke-dashoffset="${-off}"/>`;
    off += len;
    return seg;
  }).join('');
  return `<svg viewBox="0 0 190 190"><circle r="${r}" cx="95" cy="95" fill="none" stroke="#EEF3F1" stroke-width="36"/>${segs}<circle r="52" cx="95" cy="95" fill="#fff"/></svg>`;
}
const CONTACT_ICON = { fire: 'FireTruck', police: 'LocalPolice', hospital: 'LocalHospital', site: 'Engineering', other: 'Phone' };
function campInfoHtml(ci) {
  const tile = (tone, icon, label, val) => `<div class="info t-${tone}">${ic(icon)}<div><small>${label}</small><b>${esc(val || '—')}</b></div></div>`;
  return `<div class="infogrid">
    ${tile('green', 'Terrain', 'Camp Area', ci.area)}${tile('blue', 'Groups', 'Total Organisations', ci.organisations)}
    ${tile('blue', 'Public', 'UN Agencies', ci.un_agencies)}${tile('purple', 'Handshake', "NGO's & INGO's", ci.ngos)}
    ${tile('pink', 'Settings', 'Total Projects', ci.projects)}${tile('teal', 'Layers', 'Total Sectors', ci.sectors)}</div>`;
}
function contactsHtml(list) {
  if (!list || !list.length) return '<p class="muted">No contacts yet.</p>';
  return list.map((c) => `<div class="contact"><div class="cic ${esc(c.type)}">${ic(CONTACT_ICON[c.type] || 'Phone')}</div>
    <div class="t"><b>${esc(c.name)}</b><span>${esc(c.phone)}</span></div>
    ${c.phone ? `<a class="callbtn" href="tel:${esc(c.phone.replace(/[^\d+]/g, ''))}" title="Call ${esc(c.name)}">${ic('Phone')}</a>` : ''}</div>`).join('');
}

function pageHome(main) {
  const st = S.stats || {};
  const tot = st.total || 0;
  const pct = (n) => (tot ? ((n / tot) * 100).toFixed(1) + '%' : '0%');
  const today = new Date();
  const ages = [
    { k: 'u5', label: '0 – 5 yrs', v: st.under5 || 0 }, { k: 'c6', label: '6 – 12 yrs', v: st.age6to12 || 0 },
    { k: 'c13', label: '13 – 17 yrs', v: st.age13to17 || 0 }, { k: 'a18', label: '18 – 59 yrs', v: st.age18to59 || 0 },
    { k: 'e60', label: 'Elderly 60+', v: st.elderly || 0 },
  ].map((x) => Object.assign(x, { color: AGE_COLORS[x.k] }));
  const ageTotal = ages.reduce((a, x) => a + x.v, 0);
  const sum = (tone, icon, deco, val, label, sub, link) => `<div class="sum t-${tone}" data-link='${esc(JSON.stringify(link))}'>
    <div class="tile">${ic(icon)}</div><div><div class="val">${fmt(val)}</div><div class="lbl">${label}${sub ? `<br><span style="font-weight:500">${sub}</span>` : ''}</div></div>${ic(deco, 'deco')}</div>`;
  const ci = (S.settings && S.settings.camp_info) || {};
  const contacts = (S.settings && S.settings.contacts) || [];
  const blocks = S.meta.blocks.map((mb, i) => `<div class="bcard t-${BLOCK_TONES[i % BLOCK_TONES.length]}" data-link='${esc(JSON.stringify({ main: mb.name }))}'>
      <div class="tile">${esc((mb.name || '?').slice(0, 2))}</div>
      <div><b class="t">Main Block ${esc(mb.name || '(none)')}</b>
        <div class="ln">${ic('Groups')} ${fmt(mb.count)} people</div>
        <div class="ln">${ic('FamilyRestroom')} ${fmt(mb.families)} families</div>
        <div class="ln">${ic('Layers')} ${mb.subs.length} sub-blocks</div></div>
      ${ic('Terrain', 'deco')}</div>`).join('');

  main.innerHTML = `<div class="dash page">
    <div class="banner">
      <button class="iconbtn menubtn" data-menu-open title="Menu">${ic('Menu')}</button>
      <div class="tools" id="tools">
        <button class="bell" id="bellBtn" title="Notifications">${ic('Notifications')}<i class="dot hidden" id="bellDot"></i></button>
        <button class="datecard" id="dateBtn" title="Calendar">${ic('CalendarMonth')}<div><b>${today.getDate()} ${MONTHS_LONG[today.getMonth()].slice(0, 3)} ${today.getFullYear()}</b><small>${DAYS[today.getDay()]} ${ic('KeyboardArrowDown')}</small></div></button>
      </div>
    </div>
    <div class="sumcards">
      ${sum('green', 'Groups', 'Groups', st.total, 'Total Population', '', {})}
      ${sum('orange', 'FamilyRestroom', 'House', st.families, 'Families', '', {})}
      ${sum('blue', 'Man', 'Man', st.male, 'Male', '(' + pct(st.male || 0) + ')', { sex: 'M' })}
      ${sum('pink', 'Woman', 'Woman', st.female, 'Female', '(' + pct(st.female || 0) + ')', { sex: 'F' })}
    </div>
    <div class="dgrid">
      <section class="panel area-info"><div class="ph">${ic('Info')}<h3>Camp Information</h3>${isAdmin() ? `<button class="edit" data-edit-info>${ic('Edit')} Edit</button>` : ''}</div>
        <div class="pb">${campInfoHtml(ci)}</div></section>
      <section class="panel area-age"><div class="ph">${ic('People')}<h3>Population by Age Group</h3></div>
        <div class="pb"><div class="agechart">
          <div class="donut">${donut(ages, ageTotal)}<div class="mid"><b>${fmt(tot)}</b><span>People</span></div></div>
          <div class="legend">${ages.map((x) => `<div class="lr" data-link='${esc(JSON.stringify({ age: x.k }))}'><i style="background:${x.color}"></i><span>${x.label}</span><b>${fmt(x.v)}</b><span>(${pct(x.v)})</span></div>`).join('')}
            ${tot - ageTotal > 0 ? `<div class="lr" data-link='${esc(JSON.stringify({ age: 'nodob' }))}'><i style="background:#B0BEC5"></i><span>No date of birth</span><b>${fmt(tot - ageTotal)}</b><span>(${pct(tot - ageTotal)})</span></div>` : ''}
          </div></div></div></section>
      <section class="panel area-key"><div class="ph">${ic('Assignment')}<h3>Key Summary</h3></div>
        <div class="pb keylist">
          <div class="kr">${ic('Groups')}<span>Total Population</span><b>${fmt(st.total)}</b></div>
          <div class="kr">${ic('FamilyRestroom')}<span>Total Families</span><b>${fmt(st.families)}</b></div>
          <div class="kr">${ic('Layers')}<span>Total Blocks</span><b>${fmt(st.main_blocks)}</b></div>
          <div class="kr">${ic('GridView')}<span>Total Sub-blocks</span><b>${fmt(st.sub_blocks)}</b></div>
        </div></section>
      <section class="panel area-blocks"><div class="ph">${ic('Layers')}<h3>Block-wise Summary</h3></div>
        <div class="pb">${blocks ? `<div class="bgrid">${blocks}</div>` : emptyBox('No records yet', isAdmin() ? 'Go to Settings → Import from Excel to upload your beneficiary list.' : 'The Admin has not added any records yet.')}</div></section>
      <section class="panel area-emerg"><div class="ph">${ic('NotificationImportant', 'red')}<h3 class="emerg-title">Emergency Response</h3>${isAdmin() ? `<button class="edit" data-edit-contacts>${ic('Edit')} Edit</button>` : ''}</div>
        <div class="pb">${contactsHtml(contacts)}</div></section>
      <section class="panel area-quick"><div class="quick">
        <div class="qt">${ic('Bolt')} Quick Actions</div>
        ${isAdmin() ? `<button class="qbtn t-green" data-q="add">${ic('AddCircle')} Add Record</button>` : ''}
        <button class="qbtn t-blue" data-q="reports">${ic('BarChart')} Reports</button>
        <button class="qbtn t-purple" data-q="export">${ic('Download')} Export</button>
        <button class="qbtn t-yellow" data-q="print">${ic('Print')} Print</button>
      </div></section>
    </div>
  </div>`;
  updateNav();
  $('#bellBtn').onclick = (e) => openBell(e.currentTarget);
  $('#dateBtn').onclick = (e) => openCalendar(e.currentTarget);
  $$('[data-link]', main).forEach((el) => { el.onclick = () => openRecords(JSON.parse(el.dataset.link)); });
  const ei = $('[data-edit-info]', main); if (ei) ei.onclick = () => editCampInfo();
  const ec = $('[data-edit-contacts]', main); if (ec) ec.onclick = () => editContacts();
  $$('[data-q]', main).forEach((b) => {
    b.onclick = () => {
      const q = b.dataset.q;
      if (q === 'add') { S.preset = null; go('new'); }
      else if (q === 'reports') go('reports');
      else chooseBlocks(q);
    };
  });
}

// choose Main Block / Sub-Block before Export or Print
function chooseBlocks(kind) {
  const opts = (list) => list.map((n) => `<option value="${esc(n)}">${esc(n)}</option>`).join('');
  modal(`<h3>${kind === 'print' ? 'Print records' : 'Export records'}</h3>
    <p class="muted" style="margin-top:0">Choose which records to ${kind === 'print' ? 'print' : 'export'}.</p>
    <label class="field"><span>Main Block</span><select class="input" id="cbMain"><option value="">All Main Blocks</option>${opts(mainNames())}</select></label>
    <label class="field"><span>Sub-Block</span><select class="input" id="cbSub"><option value="">All Sub-Blocks</option>${opts(subNames(''))}</select></label>
    <label class="field"><span>Age group</span><select class="input" id="cbAge"><option value="">All ages</option>${AGE_GROUPS.map(([k, l]) => `<option value="${k}">${l}</option>`).join('')}</select></label>
    <div class="actions"><button class="btn ghost" data-cancel>Cancel</button>
      ${kind === 'print' ? `<button class="btn" data-go="print">${ic('Print')} Print</button>`
        : `<button class="btn outline" data-go="pdf">${ic('PictureAsPdf')} PDF</button><button class="btn" data-go="xlsx">${ic('Download')} Excel</button>`}</div>`, (m, close) => {
    const main = $('#cbMain', m), sub = $('#cbSub', m);
    main.onchange = () => { sub.innerHTML = `<option value="">All Sub-Blocks</option>${opts(subNames(main.value))}`; };
    $('[data-cancel]', m).onclick = close;
    $$('[data-go]', m).forEach((b) => {
      b.onclick = () => {
        const f = {};
        if (main.value) f.main = main.value;
        if (sub.value) f.sub = sub.value;
        if ($('#cbAge', m).value) f.age = $('#cbAge', m).value;
        close();
        if (b.dataset.go === 'print') printList(f); else if (b.dataset.go === 'pdf') exportPdf(f); else exportExcel(f);
      };
    });
  });
}

function editCampInfo() {
  const ci = Object.assign({}, (S.settings && S.settings.camp_info) || {});
  const f = (k, label) => `<label class="field"><span>${label}</span><input class="input" name="${k}" value="${esc(ci[k] || '')}"></label>`;
  modal(`<h3>Edit Camp Information</h3><form id="ciForm">
    <div class="row2">${f('area', 'Camp Area')}${f('organisations', 'Total Organisations')}</div>
    <div class="row2">${f('un_agencies', 'UN Agencies')}${f('ngos', "NGO's & INGO's")}</div>
    <div class="row2">${f('projects', 'Total Projects')}${f('sectors', 'Total Sectors')}</div>
    <div class="err" id="ciErr"></div>
    <div class="actions"><button type="button" class="btn ghost" data-cancel>Cancel</button><button class="btn" type="submit">${ic('Save')} Save</button></div></form>`, (m, close) => {
    $('[data-cancel]', m).onclick = close;
    $('#ciForm', m).onsubmit = async (e) => {
      e.preventDefault();
      const btn = $('button[type=submit]', m);
      busyBtn(btn, true);
      try {
        const r = await api('settings_save', { key: 'camp_info', value: Object.fromEntries(new FormData(e.target)) });
        S.settings = Object.assign({}, S.settings, { camp_info: r.value });
        close(); toast('Camp Information saved.'); render();
      } catch (ex) { $('#ciErr', m).textContent = ex.message; busyBtn(btn, false); }
    };
  });
}
function contactRow(c) {
  const types = [['fire', 'Fire'], ['police', 'Police'], ['hospital', 'Hospital'], ['site', 'Site / Office'], ['other', 'Other']];
  return `<div class="crow"><select class="input" data-k="type">${types.map(([v, l]) => `<option value="${v}" ${c.type === v ? 'selected' : ''}>${l}</option>`).join('')}</select>
    <input class="input" data-k="name" placeholder="Name" value="${esc(c.name || '')}"><input class="input" data-k="phone" placeholder="Phone" inputmode="tel" value="${esc(c.phone || '')}">
    <button type="button" class="iconbtn" style="color:var(--danger)" data-rm title="Remove">${ic('Delete')}</button></div>`;
}
function readContacts(box) {
  return $$('.crow', box).map((r) => ({ type: $('[data-k=type]', r).value, name: $('[data-k=name]', r).value.trim(), phone: $('[data-k=phone]', r).value.trim() })).filter((c) => c.name || c.phone);
}
function bindContactEditor(box) {
  box.onclick = (e) => { const rm = e.target.closest('[data-rm]'); if (rm) rm.closest('.crow').remove(); };
}
function editContacts() {
  const list = (S.settings && S.settings.contacts) || [];
  modal(`<h3>Edit Emergency Contacts</h3><div id="cEd">${list.map(contactRow).join('')}</div>
    <button class="btn ghost small" data-addc>${ic('Add')} Add contact</button><div class="err" id="cErr"></div>
    <div class="actions"><button class="btn ghost" data-cancel>Cancel</button><button class="btn" data-save>${ic('Save')} Save</button></div>`, (m, close) => {
    const box = $('#cEd', m);
    bindContactEditor(box);
    $('[data-addc]', m).onclick = () => box.insertAdjacentHTML('beforeend', contactRow({ type: 'other' }));
    $('[data-cancel]', m).onclick = close;
    $('[data-save]', m).onclick = async (e) => {
      busyBtn(e.currentTarget, true);
      try {
        const r = await api('settings_save', { key: 'contacts', value: readContacts(box) });
        S.settings = Object.assign({}, S.settings, { contacts: r.value });
        close(); toast('Emergency contacts saved.'); render();
      } catch (ex) { $('#cErr', m).textContent = ex.message; busyBtn(e.currentTarget, false); }
    };
  });
  const md = $$('.modal').pop(); if (md) md.style.maxWidth = '640px';
}
function openRecords(f) {
  S.rec.filters = Object.assign({}, f || {});
  S.rec.key = null;
  go('records');
}

// ---------------- CAMP PROFILE ----------------
function pageCamp(main) {
  const ci = (S.settings && S.settings.camp_info) || {};
  main.innerHTML = topbar('Camp Profile', 'About the Camp, FDMN and Activities') + `
    <div class="page pad" style="max-width:1100px">
      <img src="assets/logo_full.png" alt="Office of the Camp-in-Charge, Camp-8 West" style="width:100%;max-width:620px;display:block;margin:4px auto 16px">
      <div class="settings-grid">
        <section class="panel"><div class="ph">${ic('Info')}<h3>Camp Information</h3>${isAdmin() ? `<button class="edit" data-edit-info>${ic('Edit')} Edit</button>` : ''}</div><div class="pb">${campInfoHtml(ci)}</div></section>
        <section class="panel"><div class="ph">${ic('NotificationImportant', 'red')}<h3 class="emerg-title">Emergency Response</h3>${isAdmin() ? `<button class="edit" data-edit-contacts>${ic('Edit')} Edit</button>` : ''}</div><div class="pb">${contactsHtml((S.settings && S.settings.contacts) || [])}</div></section>
      </div>
      <div class="wcard t-camp" style="margin-top:14px">${wave()}<div class="in" style="padding:24px;text-align:center">
        <div class="tile" style="margin:0 auto 12px">${ic('Info')}</div>
        <h2 style="margin:0 0 6px;color:var(--camp)">Camp- 8 West</h2>
        <p class="muted" style="margin:0;font-size:16px">Camp information will be added soon.</p>
      </div></div>
    </div>`;
  const ei = $('[data-edit-info]', main); if (ei) ei.onclick = () => editCampInfo();
  const ec = $('[data-edit-contacts]', main); if (ec) ec.onclick = () => editContacts();
}

// ---------------- RECORDS ----------------
function personCard(p, sl) {
  const tone = sexTone(p.sex);
  const block = [p.main_block, p.sub_block].filter(Boolean).join(' / ');
  return `<div class="wcard pcard click t-${tone}" data-id="${p.id}">${wave()}<div class="in">
    <div class="slbox"><small>SL</small><b>${fmt(sl)}</b></div>
    <div class="body">
      <div class="nm"><span>${esc(p.name)}</span>${p.relation ? `<span class="tag" style="--c:#C27410">${esc(p.relation)}</span>` : ''}</div>
      <div class="l2">${[sexText(p.sex), XL.ageText(p.dob), XL.displayDate(p.dob)].filter(Boolean).map(esc).join(' · ')}</div>
      <div class="l3">FCN ${esc(p.fcn || '—')} · ${esc(block || 'No block')}${p.majhi ? ' · ' + esc(p.majhi) : ''}</div>
    </div></div></div>`;
}
function recordsTable(items) {
  return `<table class="grid"><thead><tr><th>SL</th><th>Name</th><th>Sex</th><th class="c-opt">Age</th><th>FCN</th><th>Date of birth</th><th>Relation</th><th>Main Block</th><th>Sub-Block</th><th>Majhi</th><th class="c-opt">Remarks</th></tr></thead><tbody>
    ${items.map((p, i) => `<tr data-id="${p.id}"><td>${fmt(i + 1)}</td><td class="name">${esc(p.name)}</td><td class="sx-${esc(p.sex)}">${esc(p.sex)}</td><td class="c-opt">${esc(XL.ageText(p.dob))}</td><td>${esc(p.fcn)}</td><td>${esc(XL.displayDate(p.dob))}</td><td>${esc(p.relation)}</td><td>${esc(p.main_block)}</td><td>${esc(p.sub_block)}</td><td>${esc(p.majhi)}</td><td class="rem c-opt">${esc(p.remarks)}</td></tr>`).join('')}
  </tbody></table>`;
}
function recKey() { return JSON.stringify(S.rec.filters) + '|' + S.version; }

async function loadRecords(reset) {
  const R = S.rec;
  if (!reset && (R.loading || R.items.length >= R.total)) return;
  const gen = reset ? ++R.gen : R.gen;
  R.loading = true; R.error = null;
  if (reset) { R.items = []; R.total = 0; }
  renderRecList();
  try {
    const r = await api('list', Object.assign({}, R.filters, { per: 100, offset: reset ? 0 : R.items.length }));
    if (gen !== R.gen) return;
    const have = new Set(R.items.map((p) => p.id));
    const more = (r.rows || []).filter((p) => !have.has(p.id));
    R.items = R.items.concat(more);
    R.total = more.length ? r.total : R.items.length;
    R.key = recKey();
  } catch (e) {
    if (gen === R.gen) R.error = e.message;
  } finally {
    if (gen === R.gen) { R.loading = false; renderRecList(); }
  }
}

let recObserver = null;
function renderRecList() {
  const box = $('#recList');
  if (!box) return;
  const R = S.rec;
  const sub = $('#topSub');
  if (sub) sub.textContent = `${fmt(R.total)} found · ${describe(R.filters)}`;
  const cnt = $('#recCount');
  if (cnt) cnt.innerHTML = R.items.length || !R.loading ? `<b>${fmt(R.total)}</b> records · ${esc(describe(R.filters))}${R.items.length < R.total ? ` · showing ${fmt(R.items.length)}` : ''}` : 'Loading…';
  if (!R.items.length) {
    box.innerHTML = R.loading ? loading() : R.error ? errorBox(R.error)
      : emptyBox('No records found', filtersActive(R.filters) ? 'Try a different search or clear the filters.' : 'No records have been added yet.');
    const rt = $('[data-retry]', box); if (rt) rt.onclick = () => loadRecords(true);
    return;
  }
  const more = R.items.length < R.total;
  box.innerHTML = `
    <div class="tablewrap">${recordsTable(R.items)}</div>
    <div class="plist records">${R.items.map((p, i) => personCard(p, i + 1)).join('')}</div>
    ${more || R.loading ? `<div class="loadmore" id="sentinel">${R.error ? `<button class="btn outline small" data-more>Load more</button>` : '<span class="spinner"></span>'}</div>` : ''}`;
  $$('[data-id]', box).forEach((el) => { el.onclick = () => go('record/' + el.dataset.id); });
  const lm = $('[data-more]', box); if (lm) lm.onclick = () => { R.error = null; loadRecords(false); };
  if (recObserver) recObserver.disconnect();
  const sen = $('#sentinel');
  if (sen && !R.error && 'IntersectionObserver' in window) {
    recObserver = new IntersectionObserver((es) => { if (es.some((e) => e.isIntersecting)) loadRecords(false); }, { rootMargin: '600px' });
    recObserver.observe(sen);
  }
}

function pageRecords(main) {
  const R = S.rec, f = R.filters;
  const opt = (v, label, cur) => `<option value="${esc(v)}" ${v === cur ? 'selected' : ''}>${esc(label)}</option>`;
  const sel = (key, allLabel, options) => `<select class="input ${f[key] ? 'set' : ''}" data-f="${key}">${opt('', allLabel, f[key] || '')}${options.map(([v, l]) => opt(v, l, f[key] || '')).join('')}</select>`;
  main.innerHTML = topbar('Records', `${fmt(R.total)} found · ${describe(f)}`, {
    actions: `<button class="iconbtn" data-xlsx title="Export to Excel">${ic('Download')}</button><button class="iconbtn" data-print title="Print">${ic('Print')}</button>`,
  }) + `
    <div class="page pad">
      <div class="toolbar">
        <label class="search">${ic('Search')}<input class="input" id="q" type="search" placeholder="Search name, FCN or Majhi" value="${esc(f.q || '')}"></label>
        <button class="btn outline small" data-xlsx>${ic('Download')} Excel</button>
        <button class="btn outline small" data-pdf>${ic('PictureAsPdf')} PDF</button>
        <button class="btn outline small" data-print>${ic('Print')} Print</button>
      </div>
      <div class="filters">
        ${sel('main', 'Main Block: All', mainNames().map((n) => [n, 'Main ' + n]))}
        ${sel('sub', 'Sub-Block: All', subNames(f.main).map((n) => [n, 'Sub ' + n]))}
        ${sel('sex', 'Sex: All', [['M', 'Male'], ['F', 'Female']])}
        ${sel('age', 'Age group: All', AGE_GROUPS)}
        ${sel('relation', 'Relation: All', S.meta.relations.map((r) => [r, r]))}
        ${sel('majhi', 'Majhi: All', S.meta.majhis.map((m) => [m, m]))}
        ${filtersActive(f) ? `<button class="btn ghost small" data-clear>${ic('Close')} Clear</button>` : ''}
      </div>
      <div class="count" id="recCount"></div>
      <div id="recList"></div>
    </div>
    ${isAdmin() ? `<button class="fab" data-add>${ic('Add')} Add</button>` : ''}`;

  let t = null;
  $('#q', main).oninput = (e) => {
    clearTimeout(t);
    t = setTimeout(() => { R.filters = Object.assign({}, R.filters, { q: e.target.value.trim() }); if (!R.filters.q) delete R.filters.q; loadRecords(true); }, 450);
  };
  $$('[data-f]', main).forEach((s) => {
    s.onchange = () => {
      const k = s.dataset.f;
      const nf = Object.assign({}, R.filters, { [k]: s.value });
      if (k === 'main' && nf.sub && !subNames(nf.main).includes(nf.sub)) nf.sub = '';
      Object.keys(nf).forEach((x) => { if (!nf[x]) delete nf[x]; });
      R.filters = nf;
      R.key = null;
      render();
    };
  });
  const cl = $('[data-clear]', main); if (cl) cl.onclick = () => { R.filters = {}; R.key = null; render(); };
  $$('[data-xlsx]', main).forEach((b) => { b.onclick = () => exportExcel(R.filters); });
  $$('[data-pdf]', main).forEach((b) => { b.onclick = () => exportPdf(R.filters); });
  $$('[data-print]', main).forEach((b) => { b.onclick = () => printList(R.filters); });
  const add = $('[data-add]', main); if (add) add.onclick = () => { S.preset = { main_block: R.filters.main || '', sub_block: R.filters.sub || '' }; go('new'); };

  if (R.key !== recKey()) loadRecords(true);
  else { renderRecList(); requestAnimationFrame(() => window.scrollTo(0, R.scroll || 0)); }
}

async function fetchAll(filters, onProgress) {
  const all = [];
  for (;;) {
    const r = await api('list', Object.assign({}, filters, { per: 2000, offset: all.length }));
    all.push(...(r.rows || []));
    onProgress(all.length, r.total);
    if (!(r.rows || []).length || all.length >= r.total) break;
  }
  return all;
}
function progressModal(title) {
  let closeFn;
  modal(`<h3>${esc(title)}</h3><p id="pmText">Starting…</p><div class="progress" id="pmBar"><div></div></div>`, (m, close, bg) => { closeFn = close; bg.dataset.busy = '1'; });
  return {
    set(text, frac) { const t = $('#pmText'); if (t) t.textContent = text; const b = $('#pmBar'); if (b) { b.classList.toggle('indet', frac == null); b.firstElementChild.style.width = frac == null ? '' : Math.round(frac * 100) + '%'; } },
    close() { closeFn(); },
  };
}
function stamp() { const d = new Date(); const p = (n) => String(n).padStart(2, '0'); return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}_${p(d.getHours())}${p(d.getMinutes())}`; }

function downloadBlob(blob, name) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = name;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 60000);
}
async function exportExcel(filters) {
  const f = Object.assign({}, filters || {});
  const pm = progressModal('Export to Excel');
  try {
    const rows = await fetchAll(f, (n, t) => pm.set(`Downloading records… ${fmt(n)} / ${fmt(t)}`, t ? n / t : null));
    if (!rows.length) { pm.close(); toast('No records to export.'); return; }
    pm.set('Creating Excel file…', null);
    await new Promise((r) => setTimeout(r, 30));
    const blob = XL.writeXlsx(
      ['SL', 'Name', 'Sex', 'FCN', 'Date of birth', 'Relation', 'Main Block', 'Sub-Block', 'Majhi', 'Remarks'],
      rows.map((p, i) => [i + 1, p.name, p.sex, p.fcn, XL.displayDate(p.dob), p.relation, p.main_block, p.sub_block, p.majhi, p.remarks]),
      [7, 28, 6, 11, 14, 10, 11, 11, 22, 30],
    );
    downloadBlob(blob, `FDMN_8W_${stamp()}.xlsx`);
    pm.close();
    toast(`Excel file with ${fmt(rows.length)} records downloaded.`);
  } catch (e) { pm.close(); toast(e.message); }
}

async function printList(filters) {
  const f = Object.assign({}, filters || {});
  let total = 0;
  try { total = (await api('list', Object.assign({}, f, { per: 1, offset: 0 }))).total; } catch (e) { toast(e.message); return; }
  if (!total) { toast('No records to print.'); return; }
  if (total > 5000 && !(await confirmBox('Large list', `This list has ${fmt(total)} records (about ${fmt(Math.ceil(total / 38))} pages). Printing may be slow. Continue?`, 'Continue'))) return;
  const pm = progressModal('Print / Save as PDF');
  try {
    const rows = await fetchAll(f, (n, t) => pm.set(`Downloading records… ${fmt(n)} / ${fmt(t)}`, t ? n / t : null));
    pm.set('Preparing pages…', null);
    const now = new Date().toLocaleString('en-GB', { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' });
    showPrint(`<div class="phead"><img src="assets/logo_full.png" alt=""><div><h2>Camp- 8 West · Beneficiary list</h2><div class="meta">${esc(describe(f))} · ${fmt(rows.length)} records · Printed ${esc(now)}</div></div></div>
      <table><thead><tr><th>SL</th><th>Name</th><th>Sex</th><th>FCN</th><th>Date of birth</th><th>Relation</th><th>Main Block</th><th>Sub-Block</th><th>Majhi</th><th>Remarks</th></tr></thead><tbody>
      ${rows.map((p, i) => `<tr><td>${i + 1}</td><td>${esc(p.name)}</td><td>${esc(p.sex)}</td><td>${esc(p.fcn)}</td><td>${esc(XL.displayDate(p.dob))}</td><td>${esc(p.relation)}</td><td>${esc(p.main_block)}</td><td>${esc(p.sub_block)}</td><td>${esc(p.majhi)}</td><td>${esc(p.remarks)}</td></tr>`).join('')}
      </tbody></table><div class="pfoot">Confidential — for official use only.</div>`, pm);
  } catch (e) { pm.close(); toast(e.message); }
}
async function showPrint(html, pm) {
  let pa = $('#printArea');
  if (!pa) { pa = document.createElement('div'); pa.id = 'printArea'; document.body.appendChild(pa); }
  pa.innerHTML = html;
  if (pm) pm.close();
  const img = $('img', pa);
  if (img) await new Promise((r) => { if (img.complete) r(); else { img.onload = r; img.onerror = r; } });
  document.body.classList.add('printing');
  const done = () => { document.body.classList.remove('printing'); pa.innerHTML = ''; window.removeEventListener('afterprint', done); };
  window.addEventListener('afterprint', done);
  setTimeout(() => { window.print(); setTimeout(() => { if (document.body.classList.contains('printing') && !window.matchMedia('print').matches) done(); }, 1500); }, 50);
}

// ---------------- PDF files (pdf-lib, loaded only when needed) ----------------
let pdfLibLoading = null;
function loadPdfLib() {
  if (window.PDFLib) return Promise.resolve();
  if (!pdfLibLoading) {
    pdfLibLoading = new Promise((res, rej) => {
      const sc = document.createElement('script');
      sc.src = 'assets/vendor/pdf-lib.min.js';
      sc.onload = res;
      sc.onerror = () => { pdfLibLoading = null; rej(new Error('Could not load the PDF maker. Check your internet and try again.')); };
      document.head.appendChild(sc);
    });
  }
  return pdfLibLoading;
}
/** Builds a landscape A4 table PDF. cols: [{t, w, right}], rows: arrays of text; styles: optional per-row 'sub' | 'grand'. */
async function makeTablePdf(title, subtitle, cols, rows, styles) {
  await loadPdfLib();
  const { PDFDocument, StandardFonts, rgb } = window.PDFLib;
  const doc = await PDFDocument.create();
  const font = await doc.embedFont(StandardFonts.Helvetica);
  const bold = await doc.embedFont(StandardFonts.HelveticaBold);
  let logo = null;
  try { logo = await doc.embedPng(await (await fetch('assets/logo_full.png')).arrayBuffer()); } catch (e) { /* no logo */ }
  const W = 842, H = 595, M = 28, RH = 15, FS = 8;
  const safe = (t) => String(t == null ? '' : t).replace(/[–—]/g, '-').replace(/[‘’]/g, "'").replace(/[“”]/g, '"').replace(/·/g, '-').replace(/[^\x20-\x7E\xA0-\xFF]/g, '?');
  const fit = (t, w, f) => { t = safe(t); if (f.widthOfTextAtSize(t, FS) <= w) return t; while (t && f.widthOfTextAtSize(t + '...', FS) > w) t = t.slice(0, -1); return t + '...'; };
  const totalW = cols.reduce((a, c) => a + c.w, 0);
  const scale = (W - 2 * M) / totalW;
  const ws = cols.map((c) => c.w * scale);
  const green = rgb(0.043, 0.365, 0.294), head = rgb(0.804, 0.922, 0.882), alt = rgb(0.965, 0.98, 0.976), line = rgb(0.85, 0.89, 0.87);
  const stampTxt = new Date().toLocaleString('en-GB', { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' });
  const pages = [];
  let page, y, i = 0;
  const newPage = (first) => {
    page = doc.addPage([W, H]); pages.push(page);
    y = H - M;
    if (first) {
      let tx = M;
      if (logo) { const lh = 46, lw = lh * logo.width / logo.height; page.drawImage(logo, { x: M, y: y - lh, width: lw, height: lh }); tx = M + lw + 14; }
      page.drawText(safe(title), { x: tx, y: y - 18, size: 15, font: bold, color: green });
      page.drawText(safe(subtitle), { x: tx, y: y - 32, size: 9, font, color: rgb(0.3, 0.36, 0.34) });
      page.drawText(safe('Created ' + stampTxt), { x: tx, y: y - 44, size: 8, font, color: rgb(0.3, 0.36, 0.34) });
      y -= 58;
    }
    page.drawRectangle({ x: M, y: y - RH, width: W - 2 * M, height: RH, color: head });
    let x = M;
    cols.forEach((c, k) => {
      const t = fit(c.t, ws[k] - 6, bold);
      const tw = bold.widthOfTextAtSize(t, FS);
      page.drawText(t, { x: c.right ? x + ws[k] - 3 - tw : x + 3, y: y - 10.5, size: FS, font: bold, color: rgb(0.03, 0.25, 0.18) });
      x += ws[k];
    });
    y -= RH;
  };
  newPage(true);
  for (; i < rows.length; i++) {
    if (y - RH < M + 14) newPage(false);
    const st = styles && styles[i];
    if (st === 'grand') page.drawRectangle({ x: M, y: y - RH, width: W - 2 * M, height: RH, color: green });
    else if (st === 'sub') page.drawRectangle({ x: M, y: y - RH, width: W - 2 * M, height: RH, color: head });
    else if (i % 2) page.drawRectangle({ x: M, y: y - RH, width: W - 2 * M, height: RH, color: alt });
    const f = st ? bold : font;
    let x = M;
    rows[i].forEach((v, k) => {
      const t = fit(v, ws[k] - 6, f);
      const tw = f.widthOfTextAtSize(t, FS);
      page.drawText(t, { x: cols[k].right ? x + ws[k] - 3 - tw : x + 3, y: y - 10.5, size: FS, font: f, color: st === 'grand' ? rgb(1, 1, 1) : rgb(0.09, 0.13, 0.11) });
      x += ws[k];
    });
    y -= RH;
    page.drawLine({ start: { x: M, y }, end: { x: W - M, y }, thickness: 0.4, color: line });
    if (i % 3000 === 2999) await new Promise((r) => setTimeout(r, 0));
  }
  pages.forEach((pg, k) => {
    pg.drawText(`Page ${k + 1} of ${pages.length}`, { x: W - M - 60, y: M - 12, size: 8, font, color: rgb(0.4, 0.45, 0.43) });
    pg.drawText('Camp- 8 West - Confidential, for official use only', { x: M, y: M - 12, size: 8, font, color: rgb(0.4, 0.45, 0.43) });
  });
  return new Blob([await doc.save()], { type: 'application/pdf' });
}
async function exportPdf(filters) {
  const f = Object.assign({}, filters || {});
  const pm = progressModal('Export to PDF');
  try {
    const rows = await fetchAll(f, (n, t) => pm.set(`Downloading records… ${fmt(n)} / ${fmt(t)}`, t ? n / t : null));
    if (!rows.length) { pm.close(); toast('No records to export.'); return; }
    pm.set(`Creating PDF… (${fmt(rows.length)} records)`, null);
    await new Promise((r) => setTimeout(r, 30));
    const blob = await makeTablePdf('Camp- 8 West - Beneficiary list', `${describe(f)} - ${fmt(rows.length)} records`,
      [{ t: 'SL', w: 34, right: true }, { t: 'Name', w: 150 }, { t: 'Sex', w: 30 }, { t: 'FCN', w: 58 }, { t: 'Date of birth', w: 70 }, { t: 'Relation', w: 52 }, { t: 'Main Block', w: 52 }, { t: 'Sub-Block', w: 56 }, { t: 'Majhi', w: 120 }, { t: 'Remarks', w: 150 }],
      rows.map((p, i) => [i + 1, p.name, p.sex, p.fcn, XL.displayDate(p.dob), p.relation, p.main_block, p.sub_block, p.majhi, p.remarks]));
    downloadBlob(blob, `FDMN_8W_${stamp()}.pdf`);
    pm.close();
    toast(`PDF with ${fmt(rows.length)} records downloaded.`);
  } catch (e) { pm.close(); toast(e.message); }
}

// ---------------- DETAIL ----------------
async function pageDetail(main, id) {
  main.innerHTML = topbar('Record', '', { back: true }) + loading();
  let p;
  try { p = (await api('get', { id: +id })).row; } catch (e) {
    main.innerHTML = topbar('Record', '', { back: true }) + errorBox(e.message);
    $('[data-retry]', main).onclick = () => render();
    return;
  }
  if (route().name !== 'record') return;
  const tone = sexTone(p.sex);
  const row = (k, v) => `<div class="inforow"><span>${k}</span><b>${esc(v || '—')}</b></div>`;
  main.innerHTML = topbar(p.name, 'FCN ' + p.fcn, {
    back: true,
    actions: isAdmin() ? `<button class="iconbtn" data-edit title="Edit">${ic('Edit')}</button><button class="iconbtn" data-del title="Delete">${ic('Delete')}</button>` : '',
  }) + `
    <div class="page pad"><div class="twocol">
      <div class="wcard t-${tone}">${wave()}<div class="in" style="padding:16px">
        <div style="display:flex;align-items:center;gap:12px;margin-bottom:8px">
          <div class="tile">${ic('Person')}</div>
          <div><div style="font-size:20px;font-weight:800">${esc(p.name)}</div>
            <div style="display:flex;gap:6px;flex-wrap:wrap;margin-top:4px">
              ${p.sex ? `<span class="tag" style="--c:var(--d)">${esc(sexText(p.sex))}</span>` : ''}
              ${XL.ageText(p.dob) ? `<span class="tag">${esc(XL.ageText(p.dob))}</span>` : ''}
              ${p.relation ? `<span class="tag" style="--c:#C27410">${esc(p.relation)}</span>` : ''}
            </div></div>
        </div>
        ${row('Name', p.name)}${row('Sex', sexText(p.sex))}${row('FCN', p.fcn)}${row('Date of birth', XL.displayDate(p.dob))}${row('Age', XL.ageText(p.dob))}
        ${row('Relation', p.relation)}${row('Main Block', p.main_block)}${row('Sub-Block', p.sub_block)}${row('Majhi', p.majhi)}${row('Remarks', p.remarks)}
        ${p.updated_at ? `<p class="muted" style="font-size:12px;margin:10px 0 0">Last updated ${esc(p.updated_at)}${p.updated_by_name ? ' by ' + esc(p.updated_by_name) : ''}</p>` : ''}
      </div></div>
      <div>
        ${p.fcn ? `<button class="btn block" data-family style="margin-bottom:10px">${ic('FamilyRestroom')} View whole family (FCN ${esc(p.fcn)})</button>` : ''}
        ${isAdmin() ? `<div class="btnrow" style="margin-top:0"><button class="btn outline" data-edit>${ic('Edit')} Edit</button><button class="btn outline" data-del style="color:var(--danger);border-color:var(--danger)">${ic('Delete')} Delete</button></div>`
          : `<button class="btn outline block" data-req>${ic('MailOutline')} Request a correction</button>`}
      </div>
    </div></div>`;
  const fam = $('[data-family]', main); if (fam) fam.onclick = () => go('family/' + encodeURIComponent(p.fcn));
  $$('[data-edit]', main).forEach((b) => { b.onclick = () => go('edit/' + p.id); });
  $$('[data-del]', main).forEach((b) => {
    b.onclick = async () => {
      if (!(await confirmBox('Delete record?', `${p.name} (FCN ${p.fcn}) will be permanently deleted. This cannot be undone.`, 'Delete', true))) return;
      try { await api('delete', { id: p.id }); toast('Record deleted.'); changed(); back('records'); } catch (e) { toast(e.message); }
    };
  });
  const rq = $('[data-req]', main); if (rq) rq.onclick = () => composeMessage(`Correction: ${p.name} (FCN ${p.fcn})`, p.id);
}

// ---------------- FAMILY ----------------
async function pageFamily(main, fcn) {
  main.innerHTML = topbar('Family · FCN ' + fcn, '', { back: true }) + loading();
  let rows;
  try { rows = (await api('family', { fcn })).rows || []; } catch (e) {
    main.innerHTML = topbar('Family · FCN ' + fcn, '', { back: true }) + errorBox(e.message);
    $('[data-retry]', main).onclick = () => render();
    return;
  }
  if (route().name !== 'family') return;
  const head = rows[0] || {};
  const m = rows.filter((r) => r.sex === 'M').length, fe = rows.filter((r) => r.sex === 'F').length;
  const kids = rows.filter((r) => { const a = ageYears(r.dob); return a != null && a < 18; }).length;
  main.innerHTML = topbar('Family · FCN ' + fcn, rows.length + ' members', { back: true }) + `
    <div class="page pad">
      ${rows.length ? `<div class="wcard t-orange" style="margin-bottom:12px">${wave()}<div class="in" style="padding:16px">
        <div style="display:flex;align-items:center;gap:12px"><div class="tile">${ic('FamilyRestroom')}</div><div style="font-size:22px;font-weight:800;color:var(--d)">FCN ${esc(fcn)}</div></div>
        <p class="muted" style="margin:8px 0 10px">${esc([[head.main_block, head.sub_block].filter(Boolean).join(' / '), head.majhi ? 'Majhi ' + head.majhi : ''].filter(Boolean).join(' · '))}</p>
        <div style="display:flex;gap:6px;flex-wrap:wrap"><span class="tag">${rows.length} members</span><span class="tag" style="--c:var(--male)">${m} male</span><span class="tag" style="--c:var(--female)">${fe} female</span>${kids ? `<span class="tag" style="--c:#C27410">${kids} under 18</span>` : ''}</div>
      </div></div>
      <div class="plist">${rows.map((p, i) => personCard(p, i + 1)).join('')}</div>` : emptyBox('No members', 'No records found with FCN ' + fcn + '.')}
    </div>
    ${isAdmin() && rows.length ? `<button class="fab" data-add>${ic('Add')} Add member</button>` : ''}`;
  $$('[data-id]', main).forEach((el) => { el.onclick = () => go('record/' + el.dataset.id); });
  const add = $('[data-add]', main);
  if (add) add.onclick = () => { S.preset = { fcn, main_block: head.main_block, sub_block: head.sub_block, majhi: head.majhi }; go('new'); };
}

// ---------------- ADD / EDIT ----------------
async function pageEdit(main, id) {
  const isNew = route().name === 'new';
  if (!isAdmin()) { main.innerHTML = topbar('Not allowed', '', { back: true }) + emptyBox('Only an Admin can do this.', ''); return; }
  let p = Object.assign({ id: 0, name: '', sex: '', fcn: '', dob: '', relation: '', main_block: '', sub_block: '', majhi: '', remarks: '' }, isNew ? (S.preset || {}) : {});
  if (!isNew) {
    main.innerHTML = topbar('Edit record', '', { back: true }) + loading();
    try { p = (await api('get', { id: +id })).row; } catch (e) { main.innerHTML = topbar('Edit record', '', { back: true }) + errorBox(e.message); return; }
    if (route().name !== 'edit') return;
  }
  const dl = (id2, list) => `<datalist id="${id2}">${list.map((x) => `<option value="${esc(x)}">`).join('')}</datalist>`;
  const today = new Date().toISOString().slice(0, 10);
  main.innerHTML = topbar(isNew ? 'Add record' : 'Edit record', isNew ? null : p.name, { back: true }) + `
    <div class="page pad narrow" style="margin:0 auto"><form class="card" id="editForm">
      <label class="field"><span>Name *</span><input class="input" name="name" value="${esc(p.name)}" required></label>
      <div class="field"><span>Sex</span><div class="seg">
        <label><input type="radio" name="sex" value="M" ${p.sex === 'M' ? 'checked' : ''}><span>Male</span></label>
        <label><input type="radio" name="sex" value="F" ${p.sex === 'F' ? 'checked' : ''}><span>Female</span></label></div></div>
      <div class="row2">
        <label class="field"><span>FCN</span><input class="input" name="fcn" value="${esc(p.fcn)}" inputmode="numeric"></label>
        <label class="field"><span>Date of birth</span><input class="input" name="dob" type="date" max="${today}" value="${esc(p.dob)}"></label>
      </div>
      <label class="field"><span>Relation</span><input class="input" name="relation" list="dlRel" value="${esc(p.relation)}" style="text-transform:uppercase"></label>
      <div class="row2">
        <label class="field"><span>Main Block</span><input class="input" name="main_block" list="dlMain" value="${esc(p.main_block)}"></label>
        <label class="field"><span>Sub-Block</span><input class="input" name="sub_block" list="dlSub" value="${esc(p.sub_block)}"></label>
      </div>
      <label class="field"><span>Majhi</span><input class="input" name="majhi" list="dlMajhi" value="${esc(p.majhi)}"></label>
      <label class="field"><span>Remarks</span><textarea class="input" name="remarks">${esc(p.remarks)}</textarea></label>
      ${dl('dlRel', S.meta.relations)}${dl('dlMain', mainNames())}${dl('dlSub', subNames(p.main_block))}${dl('dlMajhi', S.meta.majhis)}
      <div class="err" id="editErr"></div>
      <button class="btn block" type="submit">${isNew ? 'Add record' : 'Save changes'}</button>
    </form></div>`;
  const f = $('#editForm', main);
  f.main_block.oninput = () => { $('#dlSub', main).innerHTML = subNames(f.main_block.value.trim()).map((x) => `<option value="${esc(x)}">`).join(''); };
  // tap a selected radio again to clear it
  $$('input[name=sex]', f).forEach((r) => { r.onclick = () => { if (r.dataset.was === '1') { r.checked = false; r.dataset.was = ''; } else { $$('input[name=sex]', f).forEach((x) => { x.dataset.was = ''; }); r.dataset.was = '1'; } }; if (r.checked) r.dataset.was = '1'; });
  f.onsubmit = async (e) => {
    e.preventDefault();
    const v = Object.fromEntries(new FormData(f));
    if (!v.name.trim()) { $('#editErr').textContent = 'Name is required.'; return; }
    const btn = $('button[type=submit]', f);
    busyBtn(btn, true);
    try {
      await api('save', { id: p.id || 0, name: v.name.trim(), sex: v.sex || '', fcn: v.fcn.trim(), dob: v.dob || '', relation: v.relation.trim().toUpperCase(), main_block: v.main_block.trim(), sub_block: v.sub_block.trim(), majhi: v.majhi.trim(), remarks: v.remarks.trim() });
      toast(isNew ? 'Record added.' : 'Changes saved.');
      S.preset = null;
      changed();
      back('records');
    } catch (ex) { $('#editErr').textContent = ex.message; busyBtn(btn, false); }
  };
}

// ---------------- MESSAGES ----------------
let msgOpenOnly = true;
async function pageMessages(main) {
  const admin = isAdmin();
  main.innerHTML = topbar('Messages', admin ? 'Requests from users' : 'Your requests to the Admin', { actions: `<button class="iconbtn" data-reload title="Refresh">${ic('Refresh')}</button>` }) + `
    <div class="page pad">
      ${admin ? `<div class="chips"><button class="chip ${msgOpenOnly ? 'on' : ''}" data-open="1">Open</button><button class="chip ${!msgOpenOnly ? 'on' : ''}" data-open="0">All</button></div>` : ''}
      <div id="msgList">${loading()}</div>
    </div>
    ${!admin ? `<button class="fab" data-new>${ic('Add')} New request</button>` : ''}`;
  $('[data-reload]', main).onclick = () => render();
  $$('[data-open]', main).forEach((c) => { c.onclick = () => { msgOpenOnly = c.dataset.open === '1'; render(); }; });
  const nb = $('[data-new]', main); if (nb) nb.onclick = () => composeMessage('', 0);
  let list;
  try {
    list = (admin ? await api('msg_all', { status: msgOpenOnly ? 'open' : 'all' }) : await api('msg_mine')).messages || [];
    loadStats();
  } catch (e) { $('#msgList').innerHTML = errorBox(e.message); $('[data-retry]').onclick = () => render(); return; }
  if (route().name !== 'messages') return;
  const box = $('#msgList');
  if (!list.length) {
    box.innerHTML = emptyBox('No messages', admin ? (msgOpenOnly ? 'There are no open requests.' : 'No requests yet.') : 'Tap "New request" to ask the Admin to add, correct or remove a record.');
    return;
  }
  const tag = (s) => s === 'open' ? '<span class="tag" style="--c:#C27410">Open</span>' : s === 'replied' ? '<span class="tag">Replied</span>' : '<span class="tag" style="--c:#777">Closed</span>';
  box.innerHTML = `<div class="stack cols">${list.map((m) => `
    <div class="card">
      <div style="display:flex;gap:8px;align-items:center"><b style="flex:1">${esc(m.subject || 'Request')}</b>${tag(m.status)}</div>
      <div class="muted" style="font-size:12px;margin:2px 0 8px">${admin ? esc(m.user_name) + ' · ' : ''}${esc(m.created_at)}</div>
      <div style="white-space:pre-line">${esc(m.body)}</div>
      ${m.beneficiary ? `<div style="margin-top:6px;color:var(--brand);font-weight:600;font-size:14px">Record: ${esc(m.beneficiary)}</div>` : ''}
      ${m.reply ? `<div class="reply"><small>Reply from ${esc(m.replied_by || 'Admin')} · ${esc(m.replied_at)}</small>${esc(m.reply)}</div>` : ''}
      ${(admin || m.beneficiary_id) ? `<div class="btnrow" style="margin-top:10px">
        ${m.beneficiary_id ? `<button class="btn outline small" data-rec="${m.beneficiary_id}">Open record</button>` : ''}
        ${admin ? `<button class="btn outline small" data-reply="${m.id}">${m.reply ? 'Edit reply' : 'Reply'}</button>${m.status !== 'closed' ? `<button class="btn ghost small" data-close="${m.id}">Close</button>` : ''}` : ''}
      </div>` : ''}
    </div>`).join('')}</div>`;
  $$('[data-rec]', box).forEach((b) => { b.onclick = () => go('record/' + b.dataset.rec); });
  $$('[data-reply]', box).forEach((b) => { b.onclick = () => replyMessage(list.find((m) => m.id === +b.dataset.reply)); });
  $$('[data-close]', box).forEach((b) => { b.onclick = async () => { try { await api('msg_close', { id: +b.dataset.close }); render(); } catch (e) { toast(e.message); } }; });
}
function composeMessage(subject, beneficiaryId) {
  modal(`<h3>Message to Admin</h3>
    <label class="field"><span>Subject</span><input class="input" id="mSub" value="${esc(subject)}"></label>
    <label class="field"><span>Message</span><textarea class="input" id="mBody" placeholder="What should the Admin add, correct or remove?"></textarea></label>
    <div class="err" id="mErr"></div>
    <div class="actions"><button class="btn ghost" data-cancel>Cancel</button><button class="btn" data-send>${ic('Send')} Send</button></div>`, (m, close) => {
    $('[data-cancel]', m).onclick = close;
    $('[data-send]', m).onclick = async (e) => {
      const body = $('#mBody', m).value.trim();
      if (!body) { $('#mErr', m).textContent = 'Please write your message.'; return; }
      busyBtn(e.currentTarget, true);
      try {
        const r = await api('msg_send', { subject: $('#mSub', m).value.trim(), body, beneficiary_id: beneficiaryId || 0 });
        close(); toast(r.message || 'Sent.');
        if (route().name === 'messages') render();
      } catch (ex) { $('#mErr', m).textContent = ex.message; busyBtn(e.currentTarget, false); }
    };
    setTimeout(() => $('#mBody', m).focus(), 50);
  });
}
function replyMessage(msg) {
  if (!msg) return;
  modal(`<h3>Reply to ${esc(msg.user_name)}</h3><p class="muted" style="white-space:pre-line">${esc(msg.body)}</p>
    <label class="field"><span>Your reply</span><textarea class="input" id="rBody">${esc(msg.reply)}</textarea></label>
    <div class="err" id="rErr"></div>
    <div class="actions"><button class="btn ghost" data-cancel>Cancel</button><button class="btn" data-send>${ic('Send')} Send reply</button></div>`, (m, close) => {
    $('[data-cancel]', m).onclick = close;
    $('[data-send]', m).onclick = async (e) => {
      const reply = $('#rBody', m).value.trim();
      if (!reply) { $('#rErr', m).textContent = 'Please write a reply.'; return; }
      busyBtn(e.currentTarget, true);
      try { await api('msg_reply', { id: msg.id, reply }); close(); toast('Reply sent.'); render(); }
      catch (ex) { $('#rErr', m).textContent = ex.message; busyBtn(e.currentTarget, false); }
    };
  });
}

// ---------------- REPORTS ----------------
let repMain = '';
const REP_COLS = [['families', 'Families'], ['male', 'Male'], ['female', 'Female'], ['total', 'Total'], ['u5', '0–5'], ['c6', '6–12'], ['c13', '13–17'], ['a18', '18–59'], ['e60', '60+']];
function reportLines(data) {
  // returns [{label1, label2, vals:{}, style}] with sub-totals per Main Block and a grand total
  const out = [];
  const keys = REP_COLS.map((c) => c[0]).concat(['nodob']);
  const add = (a, b) => keys.forEach((k) => { if (k !== 'families') a[k] = (a[k] || 0) + (b[k] || 0); });
  const grand = {};
  let cur = null, acc = null;
  const flush = () => { if (cur !== null) { acc.families = (data.families_by_main || {})[cur] || 0; out.push({ a: 'Main Block ' + (cur || '(none)'), b: 'Total', v: acc, style: 'sub' }); } };
  data.rows.forEach((r) => {
    if (r.main !== cur) { flush(); cur = r.main; acc = {}; }
    out.push({ a: r.main, b: r.sub || '(none)', v: r });
    add(acc, r); add(grand, r);
  });
  flush();
  grand.families = data.families_total || 0;
  if (data.rows.length) out.push({ a: 'Grand Total', b: '', v: grand, style: 'grand' });
  return out;
}
async function pageReports(main) {
  main.innerHTML = topbar('Reports', 'Block-wise population summary') + `
    <div class="page pad">
      <div class="toolbar" style="margin-bottom:12px">
        <select class="input" id="repMain" style="max-width:260px"><option value="">All Main Blocks</option>${mainNames().map((n) => `<option value="${esc(n)}" ${n === repMain ? 'selected' : ''}>Main Block ${esc(n)}</option>`).join('')}</select>
        <span style="flex:1"></span>
        <button class="btn outline small" data-r="print">${ic('Print')} Print</button>
        <button class="btn outline small" data-r="pdf">${ic('PictureAsPdf')} PDF</button>
        <button class="btn small" data-r="xlsx">${ic('Download')} Excel</button>
      </div>
      <div id="repBox">${loading()}</div>
    </div>`;
  $('#repMain', main).onchange = (e) => { repMain = e.target.value; render(); };
  let data;
  try { data = await api('report', repMain ? { main: repMain } : {}); } catch (e) { $('#repBox').innerHTML = errorBox(e.message); $('[data-retry]').onclick = () => render(); return; }
  if (route().name !== 'reports') return;
  const lines = reportLines(data);
  const hasNoDob = lines.some((l) => l.v.nodob);
  const cols = REP_COLS.concat(hasNoDob ? [['nodob', 'No DOB']] : []);
  const title = 'Camp- 8 West - Block-wise population summary';
  const subtitle = repMain ? 'Main Block ' + repMain : 'All Main Blocks';
  $('#repBox').innerHTML = lines.length ? `<div class="repwrap"><table class="rep"><thead><tr><th>Main Block</th><th>Sub-Block</th>${cols.map((c) => `<th>${c[1]}</th>`).join('')}</tr></thead><tbody>
    ${lines.map((l) => `<tr class="${l.style || ''}"><td>${esc(l.a)}</td><td>${esc(l.b)}</td>${cols.map((c) => `<td>${fmt(l.v[c[0]])}</td>`).join('')}</tr>`).join('')}
    </tbody></table></div><p class="muted" style="font-size:13px">Ages are counted from the date of birth${hasNoDob ? '; "No DOB" means the record has no date of birth' : ''}. A family split across sub-blocks is counted once in the totals.</p>`
    : emptyBox('No data', 'There are no records yet.');
  const tableRows = lines.map((l) => [l.a, l.b].concat(cols.map((c) => l.v[c[0]] || 0)));
  $$('[data-r]', main).forEach((b) => {
    b.onclick = async () => {
      if (!lines.length) { toast('Nothing to export.'); return; }
      const k = b.dataset.r;
      if (k === 'xlsx') {
        downloadBlob(XL.writeXlsx(['Main Block', 'Sub-Block'].concat(cols.map((c) => c[1])), tableRows, [18, 12].concat(cols.map(() => 10))), `FDMN_8W_Report_${stamp()}.xlsx`);
        toast('Excel report downloaded.');
      } else if (k === 'pdf') {
        const pm = progressModal('Export to PDF'); pm.set('Creating PDF…', null);
        try {
          const blob = await makeTablePdf(title, subtitle, [{ t: 'Main Block', w: 110 }, { t: 'Sub-Block', w: 70 }].concat(cols.map((c) => ({ t: c[1], w: 52, right: true }))),
            tableRows.map((r) => r.map((v, i) => (i > 1 ? fmt(v) : v))), lines.map((l) => l.style || ''));
          downloadBlob(blob, `FDMN_8W_Report_${stamp()}.pdf`); pm.close(); toast('PDF report downloaded.');
        } catch (e) { pm.close(); toast(e.message); }
      } else {
        const now = new Date().toLocaleString('en-GB', { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' });
        showPrint(`<div class="phead"><img src="assets/logo_full.png" alt=""><div><h2>${esc(title)}</h2><div class="meta">${esc(subtitle)} · Printed ${esc(now)}</div></div></div>
          <table><thead><tr><th>Main Block</th><th>Sub-Block</th>${cols.map((c) => `<th style="text-align:right">${c[1]}</th>`).join('')}</tr></thead><tbody>
          ${lines.map((l) => `<tr style="${l.style === 'grand' ? 'font-weight:900;background:#CDEBE1' : l.style === 'sub' ? 'font-weight:800;background:#EEF7F3' : ''}"><td>${esc(l.a)}</td><td>${esc(l.b)}</td>${cols.map((c) => `<td style="text-align:right">${fmt(l.v[c[0]])}</td>`).join('')}</tr>`).join('')}
          </tbody></table><div class="pfoot">Confidential — for official use only.</div>`);
      }
    };
  });
}

// ---------------- SETTINGS ----------------
function pageSettings(main) {
  const u = S.user;
  const item = (key, icon, tone, label) => `<div class="menuitem t-${tone}" data-item="${key}"><div class="tile">${ic(icon)}</div><span class="grow">${label}</span>${ic('KeyboardArrowRight')}</div>`;
  const ci = (S.settings && S.settings.camp_info) || {};
  const f = (k, label) => `<label class="field"><span>${label}</span><input class="input" name="${k}" value="${esc(ci[k] || '')}"></label>`;
  main.innerHTML = topbar('Settings', null) + `
    <div class="page pad"><div class="settings-grid">
      <div>
        <div class="wcard t-green">${wave()}<div class="in" style="display:flex;align-items:center;gap:14px;padding:16px">
          <div class="tile" style="border-radius:50%;width:56px;height:56px">${esc((u.name || '?').slice(0, 1).toUpperCase())}</div>
          <div style="flex:1;min-width:0"><div style="font-size:18px;font-weight:800">${esc(u.name)}</div><div class="muted" style="font-size:13px">${esc(u.email)}</div>
            <span class="tag" style="margin-top:6px;${isAdmin() ? '--c:#C27410' : ''}">${isAdmin() ? '👑 Admin' : 'Viewer'}</span></div>
        </div></div>
        <div class="card menu" style="margin-top:12px">
          ${isAdmin() ? item('users', 'PeopleOutline', 'green', 'Manage users') + item('import', 'UploadFile', 'green', 'Import from Excel') : ''}
          ${item('password', 'Lock', 'green', 'Change password')}
          ${item('refresh', 'Refresh', 'green', 'Refresh data')}
          ${item('about', 'Info', 'green', 'About this app')}
          ${item('signout', 'Logout', 'red', '<span style="color:var(--danger)">Sign out</span>')}
        </div>
      </div>
      ${isAdmin() ? `<div>
        <section class="panel"><div class="ph">${ic('Info')}<h3>Camp Information</h3></div><div class="pb">
          <form id="setCi"><div class="row2">${f('area', 'Camp Area')}${f('organisations', 'Total Organisations')}</div>
            <div class="row2">${f('un_agencies', 'UN Agencies')}${f('ngos', "NGO's & INGO's")}</div>
            <div class="row2">${f('projects', 'Total Projects')}${f('sectors', 'Total Sectors')}</div>
            <div class="err" id="setCiErr"></div><button class="btn" type="submit">${ic('Save')} Save Camp Information</button></form></div></section>
        <section class="panel" style="margin-top:14px"><div class="ph">${ic('ContactPhone')}<h3>Emergency Contacts</h3></div><div class="pb">
          <div id="setC">${((S.settings && S.settings.contacts) || []).map(contactRow).join('')}</div>
          <button class="btn ghost small" data-addc>${ic('Add')} Add contact</button>
          <div class="err" id="setCErr"></div><button class="btn" data-savec>${ic('Save')} Save contacts</button></div></section>
      </div>` : ''}
    </div></div>`;
  $$('[data-item]', main).forEach((el) => {
    el.onclick = async () => {
      const k = el.dataset.item;
      if (k === 'users' || k === 'import' || k === 'password') go(k);
      else if (k === 'refresh') { changed(); loadSettings(); toast('Refreshed.'); }
      else if (k === 'about') modal(`<h3>Camp- 8 West</h3><p>FDMN Database · Web version ${APP_VERSION}</p><p class="muted">Beneficiary records for Camp-8W, organised by Main Block and Sub-Block. Data is stored securely on app.informationplant.com and is only visible to approved users. The same data is used by the Android app.</p><div class="actions"><button class="btn" data-ok>OK</button></div>`, (m, close) => { $('[data-ok]', m).onclick = close; });
      else if (k === 'signout') { if (await confirmBox('Sign out?', 'You will need your email and password to sign in again.', 'Sign out')) signOut(); }
    };
  });
  const form = $('#setCi', main);
  if (form) {
    form.onsubmit = async (e) => {
      e.preventDefault();
      const btn = $('button[type=submit]', form); busyBtn(btn, true);
      try { const r = await api('settings_save', { key: 'camp_info', value: Object.fromEntries(new FormData(form)) }); S.settings = Object.assign({}, S.settings, { camp_info: r.value }); toast('Camp Information saved.'); }
      catch (ex) { $('#setCiErr').textContent = ex.message; }
      busyBtn(btn, false);
    };
    const box = $('#setC', main);
    bindContactEditor(box);
    $('[data-addc]', main).onclick = () => box.insertAdjacentHTML('beforeend', contactRow({ type: 'other' }));
    $('[data-savec]', main).onclick = async (e) => {
      const btn = e.currentTarget; busyBtn(btn, true);
      try { const r = await api('settings_save', { key: 'contacts', value: readContacts(box) }); S.settings = Object.assign({}, S.settings, { contacts: r.value }); toast('Emergency contacts saved.'); }
      catch (ex) { $('#setCErr').textContent = ex.message; }
      busyBtn(btn, false);
    };
  }
}

// ---------------- USERS (admin) ----------------
let userFilter = '';
async function pageUsers(main) {
  if (!isAdmin()) { go('settings'); return; }
  main.innerHTML = topbar('Manage users', '', { back: true }) + `<div class="page pad"><div id="uChips" class="chips"></div><div id="uList">${loading()}</div></div>`;
  let list;
  try { list = (await api('users')).users || []; loadStats(); } catch (e) { $('#uList').innerHTML = errorBox(e.message); $('[data-retry]').onclick = () => render(); return; }
  if (route().name !== 'users') return;
  if (!userFilter) userFilter = list.some((u) => u.status === 'pending') ? 'pending' : 'active';
  $('#topSub').textContent = list.length + ' users';
  const draw = () => {
    const cnt = (k) => (k === 'all' ? list.length : list.filter((u) => u.status === k).length);
    $('#uChips').innerHTML = [['pending', 'Waiting'], ['active', 'Active'], ['blocked', 'Blocked'], ['all', 'All']]
      .map(([k, l]) => `<button class="chip ${userFilter === k ? 'on' : ''}" data-uf="${k}">${l} (${cnt(k)})</button>`).join('');
    $$('[data-uf]').forEach((c) => { c.onclick = () => { userFilter = c.dataset.uf; draw(); }; });
    const shown = userFilter === 'all' ? list : list.filter((u) => u.status === userFilter);
    const box = $('#uList');
    if (!shown.length) { box.innerHTML = emptyBox('Nobody here', userFilter === 'pending' ? 'No new sign-ups are waiting for approval.' : 'No users in this list.'); return; }
    const me = S.user.id;
    const stTag = (s) => s === 'pending' ? '<span class="tag" style="--c:#D32F2F">Waiting</span>' : s === 'blocked' ? '<span class="tag" style="--c:#777">Blocked</span>' : '<span class="tag" style="--c:#2E7D32">Active</span>';
    box.innerHTML = `<div class="stack cols">${shown.map((u) => `
      <div class="card"><div style="display:flex;gap:10px;align-items:flex-start">
        <div style="flex:1;min-width:0"><b>${esc(u.name)}${u.id === me ? ' (You)' : ''}</b><div class="muted" style="font-size:13px;word-break:break-all">${esc(u.email)}</div>
          <div style="display:flex;gap:6px;margin-top:6px">${u.role === 'admin' ? '<span class="tag" style="--c:#C27410">Admin</span>' : '<span class="tag">Viewer</span>'}${stTag(u.status)}</div>
          <div class="muted" style="font-size:11.5px;margin-top:5px">Joined ${esc((u.created_at || '').slice(0, 10))}${u.last_login ? ' · Last sign-in ' + esc(u.last_login.slice(0, 16)) : ''}</div></div>
        ${u.id !== me ? `<div class="dropdown"><button class="iconbtn" style="color:var(--muted)" data-menu="${u.id}">${ic('MoreVert')}</button></div>` : ''}
      </div>
      ${u.status === 'pending' ? `<div class="btnrow" style="margin-top:10px"><button class="btn small" data-act="approve" data-u="${u.id}">Approve</button><button class="btn ghost small" style="color:var(--danger)" data-act="delete" data-u="${u.id}">Reject</button></div>` : ''}
      </div>`).join('')}</div>`;
    $$('[data-act]', box).forEach((b) => { b.onclick = () => userAction(b.dataset.act, list.find((u) => u.id === +b.dataset.u)); });
    $$('[data-menu]', box).forEach((b) => {
      b.onclick = (e) => {
        e.stopPropagation();
        $$('.dropdown .list').forEach((l) => l.remove());
        const u = list.find((x) => x.id === +b.dataset.menu);
        const opts = [];
        if (u.status === 'blocked') opts.push(['unblock', 'Unblock']); else if (u.status === 'active') opts.push(['block', 'Block']);
        opts.push(u.role === 'admin' ? ['viewer', 'Make Viewer'] : ['admin', 'Make Admin']);
        opts.push(['reset', 'Reset password'], ['delete', 'Delete user']);
        const l = document.createElement('div'); l.className = 'list';
        l.innerHTML = opts.map(([k, t]) => `<button class="${k === 'delete' ? 'danger' : ''}" data-o="${k}">${t}</button>`).join('');
        b.parentElement.appendChild(l);
        $$('[data-o]', l).forEach((o) => { o.onclick = () => { l.remove(); userAction(o.dataset.o, u); }; });
        setTimeout(() => document.addEventListener('click', () => l.remove(), { once: true }), 0);
      };
    });
  };
  draw();
}
async function userAction(act, u) {
  if (!u) return;
  const set = async (body, msg) => { try { await api('user_set', Object.assign({ id: u.id }, body)); toast(msg); render(); } catch (e) { toast(e.message); } };
  if (act === 'approve') set({ status: 'active' }, `${u.name} approved.`);
  else if (act === 'unblock') set({ status: 'active' }, `${u.name} unblocked.`);
  else if (act === 'block') { if (await confirmBox('Please confirm', `Block ${u.name}? They will not be able to sign in.`, 'Block')) set({ status: 'blocked' }, `${u.name} blocked.`); }
  else if (act === 'admin') { if (await confirmBox('Please confirm', `Make ${u.name} an Admin? Admins can add, edit and delete all records and manage users.`, 'Make Admin')) set({ role: 'admin' }, `${u.name} is now an Admin.`); }
  else if (act === 'viewer') { if (await confirmBox('Please confirm', `Change ${u.name} to Viewer? They will only be able to view records.`, 'Make Viewer')) set({ role: 'viewer' }, `${u.name} is now a Viewer.`); }
  else if (act === 'delete') {
    if (await confirmBox('Please confirm', `Delete ${u.name}'s account permanently?`, 'Delete', true)) {
      try { await api('user_delete', { id: u.id }); toast('User deleted.'); render(); } catch (e) { toast(e.message); }
    }
  } else if (act === 'reset') {
    modal(`<h3>Reset password</h3><p>Set a new password for ${esc(u.name)}. Tell them the new password so they can sign in.</p>
      <label class="field"><span>New password (min 6)</span><input class="input" id="npw" type="text" autocomplete="off"></label><div class="err" id="npwErr"></div>
      <div class="actions"><button class="btn ghost" data-cancel>Cancel</button><button class="btn" data-save>Save</button></div>`, (m, close) => {
      $('[data-cancel]', m).onclick = close;
      $('[data-save]', m).onclick = () => {
        const pw = $('#npw', m).value;
        if (pw.length < 6) { $('#npwErr', m).textContent = 'Password must be at least 6 characters.'; return; }
        close(); set({ password: pw }, `Password changed for ${u.name}.`);
      };
    });
  }
}

// ---------------- IMPORT (admin) ----------------
function pageImport(main) {
  if (!isAdmin()) { go('settings'); return; }
  const st = { parsed: null, sent: 0, busy: false, done: false, replace: (S.stats || {}).total > 0 };
  main.innerHTML = topbar('Import from Excel', null, { back: true }) + `
    <div class="page pad narrow" style="margin:0 auto">
      <div class="card"><b>How it works</b><p style="margin:6px 0 0;font-size:14px;line-height:1.6">
        1. Choose your Excel file (.xlsx) or a .csv file.<br>
        2. The app finds the heading row (SL, Name, Sex, FCN, Date of birth, Relation, Main Block, Sub-Block, Majhi, Remarks). All sheets with this heading row are read.<br>
        3. Check the summary, then tap Upload.<br><br>
        Main Block and Sub-Block lists are created automatically. SL numbers are given automatically.</p></div>
      <label class="btn outline block" style="margin-top:12px">${ic('UploadFile')} <span id="pickLabel">Choose Excel file</span>
        <input type="file" id="file" accept=".xlsx,.csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/csv" hidden></label>
      <div id="imp"></div>
    </div>`;
  const box = $('#imp', main);
  const draw = () => {
    const p = st.parsed;
    if (!p) return;
    const fam = new Set(p.rows.map((r) => r.fcn).filter(Boolean)).size;
    const kv = (k, v, warn) => `<span>${k}</span><b class="${warn ? 'warn' : ''}">${esc(v)}</b>`;
    box.innerHTML = `
      <div class="card" style="margin-top:12px"><b>File summary</b><div class="kv" style="margin-top:8px">
        ${kv('Rows found', fmt(p.rows.length))}${kv('Families (FCN)', fmt(fam))}${kv('Sheets read', p.sheetsUsed.join(', '))}
        ${p.sheetsSkipped.length ? kv('Sheets skipped', p.sheetsSkipped.join(', ')) : ''}${kv('Columns matched', p.columnsFound.join(', '))}
        ${p.columnsMissing.length ? kv('Columns not found', p.columnsMissing.join(', '), true) : ''}
        ${p.emptyRowsSkipped ? kv('Empty rows skipped', fmt(p.emptyRowsSkipped)) : ''}
        ${p.badDates ? kv('Unclear dates', `${fmt(p.badDates)} (left empty; the original text was put in Remarks)`, true) : ''}
      </div></div>
      ${p.rows.length ? `
      <div class="card" style="margin-top:12px"><b>First rows (preview)</b>
        ${p.rows.slice(0, 6).map((r, i) => `<div style="font-size:13px;padding:6px 0;border-bottom:1px solid #EEF3F1">${i + 1}. ${esc(r.name)} · ${esc(r.sex)} · FCN ${esc(r.fcn)} · ${esc(XL.displayDate(r.dob) || 'no DOB')} · ${esc(r.relation)} · ${esc(r.main_block)}/${esc(r.sub_block)} · ${esc(r.majhi)}</div>`).join('')}
      </div>
      <label class="check" style="margin:14px 2px"><input type="checkbox" id="rep" ${st.replace ? 'checked' : ''} ${st.sent || st.busy ? 'disabled' : ''}>
        <span><b>Delete all existing records first</b><br><small class="muted">${(S.stats || {}).total > 0 ? `There are ${fmt(S.stats.total)} records now. Tick this to replace them, or untick to add these rows to them.` : 'The database is empty.'}</small></span></label>
      ${st.busy || st.sent ? `<div>Uploaded ${fmt(st.sent)} of ${fmt(p.rows.length)}</div><div class="progress"><div style="width:${Math.round(st.sent / p.rows.length * 100)}%"></div></div>` : ''}
      ${st.done ? `<div class="note" style="background:#CDEBE1;padding:12px;border-radius:12px;font-weight:600;margin:10px 0">Import finished: ${fmt(st.sent)} records uploaded.</div><button class="btn block" data-view>View records</button>`
        : `<button class="btn block" data-up ${st.busy ? 'disabled' : ''}>${st.busy ? 'Uploading…' : st.sent ? 'Continue upload' : `Upload ${fmt(p.rows.length)} records`}</button>`}` : ''}
      <div class="err" id="impErr">${esc(st.error || '')}</div>`;
    const rep = $('#rep', box); if (rep) rep.onchange = () => { st.replace = rep.checked; };
    const vw = $('[data-view]', box); if (vw) vw.onclick = () => openRecords({});
    const up = $('[data-up]', box);
    if (up) up.onclick = async () => {
      if (st.replace && !st.sent && (S.stats || {}).total > 0 && !(await confirmBox('Replace all records?', `All ${fmt(S.stats.total)} existing records will be deleted and replaced with the ${fmt(p.rows.length)} rows from this file. This cannot be undone.`, 'Replace', true))) return;
      st.busy = true; st.error = ''; draw();
      try {
        while (st.sent < p.rows.length) {
          const part = p.rows.slice(st.sent, st.sent + 1000);
          await api('import', { rows: part, replace: st.replace && st.sent === 0 });
          st.sent += part.length;
          draw();
        }
        st.done = true;
        changed();
      } catch (e) {
        st.error = `${e.message}\n${fmt(st.sent)} of ${fmt(p.rows.length)} rows were uploaded. Tap Continue to upload the rest.`;
      } finally { st.busy = false; draw(); }
    };
  };
  $('#file', main).onchange = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    Object.assign(st, { parsed: null, sent: 0, done: false, error: '' });
    box.innerHTML = `<div class="card" style="margin-top:12px">Reading ${esc(file.name)}…<div class="progress indet"><div></div></div></div>`;
    try {
      st.parsed = await XL.readFile(await file.arrayBuffer());
      $('#pickLabel').textContent = 'Choose a different file';
      draw();
    } catch (ex) { box.innerHTML = `<p class="err">${esc(ex.message || 'Could not read this file.')}</p>`; }
    e.target.value = '';
  };
}

// ---------------- CHANGE PASSWORD ----------------
function pagePassword(main) {
  main.innerHTML = topbar('Change password', null, { back: true }) + `
    <div class="page pad narrow" style="margin:0 auto"><form class="card" id="pwForm">
      <label class="field"><span>Current password</span><input class="input" type="password" name="old" autocomplete="current-password" required></label>
      <label class="field"><span>New password (min 6)</span><input class="input" type="password" name="n1" autocomplete="new-password" required></label>
      <label class="field"><span>Confirm new password</span><input class="input" type="password" name="n2" autocomplete="new-password" required></label>
      <div class="err" id="pwErr"></div>
      <button class="btn block" type="submit">Change password</button>
    </form></div>`;
  const f = $('#pwForm', main);
  f.onsubmit = async (e) => {
    e.preventDefault();
    const err = $('#pwErr');
    if (f.n1.value.length < 6) { err.textContent = 'New password must be at least 6 characters.'; return; }
    if (f.n1.value !== f.n2.value) { err.textContent = 'The two new passwords do not match.'; return; }
    const btn = $('button[type=submit]', f);
    busyBtn(btn, true);
    try { await api('change_password', { old: f.old.value, new: f.n1.value }); toast('Password changed.'); back('settings'); }
    catch (ex) { err.textContent = ex.message; busyBtn(btn, false); }
  };
}

// ---------------- start ----------------
(function start() {
  const splash = document.createElement('div');
  splash.className = 'splash';
  splash.innerHTML = '<img src="assets/logo_full.png" alt="Office of the Camp-in-Charge, Camp-8 West"><b>Camp- 8 West</b><span class="spinner"></span>';
  document.body.appendChild(splash);
  const ready = S.token && S.user ? Promise.all([loadMeta(), loadStats(), loadSettings()]) : Promise.resolve();
  Promise.all([ready, new Promise((r) => setTimeout(r, 1200))]).then(() => {
    splash.remove();
    render();
    if (S.token && S.user) {
      showWelcome();
      api('me').then((r) => { S.user = r.user; store.set('fdmn_user', JSON.stringify(r.user)); updateNav(); }).catch(() => {});
    }
  });
})();
