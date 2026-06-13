#!/usr/bin/env python3
"""Move-only refactor engine for night-watcher → code-master folder vocabulary.

Pure git mv + package-decl rewrite + \\b-bounded FQN rewrite across every .java.
No logic changes. Run one batch, then `mvn install` must be green before the next.
"""
import os, re, subprocess, sys, json

ROOT = "/home/mustafa/telcobright-projects/routesphere/night-watcher/nw-agent"
SRC_GLOB_DIRS = []  # filled at runtime

def all_java():
    out = subprocess.run(["bash", "-lc",
        f"find {ROOT} -name '*.java' -not -path '*/target/*'"],
        capture_output=True, text=True).stdout
    return [l for l in out.splitlines() if l.strip()]

def git_mv(old, new):
    os.makedirs(os.path.dirname(new), exist_ok=True)
    r = subprocess.run(["git", "-C", ROOT, "mv", old, new], capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"git mv failed: {old} -> {new}\n{r.stderr}")

def rewrite_package_decl(path, new_pkg):
    with open(path) as f: s = f.read()
    s2 = re.sub(r'^package\s+[\w.]+\s*;', f'package {new_pkg};', s, count=1, flags=re.M)
    if s2 == s:
        raise RuntimeError(f"no package decl rewritten in {path}")
    with open(path, "w") as f: f.write(s2)

def apply_renames(rename_map):
    """rename_map: {old_fqn: new_fqn}. Apply \\b-bounded across all .java. Longest first."""
    pairs = sorted(rename_map.items(), key=lambda kv: -len(kv[0]))
    compiled = [(re.compile(r'\b' + re.escape(o) + r'\b'), n) for o, n in pairs]
    changed = 0
    for jf in all_java():
        with open(jf) as f: s = f.read()
        orig = s
        for rx, n in compiled:
            s = rx.sub(n, s)
        if s != orig:
            with open(jf, "w") as f: f.write(s)
            changed += 1
    return changed

def edit_file(path, old, new):
    with open(path) as f: s = f.read()
    if old not in s:
        raise RuntimeError(f"'{old}' not found in {path}")
    with open(path, "w") as f: f.write(s.replace(old, new))

def run_batch(moves, services_edits=None):
    """moves: list of (rel_old, rel_new, old_fqn, new_fqn). rel_* under ROOT."""
    rename_map = {}
    # 1) git mv + package decl
    for rel_old, rel_new, old_fqn, new_fqn in moves:
        old = os.path.join(ROOT, rel_old); new = os.path.join(ROOT, rel_new)
        git_mv(old, new)
        new_pkg = new_fqn.rsplit(".", 1)[0]
        rewrite_package_decl(new, new_pkg)
        rename_map[old_fqn] = new_fqn
    # 2) global FQN rewrite
    n = apply_renames(rename_map)
    # 3) services / resource edits
    for path, old, new in (services_edits or []):
        edit_file(os.path.join(ROOT, path), old, new)
    print(f"  moved {len(moves)} files, rewrote FQNs in {n} files, "
          f"{len(services_edits or [])} resource edits")

# ---------------------------------------------------------------- batch specs
def batch_A_spi():
    base = "nw-spi/src/main/java/com/tb/nw/spi"
    classes = [f[:-5] for f in sorted(os.listdir(os.path.join(ROOT, base))) if f.endswith(".java")]
    moves = []
    for c in classes:
        moves.append((f"{base}/{c}.java", f"{base}/api/{c}.java",
                      f"com.tb.nw.spi.{c}", f"com.tb.nw.spi.api.{c}"))
    run_batch(moves)

import glob
def find_main(cls):
    hits = glob.glob(f"{ROOT}/nw-core/src/main/java/com/tb/nw/core/**/{cls}.java", recursive=True) \
         + glob.glob(f"{ROOT}/nw-core/src/main/java/com/tb/nw/core/{cls}.java")
    hits = sorted(set(hits))
    if len(hits) != 1:
        raise RuntimeError(f"find_main {cls}: {hits}")
    rel = os.path.relpath(hits[0], ROOT)
    pkg = os.path.dirname(rel).split("java/")[1].replace("/", ".")
    return rel, f"{pkg}.{cls}"

# nw-core target layout: service -> subpkg -> [classes]
CORE = {
  "":           {"dependencies": ["AgentConfig","CorePluginDescriptor","FabricProducer","PluginRegistry"]},
  "observe":    {"api": ["InvestigatorRunner","FacetRegistry"],
                 "publishes": ["ObservationPublisher","FacetPublisher","HostUptimeEvent"],
                 "internal": ["HostUptimeHealthCheck","StaticFacetDetector"],
                 "dependencies": ["StaticFacetsConfig"]},
  "cache":      {"api": ["ObservationCache"]},
  "vote":       {"api": ["Resolver"], "internal": ["FailureTracker"]},
  "coordinator":{"api": ["FailoverCoordinator"], "dependencies": ["FailoverConfig"],
                 "publishes": ["MasterDeadDetected","MasterFenced","SlavePromoted"]},
  "boards":     {"api": ["AgentDirectory","FailoverGate","RoleStore"]},
  "dispatch":   {"api": ["Dispatcher"], "publishes": ["CommandEnvelope","ResultEnvelope"]},
  "door":       {"api": ["ActionEndpoint"], "internal": ["ActionRegistry","IdempotencyStore"]},
  "lifecycle":  {"api": ["AgentInfo","FabricReadyCheck"],
                 "internal": ["AgentLifecycleMachine","AgentState","AgentLease",
                              "DrainRequested","FabricReachable","FabricUnreachable",
                              "FacetsDetected","InvestigatorsArmed"],
                 "publishes": ["Heartbeat"]},
  "sm":         {"api": ["StateMap"], "internal": ["StateDef","Transition"]},
  "config":     {"internal": ["TenantProfileConfigSource"]},
}
CORE_TESTS = {  # test file (rel to src/test/java/com/tb/nw) -> new package
  "core/vote/ResolverTest.java":        "com.tb.nw.core.vote.api",
  "core/vote/FailureTrackerTest.java":  "com.tb.nw.core.vote.internal",
  "core/cache/ObservationCacheTest.java":"com.tb.nw.core.cache.api",
  "core/sm/StateMapTest.java":          "com.tb.nw.core.sm.api",
  "core/boards/FailoverGateTest.java":  "com.tb.nw.core.boards.api",
  "core/boards/RoleStoreTest.java":     "com.tb.nw.core.boards.api",
  "core/TestCore.java":                 "com.tb.nw.core.dependencies",
  "core/lifecycle/TestLifecycle.java":  "com.tb.nw.core.lifecycle.internal",
}

def batch_C_core():
    moves = []
    simple_to_fqn = {}
    for service, subs in CORE.items():
        sdir = f"core/{service}".rstrip("/") if service else "core"
        for sub, classes in subs.items():
            for c in classes:
                rel_old, old_fqn = find_main(c)
                new_pkg = f"com.tb.nw.{sdir.replace('/', '.')}.{sub}"
                new_rel = f"nw-core/src/main/java/{new_pkg.replace('.', '/')}/{c}.java"
                moves.append((rel_old, new_rel, old_fqn, f"{new_pkg}.{c}"))
                simple_to_fqn[c] = f"{new_pkg}.{c}"
    # test moves
    test_moves = []
    for rel, newpkg in CORE_TESTS.items():
        old = f"nw-core/src/test/java/com/tb/nw/{rel}"
        cls = os.path.basename(rel)
        new = f"nw-core/src/test/java/{newpkg.replace('.', '/')}/{cls}"
        old_fqn = "com.tb.nw." + rel[:-5].replace("/", ".")
        test_moves.append((old, new, old_fqn, f"{newpkg}.{cls[:-5]}"))
    services_edits = [("nw-core/src/main/resources/META-INF/services/io.smallrye.config.ConfigSourceFactory",
                       "com.tb.nw.core.config.TenantProfileConfigSource",
                       "com.tb.nw.core.config.internal.TenantProfileConfigSource")]
    run_batch(moves + test_moves, services_edits)
    json.dump(simple_to_fqn, open("/tmp/nw-core-map.json", "w"), indent=1)
    print(f"  core map: {len(simple_to_fqn)} classes -> /tmp/nw-core-map.json")

def relocate(cls, from_fqn, to_fqn):
    """Move an already-moved class to a different subpackage (access-violation fix).
    Updates the persisted core map + git mv + package decl + global FQN rewrite."""
    src = f"{ROOT}/nw-core/src/main/java/{from_fqn.replace('.','/')}.java"
    dst = f"{ROOT}/nw-core/src/main/java/{to_fqn.replace('.','/')}.java"
    git_mv(src, dst)
    rewrite_package_decl(dst, to_fqn.rsplit(".",1)[0])
    apply_renames({from_fqn: to_fqn})
    mp = json.load(open("/tmp/nw-core-map.json")); mp[cls] = to_fqn
    json.dump(mp, open("/tmp/nw-core-map.json","w"), indent=1)
    print(f"  relocated {cls}: {from_fqn} -> {to_fqn}")

def fix_imports(scope, pl="nw-core", mapfile="/tmp/nw-core-map.json"):
    """Compiler-driven: add intra-service-split imports the mechanical pass can't see."""
    simple_to_fqn = json.load(open(mapfile))
    goal = "compile" if scope == "main" else "test-compile"
    for it in range(20):
        r = subprocess.run(["bash","-lc",
            f"cd {ROOT} && mvn -q -pl {pl} -am {goal} 2>&1"],
            capture_output=True, text=True)
        out = r.stdout + r.stderr
        if "BUILD SUCCESS" in out or r.returncode == 0:
            print(f"  {goal}: green after {it} import-fix rounds"); return
        # parse cannot-find-symbol class diagnostics
        adds = {}  # file -> set(fqn)
        cur = None
        for line in out.splitlines():
            m = re.search(r'(/\S+\.java):\[\d+,\d+\] (cannot find symbol|package (\w+) does not exist)', line)
            if m:
                cur = m.group(1)
                # `package X does not exist` carries the class name inline (X.static ref)
                if m.group(3) and m.group(3) in simple_to_fqn:
                    adds.setdefault(cur, set()).add(simple_to_fqn[m.group(3)])
                continue
            m2 = re.search(r'symbol:\s+(?:class|variable|static)\s+(\w+)', line)
            if m2 and cur and m2.group(1) in simple_to_fqn:
                adds.setdefault(cur, set()).add(simple_to_fqn[m2.group(1)])
        if not adds:
            print(f"  {goal}: STUCK round {it} — no fixable imports. First errors:")
            print("\n".join(l for l in out.splitlines() if "ERROR" in l)[:1500]); return
        for f, fqns in adds.items():
            with open(f) as fh: s = fh.read()
            existing = set(re.findall(r'import\s+([\w.]+);', s))
            newimps = "".join(f"import {q};\n" for q in sorted(fqns) if q not in existing)
            if not newimps: continue
            s = re.sub(r'(package [\w.]+;\n)', r'\1' + newimps, s, count=1)
            with open(f, "w") as fh: fh.write(s)
        print(f"  round {it}: added imports to {len(adds)} files")
    print("  WARN: hit 20 rounds")

PLUGINS = {
  "plugins/nw-mysql": ("com.tb.nw.plugins.mysql", {
    "api": ["MysqlFacetDetector","MySqlLocalProbe","MySqlRemoteClient",
            "MysqlCandidateSelector","MysqlFailoverPlanGenerator","MysqlHealthAggregator",
            "MysqlFenceMasterAction","MysqlPromoteSlaveAction","MysqlSetReadOnlyAction",
            "MysqlStartReplicaAction","MysqlStopReplicaAction","MysqlPluginDescriptor"],
    "publishes": ["MySqlCommandEvent","MySqlCommandResultEvent","MySqlCommandResult",
                  "MySqlFenceMasterCommand","MySqlHealthEvent","MySqlNwEvent",
                  "MySqlPromoteSlaveCommand","MySqlRemoteHealth","MySqlSetReadOnlyCommand",
                  "MySqlStartReplicaCommand","MySqlStopReplicaCommand"],
    "dependencies": ["MysqlConfig","MysqlConnections","MysqlPlugin"],
    "internal": ["MysqlReplicaSql"]}),
  "plugins/nw-mock": ("com.tb.nw.plugins.mock", {
    "api": ["MockClientProbe","MockFacetDetector","MockLocalProbe","MockPeerProbe",
            "MockCandidateSelector","MockFailoverPlanGenerator","MockFenceAction",
            "MockHealthAggregator","MockPromoteAction","MockPluginDescriptor"],
    "publishes": ["MockCommandResult","MockFenceCommand","MockHealth","MockPromoteCommand"],
    "dependencies": ["MockConfig"],
    "internal": ["MockStateStore"]}),
}
PLUGIN_TESTS = {  # rel under module src/test/java -> new package
  "plugins/nw-mysql": {"com/tb/nw/plugins/mysql/orchestrator/MysqlHealthAggregatorTest.java": "com.tb.nw.plugins.mysql.api",
                       "com/tb/nw/plugins/mysql/orchestrator/MysqlCandidateSelectorTest.java": "com.tb.nw.plugins.mysql.api"},
  "plugins/nw-mock":  {"com/tb/nw/plugins/mock/orchestrator/MockHealthAggregatorTest.java": "com.tb.nw.plugins.mock.api"},
}

def find_in(module, cls):
    hits = sorted(set(glob.glob(f"{ROOT}/{module}/src/main/java/**/{cls}.java", recursive=True)))
    if len(hits) != 1: raise RuntimeError(f"find_in {module} {cls}: {hits}")
    rel = os.path.relpath(hits[0], ROOT)
    pkg = os.path.dirname(rel).split("java/")[1].replace("/", ".")
    return rel, f"{pkg}.{cls}"

def batch_D_plugins():
    moves, smap = [], {}
    for module, (basepkg, subs) in PLUGINS.items():
        for sub, classes in subs.items():
            for c in classes:
                rel_old, old_fqn = find_in(module, c)
                new_pkg = f"{basepkg}.{sub}"
                new_rel = f"{module}/src/main/java/{new_pkg.replace('.','/')}/{c}.java"
                moves.append((rel_old, new_rel, old_fqn, f"{new_pkg}.{c}"))
                smap[c] = f"{new_pkg}.{c}"
    for module, tests in PLUGIN_TESTS.items():
        for rel, newpkg in tests.items():
            cls = os.path.basename(rel)
            old = f"{module}/src/test/java/{rel}"
            new = f"{module}/src/test/java/{newpkg.replace('.','/')}/{cls}"
            old_fqn = rel[:-5].replace("/", ".")
            moves.append((old, new, old_fqn, f"{newpkg}.{cls[:-5]}"))
    # delete defunct package-info.java (probe/, orchestrator/ packages vanish)
    for pi in glob.glob(f"{ROOT}/plugins/*/src/main/java/**/package-info.java", recursive=True):
        subprocess.run(["git","-C",ROOT,"rm","-q",os.path.relpath(pi,ROOT)], check=True)
    run_batch(moves)
    json.dump(smap, open("/tmp/nw-plugins-map.json","w"), indent=1)
    print(f"  plugins map: {len(smap)} classes")

def batch_B_fabric():
    # nw-fabric-api already IS the api/ folder (com.tb.nw.fabric.api). Only the
    # etcd impl module moves: com.tb.nw.fabric.etcd -> com.tb.nw.fabric.internal.
    base = "nw-fabric-etcd/src/main/java/com/tb/nw/fabric/etcd"
    classes = [f[:-5] for f in sorted(os.listdir(os.path.join(ROOT, base))) if f.endswith(".java")]
    moves = []
    for c in classes:
        moves.append((f"{base}/{c}.java",
                      f"nw-fabric-etcd/src/main/java/com/tb/nw/fabric/internal/{c}.java",
                      f"com.tb.nw.fabric.etcd.{c}", f"com.tb.nw.fabric.internal.{c}"))
    run_batch(moves)

if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "fiximports":
        # fiximports <scope> [pl] [mapfile]
        fix_imports(*sys.argv[2:])
    else:
        {"A": batch_A_spi, "B": batch_B_fabric, "C": batch_C_core,
         "D": batch_D_plugins}[cmd]()
        print("batch", cmd, "done")
