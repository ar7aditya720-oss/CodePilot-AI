$ErrorActionPreference = 'Stop'

# Always run relative to this script, even when launched from another folder.
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $ProjectRoot

# Repair a known corrupted download where literal \n text was inserted between React statements.
$AppPath = Join-Path $ProjectRoot 'src\App.tsx'
if (!(Test-Path -LiteralPath $AppPath)) {
    throw "Cannot find src\App.tsx. Extract the complete CodePilot AI project folder first."
}
$AppContent = Get-Content -LiteralPath $AppPath -Raw
$CorruptPattern = ';\\n([ \t]*const )'
if ([regex]::IsMatch($AppContent, $CorruptPattern)) {
    $AppContent = [regex]::Replace($AppContent, $CorruptPattern, ";`r`n`$1")
    Set-Content -LiteralPath $AppPath -Value $AppContent -Encoding utf8
    Write-Host 'Repaired malformed newline characters in src\App.tsx.' -ForegroundColor Yellow
}

Write-Host 'Checking frontend dependencies...'
if (!(Test-Path -LiteralPath (Join-Path $ProjectRoot 'node_modules'))) {
    npm install
    if ($LASTEXITCODE -ne 0) { throw 'npm install failed.' }
}

Write-Host 'Building frontend to catch errors before starting...'
npm run build
if ($LASTEXITCODE -ne 0) {
    throw 'Frontend build failed. Do not start the app until the build error is fixed.'
}

Write-Host 'Starting CodePilot AI backend in a new PowerShell window...'
$BackendPath = Join-Path $ProjectRoot 'backend'
if (!(Test-Path -LiteralPath $BackendPath)) {
    throw 'Cannot find the backend folder.'
}
Start-Process -FilePath 'powershell.exe' -WorkingDirectory $BackendPath -ArgumentList @('-NoExit', '-Command', 'mvn spring-boot:run')

Write-Host 'Starting Vite frontend...'
npm run dev
