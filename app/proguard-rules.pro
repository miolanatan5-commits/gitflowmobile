# Annotations R8 complains about that are not needed at runtime
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

# Tink (EncryptedSharedPreferences / MasterKey) — Ignora as dependências opcionais faltantes
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-dontwarn com.google.crypto.tink.util.KeysDownloader**
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class org.conscrypt.** { *; }

# App models
-keep class com.natam.gitflowmobile.data.** { *; }

# Coil and Compose
-dontwarn coil.**
-keep class coil.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

