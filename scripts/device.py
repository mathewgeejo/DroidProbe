"""ADB artifact transfers without PowerShell's text redirection corrupting binary ZIP/PNG data."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--adb', default='adb')
parser.add_argument('command', choices=['pull-runs', 'pull-latest-export'])
parser.add_argument('--output', default='.local/device-results')
args = parser.parse_args()
root = Path(args.output)
root.mkdir(parents=True, exist_ok=True)

def adb(*command):
    result = subprocess.run([args.adb, *command], check=True, capture_output=True)
    return result.stdout

names = adb('shell', 'run-as', 'dev.droidprobe.runner', 'ls', 'files/runs').decode().split()
reports = []
for name in names:
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,100}', name):
        continue
    try:
        raw = adb('exec-out', 'run-as', 'dev.droidprobe.runner', 'cat', f'files/runs/{name}/run.json')
        report = json.loads(raw)
        reports.append(report)
        folder = root / name
        folder.mkdir(exist_ok=True)
        (folder / 'run.json').write_bytes(raw)
    except (subprocess.CalledProcessError, json.JSONDecodeError):
        continue

if args.command == 'pull-latest-export':
    candidates = [r for r in reports if r.get('exportedBundle')]
    if not candidates:
        raise SystemExit('No stored export; run DemoTest first.')
    report = sorted(candidates, key=lambda r: int(re.search(r'(\d{13})', r['runId']).group(1)) if re.search(r'(\d{13})', r['runId']) else 0)[-1]
    bundle = root / report['runId'] / 'regression.zip'
    bundle.write_bytes(adb('exec-out', 'run-as', 'dev.droidprobe.runner', 'cat', f"files/runs/{report['runId']}/regression.zip"))
    extracted = bundle.parent / 'export'
    extracted.mkdir(exist_ok=True)
    with zipfile.ZipFile(bundle) as archive:
        for member in archive.infolist():
            target = (extracted / member.filename).resolve()
            if not target.is_relative_to(extracted.resolve()):
                raise ValueError('Bundle contains an invalid file path')
        archive.extractall(extracted)
    print(extracted.resolve())
else:
    rows = []
    for report in reports:
        findings = report['findings']
        replays = report.get('replays', [])
        minimization = report.get('minimization')
        row = {
            'runId': report['runId'], 'requestedPlanner': report['config']['planner'], 'actualPlanner': report['plannerIdentity'],
            'seed': report['config']['seed'], 'budget': report['config']['actionBudget'], 'mode': report['config']['mode'],
            'confirmedUniqueBugs': len({f['fingerprint'] for f in findings}),
            'actionsUntilFirstBug': sum(len(episode) for episode in report.get('previousEpisodes', [])) + findings[0]['actionIndex'] + 1 if findings else None,
            'actions': report.get('totalExecutedActions', len(report['records'])),
            'observedStates': len(report['graph']['nodes']), 'transitions': len(report['graph']['transitions']),
            'reproductionSuccesses': sum(r['status'] == 'REPRODUCED' for r in replays if r['mode'] == 'FAULTY'),
            'reproductionAttempts': sum(r['mode'] == 'FAULTY' for r in replays),
            'correctedAssertionPasses': sum(r['status'] == 'FAILURE_NOT_OBSERVED' and r['assertion']['passed'] is True for r in replays if r.get('assertion') and r['mode'] == 'CORRECTED'),
            'falsePositiveReports': None,
            'falsePositiveAdjudication': 'Not independently adjudicated; non-reproduction is recorded separately.',
            'failureNotObservedOnFaultyReplay': sum(r['status'] == 'FAILURE_NOT_OBSERVED' for r in replays if r['mode'] == 'FAULTY'),
            'invalidReplays': sum(r['status'] == 'INVALID_PRECONDITION' for r in replays),
            'originalLength': minimization['originalLength'] if minimization else None,
            'minimizedLength': len(minimization['scenario']['actions']) if minimization else None,
            'model': report.get('modelIdentity'), 'modelStatus': report['modelStatus'],
            'plannerStats': report['plannerStats'], 'device': report['device'], 'elapsedMs': report['elapsedMs'],
        }
        if row['reproductionAttempts']:
            row['reproductionRatio'] = row['reproductionSuccesses'] / row['reproductionAttempts']
        rows.append(row)
    path = root / 'evaluation.json'
    path.write_text(json.dumps(rows, indent=2), encoding='utf-8')
    print(f'{len(rows)} stored runs collected to {path.resolve()}')
