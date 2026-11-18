<?php
// Camp- 8 West · FDMN Database — web app (same data as the Android app, via api/)
header('Content-Type: text/html; charset=utf-8');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: same-origin');
$v = function ($f) { $p = __DIR__ . '/assets/' . $f; return 'assets/' . $f . '?v=' . (file_exists($p) ? filemtime($p) : '1'); };
?><!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="theme-color" content="#0B5D4B">
<meta name="robots" content="noindex, nofollow">
<title>Camp- 8 West · FDMN Database</title>
<link rel="icon" type="image/png" href="assets/favicon.png">
<link rel="apple-touch-icon" href="assets/icon-192.png">
<link rel="manifest" href="manifest.json">
<link rel="stylesheet" href="<?= $v('app.css') ?>">
</head>
<body>
<svg width="0" height="0" style="position:absolute" aria-hidden="true">
  <defs><clipPath id="heroWave" clipPathUnits="objectBoundingBox"><path d="M0,0 H1 V0.90 C0.72,1.02 0.36,0.80 0,0.93 Z"/></clipPath></defs>
</svg>
<div id="app"></div>
<noscript><p style="padding:20px">Please turn on JavaScript to use this app.</p></noscript>
<script src="<?= $v('icons.js') ?>"></script>
<script src="<?= $v('xlsx.js') ?>"></script>
<script src="<?= $v('app.js') ?>"></script>
</body>
</html>
