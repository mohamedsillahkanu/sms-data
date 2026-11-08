# ITN SMS Gateway

Informatics Consultancy Firm, Sierra Leone (ICF-SL), 2027 Mass ITN Campaign

This system lets field staff send records even where there is no internet. It has three parts.

1. **Field app** (`itn_sms_field_app.html`). Every record is packed into one short SMS and the phone's SMS app opens with it, addressed to the gateway phone, so the distributor only taps Send. The field app does not need internet.
2. **Gateway app** (this Android project). It runs on one phone with a SIM card (number +23275837298). It receives the SMS, keeps them on the phone, and sends them to the server as soon as that phone has internet. It keeps trying until the server confirms each message.
3. **Server** (`Code.gs`, Google Apps Script). It saves every record in a Google Sheet. Each record has its own ID, so a record sent by SMS twice is saved only once.

## SMS format

```
ITN1*id*yymmdd*district code*PHU*community*head of household*e-token*household size*pregnant women*under five*ITNs issued*distributor ID
```

District codes run from 1 (Bo) to 16 (Western Area Urban) in alphabetical order. A typical record is about 90 characters, which fits in one SMS.

## 1. Set up the server

1. Create a Google Sheet, then open Extensions, Apps Script.
2. Paste the contents of `Code.gs` and save.
3. Click Deploy, New deployment, choose Web app, set Execute as to Me and Who has access to Anyone, then Deploy.
4. Copy the web app address (it ends in `/exec`). The Records and SMS Log sheets are created on the first record.

## 2. Build the gateway APK

The project builds on GitHub, so Android Studio is not needed.

1. Create a new GitHub repository and upload everything in this folder, including the hidden `.github` folder.
2. Open the Actions tab. The workflow **Build ITN SMS Gateway APK** runs on every push, and you can also start it by hand with Run workflow.
3. When it finishes, open the run and download the artifact **ITN-SMS-Gateway-APK**. Unzip it to get `ITN-SMS-Gateway.apk`.

## 3. Set up the gateway phone

1. Install the APK on the phone that holds the SIM for +23275837298. Allow installing from unknown sources when Android asks.
2. Open **ITN SMS Gateway** and tap **Allow the app to receive SMS**.
3. Tap **Let the app run in the background** and allow it. On Tecno, Infinix, itel and Xiaomi phones, also open the phone's battery settings and set the app to no restrictions, otherwise the phone may stop it.
4. The server address is already filled in under Settings. Change it there only if you deploy a new web app.
5. Keep the phone switched on, charged, and with credit for data. Messages that arrive while it is offline wait on the phone and go out by themselves when data returns. The **Send waiting messages now** button sends them at once.

Only messages that start with `ITN1*` are taken. Other SMS on the phone are ignored and stay in the normal messages app.

## 4. Set up the field app

1. Open `itn_sms_field_app.html` on the distributor's phone (host it on GitHub Pages or Render, or copy the file to the phone).
2. Enter the Distributor ID at the top of the form (the phone remembers it for the next record), fill in the household and tap Submit data. The gateway number (+23275837298) is already set under Settings.
3. Each record shows Sent by SMS once the SMS app was opened for it. Any record can be sent again from the Records tab, and the server ignores a copy it already has.

## Notes

1. The APK built by the workflow is a debug build. It installs and runs normally but is not for the Play Store, which restricts apps that read SMS.
2. Android may delay background work by a few minutes to save battery. The 15 minute check makes sure nothing waits longer than that once there is internet.
