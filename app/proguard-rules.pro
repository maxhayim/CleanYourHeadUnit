# Plain Java, no reflection-based libraries. Keep the VPN service and activities (manifest-referenced; R8 keeps them).
-keepclassmembers class * extends android.app.Activity { public void *(android.view.View); }
