"""Checks that every Cobblemon class, method and field our jar links against exists in each given Cobblemon jar.

The release jar is compiled against the oldest supported Cobblemon, so a member that a newer Cobblemon removed, whose
signature changed, or that switched between static and instance (Kotlin @JvmStatic) compiles fine but crashes with
NoSuchMethodError or IncompatibleClassChangeError in game. Classes switching between class and interface are caught
too. Members that Cobblemon classes inherit from Minecraft are not checked here.

Usage: python3 tools/check_cobblemon_compat.py build/libs/cobblemonranger-X.jar compiled-against.jar newer.jar [...]
The first Cobblemon jar must be the one the mod was compiled against; it is the baseline for static/instance.
"""
import struct, sys, zipfile

def parse(data):
    pos = 10
    n = struct.unpack('>H', data[8:10])[0]
    cp = [None] * n
    i = 1
    while i < n:
        tag = data[pos]
        if tag == 1:
            ln = struct.unpack('>H', data[pos+1:pos+3])[0]
            cp[i] = ('utf8', data[pos+3:pos+3+ln].decode('utf-8', 'replace')); pos += 3 + ln
        elif tag in (3, 4):
            pos += 5
        elif tag in (5, 6):
            pos += 9; i += 1
        elif tag == 7 or tag == 8 or tag == 16 or tag == 19 or tag == 20:
            cp[i] = ('class' if tag == 7 else 'x', struct.unpack('>H', data[pos+1:pos+3])[0]); pos += 3
        elif tag in (9, 10, 11, 12, 17, 18):
            a, b = struct.unpack('>HH', data[pos+1:pos+5])
            cp[i] = ({9: 'field', 10: 'method', 11: 'imethod', 12: 'nat'}.get(tag, 'x'), a, b); pos += 5
        elif tag == 15:
            pos += 4
        else:
            raise ValueError(tag)
        i += 1
    u = lambda k: cp[k][1]
    access, this, sup = struct.unpack('>HHH', data[pos:pos+6]); pos += 6
    name = u(cp[this][1]); superName = u(cp[sup][1]) if sup else None
    ni = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
    ifaces = [u(cp[struct.unpack('>H', data[pos+2*j:pos+2*j+2])[0]][1]) for j in range(ni)]; pos += 2 * ni
    members = {}
    for kind in ('F', 'M'):
        cnt = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
        for _ in range(cnt):
            flags, nm, ds, ac = struct.unpack('>HHHH', data[pos:pos+8]); pos += 8
            members[(u(nm), u(ds))] = flags
            for _ in range(ac):
                ln = struct.unpack('>I', data[pos+2:pos+6])[0]; pos += 6 + ln
    refs = set()
    for e in cp:
        if e and e[0] in ('field', 'method', 'imethod'):
            owner = u(cp[e[1]][1]); nat = cp[e[2]]
            refs.add((owner, u(nat[1]), u(nat[2]), e[0] == 'imethod'))
    return access, name, superName, ifaces, members, refs

def load(jar):
    z = zipfile.ZipFile(jar); out = {}
    for n in z.namelist():
        if n.endswith('.class') and n.startswith('com/cobblemon/'):
            acc, nm, sp, ifs, mem, _ = parse(z.read(n)); out[nm] = (acc, sp, ifs, mem)
    return out

ACC_PUBLIC, ACC_STATIC, ACC_INTERFACE = 0x0001, 0x0008, 0x0200

def find(classes, owner, name, desc, seen=None):
    """The member's access flags, False if it doesn't exist, None if it may be inherited from outside Cobblemon."""
    seen = seen or set()
    if owner in seen: return False
    seen.add(owner)
    if owner not in classes:
        return None  # outside Cobblemon (Minecraft/Kotlin): can't check here
    _, sp, ifs, mem = classes[owner]
    if (name, desc) in mem: return mem[(name, desc)]
    res = False
    for p in [sp] + ifs:
        if p:
            r = find(classes, p, name, desc, seen)
            if r is not None and r is not False: return r
            if r is None: res = None
    return res

ours = zipfile.ZipFile(sys.argv[1])
refs = set()
for n in ours.namelist():
    if n.endswith('.class'):
        refs |= {r for r in parse(ours.read(n))[5] if r[0].startswith('com/cobblemon/')}
print(len(refs), 'Cobblemon member references checked against', len(sys.argv) - 3, 'newer jars')
bad = False
baseline = load(sys.argv[2])
for jar in sys.argv[2:]:
    classes = load(jar)
    for owner, name, desc, iface in sorted(refs):
        if owner not in classes:
            print(f'{jar}: MISSING CLASS {owner}'); bad = True; continue
        if bool(classes[owner][0] & ACC_INTERFACE) != iface:
            print(f'{jar}: CLASS/INTERFACE CHANGED {owner}'); bad = True
        r = find(classes, owner, name, desc)
        # A signature that mentions a Cobblemon type can't come from a Minecraft superclass either.
        if r is False or (r is None and 'com/cobblemon/' in desc):
            print(f'{jar}: MISSING {owner}.{name}{desc}'); bad = True
            continue
        expected = find(baseline, owner, name, desc)
        if r not in (None, False) and expected not in (None, False) and (r ^ expected) & ACC_STATIC:
            kind = 'static' if r & ACC_STATIC else 'instance'
            print(f'{jar}: NOW {kind.upper()} {owner}.{name}{desc}'); bad = True
        if r not in (None, False) and expected not in (None, False) and expected & ACC_PUBLIC and not r & ACC_PUBLIC:
            print(f'{jar}: NO LONGER PUBLIC {owner}.{name}{desc}'); bad = True
print('FAILED' if bad else 'OK')
sys.exit(1 if bad else 0)
