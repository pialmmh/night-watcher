#!/usr/bin/env python3
"""Pass H — correct the failover-domain service page to the BUILT core runtime."""
import urllib.request

TOKEN = open("/tmp/trilium-etapi-token").read().strip().splitlines()[0]
B = "http://10.9.9.6:7081/etapi"

def req(method, path, body=None, ctype="application/json"):
    data = body.encode() if isinstance(body, str) else None
    r = urllib.request.Request(B+path, data=data, method=method,
        headers={"Authorization": TOKEN, "Content-Type": ctype})
    with urllib.request.urlopen(r) as resp:
        raw = resp.read(); return raw.decode() if raw else None

def get(nid): return req("GET", f"/notes/{nid}/content", ctype="text/plain")
def put(nid, html): req("PUT", f"/notes/{nid}/content", html, ctype="text/plain")
def prepend(nid, banner):
    cur = get(nid)
    if "Built" not in cur.split("Used by")[0][:400]:
        put(nid, banner + cur)
def note(parent, title, content):
    nid = __import__("json").loads(req("POST","/create-note",
        __import__("json").dumps({"parentNoteId":parent,"title":title,"type":"text","content":content}))) ["note"]["noteId"]
    print("  +", title, nid); return nid

SVC="ITEbbTPjjBdn"; API="VPpn6kifZDYh"; DRIFT="80WH8wOXy1dj"
DOMAINOF="XukqB3JAwiqA"; PLAN="fhDzgrYDWqMz"; COND="s9tPdclhoMkN"; MEMBER="WxA5gMhOTShk"

# main page
put(SVC,
  "<p>The group orchestrator — the uniform engine that runs a failover over a "
  "<code>List&lt;Member&gt;</code>, single service or five. <strong>✅ Core built 2026-06-14</strong> "
  "(88 tests green) in <code>nw-core/com.tb.nw.core.domain</code>: the model, the config registry, "
  "the trigger, ordered-plan composition, and the executor + coordinator. A one-member group is the "
  "base case — there is no single-service code path.</p>"
  "<p>🚧 Remaining to wire live: the coordinator's auto-subscribe to the vote (the member→target map) "
  "and the @ConfigMapping runtime-verify — both land with the mock-harness rehearsal.</p>")

prepend(DOMAINOF, "<p>✅ <strong>Built</strong> — <code>DomainRegistry.domainOf/all/byId</code> "
  "(domain/api), loads <code>nw.domains[*]</code> via FailoverDomainConfig + DomainMapper. "
  "Tested: DomainRegistryTest (3) + DomainMapperTest (4).</p>")
prepend(PLAN, "<p>✅ <strong>Built</strong> — <code>GroupPlanGenerator</code> composes each member's "
  "sub-plan in promote order (contiguous index); <code>GroupFailoverExecutor</code> walks the steps "
  "(best-effort to the dying node, must-succeed to the new), quarantines the old active, marks the new. "
  "Tested: GroupPlanGeneratorTest (4) + GroupFailoverExecutorTest (5, incl. one-member end-to-end).</p>")
prepend(COND, "<p>✅ <strong>Built (built-in form)</strong> — <code>GroupTrigger</code> + "
  "<code>TriggerSpec member-odown:&lt;type&gt;</code> (the SMS domain's db-down rule). "
  "Tested: GroupTriggerTest (5). 🚧 full plugin <code>ClusterCondition</code> override still pending.</p>")
prepend(MEMBER, "<p>✅ <strong>Seam defined</strong> — the member SPI is the EXISTING "
  "<code>FailoverPlanGenerator</code> (the member's sub-plan) + <code>FailoverAction</code>; "
  "<code>PluginMemberPlanSource</code> looks them up per member and builds the ClusterView. "
  "No new plugin SPI. Tested: PluginMemberPlanSourceTest (2).</p>")

# new built-runtime leaves under api
note(API, "GroupTrigger — is the whole domain down?",
  "<p>✅ Built. Evaluates a domain's declared trigger over the live per-member verdicts.</p>"
  "<p><code>GroupTrigger.isDomainDown(group, member→verdict) → boolean</code> · "
  "<code>com.tb.nw.core.domain.api.GroupTrigger</code></p>"
  "<ul><li>built-in <code>member-odown:&lt;type&gt;</code>; new forms (node-heartbeat, quorum) add a TriggerSpec case</li>"
  "<li>deterministic — same verdicts ⇒ same decision on every agent</li></ul>")
note(API, "GroupFailoverExecutor — run one domain failover",
  "<p>✅ Built. The act side: gate → ordered group plan → quarantine old active → walk steps → mark new active → release gate. Plain, fully unit-tested.</p>"
  "<p><code>runFailover(FailoverGroup, Verdict) → GroupFailoverOutcome</code> · "
  "<code>com.tb.nw.core.domain.internal.GroupFailoverExecutor</code></p>"
  "<ul><li>best-effort to the dying node (quarantine is the durable fence), must-succeed to the new node</li>"
  "<li>stands down if another agent holds the gate; one-member domain promotes end-to-end (tested)</li></ul>")
note(API, "GroupCoordinator — the group failover entry",
  "<p>✅ Built (entry + executor wiring). Lazy bean beside the legacy FailoverCoordinator.</p>"
  "<p><code>evaluateDomains()</code> — check every domain's trigger over live verdicts · "
  "<code>declareDomainDown(group, verdict)</code> — run the failover off the watcher thread · "
  "<code>com.tb.nw.core.domain.api.GroupCoordinator</code></p>"
  "<ul><li>🚧 does not auto-subscribe to the vote yet (avoids double-firing with the legacy path) — the rehearsal calls evaluateDomains()</li></ul>")

# drift report
put(DRIFT,
  "<p>✅ <strong>Core built + unit-tested 2026-06-14</strong> (commits 0e0c33e…68b7b05, 88 tests green):</p>"
  "<ul><li>model FailoverGroup/Member (one-member = base case) · DomainRegistry config loader</li>"
  "<li>GroupTrigger (member-odown) · GroupPlanGenerator + MemberPlanSource seam</li>"
  "<li>PluginMemberPlanSource (real lookup + ClusterView) · GroupFailoverExecutor (gate→plan→dispatch→roles) · GroupCoordinator</li></ul>"
  "<p>🚧 <strong>Remaining (lands with the rehearsal):</strong></p>"
  "<ul><li>GroupCoordinator auto-subscribe to the vote (member→target mapping) — today the entry is called explicitly</li>"
  "<li>@ConfigMapping <code>nw.domains[*]</code> runtime-verify (unit-tested via record doubles; live binding checked when a tenant declares a domain)</li>"
  "<li>nw-vip / nw-fence members + group-level VIP step; multi-member rehearsal</li>"
  "<li>retire the legacy single-target FailoverCoordinator once one-member domains carry mysql</li></ul>")

print("Pass H done")
