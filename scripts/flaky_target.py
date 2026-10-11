#!/usr/bin/env python3
"""
A tiny target service whose behaviour you can switch at runtime, for testing CloudPulse locally.

    python scripts/flaky_target.py            # listens on 0.0.0.0:9000

    curl localhost:9000/health                # current behaviour
    curl localhost:9000/set/ok                # healthy: 200
    curl localhost:9000/set/fail              # failing: 503
    curl localhost:9000/set/slow              # hangs for 20 s (triggers CloudPulse timeouts)

DEVELOPMENT ONLY. Register http://host.docker.internal:9000/health in CloudPulse, which requires
CLOUDPULSE_ALLOW_PRIVATE_TARGETS=true in .env. Never enable that flag on a public deployment.
"""
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

mode = "ok"


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print(f"[{self.log_date_time_string()}] mode={mode} {fmt % args}")

    def _reply(self, status, body):
        data = body.encode()
        self.send_response(status)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        global mode
        if self.path.startswith("/set/"):
            new_mode = self.path.split("/")[2]
            if new_mode in ("ok", "fail", "slow"):
                mode = new_mode
                return self._reply(200, f"mode set to {mode}\n")
            return self._reply(400, "use ok, fail or slow\n")
        if mode == "fail":
            return self._reply(503, "simulated outage\n")
        if mode == "slow":
            time.sleep(20)
        return self._reply(200, "ok\n")


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9000
    print(f"Flaky target on port {port}. Switch with /set/ok, /set/fail, /set/slow")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
