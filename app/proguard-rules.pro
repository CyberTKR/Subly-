# --- SubFlow keep rules ---
# AccessibilityService is instantiated by the system from the manifest name
-keep class com.cybertkr.suboverlay.a11y.NetflixA11yService { *; }
# Foreground service + activity referenced by manifest (AGP keeps components, but be explicit)
-keep class com.cybertkr.suboverlay.overlay.OverlayService { *; }
-keep class com.cybertkr.suboverlay.MainActivity { *; }
# Readable-ish crash lines
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
