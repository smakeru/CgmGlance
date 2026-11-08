# Idei de Viitor & Concepte de Extindere (Future Ideas)

Acest document păstrează ideile și oportunitățile de dezvoltare identificate pentru **CGM Glance**, inspirate din feedback-ul comunității și din analiza proiectelor conexe (precum ecosistemul open-source *Sugar Sense*).

---

## 1. Indicatorul de Variație / Delta (`Δ +X` / `-X`) — *În Implementare*
* **Concept:** Afișarea diferenței numerice exacte de glicemie față de citirea precedentă (~5 minute în urmă), pe lângă săgeata direcțională de trend (ex. `112 ↗ (+4)`).
* **Utilitate:** Săgeata `↗` indică doar direcția calitativă; delta indică viteza cantitativă (+2 vs. +9 mg/dL).
* **Suprafețe vizate:**
  * Widget-uri telefon (Android Home Screen).
  * Card dedicat pe ceas (Wear OS Tile).
  * Complicații late pe ceas (`LONG_TEXT`).
  * Ecranul complet al aplicației pe ceas (`WatchMainActivity`).

---

## 2. Modul „Bedside / Nightstand” (Ceas de Noptieră / AOD)
* **Concept:** Transformarea ecranului telefonului sau ceasului într-un ceas de noapte discret atunci când dispozitivul se află la încărcat pe noptieră.
* **Caracteristici cheie:**
  * Fundal negru AMOLED 100% pentru zero strălucire deranjantă în cameră.
  * Ceas digital mare cu luminozitate minimă.
  * Valoarea glicemiei și săgeata afișate discret sub oră.
  * Gest tactil: o singură atingere oriunde pe ecran ridică luminozitatea temporar timp de 60 de secunde dacă utilizatorul se trezește noaptea și vrea să vadă clar starea.

---

## 3. Modul „Full-Color Alert Scene” (Vizibilitate de la Distanță)
* **Concept:** Opțiune vizuală (în widget-uri mari sau ecran complet) în care întregul fundal adoptă culoarea intervalului de glicemie:
  * Verde (în țintă: 70–180 mg/dL).
  * Galben / Chihlimbar (peste țintă: >180 mg/dL).
  * Roșu aprins (hipoglicemie: <70 mg/dL sau <55 mg/dL).
* **Utilitate:** Permite citirea stării dintr-o privire de la câțiva metri distanță (de pe birou sau de pe noptieră), fără a fi nevoie să descifrezi cifrele mici.

---

## 4. Rafinarea Politicii de Prospețime (Staleness / Expiry Tiers)
* **Concept:** Structurarea stării datelor în praguri clare:
  * `< 15 min`: Date proaspete și sigure.
  * `15 min – 2 ore`: Marcaj `Stale` (numărul păstrat dar marcat cu `?` sau text estompat; ascunderea săgeții de trend și a deltei pentru a preveni decizii bazate pe date vechi).
  * `> 2 ore`: `No Data` complet, cu golirea ecranelor pentru a nu induce niciodată în eroare utilizatorul.

---

## 5. Suport Hardware Dedicat: Display ESP32 de Birou / Noptieră (Opțional / DIY)
* **Concept:** Pentru utilizatorii care își doresc un ecran fizic independent pe birou sau noptieră, dar fără a trimite datele în cloud-uri externe.
* **Arhitectură posibilă:**
  * Utilizarea plăcuțelor ieftine ESP32-S3 cu ecran tactil (ex. Guition JC3248W535 de ~25$).
  * Telefonul (care recepționează deja notificările LinX / Dexcom) poate trimite un broadcast local prin Wi-Fi LAN (sau Bluetooth BLE) către display-ul de pe noptieră, păstrând principiul de bază al CGM Glance: **100% local, fără servere cloud, fără conturi externe**.

---

## 6. Mod Multi-Persoană / Părinte („Care View”)
* **Concept:** Afișarea în paralel a citirilor dacă un utilizator monitorizează doi senzori simultan (ex. testare LinX + Dexcom în paralel) sau pentru un părinte care dorește să vadă glicemia copilului pe ecranul propriu.

---

## 7. Susținere Proiect / Opțiuni de Donație („Buy Me a Coffee”) — *Post-Lansare Publică*
* **Status:** Amânat intenționat pentru etapa de după aprobarea oficială pe pista de producție Google Play (aplicația rămâne 100% gratuită pe toată durata testării închise de 14 zile).
* **Strategie de implementare sigură (conform politicilor Google Play):**
  * **Pe site-ul web (`cgm-glance-play.web.app`) & GitHub:** Integrarea unor butoane directe de susținere (*Buy Me a Coffee*, *Revolut*, *PayPal* / *Ko-fi*). Nu implică comisioane Google și nu are restricții de magazin.
  * **În aplicația mobilă (Phone):** Fără butoane directe de plată externă (pentru a respecta politica Google Payments). Doar link discret în Setări / Despre către site-ul oficial („Site & ghid: cgm-glance-play.web.app”).
  * **Varianta secundară (opțional):** Dacă se dorește buton dedicat în aplicație, acesta trebuie implementat exclusiv prin *Google Play In-App Billing* („Tip / Bacșiș dezvoltator” — comision 15% Google, necesită profil de comerciant Play Console).

