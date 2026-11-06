# Privacy Policy for CGM Glance

*Last updated: November 6, 2026*

**CGM Glance** ("we", "our", or "the app") is developed by Viorel D as a companion tool to display glucose readings on your Android phone widgets and Wear OS smartwatch. We are strongly committed to user privacy, particularly concerning sensitive health information.

---

## 1. Summary of Data Practices

- **Zero Remote Data Collection**: CGM Glance does not operate servers, cloud databases, or telemetry systems. None of your glucose values, timestamps, or device identifiers are ever sent to the developer or any third party.
- **Local On-Device Processing**: All readings are processed entirely on your phone and sent directly over the encrypted local Bluetooth/Wi-Fi connection to your paired Wear OS smartwatch via Google Play Services Wearable Data Layer.
- **No Advertisements or Analytics**: There are no ad networks, tracking SDKs, or third-party analytics libraries included in the app.

---

## 2. Information Handled by the App

### A. Blood Glucose Readings & CGM Notifications
- **How we access it**: CGM Glance uses Android's `NotificationListenerService` permission exclusively to read glucose notifications posted locally by your selected Continuous Glucose Monitoring (CGM) app (such as LinX or Dexcom ONE+).
- **Purpose**: To calculate the glucose trend arrow, determine the reading's age, and display this information on your phone widget, lock screen, and Wear OS tile / complication.
- **Scope**: Notifications from unrelated applications are strictly ignored and discarded immediately. No notification text is ever logged, shared, or transmitted off your device.

### B. Health Connect Integration
- If you voluntarily enable the **Health Connect** feature in the app settings, CGM Glance requests write permission for the **Blood Glucose** (`BloodGlucoseRecord`) data type.
- **Data Written**: The app writes only periodic blood glucose values to the on-device Android Health Connect storage so that other authorized apps (such as Samsung Health or Google Fit) can read them.
- **No Read Access**: CGM Glance never reads other health, fitness, or personal records from Health Connect.
- You can revoke this permission at any time in Android system settings or through the Health Connect app.

---

## 3. Data Storage and Retention

- The app stores a minimal, rolling history of recent readings locally on your phone and watch to calculate trend arrows and graph data points.
- This data remains strictly in your device's private app sandbox.
- Uninstalling the application immediately and permanently deletes all locally cached data.

---

## 4. Medical Disclaimer

CGM Glance is not a medical device and is not intended for the diagnosis, treatment, cure, or prevention of any disease or health condition.
- It displays values previously calculated by your official CGM app.
- It does not replace medical advice, official CGM alerts, or finger-stick blood glucose measurements.
- Always consult your official CGM application and healthcare provider before making any treatment decisions.

---

## 5. Changes to This Privacy Policy

If this policy is updated in future versions of the application, the updated text will be published at this location.

---

## 6. Contact Us

If you have any questions or feedback regarding this Privacy Policy, you can reach out via:
- Email: **draganviorel25@gmail.com**
- GitHub: [https://github.com/smakeru/CgmGlance](https://github.com/smakeru/CgmGlance)
