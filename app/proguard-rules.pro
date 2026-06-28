# ---- kotlinx.serialization ----
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
-dontnote kotlinx.serialization.**
-keepclassmembers class com.so0420.eunchoy.** {
    *** Companion;
}
-keepclasseswithmembers class com.so0420.eunchoy.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.so0420.eunchoy.**$$serializer { *; }

# Keep all DTOs + domain models and their members (filled by generated serializers / JSON).
-keep class com.so0420.eunchoy.data.net.** { *; }
-keep class com.so0420.eunchoy.data.model.** { *; }

# ---- Retrofit / OkHttp (safety on top of the libraries' bundled consumer rules) ----
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ---- WorkManager worker (instantiated by class name via reflection) ----
-keep class com.so0420.eunchoy.work.PollWorker { <init>(...); }
