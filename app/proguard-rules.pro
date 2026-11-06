# Project-specific ProGuard rules.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.baijum.purandara.**$$serializer { *; }
-keepclassmembers class com.baijum.purandara.** {
    *** Companion;
}
-keepclasseswithmembers class com.baijum.purandara.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ONNX Runtime (JNI looks classes up by name)
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
