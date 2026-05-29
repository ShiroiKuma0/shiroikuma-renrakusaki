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
- `custom` — **our development branch.** All fork changes live here. We develop, test, build, then push `custom` to `origin`.

### Rebranding (already applied on `custom`)

- **App ID:** `shiroikuma.renrakusaki` (`APP_ID` in `gradle.properties`). The Kotlin package / `namespace` stays `org.fossify.contacts` (`APP_NAMESPACE`) so the source tree is untouched and upstream rebases stay clean.
- **App label:** `白い熊 連絡先` — set in `app/src/main/res/values/strings.xml` **and** `values-ja/strings.xml` (`app_launcher_name`). Update both whenever the label changes.

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

### Rebasing onto a new upstream release

When instructed to update to a new upstream version:

1. `git fetch upstream && git checkout main && git merge --ff-only upstream/main` (keep `main` tracking upstream).
2. `git checkout custom && git rebase main` — replay our fork commits onto the new upstream. Resolve conflicts (rebrand commits and any feature commits).
3. Update `gradle.properties`: set `VERSION_NAME` / `VERSION_CODE` to the **new upstream** values, and **reset `BUILD_NUMBER` to `1`** (first build of the new upstream version).
4. Build `+1` with `buildFoss`, test, then continue with `+2`, `+3`, … as further changes are developed.
5. `git push origin custom` (force-push if the rebase rewrote history).

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
