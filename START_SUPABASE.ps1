# ASCII-only prompts keep this script compatible with Windows PowerShell 5.1.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$runtime = Join-Path $root 'runtime\bin\java.exe'
if (-not (Test-Path -LiteralPath $runtime)) { $runtime = (Get-Command java.exe -ErrorAction Stop).Source }

Write-Host 'ZerdeAI: connecting to Supabase PostgreSQL.'
Write-Host 'Use the Supabase Session pooler JDBC URL and include sslmode=verify-full.'
Write-Host 'The password is entered hidden and used only for this launch. H2 is not used if connection fails.'
$url = Read-Host 'JDBC URL (must include sslmode=verify-full)'
$dbUser = Read-Host 'PostgreSQL role name (pooler user)'
$securePassword = Read-Host 'PostgreSQL role password' -AsSecureString
if ($url -notmatch '^jdbc:postgresql://' -or $url -notmatch '(?i)(\?|&)sslmode=verify-full(&|$)') { throw 'A Supabase JDBC URL with sslmode=verify-full is required.' }
if ([string]::IsNullOrWhiteSpace($dbUser) -or $securePassword.Length -eq 0) { throw 'Role name and password are required.' }

$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
  $env:ZERDEAI_DB_URL = $url
  $env:ZERDEAI_DB_USER = $dbUser
  $env:ZERDEAI_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
  Push-Location -LiteralPath $root
  try {
    $javaArgs = @('--module-path', 'windows-lib', '--add-modules', 'javafx.controls,javafx.web', '-cp', 'EduTestSuite.jar;lib/*', 'SafeLaunch')
    & $runtime @javaArgs
    if ($LASTEXITCODE -ne 0) { throw 'ZerdeAI exited with an error. Check the connection message in the app window.' }
  } finally { Pop-Location }
} finally {
  $env:ZERDEAI_DB_URL = $null
  $env:ZERDEAI_DB_USER = $null
  $env:ZERDEAI_DB_PASSWORD = $null
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
  $securePassword.Dispose()
}
