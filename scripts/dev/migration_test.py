#!/usr/bin/env python3
"""
Migration test - run before every release.

Starts the local FTB Chunks + Lightman's Currency dev server (scripts/run-server.ps1,
i.e. the current source) on REAL server data and checks that nothing is lost:
FTB loads every team, no claim disappears, no LC bank account disappears or changes
balance, no hook data of an existing team is purged, and none of our guards fired.

Fixtures are tarballs of a server world's ftbteams/, ftbchunks/ and data/*.dat files
in migration-fixtures/ (gitignored - they contain player names). The dev world's own
files are moved aside and restored afterwards.

    python scripts/dev/migration_test.py                  # all fixtures
    python scripts/dev/migration_test.py production_2026-10-04
"""
import argparse
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from nbt_read import load, uuid_from_ints  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / 'migration-fixtures'
WORLD = ROOT / 'run' / 'world'
LOG = ROOT / 'run' / 'logs' / 'latest.log'
RCON_PORT = '25575'

# Any of these in the log fails the test.
FORBIDDEN = [
    r"can't list directory",                 # FTB Teams aborted loading (4.0.0 incident)
    r'failed - fell back to the safe default',  # one of our guards caught an exception
    r'failed again:',
    r'Removed duplicate LC team',
    r'Removed LC team',
    r'refusing to purge',
    r'Could not read hook data entry',
    r'Exception in server tick loop',
    r'Crash report',
]
# Reported, but expected after damage (e.g. the broken 4.0.0 fixture).
NOTEWORTHY = [r'Repaired hook data', r'Reconcile: purged', r'Re-linked existing LC team',
              r'Kept unlinked LC team', r'Repaired FTB party', r"Removed this mod's data", r'Chunk data check', r"left this mod's knowledge", r'No Lightman.s Currency team account for party',
              r'is linked to \d+ parties', r'kept it with its own party', r"can't be told whose it is", r'Created LC team', r'LC team \d+ was linked to more than one party']


def world_files(world):
    files = [world / 'ftbteams', world / 'ftbchunks']
    files += list((world / 'data').glob('lc_ftb_hook_team_accounts.dat'))
    files += list((world / 'data').glob('lightmanscurrency_*.dat'))
    return [f for f in files if f.exists()]


NIL_UUID = '00000000-0000-0000-0000-000000000000'


def repairable_ownerless(snbt):
    owner = re.search(r'^\s*owner: "([0-9a-f-]+)"', snbt, re.M)
    return owner is not None and owner.group(1) == NIL_UUID and len(re.findall(r'[0-9a-f-]{36}: "owner"', snbt)) == 1


def owned_chunks(world):
    """Chunk keys (ChunkPosKey format) per FTB team id, from FTB Chunks' own files."""
    owned = {}
    for f in (world / 'ftbchunks').glob('*.snbt'):
        for dim, body in re.findall(r'"([a-z0-9_:]+)": \[(.*?)\]', f.read_text(encoding='utf-8'), re.S):
            for x, z in re.findall(r'x: (-?\d+), z: (-?\d+)', body):
                owned.setdefault(f.stem, set()).add('%s#%s#%s' % (dim, x, z))
    return owned


def stale_chunk_data(world, hook_entries):
    """Per-chunk data of this mod for chunks the team doesn't own (must be none after a start)."""
    owned = owned_chunks(world)
    known = {f.stem for f in (world / 'ftbteams').glob('*/*.snbt')}
    stale = {}
    for entry in hook_entries:
        team = uuid_from_ints(entry['TeamId'])
        if team not in known:
            continue
        keys = set()
        for name in ('UnsettledChunks', 'FreshlyClaimedChunks', 'PendingForceLoads', 'PendingForceUnloads'):
            keys |= set(entry.get(name, []))
        for name in ('ChunkRegions', 'ChunkOwnership', 'PendingRegionAssignments'):
            keys |= set(entry.get(name, {}).keys())
        keys -= owned.get(team, set())
        if keys:
            stale[team] = keys
    return stale


def snapshot(world):
    snap = {'players': len(list((world / 'ftbteams' / 'player').glob('*.snbt'))),
            'parties': len(list((world / 'ftbteams' / 'party').glob('*.snbt'))),
            'server_teams': len(list((world / 'ftbteams' / 'server').glob('*.snbt'))),
            'claims': sum(len(re.findall(r'\bx: -?\d+', f.read_text(encoding='utf-8')))
                          for f in (world / 'ftbchunks').glob('*.snbt')),
            'team_files': {f.stem for f in (world / 'ftbteams').glob('*/*.snbt')},
            'hook_teams': {}, 'lc_teams': {}, 'lc_nil_owners': set(),
            # Parties saved without owner although their rank list names exactly one (4.0.0 damage) - must be repaired.
            'repairable_ownerless': {f.stem for f in (world / 'ftbteams' / 'party').glob('*.snbt')
                                     if repairable_ownerless(f.read_text(encoding='utf-8'))}}
    hook = world / 'data' / 'lc_ftb_hook_team_accounts.dat'
    snap['stale_chunk_data'] = {}
    if hook.exists():
        entries = load(hook)['data'].get('Teams', [])
        for entry in entries:
            snap['hook_teams'][uuid_from_ints(entry['TeamId'])] = entry.get('LcTeamId', -1)
        snap['stale_chunk_data'] = stale_chunk_data(world, entries)
    lc = world / 'data' / 'lightmanscurrency_team_data.dat'
    if lc.exists():
        for team in load(lc)['data'].get('Teams', []):
            snap['lc_teams'][team['ID']] = repr(team.get('BankAccount', {}).get('CoinStorage'))
            if team.get('Owner', {}).get('id') == [0, 0, 0, 0]:
                snap['lc_nil_owners'].add(team['ID'])
    return snap


def wait_until_started(proc, started):
    """None once the server is up (plus time for the startup checks), else why it isn't."""
    while time.time() - started < 600:
        if proc.poll() is not None:
            return 'server exited during startup'
        if LOG.exists() and LOG.stat().st_mtime > started:
            text = LOG.read_text(encoding='utf-8', errors='replace')
            if 'Crash report' in text:
                return None  # reported through the FORBIDDEN log check
            if re.search(r'Done \(', text):
                time.sleep(45)  # startup reconcile + deferred LC setup
                return None
        time.sleep(2)
    return 'server did not start within 10 minutes'


def run_server():
    """Starts the dev server, waits for it, and always stops it via RCON (never a hard kill)."""
    started = time.time()
    with open(ROOT / 'run' / 'migration-test-server.out', 'w', encoding='utf-8', errors='replace') as log_out:
        proc = subprocess.Popen(['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
                                 str(ROOT / 'scripts' / 'run-server.ps1')], cwd=ROOT, stdout=log_out, stderr=subprocess.STDOUT)
        try:
            error = wait_until_started(proc, started)
        finally:
            subprocess.run([sys.executable, str(ROOT / 'scripts' / 'dev' / 'rcon.py'), '--port', RCON_PORT, 'stop'],
                           cwd=ROOT, capture_output=True)
        try:
            proc.wait(timeout=180)
        except subprocess.TimeoutExpired:
            return 'server did not stop within 3 minutes - stop it via RCON before re-running'
    return error


def team_count(snap):
    return snap['players'] + snap['parties'] + snap['server_teams']


def check(name, before, after, log_text):
    problems, notes = [], []
    loaded = re.search(r'loaded team data: (\d+) known players, (\d+) teams total', log_text)
    if not loaded:
        problems.append('FTB Teams never reported loading its teams')
    elif (int(loaded.group(1)), int(loaded.group(2))) != (before['players'], team_count(before)):
        problems.append('FTB loaded %s players / %s teams, files hold %s / %s' % (
            loaded.group(1), loaded.group(2), before['players'], team_count(before)))
    for pattern in FORBIDDEN:
        for line in re.findall(r'^.*%s.*$' % pattern, log_text, re.M)[:5]:
            problems.append('log: ' + line.strip()[:220])
    for pattern in NOTEWORTHY:
        count = len(re.findall(pattern, log_text))
        if count:
            notes.append('%dx "%s"' % (count, pattern))
    if after['claims'] != before['claims']:
        problems.append('claims: %d before, %d after' % (before['claims'], after['claims']))
    if after['team_files'] != before['team_files']:
        problems.append('FTB team files changed: %s' % sorted(before['team_files'] ^ after['team_files']))
    for lc_id, coins in before['lc_teams'].items():
        if lc_id not in after['lc_teams']:
            problems.append('LC team %s disappeared (bank %s)' % (lc_id, coins[:80]))
        elif after['lc_teams'][lc_id] != coins:
            problems.append('LC team %s balance changed' % lc_id)
    if after['repairable_ownerless']:
        problems.append('parties still without owner although their ranks name one: %s' % sorted(after['repairable_ownerless']))
    if after['lc_nil_owners'] - before['lc_nil_owners']:
        problems.append('LC teams newly given the nil owner: %s' % sorted(after['lc_nil_owners'] - before['lc_nil_owners']))
    stale_before = sum(len(v) for v in before['stale_chunk_data'].values())
    if stale_before:
        notes.append('%d chunk(s) with data of a team that does not own them before the start' % stale_before)
    for team, keys in after['stale_chunk_data'].items():
        problems.append('team %s still has data for %d chunk(s) it does not own: %s' % (team, len(keys), sorted(keys)[:5]))
    for team_id in before['hook_teams']:
        if team_id not in after['hook_teams'] and team_id in before['team_files']:
            problems.append('hook data of existing team %s was purged' % team_id)
    print('\n=== %s: %s' % (name, 'PASS' if not problems else 'FAIL'))
    print('    teams %d players / %d parties, %d claims, %d hook entries -> %d, %d LC teams -> %d' % (
        before['players'], before['parties'], before['claims'], len(before['hook_teams']),
        len(after['hook_teams']), len(before['lc_teams']), len(after['lc_teams'])))
    for note in notes:
        print('    note: ' + note)
    for problem in problems:
        print('    FAIL: ' + problem)
    return not problems


def run_fixture(tarball):
    name = tarball.name.replace('.tar.gz', '')
    aside = Path(tempfile.mkdtemp(prefix='lcfh-devworld-'))
    work = Path(tempfile.mkdtemp(prefix='lcfh-fixture-'))
    for f in world_files(WORLD):
        shutil.move(str(f), aside / f.relative_to(WORLD).as_posix().replace('/', '__'))
    try:
        with tarfile.open(tarball) as tar:
            tar.extractall(work, filter="data")
        source = work / 'world' if (work / 'world').is_dir() else work
        for f in world_files(source):
            target = WORLD / f.relative_to(source)
            target.parent.mkdir(parents=True, exist_ok=True)
            (shutil.copytree if f.is_dir() else shutil.copy2)(f, target)
        before = snapshot(WORLD)
        error = run_server()
        log_text = LOG.read_text(encoding='utf-8', errors='replace') if LOG.exists() else ''
        if error:
            print('\n=== %s: FAIL - %s' % (name, error))
            return False
        return check(name, before, snapshot(WORLD), log_text)
    finally:
        for f in world_files(WORLD):
            shutil.rmtree(f) if f.is_dir() else f.unlink()
        for f in aside.iterdir():
            target = WORLD / f.name.replace('__', '/')
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(f), target)
        shutil.rmtree(aside, ignore_errors=True)
        shutil.rmtree(work, ignore_errors=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('fixtures', nargs='*', help='fixture names (default: all in migration-fixtures/)')
    args = parser.parse_args()
    tarballs = sorted(FIXTURES.glob('*.tar.gz'))
    if args.fixtures:
        tarballs = [t for t in tarballs if t.name.replace('.tar.gz', '') in args.fixtures]
    if not tarballs:
        sys.exit('no fixtures found in %s' % FIXTURES)
    results = [run_fixture(t) for t in tarballs]
    print('\n%d/%d fixtures passed' % (sum(results), len(results)))
    sys.exit(0 if all(results) else 1)


if __name__ == '__main__':
    main()
