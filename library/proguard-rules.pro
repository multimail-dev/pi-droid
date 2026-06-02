# ProGuard rules for pi-droid library module
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.anthropic.pidroid.**$$serializer { *; }
-keepclassmembers class dev.anthropic.pidroid.** {
    *** Companion;
}
-keepclasseswithmembers class dev.anthropic.pidroid.** {
    kotlinx.serialization.KSerializer serializer(...);
}
