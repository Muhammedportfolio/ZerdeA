$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root
& (Join-Path $PSScriptRoot 'build.ps1')
$java = Join-Path $root 'runtime\bin\java.exe'
foreach ($test in @('SuiteTest','HttpTest','PersistenceTest','GoogleLinkTest','TunnelTest','LaunchTest')) {
  Write-Host "Running $test"
  & $java '-Djava.awt.headless=true' -cp 'EduTestSuite.jar;lib/*' $test
  if ($LASTEXITCODE -ne 0) { throw "Test failed: $test" }
}
Write-Host 'All test programs passed.'
