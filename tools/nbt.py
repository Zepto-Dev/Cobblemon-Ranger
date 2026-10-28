"""A tiny NBT reader/writer, enough for structure template files.

Values are plain Python: dict for compounds, str, and typed wrappers for numbers and lists so the tag type survives a
round trip.
"""
import gzip
import struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Tag:
    def __init__(self, kind, value):
        self.kind = kind
        self.value = value

    def __repr__(self):
        return "Tag(%d, %r)" % (self.kind, self.value)


def Byte(v):
    return Tag(BYTE, v)


def Int(v):
    return Tag(INT, v)


def Double(v):
    return Tag(DOUBLE, v)


def Float(v):
    return Tag(FLOAT, v)


def List(kind, items):
    return Tag(LIST, (kind, items))


_NUM = {BYTE: ">b", SHORT: ">h", INT: ">i", LONG: ">q", FLOAT: ">f", DOUBLE: ">d"}


def _kind(value):
    if isinstance(value, Tag):
        return value.kind
    if isinstance(value, dict):
        return COMPOUND
    if isinstance(value, str):
        return STRING
    raise TypeError("untyped NBT value %r" % (value,))


def _write(out, kind, value):
    if isinstance(value, Tag):
        value = value.value
    if kind in _NUM:
        out += struct.pack(_NUM[kind], value)
    elif kind == STRING:
        data = value.encode("utf-8")
        out += struct.pack(">H", len(data)) + data
    elif kind == LIST:
        item_kind, items = value
        out += struct.pack(">bi", item_kind if items else END, len(items))
        for item in items:
            _write(out, item_kind, item)
    elif kind == COMPOUND:
        for name, item in value.items():
            k = _kind(item)
            out += struct.pack(">b", k)
            _write(out, STRING, name)
            _write(out, k, item)
        out += b"\x00"
    elif kind == INT_ARRAY:
        out += struct.pack(">i", len(value)) + b"".join(struct.pack(">i", v) for v in value)
    else:
        raise TypeError(kind)


def save(path, root):
    out = bytearray(b"\x0a\x00\x00")
    _write(out, COMPOUND, root)
    with open(path, "wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb", mtime=0) as f:
        f.write(bytes(out))


def _read(data, pos, kind):
    if kind in _NUM:
        fmt = _NUM[kind]
        size = struct.calcsize(fmt)
        return Tag(kind, struct.unpack_from(fmt, data, pos)[0]), pos + size
    if kind == STRING:
        n = struct.unpack_from(">H", data, pos)[0]
        return data[pos + 2:pos + 2 + n].decode("utf-8"), pos + 2 + n
    if kind == LIST:
        item_kind, n = struct.unpack_from(">bi", data, pos)
        pos += 5
        items = []
        for _ in range(n):
            item, pos = _read(data, pos, item_kind)
            items.append(item)
        return Tag(LIST, (item_kind, items)), pos
    if kind == COMPOUND:
        result = {}
        while True:
            k = data[pos]
            pos += 1
            if k == END:
                return result, pos
            name, pos = _read(data, pos, STRING)
            result[name], pos = _read(data, pos, k)
    if kind in (BYTE_ARRAY, INT_ARRAY, LONG_ARRAY):
        n = struct.unpack_from(">i", data, pos)[0]
        fmt, size = {BYTE_ARRAY: (">b", 1), INT_ARRAY: (">i", 4), LONG_ARRAY: (">q", 8)}[kind]
        pos += 4
        values = [struct.unpack_from(fmt, data, pos + i * size)[0] for i in range(n)]
        return Tag(kind, values), pos + n * size
    raise ValueError(kind)


def load(path):
    with gzip.open(path, "rb") as f:
        data = f.read()
    root, _ = _read(data, 3 + struct.unpack_from(">H", data, 1)[0], COMPOUND)
    return root
