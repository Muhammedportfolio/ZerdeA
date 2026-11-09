param([switch]$Silent,[string]$InstallDir)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName PresentationFramework
Add-Type -AssemblyName System.Windows.Forms
$script:root = Split-Path -Parent $MyInvocation.MyCommand.Path
$script:zip = Join-Path $script:root 'payload.zip'
if (-not (Test-Path $script:zip)) { [System.Windows.MessageBox]::Show('Не найден payload.zip. Поместите файл рядом с установщиком.', 'Zerde AI'); exit 2 }
if (-not $InstallDir) { $InstallDir = Join-Path $env:LOCALAPPDATA 'Programs\ZerdeAI' }
$script:selected=$InstallDir
$window = New-Object System.Windows.Window
$window.Title='Zerde AI — мастер установки'
$window.Width=820; $window.Height=540; $window.WindowStartupLocation='CenterScreen'
$window.ResizeMode='NoResize'; $window.Background=[System.Windows.Media.Brushes]::White
$main=New-Object System.Windows.Controls.Grid
$main.ColumnDefinitions.Add((New-Object System.Windows.Controls.ColumnDefinition -Property @{Width='225'}))
$main.ColumnDefinitions.Add((New-Object System.Windows.Controls.ColumnDefinition -Property @{Width='*'}))
$sidebar=New-Object System.Windows.Controls.Border
$sidebar.Background=[System.Windows.Media.BrushConverter]::new().ConvertFrom('#103C52')
[System.Windows.Controls.Grid]::SetColumn($sidebar,0)
$sideStack=New-Object System.Windows.Controls.StackPanel
$sideStack.Margin='25,52,18,0'
$brand=New-Object System.Windows.Controls.TextBlock
$brand.Text='ZERDE AI';$brand.FontSize=29;$brand.FontWeight='Bold';$brand.Foreground=[System.Windows.Media.Brushes]::White
$sideStack.Children.Add($brand) | Out-Null
$tag=New-Object System.Windows.Controls.TextBlock
$tag.Text='Desktop + Online';$tag.FontSize=14;$tag.Foreground=[System.Windows.Media.BrushConverter]::new().ConvertFrom('#87E9C5');$tag.Margin='0,8,0,35'
$sideStack.Children.Add($tag) | Out-Null
foreach($s in @('01  Приветствие','02  Папка установки','03  Установка','04  Готово')) {
 $t=New-Object System.Windows.Controls.TextBlock;$t.Text=$s;$t.FontSize=13;$t.Margin='0,0,0,20';$t.Foreground=[System.Windows.Media.BrushConverter]::new().ConvertFrom('#D8E9EF')
 $sideStack.Children.Add($t)|Out-Null
}
$sidebar.Child=$sideStack;$main.Children.Add($sidebar)|Out-Null
$content=New-Object System.Windows.Controls.Grid
[System.Windows.Controls.Grid]::SetColumn($content,1)
$content.RowDefinitions.Add((New-Object System.Windows.Controls.RowDefinition -Property @{Height='*'}))
$content.RowDefinitions.Add((New-Object System.Windows.Controls.RowDefinition -Property @{Height='76'}))
$body=New-Object System.Windows.Controls.StackPanel;$body.Margin='35,52,34,15'
$content.Children.Add($body)|Out-Null
$footer=New-Object System.Windows.Controls.DockPanel
$footer.Margin='34,12,34,20';[System.Windows.Controls.Grid]::SetRow($footer,1)
$buttons=New-Object System.Windows.Controls.StackPanel;$buttons.Orientation='Horizontal';$buttons.HorizontalAlignment='Right'
[System.Windows.Controls.DockPanel]::SetDock($buttons,'Right');$footer.Children.Add($buttons)|Out-Null
$back=New-Object System.Windows.Controls.Button;$back.Content='Назад';$back.Width=100;$back.Height=36;$back.Margin='0,0,10,0'
$next=New-Object System.Windows.Controls.Button;$next.Content='Далее';$next.Width=125;$next.Height=36;$next.Background=[System.Windows.Media.BrushConverter]::new().ConvertFrom('#158366');$next.Foreground=[System.Windows.Media.Brushes]::White;$next.BorderThickness='0'
$cancel=New-Object System.Windows.Controls.Button;$cancel.Content='Отмена';$cancel.Width=90;$cancel.Height=36;$cancel.Margin='0,0,10,0'
$buttons.Children.Add($back)|Out-Null;$buttons.Children.Add($cancel)|Out-Null;$buttons.Children.Add($next)|Out-Null
$content.Children.Add($footer)|Out-Null;$main.Children.Add($content)|Out-Null
$window.Content=$main
$script:page=0
function Add-Text([string]$s,[int]$font,[string]$color,[string]$margin) {
 $t=New-Object System.Windows.Controls.TextBlock;$t.Text=$s;$t.TextWrapping='Wrap';$t.FontSize=$font;$t.Margin=$margin
 $t.Foreground=[System.Windows.Media.BrushConverter]::new().ConvertFrom($color);$body.Children.Add($t)|Out-Null;return $t
}
function Show-Page([int]$n) {
 $script:page=$n;$body.Children.Clear();$back.IsEnabled=$n -gt 0 -and $n -lt 2;$cancel.Visibility=if($n -eq 3){'Collapsed'}else{'Visible'}
 if($n -eq 0){
  Add-Text 'Установка Zerde AI' 28 '#103C52' '0,0,0,24'|Out-Null
  Add-Text 'Приложение для преподавателей, студентов и администраторов.' 16 '#334A58' '0,0,0,20'|Out-Null
  Add-Text 'Программа запускается в собственном окне Windows. Сайт для телефонов и других компьютеров доступен по ссылке через вкладку «Сайт онлайн», пока программа запущена.' 14 '#334A58' '0,0,0,22'|Out-Null
  Add-Text 'Установка только для текущего пользователя. Права администратора Windows не нужны.' 13 '#168366' '0,0,0,0'|Out-Null
  $next.Content='Далее'
 } elseif($n -eq 1){
  Add-Text 'Выберите папку установки' 26 '#103C52' '0,0,0,20'|Out-Null
  Add-Text 'В эту папку будут скопированы файлы программы. Данные тестов и аккаунтов в профиле пользователя не удаляются.' 14 '#334A58' '0,0,0,16'|Out-Null
  $script:pathBox=New-Object System.Windows.Controls.TextBox;$script:pathBox.Text=$script:selected;$script:pathBox.FontSize=13;$script:pathBox.Padding='8';$script:pathBox.Margin='0,0,0,12';$body.Children.Add($script:pathBox)|Out-Null
  $choose=New-Object System.Windows.Controls.Button;$choose.Content='Обзор...';$choose.Width=100;$choose.Height=32;$choose.HorizontalAlignment='Left';$body.Children.Add($choose)|Out-Null
  $choose.Add_Click({$dialog=New-Object System.Windows.Forms.FolderBrowserDialog;$dialog.Description='Папка для Zerde AI';if($dialog.ShowDialog() -eq 'OK'){$script:pathBox.Text=Join-Path $dialog.SelectedPath 'ZerdeAI'}})
  Add-Text 'Рекомендуется: %LOCALAPPDATA%\Programs\ZerdeAI' 12 '#6B7C86' '0,18,0,0'|Out-Null
  $next.Content='Установить'
 } elseif($n -eq 2){
  Add-Text 'Идёт установка…' 26 '#103C52' '0,0,0,20'|Out-Null
  Add-Text 'Распаковка приложения и создание ярлыка.' 14 '#334A58' '0,0,0,20'|Out-Null
  $script:progress=New-Object System.Windows.Controls.ProgressBar;$script:progress.Height=18;$script:progress.IsIndeterminate=$true;$body.Children.Add($script:progress)|Out-Null
  $next.IsEnabled=$false;$back.IsEnabled=$false;$cancel.IsEnabled=$false
 } else {
  Add-Text 'Zerde AI установлена' 27 '#103C52' '0,0,0,20'|Out-Null
  Add-Text 'Запускай программу с ярлыка Zerde AI на рабочем столе. Вход администратора находится прямо в окне приложения.' 15 '#334A58' '0,0,0,25'|Out-Null
  Add-Text ('Папка: '+$script:selected) 12 '#6B7C86' '0,0,0,18'|Out-Null
  Add-Text 'Сначала проверь локальный запуск. После этого открой вкладку «Сайт онлайн» и проверь ссылку на другом устройстве.' 13 '#334A58' '0,0,0,0'|Out-Null
  $script:runAfter=New-Object System.Windows.Controls.CheckBox
  $script:runAfter.Content='Запустить Zerde AI после установки'
  $script:runAfter.IsChecked=$true
  $script:runAfter.Margin='0,20,0,0'
  $body.Children.Add($script:runAfter)|Out-Null
  $next.IsEnabled=$true;$next.Content='Готово'
 }
}
function Do-Install {
 $script:selected=$script:pathBox.Text.Trim()
 if([string]::IsNullOrWhiteSpace($script:selected)){[System.Windows.MessageBox]::Show('Укажи папку установки.');return}
 if($script:selected -notmatch '^[A-Za-z]:\\'){[System.Windows.MessageBox]::Show('Укажи полный путь Windows, например C:\Users\...');return}
 Show-Page 2
 $window.Dispatcher.Invoke([Action]{},[System.Windows.Threading.DispatcherPriority]::Render)
 try {
  # Explicitly refuse the current ZIP if an old Zerde process still uses its executables.
  $target=$script:selected
  $temp=Join-Path $env:TEMP ('ZerdeAI_Install_'+[guid]::NewGuid().ToString('N'))
  New-Item -ItemType Directory -Path $temp -Force|Out-Null
  Expand-Archive -LiteralPath $script:zip -DestinationPath $temp -Force
  $src=Join-Path $temp 'ZerdeAI'
  if(-not(Test-Path (Join-Path $src 'EduTestSuite.jar'))){throw 'Архив программы повреждён: EduTestSuite.jar не найден.'}
  if(-not(Test-Path (Join-Path $src 'Launch_ZerdeAI.vbs'))){throw 'Архив программы повреждён: файл оконного запуска не найден.'}
  New-Item -ItemType Directory -Path $target -Force|Out-Null
  Copy-Item -Path (Join-Path $src '*') -Destination $target -Recurse -Force
  Remove-Item -LiteralPath (Join-Path $target 'Uninstall.exe') -Force -ErrorAction SilentlyContinue
  $ws=New-Object -ComObject WScript.Shell
  $desktop=[Environment]::GetFolderPath('Desktop')
  $startMenu=Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs'
  foreach($path in @((Join-Path $desktop 'Zerde AI.lnk'),(Join-Path $startMenu 'Zerde AI.lnk'))){
    $lnk=$ws.CreateShortcut($path);$lnk.TargetPath=Join-Path $target 'Launch_ZerdeAI.vbs';$lnk.WorkingDirectory=$target;$lnk.Description='Zerde AI Desktop + Online';$lnk.IconLocation=Join-Path $target 'assets\icon.ico';$lnk.Save()
  }
  # The previous distribution may contain an old uninstaller. It is intentionally not installed here.
  Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
  Show-Page 3
 } catch {
  $next.IsEnabled=$true;$cancel.IsEnabled=$true;$back.IsEnabled=$true
  Show-Page 1
  [System.Windows.MessageBox]::Show(('Установка не завершена: '+$_.Exception.Message),'Zerde AI')|Out-Null
 }
}
$back.Add_Click({Show-Page ($script:page-1)})
$cancel.Add_Click({$window.Close()})
$next.Add_Click({if($script:page -eq 0){Show-Page 1}elseif($script:page -eq 1){Do-Install}elseif($script:page -eq 3){if($script:runAfter.IsChecked){Start-Process -FilePath 'wscript.exe' -ArgumentList ('"'+(Join-Path $script:selected 'Launch_ZerdeAI.vbs')+'"') -WorkingDirectory $script:selected};$window.Close()}})
Show-Page 0
$null=$window.ShowDialog()
