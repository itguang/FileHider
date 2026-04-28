# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

IntelliJ IDEA plugin (`local.filehider`, "File Hider") that hides configured file/directory names from the Project Tree without touching the filesystem, indexing, VCS, search, or builds. Targets IntelliJ Platform 2024.2 (`sinceBuild=242`), Java 21.

## Commands

Gradle wrapper drives everything via the `org.jetbrains.intellij.platform` plugin (v2.14.0).

- Build plugin zip: `./gradlew buildPlugin` (output in `build/distributions/`)
- Run sandbox IDE with the plugin loaded: `./gradlew runIde`
- Run tests: `./gradlew test`
- Run a single test: `./gradlew test --tests "local.filehider.settings.FileHiderSettingsTest.normalizeRulesTrimsAndDeduplicatesWithinGroup"`
- Verify against the target IDE: `./gradlew verifyPlugin`

`buildSearchableOptions` is intentionally disabled in `build.gradle.kts` — do not re-enable without reason; it slows the build by booting a headless IDE.

## Architecture

The plugin has three moving parts wired together in `src/main/resources/META-INF/plugin.xml`:

1. **`FileHiderSettings` (application service, persisted to `fileHider.xml`)** — single source of truth for rules. State has two lists (`defaultRules`, `userRules`) plus an `enabled` flag. Every mutation goes through `replaceState`, which normalizes (trim, dedup by `name+type`, drop invalid names via `RuleValidator`), rebuilds an immutable `RuleSnapshot` (two `Set<String>`s — one for files, one for directories, derived from `RuleType.FILE/DIR/BOTH`), and broadcasts on the application message bus topic `FileHiderSettingsChanged`. Read paths use `getSnapshot()` — never iterate the rule lists on the hot path.

2. **`FileHiderTreeStructureProvider` (extension `treeStructureProvider`, `order="last"`)** — the only place filtering happens. For each child node it checks the snapshot's file/dir set by exact name match. Several short-circuits are load-bearing:
   - Skip when the per-project `ShowHiddenFilesAction` toggle is on (key stored as `Project` user data).
   - Skip when the active pane is the "Project Files" pane (id `ProjectFilesPane`) — looked up reflectively because the `ViewSettings` accessor varies across platform versions (`getPaneId` / `getViewPaneId` / `getId`).
   - `isWhitelistedNode` never hides project/module nodes, library element nodes, files outside content roots, files in libraries, or content roots themselves. This is what keeps the Project Tree from collapsing on itself when a user adds an aggressive rule.

3. **`ProjectViewRefreshListener` (application listener on the topic above)** — refreshes every open project's `ProjectView` on the EDT after settings change so the new snapshot takes effect immediately.

`FileHiderConfigurable` (Settings → Tools → File Hider) is the only UI; it edits a copy of state and calls `FileHiderSettings.update(...)`. Import/Export uses Gson against `FileHiderRule[]` JSON.

### Design constraints to preserve

- Rules are **exact, case-sensitive name matches** — no globs/paths. `RuleValidator.isValidName` rejects anything containing `/ \ * ?`. Don't add glob support without revisiting the snapshot data structure.
- The provider must stay **read-only and side-effect-free** — it only filters the children list; it must never mutate the VFS, trigger indexing, or block the EDT.
- Hidden files must remain fully functional for indexing, VCS, Find in Files, builds, and already-open editor tabs. If you add a feature, verify it doesn't change visibility outside the Project Tree.
- Settings are application-level (global across projects); the show-hidden override is project-level (`Project` user data, not persisted).
