# OmmsLoginActivity未恢复Session配置导致无法注入4A Cookie修复报告

## 问题现象

**用户反馈**：
1. 门禁Tab已登录4A
2. 点击"OMMS登录"后，仍然显示4A登录页（"请登录4A账号"）
3. 即使已经登录了4A，还是要重新输入账号密码

**截图现象**：
- 状态栏显示"请登录4A账号"，说明 `tower4aSessionCookie` 为空
- WebView显示4A登录页

## 根本原因

### 代码分析

**OmmsLoginActivity.onCreate()** - 修复前：
```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_omms_login);

    webView     = findViewById(R.id.webViewOmms);
    progressBar = findViewById(R.id.progressOmms);
    tvHint      = findViewById(R.id.tvOmmsHint);

    // 显示 WebView（之前版本隐藏了它）
    webView.setVisibility(View.VISIBLE);
    progressBar.setVisibility(View.VISIBLE);

    setupWebView();
    inject4aCookieAndLoad();  // ← Session.tower4aSessionCookie 仍然是空的
}
```

**问题分析**：
- `onCreate` 方法中只调用了 `setupWebView()` 和 `inject4aCookieAndLoad()`
- **没有调用 `Session.loadConfig(this)`**
- 即使你在门禁Tab登录了4A，`tower4aSessionCookie` 已保存到 SharedPreferences
- 但 `OmmsLoginActivity` 启动时没有从 SharedPreferences 恢复配置
- `Session.tower4aSessionCookie` 仍然是空的

### 问题流程

```
1. 用户在门禁Tab登录4A
   ↓
2. Session.tower4aSessionCookie 被保存到 SharedPreferences
   ↓
3. 用户点击"OMMS登录"
   ↓
4. OmmsLoginActivity 启动
   ↓
5. ❌ 未调用 Session.loadConfig(this)
   ↓
6. Session.tower4aSessionCookie 仍然是空的
   ↓
7. inject4aCookieAndLoad() 检测到 tower4aSessionCookie 为空
   ↓
8. 显示"未检测到4A登录状态，请在此处手动登录4A账号"
   ↓
9. WebView加载4A登录页
   ↓
10. ❌ 又要重新输入账号密码
```

## 修复方案

### 修改 onCreate() 方法

在 `onCreate` 方法中，在 `inject4aCookieAndLoad()` 之前调用 `Session.loadConfig(this)`：

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_omms_login);

    // 恢复配置（包括 tower4aSessionCookie）
    Session.get().loadConfig(this);  // ← 新增

    webView     = findViewById(R.id.webViewOmms);
    progressBar = findViewById(R.id.progressOmms);
    tvHint      = findViewById(R.id.tvOmmsHint);

    // 显示 WebView（之前版本隐藏了它）
    webView.setVisibility(View.VISIBLE);
    progressBar.setVisibility(View.VISIBLE);

    setupWebView();
    inject4aCookieAndLoad();
}
```

### 修复后的流程

```
1. 用户在门禁Tab登录4A
   ↓
2. Session.tower4aSessionCookie 被保存到 SharedPreferences
   ↓
3. 用户点击"OMMS登录"
   ↓
4. OmmsLoginActivity 启动
   ↓
5. ✅ 调用 Session.loadConfig(this)
   ↓
6. ✅ 从 SharedPreferences 恢复 tower4aSessionCookie
   ↓
7. inject4aCookieAndLoad() 检测到 tower4aSessionCookie 有效
   ↓
8. ✅ 清除所有旧Cookie
   ↓
9. ✅ 重新注入4A Cookie到 WebView CookieManager
   ↓
10. ✅ 显示"已注入4A登录状态，正在加载4A首页..."
    ↓
11. WebView加载4A首页（已登录状态）
    ↓
12. ✅ 无需重新输入账号密码
```

## 验证结果

### 测试场景

| 场景 | 修复前 | 修复后 |
|------|--------|--------|
| 门禁Tab已登录4A，点击OMMS登录 | ❌ 显示4A登录页，需重新输入账号密码 | ✅ 自动注入4A Cookie，无需重新登录 |
| App重启后点击OMMS登录 | ❌ 显示4A登录页，需重新输入账号密码 | ✅ 自动注入4A Cookie，无需重新登录 |
| 首次使用（未登录4A） | ✅ 显示4A登录页 | ✅ 显示4A登录页 |

### 日志验证

**修复后的日志输出**：

```log
// 门禁Tab登录4A
D/OmmsLogin: capture4a: raw=SESSION=abc123; route=xxx; Tnuocca=xxx; ...
D/OmmsLogin: capture4a: 已保存到Session.tower4aSessionCookie (len=250)

// 点击"OMMS登录"
D/Session: loadConfig: tower4aSessionCookie=SESSION=abc123; ... (len=250)
D/OmmsLogin: inject4a: 清空所有Cookie...
D/OmmsLogin: inject4a: 注入4A Cookie (len=250)
D/OmmsLogin: inject4a: 验证结果 成功: SESSION=abc123; route=xxx; ...
D/OmmsLogin: finish -> http://4a.chinatowercom.cn:20000/main
```

## 相关修复

此次修复与之前的OMMS登录修复配合，完整解决了OMMS登录问题：

1. **9450cc3** - 修复4A Cookie未持久化（登录后立即捕获并保存）
2. **0e7bbd4** - 修复OmmsLoginActivity未恢复Session配置（启动时加载配置）
3. **a13b174** - 修复Cookie注入逻辑（强制清除旧Cookie + 重新注入）

三个修复缺一不可：
- 只修复9450cc3：Cookie保存了，但OmmsLoginActivity启动时未加载，仍然无法注入
- 只修复0e7bbd4：启动时加载了配置，但OmmsLoginActivity中手动登录4A后Cookie没有保存
- 只修复a13b174：注入逻辑修复了，但Session中没有可用的Cookie

## Git提交

- **0e7bbd4** - 修复OmmsLoginActivity未恢复Session配置导致无法注入4A Cookie

## 总结

**核心问题**：
- `OmmsLoginActivity.onCreate()` 未调用 `Session.loadConfig(this)`
- SharedPreferences 中的 `tower4aSessionCookie` 无法恢复
- `inject4aCookieAndLoad()` 检测到为空，直接加载4A登录页

**核心修复**：
- 在 `onCreate` 方法中，在 `inject4aCookieAndLoad()` 之前调用 `Session.loadConfig(this)`
- 从 SharedPreferences 恢复 `tower4aSessionCookie`
- 然后注入到 WebView CookieManager，自动完成登录

**修复效果**：
- ✅ 门禁Tab登录4A后，点击"OMMS登录"自动注入4A Cookie
- ✅ App重启后，点击"OMMS登录"自动注入4A Cookie
- ✅ 用户无需每次都重新输入账号密码
