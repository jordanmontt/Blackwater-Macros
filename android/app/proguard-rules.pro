# R8 rules for the release build. Most libraries (Compose, Room, Retrofit, OkHttp,
# kotlinx-serialization, WorkManager, CameraX) ship their own consumer rules.

# LiteRT-LM (on-device AI): the native engine calls back into these classes by name (JNI)
# and reads/writes them with Gson.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class * implements com.google.ai.edge.litertlm.MessageCallback { *; }
-dontwarn com.google.ai.edge.litertlm.**

# zxing-cpp (barcodes): JNI results are built from these classes by name.
-keep class zxingcpp.** { *; }

# Retrofit reads a suspend call's response type from the method's generic signature. A
# response class the code never reads (updateSettings' SettingsResponse) is otherwise removed
# by R8, the signature becomes Continuation<Object> and the call fails before the request
# with «Unable to create converter for class java.lang.Object»: the profile never synced and
# every sync run stopped there. Keep every wire model, read or not (names may still change).
-keep,allowobfuscation @kotlinx.serialization.Serializable class com.blackwatermacros.app.data.** { *; }

# Keep the names readable in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
