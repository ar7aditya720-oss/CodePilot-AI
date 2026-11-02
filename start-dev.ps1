$ErrorActionPreference = 'Stop'
Write-Host 'Starting CodePilot AI backend in a new PowerShell window...'
Start-Process powershell -ArgumentList '-NoExit','-Command','Set-Location backend; mvn spring-boot:run'
Write-Host 'Installing frontend dependencies if needed...'
if (!(Test-Path 'node_modules')) { npm install }
Write-Host 'Starting Vite frontend...'
npm run dev
