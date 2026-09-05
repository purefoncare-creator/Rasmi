# Add project specific ProGuard rules here.

# Keep line numbers for better crash reports (but hide source file names for security)
-keepattributes LineNumberTable
-renamesourcefileattribute ""

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt — Core
-keepclasseswithmembers class * {
    @dagger.* <methods>;
}
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Hilt — ViewModels: keep @HiltViewModel classes so the ViewModel multibinding
# map (via @LazyClassKey) can resolve them at runtime.
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Hilt — Generated factories and modules: R8 with -repackageclasses and
# aggressive optimization can break Dagger's internal class resolution.
-keep class **_Factory { *; }
-keep class **_HiltModules* { *; }
-keep @dagger.internal.DaggerGenerated class * { *; }

# Hilt — Multibinding key providers (LazyClassKey stores class names as strings)
-keep class dagger.hilt.android.internal.lifecycle.** { *; }
-keep class dagger.internal.** { *; }
-keep class * implements dagger.internal.Factory { *; }

# Kotlin Serialization
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    @kotlinx.serialization.Serializable <methods>;
}
-keep class *$$serializer { *; }
-keepclassmembers class * {
    *** Companion;
    *** $serializer;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Retrofit & OkHttp (if used)
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Compose — keep only essential platform bindings, let R8 tree-shake the rest
# ✅ FIXED: Removed overly broad -keep that prevented tree-shaking entire Compose runtime
-keep class androidx.compose.ui.platform.ComposeView { *; }
-keep class androidx.compose.ui.platform.AbstractComposeView { *; }
-dontwarn androidx.compose.**

# Prevent crashes from missing classes (deduplicated with OkHttp rules above)
-dontwarn org.bouncycastle.jsse.**

# ContentProvider crash prevention
-keepclassmembers class * extends android.content.ContentProvider {
    public <init>(...);
}

# BroadcastReceiver crash prevention
-keepclassmembers class * extends android.content.BroadcastReceiver {
    public <init>(...);
}

# Service crash prevention
-keepclassmembers class * extends android.app.Service {
    public <init>(...);
}

# ✅ FIXED: Do NOT strip Kotlin null-safety checks — they protect against
# silent corruption from platform-type nulls (e.g. ContentResolver, Telephony).
# Previously this was stripping checkNotNull/checkParameterIsNotNull which is dangerous.

# Keep data classes for serialization
-keepclassmembers class com.rasmi.purevon.data.local.entity.** {
    <fields>;
    <init>(...);
}

-keepclassmembers class com.rasmi.purevon.domain.model.** {
    <fields>;
    <init>(...);
}

# Navigation crash prevention
-keep class androidx.navigation.** { *; }
-keepnames class androidx.navigation.fragment.NavHostFragment

# Navigation type-safe routes: keep @Serializable Screen classes and their
# serializers so Navigation 2.8+ can deserialize route arguments correctly.
-keep class com.rasmi.purevon.presentation.navigation.Screen { *; }
-keep class com.rasmi.purevon.presentation.navigation.Screen$* { *; }

# Lifecycle crash prevention
-keep class * implements androidx.lifecycle.LifecycleObserver {
    <init>(...);
}

-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# WorkManager crash prevention
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker {
    <init>(...);
}

# Prevent ContentResolver query crashes
-keepclassmembers class android.content.ContentResolver {
    public android.database.Cursor query(...);
}

# Keep Cursor column access methods
-keepclassmembers class android.database.Cursor {
    public int getColumnIndex(...);
    public int getColumnIndexOrThrow(...);
    public ** get*(...);
}

# ========================================
# SECURITY & OBFUSCATION RULES
# ========================================

# Remove verbose/debug/info logging in release builds to prevent PII leaks
# ✅ FIXED: Keep Log.w and Log.e for production crash diagnostics
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Remove debug logging from our custom logger too
-assumenosideeffects class com.rasmi.purevon.util.DebugLogger {
    public static *** d(...);
    public static *** i(...);
    public static *** sensitive(...);
    public static *** dMasked(...);
}

# ✅ FIXED: Use -keepclassmembers (not -keep) to allow class name obfuscation
# while preserving member signatures needed for Hilt injection
-keepclassmembers class com.rasmi.purevon.data.repository.**Impl {
    <init>(...);
}

# Keep use case constructors for Hilt but obfuscate class names
-keepclassmembers class com.rasmi.purevon.domain.usecase.** {
    <init>(...);
}

# Keep ViewModels structure but obfuscate internals
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    private <fields>;
    private <methods>;
}

# Obfuscate receivers to prevent reverse engineering
-keepclassmembers class com.rasmi.purevon.receiver.** {
    private <methods>;
}

# Keep public API but obfuscate implementation
-keepclasseswithmembernames class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Aggressively obfuscate internal classes
-repackageclasses 'a'
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Advanced optimization
-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose

# Prevent leaking class names through reflection
-keepattributes Signature
-keepattributes *Annotation*

# Keep encryption class constructors for instantiation, obfuscate internals
-keepclassmembers class com.rasmi.purevon.util.security.DataEncryptionManager {
    <init>(...);
}

# Obfuscate database queries to hide data structure
-keepclassmembers class com.rasmi.purevon.data.local.dao.** {
    abstract <methods>;
}

-dontwarn javax.lang.model.**

# SQLCipher (legacy package, kept for safety)
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# SQLCipher Android (net.zetetic:sqlcipher-android 4.x)
# JNI native methods must keep their original names/signatures, otherwise
# UnsatisfiedLinkError: No implementation found for ...nativeOpen(...)
-keep class net.zetetic.** { *; }
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-keepclassmembers class net.zetetic.database.** {
    native <methods>;
}
-keepclasseswithmembernames class net.zetetic.database.** {
    native <methods>;
}
-dontwarn net.zetetic.**

# ─────────────────────────────────────────────────────────────
# CameraX + Compose PreviewView — keep bind internals under R8.
# The obfuscated stack showed an NPE inside Compose's layer tracking
# (ChildLayerDependenciesTracker) plus kotlin.jvm.functions.Function1
# when PreviewView bound to the lifecycle in a minified build. These
# rules prevent R8/`-repackageclasses` from breaking the view↔Compose
# holder glue and the CameraX / ML Kit scanning entry points.
# ─────────────────────────────────────────────────────────────

# PreviewView relies on reflection-created holders bound to Compose layers;
# keep its static surface-holder companion and lifecycle wiring signatures.
-keep class androidx.camera.view.** { *; }

# Keep every CameraX bound class + the transform/surface internals that
# bindToLifecycle instantiates at runtime via generated factories.
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.camera2.** { *; }

# ML Kit barcode scanning is a JNI-loaded model; keep names/methods that
# the native runtime resolves and the public scanner API the analyzer uses.
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.mlkit.vision.common.** { *; }
-keepnames class com.google.android.datatransport.** { *; }

# ML Kit's MlKitInitProvider builds its component graph at startup via
# reflection over com.google.mlkit.common.sdkinternal.* (DI). R8 obfuscates/
# drops members R8 cannot see used reflectively, producing
# "Unsatisfied dependency for component ... MlKitInitProvider.onCreate".
# Because this project enables -repackageclasses 'a' + -allowaccessmodification,
# R8 repackages and rewrites these reflection-based components, which ML Kit
# then cannot locate. Pin the entire ML Kit tree (all namespaces) so R8 leaves
# the reflective DI name/signature contracts intact.
-keep class com.google.mlkit.** { *; }
-keep class com.google.firebase.ml.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode_bundled.** { *; }
-keepclassmembers class * extends com.google.android.gms.internal.mlkit_vision_barcode_bundled.zzeh {
    <fields>;
}
-keep @com.google.android.apps.common.proguard.UsedBy* class * { <init>(); }
-keepclassmembers class * {
    @com.google.android.apps.common.proguard.UsedBy* *;
}
-keepattributes RuntimeVisible*Annotation*
-keep @interface com.google.android.apps.common.proguard.UsedBy*

# Compose: keep the AndroidView/PreviewView composition holder factory so the
# runtime view interop and layer-tracking helpers survive minification.
-keep class androidx.compose.ui.viewinterop.** { *; }
-keep class androidx.compose.ui.platform.AndroidCompositionLocalsKt { *; }

# kotlin.jvm.functions.Function1 was the visible NPE frame; kotlin stdlib
# functions are kept by default, but this guards the generic bridge frames.
-keep class kotlin.jvm.functions.** { *; }

-dontwarn androidx.camera.**
-dontwarn androidx.compose.ui.viewinterop.**
-dontwarn com.google.mlkit.vision.**
-dontwarn com.google.android.datatransport.**
