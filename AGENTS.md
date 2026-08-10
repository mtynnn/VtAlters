# vAltars agent guide

## Scope and ownership

- This repository contains only vAltars. Do not edit sibling repositories from this directory.
- Preserve `LICENSE` and the original MIT attribution to thangks.
- Do not commit, push, deploy, or invent private integration JARs unless explicitly requested.

## Supported stack

- Java 21, Gradle Kotlin DSL, Gradle Wrapper 9.1.0.
- Paper API 1.21.11+.
- Required integration: `io.lumine:Mythic-Dist:5.11.2` from Lumine's public repository.
- Optional integration: `com.nexomc:nexo:1.8.0` from Nexo's releases repository.
- Main package: `com.valerinsmp.valtars`; plugin identity and version: `vAltars` `1.1.0`.

## Non-negotiable runtime invariants

- One `RitualSession` per altar. Its only transitions are `ACTIVE -> COMMITTED` or `ACTIVE -> ROLLED_BACK`, once.
- Commit only after MythicMobs returns a valid, live `LivingEntity`. A post-commit metadata, effect, message, or cleanup error must never refund a live boss.
- Session snapshots are authoritative and immutable. Displays are visual projections and cleanup is scoped to the owning altar/session.
- The configured central amount is captured as one immutable session/refund snapshot; legacy altars default to one. Failed spawn returns the full configured amount.
- Ritual motion uses temporary non-persistent `ItemDisplay` projections with Paper teleport interpolation, never dropped-item physics.
- The single next-requirement hologram is a non-persistent projection scoped per altar. It uses Nexo's built item effective name and never becomes item authority.
- Requirements are ordered pedestal slots and duplicates never merge. One pedestal owns one natural `ItemStack` and one contributor; only that contributor may top up the same stack.
- Incomplete pedestal contributions expire per altar after the configured idle deadline or when no contributor remains nearby. Persist every refund before removing its placement/display.
- Persist refunds before ending a rollback. Keep offline and full-inventory leftovers in the single `pending-refunds.yml` mailbox until fully acknowledged.
- Delivery uses durable `PENDING`/`CLAIMED`, a unique refund UUID, and a temporary PDC tag. Partial delivery rotates the leftover to a fresh UUID.
- Do not claim atomic exactly-once semantics across Bukkit player data and the YAML file. Preserve the documented recovery compromise.
- Existing sessions retain their configuration snapshot. Reload validates then applies only to new sessions and does not re-register listeners.
- Reject edits to an occupied altar; do not couple unrelated altars.
- Bukkit, Paper, Inventory, PDC, `Player#saveData`, MythicMobs, and Nexo access stays on the primary thread.
- The admin altar browser is read-only except for same-thread teleportation. It does not edit altars or own ritual state.

## Change discipline

- Keep the domain small. Do not introduce generic transaction frameworks, event sourcing, repository layers, or speculative states.
- Maintain canonical `valtars.*` permissions and runtime compatibility with `vtalters.*`.
- Keep public `/valtars help|about`; administrative work belongs to `/valtarsadmin` and its aliases.
- Legacy migration from `plugins/VtAlters` must remain idempotent, backed up, and non-overwriting.
- Use official documentation or published artifacts for MythicMobs/Nexo changes.

## Verification

Run before handoff:

```powershell
.\gradlew.bat clean test build --no-daemon --max-workers=1 --console=plain
```

Inspect the JAR contents and `plugin.yml`, report the SHA-256, and keep Paper/Mythic/Nexo smoke testing blocked unless all real server/plugin JARs are present.
