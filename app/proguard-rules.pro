# Keep OkHttp + WebView JS interface names stable
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-dontwarn okhttp3.**
-dontwarn okio.**
