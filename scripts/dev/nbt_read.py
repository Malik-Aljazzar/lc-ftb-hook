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


# ---- Typed round-trip (for building test fixtures) -------------------------
# A node is (tag, payload): compound payload = {name: node}, list payload =
# (element_tag, [payload, ...]), arrays = list of ints, everything else raw.

def _typed(f, tag):
    if tag == 9:
        element = _read(f, 'b')
        return element, [_typed(f, element) for _ in range(_read(f, 'i'))]
    if tag == 10:
        result = {}
        while True:
            child = _read(f, 'b')
            if child == 0:
                return result
            name = _string(f)
            result[name] = (child, _typed(f, child))
    return _payload(f, tag)


def load_typed(path):
    """The whole file as a typed tree: (root_name, (tag, payload))."""
    f = io.BytesIO(gzip.open(path).read())
    tag = _read(f, 'b')
    name = _string(f)
    return name, (tag, _typed(f, tag))


def _write(out, fmt, value):
    out.write(struct.pack('>' + fmt, value))


def _write_string(out, text):
    raw = text.encode('utf-8')
    _write(out, 'H', len(raw))
    out.write(raw)


def _write_payload(out, tag, payload):
    simple = {1: 'b', 2: 'h', 3: 'i', 4: 'q', 5: 'f', 6: 'd'}
    if tag in simple:
        _write(out, simple[tag], payload)
    elif tag == 7:
        _write(out, 'i', len(payload))
        out.write(payload)
    elif tag == 8:
        _write_string(out, payload)
    elif tag == 9:
        element, items = payload
        _write(out, 'b', element if items else (element or 0))
        _write(out, 'i', len(items))
        for item in items:
            _write_payload(out, element, item)
    elif tag == 10:
        for name, (child, child_payload) in payload.items():
            _write(out, 'b', child)
            _write_string(out, name)
            _write_payload(out, child, child_payload)
        _write(out, 'b', 0)
    elif tag == 11:
        _write(out, 'i', len(payload))
        for value in payload:
            _write(out, 'i', value)
    elif tag == 12:
        _write(out, 'i', len(payload))
        for value in payload:
            _write(out, 'q', value)
    else:
        raise ValueError('unknown NBT tag %s' % tag)


def save_typed(path, tree):
    name, (tag, payload) = tree
    out = io.BytesIO()
    _write(out, 'b', tag)
    _write_string(out, name)
    _write_payload(out, tag, payload)
    with gzip.open(path, 'wb') as f:
        f.write(out.getvalue())


def uuid_to_ints(text):
    raw = uuid.UUID(text).bytes
    return [struct.unpack('>i', raw[i:i + 4])[0] for i in range(0, 16, 4)]
