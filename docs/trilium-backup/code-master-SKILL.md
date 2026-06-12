---
name: code-master
description: >
  THE single method skill: how we DESIGN (concern → feature → flows → services), how we
  DOCUMENT in Trilium (story-first feature pages, flow pages, slim service pages, leaf
  shape, voice rules), how we WRITE CODE (one package shape in every language, filename
  parity, twin-or-nothing, repository rule, SRP, naming, FSM shape, logging), and how
  changes LAND (move-only batches, gates). Auto-injected on session start in projects
  that opt in (a tracked `.claude/use-code-master` marker file in the repo). Sections
  1–4 are generic; section 5 holds per-project bindings — read only YOUR project's.
  Supersedes /spec-tree, /service-doc and /code-convention (all retired).
---

# /code-master — design · documentation · code · process, one method

Everything here is law, not suggestion. Sections 1–4 apply to every opted-in project.
Section 5 = per-project bindings (ids, paths, frozen seams) — use only your project's.

## 1 · DESIGN — the chain

```
concern (a folder of related work)
└── feature (ONE task; done = user-visible outcome)
    └── flows (1..N — a feature is fulfilled when ALL its flows exist)
        └── steps → service leaves (api / spi / publishes / subscriptions)  (N:M)
```

- **Cardinality:** concern 1→N features · feature 1→N flows · flow N→M service leaves.
- **Flow ownership:** every flow has ONE home feature. Other features LINK it, never copy.
- **Naming is the glue:** `packageName (Human description)` — the SAME string is the code
  folder, the service page title, and the concern title. Flows are camelCase trigger
  phrases (`bytesResolveOnView`, `cacheEvictsOnBudget`).
- **Flow start points:** a service api (▶), a subscription (⤳), or a trigger (⏰).
  Connectors: ▶ api call · ⤳ pub/sub · ↩ callback/return · ⏰ trigger.
- **NO-ORPHAN RULE:** every flow hop links a real service leaf. Nothing to link = the
  catalog is missing a service — add the service note, never hand-wave the hop.
- **Services live in ONE home** under their runtime unit: `apps → [platforms] → services`
  (app services are POLYGLOT: the same Trilium note CLONED under every platform) ·
  `services → <backend units>` (shared backend) · product apps carry their own
  frontend/backend subtrees.

## 2 · DOCUMENTATION — the Trilium shapes

### Voice rules (bind every page)
1. Fluent, story-like, SIMPLE English — short sentences, common words, readable by
   non-native speakers. No idioms, no clever phrases.
2. Concrete over abstract — "INSERT into sqlite", "send over the XMPP connection";
   never "fold a fact into the store".
3. Short paragraphs or bullets — never walls of text. Mix paragraph/table/bullets by fit.
4. MENTION = LINK — every service/api/event name in ANY page reference-links its leaf.

### Feature page (the combined unit — story + flows + contract)
1. **STORY** — 5–8 plain lines, present tense, named actors: what the user experiences.
2. **FLOWS** — index list, one line each: *flowName — when it runs*. Own flows = child
   notes; borrowed flows = links to their home feature.
3. **TOUCHPOINTS** — small table: every service leaf its flows touch (union), owner service.

### Flow page (child of its feature)
1. One line: the trigger ("runs when Alice taps a photo bubble").
2. The mermaid diagram (child note `type=mermaid`, embedded:
   `<section class="include-note" data-note-id="ID" data-box-size="full"></section>`).
3. Numbered steps — every hop a clickable leaf link — then a no-orphan check line.

### Service page (slim — the dictionary, not the storybook)
- One-line description → attribute CHILD NOTES: `dependencies · api · spi · publishes ·
  subscriptions · triggers · entities · invariants · failure · observability · lifecycle ·
  test-surface · drift report`. Behavior-stories only for service-internal behavior;
  situation-stories live in features.
- api/spi/publishes/subscriptions/triggers hold ONE LEAF PER ITEM.

### Leaf shape (api/spi/publish/subscribe/trigger items)
1. ONE-LINE description (fluent, simple). 2. The signature. 3. Bullets (behavior,
idempotency, caller, thread, failure). 4. **"Used by:"** backlinks to every flow that hops
through it (maintained by the flow author — an unlinked leaf is dead code or missing docs).

### Honesty rules
- A missing edge is information: write "none — why".
- Unbuilt = 🚧 banner, never pretended. Drift report on every service page.
- Entities defined once, linked everywhere. Update-in-place; never delete others' notes.

### Mechanics
ETAPI `http://10.9.9.6:7081/etapi`, token = first line of `/tmp/trilium-etapi-token` (raw
Authorization header). PUT content uses `Content-Type: text/plain`. Clone = POST /branches
{noteId,parentNoteId}. Mermaid notes: `type:"mermaid"`.

## 3 · CODE — one package shape, every language

```
<packageName>/                ← the doc name, verbatim; casing per platform idiom
  api/            what I offer (callable in)
  spi/            what I require; host implements & injects (fakes = the test surface)
  publishes/      typed events out (one concrete type per kind)
  subscriptions/  bus bindings — bus services only; absence is information
  dependencies/   builder + config record — everything injected, NEVER located
  internal/       implementation (.cpp lives here too); no outside imports
  test/           that package's tests
```

- Discover any system by scanning `**/api`, `**/spi`, `**/publishes`, `**/subscriptions` —
  never by reading `internal/`.
- **Tree + filename parity across languages:** the contract folders have IDENTICAL trees
  and IDENTICAL file basenames in every language (`XmppCore.hpp ↔ XmppCore.ts`).
  `internal/` may differ by idiom — every difference gets a row in the parity matrix,
  never silent. Acceptance: `tree -d` per package identical across langs.
- **C++ layout:** NO `include/<ns>/` nesting, NO `src/` — headers co-locate in the
  contract folders, impls in `internal/`, include style `#include "<pkg>/api/X.hpp"`
  rooted at the core dir. Namespaces are language-internal (renames need ratification).
- **TWIN-OR-NOTHING:** no feature lands in one core language without its twin in the SAME
  commit/batch — both impls + the mirrored test in both suites, one executor.
  Platform-only pieces are declared platform-pairs in the parity matrix.
- **Repository rule:** Db/Media repositories are PASSED in (builder injection). Repo/SQL
  code lives ONLY in the storage package — any stream may add apis THERE; never inline in
  a consumer.

### House style
- **SRP, one sentence:** a unit has one reason to change — if you need "and", split it.
- **Small files:** ~200–300 lines soft ceiling; one primary type per file. A growing file
  is a prompt to extract a collaborator, not to keep appending.
- **Short named orchestrators:** entry points name the work, they don't contain it —
  top-level body ≤ ~10 lines of named business steps; loops/guards one layer down.
  Helpers called once are fine — clarity over reuse.
- **Self-describing names over comments:** rename instead of explaining. Comments earn
  their keep only on extension hooks (the override contract), non-obvious
  invariants/ordering, and parameter meanings the type can't capture.
- **Wrappers only when they earn their keep:** justified only between two evolving
  boundaries, with real ritual inside and escape hatches out. If a base class is already
  the facade, extend it — don't re-wrap it.
- **State machines — the canonical shape:** states = enum; event TYPES = enum; payloads
  implement the event interface with a static kType; immutable transition table from a
  fluent builder; handlers return the next state; unhandled pairs silently dropped; ONE
  thread drives (marshal via a scheduler); every transition logged at debug. Concrete
  events `Ev`-prefixed; context actions `do`-prefixed.
- **RULE ONE logging:** debug mode logs everything; production logs only milestones
  (login/connect/resume) + failures — never per-message/per-event. Use a macro/guard that
  short-circuits on the debug flag.

## 4 · PROCESS — how changes land

- **Move-only batches:** structure changes are pure `git mv` + mechanical reference
  rewrites; ZERO logic changes in the same commit; build + full tests green BEFORE and
  AFTER. Mirrored trees move in ONE batch by ONE executor.
- **Gates (every batch):** zero-straggler grep over the old names · TS VALUE-IMPORT probe
  (strip-types erases type-imports — a broken type-import passes smoke; also grep bare
  side-effect imports) · frozen-seam byte-diff (conflict seds must EXCLUDE wire strings) ·
  doc path-mentions updated in the same commit.
- **Docs and code are two views of one contract:** changing api/spi/publishes updates the
  service page leaves in the same work unit; flows updated when hops change; drift report
  tells the truth.
- **Collision protocol:** announce files before touching another agent's territory; the
  mover updates importers; merge-asks to the architect, who merges per-area with the
  gauntlet between.
- **Refactoring an existing project to this method:** (P0) architect publishes the name
  map + frozen-seams list → (docs first) review code, write service pages + drift
  reports, break concerns into features → (P1) move-only folder-vocabulary batch →
  (P2) package assembly + small-service splits per drift reports → flows last, no orphans.

## 5 · PROJECT BINDINGS — read ONLY your project's block

### secure-link
- Trilium: method canon `CodeAndDocStyle` = `0VzUoH93vPcH` (Code `iHj1W6qrfktp` · Doc
  `fUmqyjXHbhIg` · AI_Skills `kEHrZBsX1Tnd`) · live docs `Doc-V5.0` = `5gv3kXF0RCP1` ·
  pilot/exemplar concern `appChatHandling` = `QuRznk6aUJPb` · parity matrix `dHelRlQPYKgk`.
- Code: cores at `frontend/core/{cpp,ts}` · apps `frontend/app/*` · bridges
  `frontend/bridge/*` · backend units `backend/*`.
- FROZEN SEAMS (architect ratification only): C ABI (`c_api.h` ↔ `NativeMethods.cs`,
  `sl_storage_*` wasm batch, 21 exports) · ChatMutation proto · REST paths · sync-proxy WS
  frames · the TS `MessageStore` port shape · projection tables.
- Old trees (Concerns-V2, ConcernsMap v1, Doc-V4.0) = read-only trail.

### NightWatcher
- Trilium: live docs `Doc-V5.0 — NightWatcher (code-master)` = `DgSlZt47IHna`
  (concerns `serviceObservation` · `clusterConsensus` · `failoverOrchestration` ·
  `agentLifecycle` · `pluginExtension` → 9 features → 12 flows; services home
  `nw-agent` = 14 leaf-exploded pages). Old `Doc-V4.0` = `zXWUzVUhC3xo` is bannered
  READ-ONLY trail. Refactor Batch = `dYDRKAkpemgt` (EXECUTED 2026-06-13: P0–P3
  pushed, commits 6807806…88a610e, 59 tests green).
- Code (repo `routesphere/night-watcher`, git remote pialmmh/night-watcher): runtime
  units = **nw-agent** (Java 21 / Quarkus maven multi-module: `nw-spi` · `nw-fabric-api`
  · `nw-fabric-etcd` · `nw-core` · `plugins/nw-mysql` · `plugins/nw-mock` · `nw-dist`)
  · **dashboard** (React 18 + Vite, port 7100) · **security-bundle** (transitional
  Dockerfile + supervisord, mid-compose-migration). Build `cd nw-agent && mvn -DskipTests
  clean package`; tests `mvn test` (59, JUnit 5, FakeFabric testkit in
  `nw-core/src/test/java/com/tb/nw/testkit/`).
- Packages already follow the contract shape from the api/spi refactor:
  `nw-core/com.tb.nw.core.{observe,cache,vote,coordinator,boards,dispatch,door,
  lifecycle,sm,config}` · plugins split `probe/` (observe side) vs `orchestrator/`
  (act side) + `events/`. Remaining V5 deltas land via §4 P1/P2 batches.
- FROZEN SEAMS (architect ratification only):
  · **etcd key schema** (agent-to-agent wire): `/clusters/<cluster>/observations/…` ·
    `/clusters/<cluster>/roles/<node>` (values `master|slave|quarantined`) ·
    `/clusters/<cluster>/failover/{lock,epoch}` · `/clusters/<cluster>/agents/<node>` ·
    `/agents/<node>/{heartbeat,facets}`.
  · **Observation JSON** persisted under the observations prefix (typed event payloads
    per plugin, `com.tb.nw.spi.Observation` envelope fields).
  · **Command door wire**: `POST /agent/command` carrying
    `CommandEnvelope{commandClass, command:rawJson}` → `ResultEnvelope`; identity gates
    run before typed parsing. `GET /agent/info` · `/q/health/{live,ready}`.
  · **Plugin SPI** `com.tb.nw.spi` (HealthCheck, FacetDetector, FailoverAction,
    HealthAggregator, CandidateSelector, FailoverPlanGenerator, ClusterCondition,
    StatefulAction + event/record types) and `com.tb.nw.fabric.api` (Fabric KV/lease/
    lock/txn).
  · **Ports**: 7102 agent HTTP (status + command door) · 7103 reserved mTLS gRPC ·
    2379/2380 bundled etcd.
  · **Config keys** `nw.*` env contract (`NW_CLUSTER_NAME`, `NW_MYSQL_PASSWORD`,
    `NW_BIND_HOST` — bind fail-secure 127.0.0.1, never 0.0.0.0 by default).
- Test-harness convention (not frozen, but shared): nw-mock file protocol
  `/tmp/nw-mock/{node}.state|.role` + per-seat `@client`/`@peer` override files.
