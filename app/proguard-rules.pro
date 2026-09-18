# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.duck.app.**$$serializer { *; }
-keepclassmembers class com.duck.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.duck.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class com.duck.app.DuckApplication

# Glance
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**

# Timber
-dontwarn org.jetbrains.annotations.**

# Compose / Kotlin metadata
-dontwarn kotlinx.**
# RuStore AppUpdate SDK
-keep class ru.rustore.sdk.** { *; }
-dontwarn ru.rustore.sdk.**

# MyTracker SDK
-keep class com.my.tracker.** { *; }
-dontwarn com.my.tracker.**
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient {
    com.google.android.gms.ads.identifier.AdvertisingIdClient$Info getAdvertisingIdInfo(android.content.Context);
}
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient$Info {
    java.lang.String getId();
    boolean isLimitAdTrackingEnabled();
}
-keep class com.android.installreferrer.** { *; }
-keep class com.android.vending.billing.** { *; }
-keep class com.android.billingclient.api.** { *; }
