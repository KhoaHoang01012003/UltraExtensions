from . import encoder
from . import crypto


class _Http:
    def send(self, method, url, body=""):
        return burpBridge.http().send(method, url, body)


http = _Http()

__all__ = ["encoder", "crypto", "http"]
