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
                    if (url.contains("login") || url.contains("doPrevLogin")) {
                        hint("正在自动填写账号密码...");
                        // 自动填充账号密码并提交（用户只需等短信验证码）
                        ThreadManager.postDelayed(() -> autoFill4aLogin(view), 1500);
                    } else {
                        // 4A 登录成功（包含有Cookie直接进入工作台的情况），立即捕获4A Cookie并保存
                        captureAndSave4aCookie();
                        // 自动找运维监控菜单
                        hint("4A 已就绪，正在自动点击「运维监控」...");
                        ThreadManager.postDelayed(() -> injectAutoClickOmmsMenu(view), 2000);
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
     * 在4A页面自动找「运维监控」或「综合运维管理系统」菜单并点击
     * 超时6秒后提示手动操作
     */
    private void injectAutoClickOmmsMenu(WebView view) {
        // 6秒超时保底
        ThreadManager.postDelayed(() -> {
            if (!captured) {
                hint("⚠️ 未能自动找到运维监控菜单\n\n请手动点击页面里的\n「运维监控」或「综合运维管理系统」");
            }
        }, 6000);

        String js = "(function() {" +

            "function findByText(texts, maxLen) {" +
            "    maxLen = maxLen || 30;" +
            "    if (typeof texts === 'string') texts = [texts];" +
            "    var all = document.querySelectorAll('a,li,span,div,td,button,p,h3,h4');" +
            "    for (var k = 0; k < texts.length; k++) {" +
            "        for (var i = 0; i < all.length; i++) {" +
            "            var t = (all[i].textContent || '').trim();" +
            "            if (t === texts[k]) return all[i];" +
            "        }" +
            "    }" +
            "    for (var k = 0; k < texts.length; k++) {" +
            "        for (var i = 0; i < all.length; i++) {" +
            "            var t = (all[i].textContent || '').trim();" +
            "            if (t.indexOf(texts[k]) !== -1 && t.length <= maxLen) return all[i];" +
            "        }" +
            "    }" +
            "    return null;" +
            "}" +

            "function dumpTexts() {" +
            "    var all = document.querySelectorAll('*');" +
            "    var texts = [];" +
            "    for (var i = 0; i < all.length; i++) {" +
            "        var t = (all[i].textContent || '').trim();" +
            "        if (t && t.length > 0 && t.length < 20 && all[i].children.length === 0) texts.push(t);" +
            "    }" +
            "    return texts.filter(function(v,i,a){return a.indexOf(v)===i;});" +
            "}" +

            // 直接找「运维监控」或「综合运维管理系统」
            "var target = findByText(['运维监控', '综合运维管理系统', '综合运维']);" +
            "if (target) {" +
            "    console.log('[AutoClick] 找到运维监控菜单，点击...');" +
            "    target.click();" +
            "    if (window.OmmsMenuBridge) window.OmmsMenuBridge.onMenuClicked('found');" +
            "} else {" +
            // 先尝试展开应用列表
            "    var appList = findByText(['我的应用', '应用列表', '工作台', '全部应用']);" +
            "    if (appList) {" +
            "        console.log('[AutoClick] 先展开应用列表...');" +
            "        appList.click();" +
            "        setTimeout(function() {" +
            "            var t2 = findByText(['运维监控', '综合运维管理系统', '综合运维']);" +
            "            if (t2) {" +
            "                t2.click();" +
            "                if (window.OmmsMenuBridge) window.OmmsMenuBridge.onMenuClicked('found');" +
            "            } else {" +
            "                var texts = dumpTexts();" +
            "                console.log('[AutoClick] not found, texts=' + JSON.stringify(texts.slice(0,60)));" +
            "                if (window.OmmsMenuBridge) window.OmmsMenuBridge.onMenuNotFound(JSON.stringify(texts.slice(0,60)));" +
            "            }" +
            "        }, 1000);" +
            "    } else {" +
            "        var texts = dumpTexts();" +
            "        console.log('[AutoClick] not found, texts=' + JSON.stringify(texts.slice(0,60)));" +
            "        if (window.OmmsMenuBridge) window.OmmsMenuBridge.onMenuNotFound(JSON.stringify(texts.slice(0,60)));" +
            "    }" +
            "}" +

            "})();";

        view.addJavascriptInterface(new OmmsMenuBridge(), "OmmsMenuBridge");
        view.evaluateJavascript(js, result ->
                Logger.d(TAG, "autoClickOmms JS result: " + result));
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
            hint("⚠️ 未配置4A密码，请手动输入账号密码");
            return;
        }

        Logger.d(TAG, "autoFill4a: 自动填充账号=" + username);
        hint("正在自动填写账号密码，请等待短信验证码...");

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
                // 输入框没找到，提示用户手动输入
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
