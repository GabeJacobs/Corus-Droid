# Firebase
-keepattributes Signature
-keepattributes *Annotation*

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
