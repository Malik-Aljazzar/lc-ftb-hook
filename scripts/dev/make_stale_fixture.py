#!/usr/bin/env python3
"""
Builds migration-fixtures/stale_chunk_data.tar.gz: a real world state (a
production fixture) plus per-chunk data of this mod for chunks the team does
NOT own - the state Open Parties and Claims' claim expiry and its admin
clear/transfer commands leave behind, since they bypass this mod's claim
listener. The migration test then checks that the server drops exactly that
data and nothing else.

    python scripts/dev/make_stale_fixture.py [source-fixture-name]
"""
import re
import shutil
import sys
import tarfile
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from nbt_read import load_typed, save_typed, uuid_from_ints  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / 'migration-fixtures'
HOOK = 'data/lc_ftb_hook_team_accounts.dat'
STRING, LIST, COMPOUND, LONG, INT_ARRAY = 8, 9, 10, 4, 11


def owned_chunks(world):
    owned = {}
    for f in (world / 'ftbchunks').glob('*.snbt'):
        text = f.read_text(encoding='utf-8')
        for dim, body in re.findall(r'"([a-z0-9_:]+)": \[(.*?)\]', text, re.S):
            for x, z in re.findall(r'x: (-?\d+), z: (-?\d+)', body):
                owned.setdefault(f.stem, set()).add('%s#%s#%s' % (dim, x, z))  # ChunkPosKey format
    return owned


def add_to_string_list(entry, name, key):
    if name not in entry:
        entry[name] = (LIST, (STRING, []))
    entry[name][1][1].append(key)


def main():
    source = sys.argv[1] if len(sys.argv) > 1 else 'production_2026-10-08'
    work = Path(tempfile.mkdtemp(prefix='lcfh-stale-'))
    with tarfile.open(FIXTURES / (source + '.tar.gz')) as tar:
        tar.extractall(work, filter='data')
    world = work / 'world' if (work / 'world').is_dir() else work
    owned = owned_chunks(world)

    tree = load_typed(world / HOOK)
    teams = tree[1][1]['data'][1]['Teams'][1][1]
    with_regions = [e for e in teams if 'Regions' in e and uuid_from_ints(e['TeamId'][1]) in owned]
    victim = with_regions[0]
    other = next(e for e in teams if e is not victim and uuid_from_ints(e['TeamId'][1]) in owned)
    victim_id, other_id = uuid_from_ints(victim['TeamId'][1]), uuid_from_ints(other['TeamId'][1])

    # Chunks nobody owns (claims that expired / were cleared in the claim mod).
    gone = ['minecraft:overworld#%d#%d' % (900000 + i, 900000) for i in range(4)]
    # A chunk that now belongs to another team (admin transfer).
    transferred = sorted(owned[other_id])[0]

    add_to_string_list(victim, 'UnsettledChunks', gone[0])
    add_to_string_list(victim, 'FreshlyClaimedChunks', gone[0])
    add_to_string_list(victim, 'PendingForceLoads', gone[1])
    add_to_string_list(victim, 'FreshlyClaimedChunks', transferred)
    regions = victim['Regions'][1][1]
    region = next((r for r in regions if not r.get('IsDefault', (1, 0))[1]), regions[0])
    if 'ChunkRegions' not in victim:
        victim['ChunkRegions'] = (COMPOUND, {})
    victim['ChunkRegions'][1][gone[2]] = (INT_ARRAY, region['Id'][1])
    if 'ChunkOwnership' not in victim:
        victim['ChunkOwnership'] = (COMPOUND, {})
    victim['ChunkOwnership'][1][gone[3]] = (COMPOUND, {'Price': (LONG, 12345)})
    save_typed(world / HOOK, tree)

    target = FIXTURES / 'stale_chunk_data.tar.gz'
    with tarfile.open(target, 'w:gz') as tar:
        for name in ['ftbteams', 'ftbchunks', 'data']:
            tar.add(world / name, arcname=name)
    shutil.rmtree(work, ignore_errors=True)
    print('wrote %s: team %s got data for %d unowned chunk(s) + 1 chunk owned by team %s'
          % (target.name, victim_id, len(gone), other_id))


if __name__ == '__main__':
    main()
