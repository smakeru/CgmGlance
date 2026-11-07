# CGM Glance — Project Memory & Agent Rules

Acest fișier (`AGENTS.md`) este încărcat automat în memoria locală a proiectului în fiecare sesiune.

## 1. Limba și Stilul de Comunicare
- Răspunde întotdeauna în **limba română**.
- Nu folosi automatizări de browser (Playwright) pe contul Google al utilizatorului pentru a evita blocările de securitate Google; pentru interacțiunea cu Google Play Console folosește scriptul API oficial `scripts/publish_play.py`.

## 2. Google Play Store & Identificatori Oficiali
- **Application ID**: `ro.smakeru.cgmglance` (identic pe `:phone:app` și `:wear:app`)
- **Dezvoltator**: `Viorel D` (`draganviorel25@gmail.com`) — Cont Personal
- **Google Play Console (Publishing Overview)**: `https://play.google.com/console/u/0/developers/7918042599438566714/app/4972634000184074064/publishing`
- **Grup Google Testeri**: `https://groups.google.com/g/cgm-glance-testers` (`cgm-glance-testers@googlegroups.com`)
- **Link Testare Internă**: `https://play.google.com/apps/internaltest/4701313260176212479`
- **Link Opt-In Testare Închisă (Alpha)**: `https://play.google.com/apps/testing/ro.smakeru.cgmglance`
- **Link Google Play Store**: `https://play.google.com/store/apps/details?id=ro.smakeru.cgmglance`
- **Site Oficial de Prezentare (Firebase Hosting — Free)**: `https://cgm-glance-play.web.app` (sursa în `site/`, publicare cu `python scripts/deploy_site.py`)
- **Privacy Policy URL**: `https://cgm-glance-play.web.app/privacy` (și Gist: `https://gist.github.com/smakeru/9ded20f5b72e9ce082a2d601a36f2edd`, sursa în `docs/privacy-policy.md`)
- **Documentație completă lansare**: `docs/play-store-release.md`

## 3. Automatizare Build & Publicare Google Play (`scripts/publish_play.py`)
Proiectul este conectat la **Google Play Android Developer API v3** prin contul de serviciu `play-publisher@cgm-glance-play.iam.gserviceaccount.com` (cheia locală: `play-service-account.json`, ignorată în `.gitignore`) și semnat cu `D:/Viorel Stuff Claude/CgmGlance/cgmglance-release.keystore` (configurat în `keystore.properties`, ignorat în `.gitignore`).

- **Verificare status piste în Google Play (`internal`, `alpha`, `wear:internal`, etc.)**:
  ```powershell
  python scripts/publish_play.py --status
  ```
- **Build + Incrementare `versionCode` + Upload automat în Testare Internă**:
  ```powershell
  python scripts/publish_play.py --bump both --build --upload internal
  ```
- **Promovare în Testare Închisă (Alpha)**:
  ```powershell
  python scripts/publish_play.py --bump none --target both --upload alpha
  ```
- **Reguli `versionCode`**:
  - Telefon (`phone/app/build.gradle.kts`): numere mici incrementale (ex. `5`, `6`, ...). Piste API: `internal`, `alpha`, `production`.
  - Ceas (`wear/app/build.gradle.kts`): offset de `1_000_000` (ex. `1_000_004`, `1_000_005`, ...). Piste API dedicate: `wear:internal`, `wear:alpha`, `wear:production`.

## 4. Reguli Tehnice & Politici Critice
1. **Sincronizare Wear OS Data Layer**: Aplicația de telefon și cea de ceas comunică prin Data Layer **doar** dacă sunt semnate cu aceeași cheie (ambele din Google Play / release keystore sau ambele debug). Nu amesteca o instalare din Google Play cu una locală de debug.
2. **Declarația Health Apps în Play Console**: Pe cont personal, aplicația trebuie să aibă bifate doar *Activity and fitness* și *Nutrition and weight management* sub *Health and fitness*. Nu bifa categorii medicale/clinice (*Diseases and conditions management*, *Medical device apps*), deoarece declanșează blocarea pentru organizații.
3. **Protecția datelor și a cheilor**: Nu comite niciodată în Git capturi de ecran reale cu notificările personale ale utilizatorului, fișiere `.keystore`/`.jks`, `keystore.properties` sau fișiere `*service-account*.json`.
4. **Conținutul codebase-ului**: Păstrează în proiect doar codul sursă, testele, documentația tehnică și site-ul oficial de prezentare (`site/`). Nu adăuga în repository fișiere sau drafturi cu mesaje promoționale pentru social media.
