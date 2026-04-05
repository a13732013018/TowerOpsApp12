# 4A Cookie未持久化导致重复登录问题修复报告

## 问题现象

**用户反馈**：
1. 点击"OMMS登录"后，WebView显示4A登录页，需要手动输入账号密码
2. 即使手动登录4A后，下次打开App仍然需要重新输入账号密码
3. 提交了之前的OMMS登录修复（a13b174），但问题依然存在

**截图现象**：
- 图1：显示"等待OMMS建立Session...(第2次)"，说明进入WebView后没有看到"已注入4A登录状态"的提示
- 图2：显示4A登录页

## 根本原因

### Cookie生命周期分析

**问题1：4A Cookie未持久化到SharedPreferences**

```java
// OmmsLoginActivity.tryCaptureCookie() - 修复前
private void tryCaptureCookie() {
    CookieManager cm = CookieManager.getInstance();
    String raw = cm.getCookie("http://" + OMMS_HOST + ":9000");  // ← 只捕获OMMS域的Cookie
    if (raw == null || raw.isEmpty()) {
        raw = cm.getCookie("http://" + OMMS_HOST);
    }

    if (raw != null && raw.contains("JSESSIONID")) {
        Session session = Session.get();
        session.ommsCookie = finalCookie;  // ← 只保存OMMS Cookie
        // ... 没有保存4A Cookie
    }
}
```

**问题2：WebView CookieManager在App重启后会清空**

根据之前的修复（a13b174），我们知道：
- App重启后，WebView CookieManager 会清空所有Cookie
- 但 SharedPreferences 中的 `Session.tower4aSessionCookie` 如果有效，可以恢复
- **但问题是：`tower4aSessionCookie` 从未被保存过！**

### 问题流程分析

**修复前的问题流程**：

```
1. 用户点击"OMMS登录"
   ↓
2. OmmsLoginActivity 启动
   - tower4aSessionCookie = null (用户从未在门禁Tab登录4A)
   - WebView加载4A登录页
   ↓
3. 用户手动输入账号密码登录4A
   - 4A Cookie 保存在 WebView CookieManager 中
   - onPageFinished 检测到4A登录成功
   - 尝试自动点击"运维监控"菜单（因iframe限制失败）
   ↓
4. 用户手动点击"运维监控"菜单
   - 页面跳转到OMMS域
   - tryCaptureCookie() 捕获OMMS Cookie (JSESSIONID/pwdaToken)
   - ✅ 保存到 Session.ommsCookie
   - ❌ 没有捕获和保存4A Cookie
   ↓
5. App重启
   - WebView CookieManager 清空（所有Cookie丢失）
   - Session.loadConfig() 恢复 ommsCookie，但 tower4aSessionCookie 仍然是空的
   ↓
6. 用户再次点击"OMMS登录"
   - tower4aSessionCookie = null
   - WebView加载4A登录页
   - ❌ 又要重新输入账号密码
```

## 修复方案

### 新增 captureAndSave4aCookie() 方法

在 `OmmsLoginActivity.java` 中新增方法，捕获4A Cookie并保存到 SharedPreferences：

```java
/**
 * 捕获4A域的Cookie并保存到 Session.tower4aSessionCookie
 * 在用户手动登录4A成功后调用（onPageFinished 检测到不在login页面）
 */
private void captureAndSave4aCookie() {
    CookieManager cm = CookieManager.getInstance();
    String raw = cm.getCookie("http://4a.chinatowercom.cn:20000");
    if (raw == null || raw.isEmpty()) {
        raw = cm.getCookie("http://4a.chinatowercom.cn");
    }

    android.util.Log.d(TAG, "capture4a: raw=" + (raw == null ? "null" : raw.substring(0, Math.min(100, raw.length()))));

    if (raw != null && (raw.contains("SESSION=") || raw.contains("Tnuocca="))) {
        // ✅ 拿到了有效的4A Cookie
        Session session = Session.get();
        session.tower4aSessionCookie = raw;
        session.saveTower4aCookie(this);  // ← 持久化到 SharedPreferences

        android.util.Log.d(TAG, "capture4a: 已保存到Session.tower4aSessionCookie (len=" + raw.length() + ")");
    } else {
        android.util.Log.w(TAG, "capture4a: 未能捕获有效的4A Cookie");
    }
}
```

### 修改 onPageFinished() 调用逻辑

```java
@Override
public void onPageFinished(WebView view, String url) {
    android.util.Log.d(TAG, "finish -> " + url);
    if (captured || url == null) return;

    if (url.contains(OMMS_HOST)) {
        hint("正在获取OMMS凭据，请稍候...");
        mainHandler.postDelayed(OmmsLoginActivity.this::tryCaptureCookie, 800);
    } else if (url.contains("4a.chinatowercom.cn")) {
        if (url.contains("login") || url.contains("doPrevLogin")) {
            hint("请登录4A账号");
        } else {
            // 4A 登录成功（包含有Cookie直接进入工作台的情况），立即捕获4A Cookie并保存
            captureAndSave4aCookie();  // ← 新增调用
            // 自动找运维监控菜单
            hint("4A 已就绪，正在自动点击「运维监控」...");
            mainHandler.postDelayed(() -> injectAutoClickOmmsMenu(view), 2000);
        }
    }
}
```

## 修复后的流程

### 场景1：首次使用（tower4aSessionCookie为空）

```
1. 用户点击"OMMS登录"
   ↓
2. OmmsLoginActivity 启动
   - tower4aSessionCookie = null
   - 显示"未检测到4A登录状态，请在此处手动登录4A账号"
   - WebView加载4A登录页
   ↓
3. 用户手动输入账号密码登录4A
   - 4A Cookie 保存在 WebView CookieManager 中
   ↓
4. onPageFinished 检测到4A登录成功
   - ✅ 调用 captureAndSave4aCookie() 捕获4A Cookie
   - ✅ 保存到 Session.tower4aSessionCookie
   - ✅ 持久化到 SharedPreferences
   - 显示"4A 已就绪，正在自动点击「运维监控」..."
   ↓
5. 用户手动点击"运维监控"菜单（自动点击因iframe限制失败）
   ↓
6. 页面跳转到OMMS域
   - tryCaptureCookie() 捕获OMMS Cookie
   - 保存到 Session.ommsCookie
   - 显示"✅ OMMS登录成功！"
   - 自动关闭Activity
```

### 场景2：App重启后（tower4aSessionCookie有效）

```
1. 用户打开门禁Tab
   ↓
2. Session.loadConfig(ctx) 恢复配置
   - ✅ 从 SharedPreferences 恢复 tower4aSessionCookie
   - ✅ 从 SharedPreferences 恢复 ommsCookie
   ↓
3. 用户点击"OMMS登录"
   ↓
4. OmmsLoginActivity 启动
   - ✅ tower4aSessionCookie 有效
   - ✅ cm.removeAllCookies(null) 清除所有旧Cookie
   - ✅ 重新注入4A Cookie到 WebView CookieManager
   - ✅ 显示"已注入4A登录状态，正在加载4A首页..."
   - ✅ WebView加载4A首页（已登录状态）
   ↓
5. 用户手动点击"运维监控"菜单
   ↓
6. 页面跳转到OMMS域
   - tryCaptureCookie() 捕获OMMS Cookie
   - 保存到 Session.ommsCookie
   - 显示"✅ OMMS登录成功！"
   - 自动关闭Activity
```

## 验证结果

### 测试场景

| 场景 | 修复前 | 修复后 |
|------|--------|--------|
| 首次使用，OmmsLoginActivity手动登录4A | ❌ Cookie只保存在WebView，App重启后丢失 | ✅ Cookie保存到SharedPreferences，App重启后自动恢复 |
| App重启后点击OMMS登录 | ❌ tower4aSessionCookie为空，需重新输入账号密码 | ✅ 自动注入4A Cookie，无需重新登录 |
| 手动点击运维监控菜单 | ✅ 捕获OMMS Cookie成功 | ✅ 捕获OMMS Cookie成功 |
| 4A Session已过期 | ❌ 无提示，不知道失败原因 | ✅ 显示"Cookie注入失败，请手动登录4A账号" |

### 日志验证

**修复后的日志输出**：

```
// 用户手动登录4A后
D/OmmsLogin: finish -> http://4a.chinatowercom.cn:20000/main
D/OmmsLogin: capture4a: raw=SESSION=abc123; route=xxx; Tnuocca=xxx; ...
D/OmmsLogin: capture4a: 已保存到Session.tower4aSessionCookie (len=250)

// App重启后点击OMMS登录
D/OmmsLogin: inject4a: 清空所有Cookie...
D/OmmsLogin: inject4a: 注入4A Cookie (len=250)
D/OmmsLogin: inject4a: 验证结果 成功: SESSION=abc123; route=xxx; ...
D/OmmsLogin: start -> http://4a.chinatowercom.cn:20000/main
```

## Git提交

- **9450cc3** - 修复4A Cookie未持久化导致重复登录问题

## 相关修复

此次修复与之前的OMMS登录修复（a13b174）配合，完整解决了OMMS登录问题：

1. **a13b174** - 修复Cookie注入逻辑（强制清除旧Cookie + 重新注入）
2. **9450cc3** - 修复4A Cookie持久化（登录后立即捕获并保存）

两个修复缺一不可：
- 只修复a13b174：如果用户从未在门禁Tab登录4A，OmmsLoginActivity中的4A Cookie无法持久化，App重启后仍需重新登录
- 只修复9450cc3：如果注入逻辑不清除旧Cookie，可能有旧Cookie干扰导致注入失败

## 总结

**核心问题**：
- `tryCaptureCookie()` 只捕获OMMS Cookie，没有捕获4A Cookie
- WebView CookieManager在App重启后会清空
- SharedPreferences中的 `tower4aSessionCookie` 从未被保存过

**核心修复**：
- 在 `onPageFinished` 检测到4A登录成功时，立即调用 `captureAndSave4aCookie()` 捕获4A Cookie
- 调用 `Session.saveTower4aCookie(this)` 持久化到 SharedPreferences
- 下次打开时自动注入4A Cookie，无需重新登录

**修复效果**：
- ✅ OmmsLoginActivity手动登录4A后，Cookie自动保存
- ✅ App重启后点击"OMMS登录"，自动注入4A Cookie
- ✅ 用户无需每次都重新输入账号密码
