$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$installDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$registryPath = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\ZerdeAI'
$menuDir = Join-Path ([Environment]::GetFolderPath('StartMenu')) 'Programs\ZerdeAI'
$desktopLink = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Zerde AI.lnk'
$dataDir = Join-Path $env:USERPROFILE 'EduTestSuite2'

function Confirm-Remove {
  $form = New-Object Windows.Forms.Form
  $form.Text = 'Удаление Zerde AI';$form.Size = New-Object Drawing.Size(500,240);$form.StartPosition='CenterScreen';$form.FormBorderStyle='FixedDialog';$form.MaximizeBox=$false
  $message = New-Object Windows.Forms.Label
  $message.Text='Будет удалена программа. Учётные записи, задания, попытки и настройки сохранятся.';$message.Location=New-Object Drawing.Point(20,20);$message.Size=New-Object Drawing.Size(440,50)
  $form.Controls.Add($message)
  $full = New-Object Windows.Forms.CheckBox
  $full.Text='Также полностью удалить мои данные и настройки';$full.Checked=$false;$full.Location=New-Object Drawing.Point(20,85);$full.Size=New-Object Drawing.Size(430,28)
  $form.Controls.Add($full)
  $path = New-Object Windows.Forms.Label
  $path.Text=$dataDir;$path.Location=New-Object Drawing.Point(20,115);$path.Size=New-Object Drawing.Size(440,22);$path.AutoEllipsis=$true
  $form.Controls.Add($path)
  $yes = New-Object Windows.Forms.Button;$yes.Text='Удалить программу';$yes.Location=New-Object Drawing.Point(210,155);$yes.Size=New-Object Drawing.Size(145,35)
  $no = New-Object Windows.Forms.Button;$no.Text='Отмена';$no.Location=New-Object Drawing.Point(365,155);$no.Size=New-Object Drawing.Size(95,35)
  $form.Controls.AddRange(@($yes,$no))
  $script:removeData=$false
  $yes.Add_Click({if($full.Checked){$answer=[Windows.Forms.MessageBox]::Show('Удалить без возможности восстановления папку с базой и настройками?','Полное удаление',[Windows.Forms.MessageBoxButtons]::YesNo,[Windows.Forms.MessageBoxIcon]::Warning);if($answer -ne [Windows.Forms.DialogResult]::Yes){return};$script:removeData=$true};$form.DialogResult=[Windows.Forms.DialogResult]::Yes;$form.Close()})
  $no.Add_Click({$form.DialogResult=[Windows.Forms.DialogResult]::Cancel;$form.Close()})
  return ($form.ShowDialog() -eq [Windows.Forms.DialogResult]::Yes)
}

if (-not (Confirm-Remove)) { exit 0 }
foreach ($proc in Get-Process javaw -ErrorAction SilentlyContinue) {
  try {
    if ($proc.Path -and $proc.Path.StartsWith([IO.Path]::GetFullPath($installDir).TrimEnd('\') + '\',[StringComparison]::OrdinalIgnoreCase)) {
      [Windows.Forms.MessageBox]::Show('Сначала закройте Zerde AI и повторите удаление.','Zerde AI') | Out-Null
      exit 1
    }
  } catch {}
}
if ($script:removeData) {
  $expected = [IO.Path]::GetFullPath((Join-Path $env:USERPROFILE 'EduTestSuite2'))
  $actual = [IO.Path]::GetFullPath($dataDir)
  if (-not [string]::Equals($expected,$actual,[StringComparison]::OrdinalIgnoreCase) -or $actual -eq [IO.Path]::GetPathRoot($actual)) { throw 'Путь данных не прошёл проверку. Ничего не удалено.' }
}
Remove-Item -LiteralPath $registryPath -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $menuDir -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $desktopLink -Force -ErrorAction SilentlyContinue
if ($script:removeData -and (Test-Path -LiteralPath $dataDir)) { Remove-Item -LiteralPath $dataDir -Recurse -Force }
if (Test-Path -LiteralPath $installDir) { Remove-Item -LiteralPath $installDir -Recurse -Force }
[Windows.Forms.MessageBox]::Show('Zerde AI удалена.','Удаление завершено') | Out-Null
