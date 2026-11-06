# Google Play Store Release, Automation & Testing Reference

Acest document păstrează toate linkurile, configurațiile, automatizările API și pașii necesari pentru ducerea aplicației **CGM Glance** până la publicarea finală în **Producție (Google Play Store)**.

---

## 1. Identificatori și Linkuri Oficiale

| Resursă | Valoare / Link |
| :--- | :--- |
| **Application ID (Package Name)** | `ro.smakeru.cgmglance` |
| **Nume Dezvoltator (Play Store)** | `Viorel D` (`draganviorel25@gmail.com`) |
| **Google Play Console (Publishing Overview)** | `https://play.google.com/console/u/0/developers/7918042599438566714/app/4972634000184074064/publishing` |
| **Repository GitHub** | `https://github.com/smakeru/CgmGlance` |
| **Privacy Policy (Public Gist URL)** | `https://gist.github.com/smakeru/9ded20f5b72e9ce082a2d601a36f2edd` |
| **Grup Google pentru Testeri (Web)** | `https://groups.google.com/g/cgm-glance-testers` |
| **Adresa Email Grup Google (pt. Play Console)** | `cgm-glance-testers@googlegroups.com` |
| **Link Testare Internă (Internal Testing)** | `https://play.google.com/apps/internaltest/4701313260176212479` |
| **Link Opt-In Web Testare Închisă (Closed Testing)** | `https://play.google.com/apps/testing/ro.smakeru.cgmglance` |
| **Link Google Play Store (după Opt-In / Producție)** | `https://play.google.com/store/apps/details?id=ro.smakeru.cgmglance` |

---

## 2. Chei Locale: Semnare (Keystore) & Google Play API (Service Account)

> **IMPORTANT**: Niciunul dintre aceste fișiere nu trebuie șters sau comis în Git (toate sunt excluse în `.gitignore`).

### A. Cheia de Semnare Android (Release Keystore)
- **Cale Keystore**: `D:/Viorel Stuff Claude/CgmGlance/cgmglance-release.keystore`
- **Fișier configurare local**: `D:/MyProjects/AppsViorel/CgmGlance/keystore.properties` (exclus din Git)
- **Key Alias**: `cgmglance`

### B. Contul de Serviciu Google Play Developer API (Upload Automat)
- **Proiect Google Cloud**: `cgm-glance-play`
- **Service Account Email**: `play-publisher@cgm-glance-play.iam.gserviceaccount.com` (invitat cu drepturi de Admin/Release în Google Play Console -> *Users and permissions*)
- **Fișier JSON local**: `D:/MyProjects/AppsViorel/CgmGlance/play-service-account.json` (copiat din `cgm-glance-play-f048dcdcf682.json`, ambele excluse în `.gitignore`)

---

## 3. Publicare și Verificare Automată (`scripts/publish_play.py`)

Nu mai este nevoie de încărcarea manuală a fișierelor `.aab` în browser. Scriptul `scripts/publish_play.py` comunică direct cu **Google Play Android Developer API v3**.

### Comenzi uzuale:
```powershell
# 1. Verifică statusul live al tuturor pistelor (telefon + ceas) în Google Play Console:
python scripts/publish_play.py --status

# 2. Crește automat versionCode (telefon + ceas), compilează .aab-urile semnate și le publică în Testare Internă:
python scripts/publish_play.py --bump both --build --upload internal

# 3. Publică doar o versiune nouă de telefon în Testare Internă:
python scripts/publish_play.py --bump phone --target phone --build --upload internal

# 4. Promovează versiunile deja încărcate (fără rebuild) în Testare Închisă (Alpha):
python scripts/publish_play.py --bump none --target both --upload alpha
```

### Reguli de Versiune (`versionCode`) și Piste Google Play:
- **Telefon (`:phone:app` în `phone/app/build.gradle.kts`)**:
  - Pista de testare internă în API: `internal`
  - Pista de testare închisă în API: `alpha`
  - Versiune curentă publicată: `versionCode = 5` (`versionName = "1.2"`, `status = completed` pe `internal` și `alpha`).
- **Ceas Wear OS (`:wear:app` în `wear/app/build.gradle.kts`)**:
  - Are offset de `1_000_000` față de telefon pentru a nu se ciocni niciodată codurile de versiune.
  - Pista de testare internă în API: `wear:internal`
  - Pista de testare închisă în API: `wear:alpha` (necesită activarea inițială din UI în *Testare închisă -> Alpha -> selector Wear OS*)
  - Versiune curentă publicată: `versionCode = 1_000_004` (`versionName = "1.2"`, `status = completed` pe `wear:internal`).

---

## 4. Regula de Sincronizare Telefon ↔ Ceas (Data Layer Signing)

- Wear OS Data Layer (`DataClient` / `CapabilityClient`) permite sincronizarea între telefon și ceas **DOAR dacă ambele aplicații au același `applicationId` (`ro.smakeru.cgmglance`) ȘI aceeași semnătură criptografică**.
- O aplicație instalată din Google Play (semnată de Play Store) **NU** se va sincroniza cu o aplicație instalată local din Android Studio / Gradle cu cheia de `debug`.
- Când se trece la versiunea din Google Play, vechea aplicație de `debug` de pe ceas trebuie dezinstalată înainte de a instala aplicația de ceas din Google Play.

---

## 5. Configurații Aprobate în Google Play Console (Cont Personal)

Pentru ca aplicația să nu fie blocată de restricțiile Google pentru organizații medicale:
- **App category**: `Health & fitness`
- **App content -> Health apps**:
  - Bifat **DOAR** sub *Health and fitness*:
    - `Activity and fitness`
    - `Nutrition and weight management`
  - **NU** se bifează opțiuni clinice/medicale (*Diseases and conditions management*, *Medical device apps*, *Clinical decision support*), deoarece acestea cer cont de Organizație (D-U-N-S).

---

## 6. Cum Trecem de la Testare la Producție (Cei 12 Testeri × 14 Zile)

1. **Testare internă (`https://play.google.com/apps/internaltest/4701313260176212479`)**:
   - Funcționează pentru persoanele ale căror adrese Gmail sunt adăugate în lista de emailuri din *Testare internă* (atât pe pista de telefon, cât și pe pista *Wear OS -> Testeri*).
   - **Nu** pornește cronometrul de 14 zile pentru deblocarea Producției.
2. **Testare închisă - Alpha (`https://play.google.com/apps/testing/ro.smakeru.cgmglance`)**:
   - Permite conectarea directă a Grupului Google **`cgm-glance-testers@googlegroups.com`** (`https://groups.google.com/g/cgm-glance-testers`) în tab-ul *Testeri* din *Testare închisă -> Alpha*.
   - Oricine intră în grupul Google și apasă pe linkul de opt-in poate instala aplicația de pe Google Play.
   - **Pornește cronometrul oficial de 14 zile** când cel puțin 12 testeri au aplicația instalată continuu timp de 14 zile.
   - După cele 14 zile, în pagina **Producție (Production)** se deblochează butonul **Solicită acces la producție (Apply for production)**.
