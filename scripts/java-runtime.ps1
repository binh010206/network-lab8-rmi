function Get-LabJavaHome {
 param([Parameter(Mandatory=$true)][string]$ProjectRoot)
[xml]$pom=Get-Content (Join-Path $ProjectRoot 'pom.xml') -Raw
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
 return $selected
}
