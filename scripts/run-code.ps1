param([Parameter(Mandatory=$true)][string]$Source, [switch]$Check)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
Set-Location $root
$entries=@{
 'RMIClientApp.java'='client.RMIClientApp'
 'RMIServerApp.java'='server.RMIServerApp'
 'Server1App.java'='server.Server1App'
 'Server2App.java'='server.Server2App'
}
$main=$entries[[System.IO.Path]::GetFileName($Source)]
if(!$main){throw 'Hay mo RMIClientApp.java, Server1App.java hoac Server2App.java roi bam Run Code.'}
. (Join-Path $PSScriptRoot 'java-runtime.ps1')
$selected=Get-LabJavaHome -ProjectRoot $root
$env:JAVA_HOME=$selected
$env:PATH=(Join-Path $selected 'bin')+';'+$env:PATH
if($Check){Write-Output "MAIN=$main JDK=$selected";exit 0}
& (Join-Path $root 'mvnw.ps1') '-q' 'compile' 'javafx:run' "-Dapp.main=$main" '-Djavafx.args='
exit $LASTEXITCODE
