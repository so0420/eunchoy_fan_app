# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.so0420.eunchoy.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.so0420.eunchoy.**$$serializer { *; }
-keepclassmembers class com.so0420.eunchoy.** {
    *** Companion;
}
