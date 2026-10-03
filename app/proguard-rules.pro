# SmartFlow R8 rules.
# Models are serialized by hand with org.json (no reflection), so nothing to keep there.
# Manifest components (activities, services, receivers, tile) are kept by AAPT automatically.

# Enum names are persisted (TriggerType.valueOf etc.) - keep them stable.
-keepclassmembers enum com.nahuel.homeflow.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# OkHttp ships its own consumer rules; silence optional platform warnings.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
