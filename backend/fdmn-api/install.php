<?php
// 8 West FDMN Database - one-time setup page.
define('FDMN', 1);
require __DIR__ . '/lib.php';

$done = file_exists(__DIR__ . '/config.php');
$error = '';
$ok = false;

function h($s) { return htmlspecialchars((string)$s, ENT_QUOTES, 'UTF-8'); }
function v($k, $d = '') { return isset($_POST[$k]) ? trim($_POST[$k]) : $d; }

if (!$done && $_SERVER['REQUEST_METHOD'] === 'POST') {
    $host = v('db_host', 'localhost');
    $name = v('db_name');
    $user = v('db_user');
    $pass = isset($_POST['db_pass']) ? $_POST['db_pass'] : '';
    $aName = v('admin_name');
    $aEmail = strtolower(v('admin_email'));
    $aPass = isset($_POST['admin_pass']) ? $_POST['admin_pass'] : '';
    try {
        if ($name === '' || $user === '') throw new Exception('Please fill in the database name and database user.');
        if (strlen($aName) < 2) throw new Exception('Please enter the Admin name.');
        if (!filter_var($aEmail, FILTER_VALIDATE_EMAIL)) throw new Exception('Please enter a valid Admin email.');
        if (strlen($aPass) < 6) throw new Exception('Admin password must be at least 6 characters.');
        $pdo = new PDO('mysql:host=' . $host . ';dbname=' . $name . ';charset=utf8mb4', $user, $pass);
        $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
        foreach (schema_sql('mysql') as $sql) $pdo->exec($sql);
        $st = $pdo->prepare("SELECT id FROM users WHERE email = ?");
        $st->execute([$aEmail]);
        if ($st->fetch()) {
            $pdo->prepare("UPDATE users SET name = ?, pass_hash = ?, role = 'admin', status = 'active' WHERE email = ?")
                ->execute([$aName, password_hash($aPass, PASSWORD_DEFAULT), $aEmail]);
        } else {
            $pdo->prepare("INSERT INTO users (name, email, pass_hash, role, status, created_at) VALUES (?, ?, ?, 'admin', 'active', ?)")
                ->execute([$aName, $aEmail, password_hash($aPass, PASSWORD_DEFAULT), now()]);
        }
        $cfg = "<?php\n// Created by install.php on " . now() . "\n"
            . "if (!defined('FDMN')) { http_response_code(403); exit; }\n"
            . "define('DB_DRIVER', 'mysql');\n"
            . "define('DB_HOST', " . var_export($host, true) . ");\n"
            . "define('DB_NAME', " . var_export($name, true) . ");\n"
            . "define('DB_USER', " . var_export($user, true) . ");\n"
            . "define('DB_PASS', " . var_export($pass, true) . ");\n";
        if (@file_put_contents(__DIR__ . '/config.php', $cfg) === false) {
            throw new Exception('Could not write config.php. Check that the folder is writable (permission 755).');
        }
        @chmod(__DIR__ . '/config.php', 0600);
        $ok = true;
    } catch (PDOException $e) {
        $error = 'Could not connect to the database. Check the database name, user and password, and that the user is added to the database with ALL PRIVILEGES. (' . $e->getMessage() . ')';
    } catch (Exception $e) {
        $error = $e->getMessage();
    }
}
?><!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>8 West FDMN Database - Setup</title>
<style>
body{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#eef3f1;margin:0;padding:16px;color:#1d2b27}
.card{max-width:520px;margin:0 auto;background:#fff;border-radius:16px;padding:20px;box-shadow:0 2px 10px rgba(0,0,0,.08)}
h1{font-size:20px;margin:0 0 4px;color:#0b5d4b}h2{font-size:15px;margin:20px 0 8px;color:#0b5d4b}
p{font-size:14px;line-height:1.5}label{display:block;font-size:13px;margin:10px 0 4px;color:#4a5a55}
input{width:100%;box-sizing:border-box;padding:12px;border:1px solid #c9d6d1;border-radius:10px;font-size:16px}
button{margin-top:20px;width:100%;padding:14px;border:0;border-radius:12px;background:#0b5d4b;color:#fff;font-size:16px;font-weight:600}
.err{background:#fdecea;color:#a12622;padding:12px;border-radius:10px;font-size:14px}
.ok{background:#e6f4ea;color:#1e6b34;padding:12px;border-radius:10px;font-size:14px}
</style></head><body><div class="card">
<h1>8 West FDMN Database</h1>
<?php if ($ok): ?>
  <p class="ok"><b>Setup complete!</b> The database tables were created and your Admin account is ready.</p>
  <p>Now open the Android app and sign in with <b><?= h($aEmail) ?></b>.</p>
  <p><b>Important:</b> for safety, delete <b>install.php</b> from this folder in cPanel File Manager. (It is already locked and cannot be used again.)</p>
<?php elseif ($done): ?>
  <p class="ok">This server is already set up. The setup page is locked.</p>
  <p>For safety, delete <b>install.php</b> from this folder in cPanel File Manager.</p>
<?php else: ?>
  <p>One-time setup. Enter the MySQL database you created in cPanel, and choose your Admin login.</p>
  <?php if ($error): ?><p class="err"><?= h($error) ?></p><?php endif; ?>
  <form method="post" autocomplete="off">
    <h2>1. MySQL database</h2>
    <label>Database host</label><input name="db_host" value="<?= h(v('db_host', 'localhost')) ?>">
    <label>Database name (for example: cpaneluser_fdmn)</label><input name="db_name" value="<?= h(v('db_name')) ?>" required>
    <label>Database user (for example: cpaneluser_fdmnuser)</label><input name="db_user" value="<?= h(v('db_user')) ?>" required>
    <label>Database password</label><input name="db_pass" type="password">
    <h2>2. First Admin account</h2>
    <label>Admin name</label><input name="admin_name" value="<?= h(v('admin_name')) ?>" required>
    <label>Admin email</label><input name="admin_email" type="email" value="<?= h(v('admin_email')) ?>" required>
    <label>Admin password (at least 6 characters)</label><input name="admin_pass" type="password" required>
    <button type="submit">Set up now</button>
  </form>
<?php endif; ?>
</div></body></html>
