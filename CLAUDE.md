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
- `BUILD_NUMBER` is **our** increment on top of that upstream version. It starts at `1` for the first build of a given upstream version. It is stored in `gradle.properties` as a plain int (`BUILD_NUMBER=79`) — the padding below is applied when the name is built, so the auto-increment stays a simple integer rewrite.
- **Every name the counter appears in pads it to three digits** (`+001`, `+079`) so version names, `~/tmp` file lists and release tags sort in build order instead of lexically (`+100` before `+79`). This is the global `/after-build` rule; `app/build.gradle.kts` renders it once into `forkVersionName`.
- Displayed version name = `VERSION_NAME+<BUILD_NUMBER padded to 3>` (e.g. `1.6.0+001`).
- Effective version code = `VERSION_CODE * 10000 + BUILD_NUMBER` (e.g. `13 * 10000 + 1 = 130001`), using the **unpadded** counter. The `* 10000` leaves room for many fork builds between upstream bumps while staying monotonically increasing.
- APK filename = `shiroikuma-renrakusaki_<VERSION_NAME>+<BUILD_NUMBER padded to 3>_arm64-v8a.apk` (e.g. `shiroikuma-renrakusaki_1.6.0+001_arm64-v8a.apk`).

`BUILD_NUMBER` auto-increments after every successful `./gradlew buildFoss` run (the task rewrites `gradle.properties`). So the committed `BUILD_NUMBER` always points at the **next** build to be produced.

### Building a new version

Every build with changes bumps the build number (+1 → +2 → …). Use the `build-apk` skill, or directly:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFoss < /dev/null
```

`buildFoss` builds the signed `fossRelease` APK, copies it to `~/tmp/<apk name>`, then bumps `BUILD_NUMBER`. After building, ask whether to `adb push` it to the phone (see the `build-apk` skill for why the push is done manually, not via the task's own prompt). Signing reads `keystore.properties` (gitignored) → `~/.android-keystores/shiroikuma-denwa.jks` (alias `denwa`) — the same key as denwa.

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
- **Delivery:** published to the local Maven repo, consumed as `commons = "6.1.6-sk18"` in
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

## The shared bottom bar — Favorites, Contacts, 電話, 臨電話

連絡先, 電話 (denwa, `shiroikuma.denwa`) and 臨電話 (Rindenwa, `shiroikuma.rindenwa`) show **the same four
tabs in the same order** (白い熊, 2026-11-17; here from `+123`), however each app was opened. Each owns
its own tabs and launches the others for the rest:

| tab | owner | how we open it |
| --- | --- | --- |
| Favorites, Contacts | us | pages of `MainActivity`; the others launch it with int extra `shiroikuma_open_tab` (`OPEN_TAB_INTENT_EXTRA`) = commons `TAB_FAVORITES` / `TAB_CONTACTS` |
| 電話 | denwa | `launchDialerApp(TAB_CALL_HISTORY)` (extensions/Activity.kt) |
| 臨電話 | Rindenwa | `launchRindenwa()` (helpers/Rindenwa.kt): `org.linphone.ui.main.MainActivity` with boolean extra `shiroikuma_open_history` |

- `barTabsList` / `pagerTabsList` (helpers/Constants.kt) are the bar and the pager. `TAB_RINDENWA` is a
  bar-only id of ours. A hand-over tab whose app is not installed is left out (`MainActivity.barTabs`).
- 電話 and 臨電話 are **launchers, not pages**: `tabSelectedAction` starts the other app with no animation
  and posts the selection back (`selectBarTabForPage`). We never `finish()`, so it is a warm task swap.
- **Groups has no tab.** It stays a pager page (`Config.showGroupsPage`, UI settings → Tabs), reached from
  ⋮ → Groups or by paging past Contacts; the bar then shows nothing selected.
- The old “wear the dialer's tab mask” model (`shiroikuma_dialer_tabs`) is gone and the extra is ignored;
  the *manage shown tabs* setting is retired (`showTabs` is forced to all).
- `<queries>` names `shiroikuma.denwa[.debug]` and `shiroikuma.rindenwa`.

## Calling through 臨電話 — routes, badges, swipes (`helpers/Rindenwa.kt`)

Rindenwa's call door is a ContentProvider driven by `ContentResolver.call()`, authority
`shiroikuma.rindenwa.calls` (its `SkCallsProvider`; gated by Rindenwa's own allow-list, we are on it by
default). **Keep the names in sync with Rindenwa.**

- `accounts` → `{result: "OK", accounts: <JSON array of {number, identity, name, registered, default,
  badge_color, badge_text_color, callback}>}`. `callback` (Rindenwa `+026`; absent = false) marks an account
  whose provider can set up a callback — today Odorik lines. `number` is the 臨 number (0–9, unique; -1 = none), shown as 臨0 … 臨9,
  a bare 臨 when unknown. The two colours (ARGB, since Rindenwa `+019`) are that account's own badge pair,
  set in Rindenwa's UI: every 臨N we draw — contact screen, account picker, swipe band — wears them
  (`rinBadgeColors`); our `ROUTE_RIN*` / `SWIPE_RIN*` slots are only the fallback when they are absent.
- `dial`, extras `{to, account?}` (a 臨 number as a string; absent = the account selected in Rindenwa)
  → `{result: "OK"}` or `ERROR:not allowed | no such account | not a number | no number | not ready`.
- `callback`, the extras of `dial` → `{result: "OK" | "ERROR:<why>", message}`: Odorik rings 臨電話 first and,
  once answered, rings the contact and joins the two. **It blocks for an HTTPS request (up to ~20 s)**; `OK`
  means the order was accepted, not that anything rang. `message` is Rindenwa's line to show, empty on
  `not ready` / `not allowed` / `unknown method: callback` (an older Rindenwa) — ours then (`rinErrorText`).
  Offered by a long-press on a 臨N badge whose effective account has the flag
  (`ViewContactActivity.showRinBadgeMenu`): pills *Callback*, *Call*, *Set 臨電話 account ›*. Any other 臨N
  keeps the long-press that opens the account picker directly.
- All of them cross processes: never on the main thread. The last account list is cached (`Config.rinAccountsCache`).

A **call route** is a string: `sim1`, `sim2`, `rin` (the 臨 default), `rin:N`, `off`. The 臨 default for a
contact is its own account (`Config.getRinAccount`, per number like the per-number SIM; set by a long-press
on the 臨 badge or selection menu → *Set 臨電話 account*), else `Config.rinDefaultAccount` (UI settings →
Call routes; -1 = as selected in 臨電話).

- **A contact's default route** (from `+131`; `contactDefaultRoute` / `setContactDefaultRoute` in
  helpers/Rindenwa.kt) is what it is called by when nothing names a route — a tap on its Favorites tile,
  the call button, a tap on one of its numbers: `sim1`, `sim2`, `rin` (through the default account),
  `rin:N`, or `off`. The SIM is the per-number slot denwa reads through `/sim_slot`; 臨 is a per-number flag
  beside it (`Config.getRinIsDefault`) plus the contact's own 臨 account, because denwa cannot place that
  call — **denwa itself does not know a 臨 default** and picks the SIM its own way. Picked from the
  selection menu's SIM icon (`dialogs/DefaultRouteDialog.kt`, `pickDefaultRoute`); the photo badge in the
  lists shows it: SIM 1 bottom-start, SIM 2 bottom-end, 臨N bottom-centre in its account's colours.
- **Contact screen:** each number carries badges 1 (red), 2 (blue) and 臨N (yellow ground, blue text) —
  `ViewContactActivity.setupRouteBadges`; a tap dials by that route. Switch and colours: UI settings →
  *Call routes* (`Config.routeBadges`, `ThemeSlot.ROUTE_*`).

## Swipe to call — shared with denwa through commons

Contacts and Favorites rows — and, from `+131`, tiles: the cells of the Favorites grid and of a
multi-column Contacts list (`Config.swipeTiles`, default on; commons **sk17** swipes a tile inside its own
cell) — swipe to call the contact's default number (`numberToCall()`). **Each side
dials by its own route** (`Config.swipeLeftRoute` / `swipeRightRoute`, default SIM 1 / SIM 2): UI settings →
*Swipe to call*. The swipe itself is **commons**, shared with denwa: `org.fossify.commons.helpers.SwipeToCallCallback`
and `RecyclerView.setupSwipeToCall` / `removeSwipeToCall` (tag `org.fossify.commons.R.id.swipe_to_call_tag`).
A change to the swipe is made once, in shiroikuma-commons, never re-ported here; **sk14** added the per-side
parameters (enabled, mark colour, glyph, text label) with defaults that keep denwa's sk13 behaviour.
Our side passes the look (`ContactsAdapter.SwipeSide`: `ThemeSlot.SWIPE_SIM1/2/TEXT`, `SWIPE_RIN`,
`SWIPE_RIN_TEXT`) and places the call: `callByRoute` (extensions/SwipeToCall.kt) — a SIM route calls through
denwa's DialerActivity with the SIM as `EXTRA_PHONE_ACCOUNT_HANDLE` (needs `READ_PHONE_STATE`), a 臨 route
goes through Rindenwa's call door. Master switch: `Config.swipeToCall`, default on.

## The UI page — folding sections, a chip index, an icon on every row (from `+131`)

`ThemeActivity.buildRows` registers each top-level section as a `UiSection(key, title, chip, fill)`. **The
keys are stored — never rename one.** `Config.uiSectionOrder` (keys, comma-joined) and
`Config.uiSectionsOpen` live in the main preferences, so they travel in the settings export. Every section
is folded unless its key is in the open set, and its rows are built only when it is first opened: the row
builders add to `body`, which points at a section's body only while that section's `fill` runs — **a row
added later (from a callback) must be given its parent explicitly.** The chip index (`addSectionIndex`)
jumps on a tap and reorders by a long-press drag; *Reset section order* is in Maintenance. A row's icon is
a `RowIcon` (drawable + menu icon role), set as the label's start drawable; each builder has a default by
row type, and a call site names another where the row's meaning is clearer.

Switches and the + button are drawn by commons (sk17) under `pillDialogButtons`: the outlined switch and
the outlined floating button, coloured from their own commons roles (see *Colours* below). A confirmation
that deletes passes `destructive = true` and is drawn in `UiRole.BUTTON_DESTRUCTIVE`.

## Colours — nothing in XML, every item a role (from `+133`, commons sk18)

白い熊, 2026-11-21: `#FFFF00` and `#000000` are defaults only, and every item's colour is set on the UI page.

- **No colour is written in XML** — not in this app's layouts, drawables or styles, and not in the commons'
  (its `colors.xml`, the `md_` palette, the stock per-colour / white / system themes and *Customize colors*
  are deleted). A new layout or drawable must not name one: build the shape in code
  (commons `extensions/UiRoleViews.kt`: `rowSelectorDrawable`, `rippleDrawable`, `circleDrawable`,
  `listDividerDrawable`, `menuPanelDrawable`, `sectionFrameDrawable`, …; `@layout/divider` paints itself).
- **Two role tables.** Commons `helpers/UiRoles.kt` (`UiRole`, sections `UiRoleSection`) holds everything
  the commons draws — foundation, text, fields, buttons, dialogs, switches, marks, menus, lists, bars,
  toasts — and is where the two primaries are written, once. `ThemeSlot` (extensions/ThemeColors.kt) holds
  this app's own items; a slot with `role = UiRole.X` **is** that commons role under our label and section
  (stored in the role's key `ui_role_<key>`; slot overrides from before `+133` are moved once,
  `migrateSlotsToRolesIfNeeded`). Read with `uiColor(role)` / `themeColor(slot)`.
- **The UI page** shows the commons roles no slot stands for in sections `roles_<section>` (Text, Text
  fields and pickers, Dialogs, Switches, Check boxes and radio buttons, Bars, Toasts) and inside Buttons,
  Menus and Lists (`ThemeActivity.addRoleRows`). A colour picker's *default* button resets one role;
  Maintenance → *Reset all colours* resets all (`resetAllThemeColors`). All keys travel in the export.
- **Stock widgets get their roles as they are inflated**: commons `views/FamilyViewInflater` is the theme's
  `viewInflaterClass`. An icon keeps a neutral placeholder fill in its vector file and is tinted there
  from the text role (on the drawable, never the view), unless a screen gives it its own role afterwards.
- **What stays in XML, and why:** the black `android:windowBackground` of `AppTheme.Base` (the platform
  draws it before any code runs; the activity repaints it from the role); the launcher icon variants'
  grounds (`mipmap-anydpi-v26/ic_launcher_*.xml`, drawn by the launcher); commons
  `widget_round_background` (a home-screen widget's shape); `?attr/selectableItemBackground` in layouts
  (a shape, recoloured in code from `UiRole.LIST_PRESSED`); multi-colour pictures (brand marks, flags).
- **The date picker is ours** (from `+134`): commons `views/FamilyDatePicker` — a month grid drawn in code
  (ring on the chosen day, never a filled disc; a short line under today; arrow pills, the year stepping by
  1 and by 10), every part from a `UiRole.PICKER_*` role (UI page → Dialogs). The platform `DatePicker`
  draws its day grid from theme attributes with no setter and must not be used in a layout.

## Menus — pills with coloured icons

Every popup menu row (toolbar ⋮, selection-menu overflow, submenus) is drawn by `views/PillMenuItemView`,
swapped in through our copies of AppCompat 1.7.1's `abc_popup_menu_item_layout.xml` and
`abc_cascading_menu_item_layout.xml` (diff them when AppCompat is upgraded). `helpers/MenuPills.ROWS`
gives each item id its role (icon colour: `ThemeSlot.MENU_ICON_*`, `MENU_WARNING`; UI settings → *Menus*),
whether it opens something (›) and an icon when the menu resource has none. **A new menu item needs a
line there.** `androidx/appcompat/view/menu/ForkMenuAccess.kt` exists only to read `MenuItemImpl.mMenu`.

## Call history on the contact screen — denwa's look, served by denwa

`ViewContactActivity.setupCallHistory` lists the contact's latest calls (`Config.contactCallHistoryCount`,
default 10; switch `Config.contactCallHistory`, UI settings → *Call history on the contact screen*) under
its fields, drawn like denwa's Recents: `item_contact_call.xml` / `item_contact_call_day.xml` are denwa's
`item_recent_call` / `item_recents_date` without avatar, name line and ⋮ — the shared three-line row less its
name (from `+127`): *number · label* with ✉ (writes to that number) and ☎ (calls back on the SIM that call
used) at its right end, then SIM badge, arrow, time · duration and country. The thin line between two calls
is inset 16 dp and left out under a day's last call. Style keys read for it: `color.number`, `.number_label`,
`.duration`, `.country`, `.action_message`, `.action_call`, `.unanswered`, `font.number.*` / `.duration.*` /
`.country.*`, `number_scale`; an older denwa that serves none falls back to its `subtitle` slot. The calls come straight from the
system call log (`helpers/ContactCallHistory.kt`: suffix match, denwa's SIM lookup, libphonenumber geocoder
for the place); the time / duration / imperial-date formats are commons sk13's `CallFormats.kt`, shared with
denwa (`CALL_TIME_*` / `CALL_DURATION_*` are the ordinals denwa's style provider serves).

The look is **not** a setting of ours: denwa serves it from `<denwa id>.calllogstyle` (gated to our
package) — `/style` key/value rows (colours, thicknesses, `font.<slot>.*`, `text_size_px`, formats) and
`/font/<file>` for its imported fonts (cached in `cacheDir/denwa-fonts`). `helpers/DenwaCallLogStyle.kt`
reads it each time and falls back to denwa's defaults. The pill *All calls in 白い熊 電話 ›* and a tap on a
call open denwa's Recents with `shiroikuma_recents_filter_numbers` (ArrayList<String>) and
`shiroikuma_recents_filter_name`. Both contracts are documented on denwa's side too — keep the key names
in sync.

### Call stamps — an old call keeps the badge of the card it was made with (from `+129`)

The call log records only the slot, so a badge worked out from it shows a swapped or recoloured card's
colours on old calls. denwa (from `1.11.1+120`) keeps a **stamp** per call and serves them from the same
provider and allow-list (a pure addition, style `version` stays 1):

- `content://<denwa id>.calllogstyle/stamps`; selection arguments (optional) = the contact's numbers as
  stored, which narrow the cursor to calls with them (compared as phone numbers); none = all stamps.
- Columns: `date` (long, the call log's `DATE`), `number` (as logged), `stamp` (JSON).
- Badge keys: `digit` (int; 0 = no badge), `color` (ARGB fill), `text` (ARGB digit colour), `bold`.
  Others, for an eventual detail dialog like denwa's: `src`, `num`, `op`, `slot`, `end`, `ring`, `net`,
  `roam`, `roam_op`, `roam_iso`, `route`, `dbm`, `lvl`, `cid`. An absent key means “not known”.

`withCallStamps` (helpers/DenwaCallLogStyle.kt) queries it once per contact screen and attaches a
`CallBadge` to each `ContactCall` matched on **date plus number**; `callRow` draws a stamped call's badge
from the stamp alone. No stamp, `digit` 0 or an older denwa without the path → the slot plus `color.sim1` /
`color.sim2`, as before. ☎ still calls back by the slot. The contract is denwa's (“Call stamps — a call
keeps the SIM it was made through” in its CLAUDE.md) — **keep the key names in sync.**

## Private contacts: the provider gate and the visible copies

Private contacts (“Phone storage (not visible by other apps)”, source `SMT_PRIVATE`) live only in our
Room DB. Two contracts with sister apps hang off them — **keep both stable**.

### The provider — who may read private contacts

`contentproviders/MyContactsContentProvider` serves them at
`content://${applicationId}.contactsprovider/contacts` (plus `/sim_slot` for denwa's per-contact SIM).
Consumers: denwa (its own `getPrivateContactsCursor()`), and every fork on commons ≥ `6.1.6-sk10`, whose
`getMyContactsCursor()` asks `shiroikuma.renrakusaki[.debug].contactsprovider` before the stock
authority (Messēji, yotehyo). **Never rename the authority or the `contacts` path.**

- **No `readPermission`.** The signature-level `READ_PRIVATE_DATA` was dropped in `+091`: Messēji is
  signed with a different key (`shiroikuma-messages.jks`), so a signature lock could never admit it.
- **The gate is `query()` itself:** `callingPackage` (vouched for by the framework) must be our own
  package or in `Config.privateDataApps` — UI settings → Private contacts → *Apps allowed to read them*.
  A `.debug` caller is admitted when its release id is. Defaults: `DEFAULT_PRIVATE_DATA_APPS` =
  denwa, messeji, yotehyo, rindenwa. A refused caller gets `null`, for contacts and `sim_slot` alike.
- **Photos:** a private contact keeps its picture as bytes in Room (its stored `photo_uri` is always
  empty), so the cursor's `photo_uri` points back into the provider —
  `content://…contactsprovider/photo/<private id>?v=<hash of the bytes>` — and `openFile` serves it
  (same gate, `getType` = `image/jpeg` so `loadThumbnail` works). Without it denwa showed placeholders.
- 臨電話 (`shiroikuma.rindenwa`) is a default since `+123`, and was added once to lists stored before that
  (`Config.addRindenwaToPrivateDataAppsOnce`).
- **The call-route door** (from `+133`; Messēji asked for it, so one contact has one route everywhere):
  `ContentResolver.call()` on the provider, same gate. `get_route` (arg or extra `number`) →
  `{result: "OK", route: sim1 | sim2 | rin | rin:N | off}`; `set_route`, extras `{number, route}` →
  `{result: "OK"}` (`off` clears). Errors `ERROR:not allowed | no number | bad route | failed`. Any other
  method — and these two on an older 連絡先 — return `null`, which is how a caller tells “no route set”
  from “no door”. It works per number (`numberDefaultRoute` / `setNumberDefaultRoute`, helpers/Rindenwa.kt),
  matching stored keys as phone numbers, and bumps `contactsListRevision`. **Keep the names in sync with
  Messēji.**
- **Change notification:** `helpers/PrivateContactsWatch` calls `notifyChange` on
  `content://<our id>.contactsprovider/contacts` after any write to the private contacts table (Room's
  invalidation tracker, re-attached whenever the database singleton is rebuilt). Rindenwa observes it.
- The list is device-local: `PRIVATE_DATA_APPS` is in `SettingsExport.PREFS_EXCLUDE`.
- The picker lists launchable apps, hence the `MAIN`/`LAUNCHER` `<intent>` in `<queries>`.

### “Visible to other apps” — a system copy of a private contact

`helpers/SystemMirror` writes a **copy** (never a move) of a private contact into a system account,
so apps that read only ContactsContract (WhatsApp/Signal discovery, the car's PBAP phonebook, Android
Auto, the system call log) know the name. The private record stays the one that is edited.

- **The marker is the contract:** the copy's raw contact has
  `RawContacts.SYNC1 = "shiroikuma.renrakusaki:<private local id>"`. There is no other flag — a private
  contact is visible exactly when such a raw contact exists with `DELETED = 0`. denwa
  (`extensions/RenrakusakiCopies.kt`, from `1.11.1+095`) drops these copies from its lists by the same
  tag, but only when the private original was actually read from our provider. **Never change the tag.**
- **Content:** StructuredName + Phone rows, plus Photo when `Config.mirrorPhoto` (default on). Nothing
  else — every READ_CONTACTS app sees the copy.
- **Account:** `Config.mirrorAccount`, or automatically the phone's local account
  (`com.android.huawei.phone`, else any `phone`/`local` type, else account-less). Changing it
  recreates all copies there.
- **Badges and the three-way switch:** list photos carry a crossed-out lock (system) or an eye (private +
  visible), none for private-only (`setupVisibilityBadge`, cached ids `SystemMirror.cachedMirroredIds`).
  `dialogs/ChangeVisibilityDialog` switches System / Private / Private + visible — opened by a long-press
  on a list photo and by the “Visibility: …” row on the contact screen. System ⇄ Private is a real move
  (insert into the other store, delete the old record, no confirmation — nothing is lost); Private ⇄ visible only adds
  or removes the copy.
- **Lifecycle:** toggled from the contact screen's ⋮ (*Visible to other apps*), the dialog above, or in
  bulk from the selection menu; rewritten after every save of the private contact (`EditContactActivity.updateContact`);
  orphans (original deleted or moved to another source) are deleted on the next list load.
- **Our own lists must never show the copy.** Every load goes through `Context.getOwnContacts()`
  (extensions/Context.kt) instead of `ContactsHelper.getContacts()`; it drops copies and, if duplicate
  merging kept the copy over the original, puts the original back. `getDuplicateContacts()` skips copies
  too. **New code that lists contacts must use `getOwnContacts()`.**
- **vCard round trip:** export writes `X-SHIROIKUMA-VISIBLE:1` on a visible private contact; importing
  such a card into private storage recreates its copy.

### Every field, in the contact itself — `Contact.extra` (commons `sk11`)

Fossify's model had no reading, relations, SIP addresses, organization details, event labels or
send-to-voicemail, and silently dropped many columns. Commons `sk11` adds `Contact.extra`
(`ContactExtraData`) and a matching `extra` column in the private DB (Room 3 → 4 migration), so a
contact is complete in **both** stores and a move is a plain copy:

- commons `ContactExtraIO` reads it for a system contact (`getContactWithId`, `fillExtras` for exports)
  and writes it after every insert and every update **whose extra was loaded** (`extra.loaded`) — a
  list-row contact updated without its extras must never wipe them;
- first-class and editable here (`helpers/MoreFieldsEditor`, view: `ViewContactActivity.setupMoreFields`):
  reading, department / job description / office location / company reading / symbol, relationships,
  SIP addresses, event custom labels, send to voicemail; everything else is kept verbatim in
  `extra.rows` (patched back onto the matching row, unknown kinds re-inserted) and listed read-only;
- vCard: `helpers/VcfExtra` writes `X-SHIROIKUMA-EXTRA` (base64 JSON, authoritative on import) plus
  standard RELATED / X-SIP / ORG unit / ROLE / X-PHONETIC-ORG… for other readers;
- groups move by title (`helpers/GroupMapping.kt`);
- `helpers/PrivateExtras` is now only the one-time migration of the `+097`/`+098` side file into the
  contacts (run from `App.onCreate`).

### Duplicates: the list merges by name, so state changes and cleanup act on the whole row

With “merge duplicate contacts” on, one list row stands for every entry (system and private, shown
sources) with the same display name. 白い熊's phone holds most contacts twice (an old double import), so:

- `ChangeVisibilityDialog.membersOf` finds every entry behind a row and applies the chosen state to each;
  the tick shows the row's common state, none when mixed (`stateOfGroup`).
- UI settings → Maintenance → *Clean up duplicates* (`activities/DuplicatesActivity`,
  `helpers/DuplicateCleaner`): exact copies (same content, store and account; name split ignored) go in
  one tap, keeping star and groups; differing groups offer merge-into-one (union of everything), keep
  one, or “not duplicates” (`Config.notDuplicateNames`). Every change first writes a vCard backup to
  `Android/data/<app>/files/cleanup-backups/`.

### Keeping system storage empty — `helpers/SystemStorage`, `helpers/ContactMover`

白い熊 keeps every contact private (2026-11-03: everything moved, SIM cards emptied). UI settings →
Maintenance → *System storage*:

- a live status line (raw contacts per account, entries per SIM card, pending deletes; visible copies
  excluded) with a *Check again* pill;
- *Move every contact to private* (`SystemStorage.moveEverythingToPrivate`): `ContactMover` moves each
  system contact (backup first), then `clearSimCards` empties the cards through
  `content://icc/adn/subId/N` (probing 1–8; no READ_PHONE_STATE) — Huawei only marks a SIM account's
  deleted entries deleted and never writes that to the card — saving any card entry whose number no
  private contact has, and `purgeDeleted` removes the delete marks (as a sync adapter);
- *New contacts are private* (`Config.newContactsPrivate`, default on): editor and vCard import default
  to private storage;
- *Keep the phone clean automatically* (`Config.autoKeepClean`, default off): `MainActivity.onResume`
  runs the full move when the status is not clean.

`ContactMover` is the single implementation of a System ⇄ Private move (also used by
`ChangeVisibilityDialog`); it hands over the entry's OWN photo, never the aggregate's photo URI.

## Commit convention — no Claude attribution

Do **not** add any `Co-Authored-By: Claude …` trailer — nor a "🤖 Generated with Claude Code" / Anthropic-attribution line — to commit messages or PR bodies in this repo. 白い熊 does not want Claude attribution in the history; this **overrides** the harness's default to append such a trailer. End commit messages at the last line of the body. (The existing history was scrubbed of these trailers on 2026-06-08; the global rule lives in `~/.claude/CLAUDE.md`.)
