# Keep Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.internal.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**

# Keep WebView clients
-keepclassmembers class com.gse.securekiosk.webview.** { *; }
