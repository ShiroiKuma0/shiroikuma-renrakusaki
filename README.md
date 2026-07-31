<div align="center">

<img src="graphics/icon.webp" width="120" alt="白い熊 連絡先 icon" />

# 白い熊 連絡先

**A black-and-yellow, deeply customizable contacts app — your list, your layout, your colors.**

A fork of [Fossify Contacts](https://github.com/FossifyOrg/Contacts) with **major additions**: Japanese-aware gojūon sorting with letter sections, a 詳 detail mode showing each contact's last call & message, a granular black/`#FFFF00` theming system, a fully configurable multi-column contacts list, per-contact default SIM, category export/import, and a headless, token-gated backup the companion task runner drives.

Installs **side-by-side** with Fossify Contacts (app id `shiroikuma.renrakusaki`) — keep both.

**📥 Latest release: [`1.6.0+76`](https://github.com/ShiroiKuma0/shiroikuma-renrakusaki/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-renrakusaki/releases)

</div>

---

## 🎨 Granular black & yellow theming
A full Theme & Colors system that defaults to pure black `#000000` + pure yellow `#FFFF00` (not material yellow). Every accent is yours to set: the top bar, the search action icon, the overflow menu, and the contact phone-number color each get their own control. The color pickers gain an **alpha slider**, a **recently-used colors** row, and **per-element font** selection. A matching launcher icon — a yellow-traced figure on black — completes the look.

---

## 📇 A contacts list you actually lay out
The list stops being a fixed single column. **Choose which fields show, reorder them, and arrange them into 1–4 columns** with one-tap 一二三四 buttons — each field carries its own font, size, and color. Set the **thumbnail size with a live preview**, dial in **row spacing**, add **dividers** (including between columns), and insert a configurable column spacer for left-flowing layouts. Photo-less contacts get a clean custom 人 placeholder instead of a blank avatar.

---

## 🇯🇵 Japanese-aware sorting & letter sections
Contacts sort and group the way a Japanese address book should: kana readings bucket into **gojūon rows** (あ か さ た な は ま や ら わ — voiced, semi-voiced and small kana folded into their base row), Latin names follow A–Z, and everything else lands in ＃. The provider's **phonetic reading (フリガナ)** drives it all — editable right in the contact editor — with **per-contact sort-field overrides** (reading, nickname, organization) and fully themeable **letter-section headers** (underline, dividers, padding, colors).

---

## 📋 詳 detail mode: last call & last message at a glance
Flip the list into detail mode and every contact shows its **most recent call and SMS**, read straight from the system providers — numbers matched by trailing digits so country-code formats can't hide a match. Timestamps come in your choice of format: Japanese readings (午前九時), the system format, or plain 24-/12-hour clocks, with custom patterns for today / this year / older.

---

## 📦 Export / Import
One ZIP holds everything: `settings.json` with every preference (colors, fonts, layout, options), your imported font files, and your **contacts as .vcf**. A single panel does the choosing — tick whole categories or just their **sub-options**, then export or import; a remembered folder and the last-export time sit on the page itself. Backups are named `shiroikuma-renrakusaki_2026-07-25_18-58-23.zip`, so they sort by when you took them, and the build that wrote one is recorded inside.

---

## 🤖 Headless backup, driven by automation
Three token-gated broadcasts let the companion task runner (白い熊 自由作業盤) back this app up without ever opening it: it asks for the **category list** — each item answering for itself whether it **starts ticked** — then triggers the **same export the panel runs**, headlessly, into any absolute path, and gets back the written file's path and real size. While it works, it reports **real counts, never a percentage** (`連絡先 123/456`), and a **cancel** stops it for real: the run unwinds at the next entry boundary, deletes what it had written, and answers the original request — so a cancelled backup leaves the folder exactly as it found it instead of quietly finishing. A separate `BACKUP_CONTACTS` broadcast still does a straight .vcf dump before risky system operations. Every reply travels as a plain broadcast, the one ACK channel that survives EMUI's broadcast mangling — born from a real incident, when an EMUI locale switch wiped every contact.

---

## 📱 Per-contact default SIM
On a dual-SIM phone, pin a preferred SIM to any contact: a **SIM badge** shows it right in the list, a **picker** (from the long-press menu) sets it, and a **content provider** exposes the choice so the companion dialer auto-selects the right SIM when you call.

---

## 🔗 Tab hand-off from the Phone fork
Our companion Phone fork (`shiroikuma.denwa`) launches this app straight to the **Contacts** or **Favorites** tab — tap a tab there and you land exactly where you expect here, with no extra hop.

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
