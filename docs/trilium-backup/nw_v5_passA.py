#!/usr/bin/env python3
"""Pass A — Doc-V5.0 NightWatcher umbrella: scaffold + service pages + leaves."""
import json, urllib.request, sys

TOKEN = open("/tmp/trilium-etapi-token").read().strip().splitlines()[0]
B = "http://10.9.9.6:7081/etapi"
IDS = {}

def req(method, path, body=None, ctype="application/json"):
    data = None
    if body is not None:
        data = body.encode() if isinstance(body, str) else json.dumps(body).encode()
    r = urllib.request.Request(B + path, data=data, method=method,
        headers={"Authorization": TOKEN, "Content-Type": ctype})
    with urllib.request.urlopen(r) as resp:
        raw = resp.read()
        return json.loads(raw) if raw and ctype == "application/json" else None

def note(parent, title, content, ntype="text"):
    n = req("POST", "/create-note", {"parentNoteId": parent, "title": title,
                                     "type": ntype, "content": content})
    nid = n["note"]["noteId"]
    print(f"  + {title} [{nid}]")
    return nid

def leaf(parent, title, desc, sig, bullets):
    """One leaf per item: one-line description, signature, bullets."""
    b = "".join(f"<li>{x}</li>" for x in bullets)
    html = f"<p>{desc}</p>"
    if sig:
        html += f"<p><code>{sig}</code></p>"
    if b:
        html += f"<ul>{b}</ul>"
    return note(parent, title, html)

# ---------------------------------------------------------------- scaffold
PARENT = "6pER501hjWYt"  # same parent as Doc-V4.0
root = note(PARENT, "Doc-V5.0 — NightWatcher (code-master)",
  "<p>Live documentation for night-watcher — the Consul-class HA platform: agents "
  "probe services through plugins, report evidence to etcd, vote, and act. "
  "Built to the code-master method: <strong>concerns → features → flows → service leaves</strong>. "
  "Previous set: Doc-V4.0 (read-only trail).</p>"
  "<p><em>Code: routesphere/night-watcher · nw-agent = Java 21 / Quarkus maven multi-module · "
  "59 unit tests green at commit 88a610e.</em></p>")
IDS["root"] = root
concerns = note(root, "concerns", "<p>Folders of related work. Each concern holds features; "
  "each feature tells its story and owns its flows.</p>")
services = note(root, "services", "<p>Every service lives in ONE home under its runtime unit. "
  "Slim dictionary pages: one leaf per api/spi/publish/subscribe item.</p>")
IDS["concerns"] = concerns
IDS["services"] = services

nwagent = note(services, "nw-agent (runtime unit — the HA agent)",
  "<p>One agent per host. Java 21 / Quarkus, bundled etcd as the coordination fabric. "
  "Maven modules: nw-spi · nw-fabric-api · nw-fabric-etcd · nw-core · plugins/nw-mysql · "
  "plugins/nw-mock · nw-dist. Build: <code>cd nw-agent &amp;&amp; mvn -DskipTests clean package</code> · "
  "tests: <code>mvn test</code> (59).</p>"
  "<p>Ports: 7102 HTTP (status + command door) · 7103 reserved mTLS gRPC · 2379/2380 etcd.</p>")
IDS["nw-agent"] = nwagent
dash = note(services, "dashboard (runtime unit — React security dashboard) 🚧",
  "<p>🚧 Not yet migrated to code-master docs. React 18 + Vite + MUI, dev port 7100, "
  "Keycloak JWT auth. Older docs: night-watcher/docs/dashboard/.</p>")
bundle = note(services, "security-bundle (runtime unit — transitional) 🚧",
  "<p>🚧 Transitional all-in-one container (nginx+ModSec, Wazuh, CrowdSec, Fail2Ban, watchdog) "
  "mid-migration to docker-compose, one service per container. Not part of the HA control plane. "
  "Older docs: docs/security-bundle/ (deprecation-bannered).</p>")

def svc(title, desc_html):
    return note(nwagent, title, desc_html)

def folder(parent, name, intro):
    return note(parent, name, f"<p>{intro}</p>")

# ---------------------------------------------------------------- fabric
s = svc("fabric (coordination KV — etcd)",
  "<p>The cluster bulletin board. A thin port over etcd: keys, leases, leader election, "
  "conditional transactions. Everything agents share crosses this seam.</p>"
  "<p><em>Modules: nw-fabric-api (port) + nw-fabric-etcd (jetcd impl). "
  "publishes/subscriptions: none — a library; callers watch keys themselves.</em></p>")
IDS["svc.fabric"] = s
g = folder(s, "api", "What the fabric offers. One leaf per surface.")
IDS["fabric.kv"] = leaf(g, "kv() — get · put · list · watch · delete",
  "Read and write keys, range-read a prefix, watch a prefix for changes.",
  "FabricKV kv() · get(key) · put(key,value[,lease]) · list(prefix) · watch(prefix,handler) · delete(key)",
  ["watch delivers KeyEvent{PUT|DELETE, key, value} on the fabric watcher thread",
   "put with a Lease makes the key disappear when the lease dies — heartbeats use this",
   "absent key has mod-revision 0 (the txn guard relies on it)"])
IDS["fabric.leases"] = leaf(g, "leases() — grant · keepAlive · revoke",
  "Time-bound ownership: a lease keeps keys alive only while its holder keeps pinging.",
  "FabricLease leases() · grant(ttl) · keepAlive(lease) · revoke(lease)",
  ["keepAlive returns an AutoCloseable pinger; closing it lets the lease expire",
   "lease expiry deletes attached keys and fires DELETE watch events"])
IDS["fabric.locks"] = leaf(g, "locks() — campaign · resign · observeLeader",
  "Leader election on a path; one campaigner wins, the rest queue.",
  "FabricLock locks() · campaign(path,selfIdentity) · resign(election) · observeLeader(path,onChange)",
  ["used by the failover gate so only one coordinator acts per outage"])
IDS["fabric.txns"] = leaf(g, "txns() — conditional transaction builder",
  "Compare-and-swap across keys: guards on mod-revision or value, then puts/deletes atomically.",
  "txns().begin().ifModRevisionEquals(k,rev).thenPut(...).elsePut(...).commit() → succeeded()",
  ["the epoch bump and lock acquire ride this — losers see succeeded()=false and stand down"])
note(s, "drift report",
  "<p>✅ In-memory twin exists: FakeFabric in nw-core testkit mirrors mod-revision, lease expiry, "
  "txns, watch fan-out (P2). ⚠ ping() lives only on EtcdFabric (impl), so readiness casts to the "
  "impl — a port-level health method would remove the cast.</p>")

# ---------------------------------------------------------------- spi
s = svc("plugin-contract (nw-spi — pure types)",
  "<p>The plugin SPI. Pure types, zero dependencies. A plugin teaches the watcher a new service "
  "by implementing these and shipping a descriptor. FROZEN SEAM — changes need architect ratification.</p>"
  "<p><em>publishes/subscriptions: none — a types-only library.</em></p>")
IDS["svc.spi"] = s
g = folder(s, "api", "The contract types. Observe side first, then act side.")
IDS["spi.HealthCheck"] = leaf(g, "HealthCheck — a probe",
  "One way of looking at a service from one vantage. The observe side runs every registered probe on its cadence.",
  "id() · vantage() · descriptor() · eventType() · probe(ProbeContext) → HealthReport<T>",
  ["vantage = LOCAL_SELF (the service confesses) · CLIENT (a canary asks like an app) · REMOTE_PEER (a peer looks across)",
   "the typed event T carries plugin-specific evidence (latency, replication state, …)",
   "dormant unless the node's facets say the probe applies"])
IDS["spi.FacetDetector"] = leaf(g, "FacetDetector — what runs here?",
  "Tells the agent which facets this host carries, so only the right probes and actions wake up.",
  "descriptor() · declaredFacets() · detect() → Set<String>",
  ["facet strings like mysql-master-here / mysql-slave-here drive role bookkeeping too"])
IDS["spi.HealthAggregator"] = leaf(g, "HealthAggregator — score a seat",
  "Pure function: one (target, vantage) bucket of fresh observations in, one score 0–1 out. This is how a plugin votes.",
  "descriptor() · scoreVantage(VantageBucket<T>) → double (NaN = silence)",
  ["deterministic — every agent computes the same score from the same board",
   "without one, the Resolver has no opinion on that plugin's targets"])
IDS["spi.CandidateSelector"] = leaf(g, "CandidateSelector — who gets promoted",
  "Picks the best successor from candidate observations.",
  "descriptor() · select(List<ObservationView<T>>, SelectionContext) → Optional<NodeId>",
  ["mysql impl compares GTID positions; mock impl picks the healthiest file"])
IDS["spi.FailoverPlanGenerator"] = leaf(g, "FailoverPlanGenerator — the recipe",
  "Turns a verdict plus the cluster view into an ordered plan of commands.",
  "descriptor() · generate(Verdict, ClusterView<T>) → Plan<P>",
  ["receives the Resolver's live verdict since P3 (was hardcoded SDOWN)"])
IDS["spi.FailoverAction"] = leaf(g, "FailoverAction — do one thing locally",
  "Executes one command on the agent that owns the service: fence, promote, set read-only, …",
  "id() · commandType() · allowedRoles() · checkIdempotent(ctx) · execute(ctx) → CommandResultEvent",
  ["runs only behind the command door's four gates",
   "checkIdempotent lets a replayed command answer without re-doing the work"])
IDS["spi.StatefulAction"] = leaf(g, "StatefulAction — resumable action",
  "An action that can snapshot progress and resume after an agent restart.",
  "resumeFromState(ctx, persistedState) · snapshotState()",
  ["no production implementor yet — mysql actions are single-shot SQL"])
IDS["spi.ClusterCondition"] = leaf(g, "ClusterCondition — a named cluster-wide check",
  "Evaluates a predicate over the cluster topology (e.g. quorum present).",
  "descriptor() · id() · evaluate(ClusterTopology) → boolean",
  ["no production implementor yet"])
IDS["spi.PluginDescriptor"] = leaf(g, "PluginDescriptor — the plugin's identity card",
  "Identifies the plugin and whitelists its event and command types.",
  "pluginId() · pluginVersion() · serviceType() · healthEventType() · commandEventTypes()",
  ["the command door checks an incoming commandClass against this whitelist — never arbitrary class loading"])
IDS["spi.Observation"] = leaf(g, "Observation — one piece of evidence (wire shape)",
  "The envelope every probe result travels in: who saw what, from where, when. FROZEN — this is the etcd wire.",
  "Observation<T>{id, cluster, target, publisher, investigator, vantage, state, latencyNanos, freshness, pluginId, pluginVersion, event:T}",
  ["state = FAST · DEGRADED · DEAD · UNKNOWN",
   "serialized as JSON under /clusters/&lt;cluster&gt;/observations/…"])
note(s, "drift report",
  "<p>✅ Manifest truth restored in P3 (plugin.yml synced to code, phantom ids removed). "
  "⚠ StatefulAction and ClusterCondition have no production implementor — declared, honest, unused. "
  "⚠ plugin.yml is descriptive only; no loader enforces it (open V4 item).</p>")

# ---------------------------------------------------------------- sm
s = svc("statemap-dsl (sm — tiny state machine kit)",
  "<p>The in-repo StateMap DSL. High-level entry classes (lifecycle, coordinator) describe "
  "themselves as named states and transitions, so the top of the class reads as business phases.</p>"
  "<p><em>publishes/subscriptions: none — a library.</em></p>")
IDS["svc.sm"] = s
g = folder(s, "api", "One surface.")
IDS["sm.StateMap"] = leaf(g, "StateMap — builder · fire · close",
  "Immutable transition table from a fluent builder; events fire on one scheduler thread; handlers return the next state.",
  "StateMap.builder().state(...).on(event, handler)…build() · fire(event) · close()",
  ["unhandled (state, event) pairs are silently dropped, by design",
   "fire after close is rejected — the coordinator re-arm publishes the new machine before closing the old (the P3 race fix)",
   "every transition logs at debug"])
note(s, "drift report", "<p>✅ Unit-tested (StateMapTest, 5 — includes the fire-after-close regression).</p>")

# ---------------------------------------------------------------- config
s = svc("tenant-config (per-tenant profile loader)",
  "<p>Loads tenant + profile YAML into Quarkus config, routesphere-style: application.properties "
  "only picks the tenant and active profile; the real settings live in "
  "config/tenants/&lt;tenant&gt;/&lt;profile&gt;/profile-&lt;profile&gt;.yml.</p>"
  "<p><em>publishes/subscriptions: none.</em></p>")
IDS["svc.config"] = s
g = folder(s, "api", "One surface.")
IDS["config.source"] = leaf(g, "TenantProfileConfigSource",
  "A MicroProfile ConfigSource that resolves the tenant profile YAML at startup.",
  "ordinal above application.properties · keys nw.* · env overrides win (NW_CLUSTER_NAME, NW_MYSQL_PASSWORD, NW_BIND_HOST)",
  ["secrets only via env — never in YAML",
   "bind fail-secure: 127.0.0.1 unless NW_BIND_HOST is set; never 0.0.0.0 by default"])
note(s, "drift report", "<p>✅ Stable. Tenant mock (profile dev) is the rehearsal recipe.</p>")

# ---------------------------------------------------------------- observe
s = svc("observe (probe runner)",
  "<p>The observe side of the loop. On every tick it runs each armed probe with a deadline "
  "and puts the resulting Observation on the fabric. Dormant probes (facet mismatch) are skipped.</p>")
IDS["svc.observe"] = s
g = folder(s, "api", "")
IDS["observe.tick"] = leaf(g, "tick() — run armed probes once",
  "Scheduler entry point: probe, wrap, publish — one round.",
  "InvestigatorRunner.tick() · per-probe deadline nw.agent.probe-deadline-sec (default 4)",
  ["a hung probe is cut at the deadline and reported UNKNOWN, never blocks the round"])
g = folder(s, "spi", "Ports this service consumes (host injects implementations).")
IDS["observe.spi.checks"] = leaf(g, "HealthCheck (all CDI instances)",
  "Every registered probe, filtered by the node's facets.", "",
  ["see plugin-contract / HealthCheck for the port shape"])
g = folder(s, "publishes", "")
IDS["observe.pub"] = leaf(g, "Observation JSON → fabric (the observations board)",
  "Each probe result lands at a deterministic key so every agent's cache sees the same board.",
  "put /clusters/&lt;cluster&gt;/observations/&lt;target&gt;/&lt;publisher&gt;/&lt;investigator&gt; (lease-bound)",
  ["FROZEN wire — key schema + Observation JSON envelope",
   "lease-bound: a dead agent's evidence evaporates by itself"])
note(s, "drift report", "<p>✅ Deadline seam injected + tested (P2). ⚠ no dedicated unit test for "
  "the runner loop itself — covered indirectly by the mock-tenant rehearsal.</p>")

# ---------------------------------------------------------------- cache
s = svc("observation-cache (every agent's copy of the board)",
  "<p>Seeds from a range read, then follows the watch. Keeps the freshest Observation per "
  "(target, publisher, investigator) in memory and tells listeners about changes.</p>")
IDS["svc.cache"] = s
g = folder(s, "api", "")
IDS["cache.reads"] = leaf(g, "snapshot reads — all · byTarget · byPublisher · viewOf",
  "Read the in-memory board without touching etcd.",
  "all() · byTarget(target) · byPublisher(publisher) · viewOf(target, eventType) → ObservationView<T> · size()",
  ["viewOf gives a plugin-typed window — selectors and plan generators use it"])
IDS["cache.addListener"] = leaf(g, "addListener(Consumer<ChangeEvent>)",
  "Subscribe to board changes in-process.",
  "ChangeEvent{kind: ADDED|UPDATED|REMOVED, key:(target,publisher,investigator), observation}",
  ["delivered on the single nw-cache-events executor thread (P2 seam) — listeners must stay O(1)"])
g = folder(s, "subscriptions", "")
IDS["cache.sub"] = leaf(g, "fabric watch on the observations prefix",
  "The cache IS a subscriber: range-read seed, then watch /clusters/&lt;cluster&gt;/observations/.",
  "PUT → ADDED/UPDATED · DELETE (lease expiry) → REMOVED",
  ["lease expiry turning into REMOVED is what makes silence visible"])
g = folder(s, "publishes", "")
IDS["cache.pub"] = leaf(g, "ChangeEvent (in-proc)",
  "One typed event per board mutation, fanned out to listeners.",
  "ChangeEvent{kind, ObsKey, Observation<?>}",
  ["consumers today: vote (FailureTracker)"])
note(s, "drift report", "<p>✅ Unit-tested (ObservationCacheTest, 8). Listener executor seam landed in P2; "
  "shutdown drains in stop().</p>")

# ---------------------------------------------------------------- vote
s = svc("vote (resolver + failure-tracker)",
  "<p>The consensus stage. The Resolver reduces fresh evidence to a deterministic per-target "
  "verdict; the FailureTracker counts local death strikes and — gated by the vote — wakes the coordinator.</p>")
IDS["svc.vote"] = s
g = folder(s, "api", "")
IDS["vote.verdictFor"] = leaf(g, "Resolver.verdictFor(target) / all()",
  "Compute the verdict for one target (or all) from fresh observations. Pure function of cache + clock.",
  "verdictFor(target) → Optional<Verdict> · all() → Map<target, TargetVerdict{verdict, seatScores, seatsReporting, seatsDown}>",
  ["fresh = ≤ nw.failover.resolver-freshness-sec (default 30)",
   "buckets per (target, seat, plugin) → plugin aggregator scores 0–1 → a seat's score = the WORST of its plugins",
   "seat ≤0.25 = down · &lt;0.75 = degraded · ODOWN = strict majority of ≥2 reporting seats · any seat down = SDOWN",
   "deterministic: same board + same window ⇒ same verdict on every agent"])
IDS["vote.streakFor"] = leaf(g, "FailureTracker.streakFor(target, publisher, investigator)",
  "Read the current strike count (status door uses it).",
  "streakFor(...) → int", [])
g = folder(s, "subscriptions", "")
IDS["vote.sub"] = leaf(g, "ChangeEvent from the observation-cache",
  "DEAD → strike++ (trigger investigators only) · FAST/DEGRADED → reset · UNKNOWN/REMOVED → silence.",
  "threshold nw.failover.failure-threshold (default 3)",
  ["runs on the cache event thread — O(1) counter math only"])
g = folder(s, "publishes", "")
IDS["vote.pub"] = leaf(g, "declareTargetDead (sync api edge → coordinator)",
  "At threshold AND (when require-odown) a majority verdict, the tracker calls the coordinator once per outage.",
  "coordinator.declareTargetDead(MasterDeadDetected{target, investigator, strikes})",
  ["with require-odown=true a sub-ODOWN vote KEEPS the counter — the next DEAD re-evaluates",
   "documented honesty: a sync call where the model would emit an event (single consumer, accepted)"])
note(s, "drift report", "<p>✅ Unit-tested: ResolverTest (11) + FailureTrackerTest (7) — determinism, "
  "0.25/0.75 edges, strict-majority math, hold-and-re-evaluate. Verified live 2026-06-07: minority lie "
  "held at SDOWN; true death → ODOWN.</p>")

# ---------------------------------------------------------------- coordinator
s = svc("failover-coordinator (the act sequence)",
  "<p>A StateMap: one outage in, an ordered failover out. Acquires the gate, bumps the epoch, "
  "asks the plugin for candidate + plan, dispatches each command to the owning agent, flips roles, re-arms.</p>")
IDS["svc.coordinator"] = s
g = folder(s, "api", "")
IDS["coord.declareTargetDead"] = leaf(g, "declareTargetDead(MasterDeadDetected)",
  "The one entry point. Marshals onto the coordinator thread and starts the machine.",
  "declareTargetDead(event) · re-entrant safe: quarantined targets stand down (P3 guard)",
  ["caller: vote (FailureTracker)"])
IDS["coord.status"] = leaf(g, "status reads — currentPhase() · lastTrigger()",
  "Human-shaped status for /agent/info.", "currentPhase() → String · lastTrigger() → MasterDeadDetected", [])
g = folder(s, "spi", "Ports consumed (plugin-matched by pluginId / plan-plugin config).")
IDS["coord.spi"] = leaf(g, "CandidateSelector · FailoverPlanGenerator · Resolver verdict",
  "The plugin decides WHO and HOW; the coordinator only sequences.",
  "select(candidates, ctx) · generate(currentVerdict, view) — verdict is the Resolver's live one (P3)",
  ["plan plugin pinned by nw.failover.plan-plugin when more than one is present"])
note(s, "drift report", "<p>✅ Re-arm race fixed (new machine published before old closes) + "
  "quarantined guard (both found by the mock rehearsal). ✅ FailoverGateTest (6). "
  "⚠ no end-to-end unit test of the full machine — rehearsed via tenant mock instead.</p>")

# ---------------------------------------------------------------- boards
s = svc("coordination-boards (roles · directory · failover gate)",
  "<p>Three small shared boards on the fabric. Who is master, who is here, and who may act.</p>")
IDS["svc.boards"] = s
g = folder(s, "api", "")
IDS["boards.roles"] = leaf(g, "RoleStore — the roles board",
  "Reads and writes /clusters/&lt;cluster&gt;/roles/&lt;node&gt; (values master · slave · quarantined).",
  "put(node, role) · all() → Map<role, nodes> · rolesOf(node)",
  ["role inferred from facets too: *-master-here / *-slave-here (plugin-agnostic since P2)",
   "FROZEN key schema + values"])
IDS["boards.directory"] = leaf(g, "AgentDirectory — who is here",
  "Lease-bound presence + command URL per agent.",
  "lookup(node) → Optional<AgentEntry{node, commandUrl, facets}> · keys /clusters/&lt;c&gt;/agents/&lt;node&gt; + /agents/&lt;node&gt;/{heartbeat,facets}",
  ["the dispatcher resolves the target agent's door through this"])
IDS["boards.gate"] = leaf(g, "FailoverGate — lock + epoch",
  "One coordinator per outage: conditional-txn lock plus a fencing epoch.",
  "tryAcquire() · release() · holder() · epoch() · bumpEpoch() · keys /clusters/&lt;c&gt;/failover/{lock,epoch}",
  ["epoch rides every command so a stale coordinator's orders are refused"])
note(s, "drift report", "<p>✅ RoleStoreTest (6) + FailoverGateTest (6). "
  "⚠ directory expiry path untested at unit level (open V4 item).</p>")

# ---------------------------------------------------------------- dispatch
s = svc("command-dispatcher (send a command to the owning agent)",
  "<p>The coordinator's courier. Wraps a typed command in the wire envelope, finds the target "
  "agent's door in the directory, POSTs, and returns the typed result.</p>")
IDS["svc.dispatch"] = s
g = folder(s, "api", "")
IDS["dispatch.send"] = leaf(g, "send(CommandEvent, deadline) → CommandResultEvent",
  "One call: resolve agent, envelope, POST, parse result, or throw DispatchException.",
  "Dispatcher.send(cmd, Duration deadline)",
  ["http client field is package-private — the unit-test seam (P2)"])
g = folder(s, "publishes", "")
IDS["dispatch.pub"] = leaf(g, "CommandEnvelope over HTTP (agent-to-agent wire)",
  "FROZEN wire: the command class name plus the raw JSON command tree.",
  "POST http://&lt;target-agent&gt;:7102/agent/command · CommandEnvelope{commandClass, command} → ResultEnvelope",
  ["commandClass is checked against the plugin descriptor whitelist on the far side — never arbitrary loading"])
note(s, "drift report", "<p>⚠ stub-server unit test still open (V4 item) — exercised end-to-end by the mock rehearsal.</p>")

# ---------------------------------------------------------------- door
s = svc("command-door (receive and gate a command)",
  "<p>The act side's only entrance. Four gates, then the local action runs.</p>")
IDS["svc.door"] = s
g = folder(s, "api", "")
IDS["door.command"] = leaf(g, "POST /agent/command — the four gates",
  "Identity → whitelist → epoch → idempotency, then execute the matching FailoverAction locally.",
  "command(CommandEnvelope) → ResultEnvelope · port 7102 (mTLS gRPC on 7103 is the future hardening)",
  ["gate 1 identity: caller must be a directory member",
   "gate 2 whitelist: commandClass ∈ descriptor.commandEventTypes()",
   "gate 3 epoch: stale coordinator → CommandRefused",
   "gate 4 idempotency: replayed command answers from the IdempotencyStore"])
g = folder(s, "spi", "")
IDS["door.spi"] = leaf(g, "FailoverAction (all CDI instances, matched by commandType)",
  "The door finds the one action whose commandType matches and runs it.",
  "ActionRegistry: commandType → FailoverAction",
  ["see plugin-contract / FailoverAction"])
note(s, "drift report", "<p>✅ Wire + gates exercised by the mock rehearsal (fence + promote crossed "
  "two real agents). ⚠ per-gate unit tests open.</p>")

# ---------------------------------------------------------------- lifecycle
s = svc("agent-lifecycle (boot · presence · status)",
  "<p>The agent's own state machine: connect fabric, detect facets, arm investigators, go ALIVE, "
  "keep the heartbeat. Plus the human and machine status doors.</p>")
IDS["svc.lifecycle"] = s
g = folder(s, "api", "")
IDS["life.info"] = leaf(g, "GET /agent/info — human-shaped status",
  "Phase, facets, board sizes, verdicts, strikes — what an operator curls.",
  "GET http://&lt;agent&gt;:7102/agent/info → JSON", [])
IDS["life.ready"] = leaf(g, "GET /q/health/ready — machine-shaped readiness",
  "Ready = fabric answers AND lifecycle is in a working phase. For compose / k8s probes.",
  "FabricReadyCheck: fabric ping + phase ∈ {ALIVE, ARMING_INVESTIGATORS, DETECTING_FACETS} · liveness = /q/health/live",
  ["added in P3"])
g = folder(s, "publishes", "")
IDS["life.heartbeat"] = leaf(g, "heartbeat + facets → fabric",
  "Lease-bound presence the directory and everyone's caches rely on.",
  "/agents/&lt;node&gt;/heartbeat (lease) · /agents/&lt;node&gt;/facets · /clusters/&lt;c&gt;/agents/&lt;node&gt;",
  ["agent dies → lease expires → keys vanish → watchers see DELETE"])
note(s, "drift report", "<p>✅ Lifecycle machine on the StateMap DSL. "
  "⚠ liveness-writer consolidation open (V4 item — heartbeat writing is split across lifecycle + directory).</p>")

# ---------------------------------------------------------------- nw-mysql
s = svc("plugin: nw-mysql (MySQL master-slave)",
  "<p>The first real plugin. Watches a MySQL master-slave pair, votes on its health, and can "
  "fence the old master and promote the best slave with GTID-checked SQL.</p>"
  "<p>Role 1 (client canary + local confession) is live. 🚧 Roles 2 and 3 "
  "(master-side watcher · slave's master-canary + lag policy) are designed, not built.</p>")
IDS["svc.mysql"] = s
g = folder(s, "probes (observe side)", "HealthCheck + FacetDetector implementations.")
IDS["mysql.remote"] = leaf(g, "mysql.remote.client — the CLIENT canary",
  "Asks MySQL the way an application would, from every agent that can reach it.",
  "MySqlRemoteClient · vantage CLIENT · canary SQL default SHOW DATABASES · event MySqlRemoteHealth",
  ["grades FAST / DEGRADED / DEAD by latency thresholds + connection failure",
   "credentials only via NW_MYSQL_PASSWORD"])
IDS["mysql.local"] = leaf(g, "mysql.local — the LOCAL_SELF confession",
  "On the host that runs MySQL: replication state, read-only flag, GTID position.",
  "MySqlLocalProbe · vantage LOCAL_SELF · event MySqlRemoteHealth (replication fields)",
  ["the promotion decision reads this evidence through viewOf()"])
IDS["mysql.facets"] = leaf(g, "MysqlFacetDetector",
  "Detects mysql-master-here / mysql-slave-here so probes and actions arm only where they apply.",
  "declaredFacets() = {mysql-master-here, mysql-slave-here}", [])
g = folder(s, "orchestrator (act side)", "Vote + selection + plan + the five SQL actions.")
IDS["mysql.agg"] = leaf(g, "MysqlHealthAggregator — MySQL joins the vote",
  "Freshest observation per publisher; FAST=1.0 · DEGRADED=0.5 · DEAD=0.0 · UNKNOWN=silence; averaged.",
  "scoreVantage(bucket) → double (NaN = silence)",
  ["added in P3 — before it, require-odown could never fire for mysql targets",
   "deliberately re-judges nothing: the probes already encoded the mysql-specific judgment"])
IDS["mysql.selector"] = leaf(g, "MysqlCandidateSelector",
  "Picks the slave with the most advanced GTID position among healthy candidates.",
  "select(candidates, ctx) → Optional<NodeId>", [])
IDS["mysql.plangen"] = leaf(g, "MysqlFailoverPlanGenerator",
  "Verdict + cluster view → ordered plan: fence old master, promote chosen slave.",
  "generate(verdict, view) → Plan", [])
IDS["mysql.actions"] = leaf(g, "the five SQL actions",
  "Fence master · promote slave · set read-only · start replica · stop replica — each one FailoverAction, each idempotency-checked.",
  "MysqlFenceMasterAction · MysqlPromoteSlaveAction · MysqlSetReadOnlyAction · MysqlStartReplicaAction · MysqlStopReplicaAction (shared SQL in MysqlReplicaSql)",
  ["each declares allowedRoles() so the door refuses a promote aimed at the wrong node"])
note(s, "drift report", "<p>✅ plugin.yml synced to code truth (P3): 2 probes, 5 actions, no phantom ids. "
  "✅ MysqlHealthAggregatorTest (5) + MysqlCandidateSelectorTest (6). "
  "🚧 Roles 2+3 unbuilt. ⚠ no integration pass against a real master-replica pair yet (the M4 rehearsal).</p>")

# ---------------------------------------------------------------- nw-mock
s = svc("plugin: nw-mock (full-pipeline harness)",
  "<p>A file-driven fake service that exercises the WHOLE loop — probe → board → vote → "
  "coordinator → door → action — with two real agents on one box. It found two real core bugs.</p>")
IDS["svc.mock"] = s
g = folder(s, "probes (observe side)", "")
IDS["mock.protocol"] = leaf(g, "the file protocol (MockStateStore)",
  "The 'service' is a pair of files per node; per-seat override files simulate partitions.",
  "/tmp/nw-mock/&lt;node&gt;.state (fast|degraded|dead) · &lt;node&gt;.role (master|slave) · &lt;node&gt;.state@client / @peer = that seat's lie",
  ["node name injected via nw.agent.node-name (P2 seam)"])
IDS["mock.probes"] = leaf(g, "three probes — local · client · peer",
  "One probe per vantage, each reading the file (or its seat override).",
  "MockLocalProbe (LOCAL_SELF) · MockClientProbe (CLIENT) · MockPeerProbe (REMOTE_PEER) · event MockHealth",
  ["a seat override lets one seat lie while the others tell the truth — the minority-lie rehearsal"])
g = folder(s, "orchestrator (act side)", "")
IDS["mock.orch"] = leaf(g, "aggregator · selector · plan generator · fence + promote",
  "Same contract as a real plugin; the actions rewrite the files, so a promotion is observable.",
  "MockHealthAggregator · MockCandidateSelector · MockFailoverPlanGenerator · MockFenceAction · MockPromoteAction",
  ["rehearsal verified 2026-06-07: minority lie held at SDOWN (5/3 strikes, no failover); true death → ODOWN → fence + promote across two agents in 13 s"])
note(s, "drift report", "<p>✅ MockHealthAggregatorTest (5). Recipe: tenant mock, profile dev "
  "(nw-core config/tenants/mock/dev). NW_CLUSTER_NAME bumps to a pristine roles board for reruns.</p>")

json.dump(IDS, open("/tmp/nw-v5-ids.json", "w"), indent=1)
print(f"\nDONE — {len(IDS)} ids saved to /tmp/nw-v5-ids.json")
