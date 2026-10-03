# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.yaz.dialer.** {
    *** Companion;
}
-keepclasseswithmembers class com.yaz.dialer.** {
    kotlinx.serialization.KSerializer serializer(...);
}
