import os
import pathlib
import time
import uuid


def _request_has_form_body(body):
    if body is None:
        return False
    text = str(body).strip()
    return bool(text) and "=" in text and not text.startswith("{") and not text.startswith("[")


def _rpc_send(method, url, body="", tab_name=None):
    rpc_dir = os.environ.get("BURP_PYTHON_RPC_DIR")
    if not rpc_dir:
        raise RuntimeError("BURP_PYTHON_RPC_DIR is not configured")
    request_id = uuid.uuid4().hex
    root = pathlib.Path(rpc_dir)
    request = root / f"{request_id}.request"
    response = root / f"{request_id}.response"
    lines = [
        "operation=repeater.send",
        f"method={method}",
        f"url={url}",
        f"body={body or ''}",
    ]
    if tab_name:
        lines.append(f"tabName={tab_name}")
    lines.extend(["__end=1", ""])
    request.write_text("\n".join(lines), encoding="utf-8")
    deadline = time.monotonic() + 60
    while not response.exists():
        if time.monotonic() > deadline:
            raise TimeoutError("Timed out waiting for Burp Repeater response")
        time.sleep(0.025)
    fields = {}
    for line in response.read_text(encoding="utf-8-sig").splitlines():
        key, _, value = line.partition("=")
        fields[key] = value.replace("\\n", "\n").replace("\\r", "\r")
    if fields.get("ok") != "true":
        raise RuntimeError(fields.get("error", "Burp Repeater RPC failed"))


def send(method, url, body="", tab_name=None):
    _rpc_send(method, url, body, tab_name)


def promote_form_to_post(method, url, body="", tab_name=None):
    if str(method).upper() == "GET" and _request_has_form_body(body):
        send("POST", url, body, tab_name=tab_name)
        return "POST"
    send(method, url, body, tab_name=tab_name)
    return str(method).upper()


class _Repeater:
    send = staticmethod(send)
    promote_form_to_post = staticmethod(promote_form_to_post)


repeater = _Repeater()
