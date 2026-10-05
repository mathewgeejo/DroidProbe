param([string]$Adb = 'adb', [int[]]$Seeds = @(1,2,3), [int]$Budget = 40, [switch]$IncludeLocal)
$ErrorActionPreference = 'Stop'
if ($Budget -lt 1 -or $Budget -gt 500) { throw 'Budget must be 1–500' }
$repo = Split-Path -Parent $PSScriptRoot
$output = Join-Path $repo '.local/evaluation'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$planners = @('random','graph')
if ($IncludeLocal) { $planners += 'local' }
foreach ($planner in $planners) {
    foreach ($seed in $Seeds) {
        $result = & $Adb shell am instrument -w -e class dev.droidprobe.runner.ExplorationTest -e planner $planner -e seed $seed -e budget $Budget -e mode FAULTY dev.droidprobe.runner.test/androidx.test.runner.AndroidJUnitRunner 2>&1
        $result | Set-Content -LiteralPath (Join-Path $output ($planner + '-' + $seed + '.log')) -Encoding utf8
        if (($result -join "`n") -notmatch 'OK \(1 test\)') { throw ('Instrumentation failed for ' + $planner + ' seed ' + $seed + '; inspect saved log') }
    }
}
python (Join-Path $PSScriptRoot 'device.py') --adb $Adb pull-runs --output $output
if ($LASTEXITCODE -ne 0) { throw 'Result transfer failed' }
Write-Output 'Raw evaluation.json includes requested and actual planners. Compare only runs with identical build/config/fixtures/faults/invariants. Local fallback is not an AI result.'
