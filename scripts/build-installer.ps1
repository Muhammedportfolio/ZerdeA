param([string]$OutputDirectory = (Join-Path (Split-Path -Parent (Split-Path -Parent $PSCommandPath)) 'outputs'), [switch]$PrepareOnly, [switch]$UseIExpress)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$stage = Join-Path (Split-Path -Parent $root) 'installer-stage'
$payloadZip = Join-Path $stage 'payload.zip'
$sedPath = Join-Path $stage 'ZerdeAI_Setup.sed'
$outputExe = Join-Path $OutputDirectory 'ZerdeAI_Setup.exe'
if (-not (Test-Path -LiteralPath (Join-Path $root 'EduTestSuite.jar'))) { throw 'Build EduTestSuite.jar before packaging.' }
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
New-Item -ItemType Directory -Path (Join-Path $stage 'ZerdeAI_Project') -Force | Out-Null
Copy-Item -Path (Join-Path $root '*') -Destination (Join-Path $stage 'ZerdeAI_Project') -Recurse -Force
Remove-Item -LiteralPath (Join-Path $stage 'ZerdeAI_Project\build') -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath (Join-Path $stage 'ZerdeAI_Project\outputs') -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath (Join-Path $stage 'ZerdeAI_Project\.git') -Recurse -Force -ErrorAction SilentlyContinue
# These are local build/test downloads and caches, not app dependencies.
Remove-Item -LiteralPath (Join-Path $stage 'ZerdeAI_Project\tools\temurin17') -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath (Join-Path $stage 'ZerdeAI_Project\tools\temurin17-jdk.zip') -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath (Join-Path $stage 'ZerdeAI_Project\tools\browser-check-classes') -Recurse -Force -ErrorAction SilentlyContinue
Get-ChildItem -LiteralPath (Join-Path $stage 'ZerdeAI_Project\tools') -Directory -Filter 'browser-check-data*' -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force
Compress-Archive -Path (Join-Path $stage 'ZerdeAI_Project') -DestinationPath $payloadZip -CompressionLevel Optimal -Force
Copy-Item -LiteralPath (Join-Path $root 'Install-ZerdeAI.ps1') -Destination (Join-Path $stage 'Install-ZerdeAI.ps1') -Force

$sed = @"
[Version]
Class=IEXPRESS
SEDVersion=3
[Options]
PackagePurpose=InstallApp
ShowInstallProgramWindow=1
HideExtractAnimation=1
UseLongFileName=1
InsideCompressed=0
CAB_FixedSize=0
CAB_ResvCodeSigning=0
RebootMode=N
InstallPrompt=%InstallPrompt%
DisplayLicense=%DisplayLicense%
FinishMessage=%FinishMessage%
TargetName=%TargetName%
FriendlyName=%FriendlyName%
AppLaunched=%AppLaunched%
PostInstallCmd=%PostInstallCmd%
AdminQuietInstCmd=%AdminQuietInstCmd%
UserQuietInstCmd=%UserQuietInstCmd%
SourceFiles=SourceFiles
[Strings]
InstallPrompt=
DisplayLicense=
FinishMessage=
TargetName=$outputExe
FriendlyName=Zerde AI Setup
AppLaunched=powershell.exe -NoProfile -ExecutionPolicy Bypass -File Install-ZerdeAI.ps1
PostInstallCmd=<None>
AdminQuietInstCmd=
UserQuietInstCmd=
FILE0="Install-ZerdeAI.ps1"
FILE1="payload.zip"
[SourceFiles]
SourceFiles0=$stage\
[SourceFiles0]
%FILE0%=
%FILE1%=
"@
[IO.File]::WriteAllText($sedPath,$sed,[Text.Encoding]::ASCII)
if ($PrepareOnly) { Write-Host "IExpress SED prepared: $sedPath"; return }
$bootstrap = Join-Path $PSScriptRoot 'SetupBootstrap.cs'
$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not $UseIExpress) {
  if (-not (Test-Path -LiteralPath $csc)) { throw 'Windows .NET Framework C# compiler is unavailable; use -UseIExpress or install the Windows .NET Framework developer tools.' }
  $cscArgs = @('/nologo','/target:winexe',('/out:' + $outputExe),('/resource:' + (Join-Path $stage 'Install-ZerdeAI.ps1') + ',ZerdeAI_Installer'),('/resource:' + $payloadZip + ',ZerdeAI_Payload'),$bootstrap)
  & $csc @cscArgs
  if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $outputExe)) { throw 'The self-extracting Setup EXE was not created.' }
  Write-Host "Setup created: $outputExe"
  return
}
$iexpress = Join-Path $env:WINDIR 'System32\iexpress.exe'
if (-not (Test-Path -LiteralPath $iexpress)) { throw 'Windows IExpress is unavailable; setup executable was not built.' }
$process = Start-Process -FilePath $iexpress -ArgumentList @('/N','/Q','/M',('"' + $sedPath + '"')) -Wait -PassThru -WindowStyle Hidden
if (-not (Test-Path -LiteralPath $outputExe)) { throw "IExpress did not create $outputExe (exit $($process.ExitCode))." }
Write-Host "Setup created: $outputExe"
