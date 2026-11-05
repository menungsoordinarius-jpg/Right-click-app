# ProGuard & R8 rules for RightClickDash

# Preserve Shizuku IPC classes and UserService
-keep class * extends rikka.shizuku.Shizuku$* { *; }
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }

# Preserve libsu RootService and internal IPC handlers
-keep class com.topjohnwu.superuser.** { *; }
-keepclassmembers class * extends com.topjohnwu.superuser.ipc.RootService {
    public <init>();
    public <init>(...);
}

# Preserve AIDL interfaces and InputServiceImpl classes
-keep class com.example.IInputService { *; }
-keep class com.example.IInputService$* { *; }
-keep class com.example.backend.InputServiceImpl { *; }
-keep class com.example.backend.ShizukuUserService { *; }
-keep class com.example.backend.RootInputService { *; }

# Preserve constructor for Shizuku UserService dynamic instantiation
-keepclasseswithmembers class * {
    public <init>(android.content.Context);
}

# Preserve HiddenApiBypass
-keep class org.lsposed.hiddenapibypass.** { *; }
