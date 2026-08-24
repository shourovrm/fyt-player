# FYT Player release ProGuard/R8 rules
#
# Strategy: CONSERVATIVE for anything dynamic. Third-party libraries that ship
# consumer rules (Media3, OkHttp, Coil, Compose, Room) rely on those rules.
# We only broaden keeps where reflection, native bridges, or runtime class
# generation make the consumer rules insufficient: the extraction engine,
# yt-dlp wrapper, Room app classes, and kotlinx.serialization models.

# ---------------------------------------------------------------------------
# kotlinx.serialization — official recommended keep block
# ---------------------------------------------------------------------------
# Keep `Companion` object fields of serializable classes.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named).
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep serializable fields so JSON mapping survives shrinking.
-keepclassmembers @kotlinx.serialization.Serializable class * {
    <fields>;
}

-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# ---------------------------------------------------------------------------
# Room: explicit keeps for app entities/DAOs/database (Room ships consumer rules)
# ---------------------------------------------------------------------------
-keep class com.fyiplayer.app.data.db.** { *; }

# ---------------------------------------------------------------------------
# Backup/export models are serialized to JSON
# ---------------------------------------------------------------------------
-keep class com.fyiplayer.app.data.backup.BackupModel { *; }

# ---------------------------------------------------------------------------
# Extraction engine: NewPipe/PipePipe fork + Rhino JS runtime
# ---------------------------------------------------------------------------
-keep class org.schabi.newpipe.** { *; }
-keep class org.pipepipe.** { *; }
-keep class org.mozilla.javascript.** { *; }

# ---------------------------------------------------------------------------
# yt-dlp Android wrapper: Python runtime + ffmpeg native bridge
# ---------------------------------------------------------------------------
-keep class com.yausername.** { *; }
# commons-compress registers zip extra-field classes by reflection (ExtraFieldUtils <clinit>);
# shrinking them crashes first-run python unzip with "class ... is not a concrete class".
-keep class org.apache.commons.compress.archivers.zip.** { *; }

# ---------------------------------------------------------------------------
# Native methods
# ---------------------------------------------------------------------------
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---------------------------------------------------------------------------
# Kotlin metadata used by serializer/companion lookups
# ---------------------------------------------------------------------------
-keep class kotlin.Metadata { *; }

# Diagnostics log ExtractionError subclass NAMES only (never messages -- they can carry URLs).
# Minified they come out as "(h)", which tells a bug report nothing. Names only, no members.
-keepnames class com.fyiplayer.app.core.ExtractionError$* { }
