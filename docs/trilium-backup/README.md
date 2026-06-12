# Trilium doc backup — Doc-V5.0 (code-master)

Disaster-recovery snapshot of the night-watcher documentation that lives in
Trilium (server 10.9.9.6:7081). Taken 2026-06-13, right after the code-master
migration (P0 bindings + Phase 1 doc set).

| File | What it is |
|---|---|
| `doc-v5.0-nightwatcher.zip` | Full HTML export of the LIVE doc set: umbrella `DgSlZt47IHna` — 5 concerns, 9 features, 12 flows (mermaid + leaf-linked steps), 14 leaf-exploded service pages. |
| `doc-v4.0-trail.zip` | The read-only V4 trail (`zXWUzVUhC3xo`), bannered. |
| `nw-v5-ids.json` | Note-id map for every tracked V5 page/leaf — needed to script further ETAPI edits. |
| `nw_v5_passA.py` / `nw_v5_passB.py` | The generators that built the V5 tree (scaffold+services+leaves / concerns+features+flows+backlinks). Re-runnable against a fresh Trilium to rebuild from scratch. |
| `code-master-SKILL.md` | Snapshot of the method skill incl. the NightWatcher section-5 bindings (canonical copies: `~/.claude/skills/code-master/SKILL.md` + Trilium `FSrr3QC646AM`). |

Restore: import the zip into Trilium (note tree → import), or re-run the two
pass scripts against ETAPI (token in `/tmp/trilium-etapi-token`).
