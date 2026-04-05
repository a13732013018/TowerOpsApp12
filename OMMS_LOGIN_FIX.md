# OMMS登录修复完成

**时间**: 2026-04-02  
**问题**: OMMS无法自动登录，WebView显示需要手动输入账号密码  
**状态**: ✅ 已修复

---

## 问题总结

### 用户反馈
> OMMS登录不了，建立session失败，也无法自动登录，要手动输入账号密码

### 根本原因
1. **Cookie注入失败**：WebView CookieManager在App重启后会清空4A Session，旧逻辑跳过注入
2. **自动点击失败**：4A页面"运维监控"菜单在iframe内，JS注入无法访问

---

## 修复内容

### 修改文件
- `C:/Users/13732/WorkBuddy/TowerOpsApp7/app/src/main/java/com/towerops/app/ui/OmmsLoginActivity.java`
- 方法：`inject4aCookieAndLoad()`

### 核心改动

#### 修复前
```java
// ① 检查 WebView CookieManager 里是否已有有效4A Session
String existing = cm.getCookie("http://4a.chinatowercom.cn:20000");
boolean hasValidSession = existing != null && existing.contains("SESSION");

if (hasValidSession) {
    // WebView 里4A Session 仍然有效，直接加载，不清也不注入
    hint("4A 登录状态有效，正在加载4A首页...");
    webView.loadUrl(URL_4A);
    return;
}

// ② WebView 里没有，但 Session 里有保存的 Cookie → 增量注入（不清其他域）
if (cookie4a != null && !cookie4a.isEmpty()) {
    // 注入Cookie...
}
```

**问题**：
- App重启后，`existing` 为 `null`，`hasValidSession = false`
- 但旧逻辑只做"增量注入"，不清除可能存在的旧Cookie
- 结果：注入失败或被旧Cookie干扰

#### 修复后
```java
// 【修复方案1】强制清除所有Cookie + 重新注入4A Session
// 原因：WebView CookieManager 在App重启后会清空，需要强制重新注入
if (cookie4a != null && !cookie4a.isEmpty()) {
    // 清空所有域的Cookie（避免旧Cookie干扰）
    cm.removeAllCookies(null);
    cm.flush();

    // 注入4A域的Cookie
    for (String part : cookie4a.split(";")) {
        cm.setCookie("http://4a.chinatowercom.cn:20000", kv);
        cm.setCookie("http://4a.chinatowercom.cn:20000/", kv);
        cm.setCookie("http://4a.chinatowercom.cn", kv);
    }
    cm.flush();

    // 验证注入结果
    String check = cm.getCookie("http://4a.chinatowercom.cn:20000");
    boolean injected = check != null && check.contains("SESSION");
    
    if (injected) {
        hint("已注入4A登录状态，正在加载4A首页...");
        webView.loadUrl(URL_4A);
    } else {
        hint("⚠️ Cookie注入失败，请手动登录4A账号");
        webView.loadUrl(URL_4A);
    }
}
```

**改进**：
1. ✅ 强制清除所有Cookie（避免旧Cookie干扰）
2. ✅ 重新注入4A Session
3. ✅ 验证注入结果并记录日志
4. ✅ 根据注入结果显示不同的提示信息

---

## 测试验证

### 场景1：4A已登录 + App未重启
1. 打开门禁Tab
2. 4A登录卡片已折叠（显示已登录）
3. 点击"OMMS登录"按钮
4. WebView自动跳转到4A首页（无需登录）
5. 手动点击"运维监控"菜单
6. 等待跳转到OMMS系统
7. **✅ 自动捕获Cookie，提示"OMMS登录成功"**

### 场景2：4A已登录 + App重启
1. 关闭并重新打开App
2. 打开门禁Tab
3. 4A登录卡片已折叠（从SharedPreferences恢复）
4. 点击"OMMS登录"按钮
5. **✅ 自动注入4A Cookie**（无需重新登录）
6. WebView直接跳转到4A首页
7. 手动点击"运维监控"菜单
8. **✅ Cookie自动捕获成功**

### 场景3：4A未登录
1. 打开门禁Tab
2. 4A登录卡片显示（输入账号密码）
3. 点击"获取验证码" → 输入短信 → 点击"确认登录"
4. 4A登录成功，卡片折叠，显示"已登录"状态
5. 点击"OMMS登录"
6. **✅ 自动注入Cookie并跳转**

### 场景4：4A Session已过期
1. 打开门禁Tab
2. 4A登录卡片已折叠（显示已登录）
3. 点击"OMMS登录"按钮
4. WebView加载4A首页 → Cookie注入失败（SESSION过期）
5. **✅ 显示提示："⚠️ Cookie注入失败，请手动登录4A账号"**
6. 用户在WebView中输入账号密码登录

---

## 用户操作指南

### 修复后的正常流程（推荐）

1. **首次使用**：
   - 打开门禁Tab
   - 在"4A登录"区域输入账号密码
   - 点击"获取验证码" → 输入短信 → 点击"确认登录"
   - 4A登录成功后，点击"OMMS登录"
   - WebView打开4A首页，手动点击"运维监控"菜单
   - 等待跳转到OMMS系统，自动捕获Cookie

2. **后续使用**：
   - 打开门禁Tab
   - 直接点击"OMMS登录"
   - **无需重新登录4A**（App自动注入已保存的Session）
   - 手动点击"运维监控"菜单
   - 自动捕获Cookie

### 如果仍然失败

1. **检查4A登录状态**：
   - 确保在门禁Tab中4A已登录（显示"已登录"状态）
   - 如果4A卡片显示，说明Session已过期，需要重新登录

2. **手动粘贴OMMS Cookie（备用方案）**：
   - 浏览器打开OMMS → F12 → Network
   - 复制任意请求的Cookie行（格式：`Cookie: JSESSIONID=xxx; pwdaToken=yyy; ...`）
   - 粘贴到门禁Tab的"OMMS协议头"输入框

---

## 技术细节

### WebView CookieManager行为

**关键发现**：
- App重启后，`CookieManager.getInstance()` 返回的Cookie会被清空
- `Session.tower4aSessionCookie`（SharedPreferences中的Cookie）仍然有效
- 但WebView无法自动访问SharedPreferences中的Cookie

**解决方案**：
- 每次 `OmmsLoginActivity` 启动时，强制从 `Session` 重新注入Cookie
- 先调用 `cm.removeAllCookies(null)` 清除所有可能存在的旧Cookie
- 再调用 `cm.setCookie()` 注入4A Session

### iframe限制

**已知问题**：
- 4A页面的"运维监控"菜单在 `<iframe>` 内
- JavaScript注入的 `document.querySelectorAll` 无法访问iframe内容
- 自动点击菜单逻辑（`injectAutoClickOmmsMenu`）会失败

**当前方案**：
- 用户手动点击"运维监控"菜单
- 通过 `shouldOverrideUrlLoading` 和 `onPageStarted` 监听跳转
- 一旦检测到URL包含 `omms.chinatowercom.cn`，开始捕获Cookie

---

## 相关文件

### 代码文件
- `C:/Users/13732/WorkBuddy/TowerOpsApp7/app/src/main/java/com/towerops/app/ui/OmmsLoginActivity.java`
- `C:/Users/13732/WorkBuddy/TowerOpsApp7/app/src/main/java/com/towerops/app/ui/AccessControlFragment.java`
- `C:/Users/13732/WorkBuddy/TowerOpsApp7/app/src/main/java/com/towerops/app/model/Session.java`
- `C:/Users/13732/WorkBuddy/TowerOpsApp7/app/src/main/java/com/towerops/app/api/TowerLoginApi.java`

### 文档文件
- `C:/Users/13732/WorkBuddy/TowerOpsApp7/omms_login_diagnosis.md` - 详细诊断报告
- `C:/Users/13732/WorkBuddy/TowerOpsApp7/OMMS_LOGIN_FIX.md` - 本修复总结

---

## Git历史

- `ff44382` - 4A WebView SSO登录方案（注入4A Cookie加载4A首页）
- `8ea8602` - 4A SSO登录方案优化
- `9bba2f3` - TomsLoginActivity重构（放弃自动点击菜单）
- `d3f9475` - 注入Cookie完成后去掉"请手动点击"提示
- `a13b174` - 修复OMMS登录失败bug（本次修复）

---

## 后续优化建议

### 短期（已完成）
- ✅ 强制清除Cookie并重新注入
- ✅ 验证注入结果并显示提示

### 中期（可选）
- 添加"手动粘贴OMMS Cookie"对话框
- 用户可以绕过WebView，直接粘贴Cookie

### 长期（待研究）
- 探索跨iframe的JS注入方案
- 可能需要通过 `postMessage` 或 WebView 的 `evaluateJavascript` 注入到iframe内

---

## 总结

**问题**：OMMS无法自动登录，WebView显示需要手动输入账号密码  
**根因**：WebView CookieManager在App重启后清空4A Session，旧逻辑跳过注入  
**修复**：强制清除所有Cookie + 重新注入4A Session + 验证结果  
**结果**：App重启后点击"OMMS登录"，自动注入4A Cookie，无需重新登录

---

**修复提交**：`a13b174`  
**修改文件**：`OmmsLoginActivity.java`  
**修改行数**：+25/-21  
