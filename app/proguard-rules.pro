# Сохранить правила для kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Сохранить data-классы с @Serializable
-keep,includedescriptorclasses class com.schedule.app.data.model.** { *; }
-keepclassmembers class com.schedule.app.data.model.** {
    *** Companion;
}

# Ktor
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
