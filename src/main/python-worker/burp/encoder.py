import base64
import binascii
import gzip
import html
import urllib.parse


def _bytes(data):
    if isinstance(data, str):
        return data.encode("utf-8")
    return bytes(data)


def base64_encode(data):
    return base64.b64encode(_bytes(data)).decode("ascii")


def base64_decode(value):
    return base64.b64decode(_bytes(value))


def hex_encode(data):
    return binascii.hexlify(_bytes(data)).decode("ascii")


def hex_decode(value):
    return binascii.unhexlify(_bytes(value))


def url_encode(value):
    return urllib.parse.quote(str(value), safe="")


def url_decode(value):
    return urllib.parse.unquote(str(value))


def html_encode(value):
    return html.escape(str(value), quote=True)


def html_decode(value):
    return html.unescape(str(value))


def gzip_compress(data):
    return gzip.compress(_bytes(data))


def gzip_decompress(data):
    return gzip.decompress(_bytes(data))
