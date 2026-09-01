# Firebase
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.firebase.**

# Google Play Services (Sign-In, Auth, Tasks, Play Integrity)
-dontwarn com.google.android.gms.**

# Protobuf (used internally by Firebase)
-dontwarn com.google.protobuf.**

# javax.annotation (used by Firebase but missing at runtime)
-dontwarn javax.annotation.**
-dontwarn javax.lang.model.element.Modifier

# Firestore deserialization uses reflection on these data classes.
# Without keep rules, R8 renames fields and breaks document mapping.
-keep class fm.corus.android.data.model.** { *; }
-keepclassmembers class fm.corus.android.data.remote.** {
    <init>(...);
    <fields>;
}

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class fm.corus.android.**$$serializer { *; }
-keepclassmembers class fm.corus.android.** { *** Companion; }
-keepclasseswithmembers class fm.corus.android.** { kotlinx.serialization.KSerializer serializer(...); }

# Ktor
-dontwarn io.ktor.**

# Crashlytics: keep line numbers so release stacks stay readable after R8.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# TIDAL Auth ships an empty consumer ProGuard file but uses Retrofit
# interfaces and kotlinx.serialization for on-device token storage.
# Keep the package so login/refresh cannot break after minification.
-keep class com.tidal.sdk.auth.** { *; }
-keep interface com.tidal.sdk.auth.** { *; }

# Spotify App Remote references optional Jackson adapters and a compile-time-only
# nullability annotation that are not present in (or required by) the app.
-dontwarn com.fasterxml.jackson.databind.deser.std.StdDeserializer
-dontwarn com.fasterxml.jackson.databind.ser.std.StdSerializer
-dontwarn com.spotify.base.annotations.NotNull
