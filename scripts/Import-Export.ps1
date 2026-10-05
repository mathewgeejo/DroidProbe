param([Parameter(Mandatory=$true)][string]$BundleDirectory)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$source = (Resolve-Path -LiteralPath $BundleDirectory).Path
$manifest = Get-Content -LiteralPath (Join-Path $source 'scenario.json') -Raw | ConvertFrom-Json
if ($manifest.schemaVersion -ne 1 -or $manifest.harnessVersion -ne '0.1.0') { throw 'Unsupported replay harness/scenario' }
$kotlin = Join-Path $repo 'runner/src/exportedTest/kotlin/dev/droidprobe/runner'
$assets = Join-Path $repo 'runner/src/exportedTest/assets'
New-Item -ItemType Directory -Force -Path $kotlin,$assets | Out-Null
Copy-Item -LiteralPath (Join-Path $source 'DroidProbeExportedRegression.kt') -Destination $kotlin
Copy-Item -LiteralPath (Join-Path $source 'scenario.json') -Destination $assets
Write-Output 'Imported exported test and scenario. Build runner:assembleDebugAndroidTest, then run DroidProbeExportedRegression.'
