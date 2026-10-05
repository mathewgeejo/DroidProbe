param([Parameter(Mandatory=$true)][string]$ModelPath, [string]$Adb = 'adb')
$ErrorActionPreference = 'Stop'
$model = Get-Item -LiteralPath $ModelPath
if ($model.Extension -ne '.litertlm') { throw 'A publisher-supported .litertlm artifact is required' }
$hash = (Get-FileHash -LiteralPath $model.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
$hashFile = Join-Path $env:TEMP ('droidprobe-model-' + [guid]::NewGuid().ToString('N') + '.sha256')
Set-Content -LiteralPath $hashFile -Value $hash -Encoding ascii
& $Adb push $model.FullName /data/local/tmp/droidprobe-model.litertlm
if ($LASTEXITCODE -ne 0) { throw 'Model push failed' }
& $Adb push $hashFile /data/local/tmp/droidprobe-model.sha256
if ($LASTEXITCODE -ne 0) { throw 'Checksum push failed' }
& $Adb shell run-as dev.droidprobe.runner mkdir -p files/models
& $Adb shell run-as dev.droidprobe.runner cp /data/local/tmp/droidprobe-model.litertlm files/models/model.litertlm
if ($LASTEXITCODE -ne 0) { throw 'Model installation failed; runner must be installed and debuggable' }
& $Adb shell run-as dev.droidprobe.runner cp /data/local/tmp/droidprobe-model.sha256 files/models/model.sha256
if ($LASTEXITCODE -ne 0) { throw 'Checksum installation failed' }
Remove-Item -LiteralPath $hashFile
Write-Output ('Installed model SHA-256: ' + $hash)
