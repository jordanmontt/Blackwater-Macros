# R8 rules for the release build. Most libraries (Compose, Room, Retrofit, OkHttp,
# kotlinx-serialization, WorkManager, CameraX) ship their own consumer rules.

# LiteRT-LM (on-device AI): the native engine calls back into these classes by name (JNI)
# and reads/writes them with Gson.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class * implements com.google.ai.edge.litertlm.MessageCallback { *; }
-dontwarn com.google.ai.edge.litertlm.**

# zxing-cpp (barcodes): JNI results are built from these classes by name.
-keep class zxingcpp.** { *; }

# Our wire models are @Serializable (the serialization plugin keeps what it needs); keep the
# names readable in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
