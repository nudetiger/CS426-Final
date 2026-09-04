# Learning Mocha ProGuard rules (Phase 5 will finalize for release builds).

# Gson / Retrofit DTOs
-keepattributes Signature, *Annotation*
-keep class com.cs426.learningmocha.net.dto.** { *; }
-keep class com.cs426.learningmocha.ai.protocol.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**
