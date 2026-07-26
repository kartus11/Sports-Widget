# kotlinx.serialization keeps generated serializers reachable via reflection lookups.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.kartus.sportswidget.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.kartus.sportswidget.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
