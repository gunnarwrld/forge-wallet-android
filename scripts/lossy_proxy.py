#!/usr/bin/env python3
"""Makes the case that double-posts happen on demand, against a real Ledger Forge.

Listens on 127.0.0.1:8081 and forwards to Ledger Forge on 8080. The first response for each transfer
key is thrown away after the ledger has committed, so the client sees a dropped connection. Every
attempt goes to the log file: all attempts for a transfer should show the same key, body and transfer id.

    python3 scripts/lossy_proxy.py /tmp/attempts.log
    ./gradlew installDebug -PledgerBaseUrl=http://10.0.2.2:8081/

scripts/verify_exactly_once.sh runs this for you.
"""
import json, socket, sys, urllib.error, urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

UPSTREAM = "http://127.0.0.1:8080"
LOG = open(sys.argv[1], "a", buffering=1)
seen = {}

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def forward(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else None
        headers = {k: v for k, v in self.headers.items() if k.lower() not in ("host", "connection", "content-length", "accept-encoding")}
        req = urllib.request.Request(UPSTREAM + self.path, data=body, headers=headers, method=self.command)
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.status, r.headers.get("Content-Type", ""), r.read(), body
        except urllib.error.HTTPError as e:
            return e.code, e.headers.get("Content-Type", ""), e.read(), body

    def handle_any(self):
        status, ctype, payload, body = self.forward()
        key = self.headers.get("Idempotency-Key")
        if self.path.endswith("/transfers") and key:
            attempt = seen.get(key, 0) + 1
            seen[key] = attempt
            tid = json.loads(payload).get("transferId") if status == 201 else None
            drop = attempt == 1 and status == 201
            LOG.write(json.dumps({"attempt": attempt, "key": key, "status": status, "transferId": tid, "body": (body or b"").decode(), "responseDropped": drop}) + "\n")
            if drop:
                self.connection.shutdown(socket.SHUT_RDWR)
                self.close_connection = True
                return
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    do_GET = do_POST = handle_any

ThreadingHTTPServer(("127.0.0.1", 8081), Handler).serve_forever()
