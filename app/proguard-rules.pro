# JavascriptInterface methods must keep their names or the page bridge breaks.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.m3browser.BrowserController$JsBridge { *; }

# ML Kit loads these classes by reflection.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
