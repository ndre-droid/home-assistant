# HomeFlow

Android-App zum Steuern und Automatisieren von Philips Hue, Sonos und LG webOS TV — lokal, sofort, per NFC-Tag, Widget, Button oder Hue-Licht-Trigger. Automationen werden in natürlicher Sprache per Claude erstellt.

## APK bauen — Weg A: GitHub Actions (kein Android Studio nötig, ~10 Min)

1. Kostenloses Konto auf github.com, neues **privates** Repository „homeflow" anlegen.
2. Diesen Ordner hochladen (Repo-Seite → „uploading an existing file" → alle Dateien/Ordner reinziehen — auch den versteckten Ordner `.github`!). Alternativ mit git:
   ```
   git init && git add . && git commit -m "HomeFlow"
   git branch -M main
   git remote add origin https://github.com/DEINNAME/homeflow.git
   git push -u origin main
   ```
3. Im Repo: Tab **Actions** → Workflow „Build APK" läuft automatisch (~5 Min).
4. Auf den fertigen Lauf klicken → unten **Artifacts** → `HomeFlow-debug-apk` herunterladen → ZIP entpacken → `app-debug.apk`.
5. APK aufs Handy (z. B. per USB oder Google Drive), antippen, „Unbekannte Quellen" erlauben, installieren.

Falls der Build rot wird: Log öffnen, Fehlermeldung kopieren und mir schicken — ich fixe es.

## Weg B: Android Studio

Projekt öffnen → Gradle-Sync abwarten → Build → Build APK(s). Fertige APK unter `app/build/outputs/apk/debug/`.

## Ersteinrichtung in der App

1. **Geräte-Tab:**
   - **Hue:** Bridge-IP eintragen (Hue-App → Einstellungen → Meine Hue-Systeme), runden Knopf auf der Bridge drücken, „Koppeln".
   - **Sonos:** „Suchen" — Beam, Era 100 (Bad & Küche) werden automatisch gefunden.
   - **LG TV:** IP + MAC eintragen (TV: Einstellungen → Netzwerk → WLAN → Erweitert), „Koppeln", Anfrage am TV bestätigen. Für Einschalten per App am TV „Einschalten über WLAN / LG Connect Apps" aktivieren.
2. **Einstellungen-Tab:** Anthropic API-Key eintragen (siehe unten), Tag/Nacht-Zeiten prüfen.
3. **Automationen-Tab:** + tippen, Routine in normaler Sprache beschreiben, „Los".

## Anthropic API-Key (für die Sprach-Funktion)

1. https://console.anthropic.com → Konto anlegen → **API Keys** → **Create Key**.
2. Guthaben aufladen (5 $ reichen sehr lange — eine Routine-Erstellung kostet ~1 Cent).
3. Key in HomeFlow → Einstellungen einfügen. Er wird nur beim Erstellen/Ändern von Automationen benutzt, nie beim Ausführen — Routinen bleiben deshalb immer sofort und offline-fähig.

## Unterwegs steuern: Tailscale (kostenlos)

Sonos und LG haben keine brauchbaren Cloud-APIs — der saubere Weg ist ein VPN ins Heimnetz:

1. **Tailscale**-App auf dem S22 Ultra installieren, anmelden (Google-Konto reicht).
2. Tailscale auf einem Gerät zuhause installieren, das durchläuft (dein LG Gram, wenn er meist an ist, oder ein Raspberry Pi / manche Router).
3. Auf dem Heimgerät Subnet-Routing aktivieren, z. B.:
   `tailscale up --advertise-routes=192.168.178.0/24` (dein Heimnetz-Bereich)
4. Im Tailscale-Admin (login.tailscale.com) die Route freigeben ("Edit route settings" → approve).
5. Fertig. VPN am Handy an → HomeFlow funktioniert unterwegs identisch, gleiche IPs, ~50 ms Zusatz-Latenz.

## NFC-Tags

Beliebige NTAG213/215-Sticker (ab ~1 €/Stück). In der App: Automation öffnen → Auslöser „NFC" → „NFC-Tag beschreiben" → Tag ans Handy halten. Antippen des Tags startet die Routine sofort (Bildschirm muss entsperrt sein — Android-Sicherheitsvorgabe).

## Widget

Homescreen lange drücken → Widgets → HomeFlow → platzieren → Automation auswählen. Ein Tipp = Routine läuft, ohne dass die App aufgeht.

## Sound-URLs (Vogel-/Walgeräusche)

Sonos braucht eine Stream- oder Datei-URL. Optionen:
- Eigene MP3 auf dem Heimgerät per einfachem HTTP-Server (`python -m http.server`) → URL `http://<heim-ip>:8000/voegel.mp3`.
- Internetradio-Streams (Naturgeräusch-Sender), URL in der Aktion eintragen.
Claude lässt das URL-Feld leer, wenn keine Quelle genannt ist — die App markiert das mit ⚠, antippen und URL einfügen.

## Bekannte Grenzen (ehrlich)

- **TV einschalten** geht nur per Wake-on-LAN und braucht ein paar Sekunden; wenn der TV komplett stromlos war, geht es gar nicht. Alles andere (aus, Lautstärke, mute) ist sofort.
- **Geräte-Trigger** („wenn Badlicht angeht") brauchen den Hintergrunddienst. Samsung killt gerne: Einstellungen → Apps → HomeFlow → Akku → **Nicht optimiert** setzen.
- **NFC** funktioniert nur bei entsperrtem Bildschirm (Android-Systemgrenze, kein Workaround).
- Ausführung ist lokal und parallel: typisch < 0,5 s für Licht + Sonos + TV gleichzeitig.
