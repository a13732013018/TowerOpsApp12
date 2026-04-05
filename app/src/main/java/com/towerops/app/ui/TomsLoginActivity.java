package com.towerops.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.towerops.app.R;
import com.towerops.app.model.Session;

/**
 * TOMS 巡检打卡 Token 获取页（WebView + 网络层拦截）
 *
 * 【方案说明】
 *   自动点击菜单的方式因菜单在 iframe 内，JS 注入无法访问，已放弃。
 *   改为：加载 OMMS 首页 → 提示用户手动点击菜单 → 网络层拦截自动抓取 token。
 *
 * 【流程】
 *   1. 注入 OMMS Cookie，加载 OMMS 首页
 *   2. 显示操作指引：工单管理 → 新安全打卡 → 安全打卡综合查询
 *   3. shouldInterceptRequest 拦截所有到 TOMS 的请求头，找到 token 字段
 *   4. 抓到后自动保存并关闭页面
 */
public class TomsLoginActivity extends Activity {

    public static final String EXTRA_TOKEN = "toms_token";

    private static final String TAG       = "TomsLogin";
    private static final String OMMS_HOST = "omms.chinatowercom.cn";
    private static final String TOMS_HOST = "chntoms5.chinatowercom.cn";
    private static final String TOMS_HOST2 = "chntoms";
    private static final String OMMS_URL  = "http://omms.chinatowercom.cn:9000/layout/index.xhtml";

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private WebView     webView;
    private ProgressBar progressBar;
    private TextView    tvHint;
    private View        btnReload;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean captured = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_toms_login);

        webView     = findViewById(R.id.webViewToms);
        progressBar = findViewById(R.id.progressToms);
        tvHint      = findViewById(R.id.tvTomsHint);
        btnReload   = findViewById(R.id.btnTomsReload);

        if (btnReload != null) {
            btnReload.setOnClickListener(v -> {
                captured = false;
                hint("正在重新加载...");
                injectOmmsCookieAndLoad();
            });
        }

        setupWebView();
        injectOmmsCookieAndLoad();
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

        // ── WebChromeClient ────────────────────────────────────────────────
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                progressBar.setVisibility(progress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, android.os.Message resultMsg) {
                // 处理 window.open 打开的新窗口（TOMS 可能通过此方式打开）
                WebView newWebView = new WebView(TomsLoginActivity.this);
                newWebView.getSettings().setJavaScriptEnabled(true);
                newWebView.getSettings().setDomStorageEnabled(true);
                newWebView.getSettings().setUserAgentString(UA);
                newWebView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                CookieManager.getInstance().setAcceptThirdPartyCookies(newWebView, true);

                newWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
                        interceptToken(request, "newWindow");
                        return null;
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                        String url = req.getUrl().toString();
                        android.util.Log.d(TAG, "newWindow nav -> " + url.substring(0, Math.min(120, url.length())));
                        if (!captured && (url.contains(TOMS_HOST) || url.contains(TOMS_HOST2))) {
                            String token = extractUrlParam(url, "pwdaToken");
                            if (token != null && !token.isEmpty()) {
                                android.util.Log.d(TAG, "🎯 [newWindow override] URL跳转拦截 pwdaToken");
                                mainHandler.post(() -> saveToken(token));
                                return true;
                            }
                        }
                        return false;
                    }

                    @Override
                    public void onPageFinished(WebView v, String url) {
                        android.util.Log.d(TAG, "newWindow finish -> " + url);
                    }
                });

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newWebView);
                resultMsg.sendToTarget();
                return true;
            }
        });

        // ── WebViewClient ──────────────────────────────────────────────────
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                interceptToken(request, "main");
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                android.util.Log.d(TAG, "nav -> " + url.substring(0, Math.min(120, url.length())));
                // 跳转到 TOMS 时，URL 里带有 pwdaToken 参数，直接提取
                if (!captured && (url.contains(TOMS_HOST) || url.contains(TOMS_HOST2))) {
                    String token = extractUrlParam(url, "pwdaToken");
                    if (token != null && !token.isEmpty()) {
                        android.util.Log.d(TAG, "🎯 [override] URL跳转拦截 pwdaToken");
                        mainHandler.post(() -> saveToken(token));
                        return true; // 阻止跳转，避免 ERR_CONNECTION_TIMED_OUT
                    }
                    // 没有 token 参数，允许跳转尝试加载
                    hint("✅ 已进入 TOMS 页面，正在获取 Token...");
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                android.util.Log.d(TAG, "start -> " + url.substring(0, Math.min(120, url.length())));
                // 页面开始加载时也检查 URL 里的 pwdaToken（某些情况 shouldOverrideUrlLoading 不触发）
                if (!captured && url.contains("pwdaToken=")) {
                    String token = extractUrlParam(url, "pwdaToken");
                    if (token != null && !token.isEmpty()) {
                        android.util.Log.d(TAG, "🎯 [onPageStarted] URL含pwdaToken");
                        mainHandler.post(() -> saveToken(token));
                    }
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                android.util.Log.d(TAG, "finish -> " + url);
                if (captured) return;

                if (url.contains(OMMS_HOST)) {
                    // OMMS 页面加载完成，立即显示操作指引
                    hint("👆 请在下方页面手动操作：\n左侧菜单 → 工单管理 → 新安全打卡\n→ 安全打卡综合查询\n\n点击后程序将自动抓取 Token");
                } else if (url.contains(TOMS_HOST) || url.contains(TOMS_HOST2)) {
                    hint("✅ 已进入 TOMS 页面，正在获取 Token...");
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode,
                                        String description, String failingUrl) {
                android.util.Log.e(TAG, "WebView error " + errorCode + " " + description
                        + " url=" + failingUrl);
            }
        });
    }

    /**
     * 通用拦截：从 URL 参数里提取 pwdaToken（或请求头里的 token）
     *
     * OMMS 跳转 TOMS 时 URL 格式：
     *   http://chntoms5...?view=...&pwdaToken=xxx&acctId=...
     * token 在 URL 参数里，不是请求头。
     * TOMS 页面本身可能因网络原因打不开，但我们在请求发出时就能拿到 token，无需页面加载成功。
     */
    private void interceptToken(WebResourceRequest request, String source) {
        if (captured) return;
        String url = request.getUrl().toString();
        if (!url.contains(TOMS_HOST) && !url.contains(TOMS_HOST2)) return;

        android.util.Log.d(TAG, "[" + source + "] TOMS请求: " + url.substring(0, Math.min(120, url.length())));

        // 1. 优先从 URL 参数提取 pwdaToken
        String token = extractUrlParam(url, "pwdaToken");

        // 2. 兜底：检查请求头里的 token / Authorization
        if (token == null || token.isEmpty()) {
            java.util.Map<String, String> headers = request.getRequestHeaders();
            if (headers != null) {
                for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
                    String key = entry.getKey();
                    if (("token".equalsIgnoreCase(key) || "authorization".equalsIgnoreCase(key))
                            && entry.getValue() != null && !entry.getValue().isEmpty()) {
                        token = entry.getValue().replaceFirst("(?i)^Bearer\\s+", "");
                        break;
                    }
                }
            }
        }

        if (token != null && !token.isEmpty()) {
            final String finalToken = token;
            android.util.Log.d(TAG, "🎯 [" + source + "] 拦截到 token: " + finalToken.substring(0, Math.min(30, finalToken.length())) + "...");
            mainHandler.post(() -> saveToken(finalToken));
        }
    }

    /**
     * 从 URL 中提取指定参数值
     */
    private String extractUrlParam(String url, String paramName) {
        try {
            android.net.Uri uri = android.net.Uri.parse(url);
            String value = uri.getQueryParameter(paramName);
            if (value != null && !value.isEmpty()) return value;
            // 兜底：手动解析（防止编码问题）
            String search = paramName + "=";
            int idx = url.indexOf(search);
            if (idx == -1) return null;
            int start = idx + search.length();
            int end = url.indexOf("&", start);
            return end == -1 ? url.substring(start) : url.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 保存 token 并关闭页面
     */
    private void saveToken(String token) {
        if (captured) return;
        if (token == null || token.isEmpty()) return;
        // pwdaToken 格式可能是 xxx-xxx_xxx（非JWT），也可能是JWT三段式，均接受
        if (token.length() < 10) {
            android.util.Log.w(TAG, "Token too short, ignore: " + token);
            return;
        }

        captured = true;
        hint("✅ 成功获取 TOMS Token！");
        Toast.makeText(this, "✅ TOMS Token 获取成功！", Toast.LENGTH_SHORT).show();

        Session session = Session.get();
        session.tomsToken = token;
        getSharedPreferences("session", MODE_PRIVATE)
                .edit().putString("tomsToken", token).apply();

        mainHandler.postDelayed(() -> {
            Intent result = new Intent();
            result.putExtra(EXTRA_TOKEN, token);
            setResult(RESULT_OK, result);
            finish();
        }, 800);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 注入 OMMS Cookie 并加载 OMMS 首页
     */
    private void injectOmmsCookieAndLoad() {
        Session s = Session.get();
        String ommsCookie = s.ommsCookie;
        CookieManager cm = CookieManager.getInstance();

        if (ommsCookie == null || ommsCookie.isEmpty() || !ommsCookie.contains("JSESSIONID")) {
            hint("未检测到 OMMS 登录状态，\n请先去【门禁系统】Tab 完成 OMMS 登录");
            return;
        }

        hint("正在注入 OMMS Cookie...");
        cm.removeAllCookies(success -> {
            for (String part : ommsCookie.split(";")) {
                String kv = part.trim();
                if (!kv.isEmpty()) {
                    cm.setCookie("http://" + OMMS_HOST + ":9000", kv);
                    cm.setCookie("http://" + OMMS_HOST, kv);
                }
            }
            cm.flush();

            mainHandler.post(() -> {
                hint("Cookie 注入完成，正在加载 OMMS...");
                webView.loadUrl(OMMS_URL);
            });
        });
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
