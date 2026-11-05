param([ValidateSet('1','2','test')][string]$Node='1', [int]$Port=5432)
$ErrorActionPreference='Stop'
if($Port -lt 1 -or $Port -gt 65535){throw 'Invalid PostgreSQL port.'}
$root=Split-Path -Parent $PSScriptRoot
Set-Location $root
. (Join-Path $PSScriptRoot 'java-runtime.ps1')
$env:JAVA_HOME=Get-LabJavaHome -ProjectRoot $root
$env:PATH=(Join-Path $env:JAVA_HOME 'bin')+';'+$env:PATH
& "$root/mvnw.ps1" '-q' 'compile' 'dependency:build-classpath' '-Dmdep.outputFile=.tools/postgres-classpath.txt'
if($LASTEXITCODE -ne 0){throw 'Build failed.'}
$classpath=(Join-Path $root 'target/classes')+';'+(Get-Content "$root/.tools/postgres-classpath.txt" -Raw).Trim()
$previous=$env:LAB8_PG_ADMIN_PASSWORD
try {
 if(!$previous){
  $secure=Read-Host 'Mat khau postgres da dat khi cai PostgreSQL' -AsSecureString
  $ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
  try {$env:LAB8_PG_ADMIN_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)}
  finally {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)}
 }
 & java '-cp' $classpath 'setup.PostgresSetup' $Node "jdbc:postgresql://127.0.0.1:$Port/postgres"
 if($LASTEXITCODE -ne 0){throw 'PostgreSQL setup failed. Check the service, port and postgres password.'}
} finally {$env:LAB8_PG_ADMIN_PASSWORD=$previous}
