-keep class androidx.room.** { *; }
-keep class dev.gdk.bumblebee.data.local.** { *; }
-keep class dev.gdk.bumblebee.protocol.** { *; }
-dontwarn com.google.android.gms.**

# Release builds don't write routine logs to the system log: they can name the bike's Bluetooth
# address and link details. Errors stay, for crash reports.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
