# Immersive-Me — قواعد ProGuard (معطّلة حالياً)
-keep class com.minis.imt.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
