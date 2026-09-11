# Vectorint

Vectorint is a local-first Android budgeting companion focused on one clear
answer: **How much can I safely spend right now?**

Start with your current funds for an immediate answer. Add income, expenses, and
recurring items when you are ready to make that answer more precise. Vectorint
reserves planned expenses and includes expected income only when you choose to.

## What it does

- Shows **Available now** for the selected month.
- Offers a home-screen widget for the current month's **Available now** amount.
- Uses the same predictable model for income and expenses.
- Supports one-off and recurring entries, flexible intervals, optional end dates,
  reminders, and assignment to the occurrence month or following month.
- Groups expenses by category in a monthly overview.
- Keeps tags as visual organization only.
- Stores a newest-first history and supports local JSON backup and restore.
- Works without an account, ads, analytics, tracking, or an internet connection.

Vectorint's first production release is version 0.0.8.

## Build

Install JDK 21, Android SDK Platform 37, and Android SDK Build-Tools 37.0.0.
Point `JAVA_HOME` at JDK 21, then run:

```shell
./gradlew ktlintCheck testDebugUnitTest assembleDebug lintDebug
```

No network connection, account, telemetry, or proprietary service is required by
the application.

See [PRIVACY.md](PRIVACY.md) for the complete privacy summary and
[CHANGELOG.md](CHANGELOG.md) for user-facing changes. Reproducible release-build
details are in [BUILDING.md](BUILDING.md).

## Licence

Copyright 2026 K2040.

Vectorint source is licensed under GPL-3.0-only. Third-party dependencies retain
their own licences; dependency and bundled asset details are recorded in
[DEPENDENCIES.md](DEPENDENCIES.md) and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

The **Vectorint Raven** artwork was generated with OpenAI at the direction of
K2040 and contributed by K2040. Copyright 2026 K2040. It is licensed separately
under [Creative Commons Attribution 4.0 International](LICENSES/CC-BY-4.0.txt).
The original artwork is stored at
`app/src/main/res/drawable-nodpi/vectorint_raven.png`; Android scales and masks it
for launcher and in-app presentation without changing the source image.

The K2040 creator logo shown only in the About card was supplied and contributed
by K2040. Copyright 2026 K2040. Its original 512 px artwork is stored at
`app/src/main/res/drawable-nodpi/k2040_logo.png`.
