def _bridge():
    return burpBridge.crypto()


def sha256_hex(data):
    if isinstance(data, str):
        data = data.encode("utf-8")
    return _bridge().sha256Hex(data)


def sha1_hex(data):
    if isinstance(data, str):
        data = data.encode("utf-8")
    return _bridge().sha1Hex(data)
