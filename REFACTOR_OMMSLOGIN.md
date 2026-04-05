# OmmsLoginActivity.java 批量替换指南

## 待替换代码位置

OmmsLoginActivity.java 中需要替换以下内容：
1. 所有 `android.util.Log` 为 `Logger`
2. 所有 `mainHandler.post()` 为 `ThreadManager.runOnUiThread()`
3. 移除 `Handler` 和 `Looper` 导入
4. 移除 `mainHandler` 字段声明

## 替换规则

### 1. `android.util.Log.d(TAG, ...)` 替换为 `Logger.d(TAG, ...)`

**查找：** `android\.util\.Log\.(d|e|w|i|v)\(TAG,`

**替换为：** `Logger.\1(TAG,`

**示例：**
```java
// 旧代码
android.util.Log.d(TAG, "nav -> " + url);
android.util.Log.e(TAG, "WebView error " + errorCode + " " + description);

// 新代码
Logger.d(TAG, "nav -> " + url);
Logger.e(TAG, "WebView error " + errorCode + " " + description);
```

### 2. `mainHandler.post(() -> { ... })` 替换为 `ThreadManager.runOnUiThread(() -> { ... })`

**查找：** `mainHandler\.post\(\(\) -> \{`

**替换为：** `ThreadManager.runOnUiThread(() -> {`

**示例：**
```java
// 旧代码
mainHandler.post(() -> hint("✅ 已自动点击「运维监控」，等待跳转 OMMS..."));

// 新代码
ThreadManager.runOnUiThread(() -> hint("✅ 已自动点击「运维监控」，等待跳转 OMMS..."));
```

## 需要手动调整的位置

### 位置1: 第361行附近
```java
// 旧代码
mainHandler.post(() -> hint("✅ 已自动点击「运维监控」，等待跳转 OMMS..."));

// 新代码
ThreadManager.runOnUiThread(() -> hint("✅ 已自动点击「运维监控」，等待跳转 OMMS..."));
```

### 位置2: 第368行附近
```java
// 旧代码
mainHandler.post(() ->

// 新代码
ThreadManager.runOnUiThread(() ->
```

## 依赖移除

完成所有替换后，可以移除以下导入：

```java
import android.os.Handler;
import android.os.Looper;
```

并移除以下字段声明：

```java
private final Handler mainHandler = new Handler(Looper.getMainLooper());
```

## 注意事项

1. **保留 WebView 和 WebChromeClient**：这些与线程管理无关，保留不变
2. **保留 CookieManager 操作**：Cookie 管理与线程无关，保留不变
3. **测试验证**：替换完成后，需要测试以下功能：
   - 4A Cookie 自动注入
   - 自动点击"运维监控"菜单
   - OMMS Cookie 自动捕获
   - 返回结果给调用方

## 执行步骤

1. 使用 Android Studio 的正则替换功能
2. 先替换 `android.util.Log.` 为 `Logger.`
3. 再替换 `mainHandler.post(() -> {` 为 `ThreadManager.runOnUiThread(() -> {`
4. 移除 Handler 和 Looper 的导入
5. 移除 `mainHandler` 字段声明
6. 添加 `import com.towerops.app.util.Logger;`
7. 添加 `import com.towerops.app.util.ThreadManager;`
8. 编译并测试

## 替换统计

- `android.util.Log.d()`：约 10 处
- `android.util.Log.e()`：约 2 处
- `android.util.Log.w()`：约 3 处
- `mainHandler.post()`：约 2 处
