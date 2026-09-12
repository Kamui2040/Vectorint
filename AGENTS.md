# Vectorint Repository Instructions

## Repository purpose

Vectorint is an Android-only, local-first budgeting companion. This repository has
an independent Git history and implementation.

Treat every tracked file as suitable for public release.

## Clean implementation boundary

- Implement behavior from written product requirements and public platform APIs.
- Do not copy application source, resources, assets, translations, migrations, or
  generated output from prototypes or third-party applications.
- Record the origin and licence of every third-party dependency or asset before it
  enters the distributable application.
- Keep dependency and asset licences compatible with the repository licence and a
  future F-Droid-compatible distribution path.

## Product and accounting invariants

- One pooled **Current funds** value is the cash baseline; accounts, bank sync, and
  foreign-exchange aggregation are outside the core.
- **Available now** is the primary budgeting result for one explicit calendar
  month.
- Income and expense use one direction-neutral activity and recurring-item model.
- A confirmed activity after the Current funds capture instant changes confirmed
  funds exactly once. Confirmed activity at or before that baseline is never
  replayed.
- A planned recurring occurrence is one economic event. Confirmation replaces its
  planned state, removes its reservation, and must not create a second event.
- Planned current-month expenses are always reserved. Expected income affects
  Available now only through an explicit user-controlled opt-in.
- Missing, duplicated, mixed-currency, malformed, or overflowing accounting input
  fails closed instead of producing a reassuring number.
- Negative results are valid. Money uses integer minor units; do not use floating
  point for accounting.
- Booking time and budget-month assignment remain separate.
- Every recurring definition has one First occurrence and one timing plan:
  specific date, date range, or anytime within the month. The first occurrence
  anchors all later repeats. A range books on its final date; an unspecified
  month books on that month's final date.
- Recurrence is direction-neutral and supports every N days, weeks, months, or
  years. `Ends on` is an optional inclusive cutoff.
- Automatic confirmation is the default for both directions and occurs only when
  each occurrence's own booking date is reached. Manual confirmation is an
  explicit per-item opt-in and records the actual confirmation instant. Before
  confirmation, planned income remains governed by the expected-income setting.
- Budget-month assignment is explicit and may use the occurrence month or the
  following month. Never infer a late-month rollover from the date alone.
- `Remind me on` is notification metadata only and never changes economic activity.
- Reminders work for both income and expense. Each activity and recurring item may
  have one category, and generated occurrences inherit the recurring category.
  Categories drive presentation and expense grouping only; they never change
  calculations. Tags remain separate visual metadata for user organization, such
  as the payment source, and never change calculations or chart grouping.
- User-visible number, currency, and date formatting follows Android's current OS
  regional settings, independently from app language. Keep formatted strings out
  of the domain model.
- Resolve the OS format locale at each input or presentation operation; do not
  retain locale-bound formatters after settings may change.
- Money input is interpreted only with the active format locale and the selected
  currency's exact fraction scale. Reject grouping, foreign separators, excess
  precision, unsupported currency metadata, and overflow rather than guessing.
  Convert accepted input directly to integer minor units without floating point.

## Architecture

- Keep the project Android-only. Do not add Kotlin Multiplatform, iOS, desktop, or
  other platform scaffolding without an explicit product decision.
- Keep the accounting core as deterministic plain Kotlin with pure host tests.
- Add persistence and UI around the tested domain rather than embedding accounting
  rules in composables, Android services, or database queries.
- Home may show an Available now amount only from a successful domain calculation.
  Represent loading, missing Current funds, unsafe data, and load failure as
  distinct screen states, and reload the complete snapshot when retrying.
- Keep both home-screen widget choices: the detailed current-month summary and a
  compact quick-add option. The compact widget shows only the Available now label
  and calculated value, or a safe placeholder, and tapping it opens one-off
  activity creation. Both widgets must fail closed.
- Keep user-facing text in Android resources and preserve headings, readable
  wrapping, and minimum touch targets in Compose semantics and layout.
- Write copy around the user's task in plain language. German uses informal
  `du`/`dein`, never formal address. Keep errors, destructive consequences, and
  data-recovery warnings visible; put optional concept explanations behind a
  reusable 48 dp info control with a meaningful accessibility label and a
  dismissible text dialog.
- Keep English as the unqualified fallback and maintain complete key and format-
  placeholder parity in every localized string catalogue. App language selection
  must not change the OS-regional formatting rules for dates, numbers, or money.
- Home, History, and Overview are the only bottom-navigation destinations. Keep
  the bottom navigation visible on every in-app screen, and map each creation,
  editing, or recurring-item flow to its owning top-level destination.
- Keep one permanent top app row across every view, with the tappable Vectorint
  mascot opening About, the title centered, and Settings available from the
  top-right action. Settings is not a bottom-navigation destination.
- Use the K2040 creator logo only in the About-card header. Keep the Vectorint
  Raven as the launcher icon and permanent top-row mascot.
- Overview groups the selected month's expenses by their single assigned category
  and groups unassigned expenses under `Other`. It must not change Available now
  or count one expense in multiple chart slices.
- Ship a curated set of common categories plus user-created categories. Category
  icons must come from a coherent app-style vector icon library rather than emoji,
  remain understandable without color alone, and have recorded compatible
  provenance and licensing.
- Give each top-level destination an accessibility pane title. Mark changing
  status and error text as a polite live region so assistive technology announces
  results without interrupting the user.
- Keep Room DAOs internal. Application-facing database access must use suspending
  repository operations that move blocking work to an injected I/O dispatcher.
- Keep multi-step persistence transitions atomic. Confirmation must load and update
  one activity within one database transaction.
- Construct the database and repository once at application scope.
- Prefer the smallest dependency set that supports the current implemented scope.

## Persistence integrity

- Treat the exported Room schema as a public data contract and keep every schema
  version tracked.
- Preserve `Instant` values as epoch seconds plus nanoseconds; do not reduce the
  Current funds baseline or booking time to millisecond precision.
- Keep one Current funds row and one activity row per economic event. A recurring
  item ID plus occurrence key must remain unique across activity rows.
- Do not use generic Room upsert for activity rows. Inside one transaction, update
  only a known activity ID; otherwise insert with conflict-abort so an alternate
  recurring-occurrence collision cannot become a silent no-op.
- Persist confirmation as an update of the same activity identity. Never insert a
  second row for the confirmed form of a planned occurrence.
- Apply Activity detail edits to the current stored row in one transaction. Change
  only explicitly editable fields, preserve identity, source, state, timing,
  category, and tags unless that field was explicitly edited, and never recreate
  a missing row from a stale screen.
- Deleting a recurring definition must preserve activity already recorded from it.
- Map database records through validated domain constructors. Unknown enums,
  partial timestamp fields, malformed timing shapes, and invalid money fail closed.
- After schema version 1, every schema change requires an explicit migration and
  migration test. Do not use destructive migration fallbacks for user data.

## Settings integrity

- Keep one top-level Preferences DataStore instance for the settings file and
  construct its repository once at application scope.
- Expose immutable settings through a Flow. UI code must not read or write
  DataStore directly.
- A missing expected-income preference means `false`. Never include expected
  income because a setting is absent, unreadable, or malformed.
- Keep preference writes atomic and preserve unrelated keys. Do not hide storage
  or corruption failures by emitting a permissive fallback.
- Provide app-language selection inside Settings with device default, English, and
  German choices. App language changes interface text only; current OS regional
  settings continue to control dates, numbers, currency, and input parsing.

## Backup and restore integrity

- Manual backups use a versioned, size-bounded UTF-8 JSON contract selected by
  the user through Android's document picker. Keep platform cloud backup and
  device transfer disabled unless the product policy explicitly changes.
- Automatic backups reuse the manual backup contract and write only through a
  persistable Android Storage Access Framework tree selected by the user. Keep
  the destination grant, trigger choices, and last-run state in a separate
  device-local settings store; never import or export them as portable data.
- Supported automatic triggers are successful portable-data changes, app start,
  app background, and an inexact daily or weekly schedule. Never describe app
  background as guaranteed app closure: force-stop, crashes, and abrupt process
  termination can prevent lifecycle work.
- Verify each automatic backup by exact bounded readback before reporting
  success. Only after that verification may retention delete older files whose
  names exactly match Vectorint's automatic-backup pattern; retain the newest ten
  and never delete unrelated documents.
- Missing or temporarily unavailable background-work infrastructure must not
  crash application startup, including host-side test startup; a later app start
  or settings change may retry synchronization.
- Back up user-authored budget data, including custom categories and category
  assignments, plus calculation settings. Do not back up Android permissions,
  notification delivery acknowledgements, navigation state, caches, or other
  device-local runtime state.
- Treat every restore file as untrusted. Reject unknown versions or fields,
  malformed types, duplicate identities, mixed currency, incomplete timestamps,
  invalid lifecycle/reminder relationships, unsafe sizes, and trailing content
  before the first write.
- When a test derives an older backup from the current encoder, use the contract's
  current version constant and remove every field introduced after the claimed
  legacy version. A relabeled current payload is not a valid compatibility fixture.
- Replace the complete Room dataset in one transaction. Coordinate Room and
  DataStore with compensating restore plus exact readback; never report success
  after partial replacement or when the prior state cannot be verified.
- Preserve exact timestamp precision and historical Activity whose recurring
  definition was deleted. Reconcile local reminder delivery after a successful
  restore on the receiving device.

## Reminder delivery integrity

- Deliver recurring occurrence, reminder-date, and end notifications with one
  direction-neutral local planner. `Remind me on` remains notification metadata;
  `Ends on` remains the optional inclusive economic cutoff.
- Schedule the next reevaluation with an inexact local alarm around 09:00 in the
  device's current time zone. Do not request exact-alarm access for reminder
  convenience.
- Identify each delivery by reminder kind, recurring-item identity, event date,
  and lead time. Acknowledge it only after Android confirms that exact
  notification is active; permission or delivery failure must remain eligible for
  a later catch-up while the event is still relevant.
- Replan after recurring-item changes and app resume, and after boot, wall-clock,
  time-zone, or package-replacement events. Cancel notifications and retire
  acknowledgements that no longer match an active reminder configuration.
- Keep reminder content private on the lock screen. The public notification must
  omit item names, amounts, dates, direction, and other financial detail.

## Privacy and public safety

- Core budgeting is offline-capable and must not require accounts, telemetry,
  analytics, advertising, tracking, automatic crash upload, or proprietary cloud
  services.
- Never commit credentials, signing or recovery material, authentic financial or
  personal data, raw device identifiers, machine-local paths, private links or
  IDs, internal assistant instructions, or maintainer-only diagnostics.
- Use synthetic fixtures and examples.

## Build and validation

- Use the checked-in Gradle wrapper with JDK 21.
- Keep release output independent of repository metadata. Disable AGP-generated
  VCS metadata for release builds and require byte-identical unsigned APKs from
  the same tracked source both with and without a `.git` directory.
- F-Droid builds Vectorint from the public release source and signs it with its
  own repository-specific key. Keep Vectorint's permanent developer signature
  for GitHub and other compatible storefronts; do not configure F-Droid
  `Binaries`, per-build `binary`, or developer-binary signature copying unless a
  later explicit release-policy decision changes this model.
- When testing proposed `fdroiddata` metadata locally, commit the metadata in the
  validation checkout before running Git-state-dependent checks such as
  `fdroid build` or `fdroid checkupdates`, and require its Git-derived source
  timestamp to be non-empty. An uncommitted metadata file can give Gradle an
  empty `SOURCE_DATE_EPOCH`, produce a misleading cache-journal failure, or make
  `checkupdates` reject an otherwise valid candidate.
- During an official `fdroid build`, the source scanner intentionally removes
  `gradle/wrapper/gradle-wrapper.jar`, and post-build cleanup removes `gradlew`
  and `gradlew.bat`. Treat only that exact deletion set as expected buildserver
  mutation; any other tracked-source change fails the release gate.
- Treat F-Droid-signed and developer-signed installs as separate update channels.
  Do not claim that Android can update in place between them without a verified
  platform-supported signing migration.
- Keep the application module's main manifest present; Android unit-test task
  graphs validate it before pure Kotlin tests run.
- Preserve the repository line-ending rules, including CRLF for the generated
  Windows Gradle wrapper and LF for source and the POSIX wrapper.
- Keep formatter and Android lint rules aligned for `@Composable` function names.
- Keep core-library desugaring enabled while domain code uses `java.time` below
  API 26, and run Android lint after changing either the API floor or time model.
- Run host-side Android tests on an API level explicitly supported by the pinned
  Robolectric version; do not assume support merely because the app compiles
  against a newer SDK.
- If Android's incremental resource merge reports `no data file for changedFile`
  after a resource is replaced or removed, preserve the diagnostic and rerun the
  unchanged source through a full clean build before changing the resource.
- Keep device-QA helpers fail-closed: propagate every command and assertion
  failure, isolate the exact hierarchy before parsing `uiautomator` output, use
  locale-neutral semantic assertions, and capability-check device shell commands
  before relying on them.
- Immediately before coordinate-based UI input, verify that the intended package
  is foreground and that the current hierarchy contains the target control.
- Re-read the device hierarchy after any interaction that can reflow Compose
  content. Derive controls from the current bounds and assert the visible meaning;
  do not assume that status and date use separate semantic nodes.
- Before committing, run `git diff --check`, `ktlintCheck`, pure unit tests, the
  Android unit-test task, `assembleDebug`, and `lintDebug` when applicable.
- Run `ktlintFormat` and `ktlintCheck` in separate Gradle invocations. Their task
  graphs do not guarantee that a combined invocation checks the formatted files.
- Review modified, staged, and untracked content for privacy, licence, provenance,
  and accidental generated artifacts.
- Keep licence files as self-contained tracked text; never substitute a local or
  machine-dependent reference, and validate the complete staged contents.
- When auditing packaged permissions, distinguish app-requested capabilities from
  exact app-local signature permissions generated by AndroidX. Verify the source
  and merged manifests, confirm signature protection, and reject unexpected
  external permissions before classifying the gate.
- Keep GitHub Actions absent unless a maintainer explicitly authorizes them.
- Do not commit build output, APK/AAB files, local SDK configuration, signing
  material, or private validation logs.

## Publication boundary

Repository visibility changes, signing, releases, store or F-Droid submissions,
deployment, and public announcements require separate maintainer approval.
