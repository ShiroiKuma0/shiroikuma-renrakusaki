<div align="center">

<img src="graphics/icon.webp" width="120" alt="白い熊 連絡先 icon" />

# 白い熊 連絡先

**A black-and-yellow, deeply customizable contacts app — your list, your layout, your colors.**

A fork of [Fossify Contacts](https://github.com/FossifyOrg/Contacts) with **major additions**: Japanese-aware gojūon sorting with letter sections, a 詳 detail mode showing each contact's last call & message, a granular black/`#FFFF00` theming system, a fully configurable multi-column contacts list, tap-to-dial on the Favorites grid while the car is connected, per-contact default SIM, category export/import, and a headless backup the companion task runner drives — including a data door that lets this app be restored, contacts and all, onto a wiped phone.

Installs **side-by-side** with Fossify Contacts (app id `shiroikuma.renrakusaki`) — keep both.

**📥 Latest release: [`1.6.0+090`](https://github.com/ShiroiKuma0/shiroikuma-renrakusaki/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-renrakusaki/releases)

</div>

---

## 🎨 Granular black & yellow theming
A full Theme & Colors system that defaults to pure black `#000000` + pure yellow `#FFFF00` (not material yellow). Every accent is yours to set: the top bar, the search action icon, the overflow menu, and the contact phone-number color each get their own control. The color pickers gain an **alpha slider**, a **recently-used colors** row, and **per-element font** selection. Even the icons drawn **on top of a contact's photo** — the back arrow, edit, share and delete up top, the favorite/call/SMS row below — are painted in the accent over a **halo in the icon's own shape**, so they never vanish into a solid-yellow placeholder; the halo's color and thickness are yours too. A matching launcher icon — a yellow-traced figure on black — completes the look.

---

## 📇 A contacts list you actually lay out
The list stops being a fixed single column. **Choose which fields show, reorder them, and arrange them into 1–4 columns** with one-tap 一二三四 buttons — each field carries its own font, size, and color. A name is a field like any other, and it comes in four shapes to pick between — `Lastname, Firstname`, `Firstname Lastname`, `Firstname LASTNAME`, `LASTNAME Firstname` — so sorting by surname no longer dictates how a name is written. Set the **thumbnail size with a live preview**, dial in **row spacing**, add **dividers** (including between columns), and insert a configurable column spacer for left-flowing layouts. Photo-less contacts get a clean custom 人 placeholder instead of a blank avatar.

---

## 🇯🇵 Japanese-aware sorting & letter sections
Contacts sort and group the way a Japanese address book should: kana readings bucket into **gojūon rows** (あ か さ た な は ま や ら わ — voiced, semi-voiced and small kana folded into their base row), Latin names follow A–Z, and everything else lands in ＃. The provider's **phonetic reading (フリガナ)** drives it all. The editor gives it **a field per name item** — 名のフリガナ, ミドルネームのフリガナ, 姓のフリガナ, one per phonetic column the provider actually stores, on a brand-new contact as much as an old one — and the contact screen shows the reading under the name, in whatever order names are displayed in. On top of that come **per-contact sort-field overrides** (reading, nickname, organization) and fully themeable **letter-section headers** (underline, dividers, padding, colors). Readings and overrides **survive a backup and restore**: the reading is written into every exported vCard (as `SORT-AS` and as the `X-PHONETIC-*` properties Android itself uses) and read back on import, so a new phone rebuilds the same gojūon order rather than scattering your kana contacts into ＃.

---

## ✍️ An editor that unfolds
The contact editor stays short — the fields you actually fill in — with a **pill at the bottom that unfolds every field the "shown fields" mask hides**: prefix, middle name, suffix, nickname, IM, website, ringtone, each with its own フリガナ field. It lasts for that one screen and never rewrites your saved mask, so the next contact opens short again.

---

## 📋 詳 detail mode: last call & last message at a glance
Flip the list into detail mode and every contact shows its **most recent call and SMS**, read straight from the system providers — numbers matched by trailing digits so country-code formats can't hide a match. Timestamps come in your choice of format: Japanese readings (午前九時), the system format, or plain 24-/12-hour clocks, with custom patterns for today / this year / older.

---

## 📦 Export / Import
One ZIP holds everything: `settings.json` with every preference (colors, fonts, layout, options), your imported font files, and your **contacts as .vcf**. A single panel does the choosing — tick whole categories or just their **sub-options**, then export or import; a remembered folder and the last-export time sit on the page itself. Backups are named `shiroikuma-renrakusaki_2026-07-25_18-58-23.zip`, so they sort by when you took them, and the build that wrote one is recorded inside.

**A restore brings back what the app knows about a contact, not just the contact.** vCard has no room for a favourite, a custom ringtone, or any date but a birthday and an anniversary — so those are written as properties of our own and read back on import: your **Favorites tab comes back populated**, ringtones return (and a ringtone whose track no longer exists on the new phone is dropped rather than left ringing silently), 「その他」 dates survive, and the フリガナ rebuilds the same gojūon order. A contact with no phone number is never left out of a backup — the setting about what the *list* shows no longer decides what a file gets.

---

## 🤖 Headless backup, and a door that survives a wipe
Broadcasts let the companion task runner (白い熊 自由作業盤) back this app up without ever opening it: it asks for the **category list** — each item answering for itself whether it **starts ticked** — then triggers the **same export the panel runs**, headlessly, into any absolute path, and gets back the written file's path and real size. While it works, it reports **real counts, never a percentage** (`連絡先 123/456`), and a **cancel** stops it for real: the run unwinds at the next entry boundary, deletes what it had written, and answers the original request, so a cancelled backup leaves the folder exactly as it found it. A separate `BACKUP_CONTACTS` broadcast still does a straight .vcf dump before risky system operations — born from a real incident, when an EMUI locale switch wiped every contact.

**The authorization token is now optional, and off by default.** A pasted secret cannot survive a wipe, which is precisely the situation this is for. Automation is on out of the box; if you want the old behavior, 「認証トークンを使う？」 in the Export / Import section turns the token back on, and the token itself only appears once you ask for it.

**A wiped phone can now be put back.** Alongside the broadcasts sits a **data door** — a provider the app-manager fork (白い熊 応用管理) calls to describe what this app holds, stream an export straight into the backup it is assembling, and put it back afterwards. It writes into a **file handle the caller opens**, so the backup stays encrypted and checksummed as a whole rather than having a stray plaintext file dropped into it. Nothing anonymous gets in: a caller is checked by **exact package name, kernel-reported uid, and a pinned signing certificate**, and **restoring is only ever possible through that door** — never over a broadcast any app on the phone could send.

---

## 📱 Per-contact default SIM
On a dual-SIM phone, pin a preferred SIM to any contact: a **SIM badge** shows it right in the list, a **picker** (from the long-press menu) sets it, and a **content provider** exposes the choice so the companion dialer auto-selects the right SIM when you call.

---

## 🚗 In the car, a favourite is a call button
Driving with Android Auto on the head unit, the Favorites grid is the screen you reach for — and every tap on it used to open a contact, leaving a second, smaller target to aim at. **A tap now places the call.** Long-press opens the contact instead, and multi-select moves to the toolbar, so nothing is lost.

Only where it should be: **Never / Only while the car is connected / Always**, defaulting to the middle one, because the same grid at the desk is a wall of faces where a stray tap must not ring anybody. The car is recognised by reading Android Auto's own connection provider, and the answer is taken fresh at every tap — plug in, and the grid already on screen changes its mind.

**No dialog ever appears at dial time.** Which number rings is settled in advance and stored as Android's own default-number flag, so the companion dialer, the system and Android Auto's dialer all call the number this app would. A contact with several numbers is asked about the moment it becomes a favourite, and the Favorites overflow carries a re-runnable sweep for the ones that were never asked. The call is handed to the companion dialer by name — which is what keeps the per-contact SIM badge honest, since the SIM on the tile is the SIM that dials.

---

## 🔗 Tab hand-off from the Phone fork — and the dialer's own bar
Our companion Phone fork (`shiroikuma.denwa`) launches this app straight to the **Contacts** or **Favorites** tab — tap a tab there and you land exactly where you expect here, with no extra hop. And when you arrive that way, **this app wears the dialer's bottom bar**: Contacts | Favorites | **Recents**, built from the dialer's own visible tabs so the two bars match tab for tab. Recents is always one tap away instead of vanishing behind our bar, and tapping it swaps back with no animation and nothing shut down — the two apps trade places like tabs of one app. Groups steps aside for that visit only; opened from its own launcher icon, this app looks exactly as it always did.

---

## 🧩 Side-by-side, no nags
A distinct app id (`shiroikuma.renrakusaki`) lets this run alongside the official build. It links against a **patched Fossify Commons** (`6.1.6-sk7`) that strips upstream's anti-tamper "fake version" / sideloading dialog and adds black/yellow CAB & menu fixes, styled toasts, and a stale-source import fallback — so the fork runs clean instead of nagging that it's "fake."

---

## Built on Fossify Contacts
A fork of [Fossify Contacts](https://github.com/FossifyOrg/Contacts) (app id `shiroikuma.renrakusaki`, so it coexists with the official build). Fossify Contacts is a privacy-first, open-source contact manager with no ads and no trackers — this fork keeps that foundation intact and only adds on top. The code remains under the **GPL-3.0** license.

## Building
```bash
git clone https://github.com/ShiroiKuma0/shiroikuma-renrakusaki
cd shiroikuma-renrakusaki
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFoss
```
Builds the signed `fossRelease` APK and copies it to `~/tmp/`. The build links against our patched Fossify Commons (`6.1.6-sk7`), published to your local Maven repo (`mavenLocal`) from the [`shiroikuma-commons`](https://github.com/ShiroiKuma0/shiroikuma-commons) fork.
