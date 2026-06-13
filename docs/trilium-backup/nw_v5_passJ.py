#!/usr/bin/env python3
"""Pass J — reflect the multi-check model in the three actor stories."""
import urllib.request

TOKEN = open("/tmp/trilium-etapi-token").read().strip().splitlines()[0]
B = "http://10.9.9.6:7081/etapi"

def get(nid):
    return urllib.request.urlopen(urllib.request.Request(
        f"{B}/notes/{nid}/content", headers={"Authorization": TOKEN})).read().decode()
def put(nid, html):
    urllib.request.urlopen(urllib.request.Request(
        f"{B}/notes/{nid}/content", data=html.encode(), method="PUT",
        headers={"Authorization": TOKEN, "Content-Type": "text/plain"}))
def append_once(nid, marker, html):
    cur = get(nid)
    if marker not in cur:
        put(nid, cur + html)
        print("  updated", nid)

PARENT = "1dLqJDZl9vW7"; MASTER = "uc2iTA1xf3b0"; SLAVE = "43LcqUCnEjse"; CLIENT = "5Ij9em8UQTu6"

append_once(PARENT, "many small checks",
  "<h4>How each actor grades itself — many small checks, worst wins</h4>"
  "<p>No actor uses one tangled rule. Each one runs a <strong>list of small named checks</strong> and "
  "folds them with one law: <strong>all pass → UP · one degrading check fails → DEGRADED · one "
  "critical check fails → DOWN</strong>. A check that throws counts as down. So a master whose socket "
  "answers but whose <em>disk is full</em> is DOWN, not healthy — the critical check overrides the rest. "
  "New checks are added one method at a time, per role, without touching the others.</p>")

append_once(MASTER, "checks the master's agent runs",
  "<h4>The checks the master's agent runs (worst wins)</h4>"
  "<ul>"
  "<li><strong>systemctl</strong> (critical) — is the mysqld service unit active? Inactive/failed → DOWN.</li>"
  "<li><strong>disk</strong> (critical) — does the data directory still have room? Zero free → DOWN.</li>"
  "<li><strong>read-write</strong> (degrading) — is read-only OFF? A master gone read-only → DEGRADED.</li>"
  "</ul>"
  "<p>The systemctl and disk checks are why \"the socket answered\" is not enough: a box can accept a "
  "connection while its disk is full and the service is half-dead. Those are caught here, one check each.</p>")

append_once(SLAVE, "checks the slave's agent runs",
  "<h4>The checks the slave's agent runs (worst wins)</h4>"
  "<ul>"
  "<li><strong>systemctl</strong> (critical) — mysqld service active? Inactive → DOWN.</li>"
  "<li><strong>disk</strong> (critical) — data disk has room? Full → DOWN.</li>"
  "<li><strong>replica-io</strong> (degrading) — is the IO thread running?</li>"
  "<li><strong>replica-sql</strong> (degrading) — is the SQL thread running?</li>"
  "<li><strong>lag</strong> (degrading) — within the promotion window? Beyond it → DEGRADED.</li>"
  "</ul>"
  "<p>The slave is <em>promotable</em> only when every check passes — that is exactly the FAST state, "
  "its \"I can assume master\" signal. Any failing check (stopped thread, lag, or a critical OS check) "
  "drops it below promotable.</p>")

append_once(CLIENT, "checks the canary runs",
  "<h4>The checks the canary runs (worst wins)</h4>"
  "<ul>"
  "<li><strong>returns-rows</strong> (degrading) — did the query come back with at least one row?</li>"
  "<li><strong>latency</strong> — within the fast threshold → UP; slower but under the dead threshold → "
  "DEGRADED; past the dead threshold → DOWN.</li>"
  "</ul>"
  "<p>A query that throws or never answers within the dead threshold is DOWN before these even run.</p>")

print("Pass J done")
