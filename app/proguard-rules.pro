# Project-specific ProGuard / R8 rules for Itur.
#
# Firebase, Hilt, Compose, MLKit, Kotlin Coroutines, and Okio bundle their own
# consumer ProGuard rules via their AARs, so only project-specific additions
# are needed here.

##---- Stack-trace quality ----##
# Preserve source file names and line numbers in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

##---- Wire (Protocol Buffers) ----##
# Wire bundles consumer rules in wire-runtime, but keep the project-generated
# message class explicitly: it is accessed only through the generic
# Serializer<IturPreferences> interface, which R8 cannot trace statically.
-keepclassmembers class * extends com.squareup.wire.Message {
    public static ** ADAPTER;
}
-keep class com.nohex.itur.core.datastore.IturPreferences { *; }

##---- ZXing / zxing-android-embedded ----##
# zxing-android-embedded does not ship complete consumer rules.
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }

##---- MapLibre native (JNI) ----##
# MapLibre bundles its own consumer rules; keep JNI entry points.
-keepclasseswithmembernames class * {
    native <methods>;
}

##---- Kotlin value classes ----##
# Keep the box representation of @JvmInline value classes.  These are used as
# type arguments in generic collections and Firestore queries where R8 cannot
# prove the box type is unreachable.
-keep @kotlin.jvm.JvmInline class com.nohex.itur.core.domain.id.** { *; }
