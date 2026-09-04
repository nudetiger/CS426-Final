# Learning Mocha R8 / ProGuard rules.
#
# `minifyEnabled` is false for the submitted release build (see app/build.gradle for why), so
# nothing here fires today. The rules are kept correct and complete so that flipping the flag is
# a one-line change that can be re-tested against a live AI round trip.

# --- Gson-reflected models -------------------------------------------------------------------
# Gson maps JSON keys onto field *names*, so any renamed field deserializes to null. None of
# these classes carry @SerializedName, which makes obfuscation a silent failure: the AI reply
# still parses, every field is null, and ActionParser falls back to "plain answer".
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# AI envelope + action protocol (ai/protocol/Envelope.kt: Envelope, ContextQuery, KbAction).
-keep class com.cs426.learningmocha.ai.protocol.** { *; }
# Gateway DTOs and SSE frame payloads live directly in net/, not in a net/dto sub-package.
-keep class com.cs426.learningmocha.net.** { *; }
# .mocha.json backup envelope (backup/BackupSnapshot.kt).
-keep class com.cs426.learningmocha.backup.BackupSnapshot { *; }
-keep class com.cs426.learningmocha.backup.BackupSnapshot$* { *; }
# Room entities: written by hand into the backup JSON and read back by util/ImportJsonReader.
-keep class com.cs426.learningmocha.data.local.entity.** { *; }

# util/ImportJsonReader.parseEnum resolves NodeType / LearningStatus / ResourceType through
# Enum.valueOf, and Room's NodeConverters do the same for every stored row.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Room ------------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- Markwon ---------------------------------------------------------------------------------
# commonmark resolves node visitors and parser extensions by type.
-keep class org.commonmark.** { *; }
-dontwarn io.noties.markwon.**

# --- Retrofit / OkHttp -----------------------------------------------------------------------
# Retrofit builds MochaApi from its annotated interface at runtime; OkHttp and Retrofit ship
# their own consumer rules, these cover the parts that depend on this app's own types.
-keep,allowobfuscation interface com.cs426.learningmocha.net.MochaApi
-keepclasseswithmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Kotlin ----------------------------------------------------------------------------------
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
