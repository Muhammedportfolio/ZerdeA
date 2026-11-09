$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root 'EduTestSuite.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'EduTestSuite.jar not found.' }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$temp = Join-Path $root 'EduTestSuite.web-update.tmp.jar'
Copy-Item -LiteralPath $jar -Destination $temp -Force
try {
  $archive = [IO.Compression.ZipFile]::Open($temp,[IO.Compression.ZipArchiveMode]::Update)
  try {
    foreach ($file in @('index.html','app.js','style.css')) {
      $entryName = 'web/' + $file
      $old = $archive.GetEntry($entryName)
      if ($old) { $old.Delete() }
      [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,(Join-Path $root ('web\' + $file)),$entryName,[IO.Compression.CompressionLevel]::Optimal)
    }
  } finally { $archive.Dispose() }
  Move-Item -LiteralPath $temp -Destination $jar -Force
} catch {
  Remove-Item -LiteralPath $temp -Force -ErrorAction SilentlyContinue
  throw
}
Write-Host 'Updated the three web resources in EduTestSuite.jar.'
