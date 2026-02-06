# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# Fix R8 error: Missing class org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.**

# Edge TTS: Keep data classes for Gson deserialization
-keep class com.samuel.readaloud.edgetts.EdgeVoice { *; }
-keep class com.samuel.readaloud.edgetts.TTSChunk { *; }
-keep class com.samuel.readaloud.edgetts.TTSChunk$** { *; }
-keep class com.samuel.readaloud.edgetts.TTSConfig { *; }
-keep class com.samuel.readaloud.edgetts.CommunicateState { *; }

