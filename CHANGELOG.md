# Changelog — 白い熊 連絡先

This is a fork of [Fossify Contacts](https://github.com/FossifyOrg/Contacts). It tracks an upstream
release and layers our customizations on top; versions are `<upstream version>+<fork build>`. This
file documents what the fork adds on top of stock — see upstream's own changelog for the base app.

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
