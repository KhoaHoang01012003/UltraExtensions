from . import crypto
from . import encoder
import os
import pathlib
import time
import uuid


class _Http:
    def send(self, method, url, body=""):
        rpc_dir = os.environ.get("BURP_PYTHON_RPC_DIR")
        if not rpc_dir:
            raise RuntimeError("BURP_PYTHON_RPC_DIR is not configured")
        request_id = uuid.uuid4().hex
        root = pathlib.Path(rpc_dir)
        request = root / f"{request_id}.request"
        response = root / f"{request_id}.response"
        request.write_text(
            "\n".join(
                [
                    "operation=http.send",
                    f"method={method}",
                    f"url={url}",
                    f"body={body or ''}",
                    "__end=1",
                    "",
                ]
            ),
            encoding="utf-8",
        )
        deadline = time.monotonic() + 60
        while not response.exists():
            if time.monotonic() > deadline:
                raise TimeoutError("Timed out waiting for Burp HTTP response")
            time.sleep(0.025)
        fields = {}
        for line in response.read_text(encoding="utf-8-sig").splitlines():
            key, _, value = line.partition("=")
            fields[key] = value.replace("\\n", "\n").replace("\\r", "\r")
        if fields.get("ok") != "true":
            raise RuntimeError(fields.get("error", "Burp HTTP RPC failed"))
        return type(
            "HttpResponse",
            (),
            {
                "status_code": int(fields.get("statusCode", "0")),
                "body": fields.get("body", ""),
            },
        )()


http = _Http()

__all__ = ["crypto", "encoder", "http"]
