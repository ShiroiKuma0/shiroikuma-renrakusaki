---
name: build-apk
description: Build the signed foss release APK with the buildFoss Gradle task, then deliver it AUTOMATICALLY via the global /after-build skill (adb-push to the phone if connected, else scp to skhw) — no transfer prompt. ALWAYS build automatically after applying any change to the app (UI, code, or resources) — don't wait to be asked. Use whenever the user asks to build the app, build the APK, make a release build, or build and send to the phone, AND as the final step immediately after you finish making any app change.
---

# Build the foss release APK and optionally send to phone

> **⚠️ CRITICAL — every build MUST have a unique, strictly-increasing `BUILD_NUMBER`.**
> Never produce two builds with the same `BUILD_NUMBER`; never overwrite an APK already on the
> phone. ALWAYS build with `./gradlew buildFoss < /dev/null` — it auto-increments `BUILD_NUMBER`
> in `gradle.properties`, renames the APK, and copies it to `~/tmp/`. **NEVER run a bare
> `assembleFossRelease` for a deliverable build** — it does NOT bump `BUILD_NUMBER`, so you get the
> same filename and clobber the previous build on the phone (this mistake happened once). After
> building, confirm the printed `BUILD_NUMBER` is higher than the last one pushed; clean any stale
> `app/build/outputs/apk/foss/release/*.apk` first so the freshly-numbered APK is the one copied.

> **Always build after changes.** Whenever you finish applying a change to the
> app — UI, code, or resources — run this build automatically as the final step,
> **without waiting to be asked**. Then deliver the APK via `/after-build` below
> (automatic — no transfer prompt). The only exception is when the user explicitly
> says not to build, or the change is purely to non-app files (docs, skills, memory).

> **The push destination is ALWAYS `/sdcard/tmp/`.** Every `adb push` of the APK
> goes to `/sdcard/tmp/<apk name>` — **never** `/sdcard/Download/` or anywhere
> else. This holds even when pushing outside this skill's normal flow (e.g. a
> bare "push it to the phone"): create `/sdcard/tmp` if needed and push there.

> **Never run `adb install` (or `pm install`).** The build step copies the APK
> to the phone with `adb push` automatically (via `/after-build`), but
> **the user installs the APK themselves** from the phone's file manager. Do not
> install it for them under any circumstances.

> **Never `git commit` or `git push` on your own.** Building does not include
> committing. After building (and the optional `adb push`), the user tests the
> build themselves. **Only when the user explicitly says "Push"** do you then
> `git commit` the changes and `git push origin custom`. Note: the user's
> **"Push"** means *commit-and-push-to-the-fork* — it is unrelated to the
> `adb push` file copy in step 4.

## Steps

1. **Note the output filename.** Read the current version and build number:
   - `grep -E 'VERSION_NAME|BUILD_NUMBER' gradle.properties`
   - The APK will be `shiroikuma-renrakusaki_<VERSION_NAME>+<BUILD_NUMBER>_arm64-v8a.apk`, using the `BUILD_NUMBER` value **before** the build (the task bumps it afterward).

2. **Build (JDK 17+ required):**
   - `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFoss < /dev/null`
   - The `JAVA_HOME` prefix is required: AGP 9.x needs JDK 17+, but the default `java` on this machine is 11. The `< /dev/null` guarantees it never blocks on stdin — see caveat.
   - This runs `assembleFossRelease`, copies the signed APK to `~/tmp/<apk name>`, and auto-increments `BUILD_NUMBER` in `gradle.properties`.
   - The task prints `>>> ~/tmp/<apk name>`; use that line to confirm the exact filename, and confirm `BUILD SUCCESSFUL`.

3. **Deliver via `/after-build` — no prompt.** Every build, as soon as it reports `BUILD SUCCESSFUL` with the signed APK in `~/tmp/`, invoke the global **`/after-build`** skill. It runs **`/adb-check`** UNSANDBOXED (a sandboxed check falsely reports no device), then:
   - **phone connected** → **`/adb-push`** the newest `~/tmp/*.apk` to `/sdcard/tmp/<apk name>` (creating `/sdcard/tmp` if needed), then tell the user it's at `/sdcard/tmp/<apk name>`. Never `adb install` — they install it themselves.
   - **no phone** → **`/scp`** the newest `~/tmp/*.apk` to `skhw:~/tmp/`.

   Do this automatically — it is a **file copy**, never an install.

## Caveat — why transfer directly instead of via the task

The `buildFoss` task (`app/build.gradle.kts`) has an interactive `read -p "Push to phone? (y/n)"` prompt, but it runs in a subprocess of the **Gradle daemon**, whose stdin/stdout are not connected to Claude's Bash tool. Piping `y`/`n` into `./gradlew buildFoss` does not reach the prompt — the daemon subprocess gets EOF, silently skips the push, and its output is invisible. So the task's prompt is effectively dead under this tooling: delivering the APK (`/adb-push` or `/scp` via `/after-build`) is Claude's job — run it yourself, automatically, with no transfer prompt.

## Signing

Release signing is non-interactive: `app/build.gradle.kts` reads credentials from `keystore.properties` (falling back to `SIGNING_*` env vars). If neither is present the build is unsigned and the APK will not install. **Both forks now sign with the shared denwa key** (`~/.android-keystores/shiroikuma-denwa.jks`, alias `denwa`) so they share a `signature`-permission content provider for private contacts + per-contact SIM; `keystore.properties` (gitignored) points there. Changing this key requires uninstalling the app first (cert mismatch blocks in-place update).

## Prerequisite — patched Commons in mavenLocal

This app builds against our patched Fossify Commons (`commons = "6.1.6-sk5"` in
`gradle/libs.versions.toml`), resolved from `mavenLocal()` (`~/.m2`). On this machine it is already
published, so `buildFoss` just works. **On a fresh machine, or if `~/.m2` was cleared**, the build fails
with `Could not resolve org.fossify:commons:6.1.6-sk5` — publish it first:

```bash
cd ~/git/shiroikuma-commons && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew :commons:publishToMavenLocal -PVERSION=6.1.6-sk5
```

See the `shiroikuma-commons` repo's CLAUDE.md for the patch details.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` / "Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
