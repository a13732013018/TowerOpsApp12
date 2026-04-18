package com.towerops.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.towerops.app.R;
import com.towerops.app.model.AccountConfig;
import com.towerops.app.model.Session;
import com.towerops.app.util.PrefHelper;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * 门禁 Bearer Token 获取页（参考TomsLoginActivity）
 *
 * 【流程】
 *   1. 加载4A首页
 *   2. 提示用户手动点击"统一门禁管理端"
 *   3. shouldInterceptRequest 拦截门禁API请求，找到 Authorization Bearer Token
 *   4. 抓到后自动保存并关闭页面
 */
public class TymjLoginActivity extends Activity {

    public static final String EXTRA_TOKEN = "tymj_token";

    private static final String TAG        = "TymjLogin";
    private static final String BASE_4A    = "http://4a.chinatowercom.cn:20000";
    private static final String UA4A_INDEX = BASE_4A + "/uac/index";
    private static final String TYMS_HOST  = "http://tymj.chinatowercom.cn:8006";

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private WebView     webView;
    private ProgressBar progressBar;
    private TextView    tvHint;
    private View        btnReload;
    private View        btnManual;
    private LinearLayout layoutManualInput;
    private EditText    etManualToken;
    private View        btnManualSubmit;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean captured = false;
    private boolean currentTokenIsBearer = false;  // 当前token是否为Bearer格式（非JWT）
    private boolean sessionCookieValid = false;  // 4A Cookie是否有效
    private String savedPwdaToken = "";  // 保存pwdaToken（登录跳转后URL不再包含）

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(TAG, "★★ onCreate BEGIN");

        // 全局异常捕获，防止任何未处理异常导致静默finish()
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Log.e(TAG, "★★ UNCAUGHT EXCEPTION in thread " + t.getName() + ": " + e.getMessage(), e);
        });

        try {
            setContentView(R.layout.activity_tymj_login);
            Log.d(TAG, "★★ setContentView 完成");

            // 关键：先从 SharedPreferences 恢复 Session（包含保存的 Cookie）
            Session.get().loadConfig(this);

            webView          = findViewById(R.id.webViewTymj);
            progressBar      = findViewById(R.id.progressTymj);
            tvHint           = findViewById(R.id.tvTymjHint);
            btnReload        = findViewById(R.id.btnTymjReload);
            btnManual        = findViewById(R.id.btnTymjManual);
            layoutManualInput= findViewById(R.id.layoutManualInput);
            etManualToken    = findViewById(R.id.etManualToken);
            btnManualSubmit  = findViewById(R.id.btnManualSubmit);
            Log.d(TAG, "★★ findViewById 全部完成");

            if (btnReload != null) {
                btnReload.setOnClickListener(v -> {
                    captured = false;
                    Session.get().loadConfig(this);
                    hint("正在重新加载...");
                    loadPage();
                });
            }

            // 手动输入按钮：直接显示输入区
            if (btnManual != null) {
                btnManual.setOnClickListener(v -> {
                    showManualInput();
                });
            }

            // 手动输入提交
            if (btnManualSubmit != null) {
                btnManualSubmit.setOnClickListener(v -> {
                    String input = etManualToken.getText().toString().trim();
                    if (input.isEmpty()) {
                        Toast.makeText(this, "请先粘贴Token", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 自动去掉 "Bearer " 前缀
                    if (input.toLowerCase().startsWith("bearer ")) {
                        input = input.substring(7).trim();
                    }
                    doSaveAndFinish(input);
                });
            }

            setupWebView();
            Log.d(TAG, "★★ setupWebView 完成");
            loadPage();
            Log.d(TAG, "★★ loadPage 完成");

            // ★ 10秒后如果还没抓到token，自动显示手动输入区
            mainHandler.postDelayed(() -> {
                if (!captured) {
                    Log.d(TAG, "★★ JS Hook超时，显示手动输入区");
                    showManualInput();
                }
            }, 10000);
            Log.d(TAG, "★★ onCreate 全部完成");
        } catch (Exception e) {
            Log.e(TAG, "★★ onCreate 发生异常: " + e.getMessage(), e);
            hint("❌ 页面初始化失败: " + e.getMessage() + "\n请点击右上角「手动输入Token」");
            // 显示手动输入区作为后备
            if (layoutManualInput != null) layoutManualInput.setVisibility(View.VISIBLE);
        }
    }

    /** 显示手动输入区 */
    private void showManualInput() {
        if (layoutManualInput == null) return;
        layoutManualInput.setVisibility(View.VISIBLE);
        if (etManualToken != null) etManualToken.requestFocus();
        hint("👆 请在上方粘贴Bearer Token\n或点击「手动输入Token」按钮");
    }

    /** 保存token并返回给调用者 */
    private void doSaveAndFinish(String token) {
        Session session = Session.get();
        session.tower4aToken = token;
        session.saveTower4aToken(this);
        Session.notifyOn4aTokenReady();

        captured = true;
        mainHandler.postDelayed(() -> {
            Toast.makeText(this, "✅ Token已保存（len=" + token.length() + "）", Toast.LENGTH_SHORT).show();
            Intent result = new Intent();
            result.putExtra(EXTRA_TOKEN, token);
            setResult(RESULT_OK, result);
            finish();
        }, 300);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
    }

    private void hint(String msg) {
        if (tvHint != null) tvHint.setText(msg);
        Log.d(TAG, "[hint] " + msg.replace("\n", " | "));
    }

    private void loadPage() {
        // 获取4A Cookie
        Session s = Session.get();
        String tower4aCookie = s.tower4aSessionCookie;
        
        Log.d(TAG, ">>> loadPage() 开始");
        Log.d(TAG, ">>> Session中的tower4aSessionCookie: " + (tower4aCookie == null ? "null" : (tower4aCookie.isEmpty() ? "空" : tower4aCookie.substring(0, Math.min(100, tower4aCookie.length())))));

        // 检查Cookie是否有效（包含关键认证字段）
        boolean cookieValid = tower4aCookie != null && !tower4aCookie.isEmpty() 
                && (tower4aCookie.contains("SESSION=") || tower4aCookie.contains("Tnuocca="));
        
        Log.d(TAG, ">>> Cookie有效性检查: " + cookieValid);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        // ① 检查WebView中是否已有有效的4A Cookie
        String existing = cm.getCookie(BASE_4A);
        if (existing != null && existing.contains("SESSION")) {
            Log.d(TAG, ">>> WebView中已有有效4A Cookie，直接加载");
            hint("✅ 已检测到4A登录状态\n正在加载页面...\n\n💡 如需重新登录，请点击右上角刷新按钮");
            sessionCookieValid = true;
            Log.d(TAG, ">>> 加载URL: " + UA4A_INDEX);
            webView.loadUrl(UA4A_INDEX);
            return;
        }

        if (!cookieValid) {
            // Cookie无效或为空，需要用户在WebView中登录
            s.tower4aSessionCookie = "";  // 清空无效Cookie
            hint("⚠️ 未检测到4A登录状态\n请在下方页面登录4A（输入账号密码+短信验证码）\n登录成功后程序将自动获取Bearer Token\n\n💡 提示：登录成功后会自动记住，下次无需再登录");
            sessionCookieValid = false;
        } else {
            hint("✅ 已检测到4A登录状态\n正在加载页面...\n\n💡 如需重新登录，请点击右上角刷新按钮");
            sessionCookieValid = true;
        }

        // ② 直接注入Cookie，不删除任何旧Cookie
        // （4A服务器端Session过期后删除也没用，直接让页面显示登录页更可靠）
        if (cookieValid) {
            for (String part : tower4aCookie.split(";")) {
                String kv = part.trim();
                if (!kv.isEmpty()) {
                    cm.setCookie(BASE_4A, kv);
                    cm.setCookie(TYMS_HOST, kv);
                    Log.d(TAG, ">>> 注入Cookie: " + kv);
                }
            }
        }
        cm.setCookie(TYMS_HOST, "userOrgCode=100033");
        cm.flush();

        Log.d(TAG, ">>> 加载URL: " + UA4A_INDEX);
        webView.loadUrl(UA4A_INDEX);
    }

    private void setupWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setJavaScriptCanOpenWindowsAutomatically(true);
        ws.setSupportMultipleWindows(true);  // 改为true，真机上某些页面需要创建新窗口
        ws.setUserAgentString(UA);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setSafeBrowsingEnabled(false);  // 禁用安全浏览，减少资源

        // WebChromeClient
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                progressBar.setVisibility(progress >= 100 ? View.GONE : View.VISIBLE);
            }
            
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage msg) {
                String message = msg.message();
                String location = msg.sourceId() + ":" + msg.lineNumber();
                Log.d(TAG, "[Console] " + msg.messageLevel() + ": " + message);
                
                // 检测Token Hook日志
                if (message.contains("[TokenHook]")) {
                    Log.d(TAG, "[TokenHook日志] " + message);
                }
                
                // 检测JS错误
                if (msg.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                    Log.e(TAG, "[JS错误] " + message + " @ " + location);
                    if (message.contains("SyntaxError") || message.contains("Unexpected end")) {
                        hint("⚠️ 页面JS加载出错\n正在尝试修复...");
                    }
                }
                
                return true;
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                         boolean isUserGesture, android.os.Message resultMsg) {
                // 不创建新窗口，直接在主WebView中跟随链接
                Log.d(TAG, "window.open被阻止，将在主WebView中跟随链接");
                
                // 登录后点击链接，说明登录成功！立即捕获Cookie
                if (!sessionCookieValid) {
                    mainHandler.postDelayed(() -> captureAndSaveNewCookie(view), 2000);
                }
                
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(webView);  // 使用主WebView
                resultMsg.sendToTarget();
                return true;
            }
        });

        // WebViewClient
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                interceptToken(request, "main");
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                Log.d(TAG, "nav -> " + url);

                // 跳转到登录页 - 说明Cookie无效，需要用户在WebView中重新登录
                if (url.contains("uac/login") || url.contains("doPrevLogin")) {
                    // 清空Session中的旧Cookie（已过期）
                    Session session = Session.get();
                    session.tower4aSessionCookie = "";
                    Log.d(TAG, "4A Cookie已过期，已清空，将在WebView中重新登录");
                    
                    hint("⚠️ 4A Session已过期，正在自动填写账号密码...");
                    
                    // 不关闭页面，让用户在WebView中重新登录
                    // 重置captured状态，登录成功后继续获取Token
                    captured = false;
                    // 延迟1.5秒自动填充（等页面完全加载）
                    mainHandler.postDelayed(() -> autoFill4aLogin(webView), 1500);
                    return false; // 继续加载登录页
                }
                
                // 检测到进入门禁页面 - 立即提取pwdaToken并触发Bearer Token捕获
                if (url.contains("tymj.chinatowercom.cn")) {
                    Log.d(TAG, "tymj URL检测到: " + url);
                    if (url.contains("pwdaToken=")) {
                        hint("✅ 检测到门禁跳转，正在获取Bearer Token...");
                        // 提取并保存pwdaToken
                        try {
                            int start = url.indexOf("pwdaToken=") + "pwdaToken=".length();
                            int end = url.indexOf("&", start);
                            if (end < 0) end = url.length();
                            savedPwdaToken = url.substring(start, end);
                            Log.d(TAG, "保存pwdaToken: " + savedPwdaToken.substring(0, 30) + "...");
                        } catch (Exception e) {
                            Log.e(TAG, "保存pwdaToken失败: " + e.getMessage());
                        }
                    }
                    // 注入JS Hook捕获API请求中的Bearer Token（用于API认证）
                    // Bearer Token是hex格式，约96字符，不同于JWT
                    mainHandler.postDelayed(() -> tryInterceptBearerToken(view), 500);
                    // 继续加载
                    return false;
                }
                
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "finish -> " + url);
                if (captured) return;

                if (url.contains("4a.chinatowercom.cn") && !url.contains("login")) {
                    // 4A首页加载成功
                    // 如果之前Cookie无效，说明用户可能刚登录成功，捕获新Cookie
                    if (!sessionCookieValid) {
                        sessionCookieValid = true;
                        Log.d(TAG, "检测到4A登录成功，捕获新Cookie");
                        mainHandler.postDelayed(() -> captureAndSaveNewCookie(view), 1500);
                    }
                    hint("👆 请点击「统一门禁管理端」入口\n点击后程序将自动获取Bearer Token");
                } else if (url.contains("4a.chinatowercom.cn") && url.contains("login")) {
                    // 在登录页 - 自动填充账号密码
                    hint("正在自动填写账号密码，请等待短信验证码...");
                    mainHandler.postDelayed(() -> autoFill4aLogin(view), 1000);
                } else if (url.contains("tymj.chinatowercom.cn")) {
                    // 进入门禁页面
                    hint("✅ 已进入门禁页面，正在获取Bearer Token...");
                    
                    // 方式1：立即注入JS Hook并触发请求
                    mainHandler.post(() -> {
                        Log.d(TAG, ">>> [1] 注入JS Hook");
                        tryInterceptBearerToken(view);
                        mainHandler.postDelayed(() -> {
                            Log.d(TAG, ">>> [2] 触发API请求");
                            triggerApiCall(view);
                        }, 300);
                    });
                    
                    // 方式2：多次重试注入Hook（SPA页面可能需要多次尝试）
                    mainHandler.postDelayed(() -> {
                        if (!captured) {
                            Log.d(TAG, ">>> [3] 重试注入Hook");
                            tryInterceptBearerToken(view);
                        }
                    }, 2000);
                    
                    mainHandler.postDelayed(() -> {
                        if (!captured) {
                            Log.d(TAG, ">>> [4] 重试注入Hook");
                            tryInterceptBearerToken(view);
                            extractBearerFromStorage(view);
                        }
                    }, 4000);
                    
                    // 诊断：检查Hook是否安装成功
                    mainHandler.postDelayed(() -> {
                        if (!captured) {
                            checkHookInstalled(view);
                        }
                    }, 5000);
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode,
                                        String description, String failingUrl) {
                Log.e(TAG, "WebView error " + errorCode + " " + description);
            }
        });
    }

    /**
     * 拦截门禁API请求，提取Authorization Bearer Token
     * 通过 shouldInterceptRequest 拦截 HTTP 请求
     */
    private void interceptToken(WebResourceRequest request, String source) {
        if (captured) return;

        String url = request.getUrl().toString();
        // 只拦截 API 请求
        if (!url.contains("tymj.chinatowercom.cn")) return;
        
        // 检查是否是 recordAccess API（这才是需要拦截的请求）
        boolean isTargetApi = url.contains("/api/");
        Log.d(TAG, "[" + source + "] tymj请求: " + url.substring(0, Math.min(80, url.length())) + (isTargetApi ? " ★" : ""));

        // 检查Authorization请求头
        java.util.Map<String, String> headers = request.getRequestHeaders();
        if (headers != null) {
            String auth = headers.get("Authorization");
            if (auth != null && !auth.isEmpty()) {
                boolean isJWT = auth.contains(".");
                Log.d(TAG, "[" + source + "] Authorization: " + (isJWT ? "JWT" : "Bearer") + " " + auth.substring(0, Math.min(30, auth.length())));
                
                if (isJWT) {
                    // JWT 跳过，等 Bearer Token
                    return;
                }
                
                // 去掉 "Bearer " 前缀
                String token = auth.startsWith("Bearer ") ? auth.substring(7) : auth;
                if (token.length() >= 20) {
                    final String finalToken = token;
                    Log.d(TAG, "🎯 [" + source + "] 拦截到Bearer Token: " + finalToken.substring(0, 30) + "...");
                    mainHandler.post(() -> saveBearerToken(finalToken));
                }
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

        Log.d(TAG, "autoFill4a: 自动填充账号=" + username);
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
            "    if (btn) { btn.click(); }" +
            "  }, 500);" +
            "  return 'FILLED';" +
            "} catch(e) { return 'ERR:' + e.message; }" +
            "})()";

        view.evaluateJavascript(js, result -> {
            Log.d(TAG, "autoFill4a JS result: " + result);
            if (result != null && result.contains("NOT_FOUND")) {
                mainHandler.post(() -> hint("⚠️ 未找到登录输入框，请手动输入\n账号: " + username));
            } else if (result != null && result.contains("FILLED")) {
                mainHandler.post(() -> hint("✅ 已自动填写账号密码，正在提交...\n请等待短信验证码并输入"));
            }
        });
    }

    /**
     * 4A登录成功后，捕获新的Cookie并保存到Session
     */
    private void captureAndSaveNewCookie(WebView view) {
        // 通过JS获取WebView中的所有Cookie
        String js = "(function(){" +
                "  var cookies = document.cookie;" +
                "  return cookies;" +
                "})()";
        
        view.evaluateJavascript(js, value -> {
            String rawCookies = value;
            if (rawCookies != null && !rawCookies.isEmpty() && !rawCookies.equals("null")) {
                // 去掉首尾引号（JSON字符串格式）
                rawCookies = rawCookies.replaceAll("^\"|\"$", "");
                
                Log.d(TAG, ">>> 捕获到Cookie: " + rawCookies.substring(0, Math.min(100, rawCookies.length())));
                
                // 检查是否有4A Session Cookie
                boolean hasSession = rawCookies.contains("SESSION=") 
                        || rawCookies.contains("ULTRA_U_K=") 
                        || rawCookies.contains("Tnuocca=");
                
                if (hasSession) {
                    Log.d(TAG, "✅ 检测到有效的4A Session Cookie，正在保存...");
                    
                    // 保存到Session - 关键：下次启动时不用再登录！
                    // 使用同步保存确保立即持久化
                    Session session = Session.get();
                    session.tower4aSessionCookie = rawCookies;
                    PrefHelper.putStringSync(TymjLoginActivity.this, Session.PREF_SESSION, 
                            Session.KEY_TOWER4A_COOKIE, rawCookies);
                    
                    Log.d(TAG, "✅ Cookie已同步保存到SharedPreferences");
                    Toast.makeText(TymjLoginActivity.this, "✅ 4A登录成功\nCookie已保存\n下次无需重新登录", Toast.LENGTH_LONG).show();
                } else {
                    Log.d(TAG, "⚠️ Cookie中没有有效的Session字段");
                }
            } else {
                Log.d(TAG, "⚠️ 无法获取Cookie（可能页面未完全加载）");
            }
        });
    }

    /**
     * 直接调用GET接口，从响应头获取Bearer Token
     * 用户确认：这3个GET接口的响应头中都包含 Authorization: Bearer xxx
     */
    private void fetchBearerFromApi() {
        if (captured) return;
        
        new Thread(() -> {
            try {
                // 从Session获取Cookie
                Session session = Session.get();
                String cookie = session.tower4aSessionCookie;
                
                if (cookie == null || cookie.isEmpty()) {
                    Log.d(TAG, "⚠️ 没有4A Cookie，无法获取Bearer Token");
                    mainHandler.post(() -> hint("⚠️ 未检测到4A登录\n请先在WebView中登录4A"));
                    return;
                }
                
                // 尝试3个接口，任一成功即可
                String[] apis = {
                    "http://tymj.chinatowercom.cn:8006/api/recordAccess/findCodeNameMap",
                    "http://tymj.chinatowercom.cn:8006/api/doorOpeningRecord/queryAreaTree", 
                    "http://tymj.chinatowercom.cn:8006/api/recordAccess/openSourceDict"
                };
                
                for (String apiUrl : apis) {
                    if (captured) return;
                    
                    Log.d(TAG, ">>> 尝试GET: " + apiUrl);
                    
                    HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setRequestProperty("Cookie", cookie);
                    conn.setRequestProperty("User-Agent", UA);
                    conn.setRequestProperty("Authorization", "Bearer placeholder");
                    
                    try {
                        conn.connect();
                        int code = conn.getResponseCode();
                        Log.d(TAG, "    响应码: " + code);
                        
                        // 从响应头获取 Authorization
                        String auth = conn.getHeaderField("Authorization");
                        Log.d(TAG, "    响应头Authorization: " + (auth != null ? auth.substring(0, Math.min(40, auth.length())) + "..." : "null"));
                        
                        if (auth != null && auth.startsWith("Bearer ")) {
                            String token = auth.substring(7);
                            if (token.length() >= 20) {
                                Log.d(TAG, "🎯 从响应头获取Bearer Token: " + token.substring(0, 30) + "...");
                                mainHandler.post(() -> saveBearerToken(token));
                                return;
                            }
                        }
                    } finally {
                        conn.disconnect();
                    }
                }
                
                Log.d(TAG, "⚠️ 所有API响应头中都未找到Bearer Token");
                mainHandler.post(() -> hint("⚠️ 获取Token失败\n请确保已登录门禁系统"));
                
            } catch (Exception e) {
                Log.e(TAG, "fetchBearerFromApi异常: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 注入JS主动触发API调用，从响应body中提取Bearer Token
     * Bearer Token在响应body的 data.accessToken 字段
     */
    private void triggerApiCall(WebView view) {
        if (captured) return;
        
        // 使用已保存的pwdaToken
        if (savedPwdaToken.isEmpty()) {
            Log.d(TAG, "⚠️ 没有pwdaToken，无法触发API调用");
            return;
        }
        
        // 注入JS发送POST请求，并从响应body提取accessToken
        String js = "(function(){\n" +
                "  var xhr = new XMLHttpRequest();\n" +
                "  xhr.open('POST', '/api/recordAccess/getPage', false); // 同步请求\n" +
                "  xhr.setRequestHeader('Content-Type', 'application/json');\n" +
                "  xhr.setRequestHeader('Authorization', 'Bearer " + savedPwdaToken + "');\n" +
                "  xhr.setRequestHeader('pwdaToken', '" + savedPwdaToken + "');\n" +
                "  xhr.send(JSON.stringify({pageNum:1,pageSize:10}));\n" +
                "  console.log('[TokenTrigger] status=' + xhr.status);\n" +
                "  // 尝试从响应体提取accessToken\n" +
                "  try {\n" +
                "    var resp = JSON.parse(xhr.responseText);\n" +
                "    console.log('[TokenTrigger] resp=' + JSON.stringify(resp).substring(0,200));\n" +
                "    if (resp.data && resp.data.accessToken) {\n" +
                "      window._bearerToken = resp.data.accessToken;\n" +
                "      console.log('[TokenTrigger] 找到accessToken: ' + resp.data.accessToken);\n" +
                "    }\n" +
                "  } catch(e) {\n" +
                "    console.log('[TokenTrigger] 解析响应失败: ' + e.message);\n" +
                "  }\n" +
                "})()";
        
        Log.d(TAG, ">>> 注入JS触发POST请求并提取accessToken");
        view.evaluateJavascript(js, value -> {
            Log.d(TAG, ">>> 触发结果: " + value);
            // 然后读取提取到的token
            mainHandler.postDelayed(() -> readExtractedToken(view), 500);
        });
    }
    
    /**
     * 从WebView读取JS提取的Bearer Token
     */
    private void readExtractedToken(WebView view) {
        if (captured) return;
        
        String js = "(function(){ return window._bearerToken || ''; })()";
        view.evaluateJavascript(js, value -> {
            String token = value.replaceAll("^\"|\"$", "");
            if (token != null && token.length() >= 20) {
                Log.d(TAG, "🎯 从WebView读取到Bearer Token: " + token.substring(0, 30) + "...");
                mainHandler.post(() -> saveBearerToken(token));
            } else {
                Log.d(TAG, "⚠️ 未提取到Bearer Token，尝试其他方式");
                // 尝试从响应头获取（如果服务器支持）
                extractFromResponseHeader(view);
            }
        });
    }
    
    /**
     * 尝试从响应头获取Bearer Token
     */
    private void extractFromResponseHeader(WebView view) {
        // 使用fetch API尝试获取响应头
        String js = "(function(){\n" +
                "  return fetch('/api/recordAccess/findCodeNameMap', {\n" +
                "    method: 'GET',\n" +
                "    credentials: 'include'\n" +
                "  }).then(function(resp){\n" +
                "    console.log('[HeaderCheck] status=' + resp.status);\n" +
                "    var auth = resp.headers.get('Authorization');\n" +
                "    console.log('[HeaderCheck] Authorization=' + auth);\n" +
                "    return auth || '';\n" +
                "  }).catch(function(e){\n" +
                "    console.log('[HeaderCheck] error=' + e.message);\n" +
                "    return '';\n" +
                "  });\n" +
                "})()";
        
        view.evaluateJavascript(js, value -> {
            Log.d(TAG, ">>> 响应头检查结果: " + value);
            if (value != null && value.length() > 20) {
                String rawToken = value.replaceAll("^\"|\"$", "");
                if (rawToken.startsWith("Bearer ")) {
                    rawToken = rawToken.substring(7);
                }
                if (rawToken.length() >= 20) {
                    final String finalToken = rawToken;
                    mainHandler.post(() -> saveBearerToken(finalToken));
                }
            }
        });
    }

    /**
     * 从URL中提取pwdaToken作为Bearer Token（仅作为备用）
     * 注意：pwdaToken是JWT格式（约180字符），API需要的是Bearer Token（hex格式，约96字符）
     * 如果找到Bearer Token则优先使用
     */
    private void extractTokenFromUrl(WebView view, String url) {
        // 首先尝试从localStorage提取Bearer Token
        mainHandler.postDelayed(() -> extractBearerFromStorage(view), 200);
        
        // 从URL中提取pwdaToken参数作为备用
        // URL格式: http://tymj.chinatowercom.cn:8006/#/4asso?pwdaToken=xxx&acctId=xxx
        try {
            int tokenStart = url.indexOf("pwdaToken=");
            if (tokenStart >= 0) {
                tokenStart += "pwdaToken=".length();
                int tokenEnd = url.indexOf("&", tokenStart);
                if (tokenEnd < 0) tokenEnd = url.length();
                String pwdaToken = url.substring(tokenStart, tokenEnd);
                
                if (!pwdaToken.isEmpty()) {
                    Log.d(TAG, "从URL提取到pwdaToken（JWT备用）: " + pwdaToken.substring(0, Math.min(30, pwdaToken.length())) + "...");
                    // 延迟保存JWT，等Bearer Token先被提取
                    final String token = pwdaToken;
                    mainHandler.postDelayed(() -> saveTokenAsBackup(token), 3000);
                    return;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "提取pwdaToken失败: " + e.getMessage());
        }
        
        // 如果没找到pwdaToken，继续尝试其他方式
        hint("⚠️ 未从URL提取到Token，尝试其他方式...");
        mainHandler.postDelayed(() -> tryInterceptBearerToken(view), 2000);
    }
    
    /**
     * 从localStorage/sessionStorage/cookies提取Bearer Token
     * Bearer Token是64-128字符的hex字符串
     */
    private void extractBearerFromStorage(WebView view) {
        // 深度扫描：查找所有可能包含Bearer Token的地方
        String scanJs = "(function(){\n" +
                "  var results = [];\n" +
                "  var hexPattern = /^[a-f0-9]{64,128}$/i;\n" +
                "  \n" +
                "  // 扫描localStorage\n" +
                "  try {\n" +
                "    for (var i = 0; i < localStorage.length; i++) {\n" +
                "      var key = localStorage.key(i);\n" +
                "      var val = localStorage.getItem(key);\n" +
                "      if (val && hexPattern.test(val)) {\n" +
                "        results.push({type:'localStorage', key: key, val: val, len: val.length});\n" +
                "      }\n" +
                "      // 也检查JSON值内嵌的token\n" +
                "      if (val && val.length > 100 && val.includes('accessToken')) {\n" +
                "        try {\n" +
                "          var obj = JSON.parse(val);\n" +
                "          if (obj.accessToken && hexPattern.test(obj.accessToken)) {\n" +
                "            results.push({type:'localStorage.Json', key: key, val: obj.accessToken, len: obj.accessToken.length});\n" +
                "          }\n" +
                "          if (obj.token && hexPattern.test(obj.token)) {\n" +
                "            results.push({type:'localStorage.Json', key: key, val: obj.token, len: obj.token.length});\n" +
                "          }\n" +
                "        } catch(e) {}\n" +
                "      }\n" +
                "    }\n" +
                "  } catch(e) { results.push({error: 'localStorage: ' + e.message}); }\n" +
                "  \n" +
                "  // 扫描sessionStorage\n" +
                "  try {\n" +
                "    for (var i = 0; i < sessionStorage.length; i++) {\n" +
                "      var key = sessionStorage.key(i);\n" +
                "      var val = sessionStorage.getItem(key);\n" +
                "      if (val && hexPattern.test(val)) {\n" +
                "        results.push({type:'sessionStorage', key: key, val: val, len: val.length});\n" +
                "      }\n" +
                "    }\n" +
                "  } catch(e) { results.push({error: 'sessionStorage: ' + e.message}); }\n" +
                "  \n" +
                "  // 检查已知key\n" +
                "  var knownKeys = ['accessToken', 'bearerToken', 'token', 'Authorization', 'apiToken', 'BearerToken'];\n" +
                "  for (var k in knownKeys) {\n" +
                "    var v = localStorage.getItem(knownKeys[k]) || sessionStorage.getItem(knownKeys[k]);\n" +
                "    if (v && hexPattern.test(v)) {\n" +
                "      results.push({type:'knownKey', key: knownKeys[k], val: v, len: v.length});\n" +
                "    }\n" +
                "  }\n" +
                "  \n" +
                "  return JSON.stringify(results);\n" +
                "})()";
        
        view.evaluateJavascript(scanJs, value -> {
            Log.d(TAG, "深度扫描结果: " + value);
            try {
                // 直接用正则提取96字符的hex字符串（Bearer Token特征）
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("[a-f0-9]{96}");
                java.util.regex.Matcher m = p.matcher(value);
                if (m.find()) {
                    String token = m.group();
                    Log.d(TAG, "🎯 找到Bearer Token: " + token.substring(0, 30) + "...");
                    mainHandler.post(() -> saveBearerToken(token));
                    return;
                }
                // 备用：尝试解析JSON数组
                org.json.JSONArray arr = new org.json.JSONArray(value);
                if (arr.length() > 0) {
                    org.json.JSONObject item = arr.getJSONObject(0);
                    String val = item.optString("val", "");
                    if (val.length() >= 20) {
                        Log.d(TAG, "🎯 找到Bearer Token (JSON): " + val.substring(0, 30) + "...");
                        mainHandler.post(() -> saveBearerToken(val));
                        return;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "解析扫描结果失败: " + e.getMessage());
            }
            
            Log.d(TAG, "未找到Bearer Token，尝试JS Hook拦截...");
            mainHandler.postDelayed(() -> tryInterceptBearerToken(view), 500);
        });
    }
    
    /**
     * 主动触发API调用，触发Bearer Token生成
     * 通过注入脚本发送一个XHR请求，我们的Hook会捕获Authorization头
     */
    private void triggerApiCallForToken(WebView view) {
        if (captured) return;
        
        Log.d(TAG, ">>> 主动触发API调用以生成Bearer Token");
        
        String triggerJs = "(function(){\n" +
                "  // 尝试触发一个API调用\n" +
                "  var xhr = new XMLHttpRequest();\n" +
                "  xhr.open('POST', '/api/recordAccess/getPage', true);\n" +
                "  xhr.setRequestHeader('Content-Type', 'application/json');\n" +
                "  xhr.setRequestHeader('Accept', 'application/json');\n" +
                "  xhr.onload = function() {\n" +
                "    console.log('[TokenTrigger] API响应: ' + xhr.status);\n" +
                "  };\n" +
                "  xhr.onerror = function() {\n" +
                "    console.log('[TokenTrigger] API调用失败');\n" +
                "  };\n" +
                "  var body = JSON.stringify({\n" +
                "    startTime: '2026-04-01 00:00:00',\n" +
                "    endTime: '2026-04-01 23:59:59',\n" +
                "    countyCodeList: ['330326'],\n" +
                "    pageNum: 1,\n" +
                "    pageSize: 10\n" +
                "  });\n" +
                "  xhr.send(body);\n" +
                "  console.log('[TokenTrigger] 已发送API请求');\n" +
                "  return 'triggered';\n" +
                "})()";
        
        view.evaluateJavascript(triggerJs, value -> {
            Log.d(TAG, "Token触发脚本执行结果: " + value);
        });
    }
    
    /**
     * 检查页面健康状态
     */
    private void checkPageHealth(WebView view) {
        if (captured) return;
        
        String healthJs = "(function(){\n" +
                "  var result = {\n" +
                "    hasWindow: typeof window !== 'undefined',\n" +
                "    hasDocument: typeof document !== 'undefined',\n" +
                "    hasXHR: typeof XMLHttpRequest !== 'undefined',\n" +
                "    hasFetch: typeof fetch !== 'undefined',\n" +
                "    tokenHooked: window._tokenHooked || false,\n" +
                "    capturedToken: window._capturedToken || null,\n" +
                "    docReady: document.readyState,\n" +
                "    url: window.location.href.substring(0, 100)\n" +
                "  };\n" +
                "  return JSON.stringify(result);\n" +
                "})()";
        
        view.evaluateJavascript(healthJs, value -> {
            Log.d(TAG, ">>> 页面健康检查: " + value);
            try {
                org.json.JSONObject obj = new org.json.JSONObject(value);
                boolean hooked = obj.optBoolean("tokenHooked", false);
                String captured = obj.optString("capturedToken", "null");
                String docReady = obj.optString("docReady", "");
                
                Log.d(TAG, ">>> Hook安装: " + hooked + ", 已捕获Token: " + captured + ", 文档状态: " + docReady);
                
                if (!hooked) {
                    hint("⚠️ 页面JS未正确加载\n正在重新注入...");
                    tryInterceptBearerToken(view);
                    // 同时尝试从Java端获取
                    tryGetTokenFromJava();
                    // 检查Cookie
                    checkWebViewCookies(view);
                } else if (captured.equals("null") || captured.isEmpty()) {
                    hint("👆 Hook已安装\n请在门禁页面进行操作...\n如查询数据等");
                    // 检查Cookie
                    checkWebViewCookies(view);
                }
            } catch (Exception e) {
                Log.e(TAG, "解析健康检查失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 尝试从Java端直接调用API获取Bearer Token
     * 通过检查响应头来寻找可能的Token
     */
    private void tryGetTokenFromJava() {
        if (captured) return;
        
        Log.d(TAG, ">>> 尝试从Java端获取Bearer Token...");
        
        new Thread(() -> {
            try {
                // 尝试调用token端点（如果存在）
                String[] endpoints = {
                    "http://tymj.chinatowercom.cn:8006/api/auth/token",
                    "http://tymj.chinatowercom.cn:8006/api/login",
                    "http://tymj.chinatowercom.cn:8006/api/getToken"
                };
                
                Session s = Session.get();
                String cookie = s.tower4aSessionCookie;
                
                for (String endpoint : endpoints) {
                    try {
                        URL url = new URL(endpoint);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("Cookie", cookie);
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                        conn.setConnectTimeout(3000);
                        conn.setReadTimeout(3000);
                        
                        int code = conn.getResponseCode();
                        Log.d(TAG, ">>> 端点 " + endpoint + " 返回: " + code);
                        
                        // 检查响应头
                        String authHeader = conn.getHeaderField("Authorization");
                        String tokenHeader = conn.getHeaderField("X-Token");
                        if (authHeader != null) Log.d(TAG, ">>> Authorization: " + authHeader);
                        if (tokenHeader != null) Log.d(TAG, ">>> X-Token: " + tokenHeader);
                        
                        conn.disconnect();
                    } catch (Exception e) {
                        Log.d(TAG, ">>> 端点 " + endpoint + " 失败: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "获取Token失败: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 检查WebView Cookie中是否有Bearer Token
     */
    private void checkWebViewCookies(WebView view) {
        if (captured) return;
        
        String cookieJs = "(function(){\n" +
                "  var cookies = document.cookie;\n" +
                "  // 查找可能的token cookie\n" +
                "  var parts = cookies.split(';');\n" +
                "  for (var i = 0; i < parts.length; i++) {\n" +
                "    var kv = parts[i].split('=');\n" +
                "    if (kv.length >= 2) {\n" +
                "      var name = kv[0].trim().toLowerCase();\n" +
                "      if (name.includes('token') || name.includes('bearer') || name.includes('auth')) {\n" +
                "        return {key: kv[0].trim(), val: kv.slice(1).join('=')};\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "  return null;\n" +
                "})()";
        
        view.evaluateJavascript(cookieJs, value -> {
            Log.d(TAG, ">>> Cookie检查结果: " + value);
            if (value != null && !value.equals("null") && !value.equals("")) {
                try {
                    org.json.JSONObject obj = new org.json.JSONObject(value);
                    String key = obj.optString("key", "");
                    String val = obj.optString("val", "");
                    
                    if (!val.isEmpty() && val.length() >= 20) {
                        boolean isJWT = val.contains(".");
                        boolean isBearer = !isJWT && val.length() <= 150;
                        
                        if (isBearer) {
                            Log.d(TAG, "🎯 从Cookie找到Bearer Token: " + val.substring(0, 30) + "...");
                            mainHandler.post(() -> saveBearerToken(val));
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析Cookie失败: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 保存Bearer Token（直接使用，不检查是否已保存）
     */
    private void saveBearerToken(String token) {
        if (token == null || token.isEmpty() || token.length() < 20) {
            Log.w(TAG, "Bearer Token太短，忽略");
            return;
        }
        
        Log.d(TAG, "🎯 保存Bearer Token: " + token.substring(0, Math.min(30, token.length())) + "... (长度=" + token.length() + ")");
        
        hint("✅ 成功获取Bearer Token！");
        Toast.makeText(this, "✅ Bearer Token 获取成功！", Toast.LENGTH_SHORT).show();

        Session session = Session.get();
        session.tower4aToken = token;
        session.saveTower4aToken(this);
        Session.notifyOn4aTokenReady(); // ★ 通知所有等待者（自动登录场景）

        captured = true;
        currentTokenIsBearer = true;

        mainHandler.postDelayed(() -> {
            Log.d(TAG, "★★ saveBearerToken setResult + finish() 执行中");
            Intent result = new Intent();
            result.putExtra(EXTRA_TOKEN, token);
            setResult(RESULT_OK, result);
            finish();
            Log.d(TAG, "★★ TymjLoginActivity 已finish");
        }, 800);
    }

    /**
     * 备用保存JWT Token（如果没找到Bearer Token才使用）
     */
    private void saveTokenAsBackup(String token) {
        if (captured) {
            Log.d(TAG, "已有Bearer Token，跳过JWT备份");
            return;
        }
        Log.d(TAG, "未找到Bearer Token，使用JWT作为备用");
        saveToken(token);
    }

    /**
     * 尝试拦截Bearer Token（通过注入JS Hook XHR/fetch）
     * Bearer Token 在用户登录后由前端JS调用API动态生成，需要拦截XHR请求捕获
     */
    private void tryInterceptBearerToken(WebView view) {
        if (captured) return;
        
        // 首先注入 Android 接口
        view.addJavascriptInterface(new JsInterface(), "Android");
        
        // Hook XMLHttpRequest来拦截所有API请求的Authorization头
        String hookJs = "(function(){\n" +
                "  if (window._tokenHooked) return 'already hooked';\n" +
                "  window._tokenHooked = true;\n" +
                "  \n" +
                "  // Hook XMLHttpRequest\n" +
                "  var OriginalXHR = window.XMLHttpRequest;\n" +
                "  window.XMLHttpRequest = function(){\n" +
                "    var xhr = new OriginalXHR();\n" +
                "    var originalSetRequestHeader = xhr.setRequestHeader;\n" +
                "    xhr.setRequestHeader = function(name, value){\n" +
                "      if (name.toLowerCase() === 'authorization') {\n" +
                "        window._capturedToken = value;\n" +
                "        console.log('[TokenHook] XHR Authorization: ' + value);\n" +
                "        try { window.Android.captureToken(value); } catch(e) {}\n" +
                "      }\n" +
                "      return originalSetRequestHeader.call(this, name, value);\n" +
                "    };\n" +
                "    return xhr;\n" +
                "  };\n" +
                "  \n" +
                "  // Hook fetch\n" +
                "  var OriginalFetch = window.fetch;\n" +
                "  window.fetch = function(url, options){\n" +
                "    if (options && options.headers) {\n" +
                "      var headers = options.headers;\n" +
                "      if (headers instanceof Headers) {\n" +
                "        headers.forEach(function(value, name) {\n" +
                "          if (name.toLowerCase() === 'authorization') {\n" +
                "            window._capturedToken = value;\n" +
                "            console.log('[TokenHook] fetch Authorization: ' + value);\n" +
                "            try { window.Android.captureToken(value); } catch(e) {}\n" +
                "          }\n" +
                "        });\n" +
                "      } else {\n" +
                "        for (var key in headers) {\n" +
                "          if (key.toLowerCase() === 'authorization') {\n" +
                "            window._capturedToken = headers[key];\n" +
                "            console.log('[TokenHook] fetch2 Authorization: ' + headers[key]);\n" +
                "            try { window.Android.captureToken(headers[key]); } catch(e) {}\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "    return OriginalFetch.apply(this, arguments);\n" +
                "  };\n" +
                "  \n" +
                "  console.log('[TokenHook] XHR/Fetch hooked successfully');\n" +
                "  return 'XHR/Fetch hooked OK';\n" +
                "})()";
        
        // 添加Android JS接口用于回调Token
        view.addJavascriptInterface(new JsInterface(), "Android");
        
        view.evaluateJavascript(hookJs, value -> {
            Log.d(TAG, "Hook XHR/Fetch注入结果: " + value);
            if (value != null && value.contains("hooked")) {
                hint("👆 已注入拦截脚本\n正在等待API请求...\n请稍候...");
            } else {
                Log.w(TAG, "⚠️ Hook注入可能失败，尝试重新注入...");
                // 重新注入一次
                mainHandler.postDelayed(() -> {
                    view.evaluateJavascript(hookJs, v2 -> {
                        Log.d(TAG, "Hook重新注入结果: " + v2);
                    });
                }, 1000);
            }
        });
    }
    
    /**
     * 检查JS Hook是否安装成功
     */
    private void checkHookInstalled(WebView view) {
        String js = "(function(){\n" +
                "  return {\n" +
                "    hooked: !!window._tokenHooked,\n" +
                "    hasAndroid: !!window.Android,\n" +
                "    xhrHooked: window.XMLHttpRequest.toString().indexOf('function') !== -1,\n" +
                "    storageKeys: localStorage.length,\n" +
                "    sessionKeys: sessionStorage.length\n" +
                "  };\n" +
                "})()";
        
        view.evaluateJavascript(js, value -> {
            Log.d(TAG, ">>> Hook健康检查: " + value);
            if (!captured) {
                // 最后尝试：从页面全局变量中搜索token
                searchGlobalTokens(view);
            }
        });
    }
    
    /**
     * 从页面全局变量中搜索Bearer Token
     */
    private void searchGlobalTokens(WebView view) {
        String js = "(function(){\n" +
                "  var results = [];\n" +
                "  var hexPattern = /^[a-f0-9]{64,128}$/i;\n" +
                "  \n" +
                "  // 搜索常见对象\n" +
                "  var searchIn = function(obj, path) {\n" +
                "    if (!obj || typeof obj !== 'object') return;\n" +
                "    for (var key in obj) {\n" +
                "      try {\n" +
                "        var val = obj[key];\n" +
                "        if (typeof val === 'string' && hexPattern.test(val)) {\n" +
                "          results.push({path: path + '.' + key, val: val, len: val.length});\n" +
                "        }\n" +
                "      } catch(e) {}\n" +
                "    }\n" +
                "  };\n" +
                "  \n" +
                "  // 搜索常见全局对象\n" +
                "  searchIn(window, 'window');\n" +
                "  searchIn(sessionStorage, 'sessionStorage');\n" +
                "  searchIn(localStorage, 'localStorage');\n" +
                "  \n" +
                "  return JSON.stringify(results.slice(0, 10));\n" +
                "})()";
        
        view.evaluateJavascript(js, value -> {
            Log.d(TAG, ">>> 全局变量搜索: " + value);
            try {
                // 直接用正则提取96字符的hex字符串
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("[a-f0-9]{96}");
                java.util.regex.Matcher m = p.matcher(value);
                if (m.find()) {
                    String token = m.group();
                    Log.d(TAG, "🎯 从全局变量找到Token: " + token.substring(0, 30) + "...");
                    mainHandler.post(() -> saveBearerToken(token));
                    return;
                }
                // 备用：尝试解析JSON数组
                org.json.JSONArray arr = new org.json.JSONArray(value);
                if (arr.length() > 0) {
                    org.json.JSONObject item = arr.getJSONObject(0);
                    String val = item.optString("val", "");
                    if (val.length() >= 20) {
                        Log.d(TAG, "🎯 从全局变量找到Token (JSON): " + val.substring(0, 30) + "...");
                        mainHandler.post(() -> saveBearerToken(val));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "解析全局搜索结果失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * JavaScript接口，用于从JS回调Token
     * 从XHR/Fetch请求中拦截到的Bearer Token会通过此接口回调
     */
    private class JsInterface {
        @android.webkit.JavascriptInterface
        public void captureToken(String authHeader) {
            if (authHeader == null || authHeader.isEmpty()) return;
            
            String rawToken = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            
            if (rawToken.length() >= 20) {
                boolean isJWT = rawToken.contains(".");
                Log.d(TAG, "JS Hook捕获到Token: " + (isJWT ? "JWT" : "Bearer") + " len=" + rawToken.length() + " val=" + rawToken.substring(0, Math.min(30, rawToken.length())) + "...");
                
                if (isJWT) {
                    // JWT 跳过，等 Bearer Token
                    Log.d(TAG, "跳过JWT，等待Bearer Token...");
                } else {
                    // Bearer Token，立即保存！
                    final String finalToken = rawToken;
                    mainHandler.post(() -> saveBearerToken(finalToken));
                }
            }
        }
    }

    /**
     * 保存Token并关闭页面
     * 优先使用短token（Bearer hex格式，约96字符），不用长token（JWT格式，约180字符）
     */
    private void saveToken(String token) {
        if (token == null || token.isEmpty() || token.length() < 20) {
            Log.w(TAG, "Token太短，忽略");
            return;
        }
        
        // 判断是JWT还是Bearer Token
        boolean isJWT = token.contains(".");  // JWT格式有3段，用.分隔
        boolean isBearerHex = !isJWT && token.length() <= 120;  // Bearer是hex格式，较短
        
        Log.d(TAG, "收到Token: " + (isJWT ? "JWT" : "Bearer/Hex") + " 长度=" + token.length());
        
        // 如果已经捕获了Bearer Token（短token），忽略后续的JWT
        if (captured && currentTokenIsBearer) {
            Log.d(TAG, "已有Bearer Token，忽略后续Token");
            return;
        }
        
        // 如果收到的是JWT但已经有Bearer Token，跳过
        if (isJWT && currentTokenIsBearer) {
            Log.d(TAG, "已有Bearer Token，跳过JWT");
            return;
        }
        
        // 优先Bearer Token（短），覆盖JWT
        captured = true;
        currentTokenIsBearer = isBearerHex;
        
        hint("✅ 成功获取Bearer Token！");
        Toast.makeText(this, "✅ Bearer Token 获取成功！", Toast.LENGTH_SHORT).show();

        Session session = Session.get();
        session.tower4aToken = token;
        session.saveTower4aToken(this);
        Session.notifyOn4aTokenReady(); // ★ 通知所有等待者（自动登录场景）

        mainHandler.postDelayed(() -> {
            Intent result = new Intent();
            result.putExtra(EXTRA_TOKEN, token);
            setResult(RESULT_OK, result);
            finish();
        }, 800);
    }

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
