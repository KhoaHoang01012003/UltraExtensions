from . import crypto
from . import encoder


class _Http:
    def send(self, method, url, body=""):
        raise NotImplementedError("burp.http worker bridge is not available in this CPython runtime build")


http = _Http()

__all__ = ["crypto", "encoder", "http"]
