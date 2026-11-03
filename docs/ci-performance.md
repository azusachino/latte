# CI performance round

## Baseline and measurement

Baseline source: `5c0813dcf20809bd71a7046cec9c4d35045f8ca7`. Retrieved the latest 50 workflow runs and their jobs using `gh run list --repo azusachino/latte --limit 50` and `gh api repos/azusachino/latte/actions/runs/<id>/jobs?per_page=100`.

The sample has 182 jobs across CI and Release. Failures, cancellations and skipped jobs remain in the table but are excluded from successful timing summaries. A skipped job can have invalid timestamps; those are shown as n/a.

Queue is job creation to start. Upstream wait is workflow creation to job creation, which includes change-detection or dependency waits and is not runner contention. Preparation is job start to its first validation/work step. Work is that step through the last non-cleanup step; it includes any installation hidden inside a combined command, and publication in release jobs. Job total excludes queue and includes cleanup. Historical step boundaries and whole-second timestamps limit precision.

The 31 successful Android code jobs have medians: queue 2s, preparation 9s, work 271s, total 282s. These span different source revisions and are not a controlled benchmark.

Latest successful CI [run 41569219764](https://github.com/azusachino/latte/actions/runs/41569219764) spent 313s in its Android job: checks 161s, lint 76s and debug assembly 63s, with roughly 10s preparation. Main and feature-branch pushes plus PR events could duplicate the same validation. Only the mise tool cache existed (695,884,558 bytes); no Gradle build/dependency cache was configured.

## Changes and retained coverage

- Run CI for main pushes and PRs, removing duplicate feature-push validation. Give main runs unique concurrency identities so neither running nor pending commits are replaced; cancel superseded runs of the same PR.
- Skip PRs changing only explicitly listed documentation. App sources and test fixtures remain covered. Main pushes always run. Main protection returned 404 and branch rules were empty at inspection; the workflow documents that required checks need an always-running gate before enabling path skipping.
- Keep the existing `make check` status name and replace three serial jobs (change detection, Android validation, final status) with the actual validation job. No data dependency remains to justify those runner launches.
- Use the native `gradle/actions/setup-gradle` v5 cache, pinned to its commit, alongside mise's existing tool cache. Enable Gradle task caching and run the same `testDebugUnitTest`, `lintDebug` and `assembleDebug` tasks in one invocation. Run the unchanged Markdown check separately. Same-repository PRs can save their isolated cache; fork PRs are read-only.
- Retain all unit tests, Android lint and debug APK checks. The Release workflow is unchanged; no tag, release publication or signing operation is used to test this patch.

The [setup-gradle documentation](https://github.com/gradle/actions/blob/v5/docs/setup-gradle.md) explains Gradle User Home caching and why it must not overlap another Gradle cache. No setup-java Gradle cache or duplicate hand-written archive is added.

## Local verification

Baseline `make check lint validate` passed, including Markdown checks, unit tests, lint and debug/release assembly. Existing local build outputs were reused; this is not a cold-build measurement. The patched combined command passed twice with existing local outputs: 1.325s, then 0.553s (58.2% less elapsed time on the second invocation). Post-edit `make check lint validate` passed in 2.169s. These are warm local timings, not hosted-runner or cold-compilation results.

Actionlint and `git diff --check` passed for the new workflow. Hosted cold/warm measurements and second-pass assessment: pending. No speedup is claimed before those runs.

## Every baseline job

All durations are seconds. Work includes publication for Release jobs; upstream wait is separate from runner queue.

| Run | Job | Result | Upstream wait | Queue | Preparation | Work | Job total |
| --- | --- | --- | --- | --- | --- | --- | --- |
| [41569247167](https://github.com/azusachino/latte/actions/runs/41569247167) | Build & Publish Release APK | success | 1 | 2 | 13 | 434 | 449 |
| [41569219764](https://github.com/azusachino/latte/actions/runs/41569219764) | Detect changes | success | 0 | 2 | 2 | 0 | 4 |
| [41569219764](https://github.com/azusachino/latte/actions/runs/41569219764) | make check and Android build | success | 6 | 2 | 11 | 300 | 313 |
| [41569219764](https://github.com/azusachino/latte/actions/runs/41569219764) | Markdown formatting (rumdl) | skipped | 7 | 0 | n/a | n/a | n/a |
| [41569219764](https://github.com/azusachino/latte/actions/runs/41569219764) | make check | success | 321 | 2 | 1 | 0 | 4 |
| [41568637070](https://github.com/azusachino/latte/actions/runs/41568637070) | Detect changes | success | 1 | 1 | 5 | 0 | 6 |
| [41568637070](https://github.com/azusachino/latte/actions/runs/41568637070) | make check and Android build | success | 9 | 1 | 9 | 280 | 291 |
| [41568637070](https://github.com/azusachino/latte/actions/runs/41568637070) | Markdown formatting (rumdl) | skipped | 9 | 0 | n/a | n/a | 0 |
| [41568637070](https://github.com/azusachino/latte/actions/runs/41568637070) | make check | success | 301 | 3 | 1 | 2 | 5 |
| [41568630525](https://github.com/azusachino/latte/actions/runs/41568630525) | Detect changes | success | 2 | 2 | 3 | 1 | 5 |
| [41568630525](https://github.com/azusachino/latte/actions/runs/41568630525) | make check and Android build | success | 9 | 2 | 10 | 297 | 309 |
| [41568630525](https://github.com/azusachino/latte/actions/runs/41568630525) | Markdown formatting (rumdl) | skipped | 10 | 0 | n/a | n/a | n/a |
| [41568630525](https://github.com/azusachino/latte/actions/runs/41568630525) | make check | success | 320 | 2 | 1 | 0 | 6 |
| [41568299078](https://github.com/azusachino/latte/actions/runs/41568299078) | Detect changes | success | 1 | 2 | 3 | 1 | 6 |
| [41568299078](https://github.com/azusachino/latte/actions/runs/41568299078) | make check and Android build | failure | 9 | 2 | 9 | 0 | 11 |
| [41568299078](https://github.com/azusachino/latte/actions/runs/41568299078) | Markdown formatting (rumdl) | skipped | 10 | 0 | n/a | n/a | n/a |
| [41568299078](https://github.com/azusachino/latte/actions/runs/41568299078) | make check | failure | 23 | 1 | 1 | 0 | 2 |
| [41568291922](https://github.com/azusachino/latte/actions/runs/41568291922) | Detect changes | success | 1 | 2 | 2 | 1 | 4 |
| [41568291922](https://github.com/azusachino/latte/actions/runs/41568291922) | make check and Android build | failure | 8 | 1 | 8 | 0 | 10 |
| [41568291922](https://github.com/azusachino/latte/actions/runs/41568291922) | Markdown formatting (rumdl) | skipped | 8 | 0 | n/a | n/a | 0 |
| [41568291922](https://github.com/azusachino/latte/actions/runs/41568291922) | make check | failure | 19 | 2 | 1 | 0 | 3 |
| [41567661547](https://github.com/azusachino/latte/actions/runs/41567661547) | Detect changes | success | 1 | 2 | 3 | 1 | 6 |
| [41567661547](https://github.com/azusachino/latte/actions/runs/41567661547) | make check and Android build | success | 9 | 2 | 13 | 299 | 315 |
| [41567661547](https://github.com/azusachino/latte/actions/runs/41567661547) | Markdown formatting (rumdl) | skipped | 9 | 0 | n/a | n/a | 0 |
| [41567661547](https://github.com/azusachino/latte/actions/runs/41567661547) | make check | success | 326 | 7 | 1 | 0 | 4 |
| [41567657014](https://github.com/azusachino/latte/actions/runs/41567657014) | Detect changes | success | 0 | 2 | 3 | 1 | 7 |
| [41567657014](https://github.com/azusachino/latte/actions/runs/41567657014) | make check and Android build | success | 9 | 2 | 9 | 271 | 282 |
| [41567657014](https://github.com/azusachino/latte/actions/runs/41567657014) | Markdown formatting (rumdl) | skipped | 9 | 0 | n/a | n/a | 0 |
| [41567657014](https://github.com/azusachino/latte/actions/runs/41567657014) | make check | success | 293 | 7 | 0 | 0 | 2 |
| [41566488693](https://github.com/azusachino/latte/actions/runs/41566488693) | Detect changes | success | 90 | 2 | 5 | 1 | 8 |
| [41566488693](https://github.com/azusachino/latte/actions/runs/41566488693) | make check and Android build | success | 100 | 2 | 8 | 207 | 217 |
| [41566488693](https://github.com/azusachino/latte/actions/runs/41566488693) | Markdown formatting (rumdl) | skipped | 100 | 0 | n/a | n/a | 0 |
| [41566488693](https://github.com/azusachino/latte/actions/runs/41566488693) | make check | success | 319 | 11 | 1 | 1 | 2 |
| [41566482773](https://github.com/azusachino/latte/actions/runs/41566482773) | Detect changes | success | 24 | 9 | 2 | 1 | 4 |
| [41566482773](https://github.com/azusachino/latte/actions/runs/41566482773) | make check and Android build | success | 38 | 2 | 7 | 226 | 235 |
| [41566482773](https://github.com/azusachino/latte/actions/runs/41566482773) | Markdown formatting (rumdl) | skipped | 38 | 0 | n/a | n/a | 0 |
| [41566482773](https://github.com/azusachino/latte/actions/runs/41566482773) | make check | success | 275 | 2 | 0 | 1 | 2 |
| [41566211849](https://github.com/azusachino/latte/actions/runs/41566211849) | Detect changes | success | 0 | 2 | 3 | 1 | 7 |
| [41566211849](https://github.com/azusachino/latte/actions/runs/41566211849) | make check and Android build | cancelled | 9 | 2 | 8 | 228 | 237 |
| [41566211849](https://github.com/azusachino/latte/actions/runs/41566211849) | Markdown formatting (rumdl) | skipped | 9 | 0 | n/a | n/a | 0 |
| [41566211849](https://github.com/azusachino/latte/actions/runs/41566211849) | make check | failure | 249 | 2 | 1 | 0 | 4 |
| [41566206317](https://github.com/azusachino/latte/actions/runs/41566206317) | Detect changes | success | 1 | 2 | 2 | 1 | 5 |
| [41566206317](https://github.com/azusachino/latte/actions/runs/41566206317) | make check and Android build | cancelled | 8 | 11 | 9 | 154 | 165 |
| [41566206317](https://github.com/azusachino/latte/actions/runs/41566206317) | Markdown formatting (rumdl) | skipped | 8 | 0 | n/a | n/a | 0 |
| [41566206317](https://github.com/azusachino/latte/actions/runs/41566206317) | make check | failure | 184 | 2 | 1 | 0 | 3 |
| [41565657963](https://github.com/azusachino/latte/actions/runs/41565657963) | Detect changes | success | 1 | 1 | 3 | 1 | 6 |
| [41565657963](https://github.com/azusachino/latte/actions/runs/41565657963) | make check and Android build | success | 8 | 12 | 9 | 234 | 245 |
| [41565657963](https://github.com/azusachino/latte/actions/runs/41565657963) | Markdown formatting (rumdl) | skipped | 9 | 0 | n/a | n/a | n/a |
| [41565657963](https://github.com/azusachino/latte/actions/runs/41565657963) | make check | success | 265 | 2 | 1 | 0 | 3 |
| [41565649810](https://github.com/azusachino/latte/actions/runs/41565649810) | Detect changes | success | 1 | 2 | 3 | 1 | 7 |
| [41565649810](https://github.com/azusachino/latte/actions/runs/41565649810) | make check and Android build | success | 10 | 2 | 9 | 279 | 290 |
| [41565649810](https://github.com/azusachino/latte/actions/runs/41565649810) | Markdown formatting (rumdl) | skipped | 11 | 0 | n/a | n/a | n/a |
| [41565649810](https://github.com/azusachino/latte/actions/runs/41565649810) | make check | success | 302 | 2 | 0 | 0 | 2 |
| [41565030796](https://github.com/azusachino/latte/actions/runs/41565030796) | Detect changes | success | 1 | 1 | 3 | 0 | 5 |
| [41565030796](https://github.com/azusachino/latte/actions/runs/41565030796) | make check and Android build | success | 8 | 1 | 15 | 201 | 218 |
| [41565030796](https://github.com/azusachino/latte/actions/runs/41565030796) | Markdown formatting (rumdl) | skipped | 8 | 0 | n/a | n/a | n/a |
| [41565030796](https://github.com/azusachino/latte/actions/runs/41565030796) | make check | success | 228 | 6 | 1 | 0 | 4 |
| [41565025202](https://github.com/azusachino/latte/actions/runs/41565025202) | Detect changes | success | 0 | 2 | 2 | 1 | 5 |
| [41565025202](https://github.com/azusachino/latte/actions/runs/41565025202) | make check and Android build | success | 7 | 2 | 9 | 276 | 286 |
| [41565025202](https://github.com/azusachino/latte/actions/runs/41565025202) | Markdown formatting (rumdl) | skipped | 7 | 0 | n/a | n/a | 0 |
| [41565025202](https://github.com/azusachino/latte/actions/runs/41565025202) | make check | success | 295 | 2 | 1 | 0 | 3 |
| [41564071823](https://github.com/azusachino/latte/actions/runs/41564071823) | Detect changes | success | 80 | 2 | 2 | 0 | 4 |
| [41564071823](https://github.com/azusachino/latte/actions/runs/41564071823) | make check and Android build | success | 86 | 3 | 10 | 240 | 252 |
| [41564071823](https://github.com/azusachino/latte/actions/runs/41564071823) | Markdown formatting (rumdl) | skipped | 86 | 0 | n/a | n/a | 0 |
| [41564071823](https://github.com/azusachino/latte/actions/runs/41564071823) | make check | success | 341 | 2 | 1 | 0 | 3 |
| [41564066543](https://github.com/azusachino/latte/actions/runs/41564066543) | Detect changes | success | 94 | 2 | 2 | 0 | 4 |
| [41564066543](https://github.com/azusachino/latte/actions/runs/41564066543) | make check and Android build | success | 100 | 2 | 13 | 284 | 299 |
| [41564066543](https://github.com/azusachino/latte/actions/runs/41564066543) | Markdown formatting (rumdl) | skipped | 101 | 0 | n/a | n/a | n/a |
| [41564066543](https://github.com/azusachino/latte/actions/runs/41564066543) | make check | success | 401 | 2 | 1 | 0 | 3 |
| [41563748670](https://github.com/azusachino/latte/actions/runs/41563748670) | Detect changes | success | 0 | 2 | 4 | 0 | 7 |
| [41563748670](https://github.com/azusachino/latte/actions/runs/41563748670) | make check and Android build | cancelled | 9 | 3 | 9 | 228 | 240 |
| [41563748670](https://github.com/azusachino/latte/actions/runs/41563748670) | Markdown formatting (rumdl) | skipped | 9 | 0 | n/a | n/a | 0 |
| [41563748670](https://github.com/azusachino/latte/actions/runs/41563748670) | make check | failure | 252 | 2 | 1 | 0 | 3 |
| [41563743243](https://github.com/azusachino/latte/actions/runs/41563743243) | Detect changes | success | 14 | 1 | 6 | 0 | 8 |
| [41563743243](https://github.com/azusachino/latte/actions/runs/41563743243) | make check and Android build | cancelled | 23 | 2 | 10 | 228 | 240 |
| [41563743243](https://github.com/azusachino/latte/actions/runs/41563743243) | Markdown formatting (rumdl) | skipped | 23 | 0 | n/a | n/a | 0 |
| [41563743243](https://github.com/azusachino/latte/actions/runs/41563743243) | make check | failure | 265 | 2 | 1 | 0 | 5 |
| [41563302308](https://github.com/azusachino/latte/actions/runs/41563302308) | Detect changes | success | 1 | 2 | 4 | 1 | 6 |
| [41563302308](https://github.com/azusachino/latte/actions/runs/41563302308) | make check and Android build | success | 10 | 1 | 7 | 202 | 210 |
| [41563302308](https://github.com/azusachino/latte/actions/runs/41563302308) | Markdown formatting (rumdl) | skipped | 10 | 0 | n/a | n/a | 0 |
| [41563302308](https://github.com/azusachino/latte/actions/runs/41563302308) | make check | success | 222 | 4 | 1 | 0 | 3 |
| [41563295121](https://github.com/azusachino/latte/actions/runs/41563295121) | Detect changes | success | 0 | 2 | 3 | 0 | 5 |
| [41563295121](https://github.com/azusachino/latte/actions/runs/41563295121) | make check and Android build | cancelled | 7 | 3 | 9 | 232 | 243 |
| [41563295121](https://github.com/azusachino/latte/actions/runs/41563295121) | Markdown formatting (rumdl) | skipped | 8 | 0 | n/a | n/a | n/a |
| [41563295121](https://github.com/azusachino/latte/actions/runs/41563295121) | make check | failure | 254 | 1 | 1 | 0 | 3 |
| [41562313425](https://github.com/azusachino/latte/actions/runs/41562313425) | Detect changes | success | 1 | 1 | 10 | 1 | 13 |
| [41562313425](https://github.com/azusachino/latte/actions/runs/41562313425) | make check and Android build | success | 15 | 2 | 8 | 201 | 211 |
| [41562313425](https://github.com/azusachino/latte/actions/runs/41562313425) | Markdown formatting (rumdl) | skipped | 15 | 0 | n/a | n/a | 0 |
| [41562313425](https://github.com/azusachino/latte/actions/runs/41562313425) | make check | success | 228 | 2 | 1 | 0 | 4 |
| [41562307090](https://github.com/azusachino/latte/actions/runs/41562307090) | Detect changes | success | 1 | 2 | 2 | 1 | 5 |
| [41562307090](https://github.com/azusachino/latte/actions/runs/41562307090) | make check and Android build | success | 8 | 2 | 12 | 317 | 332 |
| [41562307090](https://github.com/azusachino/latte/actions/runs/41562307090) | Markdown formatting (rumdl) | skipped | 9 | 0 | n/a | n/a | n/a |
| [41562307090](https://github.com/azusachino/latte/actions/runs/41562307090) | make check | success | 342 | 2 | 0 | 1 | 3 |
| [41453886027](https://github.com/azusachino/latte/actions/runs/41453886027) | Build & Publish Release APK | success | 1 | 2 | 12 | 372 | 386 |
| [41453857245](https://github.com/azusachino/latte/actions/runs/41453857245) | Detect changes | success | 1 | 2 | 2 | 0 | 8 |
| [41453857245](https://github.com/azusachino/latte/actions/runs/41453857245) | make check and Android build | success | 12 | 12 | 8 | 216 | 226 |
| [41453857245](https://github.com/azusachino/latte/actions/runs/41453857245) | Markdown formatting (rumdl) | skipped | 12 | 0 | n/a | n/a | 0 |
| [41453857245](https://github.com/azusachino/latte/actions/runs/41453857245) | make check | success | 250 | 2 | 0 | 1 | 2 |
| [41453829638](https://github.com/azusachino/latte/actions/runs/41453829638) | Detect changes | success | 1 | 7 | 2 | 1 | 7 |
| [41453829638](https://github.com/azusachino/latte/actions/runs/41453829638) | make check and Android build | success | 16 | 2 | 13 | 293 | 309 |
| [41453829638](https://github.com/azusachino/latte/actions/runs/41453829638) | Markdown formatting (rumdl) | skipped | 16 | 0 | n/a | n/a | n/a |
| [41453829638](https://github.com/azusachino/latte/actions/runs/41453829638) | make check | success | 327 | 3 | 1 | 0 | 3 |
| [41316832156](https://github.com/azusachino/latte/actions/runs/41316832156) | Build & Publish Release APK | success | 0 | 3 | 12 | 383 | 397 |
| [41316823779](https://github.com/azusachino/latte/actions/runs/41316823779) | Detect changes | success | 0 | 2 | 2 | 1 | 4 |
| [41316823779](https://github.com/azusachino/latte/actions/runs/41316823779) | make check and Android build | success | 7 | 2 | 11 | 283 | 296 |
| [41316823779](https://github.com/azusachino/latte/actions/runs/41316823779) | Markdown formatting (rumdl) | skipped | 7 | 0 | n/a | n/a | n/a |
| [41316823779](https://github.com/azusachino/latte/actions/runs/41316823779) | make check | success | 305 | 2 | 1 | 1 | 4 |
| [41308966625](https://github.com/azusachino/latte/actions/runs/41308966625) | Build & Publish Release APK | success | 1 | 2 | 10 | 376 | 388 |
| [41308962531](https://github.com/azusachino/latte/actions/runs/41308962531) | Detect changes | success | 9 | 2 | 3 | 0 | 5 |
| [41308962531](https://github.com/azusachino/latte/actions/runs/41308962531) | make check and Android build | success | 16 | 2 | 13 | 273 | 288 |
| [41308962531](https://github.com/azusachino/latte/actions/runs/41308962531) | Markdown formatting (rumdl) | skipped | 17 | 0 | n/a | n/a | n/a |
| [41308962531](https://github.com/azusachino/latte/actions/runs/41308962531) | make check | success | 306 | 2 | 1 | 0 | 3 |
| [41308940574](https://github.com/azusachino/latte/actions/runs/41308940574) | Detect changes | success | 0 | 2 | 2 | 1 | 5 |
| [41308940574](https://github.com/azusachino/latte/actions/runs/41308940574) | make check and Android build | cancelled | 7 | 2 | 9 | 0 | 11 |
| [41308940574](https://github.com/azusachino/latte/actions/runs/41308940574) | Markdown formatting (rumdl) | skipped | 7 | 0 | n/a | n/a | 0 |
| [41308940574](https://github.com/azusachino/latte/actions/runs/41308940574) | make check | failure | 20 | 2 | 1 | 0 | 3 |
| [41308937810](https://github.com/azusachino/latte/actions/runs/41308937810) | Detect changes | success | 0 | 2 | 3 | 0 | 5 |
| [41308937810](https://github.com/azusachino/latte/actions/runs/41308937810) | make check and Android build | success | 7 | 2 | 9 | 253 | 264 |
| [41308937810](https://github.com/azusachino/latte/actions/runs/41308937810) | Markdown formatting (rumdl) | skipped | 8 | 0 | n/a | n/a | n/a |
| [41308937810](https://github.com/azusachino/latte/actions/runs/41308937810) | make check | success | 273 | 2 | 1 | 0 | 3 |
| [41306773312](https://github.com/azusachino/latte/actions/runs/41306773312) | Detect changes | success | 1 | 2 | 3 | 0 | 5 |
| [41306773312](https://github.com/azusachino/latte/actions/runs/41306773312) | make check and Android build | success | 9 | 1 | 9 | 294 | 305 |
| [41306773312](https://github.com/azusachino/latte/actions/runs/41306773312) | Markdown formatting (rumdl) | skipped | 9 | 0 | n/a | n/a | 0 |
| [41306773312](https://github.com/azusachino/latte/actions/runs/41306773312) | make check | success | 315 | 2 | 1 | 0 | 4 |
| [41305722838](https://github.com/azusachino/latte/actions/runs/41305722838) | Detect changes | success | 0 | 2 | 2 | 1 | 4 |
| [41305722838](https://github.com/azusachino/latte/actions/runs/41305722838) | make check and Android build | success | 6 | 2 | 8 | 221 | 231 |
| [41305722838](https://github.com/azusachino/latte/actions/runs/41305722838) | Markdown formatting (rumdl) | skipped | 7 | 0 | n/a | n/a | n/a |
| [41305722838](https://github.com/azusachino/latte/actions/runs/41305722838) | make check | success | 239 | 2 | 0 | 1 | 3 |
| [41305718363](https://github.com/azusachino/latte/actions/runs/41305718363) | Detect changes | success | 89 | 2 | 2 | 1 | 4 |
| [41305718363](https://github.com/azusachino/latte/actions/runs/41305718363) | make check and Android build | success | 96 | 1 | 9 | 271 | 282 |
| [41305718363](https://github.com/azusachino/latte/actions/runs/41305718363) | Markdown formatting (rumdl) | skipped | 96 | 0 | n/a | n/a | 0 |
| [41305718363](https://github.com/azusachino/latte/actions/runs/41305718363) | make check | success | 379 | 2 | 1 | 0 | 4 |
| [41305386312](https://github.com/azusachino/latte/actions/runs/41305386312) | Detect changes | success | 1 | 2 | 4 | 1 | 7 |
| [41305386312](https://github.com/azusachino/latte/actions/runs/41305386312) | make check and Android build | cancelled | 10 | 2 | 12 | 301 | 316 |
| [41305386312](https://github.com/azusachino/latte/actions/runs/41305386312) | Markdown formatting (rumdl) | skipped | 10 | 0 | n/a | n/a | 0 |
| [41305386312](https://github.com/azusachino/latte/actions/runs/41305386312) | make check | failure | 328 | 2 | 1 | 0 | 3 |
| [41302033066](https://github.com/azusachino/latte/actions/runs/41302033066) | Build & Publish Release APK | success | 0 | 3 | 14 | 353 | 370 |
| [41299293050](https://github.com/azusachino/latte/actions/runs/41299293050) | Detect changes | success | 73 | 2 | 2 | 0 | 4 |
| [41299293050](https://github.com/azusachino/latte/actions/runs/41299293050) | make check and Android build | success | 79 | 2 | 12 | 264 | 279 |
| [41299293050](https://github.com/azusachino/latte/actions/runs/41299293050) | Markdown formatting (rumdl) | skipped | 79 | 0 | n/a | n/a | 0 |
| [41299293050](https://github.com/azusachino/latte/actions/runs/41299293050) | make check | success | 360 | 2 | 0 | 1 | 2 |
| [41299257332](https://github.com/azusachino/latte/actions/runs/41299257332) | Detect changes | success | 1 | 1 | 2 | 1 | 5 |
| [41299257332](https://github.com/azusachino/latte/actions/runs/41299257332) | make check and Android build | cancelled | 7 | 2 | 8 | 79 | 89 |
| [41299257332](https://github.com/azusachino/latte/actions/runs/41299257332) | Markdown formatting (rumdl) | skipped | 7 | 0 | n/a | n/a | 0 |
| [41299257332](https://github.com/azusachino/latte/actions/runs/41299257332) | make check | failure | 98 | 2 | 1 | 0 | 3 |
| [41298075351](https://github.com/azusachino/latte/actions/runs/41298075351) | Detect changes | success | 32 | 1 | 3 | 0 | 5 |
| [41298075351](https://github.com/azusachino/latte/actions/runs/41298075351) | make check and Android build | success | 38 | 2 | 9 | 214 | 225 |
| [41298075351](https://github.com/azusachino/latte/actions/runs/41298075351) | Markdown formatting (rumdl) | skipped | 38 | 0 | n/a | n/a | 0 |
| [41298075351](https://github.com/azusachino/latte/actions/runs/41298075351) | make check | success | 265 | 2 | 2 | 0 | 4 |
| [41297955843](https://github.com/azusachino/latte/actions/runs/41297955843) | Detect changes | success | 21 | 3 | 3 | 0 | 6 |
| [41297955843](https://github.com/azusachino/latte/actions/runs/41297955843) | make check and Android build | cancelled | 30 | 2 | 9 | 79 | 90 |
| [41297955843](https://github.com/azusachino/latte/actions/runs/41297955843) | Markdown formatting (rumdl) | skipped | 31 | 0 | n/a | n/a | n/a |
| [41297955843](https://github.com/azusachino/latte/actions/runs/41297955843) | make check | failure | 122 | 2 | 1 | 0 | 3 |
| [41297739717](https://github.com/azusachino/latte/actions/runs/41297739717) | Detect changes | success | 1 | 1 | 3 | 1 | 6 |
| [41297739717](https://github.com/azusachino/latte/actions/runs/41297739717) | make check and Android build | cancelled | 8 | 2 | 9 | 160 | 172 |
| [41297739717](https://github.com/azusachino/latte/actions/runs/41297739717) | Markdown formatting (rumdl) | skipped | 9 | 0 | n/a | n/a | n/a |
| [41297739717](https://github.com/azusachino/latte/actions/runs/41297739717) | make check | failure | 182 | 2 | 1 | 0 | 3 |
| [41294975347](https://github.com/azusachino/latte/actions/runs/41294975347) | Detect changes | success | 1 | 2 | 3 | 1 | 6 |
| [41294975347](https://github.com/azusachino/latte/actions/runs/41294975347) | make check and Android build | success | 9 | 2 | 13 | 202 | 217 |
| [41294975347](https://github.com/azusachino/latte/actions/runs/41294975347) | Markdown formatting (rumdl) | skipped | 9 | 0 | n/a | n/a | 0 |
| [41294975347](https://github.com/azusachino/latte/actions/runs/41294975347) | make check | success | 228 | 2 | 0 | 1 | 2 |
| [41293030808](https://github.com/azusachino/latte/actions/runs/41293030808) | Detect changes | success | 71 | 2 | 3 | 1 | 6 |
| [41293030808](https://github.com/azusachino/latte/actions/runs/41293030808) | make check and Android build | success | 79 | 2 | 10 | 281 | 293 |
| [41293030808](https://github.com/azusachino/latte/actions/runs/41293030808) | Markdown formatting (rumdl) | skipped | 79 | 0 | n/a | n/a | 0 |
| [41293030808](https://github.com/azusachino/latte/actions/runs/41293030808) | make check | success | 374 | 2 | 1 | 0 | 4 |
| [41292805278](https://github.com/azusachino/latte/actions/runs/41292805278) | Detect changes | success | 0 | 2 | 7 | 1 | 10 |
| [41292805278](https://github.com/azusachino/latte/actions/runs/41292805278) | make check and Android build | cancelled | 12 | 2 | 11 | 227 | 240 |
| [41292805278](https://github.com/azusachino/latte/actions/runs/41292805278) | Markdown formatting (rumdl) | skipped | 13 | 0 | n/a | n/a | n/a |
| [41292805278](https://github.com/azusachino/latte/actions/runs/41292805278) | make check | failure | 254 | 3 | 0 | 1 | 3 |
| [41291344721](https://github.com/azusachino/latte/actions/runs/41291344721) | Detect changes | success | 1 | 2 | 3 | 1 | 6 |
| [41291344721](https://github.com/azusachino/latte/actions/runs/41291344721) | make check and Android build | success | 9 | 2 | 8 | 273 | 282 |
| [41291344721](https://github.com/azusachino/latte/actions/runs/41291344721) | Markdown formatting (rumdl) | skipped | 10 | 0 | n/a | n/a | n/a |
| [41291344721](https://github.com/azusachino/latte/actions/runs/41291344721) | make check | success | 293 | 2 | 1 | 0 | 3 |
| [41288706000](https://github.com/azusachino/latte/actions/runs/41288706000) | Detect changes | success | 1 | 1 | n/a | 0 | 5 |
| [41288706000](https://github.com/azusachino/latte/actions/runs/41288706000) | make check and Android build | success | 7 | 1 | n/a | 282 | 292 |
| [41288706000](https://github.com/azusachino/latte/actions/runs/41288706000) | Markdown formatting (rumdl) | skipped | 7 | 0 | n/a | n/a | 0 |
| [41288706000](https://github.com/azusachino/latte/actions/runs/41288706000) | make check | success | 300 | 2 | n/a | 1 | 2 |
| [41286902645](https://github.com/azusachino/latte/actions/runs/41286902645) | Detect changes | success | 0 | 2 | 3 | 1 | 6 |
| [41286902645](https://github.com/azusachino/latte/actions/runs/41286902645) | make check and Android build | success | 8 | 2 | 12 | 260 | 3875 |
| [41286902645](https://github.com/azusachino/latte/actions/runs/41286902645) | Markdown formatting (rumdl) | skipped | 8 | 0 | n/a | n/a | 0 |
| [41286902645](https://github.com/azusachino/latte/actions/runs/41286902645) | make check | success | 3885 | 1 | n/a | 0 | 3 |
| [41157130412](https://github.com/azusachino/latte/actions/runs/41157130412) | Build & Publish Release APK | success | 1 | 2 | 12 | 381 | 395 |
