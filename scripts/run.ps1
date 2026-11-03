param([ValidateSet('server1','server2','client')][string]$Mode='client', [string]$Config='')
$ErrorActionPreference='Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot
. (Join-Path $PSScriptRoot 'java-runtime.ps1')
$env:JAVA_HOME=Get-LabJavaHome -ProjectRoot $projectRoot
$env:PATH=(Join-Path $env:JAVA_HOME 'bin')+';'+$env:PATH
$main = if ($Mode -eq 'client') {'client.RMIClientApp'} else {'server.RMIServerApp'}
if (!$Config -and $Mode -ne 'client') {
    $localConfig="config/$Mode.local.properties"
    $Config=if(Test-Path -LiteralPath $localConfig){$localConfig}else{"config/$Mode.properties"}
}
& "$projectRoot/mvnw.ps1" '-q' 'compile' 'javafx:run' "-Dapp.main=$main" "-Djavafx.args=`"$Config`""
exit $LASTEXITCODE
