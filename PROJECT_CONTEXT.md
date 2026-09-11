# Vectorint Project Context

## Status

Vectorint is a public Android application with an independent Git history. Version
0.0.8 is its first production release. This repository begins with a clean source
snapshot and does not include the earlier private development history. No
prototype or third-party application source, assets, translations, or migrations
were imported.

The initial foundation is deliberately small:

- one Android application module using Kotlin and Jetpack Compose;
- a plain-Kotlin accounting domain with pure unit tests;
- a version-5 Room persistence layer with exported schemas and host-side database
  tests;
- a suspending application repository that keeps blocking database work off the
  Android main thread;
- a Preferences DataStore repository for the expected-income choice, theme mode,
  and color palette;
- OS-regional date and money presentation plus strict locale-aware amount input;
- complete plain-language English and informal-`du` German UI resources with
  automatic Android app-language discovery, while money and date formatting
  continue to follow the OS region;
- a data-backed Compose Home screen with explicit loading, setup, ready, unsafe,
  and retryable failure states;
- a local home-screen widget for the current month's Available now result, with
  fail-closed setup, unsafe-data, and load-failure states;
- Current funds setup and editing that records a new exact-time cash baseline;
- named one-off income and expense creation for confirmed-now or
  planned-current-month activity;
- a permanent app header with the tappable raven, centered Vectorint title, and
  Settings action, plus persistent Home, History, and Overview bottom navigation
  on every in-app screen; creation and editing stay in focused secondary flows
  with assistive-technology announcements for screen changes and changing status;
- reusable accessible info controls that keep optional explanations out of the
  main flow while leaving errors, destructive consequences, and recovery
  warnings visible;
- a newest-first History view that displays each entry name, with name, amount,
  and direction editing, guarded deletion, planned-to-confirmed transitions,
  and optional tag metadata;
- a recurring-item list and editor for income or expenses with a specific date,
  date range, or anytime-within-month plan, every-N day/week/month/year
  intervals, optional manual confirmation, optional end and reminder dates,
  explicit budget-month assignment, and local reminder delivery;
- reusable add/remove tag controls for one-off and recurring editors, with tags
  visible in their corresponding lists;
- optional categories on one-off activity, recurring definitions, and generated
  occurrences, with predefined common choices, user-created categories, and a
  consistent rounded icon library instead of emoji;
- a browsable monthly Overview with an exact category expense total, pie chart,
  and accessible icon, amount, and share legend; uncategorized expenses appear
  once under Other;
- title-only Settings cards for system/light/dark appearance, Orbit/Nova/Nebula
  palettes, in-app device/English/German language selection, the expected-income
  calculation choice, data and backup, and About;
- a branded About card opened from the raven or Settings, with the K2040 creator
  logo, app version, short purpose, changelog, licences, sources, and the privacy
  summary at the bottom;
- a user-selected local JSON backup and fail-closed whole-data restore flow;
- idempotent current-month occurrence generation that stores one planned Activity
  row per economic occurrence and preserves an existing recorded occurrence;
- browsable past and future month summaries that preview recurring flows without
  creating or confirming Activity;
- the selected full-colour Vectorint Raven artwork used by the adaptive launcher
  icon and permanent app header, with the original source image preserved
  unchanged and licensed separately under CC BY 4.0;
- no network, account, analytics, advertising, or cloud integration;
- no iOS, desktop, web, or Kotlin Multiplatform targets;
- no GitHub Actions.

## Product direction

Vectorint answers how much the user can safely spend now from one pooled Current
funds baseline. Budgeting uses explicit calendar months. Income and expense share
the same activity and recurring-item concepts, with direction supplying the sign.

The initial calculation treats Current funds as a captured balance. Confirmed
activity after that capture changes the balance once; earlier confirmed activity is
already represented by the baseline and is not replayed. Planned current-month
expenses are always reserved, while expected income is an explicit opt-in.
Confirmation changes the state of the same occurrence so the planned reservation
and actual cash movement never overlap.

Recurring items start with a specific-date monthly default anchored by the First
occurrence. A date range uses its final day as the automatic booking date, while
an anytime-within-month occurrence uses the month's final day. The same model
supports every N days, weeks, months, or years for both income and expense. `Ends
on` is an optional inclusive economic cutoff, while `Remind me on` is
notification-only. Each occurrence explicitly counts toward its own month or the
following month. Reminders are direction-neutral. Categories group entries for
presentation and future summaries, while tags remain calculation-neutral
organizational metadata.

Each occurrence remains planned until its own effective booking date. It then
confirms automatically unless that recurring item explicitly requires manual
confirmation. Manual confirmation records the actual confirmation time. Before
confirmation, income contributes to Available now only through the saved
expected-income opt-in; after confirmation it follows the normal confirmed-cash
rules.

Money stays separated by currency and uses integer minor units. Unsafe or
ambiguous input suppresses the Available now result. Formatting is an Android UI
boundary driven by OS regional settings rather than app language.

The local database stores one Current funds baseline, activities, recurring items,
reusable tag relationships, and user-created category definitions. Recurring occurrence identity is unique in the
schema, confirmation updates the same row, and persisted timestamps retain
nanosecond precision. A single application-scoped repository provides background
access and keeps confirmation atomic. Room schema version 4 adds nullable category
assignments and custom categories on top of the version-3 occurrence-timing model.
Version 5 adds persisted activity names and recovers names for existing recurring
occurrences where their definition is still present. Existing unnamed one-off
records remain readable and use a localized History fallback.

Settings use a title-only card menu reached from the permanent top-right controls
action. They contain one calculation choice—whether expected income contributes
to Available now—device, light, or dark appearance using the Orbit, Nova, or
Nebula palette, device/English/German app-language selection, data and backup,
and About. Expected income defaults to off; appearance follows the device and
defaults to Orbit. The app language uses Android's per-app locale support and
never replaces OS-regional money, number, or date formatting. Calculation and
appearance settings are exposed as an immutable Flow and saved atomically.
Storage failures remain visible rather than silently changing calculation or
appearance.

Manual backup exports Current funds, Activity, recurring items, categories, tags, and saved
settings to a versioned, bounded JSON file chosen by the user. Backup format
version 6 adds persisted activity names on top of version 5 custom categories and
category assignments. It continues to import version-1 through version-5 files
through deterministic conversions; an older backup
changes its saved calculation choice without replacing appearance information it
did not contain.
The file is readable and never uploaded by Vectorint. Restore validates the whole
file before mutation, replaces Room data in one transaction, compensates across
Room and DataStore on failure, and verifies the final state. Android permissions,
reminder-delivery acknowledgements, and other device-local runtime state are not
portable data; reminders are reconciled from the restored definitions instead.
Platform cloud backup and device transfer remain disabled.

Presentation adapters resolve Android's current format locale for each operation,
independently of app language. Amount input accepts localized digits and the
locale's decimal separator, converts directly to the selected currency's integer
minor units, and rejects grouping, foreign separators, excess precision,
unsupported currency metadata, and overflow. Formatted text remains outside the
domain and persistence models.

Home loads one budget snapshot and the current calculation setting, delegates all
accounting to the domain calculator, and formats only a successful result. It
presents Available now as the primary value with Current funds, reserved expenses,
and expected-income inclusion underneath. Missing Current funds, unsafe data, and
read failures never display a calculated amount.

The Home month label opens lightweight previous/next navigation. The current month
alone presents Available now. Past selections are month summaries and future
selections are forecasts of known assigned income, expenses, and net flow. These
views preview recurring definitions in memory without persisting occurrences or
triggering automatic confirmation, and tapping Home returns to the current month.

Overview has its own month browser and explains the selected month's expenses by
their single category. Its exact integer total and category amounts come from the
same stored and previewed occurrences used for monthly budgeting, while drawing
proportions remain presentation-only. A complete text legend with category icons,
amounts, and shares keeps the result understandable without color or chart
geometry. Overview never changes Available now or accounting state beyond the
normal current-month occurrence refresh.

Income and expenses use shared accounting and editor structures while their
direction remains visually distinct: income uses the shared green semantic color
and expenses use red in lists, editors, and month summaries. Vectorint provides
Orbit, Nova, and Nebula Material color palettes in both light and dark appearance.

First run asks only for Current funds so Available now can provide immediate value.
Recurring items remain an optional refinement, and their empty state explains that
the user can add known commitments gradually rather than complete a forced setup.

Current funds setup defaults to the OS region's supported currency. After setup,
the currency remains fixed so editing the balance cannot silently create mixed-
currency data. Each save captures a new baseline instant; confirmed activity before
that instant is not replayed. One-off activity uses the baseline currency and can be
confirmed immediately or planned for the current calendar month. New activity is
insert-only, so an identity collision fails instead of overwriting an existing
economic event.

Activity history orders entries from newest to oldest by their confirmed or
planned timing, displays the persisted name, and keeps month-only timing imprecise
in presentation. Detail editing changes name, amount, direction, category, and
optional tags on the current stored row. Confirmation can include
those edits in the same database transaction, preserves the activity identity,
and cannot overlap a planned reservation with a second confirmed event. Tags are
display metadata only and never alter the accounting result. Deletion is an
explicit confirmed action.

Recurring items use the Current funds currency and default First occurrence to the
creation date. The editor first asks when each occurrence happens: on a specific
date, during a date range, or anytime within a month. It then accepts every N days,
weeks, months, or years, an optional inclusive `Ends on`, and an explicit
occurrence-month or following-month assignment. Automatic confirmation is the
default, with a per-item manual-confirmation toggle. `Remind me on` sits with the
other notification choices and remains economically neutral. All displayed dates
follow the current OS region. Reminder timing can be saved for an occurrence,
reminder date, or end date with the same model for income and expense.
Vectorint delivers these reminders locally around 09:00 in the device's current
time zone using an inexact alarm. Android notification permission is requested
only when a reminder is enabled. Denial does not consume the delivery, so the app
can catch up if notifications become available while the event remains relevant.

Recurring items and the Activity they generate retain their optional category and
tags without using either in calculation or lifecycle decisions. A deleted custom
category leaves every financial record intact and moves its assignments to
`Other`. Predefined and custom choices use localized labels and a curated set of
rounded Material Icons; no category is represented by a general emoji. Reminder delivery uses an
exact configuration key to prevent duplicate posting,
and acknowledges a reminder only after Android confirms it is active. The planner
reconciles saved state and notifications after app resume, recurring-item changes,
boot, wall-clock or time-zone changes, and app replacement. Notification taps open
the matching recurring-item editor. Public lock-screen content contains no item or
financial detail.

Every generated occurrence keeps its booking window separate from an explicit
following-month assignment. Daily and weekly rules may create multiple occurrences
in one month; monthly and yearly identities remain stable by occurrence month.
Home and History refresh atomically insert missing current-month occurrences and
promote due planned rows in place before displaying financial state. App resume
refreshes Home, so crossing a due date does not leave its result stale. A repeated
refresh, manual confirmation, or later Current funds baseline preserves the same
occurrence row, so it cannot be double counted or replayed. Deleting a recurring
definition stops future generation without deleting Activity already recorded
from it.

## Toolchain

- Android Gradle Plugin 9.4.0
- Gradle 9.7.1
- Compose compiler plugin 2.3.21
- Compose BOM 2026.08.00
- Room 2.8.4 with KSP 2.3.11
- Preferences DataStore 1.2.1
- Kotlin Coroutines Android 1.11.0
- Android SDK Platform 37.0 and Build-Tools 37.0.0
- compile SDK 37, target SDK 37, minimum SDK 23
- Java and Kotlin JVM target 21

## Release status

Version 0.0.8 is the first production release. Complete visual, accessibility,
device, reproducibility, signing, and publication gates before publishing any
later production release.

F-Droid is a separate update channel: it will build from the public release source
and use its own repository-specific signature. Vectorint's permanent developer
signature is reserved for GitHub and other compatible storefronts. The two channels
are not treated as interchangeable Android update paths.

F-Droid inclusion is under review in
[fdroiddata merge request !48451](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/48451).
The submission branch and merge-request pipelines pass for version 0.0.8.

Update this file only for material product, architecture, or open-work changes.
Detailed validation and incident history belongs in issues or pull requests.
