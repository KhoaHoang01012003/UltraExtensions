def _bridge():
    return burpBridge.encoder()


def base64_encode(data):
    if isinstance(data, str):
        data = data.encode("utf-8")
    return _bridge().base64Encode(data)


def base64_decode(value):
    return bytes(_bridge().base64Decode(value))
