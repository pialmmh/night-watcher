#!/usr/bin/env python3
"""Pass F — design revision: failover is UNIFORMLY group-wise (List<Member>).
Even one service is a one-member group. No single-service path.
Updates Doc-V5.0 in place (code-master shapes). Additive + reframing banners."""
import json, urllib.request

TOKEN = open("/tmp/trilium-etapi-token").read().strip().splitlines()[0]
B = "http://10.9.9.6:7081/etapi"
IDS = json.load(open("/tmp/nw-v5-ids.json"))

def req(method, path, body=None, ctype="application/json"):
    data = body.encode() if isinstance(body, str) else (json.dumps(body).encode() if body is not None else None)
    r = urllib.request.Request(B+path, data=data, method=method,
        headers={"Authorization": TOKEN, "Content-Type": ctype})
    with urllib.request.urlopen(r) as resp:
        raw = resp.read()
        if not raw: return None
        if method in ("GET",) or ctype == "application/json":
            try: return json.loads(raw)
            except ValueError: return raw.decode()
        return None

def get_content(nid): return req("GET", f"/notes/{nid}/content", ctype="text/plain")
def put_content(nid, html): req("PUT", f"/notes/{nid}/content", html, ctype="text/plain")
def retitle(nid, title): req("PATCH", f"/notes/{nid}", {"title": title})
def note(parent, title, content, ntype="text"):
    body={"parentNoteId":parent,"title":title,"type":ntype,"content":content}
    if ntype=="mermaid": body["mime"]="text/mermaid"
    nid=req("POST","/create-note",body)["note"]["noteId"]; print(f"  + {title} [{nid}]"); return nid
def L(key,label=None): return f'<a class="reference-link" href="#root/{IDS[key]}">{label or key}</a>'
def Lid(nid,label): return f'<a class="reference-link" href="#root/{nid}">{label}</a>'

CGROUP = IDS["c.group"]; COLD = IDS["c3"]; SVCDOM = IDS["svc.domain"]; FDOMS = IDS["failover-domains"]
OLDFEAT = "OAAZXqZ5Qyru"  # masterFailoverEndToEnd

# 1) Make the group concern canonical + uniform framing -------------------
retitle(CGROUP, "failoverOrchestration (uniform — a group is List<Member>)")
put_content(CGROUP,
  "<p><strong>The unit of failover is a group — always.</strong> A failover group is a "
  "<code>List&lt;Member&gt;</code> of co-located services that share one active node, a VIP, a "
  "fence policy, and a trigger. Config and capability are <em>group-wise and uniform</em>: there "
  "is no special single-service path. <strong>Even one service is a one-member group</strong> — "
  "mysql alone is a group whose member list has length 1, declared with the very same schema as "
  "the five-member SMS stack.</p>"
  "<p>The pipeline below stays per-member where it should and becomes group-wise where it must:</p>"
  "<ul><li><strong>observe + vote</strong> — per member. Each member's plugin probes its service; "
  "the " + L("vote.verdictFor","Resolver") + " scores each member target. Uniform.</li>"
  "<li><strong>trigger + act</strong> — per group. The group's "
  + L("spi.ClusterCondition","ClusterCondition") + " decides when the whole group fails over; the "
  "coordinator then walks the member <code>List</code> in order: fence → VIP → each member.</li></ul>"
  "<p>mysql (concern " + L("svc.mysql","pluginExtension/nw-mysql") + ") is no longer an owner of "
  "failover — it becomes a <strong>member plugin</strong> (probes + activate/standby/fence/select for "
  "one mysql). The " + Lid(SVCDOM,"failover-domain orchestrator") + " composes the ordered plan "
  "across the list.</p>")

# 2) The uniform model entity ---------------------------------------------
model = note(CGROUP, "FailoverGroup & Member (the uniform model + config)",
  "<p>The one data model behind every failover, single or multi. Read it as the schema every "
  "domain config is validated against.</p>"
  "<h4>The types</h4>"
  "<pre>FailoverGroup {\n"
  "  id\n"
  "  members: List&lt;Member&gt;        // ALWAYS a list; one service = length 1\n"
  "  vip: Optional&lt;Vip&gt;            // bgp /32 (default), or none\n"
  "  fencePolicy                    // self (default) | power | network\n"
  "  trigger: ClusterCondition      // per-group custom (e.g. member-odown:mysql)\n"
  "}\n"
  "Member {\n"
  "  type        // mysql | sigtran | redis | freeswitch | ...\n"
  "  pluginId    // supplies probes + activate/standby/fence/select\n"
  "  role        // master-slave | active-standby\n"
  "  order       // promote order within the group\n"
  "  dependsOn: List&lt;type&gt;   // ordering constraints\n"
  "}</pre>"
  "<h4>Uniform config — multi-member AND single-member look identical</h4>"
  "<pre>domains:\n"
  "  - id: btcl-sms                       # five-member SMS/sigtran stack\n"
  "    members:\n"
  "      - { type: mysql,      order: 2, role: master-slave }\n"
  "      - { type: redis,      order: 3 }\n"
  "      - { type: sigtran,    order: 4, dependsOn: [mysql, redis] }\n"
  "      - { type: freeswitch, order: 5, dependsOn: [mysql, sigtran] }\n"
  "    vip:     { mode: bgp, prefix: 10.x.y.Z/32 }\n"
  "    fence:   self\n"
  "    trigger: { condition: member-odown, member: mysql }\n"
  "\n"
  "  - id: mysql-only                     # SINGLE service = one-member group\n"
  "    members:\n"
  "      - { type: mysql, order: 1, role: master-slave }\n"
  "    trigger: { condition: member-odown, member: mysql }</pre>"
  "<p>Both entries are the same shape. The orchestrator never branches on \"single vs group\" — it "
  "always iterates <code>members</code>. A length-1 list is not a special case; it is the base case.</p>")
IDS["model.group"] = model

# 3) Feature: single service is a one-member group ------------------------
f = note(CGROUP, "singleServiceIsOneMemberGroup (mysql alone, same machinery)",
  "<h4>Story</h4><p>An operator wants to orchestrate just mysql — no sigtran, no VIP. They declare "
  "a domain <code>mysql-only</code> with a member list of length 1, using the identical schema. When "
  "the trigger fires, the same coordinator runs the same ordered walk over a list that happens to "
  "hold one member: (optional fence) → promote the mysql member. No single-service code path is "
  "taken because none exists. The one-member group is the base case of the uniform model.</p>"
  "<p><strong>Roles:</strong> config declares a length-1 list · the orchestrator iterates it like any "
  "other · mysql contributes its member actions.</p>"
  "<h4>Flows</h4><ul>"
  f"<li>{Lid('g3vx2puMcuF0','nodeDiesGroupPromotes')} — borrowed (home: orderedGroupPromotion). "
  "Runs identically with <code>members.size() == 1</code>; the fence/VIP steps are skipped when the "
  "group declares none.</li></ul>"
  "<h4>Touchpoints</h4><table><thead><tr><th>leaf</th><th>service</th></tr></thead><tbody>"
  f"<tr><td>{Lid(IDS['model.group'],'FailoverGroup (length 1)')}</td><td>failover-domain</td></tr>"
  f"<tr><td>{L('mysql.actions','mysql member actions')}</td><td>nw-mysql</td></tr></tbody></table>")
IDS["feat.oneMember"] = f

# 4) Reframe the OLD single-service concern + feature ---------------------
banner = ("<p>🔁 <strong>Reframed (2026-06-14).</strong> Failover is now <em>uniformly group-wise</em> "
  "— see " + Lid(CGROUP,"failoverOrchestration (uniform — List&lt;Member&gt;)") + ". This page is kept as "
  "the worked <strong>one-member</strong> case (mysql alone): the same sequence with a member list of "
  "length 1. The door transport here (commandCrossesTheDoor) stays canonical for every member command.</p>")
for nid in (COLD, OLDFEAT):
    cur = get_content(nid)
    if "Reframed (2026-06-14)" not in cur:
        put_content(nid, banner + cur)

# 5) failover-domain orchestrator: always List<Member> --------------------
cur = get_content(SVCDOM)
add = ("<p><strong>Uniform contract:</strong> the orchestrator only ever handles a "
  "<code>List&lt;Member&gt;</code>. Single service or five, it iterates the same list — a length-1 "
  "group is the base case, not a branch. The per-member " + L("vote.verdictFor","Resolver") + " verdicts "
  "feed the group " + L("spi.ClusterCondition","ClusterCondition") + "; the ordered "
  + L("spi.FailoverPlanGenerator","Plan") + " is composed across members. See "
  + Lid(IDS['model.group'],"the uniform model") + ".</p>")
if "Uniform contract" not in cur:
    put_content(SVCDOM, add + cur)

# 6) Add the one-member domain to the catalog -----------------------------
note(FDOMS, "mysql-only (a single service, modelled as a one-member group)",
  "<p>🚧 Shows the uniformity: orchestrating mysql alone uses the <em>identical</em> schema as the "
  "five-member " + Lid('0SVdzlRS80tY','btcl-sms') + " domain — just a member list of length 1, no VIP, "
  "no extra members.</p>"
  "<h4>Members (promote order)</h4>"
  "<table><thead><tr><th>#</th><th>service</th><th>action plugin</th><th>depends-on</th><th>status</th></tr></thead>"
  "<tbody>"
  f"<tr><td>1</td><td>mysql (promote)</td><td>{L('mysql.actions','nw-mysql / 5 SQL actions')}</td><td>—</td><td>✅ built</td></tr>"
  "</tbody></table>"
  f"<p>Trigger: member-odown(mysql). Same machinery as any group — see "
  f"{Lid(IDS['model.group'],'the uniform model')} and flow {Lid('g3vx2puMcuF0','nodeDiesGroupPromotes')}.</p>")

json.dump(IDS, open("/tmp/nw-v5-ids.json","w"), indent=1)
print("Pass F done")
