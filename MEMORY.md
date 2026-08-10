# vAltars internal memory

Last updated: 2026-08-10

## Current contract

- Identity: `vAltars` 1.1.0, `com.valerinsmp.valtars`, Paper 1.21.11+, Java 21, Gradle Wrapper 9.1.0.
- MythicMobs is required and Nexo is optional. Both dependencies resolve from their official public repositories; no `systemPath` or local integration JAR is used.
- Public root: `/valtars help|about`. Admin root: `/valtarsadmin`; aliases `/altar`, `/vta`, `/vtalters`.
- Canonical permissions are `valtars.admin` and `valtars.command.*`; legacy `vtalters.*` is accepted at runtime.
- `/valtarsadmin gui` is a simple paginated admin browser guarded by `valtars.command.teleport`; it shows altar metadata/state and teleports above the configured center without editing ritual state.

## Ritual and item invariants

- `RitualSessions` enforces one session per altar while allowing unrelated altars concurrently.
- `RitualSession` starts `ACTIVE` and has one idempotent terminal transition to `COMMITTED` or `ROLLED_BACK`.
- The exact commit point is a non-null, valid, non-dead `LivingEntity` returned by the Mythic adapter. Post-commit failures only log and clean up; they do not refund while the boss lives.
- The session owns cloned item snapshots, contributor data, pedestal origins through each `RefundEntry.source`, and the immutable `RitualSettings` captured at start.
- Displays and animations are projections scoped by altar. They are never item authority.
- Ritual animation uses non-persistent `ItemDisplay` entities and Paper teleport interpolation instead of dropped `Item` physics; all visuals are removed on terminal cleanup.
- `central-item-amount` stores the activation quantity. Missing legacy values load as one; activation captures, consumes, visualizes, and refunds the configured stack amount as one immutable snapshot.
- One non-persistent `TextDisplay` per altar shows only the first incomplete pedestal, its remaining amount, and the effective name of the item built through the official Nexo API. It advances after each completed slot and is removed during ritual/cleanup.
- Requirements are ordered one-to-one with pedestals and duplicate item IDs remain separate slots. One pedestal stores one natural `ItemStack` owned by one contributor; readiness checks that exact slot and only the owner may top it up. Incomplete contributions reset only their altar's 45-second idle deadline; full stacks are durably refunded on expiry or when no contributor remains online within the configured 16-block radius.
- Same-altar edits are rejected while items or a session exist. Reload validates before apply, affects new sessions only, and does not recreate listeners/managers.
- `required-items` is the canonical ordered slot list and supports either serialized Bukkit `item` or `nexo-id`. The legacy `required-items-nexo` map remains readable and is rewritten canonically on the next save.

## Durable refund protocol

- `pending-refunds.yml` is the only mailbox. Each entry contains owner UUID, unique refund UUID, source pedestal/center, state, and serialized `ItemStack`.
- Persist `CLAIMED` before `Inventory#addItem`; preserve the exact returned leftovers; call `Player#saveData`; reconcile the temporary `valtars:refund_id` PDC tag; then acknowledge the mailbox.
- Zero accepted leaves the same entry pending. Partial acceptance removes the old claim and persists leftovers under a fresh UUID. Full acknowledgement removes the entry. Confirmed and orphaned tags are stripped on the main thread so ordinary items can stack again.
- Deterministic tests cover restart from `PENDING`, restart from `CLAIMED` before ack, partial restart, full-inventory leftovers, non-reusable acknowledged IDs, and PDC cleanup.
- Placement tests cover the idle boundary, the no-nearby-contributor branch, exact missing-unit transfer, partial top-up, the natural stack limit, and repeated IDs assigned to separate pedestal slots; invalid expiry configuration cannot replace the previous snapshot.
- Sound resolution accepts Bukkit constant names without replacing compound key underscores; this prevents the demonstrated `BLOCK_END_PORTAL_FRAME_FILL` warning.

### Crash boundary

Bukkit player data and `pending-refunds.yml` cannot be atomically committed together. Claim-before-inventory plus a persisted PDC tag makes recovery deterministic under normal `Player#saveData` and filesystem behavior and leaves no known reproducible loss/dupe route. It is not an absolute exactly-once guarantee if the OS, filesystem, or server reports a successful save that is later lost. A crash after mailbox acknowledgement but before tag cleanup may temporarily leave an orphan tag; the next delivery/login cleanup removes it without re-opening the acknowledged refund.

## Migration and verification

- `LegacyDataMigrator` copies missing files from `plugins/VtAlters` into `plugins/vAltars`, backs sources up under `backups/legacy-v1`, and never overwrites modern files. Repeated runs are covered by an idempotence test.
- Unit tests use JUnit and MockBukkit. The build is verified from a clean temporary copy when OneDrive or sandbox file locking interferes with dependency JAR closure.
- Real runtime smoke uses verified read-only Paper/MythicMobs sources copied into a vAltars-owned disposable directory. Never execute or modify another task's server directory and never simulate missing provider/client artifacts.

## Last verified result

- Command: `.\gradlew.bat clean test build --no-daemon --max-workers=1 --console=plain`.
- Result: `BUILD SUCCESSFUL`; 26 tests, 0 failures, 0 errors, 0 skipped.
- Artifact: `build/libs/vAltars-1.1.0.jar`.
- Size: 123551 bytes.
- SHA-256: `E0456EBA6E7494EA761D2FA2A6FE4BC8087F41FC5E7DE86A4F33C9F6D178F3E0`.
- JAR audit: `plugin.yml` reports vAltars 1.1.0 and `com.valerinsmp.valtars.VAltarsPlugin`; Java class major version 65; production classes, language files, config, altars template, and MIT resource present; no test classes, nested private JARs, or legacy `com/vtalters` classes.
- Smoke status: PARTIAL PASS in `C:\Users\marti\.codex\visualizations\2026\08\09\019fe814-f247-77c0-8ae3-77d3830a5c0c\valtars-real-smoke-1.1.0-20260810`. Paper 1.21.11 (`5FFEF465EEEB5F2A3C23A24419D97C51AFD7DBB4923FF42DF9A3F58BBA1CCFBA`), MythicMobs 5.11.2 (`D49F5F801ADF1ED5E379840A3272DD8A7C3DFC399E4249521A4CF10DEC936523`) and this vAltars JAR enabled; legacy migration copied/backed up two files; valid reload applied; invalid radius reload was rejected while the previous snapshot remained active; disable completed and Paper exited 0. Nexo name/render and client-visible ItemDisplay/GUI/teleport remain BLOCKED until a real Nexo runtime and Minecraft client are available. Evidence log SHA-256: `18FF60357968ABA415D9AE1221E3E306470DEB04D35193F8BA36B9A9CADF15E6`.
