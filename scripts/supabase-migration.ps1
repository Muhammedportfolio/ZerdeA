param(
  [Parameter(Mandatory = $true)][ValidateSet('Export','Apply')][string]$Action,
  [string]$H2DataDirectory = (Join-Path $env:USERPROFILE 'EduTestSuite2'),
  [Parameter(Mandatory = $true)][string]$BackupFile
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $root 'runtime\bin\java.exe'
if (-not (Test-Path -LiteralPath $runtime)) { $runtime = (Get-Command java.exe -ErrorAction Stop).Source }
if (-not (Test-Path -LiteralPath $H2DataDirectory -PathType Container)) { throw "Папка H2 не найдена: $H2DataDirectory" }
if (-not (Test-Path -LiteralPath (Join-Path $H2DataDirectory 'database.mv.db') -PathType Leaf)) { throw 'В указанной папке нет ожидаемого файла database.mv.db; перенос остановлен.' }
$backupPath = [IO.Path]::GetFullPath($BackupFile)
$dataPath = [IO.Path]::GetFullPath($H2DataDirectory)
if ($backupPath.StartsWith($dataPath + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Храните файл backup отдельно от папки базы H2.' }
Push-Location -LiteralPath $root
try {
  if ($Action -eq 'Export') {
    & $runtime -cp 'EduTestSuite.jar;lib/*' SupabaseMigration --export $H2DataDirectory $backupPath
    if ($LASTEXITCODE -ne 0) { throw 'Экспорт/проверка H2 завершились ошибкой; облачная база не менялась.' }
    return
  }

  if (-not (Test-Path -LiteralPath $backupPath -PathType Leaf)) { throw 'Сначала выполните Export и проверьте созданный backup.' }
  Write-Host 'Импорт не перезаписывает строки Supabase. H2 источник останется без изменений.'
  $confirm = Read-Host 'Для подтверждения импорта в пустой Supabase введите IMPORT'
  if ($confirm -cne 'IMPORT') { throw 'Импорт отменён.' }
  $url = Read-Host 'JDBC URL jdbc:postgresql://... Session pooler с sslmode=verify-full'
  $dbUser = Read-Host 'Имя роли PostgreSQL (для pooler обычно роль.project_ref)'
  $securePassword = Read-Host 'Пароль роли PostgreSQL' -AsSecureString
  if ($url -notmatch '^jdbc:postgresql://' -or $url -notmatch '(?i)(\?|&)sslmode=verify-full(&|$)') { throw 'Нужен JDBC URL с sslmode=verify-full.' }
  if ([string]::IsNullOrWhiteSpace($dbUser) -or $securePassword.Length -eq 0) { throw 'Имя роли и пароль обязательны.' }
  $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
  try {
    $env:ZERDEAI_DB_URL = $url
    $env:ZERDEAI_DB_USER = $dbUser
    $env:ZERDEAI_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    & $runtime -cp 'EduTestSuite.jar;lib/*' SupabaseMigration --apply $H2DataDirectory $backupPath
    if ($LASTEXITCODE -ne 0) { throw 'Импорт остановлен. Сохраните H2 и backup; проверьте содержимое Supabase перед повтором.' }
  } finally {
    $env:ZERDEAI_DB_URL = $null
    $env:ZERDEAI_DB_USER = $null
    $env:ZERDEAI_DB_PASSWORD = $null
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    $securePassword.Dispose()
  }
} finally { Pop-Location }
