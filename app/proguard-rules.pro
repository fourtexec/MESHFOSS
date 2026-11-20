# Mesh Ranker — правила ProGuard/R8 для релизной сборки

# Классы, к которым обращаются по имени из WebView (@JavascriptInterface)
-keepclassmembers class ru.mesh.foss.feature.login.MeshLoginActivity$LoginBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Ядро и клиент работают с org.json по строковым ключам — сужаем оптимизации
-keep class ru.mesh.foss.core.MeshCore { *; }
-keep class ru.mesh.foss.data.MeshClient { *; }
-keep class org.json.** { *; }

# DTO Gson/Retrofit и имена полей @SerializedName
-keep class ru.mesh.foss.data.model.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# WorkManager создаёт воркер по имени класса
-keep class ru.mesh.foss.app.TokenRefreshWorker { *; }
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Glance-виджет создаётся системой по имени receiver
-keep class ru.mesh.foss.widget.MeshWidgetReceiver { *; }

# AndroidX Security (EncryptedSharedPreferences) использует Tink
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Kotlin metadata и корутины
-dontwarn kotlinx.coroutines.**
