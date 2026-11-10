# ------------------------------------------------------------------
# Keep rules for minified debug builds (APK size optimization)
# ------------------------------------------------------------------

# Readable crash/stack traces (small size cost).
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# Retrofit builds API implementations through dynamic proxies.
-keep,allowobfuscation interface com.fakepangguai.qiekj.data.DeviceApi

# Moshi: KotlinJsonAdapterFactory reflects over model classes/properties at
# runtime, so model names, constructors and property names must survive R8.
-keep class com.fakepangguai.qiekj.data.** { *; }
-keep class kotlin.Metadata { *; }

# ML Kit, CameraX, Compose, Retrofit, Moshi, Tink/security-crypto all ship
# their own consumer ProGuard rules; no blanket keeps are needed for them.
