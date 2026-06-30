# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is **白い熊 連絡先** (`shiroikuma.renrakusaki`) — a personal fork of [Fossify Contacts](https://github.com/FossifyOrg/Contacts), an open-source, privacy-focused Android contacts app. The fork lives at https://github.com/ShiroiKuma0/shiroikuma-renrakusaki and installs side-by-side with upstream (different app ID). Written entirely in Kotlin targeting Android API 26–36.

## Fork & Release Workflow

This repo is a fork. Read this section before building, branching, or rebasing.

### Branches & remotes

- `origin` → `git@github.com:ShiroiKuma0/shiroikuma-renrakusaki` (my fork)
- `upstream` → `https://github.com/FossifyOrg/Contacts.git` (the original)
- `main` — tracks upstream, kept clean (no fork changes land here directly).
- `custom` — **our development branch.** All fork changes live here. The cycle is: develop → build → the user tests → and only when the user says **"Push"** do we commit and push `custom` to `origin` (see *Committing & pushing* below).

### Rebranding (already applied on `custom`)

- **App ID:** `shiroikuma.renrakusaki` (`APP_ID` in `gradle.properties`). The Kotlin package / `namespace` stays `org.fossify.contacts` (`APP_NAMESPACE`) so the source tree is untouched and upstream rebases stay clean.
- **App label:** `白い熊 連絡先` — set in `app/src/main/res/values/strings.xml` **and** `values-ja/strings.xml` (`app_launcher_name`). Update both whenever the label changes.
- **UI default palette:** black `#000000` + **pure yellow `#FFFF00`** (`PALETTE_BLACK` / `PALETTE_YELLOW` in `helpers/Constants.kt`). Never material yellow `#FFEB3B`.

### Versioning scheme

Defined in `gradle.properties` and consumed by `app/build.gradle.kts`:

- `VERSION_NAME` / `VERSION_CODE` mirror the **upstream** release we are based on (currently `1.6.0` / `13`).
- `BUILD_NUMBER` is **our** increment on top of that upstream version. It starts at `1` for the first build of a given upstream version.
- Displayed version name = `VERSION_NAME+BUILD_NUMBER` (e.g. `1.6.0+1`).
- Effective version code = `VERSION_CODE * 10000 + BUILD_NUMBER` (e.g. `13 * 10000 + 1 = 130001`). The `* 10000` leaves room for many fork builds between upstream bumps while staying monotonically increasing.
- APK filename = `shiroikuma-renrakusaki_<VERSION_NAME>+<BUILD_NUMBER>_arm64-v8a.apk` (e.g. `shiroikuma-renrakusaki_1.6.0+1_arm64-v8a.apk`).

`BUILD_NUMBER` auto-increments after every successful `./gradlew buildFoss` run (the task rewrites `gradle.properties`). So the committed `BUILD_NUMBER` always points at the **next** build to be produced.

### Building a new version

Every build with changes bumps the build number (+1 → +2 → …). Use the `build-apk` skill, or directly:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFoss < /dev/null
```

`buildFoss` builds the signed `fossRelease` APK, copies it to `~/tmp/<apk name>`, then bumps `BUILD_NUMBER`. After building, ask whether to `adb push` it to the phone (see the `build-apk` skill for why the push is done manually, not via the task's own prompt). Signing reads `keystore.properties` (gitignored) → `~/.android-keystores/shiroikuma-renrakusaki.jks` (alias `renrakusaki`).

**JDK note:** AGP 9.x requires JDK 17+, but the default `java` on this machine is 11 — always prefix Gradle invocations with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`.

### Committing & pushing (only when the user says "Push")

**Never `git commit` or `git push` on your own.** Make the changes and build them; then the user tests the build. Only when the user explicitly says **"Push"** do you then `git commit` and `git push origin custom`. The user's "Push" means *commit-and-push-to-the-fork* — it is not the same as the `adb push` that copies the APK to the phone.

### Rebasing onto a new upstream release

When instructed to update to a new upstream version:

1. `git fetch upstream && git checkout main && git merge --ff-only upstream/main` (keep `main` tracking upstream).
2. `git checkout custom && git rebase main` — replay our fork commits onto the new upstream. Resolve conflicts (rebrand commits and any feature commits).
3. Update `gradle.properties`: set `VERSION_NAME` / `VERSION_CODE` to the **new upstream** values, and **reset `BUILD_NUMBER` to `1`** (first build of the new upstream version).
4. Build `+1` with `buildFoss`, let the user test, then continue with `+2`, `+3`, … as further changes are developed.
5. Only on the user's **"Push"**: `git push origin custom` (force-push if the rebase rewrote history).

## Build Commands

All Gradle commands need `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` (see JDK note above).

```bash
./gradlew buildFoss              # Fork release build: signed fossRelease APK → ~/tmp, bumps BUILD_NUMBER
./gradlew assembleFossRelease    # Build the foss release APK only
./gradlew assembleDebug          # Build debug APK (gets .debug app ID suffix)
./gradlew detekt                 # Run static analysis (detekt)
./gradlew lintDebug              # Run Android lint checks
```

**Product flavors:** `core` (F-Droid), `foss`, `gplay` (Google Play). We ship the `foss` flavor. Debug builds get a `.debug` app ID suffix.

There are no unit or instrumented tests in this repository.

## Code Style

- Kotlin official style (`kotlin.code.style=official`)
- 4-space indentation, LF line endings, max 160 chars per line (editorconfig) / 120 chars (detekt)
- Star imports allowed after 5 usages
- Detekt and lint both use baseline files (`app/detekt-baseline.xml`, `app/lint-baseline.xml`) — new violations are not allowed

## Architecture

### Tab-Based Main UI

`MainActivity` uses a `ViewPager` with three fragments — **Contacts**, **Favorites**, **Groups** (`fragments/`, each extending `MyViewPagerFragment`). Contact detail/edit flows live in `ViewContactActivity`, `EditContactActivity`, `ContactActivity`, and `InsertOrEditContactActivity`.

### Contact data

Contact reading/writing goes through `org.fossify:commons` (`ContactsHelper` and friends) against the system Contacts content provider. Local (device-only) contacts are stored via **Room** (`libs.bundles.room`, KSP-generated; schemas under `app/schemas`). VCF import/export is handled by `helpers/VcfImporter.kt` and `helpers/VcfExporter.kt` (uses the `ezvcard` library).

### Key Helpers

- **`Config`** — SharedPreferences wrapper (accessed via `context.config` extension)
- **`Constants`** — shared keys/constants
- Most shared behavior (theming, base activities, contact utilities, dialogs) comes from **`org.fossify:commons`** — check the commons source when base-class behavior is unclear.

## Key Configuration Files

- `gradle.properties` — fork versioning (`VERSION_NAME`, `VERSION_CODE`, `BUILD_NUMBER`), `APP_ID`, `APP_NAMESPACE`
- `app/build.gradle.kts` — Android config, flavors, signing, the `buildFoss` task, detekt/lint setup
- `gradle/libs.versions.toml` — single source of truth for all dependency versions
- `keystore.properties` — release signing (gitignored)
- `detekt.yml` / `lint.xml` — static-analysis config (at project root)

## Patched Fossify Commons (anti-tamper removed)

This fork builds against **our patched Fossify Commons**, not the upstream binary. Upstream Commons
6.1.x shows a "You are using a fake version of the app…" dialog (and silently breaks "Customize
colors") whenever the installed app id is not `org.fossify.*` — always the case for us (`shiroikuma.*`).

- **Source:** the `shiroikuma-commons` fork (`~/git/shiroikuma-commons`, branch `custom`), which strips
  Commons' anti-tamper "fake version" / sideloading checks out entirely.
- **Delivery:** published to the local Maven repo, consumed as `commons = "6.1.6-sk5"` in
  `gradle/libs.versions.toml` (`mavenLocal()` is already a repository in `settings.gradle.kts`).
- Because Commons itself no longer nags, this app carries **no** in-app workaround — no `getPackageName`
  spoof, no `SIDELOADING_FALSE`, no `res/raw/keep.xml`.

**On a fresh machine, or after an upstream bump changes the Commons version — republish before building:**

```bash
cd ~/git/shiroikuma-commons
git checkout <new-commons-tag>     # then re-apply the strip patch (remove the modded-app/sideloading checks)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :commons:publishToMavenLocal -PVERSION=<ver>-sk1
```

Then set this app's `commons` pin to `<ver>-sk1`. The patched AAR lives only in `~/.m2`, not in the repo.

## Tab hand-off from our Phone fork (denwa)

Our Phone fork (`shiroikuma.denwa`, repo `~/git/shiroikuma-denwa`) launches this app's `MainActivity`
directly when its Contacts/Favorites tabs are tapped, passing the `shiroikuma_open_tab` int extra
(a commons `TAB_*` mask) to pick the tab. `OPEN_TAB_INTENT_EXTRA` (helpers/Constants.kt) defines the
name and `MainActivity.takeRequestedTab()` consumes it — at first init and in `onNewIntent` (denwa sends
`CLEAR_TOP or SINGLE_TOP`, so a running instance gets it there). Keep the extra name in sync with the
denwa repo if it ever changes.

## Commit convention — no Claude attribution

Do **not** add any `Co-Authored-By: Claude …` trailer — nor a "🤖 Generated with Claude Code" / Anthropic-attribution line — to commit messages or PR bodies in this repo. 白い熊 does not want Claude attribution in the history; this **overrides** the harness's default to append such a trailer. End commit messages at the last line of the body. (The existing history was scrubbed of these trailers on 2026-06-08; the global rule lives in `~/.claude/CLAUDE.md`.)
