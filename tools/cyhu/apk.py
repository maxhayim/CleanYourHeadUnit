"""Read an APK's package name and signing certificate without the Android SDK.

Used before any install: the package name must be the one the catalog lists, and the signing
certificate must match the catalog's pin, so a renamed or re-signed APK is refused before it
reaches the unit. Android checks the signature again on update; this is in addition to that.

Supports APK Signature Scheme v2 and v3 (every APK built since Android 7 tooling). A v1-only APK
has no signing block; its certificate is not read here and the install is refused unless the
owner passes --allow-unpinned.
"""
from __future__ import annotations

import hashlib
import struct
import zipfile

V2_ID = 0x7109871A
V3_ID = 0xF05368C0
V31_ID = 0x1B93AD61


class ApkError(ValueError):
    pass


def _signing_block(buf: bytes) -> dict:
    eocd = buf.rfind(b"PK\x05\x06")
    if eocd < 0:
        raise ApkError("not a zip file")
    cd_offset = struct.unpack_from("<I", buf, eocd + 16)[0]
    if cd_offset < 32 or buf[cd_offset - 16:cd_offset] != b"APK Sig Block 42":
        return {}
    size = struct.unpack_from("<Q", buf, cd_offset - 24)[0]
    start = cd_offset - size - 8
    if start < 0 or struct.unpack_from("<Q", buf, start)[0] != size:
        raise ApkError("damaged APK signing block")
    pairs, pos, end = {}, start + 8, cd_offset - 24
    while pos < end:
        length = struct.unpack_from("<Q", buf, pos)[0]
        pid = struct.unpack_from("<I", buf, pos + 8)[0]
        pairs[pid] = buf[pos + 12:pos + 8 + length]
        pos += 8 + length
    return pairs


def _lp(buf: bytes, pos: int) -> tuple[bytes, int]:
    n = struct.unpack_from("<I", buf, pos)[0]
    return buf[pos + 4:pos + 4 + n], pos + 4 + n


def _first_cert(value: bytes) -> bytes:
    signers, _ = _lp(value, 0)
    signer, _ = _lp(signers, 0)
    signed_data, _ = _lp(signer, 0)
    _digests, p = _lp(signed_data, 0)
    certs, _ = _lp(signed_data, p)
    cert, _ = _lp(certs, 0)
    return cert


def signer_sha256(path: str) -> str | None:
    with open(path, "rb") as fh:
        buf = fh.read()
    pairs = _signing_block(buf)
    for pid in (V31_ID, V3_ID, V2_ID):
        if pid in pairs:
            return hashlib.sha256(_first_cert(pairs[pid])).hexdigest()
    return None


# --- binary AndroidManifest.xml, just enough to find <manifest package="..."> ------------------
def _string_pool(b: bytes, pos: int) -> list[str]:
    _type, header, _size = struct.unpack_from("<HHI", b, pos)
    count, _styles, flags, strings_start, _ = struct.unpack_from("<IIIII", b, pos + 8)
    utf8 = bool(flags & 0x100)
    offsets = struct.unpack_from(f"<{count}I", b, pos + header)
    base = pos + strings_start
    out = []
    for off in offsets:
        p = base + off
        if utf8:
            n = b[p]
            p += 2 if n & 0x80 else 1
            n = b[p]
            if n & 0x80:
                n = ((n & 0x7F) << 8) | b[p + 1]
                p += 2
            else:
                p += 1
            out.append(b[p:p + n].decode("utf-8", "replace"))
        else:
            n = struct.unpack_from("<H", b, p)[0]
            p += 2
            if n & 0x8000:
                n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", b, p)[0]
                p += 2
            out.append(b[p:p + 2 * n].decode("utf-16-le", "replace"))
    return out


def manifest_package(path: str) -> str:
    with zipfile.ZipFile(path) as z:
        b = z.read("AndroidManifest.xml")
    if struct.unpack_from("<H", b, 0)[0] != 0x0003:
        raise ApkError("AndroidManifest.xml is not binary XML")
    pos, strings = 8, []
    while pos < len(b):
        ctype, header, size = struct.unpack_from("<HHI", b, pos)
        if ctype == 0x0001:
            strings = _string_pool(b, pos)
        elif ctype == 0x0102:                                   # start element
            name_idx = struct.unpack_from("<I", b, pos + 20)[0]
            if strings[name_idx] == "manifest":
                attr_start, attr_size, attr_count = struct.unpack_from("<HHH", b, pos + 24)
                a = pos + 16 + attr_start
                for k in range(attr_count):
                    _ns, aname, raw = struct.unpack_from("<III", b, a + k * attr_size)
                    if strings[aname] == "package" and raw != 0xFFFFFFFF:
                        return strings[raw]
                raise ApkError("no package attribute")
        if size == 0:
            break
        pos += size
    raise ApkError("no <manifest> element")


def inspect(path: str) -> dict:
    with open(path, "rb") as fh:
        sha = hashlib.sha256(fh.read()).hexdigest()
    return {"sha256": sha, "package": manifest_package(path), "signer_sha256": signer_sha256(path)}
