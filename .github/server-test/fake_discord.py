"""Stand-in for the Discord webhook API, used by the server test.

The test maps discord.com to 127.0.0.1 and trusts this server's self-signed certificate,
so the plugin talks to it exactly as it would talk to Discord. Every request is appended to
a JSON lines file that the test scenario checks afterwards.

Usage: fake_discord.py <requests.jsonl> <cert.pem> <key.pem> [port] [--rate-limit-first]

With --rate-limit-first the very first request is answered with HTTP 429, like Discord does
when a webhook is rate limited, to check that the plugin waits and retries.
"""
import http.server
import itertools
import json
import ssl
import sys
import threading

args = [arg for arg in sys.argv[1:] if not arg.startswith("--")]
LOG, CERT, KEY = args[0], args[1], args[2]
PORT = int(args[3]) if len(args) > 3 else 443
RATE_LIMIT_FIRST = "--rate-limit-first" in sys.argv
CHANNEL_ID = "123456789012345678"
message_ids = itertools.count(100000000000000001)
lock = threading.Lock()
state = {"requests": 0}


class Handler(http.server.BaseHTTPRequestHandler):
    def _record(self):
        """Logs the request; returns False when it was answered with a rate limit instead."""
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length) if length else b""
        body = json.loads(raw) if raw else None
        with lock:
            state["requests"] += 1
            limited = RATE_LIMIT_FIRST and state["requests"] == 1
            entry = {"method": self.command, "path": self.path, "body": body,
                     "user_agent": self.headers.get("User-Agent"), "rate_limited": limited}
            with open(LOG, "a", encoding="utf-8") as log:
                log.write(json.dumps(entry, ensure_ascii=False) + "\n")
        if limited:
            data = json.dumps({"message": "You are being rate limited.", "retry_after": 0.4, "global": False}).encode()
            self.send_response(429)
            self.send_header("Content-Type", "application/json")
            self.send_header("Retry-After", "1")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return False
        return True

    def _reply(self, payload):
        data = json.dumps(payload).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):  # create message (?wait=true returns it)
        if self._record():
            self._reply({"id": str(next(message_ids)), "channel_id": CHANNEL_ID})

    def do_PATCH(self):  # edit message
        if self._record():
            message_id = self.path.split("/messages/")[-1].split("?")[0]
            self._reply({"id": message_id, "channel_id": CHANNEL_ID})

    def log_message(self, *args):
        pass


server = http.server.ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
context.load_cert_chain(CERT, KEY)
server.socket = context.wrap_socket(server.socket, server_side=True)
print(f"fake Discord listening on https://127.0.0.1:{PORT}", flush=True)
server.serve_forever()
