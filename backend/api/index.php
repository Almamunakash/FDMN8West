<?php
// 8 West FDMN Database - API for the Android app.
define('FDMN', 1);
require __DIR__ . '/lib.php';

if (!file_exists(__DIR__ . '/config.php')) fail('The server is not set up yet. Open install.php first.', 'setup');
require __DIR__ . '/config.php';

set_exception_handler(function ($e) {
    error_log('FDMN API: ' . $e->getMessage());
    fail('Server error. Please try again.', 'server');
});

$action = isset($_GET['a']) ? $_GET['a'] : p('a', '');
$actions = [
    'ping', 'register', 'login', 'logout', 'me', 'change_password',
    'stats', 'blocks', 'options', 'list', 'family', 'get',
    'save', 'delete', 'import',
    'users', 'user_set', 'user_delete',
    'msg_send', 'msg_mine', 'msg_all', 'msg_reply', 'msg_close',
];

// ================= public =================

function act_ping() {
    out(['app' => '8 West FDMN Database', 'version' => API_VERSION, 'time' => now()]);
}

function act_register() {
    $name = s('name', 100);
    $email = strtolower(s('email', 150));
    $pass = (string)p('password', '');
    if (mb_strlen_safe($name) < 2) fail('Please enter your full name.');
    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) fail('Please enter a valid email address.');
    if (strlen($pass) < 6) fail('Password must be at least 6 characters.');
    $st = db()->prepare("SELECT id FROM users WHERE email = ?");
    $st->execute([$email]);
    if ($st->fetch()) fail('This email is already registered. Please sign in.');
    db()->prepare("INSERT INTO users (name, email, pass_hash, role, status, created_at) VALUES (?, ?, ?, 'viewer', 'pending', ?)")
        ->execute([$name, $email, password_hash($pass, PASSWORD_DEFAULT), now()]);
    out(['message' => 'Account created. An Admin must approve it before you can sign in.']);
}

function act_login() {
    $email = strtolower(s('email', 150));
    $pass = (string)p('password', '');
    $st = db()->prepare("SELECT * FROM users WHERE email = ?");
    $st->execute([$email]);
    $u = $st->fetch();
    if (!$u || !password_verify($pass, $u['pass_hash'])) {
        sleep(1);
        fail('Wrong email or password.');
    }
    if ($u['status'] === 'pending') fail('Your account is waiting for Admin approval.', 'pending');
    if ($u['status'] === 'blocked') fail('Your account has been blocked. Contact the Admin.', 'blocked');
    $token = new_session($u['id']);
    db()->prepare("UPDATE users SET last_login = ? WHERE id = ?")->execute([now(), $u['id']]);
    // tidy old sessions now and then
    if (mt_rand(1, 20) === 1) {
        db()->prepare("DELETE FROM sessions WHERE last_used < ?")->execute([date('Y-m-d H:i:s', time() - 90 * 86400)]);
    }
    $u['last_login'] = now();
    out(['token' => $token, 'user' => user_public($u)]);
}

function mb_strlen_safe($s) {
    return function_exists('mb_strlen') ? mb_strlen($s) : strlen($s);
}

// ================= signed-in =================

function act_logout() {
    $u = current_user();
    db()->prepare("DELETE FROM sessions WHERE token = ?")->execute([$u['token_hash']]);
    out();
}

function act_me() {
    out(['user' => user_public(current_user())]);
}

function act_change_password() {
    $u = current_user();
    $old = (string)p('old', '');
    $new = (string)p('new', '');
    if (!password_verify($old, $u['pass_hash'])) fail('Your current password is wrong.');
    if (strlen($new) < 6) fail('New password must be at least 6 characters.');
    db()->prepare("UPDATE users SET pass_hash = ? WHERE id = ?")->execute([password_hash($new, PASSWORD_DEFAULT), $u['id']]);
    db()->prepare("DELETE FROM sessions WHERE user_id = ? AND token <> ?")->execute([$u['id'], $u['token_hash']]);
    out(['message' => 'Password changed.']);
}

function act_stats() {
    $u = current_user();
    $r = db()->query("SELECT COUNT(*) AS total, COUNT(DISTINCT NULLIF(fcn, '')) AS families,
        SUM(CASE WHEN sex = 'M' THEN 1 ELSE 0 END) AS male,
        SUM(CASE WHEN sex = 'F' THEN 1 ELSE 0 END) AS female FROM beneficiaries")->fetch();
    $res = [
        'total' => (int)$r['total'], 'families' => (int)$r['families'],
        'male' => (int)$r['male'], 'female' => (int)$r['female'],
        'pending_users' => 0, 'open_messages' => 0, 'unread_replies' => 0,
    ];
    if ($u['role'] === 'admin') {
        $res['pending_users'] = (int)db()->query("SELECT COUNT(*) FROM users WHERE status = 'pending'")->fetchColumn();
        $res['open_messages'] = (int)db()->query("SELECT COUNT(*) FROM messages WHERE status = 'open'")->fetchColumn();
    }
    $st = db()->prepare("SELECT COUNT(*) FROM messages WHERE user_id = ? AND user_seen = 0");
    $st->execute([$u['id']]);
    $res['unread_replies'] = (int)$st->fetchColumn();
    out(['stats' => $res]);
}

function act_blocks() {
    current_user();
    $map = [];
    foreach (db()->query("SELECT main_block, sub_block FROM blocks") as $b) {
        $map[$b['main_block']][$b['sub_block']] = ['count' => 0, 'families' => 0];
    }
    $q = db()->query("SELECT main_block, sub_block, COUNT(*) AS c, COUNT(DISTINCT NULLIF(fcn, '')) AS f
        FROM beneficiaries GROUP BY main_block, sub_block");
    foreach ($q as $r) {
        $map[$r['main_block']][$r['sub_block']] = ['count' => (int)$r['c'], 'families' => (int)$r['f']];
    }
    $mains = [];
    foreach ($map as $main => $subs) {
        $list = [];
        $count = 0;
        foreach ($subs as $sub => $v) {
            $list[] = ['name' => (string)$sub, 'count' => $v['count'], 'families' => $v['families']];
            $count += $v['count'];
        }
        usort($list, function ($a, $b) { return strnatcasecmp($a['name'], $b['name']); });
        $families = 0;
        foreach ($list as $x) $families += $x['families'];
        $mains[] = ['name' => (string)$main, 'count' => $count, 'families' => $families, 'subs' => $list];
    }
    usort($mains, function ($a, $b) { return strnatcasecmp($a['name'], $b['name']); });
    out(['blocks' => $mains]);
}

function act_options() {
    current_user();
    $rel = db()->query("SELECT DISTINCT relation FROM beneficiaries WHERE relation <> ''")->fetchAll(PDO::FETCH_COLUMN);
    $rel = array_values(array_unique(array_merge(['FP', 'HUS', 'WIFE', 'SON', 'DAU'], $rel)));
    natcasesort($rel);
    $maj = db()->query("SELECT DISTINCT majhi FROM beneficiaries WHERE majhi <> ''")->fetchAll(PDO::FETCH_COLUMN);
    natcasesort($maj);
    out(['relations' => array_values($rel), 'majhis' => array_values($maj)]);
}

function list_where(&$args) {
    $w = [];
    $q = s('q', 100);
    if ($q !== '') {
        $w[] = "(name LIKE ? OR fcn LIKE ? OR majhi LIKE ?)";
        $args[] = '%' . $q . '%';
        $args[] = $q . '%';
        $args[] = '%' . $q . '%';
    }
    foreach (['main' => 'main_block', 'sub' => 'sub_block', 'sex' => 'sex', 'relation' => 'relation', 'majhi' => 'majhi', 'fcn' => 'fcn'] as $k => $col) {
        $v = s($k, 150);
        if ($v !== '') { $w[] = "$col = ?"; $args[] = $v; }
    }
    return $w ? ' WHERE ' . implode(' AND ', $w) : '';
}

function act_list() {
    current_user();
    $args = [];
    $where = list_where($args);
    $per = max(1, min(5000, (int)p('per', 50)));
    $offset = max(0, (int)p('offset', 0));
    $st = db()->prepare("SELECT COUNT(*) FROM beneficiaries" . $where);
    $st->execute($args);
    $total = (int)$st->fetchColumn();
    $st = db()->prepare("SELECT " . B_FIELDS . " FROM beneficiaries" . $where . " ORDER BY sl, id LIMIT $per OFFSET $offset");
    $st->execute($args);
    $rows = array_map('clean_row', $st->fetchAll());
    out(['total' => $total, 'offset' => $offset, 'rows' => $rows]);
}

function act_family() {
    current_user();
    $fcn = s('fcn', 30);
    if ($fcn === '') fail('No FCN given.');
    $st = db()->prepare("SELECT " . B_FIELDS . " FROM beneficiaries WHERE fcn = ? ORDER BY sl, id");
    $st->execute([$fcn]);
    out(['fcn' => $fcn, 'rows' => array_map('clean_row', $st->fetchAll())]);
}

function act_get() {
    current_user();
    $st = db()->prepare("SELECT b.*, u.name AS updated_by_name FROM beneficiaries b LEFT JOIN users u ON u.id = b.updated_by WHERE b.id = ?");
    $st->execute([(int)p('id', 0)]);
    $r = $st->fetch();
    if (!$r) fail('This record was not found. It may have been deleted.');
    $row = clean_row($r);
    $row['updated_at'] = $r['updated_at'];
    $row['updated_by_name'] = $r['updated_by_name'] ? $r['updated_by_name'] : '';
    out(['row' => $row]);
}

// ================= admin: records =================

function act_save() {
    $u = require_admin();
    $id = (int)p('id', 0);
    $f = [
        'name' => s('name', 150),
        'sex' => norm_sex(s('sex', 10)),
        'fcn' => s('fcn', 30),
        'dob' => valid_date(s('dob', 10)),
        'relation' => strtoupper(s('relation', 30)),
        'main_block' => s('main_block', 30),
        'sub_block' => s('sub_block', 30),
        'majhi' => s('majhi', 150),
        'remarks' => s('remarks', 500),
    ];
    if ($f['name'] === '') fail('Name is required.');
    if (s('dob', 10) !== '' && $f['dob'] === null) fail('Date of birth is not valid.');
    $pdo = db();
    $pdo->beginTransaction();
    if ($id > 0) {
        $st = $pdo->prepare("UPDATE beneficiaries SET name=?, sex=?, fcn=?, dob=?, relation=?, main_block=?, sub_block=?, majhi=?, remarks=?, updated_at=?, updated_by=? WHERE id=?");
        $st->execute([$f['name'], $f['sex'], $f['fcn'], $f['dob'], $f['relation'], $f['main_block'], $f['sub_block'], $f['majhi'], $f['remarks'], now(), $u['id'], $id]);
        if ($st->rowCount() === 0) {
            $chk = $pdo->prepare("SELECT id FROM beneficiaries WHERE id = ?");
            $chk->execute([$id]);
            if (!$chk->fetch()) { $pdo->rollBack(); fail('This record was not found. It may have been deleted.'); }
        }
    } else {
        // New member goes right after the last member of the same family, otherwise at the end.
        $sl = 0;
        if ($f['fcn'] !== '') {
            $st = $pdo->prepare("SELECT MAX(sl) FROM beneficiaries WHERE fcn = ?");
            $st->execute([$f['fcn']]);
            $last = $st->fetchColumn();
            if ($last !== null && $last !== false) {
                $sl = (int)$last + 1;
                $pdo->prepare("UPDATE beneficiaries SET sl = sl + 1 WHERE sl >= ?")->execute([$sl]);
            }
        }
        if ($sl === 0) $sl = (int)$pdo->query("SELECT COALESCE(MAX(sl), 0) FROM beneficiaries")->fetchColumn() + 1;
        $st = $pdo->prepare("INSERT INTO beneficiaries (sl, name, sex, fcn, dob, relation, main_block, sub_block, majhi, remarks, created_at, updated_at, updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)");
        $st->execute([$sl, $f['name'], $f['sex'], $f['fcn'], $f['dob'], $f['relation'], $f['main_block'], $f['sub_block'], $f['majhi'], $f['remarks'], now(), now(), $u['id']]);
        $id = (int)$pdo->lastInsertId();
    }
    add_block($f['main_block'], $f['sub_block']);
    $pdo->commit();
    $st = $pdo->prepare("SELECT " . B_FIELDS . " FROM beneficiaries WHERE id = ?");
    $st->execute([$id]);
    out(['row' => clean_row($st->fetch()), 'message' => 'Saved.']);
}

function act_delete() {
    require_admin();
    $st = db()->prepare("DELETE FROM beneficiaries WHERE id = ?");
    $st->execute([(int)p('id', 0)]);
    if ($st->rowCount() === 0) fail('This record was not found. It may have been deleted already.');
    out(['message' => 'Deleted.']);
}

function act_import() {
    $u = require_admin();
    $rows = p('rows', []);
    if (!is_array($rows) || count($rows) === 0) fail('No rows to import.');
    if (count($rows) > 2000) fail('Too many rows in one batch.');
    $replace = p('replace', false) === true;
    $pdo = db();
    $pdo->beginTransaction();
    if ($replace) {
        $pdo->exec("DELETE FROM beneficiaries");
        $pdo->exec("DELETE FROM blocks");
    }
    $sl = (int)$pdo->query("SELECT COALESCE(MAX(sl), 0) FROM beneficiaries")->fetchColumn();
    $ins = $pdo->prepare("INSERT INTO beneficiaries (sl, name, sex, fcn, dob, relation, main_block, sub_block, majhi, remarks, created_at, updated_at, updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)");
    $blk = $pdo->prepare(insert_ignore() . " INTO blocks (main_block, sub_block) VALUES (?, ?)");
    $seen = [];
    $done = 0; $skipped = 0;
    $t = now();
    foreach ($rows as $r) {
        if (!is_array($r)) { $skipped++; continue; }
        $g = function ($k, $max) use ($r) {
            $v = isset($r[$k]) && is_scalar($r[$k]) ? (string)$r[$k] : '';
            return mb_substr(trim(preg_replace('/\s+/u', ' ', $v)), 0, $max);
        };
        $name = $g('name', 150);
        if ($name === '') { $skipped++; continue; }
        $main = $g('main_block', 30);
        $sub = $g('sub_block', 30);
        $sl++;
        $ins->execute([$sl, $name, norm_sex($g('sex', 10)), $g('fcn', 30), valid_date($g('dob', 10)),
            strtoupper($g('relation', 30)), $main, $sub, $g('majhi', 150), $g('remarks', 500), $t, $t, $u['id']]);
        $key = $main . "\n" . $sub;
        if (!isset($seen[$key]) && ($main !== '' || $sub !== '')) {
            $blk->execute([$main, $sub]);
            $seen[$key] = 1;
        }
        $done++;
    }
    $pdo->commit();
    out(['imported' => $done, 'skipped' => $skipped]);
}

// ================= admin: users =================

function act_users() {
    require_admin();
    $rows = db()->query("SELECT * FROM users ORDER BY CASE status WHEN 'pending' THEN 0 WHEN 'active' THEN 1 ELSE 2 END, name")->fetchAll();
    out(['users' => array_map('user_public', $rows)]);
}

function act_user_set() {
    $me = require_admin();
    $id = (int)p('id', 0);
    $st = db()->prepare("SELECT * FROM users WHERE id = ?");
    $st->execute([$id]);
    $u = $st->fetch();
    if (!$u) fail('User not found.');
    $role = s('role', 10);
    $status = s('status', 10);
    $pass = (string)p('password', '');
    if ($id === (int)$me['id'] && ($role !== '' || $status !== '')) fail('You cannot change your own role or status.');
    $endSessions = false;
    if ($role !== '') {
        if (!in_array($role, ['admin', 'viewer'], true)) fail('Unknown role.');
        db()->prepare("UPDATE users SET role = ? WHERE id = ?")->execute([$role, $id]);
    }
    if ($status !== '') {
        if (!in_array($status, ['active', 'blocked', 'pending'], true)) fail('Unknown status.');
        db()->prepare("UPDATE users SET status = ? WHERE id = ?")->execute([$status, $id]);
        if ($status !== 'active') $endSessions = true;
    }
    if ($pass !== '') {
        if (strlen($pass) < 6) fail('Password must be at least 6 characters.');
        db()->prepare("UPDATE users SET pass_hash = ? WHERE id = ?")->execute([password_hash($pass, PASSWORD_DEFAULT), $id]);
        $endSessions = true;
    }
    if ($endSessions) db()->prepare("DELETE FROM sessions WHERE user_id = ?")->execute([$id]);
    $st->execute([$id]);
    out(['user' => user_public($st->fetch()), 'message' => 'Saved.']);
}

function act_user_delete() {
    $me = require_admin();
    $id = (int)p('id', 0);
    if ($id === (int)$me['id']) fail('You cannot delete your own account.');
    db()->prepare("DELETE FROM sessions WHERE user_id = ?")->execute([$id]);
    $st = db()->prepare("DELETE FROM users WHERE id = ?");
    $st->execute([$id]);
    if ($st->rowCount() === 0) fail('User not found.');
    out(['message' => 'User deleted.']);
}

// ================= messages =================

function msg_rows($sql, $args) {
    $st = db()->prepare($sql);
    $st->execute($args);
    $res = [];
    foreach ($st->fetchAll() as $m) {
        $res[] = [
            'id' => (int)$m['id'], 'user_id' => (int)$m['user_id'],
            'user_name' => $m['user_name'] ? $m['user_name'] : '(deleted user)',
            'user_email' => $m['user_email'] ? $m['user_email'] : '',
            'beneficiary_id' => $m['beneficiary_id'] ? (int)$m['beneficiary_id'] : 0,
            'beneficiary' => $m['b_name'] ? ($m['b_name'] . ' (FCN ' . $m['b_fcn'] . ')') : '',
            'subject' => $m['subject'], 'body' => $m['body'],
            'reply' => $m['reply'] ? $m['reply'] : '', 'status' => $m['status'],
            'created_at' => $m['created_at'], 'replied_at' => $m['replied_at'] ? $m['replied_at'] : '',
            'replied_by' => $m['r_name'] ? $m['r_name'] : '',
            'user_seen' => (int)$m['user_seen'],
        ];
    }
    return $res;
}

const MSG_SQL = "SELECT m.*, u.name AS user_name, u.email AS user_email, b.name AS b_name, b.fcn AS b_fcn, r.name AS r_name
    FROM messages m LEFT JOIN users u ON u.id = m.user_id LEFT JOIN beneficiaries b ON b.id = m.beneficiary_id
    LEFT JOIN users r ON r.id = m.replied_by";

function act_msg_send() {
    $u = current_user();
    $subject = s('subject', 150);
    $body = trim((string)p('body', ''));
    if ($body === '') fail('Please write your message.');
    $body = mb_substr($body, 0, 2000);
    $bid = (int)p('beneficiary_id', 0);
    db()->prepare("INSERT INTO messages (user_id, beneficiary_id, subject, body, status, created_at, user_seen) VALUES (?, ?, ?, ?, 'open', ?, 1)")
        ->execute([$u['id'], $bid > 0 ? $bid : null, $subject, $body, now()]);
    out(['message' => 'Your request was sent to the Admin.']);
}

function act_msg_mine() {
    $u = current_user();
    $rows = msg_rows(MSG_SQL . " WHERE m.user_id = ? ORDER BY m.id DESC LIMIT 200", [$u['id']]);
    db()->prepare("UPDATE messages SET user_seen = 1 WHERE user_id = ? AND user_seen = 0")->execute([$u['id']]);
    out(['messages' => $rows]);
}

function act_msg_all() {
    require_admin();
    $status = s('status', 10);
    if ($status === 'open') {
        $rows = msg_rows(MSG_SQL . " WHERE m.status = 'open' ORDER BY m.id DESC LIMIT 300", []);
    } else {
        $rows = msg_rows(MSG_SQL . " ORDER BY m.id DESC LIMIT 300", []);
    }
    out(['messages' => $rows]);
}

function act_msg_reply() {
    $me = require_admin();
    $reply = trim((string)p('reply', ''));
    if ($reply === '') fail('Please write a reply.');
    $st = db()->prepare("UPDATE messages SET reply = ?, status = 'replied', replied_at = ?, replied_by = ?, user_seen = 0 WHERE id = ?");
    $st->execute([mb_substr($reply, 0, 2000), now(), $me['id'], (int)p('id', 0)]);
    if ($st->rowCount() === 0) fail('Message not found.');
    out(['message' => 'Reply sent.']);
}

function act_msg_close() {
    require_admin();
    $st = db()->prepare("UPDATE messages SET status = 'closed' WHERE id = ?");
    $st->execute([(int)p('id', 0)]);
    out(['message' => 'Closed.']);
}

// ================= run =================
if (!is_string($action) || !in_array($action, $actions, true)) fail('Unknown action.');
call_user_func('act_' . $action);
