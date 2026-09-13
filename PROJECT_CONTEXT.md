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
- a version-6 Room persistence layer with exported schemas and host-side database
  tests;
- a suspending application repository that keeps blocking database work off the
  Android main thread;
- Preferences DataStore repositories for portable user settings and separate
  device-local automatic-backup configuration and status;
- OS-regional date and money presentation plus strict locale-aware amount input;
- complete plain-language English and informal-`du` German UI resources with
  automatic Android app-language discovery, while money and date formatting
  continue to follow the OS region;
- a data-backed Compose Home screen with explicit loading, setup, ready, unsafe,
  and retryable failure states;
- a detailed home-screen widget for the current month's Available now result plus
  a compact value-only alternative that opens one-off entry creation when tapped;
  both use fail-closed setup, unsafe-data, and load-failure states;
- local account setup and editing with a name, exact-time Current funds baseline,
  and an Available now inclusion choice;
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
- user-selected manual and automatic local JSON backups plus a fail-closed
  whole-data restore flow;
- idempotent current-month occurrence generation that stores one planned Activity
  row per economic occurrence and preserves an existing recorded occurrence;
- browsable past and future month summaries that preview recurring flows without
  creating or confirming Activity;
- the selected full-colour Vectorint Raven artwork used by the adaptive launcher
  icon and permanent app header, with the original source image preserved
  unchanged and licensed separately under CC BY 4.0;
- no network, online sign-in, analytics, advertising, or cloud integration;
- no iOS, desktop, web, or Kotlin Multiplatform targets;
- no GitHub Actions.

## Product direction

Vectorint answers how much the user can safely spend now across explicitly included
local accounts. Bank, PayPal, cash, savings, and other same-currency money sources
stay separate. Budgeting uses explicit calendar months. Income and expense share
the same activity and recurring-item concepts, with direction supplying the sign.

The calculation treats each account's Current funds as a captured balance.
Confirmed activity after its assigned account's capture changes that balance once;
earlier confirmed activity is already represented by the baseline and is not
replayed. An excluded account's balance and assigned activity do not affect
Available now. Planned current-month expenses in included accounts are always
reserved, while expected income is an explicit opt-in.
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

The local database stores account baselines, explicit account assignments for
activities and recurring items, reusable tag relationships, and user-created
category definitions. Recurring occurrence identity is unique in the schema,
confirmation updates the same row, and persisted timestamps retain nanosecond
precision. Account deletion is blocked while records reference it. A single
application-scoped repository provides background access and keeps confirmation
atomic. Room schema version 4 adds nullable category
assignments and custom categories on top of the version-3 occurrence-timing model.
Version 5 adds persisted activity names and recovers names for existing recurring
occurrences where their definition is still present. Existing unnamed one-off
records remain readable and use a localized History fallback. Version 6 replaces
the pooled balance row with account rows and migrates every existing record to the
included `Main` account.

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

Manual backup exports accounts, Activity, recurring items, categories, tags, and
saved settings to a versioned, bounded JSON file chosen by the user. Backup format
version 7 adds account balances, inclusion choices, and record assignments on top
of version 6 persisted activity names. It continues to import version-1 through
version-6 files through deterministic conversion into one included `Main` account;
an older backup
changes its saved calculation choice without replacing appearance information it
did not contain.
The file is readable and never uploaded by Vectorint. Restore validates the whole
file before mutation, replaces Room data in one transaction, compensates across
Room and DataStore on failure, and verifies the final state. Android permissions,
reminder-delivery acknowledgements, and other device-local runtime state are not
portable data; reminders are reconciled from the restored definitions instead.
Platform cloud backup and device transfer remain disabled.

Automatic backup reuses the same bounded, readable version-7 JSON snapshot. The
user grants one folder through Android's Storage Access Framework and can choose
backups after saved portable-data changes, when the app starts, when it moves to
the background, daily, or weekly. Change-triggered work is coalesced, scheduled
work is intentionally inexact, and app-background delivery is not promised after
force-stop, crashes, or abrupt process termination. Every file is read back and
verified before success; the newest ten matching automatic backups are retained
where the selected document provider supports cleanup. Folder access, trigger
choices, and last-run status stay in a separate device-local DataStore and are
not restored from portable backups. Work is scheduled with Android's platform
JobScheduler; Vectorint still has no internet permission, proprietary scheduling
dependency, or service integration.

Presentation adapters resolve Android's current format locale for each operation,
independently of app language. Amount input accepts localized digits and the
locale's decimal separator, converts directly to the selected currency's integer
minor units, and rejects grouping, foreign separators, excess precision,
unsupported currency metadata, and overflow. Formatted text remains outside the
domain and persistence models.

Home loads one budget snapshot and the current calculation setting, delegates all
accounting to the domain calculator, and formats only a successful result. It
presents Available now as the primary value with included account funds, reserved
expenses, and expected-income inclusion underneath. Missing account setup, unsafe
data, and read failures never display a calculated amount.

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

First run asks only for one named account and its Current funds so Available now can
provide immediate value. Additional accounts and recurring items remain optional
refinements rather than forced setup.

The first account defaults to the OS region's supported currency. Every later
account uses that same currency. Changing an account balance captures a new baseline
instant; renaming it or changing its inclusion choice preserves the instant.
Confirmed activity before its assigned account's baseline is not replayed. One-off
activity uses the account currency and can be confirmed immediately or planned for
the current calendar month. With one account, it is selected automatically and no
chooser is shown. With multiple accounts, direct chips assign each new entry.
New activity is insert-only, so an identity collision fails instead of overwriting
an existing economic event.

Activity history orders entries from newest to oldest by their confirmed or
planned timing, displays the persisted name, and keeps month-only timing imprecise
in presentation. History and recurring lists show the assigned account. Detail
editing changes name, amount, direction, category, and
optional tags on the current stored row. Confirmation can include
those edits in the same database transaction, preserves the activity identity,
and cannot overlap a planned reservation with a second confirmed event. Tags are
display metadata only and never alter the accounting result. Deletion is an
explicit confirmed action.

Recurring items use the account currency and default First occurrence to the
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
refresh, manual confirmation, or later assigned-account baseline preserves the same
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

Version 0.0.11 with Android version code 12 is the next release candidate. Its
source scope is limited to separate local accounts, automatic local backups, the
detailed and compact widget choices, and the related compact Home and settings
refinements. It remains unsigned and unpublished until the remaining release
gates and maintainer approvals are complete.

F-Droid is a separate update channel: it will build from the public release source
and use its own repository-specific signature. Vectorint's permanent developer
signature is reserved for GitHub and other compatible storefronts. The two channels
are not treated as interchangeable Android update paths.

F-Droid inclusion is under review in
[fdroiddata merge request !48451](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/48451).
The submission branch and merge-request pipelines pass for version 0.0.8.

Update this file only for material product, architecture, or open-work changes.
Detailed validation and incident history belongs in issues or pull requests.
