$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$script:installDir = Join-Path $env:LOCALAPPDATA 'Programs\ZerdeAI'
$registryPath = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\ZerdeAI'
$menuDir = Join-Path ([Environment]::GetFolderPath('StartMenu')) 'Programs\ZerdeAI'
$payload = Join-Path $PSScriptRoot 'payload.zip'
if (Test-Path -LiteralPath $registryPath) {
  $registeredPath = (Get-ItemProperty -LiteralPath $registryPath -Name InstallLocation -ErrorAction SilentlyContinue).InstallLocation
  if ($registeredPath -and (Test-Path -LiteralPath (Join-Path $registeredPath 'EduTestSuite.jar'))) { $script:installDir = $registeredPath }
}

function Test-Installed {
  return (Test-Path -LiteralPath $registryPath) -and (Test-Path -LiteralPath (Join-Path $script:installDir 'EduTestSuite.jar'))
}
function Test-AppRunning {
  if (-not (Test-Path -LiteralPath $script:installDir)) { return $false }
  $prefix = [IO.Path]::GetFullPath($script:installDir).TrimEnd('\') + '\'
  foreach ($proc in Get-Process javaw -ErrorAction SilentlyContinue) {
    try { if ($proc.Path -and $proc.Path.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)) { return $true } } catch {}
  }
  return $false
}
function Set-Shortcut([string]$path,[string]$target,[string]$args,[string]$icon) {
  $shell = New-Object -ComObject WScript.Shell
  $shortcut = $shell.CreateShortcut($path)
  $shortcut.TargetPath = $target
  $shortcut.Arguments = $args
  $shortcut.WorkingDirectory = $script:installDir
  if ($icon) { $shortcut.IconLocation = $icon }
  $shortcut.Save()
}
function Install-App {
  if (-not (Test-Path -LiteralPath $payload)) { throw 'Не найден payload.zip рядом с установщиком.' }
  if (Test-AppRunning) { throw 'Сначала закройте ZerdeAI, затем повторите установку или обновление.' }
  $parent = Split-Path -Parent $script:installDir
  New-Item -ItemType Directory -Path $parent -Force | Out-Null
  $temp = Join-Path $env:TEMP ('ZerdeAI-Setup-' + [guid]::NewGuid().ToString('N'))
  try {
    New-Item -ItemType Directory -Path $temp -Force | Out-Null
    Expand-Archive -LiteralPath $payload -DestinationPath $temp -Force
    $source = Join-Path $temp 'ZerdeAI_Project'
    if (-not (Test-Path -LiteralPath (Join-Path $source 'EduTestSuite.jar'))) { throw 'Архив неполный: не найден EduTestSuite.jar.' }
    New-Item -ItemType Directory -Path $script:installDir -Force | Out-Null
    Copy-Item -Path (Join-Path $source '*') -Destination $script:installDir -Recurse -Force
    New-Item -ItemType Directory -Path $menuDir -Force | Out-Null
    $desktop = [Environment]::GetFolderPath('Desktop')
    $launcher = Join-Path $script:installDir 'Launch_ZerdeAI.vbs'
    Set-Shortcut (Join-Path $menuDir 'Zerde AI.lnk') 'wscript.exe' ('"' + $launcher + '"') (Join-Path $script:installDir 'assets\icon.ico')
    Set-Shortcut (Join-Path $desktop 'Zerde AI.lnk') 'wscript.exe' ('"' + $launcher + '"') (Join-Path $script:installDir 'assets\icon.ico')
    $uninstaller = Join-Path $script:installDir 'scripts\uninstall.ps1'
    $powershell = Join-Path $PSHOME 'powershell.exe'
    $quoted = '"' + $uninstaller + '"'
    New-Item -Path $registryPath -Force | Out-Null
    Set-ItemProperty $registryPath DisplayName 'Zerde AI'
    Set-ItemProperty $registryPath DisplayVersion '2.3.0'
    Set-ItemProperty $registryPath Publisher 'ZerdeAI'
    Set-ItemProperty $registryPath InstallLocation $script:installDir
    Set-ItemProperty $registryPath UninstallString ('"' + $powershell + '" -NoProfile -ExecutionPolicy Bypass -File ' + $quoted)
    Set-ItemProperty $registryPath NoModify 1 -Type DWord
    Set-ItemProperty $registryPath NoRepair 1 -Type DWord
    Set-Shortcut (Join-Path $menuDir 'Удалить ZerdeAI.lnk') $powershell ('-NoProfile -ExecutionPolicy Bypass -File ' + $quoted) ''
  } finally {
    Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
  }
}

$form = New-Object Windows.Forms.Form
$form.Text = 'Zerde AI — установка'
$form.Size = New-Object Drawing.Size(560,310)
$form.StartPosition = 'CenterScreen'
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
$form.Font = New-Object Drawing.Font('Segoe UI',10)
$title = New-Object Windows.Forms.Label
$title.Text = 'ZERDE AI'
$title.Font = New-Object Drawing.Font('Segoe UI',22,[Drawing.FontStyle]::Bold)
$title.Location = New-Object Drawing.Point(28,22);$title.Size = New-Object Drawing.Size(460,42)
$form.Controls.Add($title)
$pathLabel = New-Object Windows.Forms.Label
$pathLabel.Text = 'Папка программы';$pathLabel.Location = New-Object Drawing.Point(30,82);$pathLabel.Size = New-Object Drawing.Size(460,22)
$form.Controls.Add($pathLabel)
$pathBox = New-Object Windows.Forms.TextBox
$pathBox.Text = $script:installDir;$pathBox.Location = New-Object Drawing.Point(30,108);$pathBox.Size = New-Object Drawing.Size(485,28)
$form.Controls.Add($pathBox)
$info = New-Object Windows.Forms.Label
$info.Text = 'Данные аккаунтов, заданий и оценок хранятся отдельно и сохраняются при обновлении.'
$info.Location = New-Object Drawing.Point(30,145);$info.Size = New-Object Drawing.Size(485,42)
$form.Controls.Add($info)
$install = New-Object Windows.Forms.Button
$install.Text = if (Test-Installed) { 'Обновить' } else { 'Установить' }
$install.Location = New-Object Drawing.Point(30,205);$install.Size = New-Object Drawing.Size(115,38)
$form.Controls.Add($install)
$launch = New-Object Windows.Forms.Button
$launch.Text = 'Запустить';$launch.Location = New-Object Drawing.Point(155,205);$launch.Size = New-Object Drawing.Size(105,38)
$launch.Enabled = Test-Installed;$form.Controls.Add($launch)
$remove = New-Object Windows.Forms.Button
$remove.Text = 'Удалить';$remove.Location = New-Object Drawing.Point(270,205);$remove.Size = New-Object Drawing.Size(95,38)
$remove.Enabled = Test-Installed;$form.Controls.Add($remove)
$close = New-Object Windows.Forms.Button
$close.Text = 'Закрыть';$close.Location = New-Object Drawing.Point(410,205);$close.Size = New-Object Drawing.Size(105,38)
$form.Controls.Add($close)
$install.Add_Click({ try { $script:installDir = $pathBox.Text.Trim(); if ([string]::IsNullOrWhiteSpace($script:installDir)) { throw 'Укажите папку установки.' }; Install-App; [Windows.Forms.MessageBox]::Show('Установка завершена.','Zerde AI') | Out-Null; $launch.Enabled=$true;$remove.Enabled=$true;$install.Text='Обновить' } catch { [Windows.Forms.MessageBox]::Show($_.Exception.Message,'Zerde AI') | Out-Null } })
$launch.Add_Click({ $launcher = Join-Path $script:installDir 'Launch_ZerdeAI.vbs'; if (Test-Path -LiteralPath $launcher) { Start-Process -FilePath 'wscript.exe' -ArgumentList ('"' + $launcher + '"') -WorkingDirectory $script:installDir } else { [Windows.Forms.MessageBox]::Show('Файл запуска не найден. Выполните обновление программы.','Zerde AI') | Out-Null } })
$remove.Add_Click({ Start-Process -FilePath (Join-Path $PSHOME 'powershell.exe') -ArgumentList ('-NoProfile -ExecutionPolicy Bypass -File "' + (Join-Path $script:installDir 'scripts\uninstall.ps1') + '"') -WorkingDirectory $script:installDir; $form.Close() })
$close.Add_Click({ $form.Close() })
[void]$form.ShowDialog()
