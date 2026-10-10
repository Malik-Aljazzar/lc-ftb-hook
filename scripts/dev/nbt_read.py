"""Minimal reader for gzipped NBT files (Minecraft SavedData .dat) - read only."""
import gzip
import io
import struct
import uuid


def _read(f, fmt):
    return struct.unpack('>' + fmt, f.read(struct.calcsize('>' + fmt)))[0]


def _string(f):
    return f.read(_read(f, 'H')).decode('utf-8', 'replace')


def _payload(f, tag):
    if tag == 1: return _read(f, 'b')
    if tag == 2: return _read(f, 'h')
    if tag == 3: return _read(f, 'i')
    if tag == 4: return _read(f, 'q')
    if tag == 5: return _read(f, 'f')
    if tag == 6: return _read(f, 'd')
    if tag == 7: return f.read(_read(f, 'i'))
    if tag == 8: return _string(f)
    if tag == 9:
        element = _read(f, 'b')
        return [_payload(f, element) for _ in range(_read(f, 'i'))]
    if tag == 10:
        result = {}
        while True:
            child = _read(f, 'b')
            if child == 0:
                return result
            name = _string(f)  # must be read before the value (Python evaluates the right side first)
            result[name] = _payload(f, child)
    if tag == 11: return [_read(f, 'i') for _ in range(_read(f, 'i'))]
    if tag == 12: return [_read(f, 'q') for _ in range(_read(f, 'i'))]
    raise ValueError('unknown NBT tag %s' % tag)


def load(path):
    f = io.BytesIO(gzip.open(path).read())
    tag = _read(f, 'b')
    _string(f)
    return _payload(f, tag)


def uuid_from_ints(ints):
    """Minecraft stores UUIDs as 4 signed ints."""
    raw = b''.join(struct.pack('>i', i) for i in ints)
    return str(uuid.UUID(bytes=raw))
