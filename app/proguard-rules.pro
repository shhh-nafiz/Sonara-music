# keep our own code (services, widget, receivers are referenced from the manifest)
-keep class com.sonara.player.** { *; }
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
