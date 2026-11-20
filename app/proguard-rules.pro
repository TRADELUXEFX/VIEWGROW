# Keep line numbers in crash reports so they can be read.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Android creates fragments by class name when a screen is restored, so their constructors must be kept.
-keep public class * extends androidx.fragment.app.Fragment {
    public <init>();
}

# The background timer creates its worker by class name.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Google Play Billing keeps its own rules in the library.
-keep class com.android.billingclient.** { *; }

# Phone number rules per country (CountryField.kt)
-keep class io.michaelrocks.libphonenumber.android.** { *; }
