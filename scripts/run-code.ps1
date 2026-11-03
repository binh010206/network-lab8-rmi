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
[xml]$pom=Get-Content (Join-Path $root 'pom.xml') -Raw
$required=[int]$pom.project.properties.'maven.compiler.release'
$homes=@($env:JAVA_HOME)
$java=Get-Command java -ErrorAction SilentlyContinue
if($java){$homes+=Split-Path -Parent (Split-Path -Parent $java.Source)}
$homes+=@(Get-ChildItem "$env:ProgramFiles/Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue | ForEach-Object FullName)
$homes+=@(Get-ChildItem "$env:USERPROFILE/.vscode/extensions" -Directory -Filter 'redhat.java*' -ErrorAction SilentlyContinue | ForEach-Object {Get-ChildItem (Join-Path $_.FullName 'jre') -Directory -ErrorAction SilentlyContinue | ForEach-Object FullName})
$selected=$null
foreach($homePath in ($homes | Where-Object {$_} | Select-Object -Unique)){
 if(!(Test-Path -LiteralPath (Join-Path $homePath 'bin/javac.exe'))){continue}
 $release=Get-Content (Join-Path $homePath 'release') -Raw -ErrorAction SilentlyContinue
 if($release -match 'JAVA_VERSION="(\d+)' -and [int]$matches[1] -ge $required){$selected=$homePath;break}
}
if(!$selected){throw "Can JDK $required de chay cau hinh hien tai. Cai JDK hoac chon lai Java runtime trong VS Code."}
$env:JAVA_HOME=$selected
$env:PATH=(Join-Path $selected 'bin')+';'+$env:PATH
if($Check){Write-Output "MAIN=$main JDK=$selected RELEASE=$required";exit 0}
& (Join-Path $root 'mvnw.ps1') '-q' 'compile' 'javafx:run' "-Dapp.main=$main" '-Djavafx.args='
exit $LASTEXITCODE
