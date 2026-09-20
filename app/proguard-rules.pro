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

# ============================================================
# AndroidX WorkManager / Startup — R8 Runtime Protection
# ============================================================

-keep class androidx.work.** { *; }
-keep class androidx.startup.** { *; }

-keep class androidx.work.impl.WorkDatabase { *; }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }

-keep class androidx.work.impl.WorkManagerInitializer { *; }

-keepclassmembers class * extends androidx.work.ListenableWorker {
    <init>(...);
}

-keepclassmembers class * extends androidx.work.Worker {
    <init>(...);
}

-keepnames class androidx.work.**
-keepnames class androidx.startup.**

# ============================================================
# 👑 Nexus / Royal Engine — Core System Protection Rules
# ============================================================

# 1. حماية الحزمة بالكامل (Package-Wide Total Protection)
# تضمن حماية كافة الكلاسات، الدوال، والمجالات تحت الحزمةcom.store.app
-keep class com.store.app.** { *; }
-keepclassmembers class com.store.app.** { *; }
-keepclasseswithmembers class com.store.app.** { *; }

# 2. حماية كاملة لـ JavaScript Interfaces & Dynamic Bridges
# حماية جميع الدوال المربوطة بالجافاسكريبت منعاً لانهيار الاتصال بالـ Web Engine
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 3. حماية C++ Native Libraries (JNI / WebAssembly Integration)
# تضمن عدم تجريد أو تغيير أسماء دوال الجافا الصريحة المربوطة بـ C++ / Wasm Core
-keepclasseswithmembernames class * {
    native <methods>;
}

# 4. حماية الـ Dynamic Modules / VIP Injection (Reflection Safety)
# تضمن عمل الكلاسات التي تُستدعى عبر Class.forName() بدون أي ClassNotFoundException
-keep class * implements java.lang.reflect.InvocationHandler
-keep class com.store.app.modules.** { *; }

# 5. الحفاظ على الميتاداتا الهيكلية وتتبع الـ Crash Traces
# تضمن استقرار السلاسل وتتبع أخطاء المحرك بدقة في بيئة الإنتاج Release
-keepattributes Signature, InnerClasses, EnclosingMethod, Annotation, Exceptions, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# 6. منع تحسينات R8 الهجومية التي قد تكسر مسارات خيوط الرسم (Threading Barrier Protection)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
