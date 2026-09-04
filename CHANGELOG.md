# Changelog — 白い熊 連絡先

This is a fork of [Fossify Contacts](https://github.com/FossifyOrg/Contacts). It tracks an upstream
release and layers our customizations on top; versions are `<upstream version>+<fork build>`.

**This file carries both histories.** The fork's releases come first, newest first, each saying which
upstream release it is built on; **upstream's own changelog follows below, verbatim** — their text is
never edited or reordered, so a rebase merges this file cleanly instead of conflicting every sync.

## [1.6.0+080] — 2026-09-04

Everything added since `1.6.0+079`, still on **Fossify Contacts 1.6.0**. This release implements the
sister-app automation contract **v2**, and fixes a long-standing defect that had been quietly breaking
the one automation this app is asked for most.

### 自由作業盤's language switch was never hearing our answer — fixed
The companion task runner's **Japanese⇄English switch** asks this app to back the contacts up first and
refuses to run unless that backup reports success. It has been reading a **silence** rather than an
answer, on every attempt.

- The manifest's `<queries>` element listed only the two upstream Fossify packages — the ones kept so
  this fork installs side-by-side. **Neither automation caller was named.**
- Every reply this app sends is a broadcast aimed at the caller with `setPackage(…)`, and since Android
  11 a `setPackage` to a package the sender cannot *see* is discarded **silently**. The export ran, wrote
  the right file, reported the right result — into nothing.
- It is the failure that passes every test that does not check whether the answer arrived, and the
  element being *present* is why an audit of the manifest missed it.
- **Both callers (白い熊 自由作業盤 and 白い熊 応用管理) are now named**, with the reason recorded in the
  manifest so it is not trimmed back later.

### The authorization token is now optional — and off by default
A pasted 48-character secret cannot survive a wipe, and restoring this app onto a clean phone is exactly
the case the automation now exists to serve. A gate that only works once the phone is already set up is
no gate for setting the phone up.

- **Automation is now ON by default**; the master switch stays, because being able to close one app off
  is the point of having a switch at all.
- **New: 「認証トークンを使う？」, default OFF** — in 白い熊 連絡先 UI → **Export / Import** → 自動化,
  directly under 「自動化エクスポート」.
- **The token row is hidden unless that switch is on.** A secret sitting under an off switch only invites
  being pasted somewhere it will do nothing.
- **A token sent to the app when it is not required is ignored, never refused.** Tokens outlive the
  setting they were pasted for, and half a backup batch failing because one app's switch moved is exactly
  the friction the change removes.
- All three entry points — `BACKUP_CONTACTS`, `EXPORT_STATE`/`LIST_CATEGORIES`/`CANCEL_EXPORT`, and the
  new data door — now answer through **one** gate, so "disabled" and "bad token" cannot drift apart.
- The new preference is **excluded from export**, alongside the token and the enable switch: restoring a
  backup must never quietly relax this device's own security state.

### A wiped phone can be put back — the data door
A new provider lets the app-manager fork (白い熊 応用管理) back up this app's *data*, not just its APK,
and restore it onto a phone where nothing has been set up yet.

- **`describe` · `export` · `import` · `cancel`.** `describe` answers what this app holds without
  exporting anything, so a list can be drawn — and compatibility judged — before any bytes move.
- **Callers are identified three ways: exact package name (never a prefix), the uid the kernel reports,
  and a pinned signing certificate.** A package name is not a namespace anyone owns, so a prefix test
  would have been weaker than the token it replaces.
- **The payload travels through a file handle the caller opens**, not a path. The backup is encrypted and
  checksummed per file it knows about, so a file dropped in from outside would sit in plaintext inside an
  otherwise encrypted archive and be unverified rather than verified.
- **Restoring exists only here.** It is never reachable by broadcast: an import overwrites this app's
  data, and the broadcast surface cannot tell who is calling.
- Long work runs in a foreground service, because a backgrounded app writing for minutes is frozen
  mid-stream on this phone — which yields a truncated archive underneath a success reply, the one failure
  indistinguishable from a good backup until the day it is needed.
- A refusal is always returned as a message, never thrown across the boundary.

### Known limitation — restoring contacts onto a *freshly installed* app
Settings and fonts restore onto a clean phone without ceremony. **Contacts cannot**, yet: writing them
goes through the system contacts provider, which needs the `WRITE_CONTACTS` runtime permission, and an
app that has just been installed and never opened holds no runtime permissions at all. The import fails
honestly rather than reporting a success it did not achieve — but it will fail until the permission is
granted by hand. Granting it, or opening the app once and granting it there, is enough.

## [1.6.0+079] — 2026-08-16

Everything added since `1.6.0+76`, still on **Fossify Contacts 1.6.0**. One visible change and one to
how builds are named. From this release the fork's build counter is **zero-padded to three digits**,
so `1.6.0+76` is followed by `1.6.0+079` — the earlier tags stay as they were published.

### 写真の上のアイコン — the icons over a contact's photo stop merging into it
A contact screen draws its icons straight on top of the photo, and on this palette a photo-less
contact is a solid pure-yellow placeholder — so a yellow icon over it was invisible, and stock white
was the only thing that read. Those icons are now the accent, over a halo in the background color.

- **The halo is the icon's own shape, not a box.** The glyph is stamped 16 times around a circle of
  the halo's radius in the outline color, then drawn on top in its fill color; at every width offered
  the stamps overlap, so the ring reads as one solid edge that traces the arrow, the star, the ⋮.
- **Covered on the view *and* edit screens:** the top bar's back arrow, edit, share, delete and
  overflow icons, plus the actions on the photo's bottom edge — favorite, call, SMS, email, and the
  edit screen's change-photo camera.
- **Re-entering a screen never thickens it.** The composed icon keeps the drawable it was built from,
  so the repaint that runs on every resume re-renders from the original instead of haloing the halo.
- The field icons *below* the photo are deliberately untouched — they sit on the background and
  already follow the foundation text color.
- **New section in 白い熊 連絡先 UI: 写真の上のアイコン**, between 上部バー and 頭文字セクション —
  a color for the top-bar icons, a color for the action icons, the halo's **thickness in dp**
  (0–8, default 2; 0 turns it off) and the halo's **color**. The two icon colors inherit from
  PRIMARY and the halo from BACKGROUND, so the palette still cascades until a slot is overridden.

### The fork build counter is zero-padded to three digits
- **Every name the counter appears in now renders `+079`, not `+79`** — the version shown in the app,
  the APK filename, and the release tag cut from it. They sort in build order instead of lexically,
  where `+100` came before `+79`. It also puts this repo in line with the sister apps, which had
  already migrated.
- `gradle.properties` deliberately keeps the plain integer (the build task's auto-increment rewrites
  that literal), and `versionCode` still computes as `VERSION_CODE * 10000 + BUILD_NUMBER`, so
  upgrade ordering is untouched. The padding is applied once, where the version name is built.

## [1.6.0+76] — 2026-07-31

Everything added since `1.6.0+75`, still on **Fossify Contacts 1.6.0**. Two changes to the 保存復元
contract: the categories now state their own default, and a running export can be stopped from outside.

### `LIST_CATEGORIES` states each category's default
白い熊 自由作業盤's 保存復元 project redraws its backup-item picker from this app's reply every time it
is opened, so whether an item starts ticked is this app's answer to give, not the picker's to guess.

- **Every line is now `id<TAB>label<TAB>parent<TAB>on|off`** — the contract's optional fourth field.
  The fields are positional, so the third is always present and **empty for a top-level item**; for a
  sub-option it is still its parent's id, and the line still follows the parent's.
- **Nothing in this app is `off`.** The rule is for data that is large, derived *and* re-creatable — a
  regenerable thumbnail cache, downloaded map tiles — and every category here (settings, fonts,
  contacts) is small and irreplaceable. Sending `on` still matters: it is the app stating a default
  rather than the picker assuming one, and any category added later inherits a field already there.
- `SettingsExport.Item` carries the answer as **`defaultOn`** (defaulting to `true`), with
  `Item.defaultSelection` as the single source of "what starts ticked".
- **The in-app Export / Import sheet seeds from the same flag**, so that sheet and the automation
  picker open on identical ticks.
- An **absent `items` extra** now resolves to `defaultSelection` rather than "every id" — identical
  today, and still correct the moment anything goes `off`.

### `CANCEL_EXPORT` — a running export can be stopped
保存復元's 中止 button used to only stop *listening*, so a cancelled run carried on to the end and
delivered a backup that had been stopped. It now fires a real cancel at the app.

- **`shiroikuma.renrakusaki.action.CANCEL_EXPORT`**, a third action on the **same exported receiver**:
  the export runs inside that receiver, so the stop signal arrives at a component the caller can
  already reach — nothing has to start a non-exported service. Extras: `token` (the same gate as the
  other two) and an optional `reply_id` (absent = the export that is running, unambiguous because two
  at once are forbidden).
- **Fire-and-forget:** it sends no reply of its own, not even `OK:`.
- **Safe to send at any time.** With nothing running, after the export already finished, or naming a
  different `reply_id`, it is a **silent no-op** — not an error, not a reply, not a crash.
- **It unwinds at the next boundary, never mid-`write()`.** A `@Volatile` flag on the receiver's
  companion — a `BroadcastReceiver` is a fresh instance per delivery, so the run's state cannot live
  on the instance — is polled before every ZIP entry, and once more after the vCards are built, the
  long part of a run, so a cancel during the contact conversion keeps all of it out of the archive.
  No thread interruption, no `System.exit`.
- **The terminal reply still arrives:** `ERROR:cancelled` for the original request, through the normal
  reply broadcast, behind the same `AtomicBoolean` that already prevents a double-fire — sent even
  though nobody may still be listening, since it is what proves the run ended rather than continuing
  unseen.

### A run that does not finish leaves nothing behind
- **`Target.discard()`**, called from the `finally` that already handles every failure, so a cancel
  and a real error end the same way: the destination is created before the first byte, so it is taken
  back. The backup directory is left exactly as it was found, never holding a short ZIP that looks
  like a backup.
- Backing it, **`deleteBackupFile()`** undoes `openBackupOutputStream` through the same three writers
  in the same order — persisted SAF grant, MediaStore row, plain file — because which one created the
  file is not knowable afterwards, and `File.delete()` silently fails on a MediaStore-owned entry
  under `Download/` or `Documents/`.
- **No `.part` stage**, deliberately: MediaStore rewrites a `DISPLAY_NAME` whose extension disagrees
  with the MIME type, so `…zip.part` would land as `…zip.part.zip`. Deleting the destination gives the
  same guarantee the `.part` convention exists to provide.

## [1.6.0+75] — 2026-07-25

Everything added since `1.6.0+67`, still on **Fossify Contacts 1.6.0** (upstream has not cut a new
release; `main` was synced to its latest maintenance commits).

### The 保存復元 state-export contract
白い熊 自由作業盤's 保存復元 project backs up every sister app in one run. This build implements its
wire contract, so 連絡先 joins the batch.

- **`shiroikuma.renrakusaki.action.EXPORT_STATE`** — runs the same category ZIP as the Export /
  Import panel, **headlessly**: no Activity, no interaction. Extras: `token`, optional `path` (an
  absolute directory that **overrides** the configured export folder, created if missing), optional
  `items` (comma-separated category ids; absent means everything), optional `progress_action`, plus
  `reply_action` / `reply_package` / `reply_id`. Directory precedence is `path` → the configured
  export folder → `ERROR:no-directory`.
- **`shiroikuma.renrakusaki.action.LIST_CATEGORIES`** — instant, token-gated; replies `OK:` plus one
  `id<TAB>label` line per selectable item, a **sub-option** adding a third `parent-id` field after
  its parent's line so the caller can render it indented and make it follow the parent's toggle.
- **The reply:** `OK:<absolute path>|<bytes>|<human size>|<n> categories`, or `ERROR:<reason>` with
  `automation disabled` and `bad token` reported distinctly. Exactly one terminal reply per request,
  behind an `AtomicBoolean`, sent as a plain broadcast carrying `reply_id` + `result` with
  `FLAG_INCLUDE_STOPPED_PACKAGES`. Both byte forms are computed here, since the caller cannot stat
  the file.
- **Progress broadcasts** while exporting — **real counts, never a percentage**: `text` is the
  numbers-first line (`連絡先 123/456`, `区分 1/3 — 設定`) alongside `current` / `total` / `unit` as
  structured extras, throttled to at most one per 500 ms with an unthrottled final one at
  completion. Fed by a new per-contact callback on `VcfExporter`.
- Verified live on the Mate XT: a headless export wrote a valid 4.7 MB ZIP to an overridden path,
  `items` subsets and unknown ids behaved (unknown ids write **nothing**), the no-directory branch
  replied without writing, progress fired 605 ms and 502 ms apart, and every one of ten requests
  produced exactly one reply. The ordered-broadcast result is severed **even for a shell caller**
  (dumpsys shows a phantom record finishing empty beside the real one) and app-originated extras come
  back `STRIPPED`, so the reply text is readable only by the receiving app — which is precisely why
  the contract mandates the reply broadcast.

### Export / Import — sub-options and a single panel
- **Sub-options:** the flat category enum became a parent/child model. `settings.fonts` is dotted
  under `settings`, and picking a **parent alone means that category's own data only** — its parts
  are separate ids, included only when asked for. Import applies the parts independently, so
  restoring font files without the prefs that reference them (or the reverse) is a valid choice.
- A **pre-sub-option ZIP is still recognised** by its entries, so older backups restore unchanged.
- **The section is two rows** — the export folder, and the row that opens the panel (its value line
  reports the last export). Everything selectable moved **into** the panel: the 全選択 master toggle,
  the category checklist with sub-options indented under their parent, and the one-shot VCF actions.
  The panel's buttons are Export / Import / Cancel. This matches the jami fork's layout.
- **Backup filenames are now `shiroikuma-renrakusaki_<yyyy-MM-dd_HH-mm-ss>.zip`** — version-free, so
  a backup is identified by when it was taken. The build that wrote it is recorded inside, as
  `manifest.json`'s `appVersion`. Older version-bearing names still match the discovery prefix, so
  the last-export line keeps finding them.
- The export core is **callable headlessly** — `exportBlocking(context, items, out, onProgress)`
  takes a `Context` instead of a `SimpleActivity`, with the panel and the receiver as two thin
  callers rather than duplicated logic. The configured export folder moved into `SettingsExport`, so
  both resolve the same one.

### Automation UI
- The automation controls live **inside the Export / Import section**, directly below the export
  rows they drive — not in a section of their own — matching every sister app.
- **A master switch** (default off) labelled 自動化エクスポート, with a one-line description
  explaining that sister-app tasks may trigger this app's export through the token-gated intent.
- **A token row** showing the token **abbreviated** (`ad81fc6f…7a44a0dc`), copying it **in full** on
  tap with a confirmation toast, and carrying a **Regenerate** action on the right that warns pasted
  copies stop working until the new token is pasted into 自由作業盤. Copying deliberately avoids
  commons' `copyToClipboard`, which would toast the secret straight back onto the screen.
- The token still never travels in a backup: `automation_token` and `automation_enabled` stay in
  `PREFS_EXCLUDE`, verified by unzipping a headless export and grepping for the live token.

### UI density
- **Rows are packed tight.** `indentRow` — the choke point all fourteen row builders pass through —
  now replaces the commons row style's 20 dp top/bottom padding with 4 dp, reclaiming 32 dp per row
  across the whole 白い熊 連絡先 UI page and the Export / Import panel. Section headers keep their
  spacing, since with rows this tight they are what separates one section from the next.

### Fixes & packaging
- `openBackupOutputStream` takes a **MIME type**, so a state ZIP written under `Download/` or
  `Documents/` is no longer registered to MediaStore as `text/x-vcard`.
- Both exported receivers carry `tools:ignore="ExportedReceiver"` — the caller cannot hold a
  permission, so the token is the gate — which makes `lintFossRelease` pass again.
- `VcfExporter`'s progress callback is a **constructor** parameter, leaving `exportContacts`'
  signature (and its detekt baseline entries, which key on the full signature) untouched.
- **Upstream sync:** `main` fast-forwarded onto the latest FossifyOrg/Contacts maintenance commits —
  AGP 9.2.1 → 9.3.0, KSP 2.3.8 → 2.3.10, Gradle wrapper 9.5.1 → 9.6.1, refreshed lint baselines, and
  Belarusian, Persian, French, Hungarian, Portuguese (Portugal) and Romanian translations. All 39
  fork commits replayed with no conflicts; upstream still pins Commons 6.1.6, so the patched
  `6.1.6-sk7` pin is unchanged.

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

---

# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.6.0] - 2026-01-30
### Added
- Added support for custom fonts

### Changed
- Tapping contact photo in lists now launches the contact details page ([#452])
- Updated translations

### Fixed
- Fixed incorrect spacing between prefix and last name ([#157])

## [1.5.0] - 2025-12-16
### Changed
- Updated translations

### Fixed
- Fixed invisible navigation bars in contact viewer ([#415])
- Fixed search highlighting for characters with accents and diacritics ([#12])

## [1.4.0] - 2025-10-29
### Changed
- Compatibility updates for Android 15 & 16
- Search query is now preserved when switching tabs
- Updated translations

## [1.3.0] - 2025-10-09
### Added
- Support for importing contacts from vCards shared by other apps ([#321])

### Changed
- Updated translations

### Fixed
- Fixed search not matching full phone numbers

## [1.2.5] - 2025-09-09
### Changed
- Updated translations

### Fixed
- Fixed contacts edits being silently discarded when using navigation arrow ([#360])

## [1.2.4] - 2025-07-31
### Changed
- Updated translations

### Fixed
- Fixed issue with contacts not displaying when syncing via DAVx⁵ ([#339])

## [1.2.3] - 2025-07-23
### Changed
- All contact exports now use the vCard 4.0 format
- Preference category labels now use sentence case
- Updated translations

### Fixed
- Filtering contacts now works correctly on the favorites tab ([#78])

## [1.2.2] - 2025-06-17
### Changed
- Updated translations

### Fixed
- Fixed invisible preferred number indicator in light themes ([#289])

## [1.2.1] - 2025-06-03
### Changed
- Updated translations

### Fixed
- Fixed crash on startup due to private contacts ([#281])
- Fixed crash when creating new contacts

## [1.2.0] - 2025-05-31
### Added
- Support for structured addresses ([#30])
- Dialog for choosing contact source when editing a merged contact ([#201])

### Changed
- Updated translations

## [1.1.0] - 2024-10-28
### Added
- Added an option to display formatted phone numbers
- Added a favorite button for contacts in groups

### Changed
- Replaced checkboxes with switches
- Other minor bug fixes and improvements
- Added more translations

### Removed
- Removed support for Android 7 and older versions

### Fixed
- Fixed issue with contacts not displaying on Android 14 and above
- Fixed data loss when deleting contacts with identical names
- Fixed corrupted automatic backups
- Fixed low-quality photo exports in vCards
- Fixed overlap between the floating action button and list items

## [1.0.1] - 2024-01-17
### Fixed
- Fixed vcf importer

## [1.0.0] - 2024-01-17
### Added
- Initial release

[#12]: https://github.com/FossifyOrg/Contacts/issues/12
[#30]: https://github.com/FossifyOrg/Contacts/issues/30
[#78]: https://github.com/FossifyOrg/Contacts/issues/78
[#157]: https://github.com/FossifyOrg/Contacts/issues/157
[#201]: https://github.com/FossifyOrg/Contacts/issues/201
[#281]: https://github.com/FossifyOrg/Contacts/issues/281
[#289]: https://github.com/FossifyOrg/Contacts/issues/289
[#321]: https://github.com/FossifyOrg/Contacts/issues/321
[#339]: https://github.com/FossifyOrg/Contacts/issues/339
[#360]: https://github.com/FossifyOrg/Contacts/issues/360
[#415]: https://github.com/FossifyOrg/Contacts/issues/415
[#452]: https://github.com/FossifyOrg/Contacts/issues/452

[Unreleased]: https://github.com/FossifyOrg/Contacts/compare/1.6.0...HEAD
[1.6.0]: https://github.com/FossifyOrg/Contacts/compare/1.5.0...1.6.0
[1.5.0]: https://github.com/FossifyOrg/Contacts/compare/1.4.0...1.5.0
[1.4.0]: https://github.com/FossifyOrg/Contacts/compare/1.3.0...1.4.0
[1.3.0]: https://github.com/FossifyOrg/Contacts/compare/1.2.5...1.3.0
[1.2.5]: https://github.com/FossifyOrg/Contacts/compare/1.2.4...1.2.5
[1.2.4]: https://github.com/FossifyOrg/Contacts/compare/1.2.3...1.2.4
[1.2.3]: https://github.com/FossifyOrg/Contacts/compare/1.2.2...1.2.3
[1.2.2]: https://github.com/FossifyOrg/Contacts/compare/1.2.1...1.2.2
[1.2.1]: https://github.com/FossifyOrg/Contacts/compare/1.2.0...1.2.1
[1.2.0]: https://github.com/FossifyOrg/Contacts/compare/1.1.0...1.2.0
[1.1.0]: https://github.com/FossifyOrg/Contacts/compare/1.0.1...1.1.0
[1.0.1]: https://github.com/FossifyOrg/Contacts/compare/1.0.0...1.0.1
[1.0.0]: https://github.com/FossifyOrg/Contacts/releases/tag/1.0.0
