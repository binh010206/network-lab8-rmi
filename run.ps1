param([ValidateSet('server1','server2','client')][string]$Mode='client', [string]$Config='')
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
$main = if ($Mode -eq 'client') {'client.RMIClientApp'} else {'server.RMIServerApp'}
if (!$Config -and $Mode -ne 'client') { $Config="config/$Mode.properties" }
& "$PSScriptRoot/mvnw.ps1" '-q' 'compile' 'javafx:run' "-Dapp.main=$main" "-Djavafx.args=`"$Config`""
exit $LASTEXITCODE
