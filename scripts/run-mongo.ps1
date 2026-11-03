param([ValidateSet(1,2)][int]$Instance=1, [ValidateRange(1,65535)][int]$Port=27017)
$ErrorActionPreference='Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot
$portable=Join-Path $projectRoot '.tools/mongodb-win32-x86_64-windows-8.0.4/bin/mongod.exe'
$installed=Get-Command mongod -ErrorAction SilentlyContinue
if(Test-Path -LiteralPath $portable){$exe=$portable}
elseif($installed){$exe=$installed.Source}
else {throw 'Can cai MongoDB Community va them mongod vao PATH.'}
$db=Join-Path $projectRoot ".data/mongo$Instance"
New-Item -ItemType Directory -Force $db | Out-Null
& $exe --dbpath $db --port $Port --bind_ip 127.0.0.1
exit $LASTEXITCODE
