#!/usr/bin/env python3
"""Pass B — concerns/features/flows + mermaid; Pass C — Used-by backlinks."""
import json, urllib.request

TOKEN = open("/tmp/trilium-etapi-token").read().strip().splitlines()[0]
B = "http://10.9.9.6:7081/etapi"
IDS = json.load(open("/tmp/nw-v5-ids.json"))

def req(method, path, body=None, ctype="application/json"):
    data = None
    if body is not None:
        data = body.encode() if isinstance(body, str) else json.dumps(body).encode()
    r = urllib.request.Request(B + path, data=data, method=method,
        headers={"Authorization": TOKEN, "Content-Type": ctype})
    with urllib.request.urlopen(r) as resp:
        raw = resp.read()
        if not raw:
            return None
        if method == "GET" or ctype == "application/json":
            try:
                return json.loads(raw)
            except ValueError:
                return raw.decode()
        return None

def note(parent, title, content, ntype="text"):
    body = {"parentNoteId": parent, "title": title, "type": ntype, "content": content}
    if ntype == "mermaid":
        body["mime"] = "text/mermaid"
    n = req("POST", "/create-note", body)
    nid = n["note"]["noteId"]
    print(f"  + {title} [{nid}]")
    return nid

def L(key, label=None):
    """reference link to a tracked leaf"""
    nid = IDS[key]
    return f'<a class="reference-link" href="#root/{nid}">{label or key}</a>'

USED_BY = {}  # leaf key -> [(flow_id, flow_title)]
def touch(flow_id, flow_title, *leaf_keys):
    for k in leaf_keys:
        USED_BY.setdefault(k, []).append((flow_id, flow_title))

def flow(feature_id, title, runs_when, mermaid_src, steps_html, touches):
    fid = note(feature_id, title, "<p>placeholder</p>")
    mid = note(fid, f"diagram — {title}", mermaid_src, ntype="mermaid")
    html = (f"<p><strong>Runs when:</strong> {runs_when}</p>"
            f'<section class="include-note" data-note-id="{mid}" data-box-size="full"></section>'
            f"<h4>Steps</h4><ol>{steps_html}</ol>"
            "<p><em>No-orphan check: every hop above links a real leaf.</em></p>")
    req("PUT", f"/notes/{fid}/content", html, ctype="text/plain")
    touch(fid, title, *touches)
    return fid

def feature(concern_id, title, story_html, flows_html, touchpoints_rows):
    rows = "".join(f"<tr><td>{l}</td><td>{s}</td></tr>" for l, s in touchpoints_rows)
    html = (f"<h4>Story</h4>{story_html}"
            f"<h4>Flows</h4><ul>{flows_html}</ul>"
            f"<h4>Touchpoints</h4><table><thead><tr><th>leaf</th><th>service</th></tr></thead>"
            f"<tbody>{rows}</tbody></table>")
    return note(concern_id, title, html)

def set_content(nid, html):
    req("PUT", f"/notes/{nid}/content", html, ctype="text/plain")

CONCERNS = IDS["concerns"]

# ================================================== C1 serviceObservation
c1 = note(CONCERNS, "serviceObservation (probe everything, share everything)",
  "<p>Agents keep looking at the services they can see and put every piece of evidence on the "
  "shared board. Nothing is decided here — this concern only makes the truth visible to everyone.</p>")

f1 = feature(c1, "probeRunsOnSchedule (an agent keeps watching)",
  "<p>An agent wakes on its tick. It runs every probe that applies to this host — a MySQL canary "
  "here, a local confession there. Each probe gets a hard deadline (default 4 s), so a hung "
  "service cannot freeze the watcher. Every result becomes an Observation and lands on the etcd "
  "board, tied to the agent's lease. Seconds later, every other agent's cache holds the same fact.</p>"
  "<p><strong>Roles:</strong> the runner schedules and deadlines · probes judge · the fabric remembers.</p>",
  "FLOWS_PLACEHOLDER",
  [(L("observe.tick","tick()"), "observe"), (L("spi.HealthCheck","HealthCheck"), "plugin-contract"),
   (L("observe.pub","Observation → fabric"), "observe"), (L("fabric.kv","kv()"), "fabric")])
fl1 = flow(f1, "probeFiresAndPublishes",
  "the agent's scheduler ticks (⏰, every few seconds).",
  "sequenceDiagram\n  participant S as scheduler\n  participant R as observe.tick\n  participant P as plugin probe\n  participant F as fabric (etcd)\n  S->>R: tick\n  R->>P: probe(ctx) — deadline 4s\n  P-->>R: HealthReport (FAST/DEGRADED/DEAD)\n  R->>F: put /clusters/c/observations/... (lease-bound)\n  Note over F: every agent's cache sees it",
  f"<li>⏰ The scheduler fires {L('observe.tick','tick()')}.</li>"
  f"<li>For each armed probe, the runner calls ▶ {L('spi.HealthCheck','HealthCheck.probe()')} with the 4 s deadline; a hung probe is cut and reported UNKNOWN.</li>"
  f"<li>The runner wraps the report in an Observation ({L('spi.Observation','wire shape')}) and ⤳ {L('observe.pub','puts it on the observations board')} via {L('fabric.kv','kv().put')}, lease-bound.</li>",
  ["observe.tick", "spi.HealthCheck", "observe.pub", "fabric.kv", "spi.Observation"])

f2 = feature(c1, "evidenceSharedClusterWide (every agent holds the same board)",
  "<p>An agent boots and asks the fabric for everything under the observations prefix, then follows "
  "the watch. From that moment its in-memory cache mirrors the board. When an agent dies, its lease "
  "expires and its evidence vanishes on its own — silence becomes visible as REMOVED events. "
  "No polling, no cleanup job.</p>"
  "<p><strong>Roles:</strong> the cache mirrors and announces · the fabric stores and expires · listeners stay O(1).</p>",
  "FLOWS_PLACEHOLDER",
  [(L("cache.sub","watch on observations"), "observation-cache"), (L("cache.reads","snapshot reads"), "observation-cache"),
   (L("cache.pub","ChangeEvent"), "observation-cache"), (L("fabric.leases","leases()"), "fabric")])
fl2 = flow(f2, "cacheSeedsAndFollows",
  "an agent starts (⏰ boot) — or reconnects to the fabric.",
  "flowchart LR\n  A[boot] --> B[range read\\nobservations prefix]\n  B --> C[seed in-memory map]\n  C --> D[watch prefix]\n  D --> E[ChangeEvent per mutation\\non nw-cache-events thread]",
  f"<li>⏰ On boot the cache range-reads the observations prefix via {L('fabric.kv','kv().list')} and seeds its map.</li>"
  f"<li>It opens ⤳ {L('cache.sub','the watch')} on the same prefix; every PUT/DELETE updates the freshest-per-key map.</li>"
  f"<li>Each mutation fans out as ⤳ {L('cache.pub','ChangeEvent')} on the single cache-events thread; readers use ▶ {L('cache.reads','snapshot reads')} any time.</li>",
  ["fabric.kv", "cache.sub", "cache.pub", "cache.reads"])
fl3 = flow(f2, "silenceBecomesVisible",
  "an agent dies or loses the fabric — its lease stops being renewed.",
  "sequenceDiagram\n  participant A as dying agent\n  participant F as fabric (etcd)\n  participant C as every cache\n  A--xF: keepAlive stops\n  F->>F: lease TTL expires\n  F->>C: DELETE events (watch)\n  C->>C: ChangeEvent REMOVED\n  Note over C: trackers see silence, not stale FAST",
  f"<li>The dying agent stops renewing {L('fabric.leases','its lease')}; its heartbeat and every lease-bound Observation it published expire together ({L('life.heartbeat','heartbeat + facets')}).</li>"
  f"<li>The fabric fires DELETE watch events; every cache turns them into ⤳ {L('cache.pub','ChangeEvent REMOVED')}.</li>"
  f"<li>Consumers now see honest silence — a dead reporter cannot keep voting FAST.</li>",
  ["fabric.leases", "life.heartbeat", "cache.pub"])

# ================================================== C2 clusterConsensus
c2 = note(CONCERNS, "clusterConsensus (the vote)",
  "<p>Evidence is not a verdict. This concern reduces the shared board to one deterministic opinion "
  "per target — and refuses to kill anything on one seat's word.</p>")

f3 = feature(c2, "verdictFromEvidence (one deterministic opinion per target)",
  "<p>Anyone — a tracker, the status door, the coordinator — asks the Resolver about a target. "
  "The Resolver takes only fresh evidence (≤30 s), groups it per seat and plugin, and lets each "
  "plugin's aggregator score its own service. A seat is as sick as its sickest plugin says. "
  "The verdict comes out UP, DEGRADED, SDOWN, or ODOWN — and every agent, given the same board, "
  "computes exactly the same answer. No election for the truth; the math IS the agreement.</p>"
  "<p><strong>Roles:</strong> the Resolver buckets and reduces · plugin aggregators judge their own service · nobody stores the verdict.</p>",
  "FLOWS_PLACEHOLDER",
  [(L("vote.verdictFor","verdictFor()"), "vote"), (L("cache.reads","snapshot reads"), "observation-cache"),
   (L("spi.HealthAggregator","HealthAggregator"), "plugin-contract"), (L("mysql.agg","MysqlHealthAggregator"), "nw-mysql")])
fl4 = flow(f3, "verdictComputedOnRead",
  "any caller asks ▶ verdictFor(target) — there is no stored verdict to go stale.",
  "flowchart TD\n  A[verdictFor target] --> B[fresh obs ≤30s\\nfrom cache]\n  B --> C[buckets per seat × plugin]\n  C --> D[plugin aggregator scores 0–1]\n  D --> E[seat = worst plugin score]\n  E --> F{seats}\n  F -->|majority ≤0.25| ODOWN\n  F -->|any seat down| SDOWN\n  F -->|any <0.75| DEGRADED\n  F -->|else| UP",
  f"<li>▶ {L('vote.verdictFor','Resolver.verdictFor(target)')} pulls fresh observations from ▶ {L('cache.reads','the cache')}.</li>"
  f"<li>It buckets them per (seat, plugin) and asks each plugin's ▶ {L('spi.HealthAggregator','HealthAggregator')} — e.g. {L('mysql.agg','the MySQL one')} — to score its bucket 0–1 (NaN = silence).</li>"
  f"<li>A seat's score is the worst of its plugins; seat ≤0.25 = down, &lt;0.75 = degraded.</li>"
  f"<li>ODOWN needs a strict majority of ≥2 reporting seats; any down seat alone is only SDOWN.</li>",
  ["vote.verdictFor", "cache.reads", "spi.HealthAggregator", "mysql.agg"])

f4 = feature(c2, "deathNeedsQuorum (strikes alone cannot kill)",
  "<p>A probe reports DEAD. The tracker counts a strike. At the third strike it wants to wake the "
  "coordinator — but with require-odown on, it first asks the Resolver. If the cluster as a whole "
  "does not agree (no ODOWN), the counter is kept and nothing fires; the next DEAD re-asks. "
  "One lying seat can shout forever and the cluster stays calm. This was rehearsed live: a seat "
  "lied for 5 strikes and the verdict held at SDOWN.</p>"
  "<p><strong>Roles:</strong> the tracker counts locally · the Resolver speaks for the cluster · the coordinator only hears agreed deaths.</p>",
  "FLOWS_PLACEHOLDER",
  [(L("vote.sub","ChangeEvent subscription"), "vote"), (L("vote.verdictFor","verdictFor()"), "vote"),
   (L("vote.pub","declareTargetDead edge"), "vote"), (L("mock.protocol","mock file protocol"), "nw-mock")])
fl5 = flow(f4, "deadObservationStrikes",
  "a DEAD observation lands in the cache (⤳ ChangeEvent).",
  "sequenceDiagram\n  participant C as cache\n  participant T as FailureTracker\n  participant R as Resolver\n  participant K as coordinator\n  C->>T: ChangeEvent DEAD\n  T->>T: streak++ (trigger investigators only)\n  alt streak < 3\n    T-->>C: wait\n  else streak ≥ 3 and require-odown\n    T->>R: verdictFor(target)\n    alt ODOWN\n      T->>K: declareTargetDead (once)\n    else below ODOWN\n      T->>T: KEEP counter — re-evaluate on next DEAD\n    end\n  end",
  f"<li>⤳ {L('vote.sub','The tracker')} hears a DEAD ChangeEvent for a trigger investigator and bumps the streak; FAST/DEGRADED resets it.</li>"
  f"<li>At the threshold (default 3) it asks ▶ {L('vote.verdictFor','the Resolver')} — the quorum gate (require-odown).</li>"
  f"<li>On ODOWN it calls ↩ {L('vote.pub','declareTargetDead')} exactly once per outage; below ODOWN the counter is kept so the next DEAD re-evaluates.</li>",
  ["vote.sub", "vote.verdictFor", "vote.pub"])
fl6 = flow(f4, "minorityLieHeldAtSdown",
  "one seat's probe lies DEAD while the service is actually fine (rehearsed with the mock plugin's seat-override file).",
  "flowchart LR\n  A[seat override file\\nstate@client = dead] --> B[one seat reports DEAD\\nstrikes 5/3]\n  B --> C[Resolver: other seats FAST]\n  C --> D[verdict SDOWN — no majority]\n  D --> E[counter kept · NO failover]",
  f"<li>The rehearsal writes a lie into {L('mock.protocol','the seat-override file')} — only the CLIENT seat sees DEAD.</li>"
  f"<li>Strikes pass the threshold (5/3) but ▶ {L('vote.verdictFor','the vote')} says SDOWN: the other seats still score the target up.</li>"
  f"<li>{L('vote.pub','The coordinator edge')} never fires; the counter is kept. Verified live 2026-06-07.</li>",
  ["mock.protocol", "vote.verdictFor", "vote.pub"])

# ================================================== C3 failoverOrchestration
c3 = note(CONCERNS, "failoverOrchestration (act once, act safely)",
  "<p>An agreed death becomes an ordered sequence of commands, executed locally by the agents that "
  "own the service — behind a lock, under an epoch, through a gated door.</p>")

f5 = feature(c3, "masterFailoverEndToEnd (the cluster replaces a dead master)",
  "<p>The coordinator hears an agreed death. It races its peers for the failover lock — exactly one "
  "wins — and bumps the fencing epoch. The plugin picks the successor and writes the plan: fence "
  "the corpse, promote the chosen one. Each command travels to the agent that owns that node and "
  "runs locally behind four gates. Roles flip on the board, the machine re-arms. In the mock "
  "rehearsal the whole story took 13 seconds across two real agents.</p>"
  "<p><strong>Roles:</strong> the coordinator sequences · the plugin decides who and how · the dispatcher carries · the door guards · actions touch the service.</p>",
  "FLOWS_PLACEHOLDER",
  [(L("coord.declareTargetDead","declareTargetDead()"), "failover-coordinator"), (L("boards.gate","FailoverGate"), "boards"),
   (L("coord.spi","selector + plan ports"), "failover-coordinator"), (L("dispatch.send","send()"), "dispatcher"),
   (L("door.command","POST /agent/command"), "command-door"), (L("boards.roles","RoleStore"), "boards")])
fl7 = flow(f5, "masterDiesFailoverFires",
  "the vote delivers an agreed death (▶ declareTargetDead).",
  "sequenceDiagram\n  participant V as vote\n  participant K as coordinator\n  participant G as gate (lock+epoch)\n  participant P as plugin\n  participant D as dispatcher\n  participant A as owning agents\n  V->>K: declareTargetDead\n  K->>G: tryAcquire + bumpEpoch\n  alt lost the race\n    K-->>V: stand down (peer is acting)\n  end\n  K->>P: select(candidates) · generate(verdict, view)\n  P-->>K: plan [fence master, promote slave]\n  loop each command\n    K->>D: send(cmd, deadline)\n    D->>A: POST /agent/command\n    A-->>D: result\n  end\n  K->>K: flip roles · re-arm",
  f"<li>▶ {L('coord.declareTargetDead','declareTargetDead')} marshals onto the coordinator thread; a quarantined target stands down immediately (the P3 guard).</li>"
  f"<li>The machine takes {L('boards.gate','the failover gate')} — conditional-txn lock + epoch bump via {L('fabric.txns','txns()')}; losers stand down.</li>"
  f"<li>It asks the plugin through ▶ {L('coord.spi','the selector and plan ports')} — for MySQL that is {L('mysql.selector','GTID-best slave')} and {L('mysql.plangen','fence-then-promote')} — passing the Resolver's live verdict.</li>"
  f"<li>Each plan step goes out ▶ {L('dispatch.send','Dispatcher.send')} → the owning agent's door → a local action ({L('mysql.actions','the SQL actions')}).</li>"
  f"<li>On success the coordinator updates ▶ {L('boards.roles','the roles board')} (old master → quarantined, new → master) and re-arms — the new machine is published before the old closes.</li>",
  ["coord.declareTargetDead", "boards.gate", "fabric.txns", "coord.spi", "mysql.selector",
   "mysql.plangen", "dispatch.send", "mysql.actions", "boards.roles"])
fl8 = flow(f5, "commandCrossesTheDoor",
  "the coordinator dispatches one plan step to the agent that owns the target node.",
  "sequenceDiagram\n  participant K as dispatcher\n  participant Dir as directory\n  participant E as door (target agent)\n  participant X as FailoverAction\n  K->>Dir: lookup(node) → commandUrl\n  K->>E: POST /agent/command {commandClass, command}\n  E->>E: gate 1 identity · gate 2 whitelist\n  E->>E: gate 3 epoch · gate 4 idempotency\n  E->>X: execute(ctx) — locally\n  X-->>E: CommandResultEvent\n  E-->>K: ResultEnvelope",
  f"<li>▶ {L('dispatch.send','send()')} resolves the target through ▶ {L('boards.directory','the directory')} and ⤳ {L('dispatch.pub','POSTs the CommandEnvelope')} (frozen wire).</li>"
  f"<li>▶ {L('door.command','The door')} runs the four gates — identity, descriptor whitelist, epoch, idempotency; a stale coordinator gets CommandRefused.</li>"
  f"<li>The matching ▶ {L('door.spi','FailoverAction')} executes locally and the typed result rides back ↩ as the ResultEnvelope.</li>",
  ["dispatch.send", "boards.directory", "dispatch.pub", "door.command", "door.spi"])

# ================================================== C4 agentLifecycle
c4 = note(CONCERNS, "agentLifecycle (join, stay, answer)",
  "<p>How an agent comes up, stays visibly alive, and answers humans and machines about itself.</p>")

f6 = feature(c4, "agentBootsToAlive (a node joins the cluster)",
  "<p>The process starts. The lifecycle machine connects the fabric, asks the plugins what runs "
  "on this host, arms only the probes that apply, and goes ALIVE. From then on a lease-bound "
  "heartbeat plus the facet list make the agent visible — and its disappearance automatic.</p>"
  "<p><strong>Roles:</strong> the lifecycle machine sequences boot · facet detectors say what is here · the fabric makes presence honest.</p>",
  "FLOWS_PLACEHOLDER",
  [(L("spi.FacetDetector","FacetDetector"), "plugin-contract"), (L("life.heartbeat","heartbeat + facets"), "lifecycle"),
   (L("fabric.leases","leases()"), "fabric")])
fl9 = flow(f6, "agentStartsAndArms",
  "the nw-agent process starts (⏰).",
  "flowchart LR\n  A[start] --> B[connect fabric]\n  B --> C[DETECTING_FACETS\\nplugins detect()]\n  C --> D[ARMING_INVESTIGATORS\\nmatching probes only]\n  D --> E[ALIVE]\n  E --> F[heartbeat lease +\\nfacets on the board]",
  f"<li>⏰ Boot drives the lifecycle StateMap ({L('sm.StateMap','the DSL')}): connect → detect → arm → ALIVE.</li>"
  f"<li>Every plugin's ▶ {L('spi.FacetDetector','FacetDetector.detect()')} reports what runs here (e.g. mysql-master-here); probes whose facets do not match stay dormant.</li>"
  f"<li>The agent ⤳ {L('life.heartbeat','publishes heartbeat + facets')} on a {L('fabric.leases','lease')} — presence that cleans itself up.</li>",
  ["sm.StateMap", "spi.FacetDetector", "life.heartbeat", "fabric.leases"])

f7 = feature(c4, "statusAndReadiness (humans curl, machines probe)",
  "<p>An operator curls /agent/info and reads phase, facets, verdicts, strikes in plain JSON. "
  "Compose and Kubernetes hit /q/health/ready instead and get a yes/no: fabric reachable and the "
  "machine in a working phase. Two doors, two audiences, one truth.</p>"
  "<p><strong>Roles:</strong> the info door narrates · the readiness check decides · the fabric is the dependency that matters.</p>",
  "FLOWS_PLACEHOLDER",
  [(L("life.info","GET /agent/info"), "lifecycle"), (L("life.ready","GET /q/health/ready"), "lifecycle")])
fl10 = flow(f7, "readinessAnswersCompose",
  "an orchestrator probes ▶ GET /q/health/ready.",
  "flowchart LR\n  A[GET /q/health/ready] --> B{fabric ping?}\n  B -->|no| DOWN[503]\n  B -->|yes| C{phase working?}\n  C -->|no| DOWN\n  C -->|yes| UP[200]",
  f"<li>▶ {L('life.ready','FabricReadyCheck')} pings the fabric and reads the lifecycle phase.</li>"
  f"<li>Ready only when both hold; the response names which half failed. Humans use ▶ {L('life.info','/agent/info')} for the full story.</li>",
  ["life.ready", "life.info"])

# ================================================== C5 pluginExtension
c5 = note(CONCERNS, "pluginExtension (teach the watcher a new service)",
  "<p>The platform knows nothing about MySQL, FreeSWITCH, or sigtran. A plugin brings probes, "
  "an aggregator, a selector, a plan, and actions — the core never changes.</p>")

f8 = feature(c5, "mysqlMasterSlaveWatching (the first real plugin)",
  "<p>A MySQL pair joins the cluster. The canary probe asks the master exactly what an application "
  "would ask, from every agent that can reach it. On the database hosts the local probe reads the "
  "replication confession. The aggregator turns that evidence into the MySQL vote. When the vote "
  "kills the master, the selector picks the GTID-best slave and the plan fences then promotes.</p>"
  "<p>🚧 Built today: role 1 (canary + confession + vote + actions). Roles 2 and 3 — the master-side "
  "watcher and the slave's master-canary with lag policy — are designed, not built.</p>"
  "<p><strong>Roles:</strong> probes ask MySQL · the aggregator votes · selector and plan decide the succession · SQL actions execute.</p>",
  f"FLOWS_PLACEHOLDER",
  [(L("mysql.remote","mysql.remote.client"), "nw-mysql"), (L("mysql.local","mysql.local"), "nw-mysql"),
   (L("mysql.agg","MysqlHealthAggregator"), "nw-mysql"), (L("mysql.actions","the five SQL actions"), "nw-mysql")])
fl11 = flow(f8, "mysqlCanaryGradesTheMaster",
  "the observe tick reaches the MySQL canary on any agent that can see the master (⏰).",
  "sequenceDiagram\n  participant R as observe.tick\n  participant P as mysql.remote.client\n  participant M as MySQL master\n  participant F as fabric\n  R->>P: probe(ctx)\n  P->>M: SHOW DATABASES (canary SQL)\n  M-->>P: rows / error / latency\n  P-->>R: FAST · DEGRADED · DEAD\n  R->>F: Observation (CLIENT seat)",
  f"<li>⏰ {L('observe.tick','tick()')} runs ▶ {L('mysql.remote','the CLIENT canary')}: real credentials, real SQL, graded by latency thresholds.</li>"
  f"<li>On the database hosts ▶ {L('mysql.local','the LOCAL_SELF confession')} adds replication state, read-only flag, GTID position.</li>"
  f"<li>Both land as ⤳ {L('observe.pub','Observations')}; later, ▶ {L('mysql.agg','the aggregator')} scores them when the vote asks. The promotion itself is the borrowed flow masterDiesFailoverFires (home: masterFailoverEndToEnd).</li>",
  ["observe.tick", "mysql.remote", "mysql.local", "observe.pub", "mysql.agg"])

f9 = feature(c5, "mockFullPipelineRehearsal (the harness that proves the loop)",
  "<p>No real service needed: the mock plugin's 'service' is a pair of files per node. Edit "
  "&lt;node&gt;.state to dead and the whole platform reacts — probes report, strikes mount, the vote "
  "agrees, the coordinator fences and promotes by rewriting the files. Two real agents on one "
  "box rehearse the entire story in 13 seconds. The rehearsal found two real core bugs "
  "(the re-arm race and the missing quarantine guard).</p>"
  "<p><strong>Roles:</strong> the files play the service · three probes give three seats · the plugin's actions make the failover observable.</p>",
  "FLOWS_PLACEHOLDER",
  [(L("mock.protocol","file protocol"), "nw-mock"), (L("mock.probes","three probes"), "nw-mock"),
   (L("mock.orch","orchestrator pieces"), "nw-mock")])
fl12 = flow(f9, "mockMasterKilledPromotes",
  "a human (or a test) writes dead into the mock master's state file (⏰).",
  "flowchart TD\n  A[echo dead > node.state] --> B[three probes report DEAD\\nthree seats]\n  B --> C[strikes reach 3 · vote = ODOWN]\n  C --> D[coordinator: gate + plan]\n  D --> E[fence: file → quarantined\\npromote: slave file → master]\n  E --> F[roles board flips ·\\nprobes now grade the new master]",
  f"<li>⏰ The state file flips to dead ({L('mock.protocol','the file protocol')}); ▶ {L('mock.probes','all three seats')} start reporting DEAD.</li>"
  f"<li>Strikes pass threshold, the vote reaches ODOWN (majority of seats) — the borrowed flows deadObservationStrikes and verdictComputedOnRead, home: clusterConsensus.</li>"
  f"<li>The coordinator runs the borrowed flow masterDiesFailoverFires; ▶ {L('mock.orch','the mock fence + promote actions')} rewrite the files, so the promotion is visible on disk and the roles board.</li>",
  ["mock.protocol", "mock.probes", "mock.orch"])

# -------- now fix the Flows index inside each feature page (needs flow ids)
FEATURE_FLOWS = {
  f1: [(fl1, "probeFiresAndPublishes", "the scheduler ticks and evidence lands on the board")],
  f2: [(fl2, "cacheSeedsAndFollows", "an agent boots and mirrors the board"),
       (fl3, "silenceBecomesVisible", "a dead agent's evidence expires by itself")],
  f3: [(fl4, "verdictComputedOnRead", "anyone asks; the same answer everywhere")],
  f4: [(fl5, "deadObservationStrikes", "strikes mount, the quorum gate decides"),
       (fl6, "minorityLieHeldAtSdown", "one lying seat cannot kill a healthy service")],
  f5: [(fl7, "masterDiesFailoverFires", "lock, plan, fence, promote, flip"),
       (fl8, "commandCrossesTheDoor", "one command, four gates, local execution")],
  f6: [(fl9, "agentStartsAndArms", "boot to ALIVE with only the right probes")],
  f7: [(fl10, "readinessAnswersCompose", "yes/no for orchestrators")],
  f8: [(fl11, "mysqlCanaryGradesTheMaster", "real SQL, graded evidence"),
       ("BORROW:" + fl7, "masterDiesFailoverFires", "borrowed — home: masterFailoverEndToEnd")],
  f9: [(fl12, "mockMasterKilledPromotes", "kill a file, watch the cluster heal"),
       ("BORROW:" + fl7, "masterDiesFailoverFires", "borrowed — home: masterFailoverEndToEnd"),
       ("BORROW:" + fl5, "deadObservationStrikes", "borrowed — home: deathNeedsQuorum")],
}
for fid, flows in FEATURE_FLOWS.items():
    cur = req("GET", f"/notes/{fid}/content", ctype="text/plain")
    items = ""
    for x, name, hook in flows:
        nid = x.split(":")[1] if x.startswith("BORROW:") else x
        items += f'<li><a class="reference-link" href="#root/{nid}">{name}</a> — {hook}</li>'
    set_content(fid, cur.replace("FLOWS_PLACEHOLDER", items))
print("feature flow indexes fixed")

# -------- Pass C: Used-by backlinks on every touched leaf
for key, flows in USED_BY.items():
    nid = IDS[key]
    cur = req("GET", f"/notes/{nid}/content", ctype="text/plain")
    seen, links = set(), []
    for fid, title in flows:
        if fid not in seen:
            seen.add(fid)
            links.append(f'<a class="reference-link" href="#root/{fid}">{title}</a>')
    set_content(nid, cur + f"<p><em>Used by: {' · '.join(links)}</em></p>")
print(f"backlinks on {len(USED_BY)} leaves")

IDS.update({"c1": c1, "c2": c2, "c3": c3, "c4": c4, "c5": c5})
json.dump(IDS, open("/tmp/nw-v5-ids.json", "w"), indent=1)
print("DONE")
