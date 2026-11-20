===============================================================
 AIS Inventory TV  -  Android TV app  (v1.0.0)
 Build + install guide            Developed by RYZ Systems
===============================================================

This folder is the complete source of the Android TV app. The APK
is built for you by GitHub (free) - no Android Studio, no admin
rights, nothing to install on your PC. Same way as AIS Terminal.

---------------------------------------------------------------
 A) BUILD THE APK ON GITHUB  (first time ~10 min, later ~4 min)
---------------------------------------------------------------
 1) github.com -> sign in -> "+" (top right) -> "New repository"
      Name: ais-inventory-tv      Private: YES      -> Create.
 2) On the new repo page click  "uploading an existing file".
 3) Open this AndroidTV_App folder on your PC, select EVERYTHING
    inside it (including the hidden-looking ".github" folder) and
    drag it into the browser. -> "Commit changes".
      TIP: if Windows hides ".github", in File Explorer tick
      View -> Show -> Hidden items. The .github\workflows\
      build-apk.yml file MUST be uploaded - it is the build recipe.
 4) Click the "Actions" tab. A run "Build Android TV APK" starts by
    itself (if Actions asks to be enabled, click Enable, then
    "Run workflow").
 5) When it shows a green tick, open the run -> at the bottom under
    "Artifacts" click  AIS_Inventory_TV-apk  -> a zip downloads.
 6) Unzip it -> you get  AIS_Inventory_TV.apk
    (and the same file with the version in its name, for records).

---------------------------------------------------------------
 B) PUT IT ON THE SERVER  (so every TV can install it over LAN)
---------------------------------------------------------------
 Copy  AIS_Inventory_TV.apk  next to  AIS_Inventory_TV.exe  on the
 server PC. TVs can then download it from:
        http://<server-PC-IP>:8080/app
 (Only this one file is shared - config.json etc. stay private.)

---------------------------------------------------------------
 C) INSTALL ON EACH TV
---------------------------------------------------------------
 See "TV_DISPLAY_OPTIONS_(Browser_or_Android_App).txt", OPTION 2:
 Downloader app -> http://<server-PC-IP>:8080/app -> Install ->
 open AIS Inventory TV -> "Find server automatically" ->
 "Save & start dashboard" -> "Set as TV Home app" ->
 "Allow auto-return".

---------------------------------------------------------------
 D) MAKING A NEW VERSION LATER
---------------------------------------------------------------
 1) Edit what is needed (or ask RYZ Systems), then in
    app\build.gradle raise:   versionCode 1 -> 2
                              versionName '1.0.0' -> '1.0.1'
    and in  app\src\main\java\com\aisglass\inventorytv\Prefs.java
    set VERSION = "1.0.1".
 2) Upload the changed files to the GitHub repo -> Actions builds
    the new APK automatically -> steps B and C (installs as an
    UPDATE, the TV keeps its settings).

---------------------------------------------------------------
 IMPORTANT - THE SIGNING KEY
---------------------------------------------------------------
 app\ryz-release.keystore is the RYZ Systems signing key. Every
 build is signed with it, which is what lets a new APK install
 OVER the old one. Keep a backup copy. If it is lost, TVs must
 uninstall the old app once before installing a new build.
 (Keep the GitHub repo PRIVATE.)

---------------------------------------------------------------
 WHAT'S INSIDE (for IT)
---------------------------------------------------------------
 Plain Android + WebView, no third-party libraries. Package
 com.aisglass.inventorytv, minSdk 21 (Android TV 5+), works on
 Android TV / Google TV boxes and also phones/tablets.
 Permissions: INTERNET, network/Wi-Fi state, boot completed,
 wake lock, foreground service (watchdog), display-over-apps
 (only to bring the dashboard back to the front). It talks only
 to the AIS server you configure, over the LAN. No data leaves
 the network; nothing is collected.
   MainActivity ...... full-screen dashboard, HOME app, reconnect,
                       health check, PIN-protected Setup access
   SetupActivity ..... remote-friendly setup, LAN auto-find, test
   KeepFrontService .. watchdog that brings the dashboard back
   BootReceiver ...... start on TV power-on / after update
   App ............... auto-restart on crash
===============================================================
