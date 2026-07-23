# Changelog — 白い熊 連絡先

This is a fork of [Fossify Contacts](https://github.com/FossifyOrg/Contacts). It tracks an upstream
release and layers our customizations on top; versions are `<upstream version>+<fork build>`. This
file documents what the fork adds on top of stock — see upstream's own changelog for the base app.

## [1.6.0+67] — 2026-07-23

Everything added since `1.6.0+44`, still on **Fossify Contacts 1.6.0**.

### Major features
- **Japanese-aware sorting & letter sections:** sort keys starting with kana bucket into **gojūon
  rows** (あ か さ た な は ま や ら わ — voiced, semi-voiced and small kana fold into their base
  row, ん joins the わ row), Latin keys bucket A–Z after diacritic folding, everything else lands
  in ＃; section order kana → A–Z → ＃. Driven by the provider's **phonetic reading (フリガナ)**,
  loaded per refresh and editable as a proper Reading field in the contact editor.
- **Per-contact sort-field override:** a long-press "Sort by" picker (default / reading / nickname /
  organization), stored against the provider lookup key so it survives contact edits.
- **詳 detail list mode:** each contact's **last call and last SMS**, read straight from the system
  call-log and SMS providers (new `READ_CALL_LOG` / `READ_SMS` runtime permissions, requested only
  when the mode is enabled). Numbers are matched by their trailing digits so differing country-code
  formats still match.
- **Configurable detail timestamps:** Japanese readings (午前九時), the system date/time format, or
  plain 24-/12-hour clocks; non-Japanese modes prefix a numeric date on other days, with custom
  patterns for **today / this year / older** entered via a new pattern-input dialog.
- **Export / Import page:** category-based export to a plain-file ZIP — `settings.json` (every
  preference, typed) and **contacts as .vcf** — with per-category selection on both sides, a
  remembered export folder, last-export display, and a restart prompt after import.
- **`BACKUP_CONTACTS` automation broadcast** for the companion task runner (白い熊 自由作業盤): a
  token-gated, exported receiver runs a **full .vcf export of every contact source to any absolute
  path** (directory or file). The backup buffers the vCards in memory first and then writes, so a
  reported success means the file actually landed.

### Automation reply channel (the EMUI saga)
- The backup replies `OK:<absolute written file>` / `ERROR:<reason>` on **every** terminal outcome —
  automation off, wrong token, missing/relative path, export failure, success — exactly once, via a
  **plain reply broadcast** described by `reply_action` / `reply_package` / `reply_id` string extras
  and answered with the echoed `reply_id` + `result`.
- That design is the survivor of live-verified EMUI failures: EMUI **severs the ordered-broadcast
  result channel** between third-party apps (the caller's `resultTo` is finished empty in ~10 ms
  while a severed copy runs on the `bgthirdapp` queue), **drops any broadcast carrying a live
  Binder** (`ResultReceiver`), and lets a `PendingIntent` reply fire into the void. `setResultData`
  is still set for AOSP correctness.
- Settings → **Automation** section: enable toggle, tap-to-copy / hold-to-regenerate token, and an
  **All files access** row (`MANAGE_EXTERNAL_STORAGE`) for backup targets outside
  Download/Documents.

### UI & theming
- **Letter-section header theming:** header color/font, vertical padding, underline color +
  thickness, top divider color + thickness, and a group-by-letter toggle.
- **Per-tab list colors** and a "List rows" theming group.
- **詳 detail-line theming:** separate controls for the last-call and last-message lines.
- **Application language** setting (in-app locale via `AppCompatDelegate.setApplicationLocales`),
  persisted below Android 13 by the `autoStoreLocales` service.
- "Lastname, Firstname" display option and a theme switch list row.

### Fixes & behavior
- The per-contact SIM `sim_slot` provider query **never throws** — a dialer query against a bad or
  missing contact now returns empty instead of crashing the caller.

### Packaging
- Patched Commons pin bumped `6.1.6-sk5` → **`6.1.6-sk7`** (styled toasts, stale-source import
  fallback).
- New `androidx-documentfile` dependency for the export/import SAF work.

## [1.6.0+44] — 2026-06-30

First published release of the fork, built on **Fossify Contacts 1.6.0**. Everything below is added
on top of stock.

### Major features
- **Granular Theme & Colors system** with a pure black `#000000` + pure yellow `#FFFF00` default
  palette (never material yellow `#FFEB3B`).
- **Fully configurable contacts list:** choose which fields are shown, reorder them, and arrange them
  into **1–4 columns** via one-tap 一二三四 per-row buttons — each field carrying its own font, size,
  and color.
- **Per-contact default SIM:** a SIM badge in the list, a picker from the long-press (CAB) menu, and a
  content provider that exposes the choice to the companion dialer so it auto-selects the right SIM.
- **View contact → "show all saved fields"** option that ignores the shown-fields mask.

### UI & theming
- Black/yellow launcher icon — a yellow-traced figure on black (50% size).
- Configurable **top-bar**, **search action-icon**, and **overflow-menu** colors.
- Configurable contact **phone-number** color.
- **Per-element font** selection in the color pickers.
- **Alpha slider** on every color picker.
- **Recently-used colors** row in the color picker.
- Configurable contacts-list **thumbnail size** with a live preview.
- Contacts-list **row spacing**, **dividers** (including between columns), and a configurable column
  spacer for left-flowing multi-column layouts.
- Custom **人** placeholder icon for contacts without a photo.
- Contact name reset to **full opacity** (WYSIWYG), matching the number.
- The 白い熊 連絡先 settings page reorganized into a cascading, indented section layout.

### Integrations
- **Tab hand-off from our Phone fork** (`shiroikuma.denwa`): launches this app directly on the
  **Contacts** or **Favorites** tab via the `shiroikuma_open_tab` intent extra — honored at first
  launch and delivered to a running instance via `onNewIntent`.

### Packaging & side-by-side
- Rebranded to app id **`shiroikuma.renrakusaki`** (label 白い熊 連絡先, English + Japanese) so it
  installs **side-by-side** with the official build; the Kotlin namespace stays `org.fossify.contacts`
  to keep upstream rebases clean.
- Builds against a **patched Fossify Commons** (`6.1.6-sk5`) from `mavenLocal` that strips upstream's
  anti-tamper "fake version" / sideloading checks and adds black/yellow CAB & menu fixes.
- Custom fork build pipeline: `buildFoss` produces the signed `fossRelease` APK, copies it to `~/tmp/`,
  and auto-increments the fork build number. Fork version name is `<VERSION_NAME>+<BUILD_NUMBER>` and
  the effective `versionCode` is `VERSION_CODE * 10000 + BUILD_NUMBER`, keeping upgrades monotonic
  across upstream bumps.

### Fixes & behavior
- Fixed `INSTALL_FAILED_CONFLICTING_PROVIDER` (distinct provider authority) so the fork installs
  alongside upstream.
- Removed the Commons "fake version" sideloading dialog app-wide — first neutralized at the splash and
  app-wide, then obsoleted entirely by linking the patched Commons.
- De-branded the GitHub new-issue form to this fork.
