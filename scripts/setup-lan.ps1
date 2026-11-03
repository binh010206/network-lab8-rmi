param([ValidateSet(1,2)][int]$Node=1, [string]$LocalIP='', [string]$PeerIP='')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$localFile=Join-Path $root "config/server$Node.local.properties"
if($LocalIP -and $PeerIP){
 $localAddress=[System.Net.IPAddress]::Parse($LocalIP)
 $peerAddress=[System.Net.IPAddress]::Parse($PeerIP)
 if($localAddress.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork -or $peerAddress.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork){throw 'Can nhap dia chi IPv4.'}
 $template=Get-Content (Join-Path $root "config/server$Node.properties") -Raw
 $template=$template -replace '(?m)^advertise.host=.*$',("advertise.host="+$LocalIP)
 $template=$template -replace '(?m)^peer.host=.*$',("peer.host="+$PeerIP)
 [System.IO.File]::WriteAllText($localFile,$template,[System.Text.UTF8Encoding]::new($false))
}
if(!(Test-Path -LiteralPath $localFile)){throw 'Can truyen -LocalIP va -PeerIP o lan thiet lap dau.'}
$identity=[Security.Principal.WindowsIdentity]::GetCurrent()
$admin=([Security.Principal.WindowsPrincipal]::new($identity)).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if(!$admin){
 $p=Start-Process powershell.exe -Verb RunAs -WindowStyle Hidden -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+$PSCommandPath+'"'),'-Node',$Node) -Wait -PassThru
 if($p.ExitCode -ne 0){throw 'Cau hinh that bai. Kiem tra MongoDB da cai va chap nhan quyen Administrator.'}
 Write-Host 'Da cau hinh MongoDB va cong RMI. Mo run-server tuong ung trong scripts.'
 exit 0
}
$c=@{}
Get-Content $localFile | ForEach-Object {if($_ -match '^([^#=]+)=(.*)$'){$c[$matches[1].Trim()]=$matches[2].Trim()}}
Start-Service MongoDB
$name="Lab8 RMI Server$Node LAN"
. (Join-Path $PSScriptRoot 'java-runtime.ps1')
# Prefer the process actually listening for this server; F5 may use VS Code's JDK.
$owners=@(Get-NetTCPConnection -State Listen -LocalPort ([int]$c['object.port']) -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique)
$java=$null
if($owners.Count -eq 1){
 $process=Get-CimInstance Win32_Process -Filter "ProcessId=$($owners[0])"
 if($process.ExecutablePath -and [System.IO.Path]::GetFileName($process.ExecutablePath) -in @('java.exe','javaw.exe')){$java=$process.ExecutablePath}
 else {throw 'Cong RMI dang duoc chuong trinh khac su dung. Dung chuong trinh do truoc khi thiet lap.'}
}
if(!$java){$java=Join-Path (Get-LabJavaHome -ProjectRoot $root) 'bin/java.exe'}
$ruleArgs=@{
 Enabled='True'; Direction='Inbound'; Action='Allow'; Protocol='TCP'
 LocalPort=@([int]$c['registry.port'],[int]$c['object.port'])
 # DHCP can change the local IP. Keep access limited to the LAN, Java and these two ports.
 LocalAddress='Any'; RemoteAddress='LocalSubnet'
 Profile=@('Public','Private'); Program=$java
}
if(Get-NetFirewallRule -DisplayName $name -ErrorAction SilentlyContinue){
 Set-NetFirewallRule -DisplayName $name @ruleArgs
} else {
 New-NetFirewallRule -DisplayName $name @ruleArgs | Out-Null
}
