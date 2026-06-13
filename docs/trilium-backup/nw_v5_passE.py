#!/usr/bin/env python3
"""Pass E — group/node failover: groupFailover concern + failover-domains catalog
+ orchestration service stubs. Bakes in the ratified design decisions:
  VIP = BGP /32 via FRR · fence = self-fence on quorum loss · VIP-first ordering
  · trigger = per-domain ClusterCondition (BTCL SMS: mysql ODOWN -> group failover).
Everything not yet coded carries a 🚧 banner. Additive to Doc-V5.0."""
import json, urllib.request

TOKEN = open("/tmp/trilium-etapi-token").read().strip().splitlines()[0]
B = "http://10.9.9.6:7081/etapi"
IDS = json.load(open("/tmp/nw-v5-ids.json"))

def req(method, path, body=None, ctype="application/json"):
    data = body.encode() if isinstance(body, str) else (json.dumps(body).encode() if body is not None else None)
    r = urllib.request.Request(B + path, data=data, method=method,
        headers={"Authorization": TOKEN, "Content-Type": ctype})
    with urllib.request.urlopen(r) as resp:
        raw = resp.read()
        if not raw: return None
        if method == "GET" or ctype == "application/json":
            try: return json.loads(raw)
            except ValueError: return raw.decode()
        return None

def note(parent, title, content, ntype="text"):
    body = {"parentNoteId": parent, "title": title, "type": ntype, "content": content}
    if ntype == "mermaid": body["mime"] = "text/mermaid"
    nid = req("POST", "/create-note", body)["note"]["noteId"]
    print(f"  + {title} [{nid}]")
    return nid

def L(key, label=None):
    return f'<a class="reference-link" href="#root/{IDS[key]}">{label or key}</a>'

def Lid(nid, label):
    return f'<a class="reference-link" href="#root/{nid}">{label}</a>'

def leaf(parent, title, desc, sig, bullets, planned=True):
    b = "".join(f"<li>{x}</li>" for x in bullets)
    banner = "<p>🚧 <strong>Planned — not built.</strong></p>" if planned else ""
    html = f"{banner}<p>{desc}</p>"
    if sig: html += f"<p><code>{sig}</code></p>"
    if b: html += f"<ul>{b}</ul>"
    return note(parent, title, html)

USED_BY = {}
def used(nid, flow_id, flow_title):
    USED_BY.setdefault(nid, []).append((flow_id, flow_title))

def flow(feature_id, title, runs_when, mermaid_src, steps_html, touched_ids):
    fid = note(feature_id, title, "<p>placeholder</p>")
    mid = note(fid, f"diagram — {title}", mermaid_src, ntype="mermaid")
    html = (f"<p><strong>Runs when:</strong> {runs_when}</p>"
            f'<section class="include-note" data-note-id="{mid}" data-box-size="full"></section>'
            f"<h4>Steps</h4><ol>{steps_html}</ol>"
            "<p><em>No-orphan check: every hop above links a real leaf.</em></p>")
    req("PUT", f"/notes/{fid}/content", html, ctype="text/plain")
    for nid in touched_ids: used(nid, fid, title)
    return fid

def feature(concern_id, title, story_html, touchpoints):
    rows = "".join(f"<tr><td>{l}</td><td>{s}</td></tr>" for l, s in touchpoints)
    html = (f"<h4>Story</h4>{story_html}<h4>Flows</h4><ul>FLOWS</ul>"
            f"<h4>Touchpoints</h4><table><thead><tr><th>leaf</th><th>service</th></tr></thead>"
            f"<tbody>{rows}</tbody></table>")
    return note(concern_id, title, html)

def set_flows(fid, flows):
    cur = req("GET", f"/notes/{fid}/content", ctype="text/plain")
    items = "".join(f'<li><a class="reference-link" href="#root/{x}">{n}</a> — {h}</li>' for x,n,h in flows)
    req("PUT", f"/notes/{fid}/content", cur.replace("FLOWS", items), ctype="text/plain")

# nw-agent services parent (parent of svc.mysql)
NWAGENT = req("GET", f"/notes/{IDS['svc.mysql']}")["parentNoteIds"][0]
SERVICES = IDS["services"]; CONCERNS = IDS["concerns"]; ROOT = IDS["root"]

# ======================================================= NEW SERVICE STUBS
# A new grouping for orchestration services
orch = note(SERVICES, "orchestration (group failover — runtime unit, 🚧 design)",
  "<p>🚧 The services that turn a single-target failover into a whole-node, "
  "service-group failover. All designed, none built yet — the bones (ordered Plan, "
  "ClusterCondition, StatefulAction, per-host agents) already exist in the core/SPI.</p>")

# --- failover-domain orchestrator
s = note(orch, "failover-domain (the group orchestrator)",
  "<p>🚧 Planned. Holds the failover <strong>domain</strong> model — a node pair plus an "
  "ordered list of member services, a VIP, a fence policy, and a per-domain trigger. "
  "Turns one domain-down verdict into an ordered Plan that fences, moves the VIP, and "
  "promotes every member in order. Built on the existing "
  + L("spi.FailoverPlanGenerator","FailoverPlanGenerator") + " (the Plan is already an "
  "ordered command list), " + L("spi.ClusterCondition","ClusterCondition") + " (the "
  "trigger), and " + L("spi.StatefulAction","StatefulAction") + " (resume a half-done promote).</p>")
g = note(s, "api", "")
dom_domainOf = leaf(g, "domainOf(node) — which domain a node belongs to",
  "Resolve the failover domain (member services, order, VIP, partner node) for a host.",
  "domainOf(node) → Domain{members[ordered], vip, activeNode, standbyNode, fencePolicy, trigger}",
  ["a domain is declared in tenant config, not code — see the failover-domains catalog",
   "members carry promote-order + depends-on so the Plan can sequence them"])
dom_plan = leaf(g, "ordered group Plan — fence → VIP → members",
  "Generate the whole-node failover Plan for a domain, in dependency order.",
  "generate(domainVerdict, view) → Plan[ fence.self, vip.announce, <members in order> ]",
  ["VIP-first ordering (ratified): clients cut over before the app layer is fully ready",
   "resumable via " + L("spi.StatefulAction","StatefulAction") + " if an agent restarts mid-promote"])
g = note(s, "spi", "What the orchestrator consumes (host/plugins implement).")
dom_cond = leaf(g, "the domain's ClusterCondition (the custom trigger)",
  "Each domain ships its own condition that decides when the whole group fails over. "
  "This is per-domain custom logic — not one global rule.",
  L("spi.ClusterCondition","ClusterCondition") + ".evaluate(ClusterTopology) → boolean",
  ["BTCL SMS domain: <em>mysql ODOWN → fail the whole group over</em>",
   "another domain might use node-heartbeat-lost, or a quorum of members"])
dom_members = leaf(g, "member activate / standby actions",
  "Every member service provides its own activate (become primary) and standby actions; "
  "the orchestrator calls them in order.",
  "FailoverAction per member — see nw-vip · nw-fence · nw-mysql · nw-sigtran · nw-redis · nw-freeswitch", [])
note(s, "drift report", "<p>🚧 Designed, not built. The enabling hooks already exist and are "
  "tested in the single-service path (mysql): ordered Plan, ClusterCondition, StatefulAction, "
  "per-host command door. What is new: the Domain model + config loader + ordered plan generator.</p>")
IDS["svc.domain"] = s

def stub_service(parent, title, desc, leaves):
    sid = note(parent, title, f"<p>🚧 Planned. {desc}</p>")
    g = note(sid, "api", "")
    out = {}
    for key, t, d, sig, bl in leaves:
        out[key] = leaf(g, t, d, sig, bl)
    note(sid, "drift report", "<p>🚧 Designed, not built. Member of a failover domain.</p>")
    return sid, out

# --- nw-vip
vip_s, vipL = stub_service(orch, "nw-vip (service VIP via BGP/FRR)",
  "Moves a service VIP between nodes by BGP. The active node's FRR announces the service "
  "/32; failover withdraws it there and announces it on the new node.",
  [("announce","vip.announce — start announcing the /32",
    "The new active node's FRR begins announcing the service /32 over BGP.",
    "vip.announce(vip/32) — FRR network statement / route-map, higher local-pref",
    ["a revived old node cannot win the route — survivor keeps a higher local-pref"]),
   ("withdraw","vip.withdraw — stop announcing the /32",
    "The old/standby node withdraws its /32 announcement so traffic leaves it.",
    "vip.withdraw(vip/32)", ["paired with self-fence so a dying node drops its VIP itself"])])
IDS["vip.announce"]=vipL["announce"]; IDS["vip.withdraw"]=vipL["withdraw"]

# --- nw-fence
fence_s, fenceL = stub_service(orch, "nw-fence (self-fence on quorum loss)",
  "Prevents split-brain. On losing quorum the dying/isolated node stops its own services "
  "and withdraws its VIP; the survivor takes over regardless. (A hard power fence can be "
  "added later for the hung-but-alive case.)",
  [("self","fence.self — stand down on quorum loss",
    "The node's own agent stops member services and withdraws the VIP when it can no longer "
    "see a fabric quorum.",
    "fence.self() — stop members + " + L("vip.withdraw","vip.withdraw"),
    ["self-fence only — weak against a hung-but-alive node; pair with power fence later"])])
IDS["fence.self"]=fenceL["self"]

# --- member plugins (sigtran / redis / freeswitch)
sig_s, sigL = stub_service(orch, "plugin: nw-sigtran (SS7 MAP) 🚧",
  "Watches sigtran (SS7 MAP, 8282 UDP) and activates/stands-by the SIGTRAN stack. Its health "
  "depends on the DB — when mysql is down the sigtran domain fails over.",
  [("activate","sigtran.activate — become the active SS7 point",
    "Bring the sigtran stack up as the active node (bind point codes, open associations).",
    "sigtran.activate()", ["depends-on: mysql primary + redis"]),
   ("standby","sigtran.standby — stand down",
    "Quiesce the sigtran stack on the old active node.", "sigtran.standby()", []),
   ("health","sigtran.health — probe associations + DB reachability",
    "Report sigtran health, including its dependency on the database.",
    "HealthCheck → MySqlRemoteHealth-style grade", ["DB unreachable contributes to the group trigger"])])
IDS["sig.activate"]=sigL["activate"]

redis_s, redisL = stub_service(orch, "plugin: nw-redis 🚧",
  "Promotes/demotes redis (REPLICAOF) on failover.",
  [("promote","redis.promote — REPLICAOF NO ONE",
    "Make the standby redis the primary.", "redis.promote()", ["depends-on: none (early in order)"]),
   ("standby","redis.standby — REPLICAOF new-primary", "Re-point the old primary as replica.",
    "redis.standby(newPrimary)", [])])
IDS["redis.promote"]=redisL["promote"]

fs_s, fsL = stub_service(orch, "plugin: nw-freeswitch (SIP) 🚧",
  "Activates FreeSWITCH (5060 SIP) on the new active node.",
  [("activate","fs.activate — bind SIP + register gateways",
    "Bring FreeSWITCH up as active (bind 5060, register gateways).", "fs.activate()",
    ["depends-on: mysql + sigtran"])])
IDS["fs.activate"]=fsL["activate"]

# ======================================================= CONCERN: groupFailover
c = note(CONCERNS, "groupFailover (promote a whole standby node)",
  "<p>🚧 Design. The unit of failover is a <strong>node / service-group</strong>, not a single "
  "service. A failover <em>domain</em> is a bundle of co-located services — mysql, redis, "
  "sigtran, freeswitch, a VIP — that live on the active node and move together, in order, "
  "when that node's trigger fires. This is the Pacemaker resource-group model built on our "
  "etcd + agent plugins. The single-service mysql failover (concern "
  + L("svc.mysql","pluginExtension/nw-mysql") + ") becomes one member of a domain.</p>")
IDS["c.group"] = c

# F1 — domain model
f1 = feature(c, "failoverDomainModel (a node pair as one unit)",
  "<p>An operator declares a domain in tenant config: an active node, a standby node, an "
  "ordered list of member services, a VIP, a fence policy, and a trigger. The "
  + Lid(IDS['svc.domain'],"failover-domain orchestrator") + " loads it; each agent learns "
  "which members it owns. Nothing about a domain lives in code — it is data, so a new "
  "service stack is a config change, not a release.</p>"
  "<p><strong>Roles:</strong> config declares · the orchestrator models · agents own their members.</p>",
  [(Lid(dom_domainOf,"domainOf()"),"failover-domain"), (Lid(dom_members,"member actions"),"failover-domain"),
   (L("boards.roles","RoleStore"),"boards")])
fl1 = flow(f1, "domainDeclaredAndWatched",
  "an operator adds or edits a failover domain in tenant config (⏰ load).",
  "flowchart LR\n  A[tenant config:\\ndomain = nodes+members+vip+trigger] --> B[failover-domain\\nloads the Domain]\n  B --> C[each agent learns\\nits member services]\n  C --> D[roles board: active/standby]",
  f"<li>⏰ Config declares the domain; ▶ {Lid(dom_domainOf,'domainOf()')} resolves the member list, order, VIP and partner node.</li>"
  f"<li>Each agent learns the members it owns (facets) and writes active/standby to ▶ {L('boards.roles','the roles board')}.</li>"
  f"<li>The domain is now watched — no code change was needed to add it.</li>",
  [dom_domainOf, dom_members, IDS["boards.roles"]])
set_flows(f1, [(fl1,"domainDeclaredAndWatched","a domain is declared in config and loaded")])

# F2 — custom trigger
f2 = feature(c, "groupTriggerCustom (each domain decides when to fail over)",
  "<p>Each domain ships its own trigger — custom logic, not one global rule. The BTCL SMS "
  "domain's rule is simple: <em>if the database goes down, switch the whole group over.</em> "
  "So when the mysql seat vote reaches ODOWN, the domain's "
  + L("spi.ClusterCondition","ClusterCondition") + " evaluates true and the orchestrator is "
  "told the whole domain is down — even though sigtran and redis on that node may still look "
  "alive. Another domain could trigger on node-heartbeat-loss instead.</p>"
  "<p><strong>Roles:</strong> the vote scores each service · the domain condition decides for the group · the coordinator hears one domain-down.</p>",
  [(L("vote.verdictFor","verdictFor()"),"vote"), (Lid(dom_cond,"domain ClusterCondition"),"failover-domain"),
   (L("coord.declareTargetDead","declareTargetDead()"),"coordinator")])
fl2 = flow(f2, "mysqlDownTriggersSigtranGroup",
  "the mysql seat vote in the BTCL SMS domain reaches ODOWN.",
  "sequenceDiagram\n  participant V as vote (Resolver)\n  participant D as domain condition\n  participant K as coordinator\n  V->>D: mysql verdict = ODOWN\n  D->>D: BTCL SMS rule: db down => group down\n  D->>K: declareTargetDead(domain = btcl-sms)\n  Note over K: whole group fails over, not just mysql",
  f"<li>▶ {L('vote.verdictFor','The Resolver')} marks the mysql target ODOWN (the existing single-service vote).</li>"
  f"<li>The domain's ▶ {Lid(dom_cond,'ClusterCondition')} reads that and applies the BTCL SMS rule — <em>db down → group down</em>.</li>"
  f"<li>It calls ↩ {L('coord.declareTargetDead','declareTargetDead')} for the whole domain, not just mysql.</li>",
  [IDS["vote.verdictFor"], dom_cond, IDS["coord.declareTargetDead"]])
set_flows(f2, [(fl2,"mysqlDownTriggersSigtranGroup","the SMS domain's db-down rule fires")])

# F3 — ordered promotion (headline)
f3 = feature(c, "orderedGroupPromotion (fence, VIP, then services in order)",
  "<p>The coordinator takes the failover gate and runs the domain's ordered Plan. First the "
  "old node self-fences. Then — VIP first, so the blackhole ends fast — the new node "
  "announces the VIP over BGP. Then the members promote in dependency order: mysql becomes "
  "master, redis promotes, sigtran activates, freeswitch activates. Each step is a local "
  "action on the new active node's agent. If an agent restarts mid-promote, the "
  + L("spi.StatefulAction","StatefulAction") + " resumes where it left off.</p>"
  "<p><strong>Roles:</strong> the coordinator sequences · the orchestrator orders · each member promotes itself.</p>",
  [(L("coord.declareTargetDead","declareTargetDead()"),"coordinator"), (L("boards.gate","FailoverGate"),"boards"),
   (Lid(dom_plan,"ordered Plan"),"failover-domain"), (Lid(fenceL["self"],"fence.self"),"nw-fence"),
   (Lid(vipL["announce"],"vip.announce"),"nw-vip"), (L("mysql.actions","mysql actions"),"nw-mysql"),
   (Lid(redisL["promote"],"redis.promote"),"nw-redis"), (Lid(sigL["activate"],"sigtran.activate"),"nw-sigtran"),
   (Lid(fsL["activate"],"fs.activate"),"nw-freeswitch")])
fl3 = flow(f3, "nodeDiesGroupPromotes",
  "the domain trigger has fired (⤳ declareTargetDead for the whole group).",
  "sequenceDiagram\n  participant K as coordinator\n  participant G as gate\n  participant O as failover-domain\n  participant N as new active agent\n  K->>G: tryAcquire + bumpEpoch\n  K->>O: ordered Plan\n  Note over N: old node self-fences\n  K->>N: 1 vip.announce (BGP) — fail fast\n  K->>N: 2 mysql promote (GTID)\n  K->>N: 3 redis.promote\n  K->>N: 4 sigtran.activate\n  K->>N: 5 fs.activate\n  K->>K: roles board: new=active, old=quarantined",
  f"<li>▶ {L('coord.declareTargetDead','declareTargetDead')} takes {L('boards.gate','the failover gate')} (one coordinator per outage).</li>"
  f"<li>The old node ▶ {Lid(fenceL['self'],'self-fences')} — stops members + drops its VIP.</li>"
  f"<li>VIP first: ▶ {Lid(vipL['announce'],'vip.announce')} on the new node ends the blackhole immediately.</li>"
  f"<li>Members promote in order via the ▶ {Lid(dom_plan,'ordered Plan')}: {L('mysql.actions','mysql')} → {Lid(redisL['promote'],'redis')} → {Lid(sigL['activate'],'sigtran')} → {Lid(fsL['activate'],'freeswitch')}.</li>"
  f"<li>On success the coordinator flips ▶ {L('boards.roles','the roles board')} (new=active, old=quarantined). Resumable via {L('spi.StatefulAction','StatefulAction')}.</li>",
  [IDS["coord.declareTargetDead"], IDS["boards.gate"], dom_plan, fenceL["self"], vipL["announce"],
   IDS["mysql.actions"], redisL["promote"], sigL["activate"], fsL["activate"], IDS["boards.roles"]])
set_flows(f3, [(fl3,"nodeDiesGroupPromotes","fence → VIP → mysql → redis → sigtran → freeswitch")])

# F4 — VIP via BGP
f4 = feature(c, "vipCutoverByBgp (the VIP follows the active node)",
  "<p>The service VIP is a /32 announced over BGP by whichever node is active — reusing the "
  "per-host FRR routers already in the network design. Failover withdraws the /32 on the old "
  "node and announces it on the new one. A higher local-pref on the survivor means a revived "
  "old node cannot steal the route back. No L2 adjacency assumptions, no gratuitous-ARP races.</p>"
  "<p><strong>Roles:</strong> FRR carries the route · the agent flips the announcement · BGP best-path does the rest.</p>",
  [(Lid(vipL["announce"],"vip.announce"),"nw-vip"), (Lid(vipL["withdraw"],"vip.withdraw"),"nw-vip")])
fl4 = flow(f4, "vipMovesWithBgp",
  "the promotion sequence reaches the VIP step (▶ vip.announce).",
  "flowchart LR\n  A[old node FRR\\nwithdraw /32] --> C[BGP reconverges]\n  B[new node FRR\\nannounce /32\\nhigher local-pref] --> C\n  C --> D[clients reach new active]",
  f"<li>The old node ▶ {Lid(vipL['withdraw'],'withdraws')} the service /32 (or self-fence does it).</li>"
  f"<li>The new node ▶ {Lid(vipL['announce'],'announces')} the /32 with a higher local-pref.</li>"
  f"<li>BGP reconverges; clients reach the new active. A revived old node cannot win the route back.</li>",
  [vipL["withdraw"], vipL["announce"]])
set_flows(f4, [(fl4,"vipMovesWithBgp","FRR moves the /32 to the new active node")])

# F5 — self-fence
f5 = feature(c, "selfFenceOnQuorumLoss (the dying node stands down)",
  "<p>Split-brain guard, first cut. When a node can no longer see a fabric quorum — it is "
  "isolated or dying — its own agent stops the member services and withdraws the VIP. The "
  "survivor takes over regardless. This is weak only against a node that is hung but still "
  "serving; a hard power fence can be added later for that case.</p>"
  "<p><strong>Roles:</strong> the dying node fences itself · the fabric lease is the quorum signal · the survivor never waits on the corpse.</p>",
  [(L("life.heartbeat","heartbeat / lease"),"lifecycle"), (Lid(fenceL["self"],"fence.self"),"nw-fence"),
   (Lid(vipL["withdraw"],"vip.withdraw"),"nw-vip")])
fl5 = flow(f5, "isolatedNodeSelfFences",
  "a node loses its fabric quorum (lease cannot be renewed).",
  "sequenceDiagram\n  participant A as dying node agent\n  participant F as fabric\n  A->>F: keepAlive fails (no quorum)\n  A->>A: fence.self — stop members\n  A->>A: vip.withdraw\n  Note over A: survivor takes over regardless",
  f"<li>The node's {L('life.heartbeat','lease')} can no longer be renewed — quorum lost.</li>"
  f"<li>Its agent runs ▶ {Lid(fenceL['self'],'fence.self')}: stop member services, then {Lid(vipL['withdraw'],'vip.withdraw')}.</li>"
  f"<li>The survivor promotes regardless — it never blocks on the corpse.</li>",
  [IDS["life.heartbeat"], fenceL["self"], vipL["withdraw"]])
set_flows(f5, [(fl5,"isolatedNodeSelfFences","a node that loses quorum stops itself")])

# ======================================================= FAILOVER-DOMAINS CATALOG
fd = note(ROOT, "failover-domains (compose-like: services grouped by node)",
  "<p>🚧 Design. Each <strong>domain</strong> is a group of services that live on one active "
  "node and fail over together — read it like a docker-compose for a node pair. A domain is "
  "declared in tenant config, not code.</p>")
IDS["failover-domains"] = fd

btcl = note(fd, "btcl-sms (dell-sms-master ⇄ dell-sms-slave)",
  "<p>🚧 The BTCL SMS / sigtran stack as one failover domain. Active on dell-sms-master "
  "(10.246.7.102), standby on dell-sms-slave (10.246.7.103).</p>"
  "<p><strong>Trigger:</strong> mysql ODOWN → the whole group fails over (db-down rule). "
  "<strong>VIP:</strong> service /32 announced by the active node's FRR over BGP. "
  "<strong>Fence:</strong> self-fence on quorum loss. <strong>VIP timing:</strong> first (fail fast).</p>"
  "<h4>Members (promote order)</h4>"
  "<table><thead><tr><th>#</th><th>service</th><th>action plugin</th><th>depends-on</th><th>status</th></tr></thead>"
  "<tbody>"
  f"<tr><td>0</td><td>fence old node</td><td>{Lid(fenceL['self'],'nw-fence / fence.self')}</td><td>—</td><td>🚧</td></tr>"
  f"<tr><td>1</td><td>VIP (/32 BGP)</td><td>{Lid(vipL['announce'],'nw-vip / vip.announce')}</td><td>—</td><td>🚧</td></tr>"
  f"<tr><td>2</td><td>mysql (promote)</td><td>{L('mysql.actions','nw-mysql / 5 SQL actions')}</td><td>—</td><td>✅ built</td></tr>"
  f"<tr><td>3</td><td>redis (promote)</td><td>{Lid(redisL['promote'],'nw-redis / redis.promote')}</td><td>—</td><td>🚧</td></tr>"
  f"<tr><td>4</td><td>sigtran (activate)</td><td>{Lid(sigL['activate'],'nw-sigtran / sigtran.activate')}</td><td>mysql · redis</td><td>🚧</td></tr>"
  f"<tr><td>5</td><td>freeswitch (activate)</td><td>{Lid(fsL['activate'],'nw-freeswitch / fs.activate')}</td><td>mysql · sigtran</td><td>🚧</td></tr>"
  "</tbody></table>"
  f"<p>Full sequence: see flow {Lid(fl3,'nodeDiesGroupPromotes')}. Trigger: {Lid(fl2,'mysqlDownTriggersSigtranGroup')}.</p>")

# ======================================================= Used-by backlinks
for nid, flows in USED_BY.items():
    cur = req("GET", f"/notes/{nid}/content", ctype="text/plain")
    seen, links = set(), []
    for fid, title in flows:
        if fid not in seen:
            seen.add(fid); links.append(f'<a class="reference-link" href="#root/{fid}">{title}</a>')
    req("PUT", f"/notes/{nid}/content", cur + f"<p><em>Used by: {' · '.join(links)}</em></p>", ctype="text/plain")
print(f"backlinks on {len(USED_BY)} leaves")

json.dump(IDS, open("/tmp/nw-v5-ids.json","w"), indent=1)
print("Pass E done")
