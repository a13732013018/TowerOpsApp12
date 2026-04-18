package com.towerops.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.towerops.app.util.Logger;
import com.towerops.app.util.ThreadManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.towerops.app.R;
import com.towerops.app.model.AccountConfig;
import com.towerops.app.model.Session;
import com.towerops.app.util.Logger;
import com.towerops.app.util.ThreadManager;

/**
 * OMMS 授权登录页（全WebView交互方案）
 *
 * 【方案说明】
 *   OMMS 没有独立账号密码，只能从4A SSO跳转进入。
 *   本页面直接显示4A登录界面，由用户：
 *     1. 在WebView里完成4A账号登录
 *     2. 点击"运维监控"菜单
 *     3. 4A服务器生成含一次性token的跳转URL，自动跳到OMMS
 *   程序监听 URL 跳转，一旦落到 omms.chinatowercom.cn 域：
 *     - 等待OMMS建立Session（pwdaToken写入）
 *     - 从CookieManager取出完整Cookie
 *     - 保存到 Session.ommsCookie 并返回
 *
 * 【使用方法】
 *   用户打开页面后：
 *   ① 输入4A账号密码登录
 *   ② 找到"运维监控"或"综合运维管理系统"菜单并点击
 *   ③ 跳转后程序自动捕获Cookie，提示成功并关闭页面
 */
public class OmmsLoginActivity extends Activity {

    public static final String EXTRA_COOKIE = "omms_cookie";

    private static final String TAG       = "OmmsLogin";
    private static final String URL_4A    = "http://4a.chinatowercom.cn:20000/uac/index";
    private static final String OMMS_HOST = "omms.chinatowercom.cn";

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private WebView    webView;
    private ProgressBar progressBar;
    private TextView   tvHint;

    private boolean captured = false;
    private boolean captured4aToken = false;  // 是否已捕获4A Bearer Token
    private boolean autoJumpTriggered = false; // 是否已触发OMMS自动跳转（防止重复）
    private boolean autoFilling = false;       // 是否正在自动填充（防重入死循环）
    private String savedPwdaToken = "";  // 保存pwdaToken用于后续获取4A Token

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_omms_login);

        // 恢复配置（包括 tower4aSessionCookie）
        Session.get().loadConfig(this);

        webView     = findViewById(R.id.webViewOmms);
        progressBar = findViewById(R.id.progressOmms);
        tvHint      = findViewById(R.id.tvOmmsHint);

        // 显示 WebView（之前版本隐藏了它）
        webView.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);

        setupWebView();
        inject4aCookieAndLoad();
    }

    private void hint(String msg) {
        tvHint.setText(msg);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void setupWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setJavaScriptCanOpenWindowsAutomatically(true);  // 允许JS通过window.open跳转
        ws.setSupportMultipleWindows(true);                  // 支持多窗口（4A菜单用window.open）
        ws.setUserAgentString(UA);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        // ── WebChromeClient：处理 window.open 新窗口 ──────────────────────
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, android.os.Message resultMsg) {
                // 4A菜单点击后用window.open开新窗口，我们在同一个WebView里打开
                WebView newWebView = new WebView(OmmsLoginActivity.this);
                newWebView.getSettings().setJavaScriptEnabled(true);
                newWebView.getSettings().setDomStorageEnabled(true);
                newWebView.getSettings().setUserAgentString(UA);
                newWebView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                CookieManager.getInstance().setAcceptThirdPartyCookies(newWebView, true);

                newWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                        String url = req.getUrl().toString();
                        Logger.d(TAG, "newWindow nav -> " + url);
                        // 如果是OMMS域，用主WebView加载，让我们的Cookie捕获逻辑生效
                        if (url.contains(OMMS_HOST)) {
                            Logger.d(TAG, "检测到OMMS域，切换到主WebView加载: " + url);
                            webView.loadUrl(url);
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public void onPageStarted(WebView v, String url, android.graphics.Bitmap fav) {
                        Logger.d(TAG, "newWindow start -> " + url);
                        if (!captured && url != null && url.contains(OMMS_HOST)) {
                            ThreadManager.postDelayed(OmmsLoginActivity.this::tryCaptureCookie, 1500);
                        }
                    }

                    @Override
                    public void onPageFinished(WebView v, String url) {
                        Logger.d(TAG, "newWindow finish -> " + url);
                        if (!captured && url != null && url.contains(OMMS_HOST)) {
                            hint("正在获取OMMS凭据，请稍候...");
                            ThreadManager.postDelayed(OmmsLoginActivity.this::tryCaptureCookie, 800);
                        }
                    }
                });

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newWebView);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (progress >= 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }
        });

        // ── WebViewClient：主窗口导航处理 ────────────────────────────────
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                Logger.d(TAG, "nav -> " + url);

                // 检测到进入门禁页面
                if (url.contains("tymj.chinatowercom.cn")) {
                    // 保存pwdaToken（如果URL中有）
                    if (url.contains("pwdaToken=")) {
                        try {
                            int start = url.indexOf("pwdaToken=") + "pwdaToken=".length();
                            int end = url.indexOf("&", start);
                            if (end < 0) end = url.length();
                            savedPwdaToken = url.substring(start, end);
                            Logger.d(TAG, "[4A] 保存pwdaToken: " + savedPwdaToken.substring(0, 20) + "...");
                        } catch (Exception e) {
                            Logger.e(TAG, "[4A] 保存pwdaToken失败: " + e.getMessage());
                        }
                    }
                }

                return false; // 全部放行，让 WebView 自己加载
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                Logger.d(TAG, "start -> " + url);
                // 一进入 OMMS 域，立刻开始等待 Cookie
                if (!captured && url != null && url.contains(OMMS_HOST)) {
                    hint("正在跳转OMMS，获取凭据...");
                    ThreadManager.postDelayed(OmmsLoginActivity.this::tryCaptureCookie, 1500);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Logger.d(TAG, "finish -> " + url);
                if (captured || url == null) return;

                if (url.contains(OMMS_HOST)) {
                    hint("正在获取OMMS凭据，请稍候...");
                    ThreadManager.postDelayed(OmmsLoginActivity.this::tryCaptureCookie, 800);
                } else if (url.contains("4a.chinatowercom.cn")) {
                    // ★ 精准检测真正的4A登录页：URL路径中包含login字样（而非只是参数）
                    // 排除：/uac/index（有loginTime参数但不是登录页）、任何含login参数的普通页面
                    boolean isLoginPage = url.contains("/uac/login")
                            || url.contains("/login.do")
                            || url.contains("/doPrevLogin")
                            || (url.contains("/uac/") && url.contains("login") && !url.contains("index"));

                    if (isLoginPage && !autoFilling) {
                        autoFilling = true;
                        hint("正在自动填写账号密码...");
                        ThreadManager.postDelayed(() -> autoFill4aLogin(view), 1500);
                    } else if (!isLoginPage) {
                        // 4A 登录成功（包含有Cookie直接进入工作台的情况），立即捕获4A Cookie并保存
                        captureAndSave4aCookie();
                        // 只触发一次自动跳转，防止SSO URL重定向后再次触发
                        if (!autoJumpTriggered) {
                            autoJumpTriggered = true;
                            hint("4A 已就绪，正在自动跳转到「运维监控」...");
                            ThreadManager.postDelayed(() -> injectAutoClickOmmsMenu(view), 1500);
                        }
                    }
                } else if (url.contains("tymj.chinatowercom.cn")) {
                    // 进入门禁管理端，同时获取OMMS Cookie和4A Bearer Token
                    hint("✅ 进入门禁管理端，获取OMMS凭据和4A Token...");

                    // 方式1：注入JS Hook拦截XHR Authorization头获取4A Bearer Token
                    if (!captured4aToken) {
                        ThreadManager.postDelayed(() -> inject4aTokenHook(view), 1000);
                    }
                    // 方式2：延迟获取OMMS Cookie
                    ThreadManager.postDelayed(OmmsLoginActivity.this::tryCaptureCookie, 1500);
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode,
                                        String description, String failingUrl) {
                Logger.e(TAG, "WebView error " + errorCode + " " + description
                        + " url=" + failingUrl);
            }
        });
    }

    /**
     * 智能注入4A Cookie 并加载4A首页。
     *
     * 策略：
     *  1. 先检查 WebView 中是否已有有效的4A Cookie → 直接加载
     *  2. 没有则从 Session 读取保存的 Cookie → 直接注入（不清旧Cookie）
     *  3. 两者都没有 → 加载4A登录页
     *
     * 注意：不再删除Cookie，因为4A服务器端Session过期后即使删除也没用，
     *       直接让页面显示登录页让用户重新登录更可靠。
     */
    private void inject4aCookieAndLoad() {
        Session s = Session.get();
        String cookie4a = s.tower4aSessionCookie;
        CookieManager cm = CookieManager.getInstance();

        // ① 检查WebView中是否已有有效的4A Cookie
        String existing = cm.getCookie("http://4a.chinatowercom.cn:20000");
        if (existing != null && existing.contains("SESSION")) {
            Logger.d(TAG, "inject4a: WebView中已有有效4A Cookie，直接加载");
            hint("✅ 已检测到4A登录状态，正在加载...");
            webView.loadUrl(URL_4A);
            return;
        }

        // ② 从Session读取并注入Cookie
        if (cookie4a != null && !cookie4a.isEmpty()) {
            Logger.d(TAG, "inject4a: 从Session注入4A Cookie (len=" + cookie4a.length() + ")");
            hint("正在注入4A登录状态...");

            // 直接注入Cookie，不删除任何旧Cookie
            for (String part : cookie4a.split(";")) {
                String kv = part.trim();
                if (!kv.isEmpty()) {
                    cm.setCookie("http://4a.chinatowercom.cn:20000", kv);
                    cm.setCookie("http://4a.chinatowercom.cn:20000/", kv);
                    cm.setCookie("http://4a.chinatowercom.cn", kv);
                }
            }
            cm.flush();

            // 验证注入结果
            String check = cm.getCookie("http://4a.chinatowercom.cn:20000");
            boolean injected = check != null && check.contains("SESSION");
            Logger.d(TAG, "inject4a: 验证结果 " + (injected ? "成功" : "失败")
                    + ": " + (check == null ? "null" : (check.length() > 100 ? check.substring(0, 100) : check)));

            if (injected) {
                hint("✅ 已注入4A登录状态，正在加载4A首页...");
            } else {
                hint("⚠️ Cookie注入可能失败，尝试加载页面...");
            }
            webView.loadUrl(URL_4A);
            return;
        }

        // ③ 完全没有4A凭据，让用户手动登录
        Logger.w(TAG, "inject4a: tower4aSessionCookie为空");
        hint("未检测到4A登录状态，请在此处手动登录4A账号");
        webView.loadUrl(URL_4A);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 4A登录成功后，自动跳转到OMMS并捕获Cookie。
     *
     * 完整自动化流程（零手动操作）：
     *  1. [当前] 4A Cookie有效 -> 4A首页加载完成
     *  2. [本方法] 将4A Cookie注入OMMS域 -> 直接加载OMMS主页
     *     - 若4A Cookie有效 -> OMMS接受SSO -> 捕获Cookie -> 完成
     *     - 若4A Cookie过期 -> OMMS重定向回4A -> 进入autoFill4aLogin流程
     *  3. [备用] JS点菜单 + 手动提示
     */
    private void injectAutoClickOmmsMenu(WebView view) {
        view.addJavascriptInterface(new OmmsMenuBridge(), "OmmsMenuBridge");

        hint("正在自动跳转到运维监控（OMMS）...");
        Logger.d(TAG, "autoJump: 开始OMMS自动跳转流程");

        Session s = Session.get();

        // 核心改进：先把4A Cookie注入到OMMS域
        // 当OMMS做SSO验证时，4A域的Cookie会自动随请求发送
        if (s.tower4aSessionCookie != null && !s.tower4aSessionCookie.isEmpty()) {
            CookieManager cm = CookieManager.getInstance();
            for (String part : s.tower4aSessionCookie.split(";")) {
                String kv = part.trim();
                if (!kv.isEmpty()) {
                    cm.setCookie("http://chinatowercom.cn", kv);
                    cm.setCookie("http://.chinatowercom.cn", kv);
                    cm.setCookie("http://4a.chinatowercom.cn:20000", kv);
                }
            }
            cm.flush();
            Logger.d(TAG, "autoJump: 4A Cookie已注入到各子域");
        }

        // 策略1（主）：直接加载OMMS主页
        // 如果4A Cookie有效，OMMS会在服务端完成SSO验证并设置自己的Session
        webView.loadUrl("http://omms.chinatowercom.cn:9000/Layout/index.xhtml");

        // 策略2备用：2秒后检查状态
        ThreadManager.postDelayed(() -> {
            if (!captured) {
                String curUrl = "";
                try { curUrl = webView.getUrl(); } catch (Exception ignored) {}
                Logger.d(TAG, "autoJump: 2s后当前URL=" + curUrl + " captured=" + captured);

                if (curUrl != null && (curUrl.contains("login") || curUrl.contains("uac/login"))) {
                    Logger.d(TAG, "autoJump: 4A Cookie过期，触发自动登录");
                    hint("4A登录状态已过期，正在自动填写账号密码...");
                    autoFill4aLogin(view);
                } else if (curUrl != null && curUrl.contains("4a.chinatowercom.cn")) {
                    Logger.d(TAG, "autoJump: 仍在4A首页，尝试JS点菜单");
                    tryJsClickOmmsMenu(view);
                } else if (curUrl != null && curUrl.contains("omms")) {
                    Logger.d(TAG, "autoJump: 在OMMS页面，重新尝试捕获Cookie");
                    retryCount = 0;
                    tryCaptureCookie();
                }
            }
        }, 2000);

        // 策略3最终超时：8秒后还未成功，提示手动
        ThreadManager.postDelayed(() -> {
            if (!captured) {
                hint("未能自动获取Cookie，请手动点击页面中的「运维监控」或重试");
            }
        }, 8000);
    }
    /**
     * 用JS在当前页面（包括iframe）查找「运维监控」菜单并点击
     */
    private void tryJsClickOmmsMenu(WebView view) {
        String js = "(function() {" +

            // 在指定document中查找目标文字节点
            "function findInDoc(doc, texts) {" +
            "  var all = doc.querySelectorAll('a,li,span,div,td,button,p,h3,h4,label');" +
            "  for (var k = 0; k < texts.length; k++) {" +
            "    for (var i = 0; i < all.length; i++) {" +
            "      var t = (all[i].textContent || '').trim();" +
            "      if (t === texts[k] || (t.indexOf(texts[k]) !== -1 && t.length <= 20)) {" +
            "        return all[i];" +
            "      }" +
            "    }" +
            "  }" +
            "  return null;" +
            "}" +

            // 获取所有可能包含菜单的document（主文档+所有iframe）
            "function getAllDocs() {" +
            "  var docs = [document];" +
            "  try {" +
            "    var frames = document.querySelectorAll('iframe,frame');" +
            "    for (var i = 0; i < frames.length; i++) {" +
            "      try { if (frames[i].contentDocument) docs.push(frames[i].contentDocument); } catch(e) {}" +
            "    }" +
            "  } catch(e) {}" +
            "  return docs;" +
            "}" +

            "var keywords = ['运维监控','综合运维管理系统','综合运维','OMMS'];" +
            "var docs = getAllDocs();" +
            "var found = null;" +
            "for (var d = 0; d < docs.length && !found; d++) {" +
            "  found = findInDoc(docs[d], keywords);" +
            "}" +

            "if (found) {" +
            "  console.log('[AutoClick] 找到菜单: ' + found.textContent.trim());" +
            "  found.click();" +
            "  if (window.OmmsMenuBridge) window.OmmsMenuBridge.onMenuClicked(found.textContent.trim());" +
            "  return 'CLICKED';" +
            "} else {" +
            // 先展开「我的应用」再查
            "  var expand = null;" +
            "  for (var d = 0; d < docs.length && !expand; d++) {" +
            "    expand = findInDoc(docs[d], ['我的应用','应用列表','全部应用','工作台']);" +
            "  }" +
            "  if (expand) {" +
            "    expand.click();" +
            "    setTimeout(function() {" +
            "      var docs2 = getAllDocs();" +
            "      var t2 = null;" +
            "      for (var d = 0; d < docs2.length && !t2; d++) {" +
            "        t2 = findInDoc(docs2[d], ['运维监控','综合运维管理系统','综合运维']);" +
            "      }" +
            "      if (t2) {" +
            "        t2.click();" +
            "        if (window.OmmsMenuBridge) window.OmmsMenuBridge.onMenuClicked(t2.textContent.trim());" +
            "      } else {" +
            "        if (window.OmmsMenuBridge) window.OmmsMenuBridge.onMenuNotFound('after_expand');" +
            "      }" +
            "    }, 1200);" +
            "    return 'EXPAND_THEN_SEARCH';" +
            "  } else {" +
            "    if (window.OmmsMenuBridge) window.OmmsMenuBridge.onMenuNotFound('no_expand');" +
            "    return 'NOT_FOUND';" +
            "  }" +
            "}" +

            "})();";

        view.evaluateJavascript(js, result ->
                Logger.d(TAG, "jsClickOmms result: " + result));
    }

    /**
     * 注入JS Hook拦截XHR/fetch请求，获取4A Bearer Token
     * Bearer Token在API请求的Authorization头中
     */
    private void inject4aTokenHook(WebView view) {
        if (captured4aToken) return;

        // 添加Android接口（确保已添加）
        view.addJavascriptInterface(new OmmsMenuBridge(), "OmmsMenuBridge");

        // 注入XHR Hook脚本
        String hookJs = "(function(){" +
                "  if (window._omms4aHooked) return 'already hooked';" +
                "  window._omms4aHooked = true;" +

                "  // Hook XMLHttpRequest" +
                "  var OriginalXHR = window.XMLHttpRequest;" +
                "  window.XMLHttpRequest = function(){" +
                "    var xhr = new OriginalXHR();" +
                "    var originalSetRequestHeader = xhr.setRequestHeader;" +
                "    xhr.setRequestHeader = function(name, value){" +
                "      if (name.toLowerCase() === 'authorization' && value){" +
                "        window._4aAuthHeader = value;" +
                "        console.log('[4A-Hook] Authorization: ' + value);" +
                "        try { window.OmmsMenuBridge.on4aTokenCaptured(value); } catch(e) {}" +
                "      }" +
                "      return originalSetRequestHeader.call(this, name, value);" +
                "    };" +
                "    return xhr;" +
                "  };" +

                "  // Hook fetch" +
                "  var OriginalFetch = window.fetch;" +
                "  window.fetch = function(url, options){" +
                "    if (options && options.headers){" +
                "      var headers = options.headers;" +
                "      if (typeof headers.get === 'function'){" +
                "        var auth = headers.get('authorization');" +
                "        if (auth) window._4aAuthHeader = auth;" +
                "      } else {" +
                "        for (var key in headers){" +
                "          if (key.toLowerCase() === 'authorization'){" +
                "            window._4aAuthHeader = headers[key];" +
                "            break;" +
                "          }" +
                "        }" +
                "      }" +
                "    }" +
                "    return OriginalFetch.apply(this, arguments);" +
                "  };" +

                "  console.log('[4A-Hook] XHR/Fetch hooked');" +
                "  return 'XHR/Fetch hooked';" +
                "})()";

        view.evaluateJavascript(hookJs, result ->
                Logger.d(TAG, "[4A-Hook] 注入结果: " + result));

        // 延迟触发一个API请求，激活token生成
        ThreadManager.postDelayed(() -> triggerApiForToken(view), 2000);
    }

    /**
     * 触发API请求，激活4A Bearer Token生成
     */
    private void triggerApiForToken(WebView view) {
        if (captured4aToken) return;

        String triggerJs = "(function(){" +
                "  var xhr = new XMLHttpRequest();" +
                "  xhr.open('GET', '/api/doorOpeningRecord/queryAreaTree', true);" +
                "  xhr.onload = function(){" +
                "    console.log('[4A-Trigger] API响应: ' + xhr.status);" +
                "  };" +
                "  xhr.send();" +
                "  return 'triggered';" +
                "})()";

        view.evaluateJavascript(triggerJs, result ->
                Logger.d(TAG, "[4A-Trigger] 触发结果: " + result));
    }

    public class OmmsMenuBridge {
        @android.webkit.JavascriptInterface
        public void onMenuClicked(String menu) {
            Logger.d(TAG, "OmmsMenu clicked: " + menu);
            ThreadManager.runOnUiThread(() -> hint("✅ 已自动点击「运维监控」，等待跳转 OMMS..."));
        }

        @android.webkit.JavascriptInterface
        public void onMenuNotFound(String textsJson) {
            Logger.w(TAG, "OmmsMenu not found, texts: " + textsJson);
            String preview = textsJson.length() > 200 ? textsJson.substring(0, 200) + "..." : textsJson;
            ThreadManager.runOnUiThread(() ->
                hint("⚠️ 自动找菜单失败，请手动点击「运维监控」\n\n页面文本:\n" + preview));
        }

        /**
         * 从XHR Hook回调4A Bearer Token
         * 格式：Bearer xxxxxxxx（去掉Bearer前缀后约96字符的hex字符串）
         */
        @android.webkit.JavascriptInterface
        public void on4aTokenCaptured(String authHeader) {
            if (captured4aToken || authHeader == null || authHeader.isEmpty()) return;

            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            // Bearer Token特征：非JWT格式，约96字符hex
            if (token.contains(".")) {
                Logger.d(TAG, "[4A Token] 跳过JWT，等待Bearer Token...");
                return;
            }
            if (token.length() < 20) return;

            Logger.d(TAG, "🎯 [4A Token] 捕获到Bearer Token: " + token.substring(0, 20) + "...");
            captured4aToken = true;

            // 保存到Session
            Session session = Session.get();
            session.tower4aToken = token;
            session.saveTower4aToken(OmmsLoginActivity.this);
            Logger.d(TAG, "[4A Token] 已保存到Session.tower4aToken");

            ThreadManager.runOnUiThread(() -> {
                hint("✅ OMMS + 4A Token 获取成功！");
                Toast.makeText(OmmsLoginActivity.this, "✅ 4A Bearer Token 自动获取成功！", Toast.LENGTH_SHORT).show();
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 尝试从 CookieManager 获取 OMMS Cookie。
     * 只要有 JSESSIONID 就算成功（pwdaToken可能稍晚写入）。
     * 若没有则延迟再试，最多重试5次（共约5秒）。
     */
    private int retryCount = 0;

    private void tryCaptureCookie() {
        if (captured) return;

        CookieManager cm = CookieManager.getInstance();
        String raw = cm.getCookie("http://" + OMMS_HOST + ":9000");
        if (raw == null || raw.isEmpty()) {
            raw = cm.getCookie("http://" + OMMS_HOST);
        }
        Logger.d(TAG, "tryCapture [" + retryCount + "] raw="
                + (raw == null ? "null" : raw.substring(0, Math.min(150, raw.length()))));

        if (raw != null && raw.contains("JSESSIONID")) {
            // ✅ 拿到了有效 Cookie
            captured = true;
            final String finalCookie = raw;

            Session session = Session.get();
            session.ommsCookie = finalCookie;

            // ★ 持久化到 SharedPreferences（使用 Session 统一接口，确保 key 一致）
            Session.get().saveOmmsCookie(OmmsLoginActivity.this);

            boolean hasPwda = finalCookie.contains("pwdaToken");
            Logger.d(TAG, "captured! hasPwda=" + hasPwda + " cookie=" + finalCookie);

            hint("✅ 获取成功！" + (hasPwda ? "含pwdaToken" : "含JSESSIONID"));
            Toast.makeText(this, "✅ OMMS 登录成功！", Toast.LENGTH_SHORT).show();

            // 如果4A Token也已捕获，延迟关闭
            if (captured4aToken) {
                hint("✅ OMMS + 4A Token 全部获取成功！");
                Toast.makeText(this, "✅ OMMS Cookie + 4A Bearer Token 获取成功！", Toast.LENGTH_LONG).show();
            }

            // 延迟600ms让用户看到提示后自动关闭
            ThreadManager.postDelayed(() -> {
                Intent result = new Intent();
                result.putExtra(EXTRA_COOKIE, finalCookie);
                setResult(RESULT_OK, result);
                finish();
            }, 600);

        } else {
            retryCount++;
            if (retryCount <= 5) {
                hint("等待OMMS建立Session...(第" + retryCount + "次)");
                ThreadManager.postDelayed(this::tryCaptureCookie, 1000);
            } else {
                // 超过重试次数，让用户知道失败了，但别强制关闭，让用户继续操作
                hint("未能自动获取Cookie，请重试点击运维监控菜单");
                retryCount = 0; // 重置计数，允许再次触发
            }
        }
    }

    /**
     * 自动填充4A登录页的账号密码并提交
     * 用户只需等待短信验证码，无需手动输入账号密码
     */
    private void autoFill4aLogin(WebView view) {
        Session s = Session.get();
        int idx = s.selected4AAccountIndex;
        String username = AccountConfig.ACCOUNTS[idx][0];
        String password = AccountConfig.get4aPassword(idx);

        if (password == null || password.isEmpty()) {
            autoFilling = false; // 重置标志
            hint("⚠️ 未配置4A密码，请手动输入账号密码");
            return;
        }

        Logger.d(TAG, "autoFill4a: 自动填充账号=" + username);
        hint("正在自动填写账号密码，请等待短信验证码...");

        // ★ 5秒超时：防止登录失败导致autoFilling永远不重置
        ThreadManager.postDelayed(() -> {
            autoFilling = false;
            Logger.d(TAG, "autoFill4a: 超时重置autoFilling标志");
        }, 5000);

        // JS：找到账号密码输入框，填值，点击登录按钮
        String js = "(function() {" +
            "try {" +
            // 找账号输入框（多种选择器兜底）
            "  var userInput = document.querySelector('input[name=\"username\"]') " +
            "    || document.querySelector('input[type=\"text\"]') " +
            "    || document.querySelector('#username') " +
            "    || document.querySelector('input[placeholder*=\"账号\"]') " +
            "    || document.querySelector('input[placeholder*=\"工号\"]');" +
            // 找密码输入框
            "  var pwdInput = document.querySelector('input[name=\"password\"]') " +
            "    || document.querySelector('input[type=\"password\"]') " +
            "    || document.querySelector('#password');" +
            "  if (!userInput || !pwdInput) {" +
            "    return 'NOT_FOUND: user=' + !!userInput + ' pwd=' + !!pwdInput;" +
            "  }" +
            // 填账号
            "  userInput.value = '" + username.replace("'", "\\'") + "';" +
            "  userInput.dispatchEvent(new Event('input', {bubbles:true}));" +
            "  userInput.dispatchEvent(new Event('change', {bubbles:true}));" +
            // 填密码
            "  pwdInput.value = '" + password.replace("'", "\\'") + "';" +
            "  pwdInput.dispatchEvent(new Event('input', {bubbles:true}));" +
            "  pwdInput.dispatchEvent(new Event('change', {bubbles:true}));" +
            // 延迟500ms点提交按钮
            "  setTimeout(function() {" +
            "    var btn = document.querySelector('button[type=\"submit\"]') " +
            "      || document.querySelector('input[type=\"submit\"]') " +
            "      || document.querySelector('.login-btn') " +
            "      || document.querySelector('button.btn-primary') " +
            "      || document.querySelector('button');" +
            "    if (btn) { btn.click(); return 'SUBMITTED'; }" +
            "    return 'NO_BUTTON';" +
            "  }, 500);" +
            "  return 'FILLED';" +
            "} catch(e) { return 'ERR:' + e.message; }" +
            "})()";

        view.evaluateJavascript(js, result -> {
            Logger.d(TAG, "autoFill4a JS result: " + result);
            if (result != null && result.contains("NOT_FOUND")) {
                autoFilling = false; // 重置标志，允许用户手动输入
                ThreadManager.runOnUiThread(() ->
                    hint("⚠️ 未找到登录输入框，请手动输入账号密码\n账号: " + username));
            } else if (result != null && result.contains("FILLED")) {
                ThreadManager.runOnUiThread(() ->
                    hint("✅ 已自动填写账号密码，正在提交...\n请等待短信验证码"));
            }
        });
    }

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

        Logger.d(TAG, "capture4a: raw=" + (raw == null ? "null" : raw.substring(0, Math.min(100, raw.length()))));

        if (raw != null && (raw.contains("SESSION=") || raw.contains("Tnuocca="))) {
            // ✅ 拿到了有效的4A Cookie
            Session session = Session.get();
            session.tower4aSessionCookie = raw;
            session.saveTower4aCookie(this);

            Logger.d(TAG, "capture4a: 已保存到Session.tower4aSessionCookie (len=" + raw.length() + ")");
        } else {
            Logger.w(TAG, "capture4a: 未能捕获有效的4A Cookie");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            setResult(RESULT_CANCELED);
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
