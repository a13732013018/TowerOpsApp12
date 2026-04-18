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
 * 【核心流程】
 *   1. 加载4A门户界面
 *   2. 用户手动点击"运维监控"进入OMMS
 *   3. 程序自动从CookieManager获取OMMS Cookie
 *
 * 【注意】
 *   - pwdaToken 是通过浏览器JS写入的，纯HTTP请求拿不到
 *   - 自动获取只能拿到 JSESSIONID
 *   - pwdaToken 需要用户在OMMS页面手动操作后，通过其它方式获取
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
    private boolean autoFilling = false;  // 是否正在自动填充4A登录
    private boolean autoClickTriggered = false;  // 是否已触发自动点击OMMS
    private boolean firstLoadDone = false;  // 首次加载是否已完成（避免Cookie注入后误触发）

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_omms_login);

        Session.get().loadConfig(this);

        webView     = findViewById(R.id.webViewOmms);
        progressBar = findViewById(R.id.progressOmms);
        tvHint      = findViewById(R.id.tvOmmsHint);

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
        ws.setJavaScriptCanOpenWindowsAutomatically(true);
        ws.setSupportMultipleWindows(true);
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
                        if (url.contains(OMMS_HOST)) {
                            Logger.d(TAG, "检测到OMMS域，切换到主WebView: " + url);
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
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                Logger.d(TAG, "start -> " + url);
                if (!captured && url != null && url.contains(OMMS_HOST)) {
                    hint("正在跳转OMMS，获取凭据...");
                    ThreadManager.postDelayed(OmmsLoginActivity.this::tryCaptureCookie, 1500);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Logger.d(TAG, "finish -> " + url);
                if (captured || url == null) return;

                // ★ 首次加载完成后标记
                if (!firstLoadDone) {
                    firstLoadDone = true;
                    Logger.d(TAG, "首次加载完成: " + url);
                }

                if (url.contains(OMMS_HOST)) {
                    // ★★★ 到达OMMS域，直接获取Cookie ★★★
                    hint("正在获取OMMS凭据，请稍候...");
                    autoClickTriggered = false;  // 重置标志
                    ThreadManager.postDelayed(OmmsLoginActivity.this::tryCaptureCookie, 800);
                    
                } else if (url.contains("4a.chinatowercom.cn")) {
                    // ★★★ 4A域页面加载完成 ★★★
                    
                    // 1. 先捕获并保存4A Cookie
                    captureAndSave4aCookie();
                    
                    // 2. 检测是否为登录页（Cookie无效或过期）
                    boolean isLoginPage = url.contains("/uac/login")
                            || url.contains("/doPrevLogin")
                            || url.contains("/sso/login")
                            || url.contains("login.jsp")
                            || url.contains("login.html")
                            || url.equals("http://4a.chinatowercom.cn:20000/")
                            || url.equals("http://4a.chinatowercom.cn:20000");
                    
                    if (isLoginPage) {
                        // ★ Cookie无效，跳转到登录页，触发自动填充
                        if (!autoFilling) {
                            autoFilling = true;
                            Logger.d(TAG, ">>> 4A Cookie无效/过期，触发自动填充登录");
                            hint("4A登录状态已过期，正在自动填写账号密码...");
                            ThreadManager.postDelayed(() -> autoFill4aLogin(view), 1500);
                        }
                    } else {
                        // ★ Cookie有效，4A门户已加载！自动点击运维监控
                        autoFilling = false;  // 重置登录标志
                        if (!autoClickTriggered) {
                            autoClickTriggered = true;
                            hint("✅ 4A已登录，正在自动点击「运维监控」...");
                            Logger.d(TAG, ">>> 4A Cookie有效，自动点击运维监控");
                            ThreadManager.postDelayed(() -> injectAutoClickOmmsMenu(view), 1500);
                        }
                    }
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
     * 智能注入4A Cookie并加载4A首页。
     * 
     * 流程：
     * 1. 如果有保存的4A Cookie，注入后加载4A首页
     * 2. Cookie有效 → 4A门户页面 → 自动点击"运维监控"
     * 3. Cookie无效 → 4A登录页 → 自动填充账号密码
     * 4. 没有保存的Cookie → 加载4A首页让用户手动登录
     */
    private void inject4aCookieAndLoad() {
        Session s = Session.get();
        String cookie4a = s.tower4aSessionCookie;
        CookieManager cm = CookieManager.getInstance();

        if (cookie4a != null && !cookie4a.isEmpty()) {
            // ★ 有保存的Cookie，尝试注入
            Logger.d(TAG, "inject4a: 清空所有Cookie，注入4A Cookie (len=" + cookie4a.length() + ")");
            hint("正在注入4A登录状态...");
            cm.removeAllCookies(null);
            cm.flush();

            for (String part : cookie4a.split(";")) {
                String kv = part.trim();
                if (!kv.isEmpty()) {
                    cm.setCookie("http://4a.chinatowercom.cn:20000", kv);
                    cm.setCookie("http://4a.chinatowercom.cn:20000/", kv);
                    cm.setCookie("http://4a.chinatowercom.cn", kv);
                }
            }
            cm.flush();

            String check = cm.getCookie("http://4a.chinatowercom.cn:20000");
            boolean injected = check != null && check.contains("SESSION");
            Logger.d(TAG, "inject4a: Cookie注入" + (injected ? "成功" : "失败"));
            
            // ★ 首次加载完成后才允许触发自动逻辑（避免误触发）
            firstLoadDone = false;
            webView.loadUrl(URL_4A);
            return;
        }

        // ★ 没有保存的Cookie，让用户手动登录
        Logger.w(TAG, "inject4a: tower4aSessionCookie为空");
        hint("未检测到4A登录状态，请手动登录4A账号");
        webView.loadUrl(URL_4A);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 在4A页面自动找「运维监控」或「综合运维管理系统」菜单并点击
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

            "var target = findByText(['运维监控', '综合运维管理系统', '综合运维']);" +
            "if (target) {" +
            "    console.log('[AutoClick] 找到运维监控菜单，点击...');" +
            "    target.click();" +
            "} else {" +
            "    var appList = findByText(['我的应用', '应用列表', '工作台', '全部应用']);" +
            "    if (appList) {" +
            "        console.log('[AutoClick] 先展开应用列表...');" +
            "        appList.click();" +
            "        setTimeout(function() {" +
            "            var t2 = findByText(['运维监控', '综合运维管理系统', '综合运维']);" +
            "            if (t2) t2.click();" +
            "        }, 1000);" +
            "    }" +
            "}" +

            "})();";

        view.evaluateJavascript(js, result ->
                Logger.d(TAG, "autoClickOmms JS result: " + result));
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 尝试从 CookieManager 获取 OMMS Cookie。
     * 有 JSESSIONID 就算成功，最多重试5次（约5秒）。
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

            // ★ 关键：只保留 OMMS 专属字段，过滤掉4A的Cookie
            String filteredCookie = filterOmmsCookie(raw);
            
            Session session = Session.get();
            session.ommsCookie = filteredCookie;
            session.saveOmmsCookie(OmmsLoginActivity.this);

            boolean hasPwda = filteredCookie.contains("pwdaToken");
            boolean hasJsid = filteredCookie.contains("JSESSIONID");
            Logger.d(TAG, "captured! hasPwda=" + hasPwda + " hasJsid=" + hasJsid 
                    + " cookieLen=" + filteredCookie.length());

            hint("✅ 获取成功！" + (hasPwda ? "含pwdaToken" : "⚠️ 仅JSESSIONID（POST可能需手动）"));
            Toast.makeText(this, "✅ OMMS Cookie已获取！", Toast.LENGTH_SHORT).show();

            // 延迟关闭
            ThreadManager.postDelayed(() -> {
                Intent result = new Intent();
                result.putExtra(EXTRA_COOKIE, filteredCookie);
                setResult(RESULT_OK, result);
                finish();
            }, 600);

        } else {
            retryCount++;
            if (retryCount <= 5) {
                hint("等待OMMS建立Session...(第" + retryCount + "次)");
                ThreadManager.postDelayed(this::tryCaptureCookie, 1000);
            } else {
                hint("⚠️ 未能获取Cookie，请重试点击运维监控菜单");
                retryCount = 0;
            }
        }
    }

    /**
     * 过滤掉4A域 Cookie，只保留 OMMS 专属字段。
     */
    private static String filterOmmsCookie(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        java.util.Set<String> keepNames = new java.util.HashSet<>(java.util.Arrays.asList(
                "JSESSIONID", "pwdaToken", "nodeInformation", "acctId", "uid",
                "loginName", "fp", "userOrgCode", "lang", "route"
        ));
        java.util.Set<String> filterNames = new java.util.HashSet<>(java.util.Arrays.asList(
                "SESSION", "Tnuocca", "ULTRA_U_K"
        ));
        StringBuilder sb = new StringBuilder();
        for (String pair : raw.split(";")) {
            pair = pair.trim();
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String k = pair.substring(0, eq).trim();
                if (keepNames.contains(k) && !filterNames.contains(k)) {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(pair);
                }
            }
        }
        String result = sb.toString();
        return result.isEmpty() ? raw : result;
    }

    /**
     * 自动填充4A登录页的账号密码并提交。
     * 用户只需输入短信验证码。
     */
    private void autoFill4aLogin(WebView view) {
        Session s = Session.get();
        int idx = s.selected4AAccountIndex;
        
        if (idx < 0 || idx >= AccountConfig.ACCOUNTS.length) {
            Logger.e(TAG, "账号索引无效: " + idx);
            autoFilling = false;
            hint("⚠️ 未选择4A账号，请在设置中选择");
            return;
        }
        
        String username = AccountConfig.ACCOUNTS[idx][0];
        String password = AccountConfig.get4aPassword(idx);

        if (password == null || password.isEmpty()) {
            autoFilling = false;
            hint("⚠️ 未配置4A密码，请手动输入\n账号: " + username);
            return;
        }

        Logger.d(TAG, "autoFill4a: 自动填充账号=" + username);
        hint("正在自动填写账号密码，请等待短信验证码...");

        // JS：找到账号密码输入框，填值，点击登录按钮
        String js = "(function() {" +
            "try {" +
            "  var userInput = document.querySelector('input[name=\"username\"]') " +
            "    || document.querySelector('input[type=\"text\"]') " +
            "    || document.querySelector('#username') " +
            "    || document.querySelector('input[placeholder*=\"账号\"]') " +
            "    || document.querySelector('input[placeholder*=\"工号\"]');" +
            "  var pwdInput = document.querySelector('input[name=\"password\"]') " +
            "    || document.querySelector('input[type=\"password\"]') " +
            "    || document.querySelector('#password');" +
            "  if (!userInput || !pwdInput) {" +
            "    return 'NOT_FOUND: user=' + !!userInput + ' pwd=' + !!pwdInput;" +
            "  }" +
            "  userInput.value = '" + username.replace("'", "\\'") + "';" +
            "  userInput.dispatchEvent(new Event('input', {bubbles:true}));" +
            "  userInput.dispatchEvent(new Event('change', {bubbles:true}));" +
            "  pwdInput.value = '" + password.replace("'", "\\'") + "';" +
            "  pwdInput.dispatchEvent(new Event('input', {bubbles:true}));" +
            "  pwdInput.dispatchEvent(new Event('change', {bubbles:true}));" +
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
                autoFilling = false;
                hint("⚠️ 未找到登录输入框，请手动输入\n账号: " + username);
            } else if (result != null && result.contains("SUBMITTED")) {
                hint("✅ 已自动填写并提交\n请输入短信验证码");
            } else if (result != null && result.contains("FILLED")) {
                hint("✅ 已自动填写账号密码\n请输入短信验证码并提交");
            } else {
                autoFilling = false;
            }
        });
    }

    /**
     * 捕获4A域的Cookie并保存到 Session.tower4aSessionCookie
     */
    private void captureAndSave4aCookie() {
        CookieManager cm = CookieManager.getInstance();
        String raw = cm.getCookie("http://4a.chinatowercom.cn:20000");
        if (raw == null || raw.isEmpty()) {
            raw = cm.getCookie("http://4a.chinatowercom.cn");
        }

        Logger.d(TAG, "capture4a: raw=" + (raw == null ? "null" : raw.substring(0, Math.min(100, raw.length()))));

        if (raw != null && (raw.contains("SESSION=") || raw.contains("Tnuocca="))) {
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
