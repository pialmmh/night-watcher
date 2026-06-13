#!/usr/bin/env python3
"""Pass I — the three MySQL actors, as follow-along stories (storytelling voice)."""
import json, urllib.request

TOKEN = open("/tmp/trilium-etapi-token").read().strip().splitlines()[0]
B = "http://10.9.9.6:7081/etapi"
IDS = json.load(open("/tmp/nw-v5-ids.json"))

def note(parent, title, content):
    nid = json.loads(urllib.request.urlopen(urllib.request.Request(
        B+"/create-note", data=json.dumps({"parentNoteId":parent,"title":title,
        "type":"text","content":content}).encode(),
        headers={"Authorization":TOKEN,"Content-Type":"application/json"})).read())["note"]["noteId"]
    print("  +", title, nid); return nid

def L(key, label): return f'<a class="reference-link" href="#root/{IDS[key]}">{label}</a>'

SVC = IDS["svc.mysql"]; FEAT = "20BaEi65OMDO"

# parent — introduce the three actors
parent = note(SVC, "The three actors (how MySQL is watched)",
  "<p>Three watchers look at one MySQL master-slave cluster from three angles. None of them "
  "decides a failover — each one only reports what it sees. The "
  + L("spi.HealthAggregator","aggregator") + " turns the three reports into seat scores, and the "
  + L("vote.verdictFor","vote") + " needs a majority of seats to agree before anything moves.</p>"
  "<p>Read the three stories below in order; each names one actor and follows it through a single tick.</p>"
  "<ol>"
  "<li><strong>masterWatchesItself</strong> — the agent on the master confesses its own health.</li>"
  "<li><strong>slaveJudgesIfItCanTakeOver</strong> — the agent on the slave decides if it may assume master.</li>"
  "<li><strong>clientSimulatorPingsLikeAnApp</strong> — a canary measures the master the way a real app feels it.</li>"
  "</ol>")

# Actor 1 — master self-watch
note(parent, "masterWatchesItself — the master's own agent (Role 1)",
  "<p><em>Pre-condition: this agent runs on the host that IS the MySQL master.</em></p>"
  "<h4>What happens each tick</h4>"
  "<ol>"
  "<li>The agent's local probe (" + L("mysql.local","mysql.local") + ") opens a socket to its own "
  "MySQL on 127.0.0.1 — no network in the way.</li>"
  "<li>It asks one telling question: <em>am I read-only?</em> (<code>SELECT @@global.read_only</code>). "
  "A real master answers <strong>OFF</strong> — it accepts writes.</li>"
  "<li>It also reads how long it has been up and how many replicas are streaming from it "
  "(the binlog-dump threads) — useful colour for later.</li>"
  "<li>It grades itself: read-write and answering quickly → <strong>FAST</strong> (a healthy master). "
  "Flipped to read-only → <strong>DEGRADED</strong> — a master should never be read-only, so something "
  "demoted it. The socket refuses to open → <strong>DEAD</strong>.</li>"
  "<li>It writes this confession to etcd as a LOCAL_SELF observation, keyed by its own node name.</li>"
  "</ol>"
  "<h4>Edge cases the master's agent handles</h4>"
  "<ul><li>If read-only flipped to ON → DEGRADED (suspicious — it was supposed to be the master).</li>"
  "<li>If the local socket answers but slowly → DEGRADED.</li>"
  "<li>If the connection throws → DEAD.</li></ul>"
  "<h4>What the master's agent does NOT do</h4>"
  "<p>It never decides a failover and never touches the slave. It only reports what its own database "
  "says about itself.</p>"
  "<h4>Roles for this story</h4>"
  "<ul><li><strong>Master's agent:</strong> confess my own read-write health, every tick.</li>"
  "<li><strong>" + L("vote.verdictFor","Vote") + ":</strong> weigh this confession against the other two seats.</li>"
  "<li><strong>Coordinator:</strong> act only if the cluster as a whole agrees the master is gone.</li></ul>")

# Actor 2 — slave self-watch + promotability
note(parent, "slaveJudgesIfItCanTakeOver — the slave's own agent (Role 2)",
  "<p><em>Pre-condition: this agent runs on the host that IS a MySQL slave (a replica).</em></p>"
  "<h4>What happens each tick</h4>"
  "<ol>"
  "<li>The agent's local probe (" + L("mysql.local","mysql.local") + ") asks its own MySQL for replica "
  "status (<code>SHOW REPLICA STATUS</code>).</li>"
  "<li>It checks two things only it can know for sure: are <strong>both</strong> replica threads (IO and "
  "SQL) running? and <strong>how many seconds behind</strong> the master am I?</li>"
  "<li>It applies the promotion rule: threads running <strong>AND</strong> lag within the window "
  "(default 10&nbsp;s — <em>does not lack in binlog</em>) → I am healthy <strong>and I can assume the "
  "master role</strong>.</li>"
  "<li>If a thread is stopped, or I am lagging beyond the window, or I cannot even measure my lag → I "
  "report <strong>DEGRADED</strong>: still fine for reads, but <strong>not promotable</strong>.</li>"
  "<li>It writes this self-judgment to etcd as a LOCAL_SELF observation, keyed by its node name.</li>"
  "<li>When a master dies, the " + L("mysql.selector","candidate selector") + " reads these "
  "self-judgments and picks the least-lagged slave that said <em>\"I can take over\"</em>. A slave that "
  "lacks in binlog is never chosen — promoting it would throw away writes the old master already took.</li>"
  "</ol>"
  "<h4>Edge cases the slave's agent handles</h4>"
  "<ul><li>If the IO or SQL replica thread is stopped → not promotable (DEGRADED).</li>"
  "<li>If lag is past the window → not promotable (DEGRADED).</li>"
  "<li>If lag is unknown → not promotable — it will not gamble on a slave that cannot prove it is caught up.</li></ul>"
  "<h4>What the slave's agent does NOT do</h4>"
  "<p>It never promotes itself. It only declares <em>readiness</em>. The coordinator does the actual "
  "promotion through the " + L("mysql.actions","SQL actions") + ".</p>"
  "<h4>Roles for this story</h4>"
  "<ul><li><strong>Slave's agent:</strong> judge honestly whether I am caught up enough to be master.</li>"
  "<li><strong>" + L("mysql.selector","Selector") + ":</strong> among the willing slaves, pick the least-lagged.</li>"
  "<li><strong>Coordinator:</strong> promote the chosen slave.</li></ul>")

# Actor 3 — client simulator
note(parent, "clientSimulatorPingsLikeAnApp — the canary (Role 3)",
  "<p><em>Pre-condition: this agent can reach the master's address — the same VIP a real application "
  "would connect to.</em></p>"
  "<h4>What happens each tick</h4>"
  "<ol>"
  "<li>The canary (" + L("mysql.remote","mysql.remote.client") + ") connects to the master address the "
  "way an application would — across the network, with real credentials.</li>"
  "<li>It runs the configured query (default <code>SHOW DATABASES</code>; operators can point it at a "
  "heartbeat row instead).</li>"
  "<li>It times the answer: fast (≤ the fast threshold) → <strong>FAST</strong>; slow but answered "
  "(≤ the dead threshold) → <strong>DEGRADED</strong>; zero rows → <strong>DEGRADED</strong>; throws or "
  "never answers within the dead threshold → <strong>DEAD</strong>.</li>"
  "<li>It writes this outside-in view to etcd as a CLIENT observation.</li>"
  "</ol>"
  "<h4>Why this actor matters</h4>"
  "<p>This is the seat that catches a master which is <em>up but unusable</em>. The box answers its own "
  "local socket — so " + L("mysql.local","the master's own agent") + " says \"I'm fine\" — yet real "
  "client traffic times out (a full connection table, a wedged disk, a firewall). The canary disagrees, "
  "and the vote weighs all three seats rather than trusting any one.</p>"
  "<h4>Edge cases the canary handles</h4>"
  "<ul><li>Query returns no rows → DEGRADED (answering, but wrong).</li>"
  "<li>Query exceeds the dead threshold → DEAD, even if no exception was thrown.</li>"
  "<li>The query is configurable — a cheap default, or a real heartbeat for stricter checks.</li></ul>"
  "<h4>What the canary does NOT do</h4>"
  "<p>It never looks inside replication or read-only state. It only measures what an app would feel.</p>"
  "<h4>Roles for this story</h4>"
  "<ul><li><strong>Client simulator:</strong> measure the master from the outside, like an app.</li>"
  "<li><strong>" + L("spi.HealthAggregator","Aggregator") + ":</strong> turn this seat into a 0–1 score.</li>"
  "<li><strong>" + L("vote.verdictFor","Vote") + ":</strong> combine all three seats; ODOWN needs a majority to agree.</li></ul>")

# cross-link from the feature
cur = urllib.request.urlopen(urllib.request.Request(
    f"{B}/notes/{FEAT}/content", headers={"Authorization":TOKEN})).read().decode()
if "three actors" not in cur:
    add = ('<p>📖 Follow the three watchers actor-by-actor: '
           f'<a class="reference-link" href="#root/{parent}">The three actors (how MySQL is watched)</a>.</p>')
    urllib.request.urlopen(urllib.request.Request(
        f"{B}/notes/{FEAT}/content", data=(cur+add).encode(), method="PUT",
        headers={"Authorization":TOKEN,"Content-Type":"text/plain"}))
    print("  linked from feature mysqlMasterSlaveWatching")

print("Pass I done")
