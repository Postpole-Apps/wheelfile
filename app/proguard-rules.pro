# Room, Hilt, Compose and DataStore ship consumer ProGuard rules via their AARs.
# Keep line numbers for readable crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Play In-App Review ktx references an annotation that only exists in play-services-basement sources.
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite

# Keep Room entities (reflection-free, but keep names stable for schema/debugging).
-keep class com.postpole.wheelfile.data.Item { *; }

# Ads, consent and billing: keep the SDKs' public classes so R8 never strips what they load by name.
# Each SDK also ships consumer rules; these are explicit so a release build can be checked in a unit test.
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.ump.** { *; }
-keep class com.android.billingclient.** { *; }
-dontwarn com.google.android.gms.ads.**
-dontwarn com.google.android.ump.**
-dontwarn com.android.billingclient.**
