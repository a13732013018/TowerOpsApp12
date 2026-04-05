# AccessControlFragment.java 批量替换指南

## 待替换代码位置

AccessControlFragment.java 中剩余的 `new Thread()` 和 `mainHandler.post()` 需要批量替换为 ThreadManager 的方法。

## 替换规则

### 1. `new Thread(() -> { ... }).start()` 替换为 `ThreadManager.execute(() -> { ... })`

**查找：** `new Thread\(\(\) -> \{`

**替换为：** `ThreadManager.execute(() -> {`

**示例：**
```java
// 旧代码
new Thread(() -> {
    AccessControlApi.doOpenDoor(objId, doorId);
    mainHandler.post(() -> {
        adapter.updateItem(pos, item);
    });
}).start();

// 新代码
ThreadManager.execute(() -> {
    AccessControlApi.doOpenDoor(objId, doorId);
    ThreadManager.runOnUiThread(() -> {
        adapter.updateItem(pos, item);
    });
});
```

### 2. `mainHandler.post(() -> { ... })` 替换为 `ThreadManager.runOnUiThread(() -> { ... })`

**查找：** `mainHandler\.post\(\(\) -> \{`

**替换为：** `ThreadManager.runOnUiThread(() -> {`

**示例：**
```java
// 旧代码
mainHandler.post(() -> {
    adapter.updateItem(pos, item);
});

// 新代码
ThreadManager.runOnUiThread(() -> {
    adapter.updateItem(pos, item);
});
```

## 需要手动调整的位置

由于文件中有多个嵌套的 `new Thread()`，需要手动逐步替换：

### 位置1: doConfirmLogin4A() 方法（第170行附近）
- 已完成 ✓

### 位置2: doGetSmsCode() 方法（第437行附近）
- 已完成 ✓

### 位置3: doConfirmLogin4A() 方法（第525行附近）
- 待替换

### 位置4: startMonitor() 方法（第623行附近）
- 待替换

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

1. **保留 ExecutorService**：文件中已有的 `queryPool`（ExecutorService）是专用的线程池，不需要替换为 ThreadManager。
2. **保留 Semaphore**：`querySemaphore` 是并发控制锁，与线程管理无关，保留不变。
3. **测试验证**：替换完成后，需要测试所有功能是否正常：
   - 手动远程开门
   - 4A登录（获取验证码/确认登录）
   - 自动监控（开始/停止）

## 执行步骤

1. 使用 Android Studio 的正则替换功能
2. 先替换 `new Thread(() -> {` 为 `ThreadManager.execute(() -> {`
3. 再替换 `mainHandler.post(() -> {` 为 `ThreadManager.runOnUiThread(() -> {`
4. 移除 Handler 和 Looper 的导入
5. 移除 `mainHandler` 字段声明
6. 编译并测试
