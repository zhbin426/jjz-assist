# ML Kit 自带 consumer rules，这里只兜底关掉第三方警告，不做全量 keep（否则 R8 白干）
-dontwarn com.google.android.gms.**
-dontwarn com.google.mlkit.**
-dontwarn javax.annotation.**

# 组件/接收器由 Manifest 声明，AGP 自动保留；此处仅保 App 内被字符串引用的资源名
-keepclassmembers class com.jjz.assist.ShapeArt {
    private static *** glass;
}
