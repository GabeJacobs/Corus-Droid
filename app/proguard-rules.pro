# Firebase
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.firebase.** { *; }
-keep interface com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Google Play Services (Sign-In, Auth, Tasks, Play Integrity)
-keep class com.google.android.gms.** { *; }
-keep interface com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Protobuf (used internally by Firebase)
-keep class com.google.protobuf.** { *; }
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
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# RevenueCat
-keep class com.revenuecat.purchases.** { *; }
