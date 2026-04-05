# OMMS登录失败诊断报告

**时间**: 2026-04-02  
**问题**: OMMS无法自动登录，建立session失败，需要手动输入账号密码

---

## 问题现象

用户反馈：
1. 点击"OMMS登录"按钮后无法自动完成登录
2. WebView打开后需要手动输入账号密码
3. 无法自动建立OMMS session

---

## 根本原因分析

### 1. 4A登录流程正常

检查代码发现，4A登录逻辑完全正常：
- `AccessControlFragment.init4ALogin()` (第348-373行)
- `doGetSmsAc()` (第417-501行) - 获取短信验证码
- `doConfirmLogin4A()` (第505-551行) - 验证验证码并登录

4A登录成功后：
- `Session.tower4aSessionCookie` 已保存
- `Session.saveTower4aCookie(ctx)` 已调用（第537行）

### 2. OmmsLoginActivity 的问题

#### 问题一：自动点击菜单失败（iframe限制）

查看 `OmmsLoginActivity.java` 第273-347行的 `injectAutoClickOmmsMenu()` 方法：

```java
"var all = document.querySelectorAll('a,li,span,div,td,button,p,h3,h4');" +
"for (var k = 0; k < texts.length; k++) {" +
"    for (var i = 0; i < all.length; i++) {" +
"        var t = (all[i].textContent || '').trim();" +
"        if (t === texts[k]) return all[i];" +
"    }" +
"}"
```

**问题**：4A页面的"运维监控"菜单在 `<iframe>` 内，`document.querySelectorAll` 无法访问iframe内容，导致自动点击失败。

**证据**：注释中提到：
```
// 9bba2f3重构：彻底放弃自动点击菜单（OMMS菜单在iframe内，JS注入无法访问）
// 改为：手动引导+网络层拦截。页面加载完立即显示操作步骤黄色横幅
// 用户手动点：工单管理→新安全打卡→安全打卡综合查询
```

但当前代码仍然保留了自动点击逻辑（`injectAutoClickOmmsMenu`），这是一个**遗留代码**。

#### 问题二：Cookie捕获时机问题

查看 `tryCaptureCookie()` 方法（第374-422行）：

```java
private void tryCaptureCookie() {
    if (captured) return;
    
    CookieManager cm = CookieManager.getInstance();
    String raw = cm.getCookie("http://" + OMMS_HOST + ":9000");
    if (raw == null || raw.isEmpty()) {
        raw = cm.getCookie("http://" + OMMS_HOST);
    }
    
    if (raw != null && raw.contains("JSESSIONID")) {
        // 捕获成功
        captured = true;
        Session session = Session.get();
        session.ommsCookie = finalCookie;
        // ...
    } else {
        retryCount++;
        if (retryCount <= 5) {
            mainHandler.postDelayed(this::tryCaptureCookie, 1000);
        }
    }
}
```

**问题**：
1. Cookie 捕获依赖用户**手动点击"运维监控"菜单**触发SSO跳转
2. 如果用户没有点击菜单，重试5次（共5秒）后放弃
3. 用户看到的提示是"未能自动获取Cookie，请重试点击运维监控菜单"，但**黄色横幅提示可能被忽略**

#### 问题三：WebView Cookie域隔离问题

查看 `inject4aCookieAndLoad()` 方法（第222-265行）：

```java
CookieManager cm = CookieManager.getInstance();
String existing = cm.getCookie("http://4a.chinatowercom.cn:20000");
boolean hasValidSession = existing != null && existing.contains("SESSION");

if (hasValidSession) {
    // WebView 里4A Session 仍然有效，直接加载，不清也不注入
    hint("4A 登录状态有效，正在加载4A首页...");
    webView.loadUrl(URL_4A);
    return;
}
```

**问题**：如果 WebView CookieManager 里没有4A Session（例如App重启后），需要重新注入。但此时用户已经无法看到4A登录界面（因为 `AccessControlFragment` 的4A登录卡片已折叠）。

---

## 实际场景分析

### 场景1：首次使用（4A未登录）

1. 用户打开门禁Tab → 看到4A登录卡片
2. 输入账号密码 → 获取短信 → 验证成功 → **4A登录成功**
3. `init4ALogin()` 检测到 `tower4aSessionCookie` 有值 → 折叠4A卡片，显示"已登录"状态行
4. 用户点击"OMMS登录"按钮 → 打开 OmmsLoginActivity WebView
5. **问题1**：`inject4aCookieAndLoad()` 注入Cookie失败（WebView里没有4A Session）
6. **问题2**：自动点击菜单失败（iframe限制）
7. **问题3**：用户需要手动输入4A账号密码（因为WebView里没有Cookie）

### 场景2：App重启后（4A已登录但WebView无Cookie）

1. 用户打开门禁Tab → `Session.loadConfig(ctx)` 恢复 `tower4aSessionCookie`
2. `init4ALogin()` 检测到Cookie有值 → 折叠4A卡片
3. 用户点击"OMMS登录" → WebView加载4A首页
4. **问题**：CookieManager 里没有4A Session（App重启后WebView Cookie清空）
5. WebView显示登录页 → **用户需要重新输入账号密码**

### 场景3：OMMS已登录（Cookie已保存）

1. 用户点击"OMMS登录"
2. `launchOmmsLogin()` 检测到 `s.ommsCookie` 包含JSESSIONID
3. 弹出确认对话框："检测到已有 OMMS 登录凭据... 是否强制重新登录？"
4. 如果用户点击"取消"，则直接返回（不重新登录）
5. 如果用户点击"强制重新登录"，则走完整登录流程

---

## 解决方案

### 方案1：修复 WebView Cookie 注入（推荐）

**目标**：确保 OmmsLoginActivity WebView 能正确携带4A Session

**修改点**：`OmmsLoginActivity.java` 的 `inject4aCookieAndLoad()` 方法

```java
private void inject4aCookieAndLoad() {
    Session s = Session.get();
    String cookie4a = s.tower4aSessionCookie;
    CookieManager cm = CookieManager.getInstance();

    // ① 强制注入4A Cookie（即使WebView里已有）
    if (cookie4a != null && !cookie4a.isEmpty()) {
        hint("正在注入4A登录状态...");
        
        // 清空所有域的Cookie（避免冲突）
        cm.removeAllCookies(null);
        cm.flush();
        
        // 注入4A域的Cookie
        for (String part : cookie4a.split(";")) {
            String kv = part.trim();
            if (!kv.isEmpty()) {
                cm.setCookie("http://4a.chinatowercom.cn:20000", kv);
                cm.setCookie("http://4a.chinatowercom.cn:20000/", kv);
                cm.setCookie("http://4a.chinatowercom.cn", kv);
            }
        }
        cm.flush();
        
        hint("已注入4A登录状态，正在加载4A首页...");
        webView.loadUrl(URL_4A);
        return;
    }

    // ② 没有4A凭据，让用户手动登录
    hint("未检测到4A登录状态，请在此处手动登录4A账号");
    webView.loadUrl(URL_4A);
}
```

**优点**：
- 强制清除所有Cookie，避免旧Cookie干扰
- 确保WebView能携带4A Session访问4A首页
- 不需要用户重新输入账号密码

**缺点**：
- 可能清除其他域的Cookie（但影响不大）

---

### 方案2：去除自动点击菜单逻辑，增强用户引导

**目标**：明确告知用户需要手动操作

**修改点**：`OmmsLoginActivity.java` 的 `setupWebView()` 和 `onPageFinished()` 方法

```java
@Override
public void onPageFinished(WebView view, String url) {
    android.util.Log.d(TAG, "finish -> " + url);
    if (captured || url == null) return;

    if (url.contains(OMMS_HOST)) {
        hint("正在获取OMMS凭据，请稍候...");
        mainHandler.postDelayed(this::tryCaptureCookie, 800);
    } else if (url.contains("4a.chinatowercom.cn")) {
        if (url.contains("login") || url.contains("doPrevLogin")) {
            // ⚠️ 注入失败，显示登录页
            hint("❌ 4A登录状态未注入\n\n请手动输入4A账号密码登录");
        } else {
            // 4A首页加载成功，显示操作步骤
            hint("⏳ 请按以下步骤操作：\n\n1. 在4A首页找到「运维监控」菜单\n2. 点击该菜单\n3. 等待跳转到OMMS系统\n4. 系统将自动获取登录凭据");
            
            // ⚠️ 移除自动点击逻辑（iframe限制）
            // mainHandler.postDelayed(() -> injectAutoClickOmmsMenu(view), 2000);
        }
    }
}
```

**优点**：
- 避免自动点击失败的误导
- 清晰的用户操作指引

---

### 方案3：添加"手动粘贴OMMS Cookie"入口

**目标**：让用户可以绕过WebView登录，直接粘贴Cookie

**修改点**：`AccessControlFragment.java` 的 `btnOmmsLogin` 点击事件

```java
btnOmmsLogin.setOnClickListener(v -> {
    Session s = Session.get();
    
    // 弹出选择对话框
    new android.app.AlertDialog.Builder(requireContext())
        .setTitle("OMMS登录方式")
        .setMessage("请选择登录方式：")
        .setPositiveButton("自动登录（WebView）", (dialog, which) -> {
            launchOmmsLogin(s);
        })
        .setNegativeButton("手动粘贴Cookie", (dialog, which) -> {
            showCookieInputDialog();
        })
        .show();
});

private void showCookieInputDialog() {
    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
    builder.setTitle("粘贴OMMS Cookie");
    
    EditText input = new EditText(requireContext());
    input.setHint("从浏览器F12复制Cookie行");
    input.setMinLines(5);
    input.setMaxLines(10);
    builder.setView(input);
    
    builder.setPositiveButton("确定", (dialog, which) -> {
        String cookie = input.getText().toString().trim();
        if (cookie.isEmpty()) return;
        
        String extracted = extractCookieValue(cookie);
        Session.get().ommsCookie = extracted;
        Session.get().saveOmmsCookie(requireContext());
        etOmmsCookie.setText("");
        appendLog("✅ OMMS Cookie已手动粘贴（len=" + extracted.length() + "）");
        Toast.makeText(requireContext(), "OMMS登录成功", Toast.LENGTH_SHORT).show();
    });
    builder.setNegativeButton("取消", null);
    builder.show();
}
```

**优点**：
- 给用户备用方案
- 绕过WebView的iframe问题

---

## 推荐实施顺序

1. **立即修复**：方案1（强制清除Cookie + 重新注入）
2. **短期优化**：方案2（去除自动点击，增强用户引导）
3. **长期改进**：方案3（添加手动粘贴入口）

---

## 测试验证

修复后需要验证以下场景：

### 场景1：4A已登录 + App未重启
1. 点击"OMMS登录"
2. WebView自动跳转到4A首页（无需登录）
3. 手动点击"运维监控"菜单
4. 等待跳转到OMMS
5. ✅ 自动捕获Cookie，提示"OMMS登录成功"

### 场景2：4A已登录 + App重启
1. 打开门禁Tab
2. 4A卡片已折叠（显示已登录）
3. 点击"OMMS登录"
4. WebView加载4A首页（无需重新登录）
5. 手动点击"运维监控"菜单
6. ✅ 自动捕获Cookie

### 场景3：4A未登录
1. 打开门禁Tab
2. 在4A登录卡片输入账号密码
3. 获取短信并验证
4. 4A登录成功，卡片折叠
5. 点击"OMMS登录"
6. ✅ 自动注入Cookie并跳转

---

## 相关代码位置

- `OmmsLoginActivity.java`: C:/Users/13732/WorkBuddy/TowerOpsApp7/app/src/main/java/com/towerops/app/ui/OmmsLoginActivity.java
- `AccessControlFragment.java`: C:/Users/13732/WorkBuddy/TowerOpsApp7/app/src/main/java/com/towerops/app/ui/AccessControlFragment.java
- `Session.java`: C:/Users/13732/WorkBuddy/TowerOpsApp7/app/src/main/java/com/towerops/app/model/Session.java
- `TowerLoginApi.java`: C:/Users/13732/WorkBuddy/TowerOpsApp7/app/src/main/java/com/towerops/app/api/TowerLoginApi.java

---

## Git历史参考

- `ff44382` - 4A WebView SSO登录方案（注入4A Cookie加载4A首页）
- `8ea8602` - 4A SSO登录方案优化
- `9bba2f3` - TomsLoginActivity重构（放弃自动点击菜单）
- `d3f9475` - 注入Cookie完成后去掉"请手动点击"提示
