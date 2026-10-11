# Pregled projekta nakturk/kovemirror in predlogi izboljšav

Pregledana različica: build 543 (2026‑09‑18), 36 datotek Kotlin, 13 103 vrstic, brez datoteke LICENSE.
Status: ✅ narejeno v KoveLink 1.0.0 · 🔜 predlagano, še ni narejeno.

## Kako aplikacija deluje (na kratko)

- `MirrorService` (foreground storitev) drži `TcpServer` (vrata video 15456, nadzor 17818, utrip 15457), `BleManager` (BLE rokovanje, ki na TFT vklopi Wi‑Fi) in preklaplja med dvema kodirnikoma:
  - **A – zrcaljenje zaslona** (`ProjectionEncoder`): MediaProjection → OpenGL (črni pasovi) → H.264 → TCP.
  - **B – vgrajeni zemljevid** (`PresentationEncoder` + `KovePresentation`): lasten VirtualDisplay, na njem osmdroid zemljevid z lastno navigacijo (OSRM/Valhalla), GPX uvoz, ovinki, vreme.
  - **C – tuja aplikacija na VirtualDisplay** (`setLaunchDisplayId`): deluje samo za aplikacije z `allowEmbedded` (DMD2, OsmAnd); Google Maps/Waze Android zavrne.
- Ob ugasnjenem zaslonu (`ScreenStateManager`) se preklopi A → B, ob prižganem nazaj.
- Zasloni 2026 pošiljajo utrip `02 01 00 00 00 00` na nadzorna vrata; aplikacija ga mora takoj vrniti (echo), sicer TFT ne odpre video vrat (`TcpServer` to dela).

## 1. Kritično

| # | Težava | Stanje |
|---|---|---|
| 1.1 | **Strežnik videa sprejme kogarkoli.** Vrata se vežejo na Wi‑Fi IP ali celo `0.0.0.0`; brez preverjanja odjemalca. Če storitev teče na skupnem Wi‑Fi (kavarna, servis), prva naprava, ki se poveže na 15456, dobi živo sliko zaslona telefona. | ✅ samo prehod motorja, nikoli `0.0.0.0` |
| 1.2 | **Zaklenjen telefon izgubi GPS** – storitev nima tipa `location`, ni `ACCESS_BACKGROUND_LOCATION`; Android 14 ustavi lokacijo, zemljevid na TFT zmrzne. | ✅ |
| 1.3 | **Google Maps ni mogoč na TFT pri zaklenjenem telefonu** – izbira »Google Maps« v meniju tiho pade na vgrajeni zemljevid. | ✅ branje obvestila navigacije (NavNotificationListener) |
| 1.4 | **Preklop nazaj na zrcaljenje po ugasnjenem zaslonu na Android 14** – ponovna uporaba shranjenega MediaProjection žetona in večkratni `createVirtualDisplay` na isti projekciji sta na API 34 prepovedana (SecurityException). | 🔜 en VirtualDisplay, zamenjava površine; ali ponovna zahteva soglasja |
| 1.5 | **Shranjene točke POI se množijo ob vsakem zagonu** (`saveCurrentRoutesToStorage` shrani vse POI v vsak zapis poti, nalaganje jih ponovno doda za vsak zapis). | 🔜 |
| 1.6 | **Gumbi na krmilu pri zaklenjenem telefonu ne delujejo** – zoom/pan so geste dostopnosti na zaslonu telefona, ne na VirtualDisplay; `KovePresentation` ne posluša `HandlebarKeyManager`. | 🔜 pošiljati ukaze neposredno v KovePresentation |

## 2. Stabilnost in baterija

| # | Težava | Stanje |
|---|---|---|
| 2.1 | Wakelock se ob drugem START‑u prepiše brez sprostitve (puščanje). | ✅ |
| 2.2 | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` deklariran, nikoli zahtevan → Samsung/Xiaomi ubijejo storitev. | ✅ gumb + preverjanje |
| 2.3 | Kodirnik 30 sl/s, ključna sličica vsako sekundo, High 4.1 – za skoraj statičen zemljevid preveč; telefon se greje. | ✅ nastavitev 30/24/15; 🔜 daljši I‑frame interval + ključna sličica ob povezavi, ponovna uporaba medpomnilnikov |
| 2.4 | Tri vzporedne zanke `Thread.sleep(200)` samo za vzdrževanje povezave; parser nadzornih vrat kopira celoten medpomnilnik v vsaki iteraciji. | 🔜 blokirajoče branje + coroutines |
| 2.5 | Trije ločeni viri GPS hkrati (MapActivity 2 s/5 m, KovePresentation GPS+network 1 s/1 m + overlay). | 🔜 en vir v MirrorService → `MapStateHolder` |
| 2.6 | `SpeedLimitHelper` kliče Overpass ob **vsakem** GPS popravku, kadar cesta nima oznake omejitve. | 🔜 vedno dušiti + negativni predpomnilnik |
| 2.7 | GPX snemanje prepiše celotno datoteko ob vsakem popravku; uvoz KML/KMZ in shranjevanje poti tečejo na glavni niti. | 🔜 inkrementalno pisanje, IO v ozadju |
| 2.8 | `MapLibre mapView3d` brez lifecycle klicev, `recordTimerHandler` se v `onDestroy` ne počisti, osmdroid `onDetach` se ne kliče, stari tile providerji se ne odklopijo. | 🔜 |
| 2.9 | `DebugLogger`: deljen `SimpleDateFormat` (ni thread‑safe), sinhrono pisanje v datoteko z vsake niti; `LocaleHelper.applyLocale` kliče `Locale.setDefault` ob vsaki lokalizirani vrstici dnevnika. | 🔜 |

## 3. Zasebnost in skladnost

| # | Težava | Stanje |
|---|---|---|
| 3.1 | `QUERY_ALL_PACKAGES` (Play ga zavrne), `usesCleartextTraffic=true` (vsi URL‑ji so HTTPS), `allowBackup=true` (priljubljene = domači naslov, MAC motorja v oblak). | ✅ |
| 3.2 | Dnevnik v zunanji mapi vsebuje koordinate vsakih 5 s, serijsko številko TFT (TUC) in surove BLE pakete; deli se z enim klikom. | 🔜 koordinate in TUC zakriti |
| 3.3 | Google tile‑i (`mt0‑3.google.com/vt`) kršijo Googlove pogoje; MapLibre atribucija izklopljena (krši licenco OSM); OSRM demo strežnik ni za produkcijo. | 🔜 odstraniti Google sloje, vklopiti atribucijo |
| 3.4 | Besedilo o rootu govori o »naročninah«, ki jih ni. | ✅ |
| 3.5 | Storitev dostopnosti se naroči na `typeAllMask`, dogodkov pa ne uporablja. | 🔜 zožiti |
| 3.6 | Repozitorij brez LICENSE. | 🔜 prositi avtorja za MIT/Apache |

## 4. Kakovost kode

- `MapActivity`: 3 563 vrstic, 82 funkcij, 23 klicev `getSharedPreferences`, 52 `Color.parseColor`. 🔜 razbiti na TileSourceRegistry, NavigationEngine, RouteRepository, TrackRecorder, Map3dController + ViewModel.
- Podvojena logika: viri tile‑ov (MapActivity ↔ KovePresentation), navigacijski napredek in ikone zavojev, dialog robov TFT (dve kopiji, ki shranjujeta v **različne** ključe `kove_prefs.tft_*` vs `kove_map_prefs.map_*`), gradnja overlayev poti (×3).
- Navodila navigacije v 6 jezikih so trdo kodirana v Kotlinu (`NavigationHelper:387‑531`) namesto v `strings.xml`.
- Turška besedila v kodi: vreme (`RouteWeatherHelper:193‑212` – vidi jih vsak uporabnik), ovinki, GPX statistika, merilo, obvestilo, `NetworkUtils`. ✅ delno (obvestilo, NetworkUtils, layout); 🔜 ostalo.
- 67 praznih `catch (_: …)`; `OesTextureRenderer.drawFrame` skrije vse GL napake.
- Zastarelo: `startActivityForResult`, `onBackPressed`, `BluetoothAdapter.getDefaultAdapter`, `characteristic.value`, `WifiManager.connectionInfo`, `TYPE_PHONE`, `Locale(String)`; mrtva koda `HeartbeatManager.kt`, `CrashHandler.getSavedCrash`.
- Napake v tipkah: ENTER/ESC pri zaprtem meniju vedno odpreta meni (ESC v MapActivity nikoli ne pride), gumb Nazaj in glasnost sta prestrežena → opozorilo o snemanju se ne prikaže.
- Protokol: niz različice `UC=…_SV=…` se zavrže (ni razčlenjen), `SV=2.0.4` nima posebne obravnave razen MTU 512; jezik BLE trdo `language: 2`; sinhronizacija ure uporablja privzeti locale in pošlje samo `tag:-1`; BLE sporočila, daljša od enega paketa, se ne sestavijo.

## 5. UX

- ✅ Kartica z razlago in gumbi za dovoljenja (obvestila, baterija), dialog za lokacijo v ozadju, slovenščina, popravljen prikaz jezika.
- 🔜 Prvi zagon kot čarovnik (Bluetooth → Wi‑Fi → zajem zaslona → obvestila → baterija), razumljiva sporočila napak namesto surovih angleških, milje/km, privzeta lokacija zemljevida po GPS namesto Turčije, možnost »samo navodila na vgrajeni navigacijski strani TFT« brez video prenosa (protokol `msg_id 1` / `func NAVI` že obstaja → velik prihranek baterije).

## 6. Ideje za naprej (nove funkcije)

1. **Navodila brez videa**: Googlove zavoje pošiljati na vgrajeno stran *Simple Navigation* TFT prek BLE (`{"msg_id":1,"icon":…,"next_road":…,"cur_retain_distance":…}`) – ni kodiranja, baterija zdrži cel dan. Potrebno je še preslikati Googlove ikone na ID‑je ikon TFT (preizkus na motorju).
2. Klici in sporočila na TFT (`msg_id 3`) iz istega poslušalca obvestil.
3. Vreme na TFT (`msg_id 8`) iz Open‑Meteo, ki ga aplikacija že uporablja.
4. TPMS prek BLE senzorjev (`func TIRE`) – KF Mirror to že ponuja kot plačljiv vtičnik.
5. Samodejni zagon ob povezavi na `CQKY_*` Bluetooth (brez pritiska na Start).
