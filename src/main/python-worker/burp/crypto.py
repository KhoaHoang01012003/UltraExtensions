import hashlib
import hmac
import secrets


def _bytes(data):
    if isinstance(data, str):
        return data.encode("utf-8")
    return bytes(data)


def sha256_hex(data):
    return hashlib.sha256(_bytes(data)).hexdigest()


def sha1_hex(data):
    return hashlib.sha1(_bytes(data)).hexdigest()


def md5_hex(data):
    return hashlib.md5(_bytes(data)).hexdigest()


def hmac_sha256_hex(key, message):
    return hmac.new(_bytes(key), _bytes(message), hashlib.sha256).hexdigest()


def random_bytes(length):
    return secrets.token_bytes(int(length))
