# KoveLink

Android aplikacija, ki zaslon telefona (Google Maps, Waze, Kurviger, Calimoto …) prenaša na vgrajeni 7" TFT zaslon motorjev **Kove 800X Pro / 800X Rally / 800 GT (2024–2026)** – in ki navigacijo Google Maps prikazuje na TFT tudi takrat, ko je **telefon zaklenjen v žepu**.

KoveLink je izpeljanka (fork) odprtokodnega projekta [nakturk/kovemirror](https://github.com/nakturk/kovemirror). Celoten protokol BLE + Wi‑Fi/TCP in vgrajeni zemljevid sta delo H. Nakturka; KoveLink dodaja spodnje spremembe. Neuradna aplikacija, ni povezana s podjetjem Kove.

> **English:** fork of KoveMirror with (1) Google Maps / Waze turn‑by‑turn shown on the TFT while the phone is locked, read from the navigation notification, (2) TCP servers that accept only the bike (no more streaming the phone screen to anyone on a shared Wi‑Fi), (3) foreground‑service `location` type so GPS keeps flowing when locked, (4) Slovenian translation, (5) frame‑rate presets for battery, (6) Play‑policy manifest clean‑up and a GitHub Actions build. Details in [CHANGELOG.md](CHANGELOG.md) and [IZBOLJSAVE.md](IZBOLJSAVE.md).

---

## Kaj je novega v primerjavi s KoveMirror

| | KoveMirror | KoveLink |
|---|---|---|
| Google Maps na TFT, telefon **odklenjen** | ✅ (zrcaljenje) | ✅ (zrcaljenje) |
| Google Maps na TFT, telefon **zaklenjen** | ❌ slika zamrzne, Google Maps se ne da zagnati na navideznem zaslonu | ✅ naslednji zavoj, razdalja in ETA iz obvestila Google Maps na vgrajenem zemljevidu |
| Kdo se lahko poveže na strežnik videa | kdorkoli v istem Wi‑Fi omrežju | samo motor (prehod 192.168.10.1) |
| GPS pri zaklenjenem telefonu (Android 14) | se ustavi/zadrži | teče (`location` tip storitve + dovoljenje »vedno«) |
| Jeziki | tr, en, el, es, it, fr | + **slovenščina** |
| Hitrost sličic | fiksno 30 | 30 / 24 / 15 (manj gretja telefona) |
| QUERY_ALL_PACKAGES, cleartext, backup | vklopljeno | odstranjeno |
| Gradnja APK | ročno v Android Studiu | samodejno v GitHub Actions |

## Kako deluje navigacija z zaklenjenim telefonom

1. Zrcaljenje zaslona (MediaProjection) ob zaklepu telefona **vedno** zamrzne – to je omejitev Androida, ne aplikacije.
2. Google Maps se ne da zagnati na navideznem zaslonu, ki ga ustvari tuja aplikacija (Android zavrne zagon aktivnosti brez `allowEmbedded`). KoveMirror je v tem primeru tiho prikazal svoj zemljevid brez navodil.
3. KoveLink zato prebere **obvestilo navigacije Google Maps** (NotificationListenerService), ki se posodablja tudi pri zaklenjenem telefonu, in na vgrajenem zemljevidu OSM prikaže Googlovo puščico, besedilo zavoja, razdaljo in ETA. Pot računa Google, zemljevid riše KoveLink.

Praktično: v Google Maps zaženi navigacijo → zakleni telefon → na TFT se prikaže zemljevid s tvojo pozicijo in Googlovim naslednjim zavojem. Ko telefon odkleneš, se vrne polno zrcaljenje Google Maps.

## Namestitev

1. Prenesi `KoveLink-vX.Y.Z-release.apk` iz **Releases** (ali iz **Actions → Build APK → Artifacts**).
2. Namesti (dovoli namestitev iz neznanih virov). Lahko sobiva s KoveMirror in KF Mirror.
3. Na TFT: dolg pritisk **ENT** → nastavitve zaslona → *Phone Screen projection Navigation* (QR kode ne skeniraj, aplikacijo že imaš).
4. Telefon seznani z motorjem prek Bluetootha (naprava `CQKY_…`).

## Nastavitev (enkrat)

V aplikaciji na kartici **🔒 Navigacija z zaklenjenim telefonom**:

- **Dovoli dostop do obvestil** → vklopi *KoveLink navigacija*.
- **Dovoli neomejeno baterijo** (sicer Samsung/Xiaomi storitev med vožnjo ubijejo).
- Ob prvem zagonu potrdi lokacijo in nato **»Vedno dovoli«** (dovoljenje v ozadju).
- Priporočeno: v nastavitvah Wi‑Fi telefona vklopi *Uporabi mobilne podatke, ko Wi‑Fi nima interneta* – telefon je povezan na Wi‑Fi motorja, zemljevidi pa pridejo prek mobilnih podatkov.

Po želji: **🎞️ Video** preklopi na 24 ali 15 sl/s, če se telefon pregreva.

## Gradnja iz izvorne kode

Brez Android Studia: forkaj repozitorij na GitHub, `git push` na vejo `main` → delovni tok **Build APK** zgradi APK in ga pripne kot artefakt; `git tag v1.0.1 && git push --tags` ustvari Release z APK‑jem.

Lokalno: Android Studio (JDK 17, SDK 34) → `./gradlew assembleRelease` → `app/build/outputs/apk/release/`.

## Licenca

Izvorni projekt KoveMirror **nima licenčne datoteke**, kar formalno pomeni »vse pravice pridržane«. KoveLink je zato namenjen osebni uporabi; pred javno distribucijo prosi avtorja (@nakturk) za dovoljenje ali licenco (npr. MIT/Apache‑2.0). Spremembe v KoveLink (NavNotificationListener, ExternalNavState, varnostne popravke, prevod) so na voljo pod MIT.

## Varnost in zasebnost

- Vrata 15456/17818/15457 se vežejo samo na IP telefona v omrežju motorja in sprejmejo samo prehod motorja.
- Dostop do obvestil se uporabi izključno za obvestila paketov `com.google.android.apps.maps` in `com.waze`; vsebina se ne shranjuje in ne pošilja nikamor razen na TFT.
- Zemljevidi, usmerjanje, vreme in iskanje naslovov uporabljajo javne storitve OSM/Open‑Meteo/Nominatim (podedovano iz KoveMirror) – koordinate se pošljejo tem strežnikom.
