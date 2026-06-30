<div align="center">

<img src="graphics/icon.webp" width="120" alt="白い熊 連絡先 icon" />

# 白い熊 連絡先

**A black-and-yellow, deeply customizable contacts app — your list, your layout, your colors.**

A fork of [Fossify Contacts](https://github.com/FossifyOrg/Contacts) with **major additions**: a granular black/`#FFFF00` theming system, a fully configurable multi-column contacts list, per-contact default SIM, and direct tab hand-off from the matching Phone fork.

Installs **side-by-side** with Fossify Contacts (app id `shiroikuma.renrakusaki`) — keep both.

**📥 Latest release: [`1.6.0+44`](https://github.com/ShiroiKuma0/shiroikuma-renrakusaki/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-renrakusaki/releases)

</div>

---

## 🎨 Granular black & yellow theming
A full Theme & Colors system that defaults to pure black `#000000` + pure yellow `#FFFF00` (not material yellow). Every accent is yours to set: the top bar, the search action icon, the overflow menu, and the contact phone-number color each get their own control. The color pickers gain an **alpha slider**, a **recently-used colors** row, and **per-element font** selection. A matching launcher icon — a yellow-traced figure on black — completes the look.

---

## 📇 A contacts list you actually lay out
The list stops being a fixed single column. **Choose which fields show, reorder them, and arrange them into 1–4 columns** with one-tap 一二三四 buttons — each field carries its own font, size, and color. Set the **thumbnail size with a live preview**, dial in **row spacing**, add **dividers** (including between columns), and insert a configurable column spacer for left-flowing layouts. Photo-less contacts get a clean custom 人 placeholder instead of a blank avatar.

---

## 📱 Per-contact default SIM
On a dual-SIM phone, pin a preferred SIM to any contact: a **SIM badge** shows it right in the list, a **picker** (from the long-press menu) sets it, and a **content provider** exposes the choice so the companion dialer auto-selects the right SIM when you call.

---

## 🔗 Tab hand-off from the Phone fork
Our companion Phone fork (`shiroikuma.denwa`) launches this app straight to the **Contacts** or **Favorites** tab — tap a tab there and you land exactly where you expect here, with no extra hop.

---

## 🧩 Side-by-side, no nags
A distinct app id (`shiroikuma.renrakusaki`) lets this run alongside the official build. It links against a **patched Fossify Commons** (`6.1.6-sk5`) that strips upstream's anti-tamper "fake version" / sideloading dialog and adds black/yellow CAB & menu fixes — so the fork runs clean instead of nagging that it's "fake."

---

## Built on Fossify Contacts
A fork of [Fossify Contacts](https://github.com/FossifyOrg/Contacts) (app id `shiroikuma.renrakusaki`, so it coexists with the official build). Fossify Contacts is a privacy-first, open-source contact manager with no ads and no trackers — this fork keeps that foundation intact and only adds on top. The code remains under the **GPL-3.0** license.

## Building
```bash
git clone https://github.com/ShiroiKuma0/shiroikuma-renrakusaki
cd shiroikuma-renrakusaki
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFoss
```
Builds the signed `fossRelease` APK and copies it to `~/tmp/`. The build links against our patched Fossify Commons (`6.1.6-sk5`), published to your local Maven repo (`mavenLocal`) from the [`shiroikuma-commons`](https://github.com/ShiroiKuma0/shiroikuma-commons) fork.
