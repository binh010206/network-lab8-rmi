param([ValidateSet(1,2)][int]$Instance=1)
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
$portable=Join-Path $PSScriptRoot '.tools/mongodb-win32-x86_64-windows-8.0.4/bin/mongod.exe'
$installed=Get-Command mongod -ErrorAction SilentlyContinue
if(Test-Path -LiteralPath $portable){$exe=$portable}
elseif($installed){$exe=$installed.Source}
else {throw 'Can cai MongoDB Community va them mongod vao PATH. Xem README.md; hoac dung docker compose up -d.'}
$db=Join-Path $PSScriptRoot ".data/mongo$Instance"
New-Item -ItemType Directory -Force $db | Out-Null
$port=if($Instance -eq 1){27017}else{27018}
& $exe --dbpath $db --port $port --bind_ip 127.0.0.1
exit $LASTEXITCODE
