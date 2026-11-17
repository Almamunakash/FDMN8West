<?php
// 8 West FDMN Database - shared helpers. Not opened directly.
if (!defined('FDMN')) { http_response_code(403); exit('Forbidden'); }

const API_VERSION = '1.2';
date_default_timezone_set('Asia/Dhaka');

if (!function_exists('mb_substr')) {
    function mb_substr($s, $start, $len = null) {
        if (preg_match_all('/./us', $s, $m)) {
            return implode('', array_slice($m[0], $start, $len));
        }
        return $len === null ? substr($s, $start) : substr($s, $start, $len);
    }
}

function db() {
    static $pdo = null;
    if ($pdo) return $pdo;
    if (DB_DRIVER === 'sqlite') {
        $pdo = new PDO('sqlite:' . DB_PATH);
    } else {
        $pdo = new PDO('mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=utf8mb4', DB_USER, DB_PASS);
    }
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $pdo->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
    return $pdo;
}

function is_sqlite() { return DB_DRIVER === 'sqlite'; }
function now() { return date('Y-m-d H:i:s'); }
function insert_ignore() { return is_sqlite() ? 'INSERT OR IGNORE' : 'INSERT IGNORE'; }

function schema_sql($driver) {
    if ($driver === 'sqlite') {
        return [
            "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE, pass_hash TEXT NOT NULL, role TEXT NOT NULL DEFAULT 'viewer', status TEXT NOT NULL DEFAULT 'pending', created_at TEXT NOT NULL, last_login TEXT NULL)",
            "CREATE TABLE IF NOT EXISTS sessions (token TEXT PRIMARY KEY, user_id INTEGER NOT NULL, created_at TEXT NOT NULL, last_used TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_sess_user ON sessions(user_id)",
            "CREATE TABLE IF NOT EXISTS beneficiaries (id INTEGER PRIMARY KEY AUTOINCREMENT, sl INTEGER NOT NULL DEFAULT 0, name TEXT NOT NULL, sex TEXT NOT NULL DEFAULT '', fcn TEXT NOT NULL DEFAULT '', dob TEXT NULL, relation TEXT NOT NULL DEFAULT '', main_block TEXT NOT NULL DEFAULT '', sub_block TEXT NOT NULL DEFAULT '', majhi TEXT NOT NULL DEFAULT '', remarks TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL, updated_at TEXT NOT NULL, updated_by INTEGER NULL)",
            "CREATE INDEX IF NOT EXISTS idx_b_sl ON beneficiaries(sl)",
            "CREATE INDEX IF NOT EXISTS idx_b_fcn ON beneficiaries(fcn)",
            "CREATE INDEX IF NOT EXISTS idx_b_name ON beneficiaries(name)",
            "CREATE INDEX IF NOT EXISTS idx_b_block ON beneficiaries(main_block, sub_block)",
            "CREATE INDEX IF NOT EXISTS idx_b_majhi ON beneficiaries(majhi)",
            "CREATE TABLE IF NOT EXISTS blocks (id INTEGER PRIMARY KEY AUTOINCREMENT, main_block TEXT NOT NULL, sub_block TEXT NOT NULL, UNIQUE(main_block, sub_block))",
            "CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, beneficiary_id INTEGER NULL, subject TEXT NOT NULL DEFAULT '', body TEXT NOT NULL, reply TEXT NULL, status TEXT NOT NULL DEFAULT 'open', created_at TEXT NOT NULL, replied_at TEXT NULL, replied_by INTEGER NULL, user_seen INTEGER NOT NULL DEFAULT 1)",
            "CREATE INDEX IF NOT EXISTS idx_m_user ON messages(user_id)",
        ];
    }
    $e = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    return [
        "CREATE TABLE IF NOT EXISTS users (
            id INT AUTO_INCREMENT PRIMARY KEY,
            name VARCHAR(100) NOT NULL,
            email VARCHAR(150) NOT NULL,
            pass_hash VARCHAR(255) NOT NULL,
            role VARCHAR(10) NOT NULL DEFAULT 'viewer',
            status VARCHAR(10) NOT NULL DEFAULT 'pending',
            created_at DATETIME NOT NULL,
            last_login DATETIME NULL,
            UNIQUE KEY uq_email (email)
        )$e",
        "CREATE TABLE IF NOT EXISTS sessions (
            token CHAR(64) NOT NULL PRIMARY KEY,
            user_id INT NOT NULL,
            created_at DATETIME NOT NULL,
            last_used DATETIME NOT NULL,
            KEY idx_sess_user (user_id)
        )$e",
        "CREATE TABLE IF NOT EXISTS beneficiaries (
            id INT AUTO_INCREMENT PRIMARY KEY,
            sl INT NOT NULL DEFAULT 0,
            name VARCHAR(150) NOT NULL,
            sex VARCHAR(10) NOT NULL DEFAULT '',
            fcn VARCHAR(30) NOT NULL DEFAULT '',
            dob DATE NULL,
            relation VARCHAR(30) NOT NULL DEFAULT '',
            main_block VARCHAR(30) NOT NULL DEFAULT '',
            sub_block VARCHAR(30) NOT NULL DEFAULT '',
            majhi VARCHAR(150) NOT NULL DEFAULT '',
            remarks VARCHAR(500) NOT NULL DEFAULT '',
            created_at DATETIME NOT NULL,
            updated_at DATETIME NOT NULL,
            updated_by INT NULL,
            KEY idx_b_sl (sl),
            KEY idx_b_fcn (fcn),
            KEY idx_b_name (name),
            KEY idx_b_block (main_block, sub_block),
            KEY idx_b_majhi (majhi)
        )$e",
        "CREATE TABLE IF NOT EXISTS blocks (
            id INT AUTO_INCREMENT PRIMARY KEY,
            main_block VARCHAR(30) NOT NULL,
            sub_block VARCHAR(30) NOT NULL,
            UNIQUE KEY uq_block (main_block, sub_block)
        )$e",
        "CREATE TABLE IF NOT EXISTS messages (
            id INT AUTO_INCREMENT PRIMARY KEY,
            user_id INT NOT NULL,
            beneficiary_id INT NULL,
            subject VARCHAR(150) NOT NULL DEFAULT '',
            body TEXT NOT NULL,
            reply TEXT NULL,
            status VARCHAR(10) NOT NULL DEFAULT 'open',
            created_at DATETIME NOT NULL,
            replied_at DATETIME NULL,
            replied_by INT NULL,
            user_seen TINYINT NOT NULL DEFAULT 1,
            KEY idx_m_user (user_id),
            KEY idx_m_status (status)
        )$e",
    ];
}

// ---------- request / response ----------
function send_json($arr) {
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode($arr, JSON_UNESCAPED_UNICODE);
    exit;
}
function out($data = []) { send_json(array_merge(['ok' => true], $data)); }
function fail($msg, $code = 'error') { send_json(['ok' => false, 'error' => $msg, 'code' => $code]); }

function input() {
    static $in = null;
    if ($in === null) {
        $raw = file_get_contents('php://input');
        $in = json_decode($raw ?: '', true);
        if (!is_array($in)) $in = $_POST;
    }
    return $in;
}
function p($k, $def = '') {
    $in = input();
    if (!array_key_exists($k, $in) || $in[$k] === null) return $def;
    return is_string($in[$k]) ? trim($in[$k]) : $in[$k];
}
function s($k, $max = 255) {
    $v = p($k, '');
    if (!is_string($v)) $v = is_scalar($v) ? (string)$v : '';
    $v = trim(preg_replace('/\s+/u', ' ', $v));
    return mb_substr($v, 0, $max);
}

// ---------- auth ----------
function user_public($u) {
    return [
        'id' => (int)$u['id'], 'name' => $u['name'], 'email' => $u['email'],
        'role' => $u['role'], 'status' => $u['status'],
        'created_at' => $u['created_at'], 'last_login' => $u['last_login'],
    ];
}

function new_session($userId) {
    $token = bin2hex(random_bytes(32));
    $st = db()->prepare("INSERT INTO sessions (token, user_id, created_at, last_used) VALUES (?, ?, ?, ?)");
    $st->execute([hash('sha256', $token), $userId, now(), now()]);
    return $token;
}

function request_token() {
    if (!empty($_SERVER['HTTP_X_AUTH_TOKEN'])) return $_SERVER['HTTP_X_AUTH_TOKEN'];
    $t = p('token', '');
    return is_string($t) ? $t : '';
}

function current_user() {
    static $user = null;
    if ($user) return $user;
    $token = request_token();
    if ($token === '') fail('Please sign in.', 'auth');
    $hash = hash('sha256', $token);
    $st = db()->prepare("SELECT u.*, s.last_used AS s_used FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token = ?");
    $st->execute([$hash]);
    $u = $st->fetch();
    if (!$u) fail('Your session has ended. Please sign in again.', 'auth');
    if (strtotime($u['s_used']) < time() - 90 * 86400) {
        db()->prepare("DELETE FROM sessions WHERE token = ?")->execute([$hash]);
        fail('Your session has ended. Please sign in again.', 'auth');
    }
    if ($u['status'] !== 'active') {
        db()->prepare("DELETE FROM sessions WHERE user_id = ?")->execute([$u['id']]);
        fail($u['status'] === 'blocked' ? 'Your account has been blocked. Contact the Admin.' : 'Your account is waiting for Admin approval.', 'auth');
    }
    if (strtotime($u['s_used']) < time() - 600) {
        db()->prepare("UPDATE sessions SET last_used = ? WHERE token = ?")->execute([now(), $hash]);
    }
    $u['token_hash'] = $hash;
    $user = $u;
    return $user;
}

function require_admin() {
    $u = current_user();
    if ($u['role'] !== 'admin') fail('Only an Admin can do this.', 'forbidden');
    return $u;
}

// ---------- beneficiaries ----------
const B_FIELDS = 'id, sl, name, sex, fcn, dob, relation, main_block, sub_block, majhi, remarks';

function clean_row($r) {
    return [
        'id' => (int)$r['id'], 'sl' => (int)$r['sl'], 'name' => $r['name'], 'sex' => $r['sex'],
        'fcn' => $r['fcn'], 'dob' => $r['dob'] ? substr($r['dob'], 0, 10) : '',
        'relation' => $r['relation'], 'main_block' => $r['main_block'], 'sub_block' => $r['sub_block'],
        'majhi' => $r['majhi'], 'remarks' => $r['remarks'],
    ];
}

function valid_date($d) {
    if (!is_string($d) || $d === '') return null;
    $dt = DateTime::createFromFormat('!Y-m-d', $d);
    if (!$dt || $dt->format('Y-m-d') !== $d) return null;
    $y = (int)$dt->format('Y');
    if ($y < 1880 || $y > (int)date('Y') + 1) return null;
    return $d;
}

function norm_sex($v) {
    $v = strtoupper(trim((string)$v));
    if ($v === 'FEMALE') return 'F';
    if ($v === 'MALE') return 'M';
    return mb_substr($v, 0, 10);
}

function add_block($main, $sub) {
    if ($main === '' && $sub === '') return;
    db()->prepare(insert_ignore() . " INTO blocks (main_block, sub_block) VALUES (?, ?)")->execute([$main, $sub]);
}
