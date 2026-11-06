# Google Play Store Release & Testing Reference

Acest document păstrează toate linkurile, configurațiile și pașii necesari pentru ducerea aplicației **CGM Glance** până la publicarea finală în **Producție (Google Play Store)**.

---

## 1. Identificatori și Linkuri Oficiale

| Resursă | Valoare / Link |
| :--- | :--- |
| **Application ID (Package Name)** | `ro.smakeru.cgmglance` |
| **Nume Dezvoltator (Play Store)** | `Viorel D` (`draganviorel25@gmail.com`) |
| **Repository GitHub** | `https://github.com/smakeru/CgmGlance` |
| **Privacy Policy (Public Gist URL)** | `https://gist.github.com/smakeru/9ded20f5b72e9ce082a2d601a36f2edd` |
| **Grup Google pentru Testeri (Web)** | `https://groups.google.com/g/cgm-glance-testers` |
| **Adresa Email Grup Google (pt. Play Console)** | `cgm-glance-testers@googlegroups.com` |
| **Link Testare Internă (Internal Testing)** | `https://play.google.com/apps/internaltest/4701313260176212479` |
| **Link Opt-In Web Testare Închisă (Closed Testing)** | `https://play.google.com/apps/testing/ro.smakeru.cgmglance` |
| **Link Google Play Store (după Opt-In / Producție)** | `https://play.google.com/store/apps/details?id=ro.smakeru.cgmglance` |

---

## 2. Cheia de Semnare (Release Keystore)

> **IMPORTANT**: Nu șterge și nu pierde niciodată fișierul `.keystore`! Fără el nu se mai pot încărca actualizări pe Google Play. Fișierul este în afara repo-ului git (și ignorat în `.gitignore`).

- **Cale Keystore**: `D:/Viorel Stuff Claude/CgmGlance/cgmglance-release.keystore`
- **Fișier configurare local**: `D:/MyProjects/AppsViorel/CgmGlance/keystore.properties` (exclus din Git)
- **Key Alias**: `cgmglance`

---

## 3. Reguli de Versiune (`versionCode`)

Google Play cere ca fiecare pachet `.aab` încărcat să aibă un `versionCode` strict mai mare decât cel anterior, iar pachetul de ceas (**Wear OS**) are propria pistă dedicată:

- **Telefon (`:phone:app` în `phone/app/build.gradle.kts`)**:
  - `versionCode = 5` (`versionName = "1.2"`) — Include cardul interactiv cu ghidul în 4 pași (`SetupGuideCard.kt`: *1. Instalare ceas*, *2. Cadran ceas*, *3. Card (Tile)*, *4. Widget telefon*) și butonul direct **Deschide în Google Play**.
  - Pachet generat: `release/CgmGlance-phone-1.2-v5.aab` (și `release/CgmGlance-phone-1.2.aab`).
- **Ceas Wear OS (`:wear:app` în `wear/app/build.gradle.kts`)**:
  - `versionCode = 1_000_004` (`versionName = "1.2"`).
  - Pachet generat: `release/CgmGlance-wear-1.2-v1000004.aab` (și `release/CgmGlance-wear-1.2.aab`).

### Comenzi pentru generarea unei versiuni noi
```powershell
# 1. Telefon
./gradlew :phone:app:bundleRelease

# 2. Ceas (Wear OS)
./gradlew :wear:app:bundleRelease
```

---

## 4. Configurații Aprobate în Google Play Console (Cont Personal)

Pentru ca aplicația să nu fie blocată de restricțiile Google pentru organizații medicale:
- **App category**: `Health & fitness`
- **App content -> Health apps**:
  - Bifat **DOAR** sub *Health and fitness*:
    - `Activity and fitness`
    - `Nutrition and weight management`
  - **NU** se bifează opțiuni clinice/medicale (*Diseases and conditions management*, *Medical device apps*, *Clinical decision support*), deoarece acestea cer cont de Organizație (D-U-N-S).

---

## 5. Cum Trecem de la Testare la Producție (Cei 12 Testeri × 14 Zile)

### Diferența dintre cele două linkuri de testare:
1. **Testare internă (`https://play.google.com/apps/internaltest/4701313260176212479`)**:
   - Funcționează instantaneu pentru persoanele ale căror adrese Gmail sunt adăugate manual în lista de emailuri din *Testare internă*.
   - **Nu** pornește cronometrul de 14 zile pentru deblocarea Producției.
2. **Testare închisă - Alpha (`https://play.google.com/apps/testing/ro.smakeru.cgmglance`)**:
   - Permite conectarea directă a Grupului Google **`cgm-glance-testers@googlegroups.com`** (`https://groups.google.com/g/cgm-glance-testers`).
   - Oricine intră în grupul Google (fără să îți dea emailul în privat) și apasă pe linkul de testare poate instala aplicația de pe Google Play.
   - **Pornește cronometrul oficial de 14 zile** când cel puțin 12 testeri au aplicația instalată.

### Pașii în Play Console pentru a activa Grupul Google pe Testare Închisă:
1. În Google Play Console, mergi în meniul din stânga la **Testare și lansare** -> **Testare** -> **Testare închisă (Closed testing)**.
2. Pe pista **Alpha**, apasă pe **Gestionează pista (Manage track)**.
3. Mergi pe tab-ul **Testeri (Testers)**:
   - Bifează **Grupuri Google (Google Groups)**.
   - Introdu adresa: `cgm-glance-testers@googlegroups.com` și apasă **Salvează**.
4. Mergi pe tab-ul **Țări / regiuni (Countries / regions)** și asigură-te că sunt adăugate țările dorite (România sau toate țările).
5. Apasă pe **Creează o versiune (Create new release)** (sau promovează versiunea din *Testare internă* în *Testare închisă -> Alpha* atât pentru telefon, cât și pentru pista **Wear OS**).
6. După ce Google aprobă pista Alpha și cel puțin 12 persoane din grup instalează aplicația și o păstrează 14 zile, în pagina **Producție (Production)** se va activa butonul **Solicită acces la producție (Apply for production)**.
