$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$maven = Join-Path $root '.tools/apache-maven-3.9.9/bin/mvn.cmd'
if (!(Test-Path -LiteralPath $maven)) {
    New-Item -ItemType Directory -Force (Join-Path $root '.tools') | Out-Null
    $zip = Join-Path $root '.tools/maven.zip'
    Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip' -OutFile $zip
    Expand-Archive -LiteralPath $zip -DestinationPath (Join-Path $root '.tools') -Force
}
Push-Location $root
try { & $maven @args; exit $LASTEXITCODE } finally { Pop-Location }
