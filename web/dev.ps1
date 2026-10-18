$nodePath = "C:\Users\nhanl\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin"
$fallbackDir = "C:\Users\nhanl\.cache\codex-runtimes\codex-primary-runtime\dependencies\bin\fallback"
if ($env:PATH -notlike "*$nodePath*") {
    $env:PATH = "$nodePath;$fallbackDir;" + $env:PATH
}
npm run dev
