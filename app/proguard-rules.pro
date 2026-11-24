# R8 / ProGuard Security Hardening for Mon Agent Immo

# Flatten package hierarchy & obscure internal structure against reverse engineering
-repackageclasses 'o'
-allowaccessmodification
-renamesourcefileattribute SourceFile
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Strip verbose debug/info/warning logs in release builds to prevent metadata leaks
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# Preserve classes & members annotated with @Keep (Firestore & Room models)
-keep class androidx.annotation.Keep
-keep @androidx.annotation.Keep class * {
    <fields>;
    <methods>;
}
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Preserve Firebase Firestore models & annotations
-keepclassmembers class * {
    @com.google.firebase.firestore.DocumentId *;
    @com.google.firebase.firestore.ServerTimestamp *;
    @com.google.firebase.firestore.PropertyName *;
    @com.google.firebase.firestore.IgnoreExtraProperties *;
}
-keep class com.example.data.model.** { *; }

# Preserve Room database & entities
-keep class * extends androidx.room.RoomDatabase
-keep class com.example.data.local.** { *; }

# Preserve Kotlin metadata & Coroutines
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.reflect.**
-dontwarn kotlinx.coroutines.**

# Preserve Credential Manager & Google Identity Services
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
