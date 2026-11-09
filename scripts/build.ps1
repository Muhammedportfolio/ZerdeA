$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root
$java = Join-Path $root 'runtime\bin\java.exe'
if (-not (Test-Path -LiteralPath $java)) { throw 'Bundled Java is missing.' }
$javac = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
  Join-Path $env:JAVA_HOME 'bin\javac.exe'
} else {
  $command = Get-Command javac.exe -ErrorAction SilentlyContinue
  if ($command) { $command.Source } else { $null }
}
$jarTool = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\jar.exe'))) {
  Join-Path $env:JAVA_HOME 'bin\jar.exe'
} else {
  $command = Get-Command jar.exe -ErrorAction SilentlyContinue
  if ($command) { $command.Source } else { $null }
}
if (-not $javac -or -not $jarTool) {
  $modules = & $java --list-modules 2>$null
  if (-not ($modules -match '^jdk\.compiler@') -or -not ($modules -match '^jdk\.jartool@')) {
    throw 'Bundled runtime has no jdk.compiler/jdk.jartool modules. Install a full JDK 17 and set JAVA_HOME before building.'
  }
}
$deps = @((Get-ChildItem -LiteralPath (Join-Path $root 'lib') -Filter '*.jar').FullName)
$deps += @((Get-ChildItem -LiteralPath (Join-Path $root 'windows-lib') -Filter '*.jar').FullName)
$sources = @((Get-ChildItem -LiteralPath (Join-Path $root 'src') -Filter '*.java').FullName)
$build = Join-Path $root 'build'
if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
New-Item -ItemType Directory -Path $build -Force | Out-Null
$compilerArgs = @('--release','17','-encoding','UTF-8','-cp',($deps -join ';'),'-d',$build) + $sources
if ($javac) { & $javac @compilerArgs } else {
  $compilerCommand = @('-m','jdk.compiler/com.sun.tools.javac.Main') + $compilerArgs
  & $java @compilerCommand
}
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed; existing application JAR was preserved.' }
$candidate = Join-Path $root 'EduTestSuite.next.jar'
if ($jarTool) {
  & $jarTool --create --file $candidate --main-class SafeLaunch -C $build . -C $root web -C $root assets
} else {
  & $java -m jdk.jartool/sun.tools.jar.Main --create --file $candidate --main-class SafeLaunch -C $build . -C $root web -C $root assets
}
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed; existing application JAR was preserved.' }
Move-Item -LiteralPath $candidate -Destination (Join-Path $root 'EduTestSuite.jar') -Force
Write-Host 'Build OK. Start with START_WINDOWS.bat.'
