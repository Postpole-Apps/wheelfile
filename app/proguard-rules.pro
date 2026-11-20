# Room, Hilt, Compose and DataStore ship consumer ProGuard rules via their AARs.
# Keep line numbers for readable crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Play In-App Review ktx references an annotation that only exists in play-services-basement sources.
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite

# Keep Room entities (reflection-free, but keep names stable for schema/debugging).
-keep class com.postpole.template.data.Item { *; }
