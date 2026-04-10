package com.towerops.app.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.api.AccessControlApi;
import com.towerops.app.api.LoginApi;

import com.towerops.app.api.TowerLoginApi;
import com.towerops.app.api.TymjWebViewHelper;
import com.towerops.app.model.AccessControlItem;
import com.towerops.app.model.AccountConfig;
import com.towerops.app.model.Session;
import com.towerops.app.util.CookieStore;
import com.towerops.app.util.HttpUtil;
import com.towerops.app.util.ThreadManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 门禁系统 Tab Fragment
 *
 * 功能对应易语言：
 * 1. 阶段一：并发查询（最多20线程），取每个站的蓝牙记录+远程开门时间
 * 2. 阶段二：单行道排队自动开门（仿生延迟 2.5-4.5秒 + 冷却 4-7秒）
 *
 * 额外配置：
 * - 是否自动开门（CheckBox）
 * - OMMS协议头输入框（粘贴 Cookie/Headers）
 */
public class AccessControlFragment extends Fragment {

    // ── UI 控件 ────────────────────────────────────────────────────────────
    private Button      btnStart, btnStop, btnOmmsLogin;
    private CheckBox    cbAutoOpen;
    private EditText    etOmmsCookie;
    private TextView    tvLog;
    private RecyclerView recyclerView;
    private AccessControlAdapter adapter;

    // ── 4A登录控件 ─────────────────────────────────────────────────────────
    private Spinner                     spinnerAccount4A;
    private Button                      btnGetSmsAc;
    private android.widget.LinearLayout layout4aCodeRowAc;
    private EditText                    etPinAc;
    private Button                      btnLoginAc;
    private TextView                    tvLoginStatus;

    // ── 4A登录状态（TowerLoginApi 流程） ───────────────────────────────────
    private TowerLoginApi towerLoginApi4A;
    private String        msgId4A;
    private boolean       codeSent4A = false;

    // ── 4A已登录状态行 ──────────────────────────────────────────────────────
    private android.widget.LinearLayout layoutLoggedIn4A;
    private TextView    tvLoggedInUser;
    private Button      btnLogout4A;

    // ── 4A Token 输入控件 ─────────────────────────────────────────────────
    private EditText    et4AToken;
    private EditText    et4ACountyCode;
    private EditText    et4ASsoPwd;
    private Button      btn4ASave;
    private Button      btn4AGet;

    // OMMS登录Activity请求码
    private static final int REQ_OMMS_LOGIN = 0x1001;

    // ── 运行状态 ──────────────────────────────────────────────────────────
    private volatile boolean isRunning = false;

    // ── 线程池和并发锁 ────────────────────────────────────────────────────
    /**
     * 最多20个并发查询线程（阶段一防封核心：服务器安全线）
     */
    private static final int MAX_CONCURRENT = 20;
    private final Semaphore querySemaphore = new Semaphore(MAX_CONCURRENT, true);
    private ExecutorService queryPool;

    // ── 随机数 ────────────────────────────────────────────────────────────
    private final Random rnd = new Random();

    // ─────────────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_access_control, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ★ 确保 Session 数据已恢复（应用重启后 volatile 变量会丢失）
        Session.get().loadConfig(requireContext());

        btnStart      = view.findViewById(R.id.btnAcStart);
        btnStop       = view.findViewById(R.id.btnAcStop);
        btnOmmsLogin  = view.findViewById(R.id.btnOmmsLogin);
        cbAutoOpen    = view.findViewById(R.id.cbAcAutoOpen);
        etOmmsCookie  = view.findViewById(R.id.etOmmsCookie);
        tvLog         = view.findViewById(R.id.tvAcLog);
        recyclerView  = view.findViewById(R.id.recyclerAccessControl);

        // 4A登录控件
        spinnerAccount4A  = view.findViewById(R.id.spinnerAccount4A);
        btnGetSmsAc       = view.findViewById(R.id.btnGetSmsAc);
        layout4aCodeRowAc = view.findViewById(R.id.layout4aCodeRowAc);
        etPinAc           = view.findViewById(R.id.etPinAc);
        btnLoginAc        = view.findViewById(R.id.btnLoginAc);
        tvLoginStatus     = view.findViewById(R.id.tvLoginStatus);

        // 4A已登录状态行
        layoutLoggedIn4A = view.findViewById(R.id.layoutLoggedIn4A);
        tvLoggedInUser   = view.findViewById(R.id.tvLoggedInUser);
        btnLogout4A      = view.findViewById(R.id.btnLogout4A);

        // 4A Token 输入控件（用于门禁数据Tab查询4A开门记录）
        et4AToken      = view.findViewById(R.id.et4AToken);
        et4ACountyCode = view.findViewById(R.id.et4ACountyCode);
        et4ASsoPwd     = view.findViewById(R.id.et4ASsoPwd);
        btn4ASave      = view.findViewById(R.id.btn4ASave);
        btn4AGet       = view.findViewById(R.id.btn4AGet);

        // 恢复已保存的4A Token
        Session s4a = Session.get();
        if (et4AToken != null && s4a.tower4aToken != null && !s4a.tower4aToken.isEmpty()) {
            et4AToken.setText(s4a.tower4aToken);
        }
        if (et4ACountyCode != null && s4a.tower4aCountyCode != null && !s4a.tower4aCountyCode.isEmpty()) {
            et4ACountyCode.setText(s4a.tower4aCountyCode);
        }

        // 保存4A Token 按钮
        if (btn4ASave != null) {
            btn4ASave.setOnClickListener(v -> {
                String tokenInput = et4AToken != null ? et4AToken.getText().toString().trim() : "";
                String countyInput = et4ACountyCode != null ? et4ACountyCode.getText().toString().trim() : "";
                // 自动去掉 "Bearer " 前缀（如果用户粘贴的是完整 Authorization 头值）
                if (tokenInput.toLowerCase().startsWith("bearer ")) {
                    tokenInput = tokenInput.substring(7).trim();
                }
                Session sv = Session.get();
                sv.tower4aToken = tokenInput;
                sv.tower4aCountyCode = countyInput;
                sv.saveTower4aToken(requireContext());
                Toast.makeText(requireContext(),
                        tokenInput.isEmpty() ? "4A Token 已清空" : "✅ 4A Token 已保存（len=" + tokenInput.length() + "）",
                        Toast.LENGTH_SHORT).show();
                appendLog("4A Token 已保存 len=" + tokenInput.length() + " county=" + countyInput);
            });
        }

        // 从4A获取Token按钮 - 通过soaprequest SSO自动获取Bearer Token
        if (btn4AGet != null) {
            btn4AGet.setOnClickListener(v -> {
                Session s = Session.get();
                String tower4aCookie = s.tower4aSessionCookie;
                if (tower4aCookie == null || tower4aCookie.isEmpty()) {
                    Toast.makeText(requireContext(), "请先完成4A账号登录", Toast.LENGTH_SHORT).show();
                    return;
                }
                String ssoPwd = et4ASsoPwd != null ? et4ASsoPwd.getText().toString().trim() : "";
                if (ssoPwd.isEmpty()) {
                    Toast.makeText(requireContext(), "请先输入 ssoPwd（从浏览器F12抓包获取）", Toast.LENGTH_LONG).show();
                    appendLog("❌ 从4A获取失败：ssoPwd 为空");
                    return;
                }
                // URL decode ssoPwd（因为抓包是URL编码的）
                try {
                    ssoPwd = java.net.URLDecoder.decode(ssoPwd, "UTF-8");
                } catch (Exception ignored) {}
                btn4AGet.setEnabled(false);
                btn4AGet.setText("获取中...");
                appendLog("🔄 从4A获取Bearer Token，ssoPwd len=" + ssoPwd.length() + "...");
                TymjWebViewHelper.fetchBearerToken(requireContext(), tower4aCookie, ssoPwd,
                        new TymjWebViewHelper.Callback() {
                            @Override
                            public void onSuccess(String token) {
                                if (et4AToken != null) et4AToken.setText(token);
                                if (et4ACountyCode != null && et4ACountyCode.getText().toString().isEmpty()) {
                                    et4ACountyCode.setText("330326");
                                }
                                String county = et4ACountyCode != null ? et4ACountyCode.getText().toString().trim() : "";
                                Session.get().tower4aCountyCode = county;
                                Session.get().saveTower4aToken(requireContext());
                                btn4AGet.setEnabled(true);
                                btn4AGet.setText("从4A获取");
                                Toast.makeText(requireContext(),
                                        "✅ Bearer Token 获取成功（len=" + token.length() + "）",
                                        Toast.LENGTH_SHORT).show();
                                appendLog("✅ Bearer Token 获取成功（len=" + token.length() + "），已自动保存");
                            }
                            @Override
                            public void onFail(String reason) {
                                btn4AGet.setEnabled(true);
                                btn4AGet.setText("从4A获取");
                                Toast.makeText(requireContext(), "❌ 获取失败: " + reason, Toast.LENGTH_LONG).show();
                                appendLog("❌ 从4A获取失败: " + reason);
                            }
                        });
            });
        }

        tvLog.setMovementMethod(new ScrollingMovementMethod());

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AccessControlAdapter();
        recyclerView.setAdapter(adapter);

        // ★ 长按条目 → 手动远程开门确认框
        adapter.setOnItemLongClickListener((pos, item) -> {
            String doorId = item.getDoorId();
            String objId  = item.getObjId();
            String name   = item.getStName();

            if (doorId == null || doorId.isEmpty()) {
                android.widget.Toast.makeText(requireContext(),
                        "该站点无门禁设备ID，无法开门", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("手动远程开门")
                    .setMessage("站点：" + name + "\n\n确认要执行远程开门指令？")
                    .setPositiveButton("确认开门", (dialog, which) -> {
                        appendLog("🔓 手动开门: " + name);
                        ThreadManager.execute(() -> {
                            AccessControlApi.doOpenDoor(objId, doorId);
                            item.setStatus("已开门");
                            item.setRemoteOpenTime("√ 手动开门");
                            ThreadManager.runOnUiThread(() -> {
                                adapter.updateItem(pos, item);
                                appendLog("✓ 手动开门完成: " + name);
                            });
                        });
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        // 恢复已保存的 OMMS Cookie（对应易语言"编辑框1"的内容）
        Session s0 = Session.get();
        if (!s0.ommsCookie.isEmpty()) {
            etOmmsCookie.setText(s0.ommsCookie);
        }

        // 监听输入变化 → 自动提取并保存（对应易语言 提取Cookie() 子程序）
        etOmmsCookie.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(android.text.Editable e) {
                String raw = e.toString().trim();
                if (raw.isEmpty()) {
                    // ★ 输入框被清空（如授权成功后 setText("")），不覆盖已保存的 ommsCookie
                    return;
                }
                // 与易语言逻辑完全对应：找到"Cookie: "前缀就截取后面的值，否则直接用全文
                String extracted = extractCookieValue(raw);
                Session.get().ommsCookie = extracted;
                Session.get().saveOmmsCookie(requireContext());
            }
        });

        btnStart.setOnClickListener(v -> startMonitor());
        btnStop.setOnClickListener(v -> stopMonitor());

        // OMMS 内置浏览器登录：注入4A Cookie，打开WebView，用户只需点运维监控
        btnOmmsLogin.setOnClickListener(v -> {
            Session s = Session.get();
            // ① 如果已有有效 ommsCookie，给用户提示是否强制刷新
            if (s.ommsCookie != null && s.ommsCookie.contains("JSESSIONID")) {
                new android.app.AlertDialog.Builder(requireContext())
                        .setTitle("OMMS 已登录")
                        .setMessage("检测到已有 OMMS 登录凭据（JSESSIONID 存在）。\n\n如果当前功能正常，无需重新登录。\n若遇到「session过期」错误，才需要重新登录。\n\n是否强制重新登录？")
                        .setPositiveButton("强制重新登录", (dialog, which) -> launchOmmsLogin(s))
                        .setNegativeButton("取消（保留现有凭据）", null)
                        .show();
                return;
            }
            // ② 没有有效 Cookie，正常走登录流程
            launchOmmsLogin(s);
        });

        // 初始化4A登录区
        init4ALogin();

        syncButtonState();
    }

    /** 启动 OmmsLoginActivity，无论是否已有 Cookie，强制走一遍登录流程 */
    private void launchOmmsLogin(Session s) {
        if (s.tower4aSessionCookie == null || s.tower4aSessionCookie.isEmpty()) {
            Toast.makeText(requireContext(),
                    "请先在上方完成4A账号登录", Toast.LENGTH_LONG).show();
            return;
        }
        android.content.Intent intent = new android.content.Intent(
                requireContext(), OmmsLoginActivity.class);
        startActivityForResult(intent, REQ_OMMS_LOGIN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OMMS_LOGIN) {
            if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                String cookie = data.getStringExtra(OmmsLoginActivity.EXTRA_COOKIE);
                if (cookie != null && !cookie.isEmpty()) {
                    Session s2 = Session.get();
                    s2.ommsCookie = cookie;
                    s2.saveOmmsCookie(requireContext());   // ★ 持久化，防止重启丢失
                    etOmmsCookie.setHint("✅ OMMS已登录");
                    etOmmsCookie.setText("");
                    appendLog("✅ OMMS 授权成功，Cookie已保存（len=" + cookie.length() + "）");
                    appendLog("   现在可以点击「开启蓝牙进站监控」");
                }
            } else {
                appendLog("ℹ OMMS 登录已取消");
            }
        }
    }

    // ── 启动监控 ──────────────────────────────────────────────────────────

    private void startMonitor() {
        if (isRunning) {
            Toast.makeText(requireContext(), "监控已在运行中", Toast.LENGTH_SHORT).show();
            return;
        }
        Session s = Session.get();
        if (s.userid.isEmpty() || s.token.isEmpty()) {
            Toast.makeText(requireContext(), "请先登录铁塔APP账号", Toast.LENGTH_SHORT).show();
            return;
        }

        // ── 优先使用已有 ommsCookie（手动粘贴或通过「OMMS登录」按钮获取） ──
        String rawInput = etOmmsCookie.getText().toString().trim();
        boolean hasInputCookie = !rawInput.isEmpty()
                && !rawInput.contains("自动") && !rawInput.contains("无需");

        if (hasInputCookie) {
            // 编辑框有内容，提取并保存
            s.ommsCookie = extractCookieValue(rawInput);
            s.saveOmmsCookie(requireContext());
        }

        // ommsCookie 非空才能运行
        if (s.ommsCookie == null || s.ommsCookie.isEmpty()) {
            // ★ 自动尝试获取OMMS Cookie（如果有4A Session的话）
            if (s.tower4aSessionCookie != null && !s.tower4aSessionCookie.isEmpty()) {
                appendLog("🔄 OMMS Cookie 为空，自动尝试获取...");
                ThreadManager.execute(() -> {
                    String loginName = s.username != null && !s.username.isEmpty() ? s.username : "";
                    TowerLoginApi.Result ommsResult = TowerLoginApi.autoGetOmmsCookie(
                            s.tower4aSessionCookie, loginName, requireContext());
                    ThreadManager.runOnUiThread(() -> {
                        if (ommsResult.success) {
                            appendLog("✅ OMMS Cookie 自动获取成功（len=" + s.ommsCookie.length() + "）");
                            etOmmsCookie.setHint("✅ OMMS已自动登录");
                            // 自动开始监控
                            startMonitor();
                        } else {
                            appendLog("❌ OMMS 自动获取失败: " + ommsResult.message);
                            appendLog("   → 请手动点击「OMMS登录」按钮");
                            Toast.makeText(requireContext(),
                                    "OMMS自动获取失败，请点击「OMMS登录」手动完成",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                });
                return;
            }
            Toast.makeText(requireContext(),
                    "请先点击「OMMS登录」完成授权，或手动粘贴OMMS Cookie",
                    Toast.LENGTH_LONG).show();
            return;
        }

        appendLog("▶ 使用 OMMS Cookie 启动监控（len=" + s.ommsCookie.length() + "）");
        isRunning = true;
        syncButtonState();
        adapter.clearData();
        queryPool = Executors.newCachedThreadPool();
        queryPool.execute(this::runMainTask);
    }

    private void stopMonitor() {
        isRunning = false;
        if (queryPool != null && !queryPool.isShutdown()) {
            queryPool.shutdownNow();
        }
        ThreadManager.runOnUiThread(() -> {
            syncButtonState();
            appendLog("⏹ 已停止监控");
        });
    }

    /**
     * 从用户粘贴的原始文本中提取 Cookie 值
     * 对应易语言 提取Cookie() 子程序：
     *   找到 "Cookie: " 的位置，截取其后到换行符之间的内容；
     *   若没有 "Cookie: " 前缀，则直接返回原始文本（用户直接粘贴的是Cookie值本身）。
     *
     * 支持两种粘贴格式：
     *   1. 整个请求头行：  "Cookie: JSESSIONID=xxx; pwdaToken=yyy; ..."
     *   2. 直接Cookie值：  "JSESSIONID=xxx; pwdaToken=yyy; ..."
     */
    private static String extractCookieValue(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        // 查找 "Cookie: "（不区分大小写）
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        int idx = lower.indexOf("cookie: ");
        if (idx >= 0) {
            int start = idx + 8; // 跳过 "cookie: "
            // 找到换行符作为结束（对应易语言 寻找文本(源文本, #换行符, ...)）
            int end = raw.indexOf('\n', start);
            if (end < 0) end = raw.length();
            return raw.substring(start, end).trim().replace("\r", "");
        }
        // 没有 "Cookie: " 前缀 → 用户直接粘贴了 Cookie 值，原样返回
        return raw;
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4A 登录区逻辑（TowerLoginApi，与工单Tab一样的4A门户流程）
    // ══════════════════════════════════════════════════════════════════════

    private void init4ALogin() {
        // 初始化账号下拉框
        if (spinnerAccount4A != null) {
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    AccountConfig.getDisplayNames());
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerAccount4A.setAdapter(spinnerAdapter);
        }

        // 如果已登录（有有效的 tower4aSessionCookie），折叠登录区、显示已登录状态行
        Session s = Session.get();
        String cookie = s.tower4aSessionCookie;
        if (cookie != null && !cookie.isEmpty()) {
            View card = getView() != null ? getView().findViewById(R.id.cardLogin4A) : null;
            if (card != null) card.setVisibility(View.GONE);
            String displayName = s.realname.isEmpty() ? s.username : s.realname;
            showLoggedInBar(displayName.isEmpty() ? cookie.substring(0, Math.min(10, cookie.length())) : displayName);
            return;
        }

        // 绑定按钮事件
        btnGetSmsAc.setOnClickListener(v -> doGetSmsAc());
        btnLoginAc.setOnClickListener(v  -> doConfirmLogin4A());
    }

    /** 显示已登录状态栏，并绑定退出4A登录按钮 */
    private void showLoggedInBar(String username) {
        if (layoutLoggedIn4A != null) {
            layoutLoggedIn4A.setVisibility(View.VISIBLE);
            if (tvLoggedInUser != null) tvLoggedInUser.setText("✅ 已登录: " + username);
        }
        if (btnLogout4A != null) {
            btnLogout4A.setOnClickListener(v -> doLogout4A());
        }
    }

    /** 退出4A登录：仅清空4A Cookie，不影响铁塔APP的 token/userid 等字段 */
    private void doLogout4A() {
        Session s = Session.get();
        // ★ 只清 4A Cookie 和 OMMS Cookie，不清铁塔APP的 token/userid/mobilephone
        // ★ 铁塔APP（工单监控/停电监控）与4A是两套独立系统，退出4A不应波及铁塔APP
        s.tower4aSessionCookie = "";
        s.ommsCookie           = "";
        s.saveTower4aCookie(requireContext());
        s.saveOmmsCookie(requireContext());

        // 隐藏已登录状态栏，重新显示登录面板
        if (layoutLoggedIn4A != null) layoutLoggedIn4A.setVisibility(View.GONE);
        View card = getView() != null ? getView().findViewById(R.id.cardLogin4A) : null;
        if (card != null) card.setVisibility(View.VISIBLE);

        // 清空输入框，重置验证码状态
        if (etPinAc != null) etPinAc.setText("");
        if (layout4aCodeRowAc != null) layout4aCodeRowAc.setVisibility(View.GONE);
        if (tvLoginStatus != null) tvLoginStatus.setText("未登录");
        codeSent4A    = false;
        msgId4A       = null;
        towerLoginApi4A = null; // 重置登录实例，确保下次是全新流程

        // ★ 关键：重新绑定按钮事件（退出前 init4ALogin 已提前 return，按钮未绑定）
        if (btnGetSmsAc != null) btnGetSmsAc.setOnClickListener(v -> doGetSmsAc());
        if (btnLoginAc  != null) btnLoginAc.setOnClickListener(v  -> doConfirmLogin4A());

        Toast.makeText(requireContext(), "已退出4A登录，可重新登录", Toast.LENGTH_SHORT).show();
    }

    /** Step 1：点击「获取验证码」—— 走 TowerLoginApi 流程 */
    private void doGetSmsAc() {
        int accountIdx = (spinnerAccount4A != null) ? spinnerAccount4A.getSelectedItemPosition() : 0;
        if (accountIdx < 0) accountIdx = 0;
        String username = AccountConfig.ACCOUNTS[accountIdx][0];
        String password = AccountConfig.get4aPassword(accountIdx);

        if (password.isEmpty()) {
            if (tvLoginStatus != null) tvLoginStatus.setText("该账号未配置4A密码，请联系管理员");
            return;
        }

        if (tvLoginStatus != null) tvLoginStatus.setText("连接中...");
        btnGetSmsAc.setEnabled(false);
        btnGetSmsAc.setText("发送中...");

        towerLoginApi4A = new TowerLoginApi();
        final String finalUsername = username;
        final String finalPassword = password;

        ThreadManager.execute(() -> {
            TowerLoginApi.Result r1 = towerLoginApi4A.initLogin();
            if (!r1.success) {
                ThreadManager.runOnUiThread(() -> {
                    btnGetSmsAc.setEnabled(true);
                    btnGetSmsAc.setText("获取验证码");
                    if (tvLoginStatus != null) tvLoginStatus.setText("失败");
                    Toast.makeText(requireContext(), "初始化失败: " + r1.message, Toast.LENGTH_LONG).show();
                });
                return;
            }

            TowerLoginApi.Result r2 = towerLoginApi4A.doPrevLogin(finalUsername, finalPassword);
            if (!r2.success) {
                ThreadManager.runOnUiThread(() -> {
                    btnGetSmsAc.setEnabled(true);
                    btnGetSmsAc.setText("获取验证码");
                    if (tvLoginStatus != null) tvLoginStatus.setText("账密错误");
                    Toast.makeText(requireContext(), r2.message, Toast.LENGTH_LONG).show();
                });
                return;
            }

            // 直接登录成功（无需短信）
            if ("direct".equals(r2.data)) {
                String ck = towerLoginApi4A.getSessionCookie();
                Session s = Session.get();
                s.tower4aSessionCookie = ck != null ? ck : "";
                s.username = finalUsername;
                s.realname = AccountConfig.getRealname(
                        spinnerAccount4A != null ? spinnerAccount4A.getSelectedItemPosition() : 0);
                s.selected4AAccountIndex = spinnerAccount4A != null
                        ? spinnerAccount4A.getSelectedItemPosition() : 0;
                s.saveTower4aCookie(requireContext());
                s.saveLogin(requireContext());
                ThreadManager.runOnUiThread(() -> {
                    View card = getView() != null ? getView().findViewById(R.id.cardLogin4A) : null;
                    if (card != null) card.setVisibility(View.GONE);
                    showLoggedInBar(s.realname.isEmpty() ? s.username : s.realname);
                    Toast.makeText(requireContext(), "4A登录成功，正在自动获取OMMS...", Toast.LENGTH_SHORT).show();
                });
                // ★ 4A登录成功后自动获取OMMS Cookie（无需手动点OMMS登录）
                autoFetchOmmsCookie(finalUsername);
                return;
            }

            TowerLoginApi.Result r3 = towerLoginApi4A.refreshMsg(finalUsername, finalPassword);
            if (!r3.success) {
                ThreadManager.runOnUiThread(() -> {
                    btnGetSmsAc.setEnabled(true);
                    btnGetSmsAc.setText("获取验证码");
                    if (tvLoginStatus != null) tvLoginStatus.setText("发码失败");
                    Toast.makeText(requireContext(), r3.message, Toast.LENGTH_LONG).show();
                });
                return;
            }

            msgId4A    = r3.data;
            codeSent4A = true;
            String phoneInfo = r3.message;
            ThreadManager.runOnUiThread(() -> {
                if (layout4aCodeRowAc != null) layout4aCodeRowAc.setVisibility(View.VISIBLE);
                btnGetSmsAc.setEnabled(true);
                btnGetSmsAc.setText("重发验证码");
                if (tvLoginStatus != null) tvLoginStatus.setText("待验证");
                Toast.makeText(requireContext(), "短信已发送: " + phoneInfo, Toast.LENGTH_LONG).show();
            });
        });
    }

    /** Step 2：点击「确认登录」—— 验证短信验证码 */
    private void doConfirmLogin4A() {
        if (!codeSent4A || msgId4A == null) {
            Toast.makeText(requireContext(), "请先点击[获取验证码]", Toast.LENGTH_SHORT).show();
            return;
        }
        String msgCode = etPinAc != null ? etPinAc.getText().toString().trim() : "";
        if (msgCode.isEmpty()) {
            Toast.makeText(requireContext(), "请输入短信验证码", Toast.LENGTH_SHORT).show();
            return;
        }

        int accountIdx = (spinnerAccount4A != null) ? spinnerAccount4A.getSelectedItemPosition() : 0;
        if (accountIdx < 0) accountIdx = 0;
        final int finalIdx     = accountIdx;
        String username = AccountConfig.ACCOUNTS[accountIdx][0];
        String password = AccountConfig.get4aPassword(accountIdx);

        btnLoginAc.setEnabled(false);
        btnLoginAc.setText("验证中...");

        ThreadManager.execute(() -> {
            TowerLoginApi.Result r = towerLoginApi4A.doNextLogin(username, password, msgId4A, msgCode);
            ThreadManager.runOnUiThread(() -> {
                btnLoginAc.setEnabled(true);
                btnLoginAc.setText("确认登录");
                if (r.success) {
                    String ck = towerLoginApi4A.getSessionCookie();
                    Session s = Session.get();
                    s.tower4aSessionCookie = ck != null ? ck : "";
                    s.username = username;
                    s.realname = AccountConfig.getRealname(finalIdx);
                    s.selected4AAccountIndex = finalIdx;
                    // ★ 使用 commit() 强制同步写入，确保 Cookie 立即持久化
                    //   避免用户点击"OMMS登录"时读到空 Cookie（apply 是异步的）
                    android.content.SharedPreferences prefs = requireContext()
                        .getApplicationContext()
                        .getSharedPreferences(Session.PREF_SESSION, android.content.Context.MODE_PRIVATE);
                    prefs.edit()
                        .putString(Session.KEY_TOWER4A_COOKIE, s.tower4aSessionCookie)
                        .putString(Session.KEY_USERNAME, s.username)
                        .putString(Session.KEY_REALNAME, s.realname)
                        .commit();  // 同步写入
                    s.saveLogin(requireContext());  // 保存其他登录信息（token 等）
                    codeSent4A = false;
                    if (layout4aCodeRowAc != null) layout4aCodeRowAc.setVisibility(View.GONE);

                    View card = getView() != null ? getView().findViewById(R.id.cardLogin4A) : null;
                    if (card != null) card.setVisibility(View.GONE);
                    showLoggedInBar(s.realname.isEmpty() ? s.username : s.realname);
                    Toast.makeText(requireContext(), "4A登录成功，正在自动获取OMMS...", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "登录失败: " + r.message, Toast.LENGTH_LONG).show();
                }
            });
            // ★ 4A登录成功后自动获取OMMS Cookie（无需手动点OMMS登录）
            if (r.success) {
                autoFetchOmmsCookie(username);
            }
            return;
        });
    }

    /**
     * ★ 4A登录成功后自动获取OMMS Cookie（后台静默，无需用户操作）
     *
     * 流程：用已保存的4A Session Cookie → POST soaprequest → SSO跳转 → 获取OMMS Cookie
     * 成功：更新 etOmmsCookie 提示 + 自动刷新 ViewState
     * 失败：提示用户手动点OMMS登录
     *
     * @param loginName 4A登录账号（用于 soaprequest 认证）
     */
    private void autoFetchOmmsCookie(String loginName) {
        final android.content.Context ctx = requireContext();
        appendLog("🔄 4A已登录，自动获取OMMS Cookie中...");
        ThreadManager.execute(() -> {
            Session s = Session.get();
            TowerLoginApi.Result ommsResult = TowerLoginApi.autoGetOmmsCookie(
                    s.tower4aSessionCookie, loginName, ctx);

            ThreadManager.runOnUiThread(() -> {
                if (ommsResult.success) {
                    String cookie = Session.get().ommsCookie;
                    boolean hasPwda = cookie != null && cookie.contains("pwdaToken");
                    appendLog("✅ OMMS自动获取" + (hasPwda ? "成功" : "完成") + "（len=" + (cookie != null ? cookie.length() : 0) + "）");
                    etOmmsCookie.setHint("✅ OMMS已自动登录");
                    etOmmsCookie.setText("");
                    if (!hasPwda) {
                        appendLog("⚠ 无pwdaToken，建议手动「OMMS登录」获取完整Cookie");
                    }
                    // 自动刷新ViewState，确保后续开门可用
                    ThreadManager.execute(() -> {
                        String vsResult = AccessControlApi.refreshViewState();
                        ThreadManager.runOnUiThread(() -> {
                            String vs = AccessControlApi.getCachedViewState();
                            appendLog("🔄 ViewState自动刷新: " + vsResult + " → " + vs.substring(0, Math.min(20, vs.length())));
                        });
                    });
                } else {
                    appendLog("❌ OMMS自动获取失败: " + ommsResult.message);
                    appendLog("   → 请手动点击「OMMS登录」完成授权");
                    Toast.makeText(ctx, "OMMS自动获取失败: " + ommsResult.message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void syncButtonState() {
        if (btnStart == null) return;
        btnStart.setEnabled(!isRunning);
        btnStop.setEnabled(isRunning);
        btnStart.setText(isRunning ? "监控中..." : "开启蓝牙进站监控");
    }

    // ══════════════════════════════════════════════════════════════════════
    // 主任务：对应易语言 主程序处理门禁数据()
    // ══════════════════════════════════════════════════════════════════════

    private void runMainTask() {
        Session s = Session.get();

        appendLog("▶ 蓝牙进站实时监控启动...");

        // ① 刷新 OMMS ViewState（使用 OMMS Cookie，支持自动获取和手动粘贴两种方式）
        appendLog("🔄 正在刷新 OMMS ViewState...");
        // Cookie 诊断（UI可见）
        String ck = s.ommsCookie;
        boolean hasPwdaToken = ck.contains("pwdaToken");
        appendLog("   Cookie: len=" + ck.length()
                + " pwdaToken=" + hasPwdaToken
                + " JSESSIONID=" + ck.contains("JSESSIONID")
                + " nodeInfo=" + ck.contains("nodeInformation"));

        // ★ 没有 pwdaToken → 提示用户重新点「OMMS登录」，不再走 soaprequest/SSO
        if (!hasPwdaToken) {
            appendLog("❌ OMMS Cookie 无效（缺少 pwdaToken）");
            appendLog("   → 请点击「OMMS登录」按钮重新授权");
            ThreadManager.runOnUiThread(() -> {
                isRunning = false;
                syncButtonState();
                Toast.makeText(requireContext(),
                        "OMMS Cookie 已过期，请点击「OMMS登录」重新授权",
                        Toast.LENGTH_LONG).show();
            });
            return;
        }
        String vsResult = AccessControlApi.refreshViewState();
        String vs = AccessControlApi.getCachedViewState();
        if ("OK".equals(vsResult)) {
            appendLog("✓ ViewState(OMMS): " + vs.substring(0, Math.min(30, vs.length())));
        } else if ("OK_4A".equals(vsResult)) {
            appendLog("✓ ViewState(4ASSO): " + vs.substring(0, Math.min(30, vs.length())));
        } else if ("LOGIN_REDIRECT".equals(vsResult)) {
            appendLog("⚠ OMMS会话全部失效！4A和OMMS Cookie均无效，请重新登录4A后再启动");
        } else if ("EMPTY_RESPONSE".equals(vsResult)) {
            appendLog("⚠ OMMS响应为空（网络超时）");
        } else if ("NOT_FOUND".equals(vsResult)) {
            appendLog("⚠ ViewState未找到，使用默认值: " + vs + "  (将继续尝试)");
        } else if (vsResult != null && vsResult.startsWith("ERROR:")) {
            appendLog("⚠ ViewState异常: " + vsResult);
        }

        // ② 第一步：先获取门禁告警数据
        appendLog("📡 正在获取门禁告警数据...");
        if (s.shuyunPcToken == null || s.shuyunPcToken.isEmpty()) {
            appendLog("⚠ 数运PC端未登录，蓝牙记录将为空（请先在「数运工单」Tab登录PC端）");
        }
        final String[] results = new String[1];
        java.util.concurrent.Future<?> future = ThreadManager.submit(() ->
            results[0] = AccessControlApi.getAlarmList(s.userid, s.token)
        );
        try { future.get(15, java.util.concurrent.TimeUnit.SECONDS); }
        catch (Exception e) { Thread.currentThread().interrupt(); }

        if (!isRunning) return;

        String alarmJson = results[0] == null ? "" : results[0];

        // ② 第二步：从告警数据中提取所有运维站名，逐一查询数运蓝牙记录，精确匹配
        // 核心思路：用运维站名作为 station_name 参数查数运接口，服务端模糊匹配，比本地全量匹配更准确
        appendLog("📡 正在按站名查询数运蓝牙记录...");
        Map<String, String> bluetoothMap = new HashMap<>(); // alarmStName -> bluetoothTime
        String lanyaJson = ""; // 保留兼容，后面解析用全量兜底

        try {
            // 先从告警JSON里提取站名列表
            List<String> alarmStNames = new ArrayList<>();
            JSONObject tmpAlarm = new JSONObject(alarmJson.isEmpty() ? "{}" : alarmJson);
            JSONArray tmpList = null;
            JSONObject tmpData = tmpAlarm.optJSONObject("data");
            if (tmpData != null) {
                tmpList = tmpData.optJSONArray("rows");
                if (tmpList == null) tmpList = tmpData.optJSONArray("alarmList");
                if (tmpList == null) tmpList = tmpData.optJSONArray("list");
            }
            if (tmpList == null) tmpList = tmpAlarm.optJSONArray("alarmList");
            if (tmpList == null) tmpList = tmpAlarm.optJSONArray("rows");

            if (tmpList != null) {
                for (int i = 0; i < tmpList.length(); i++) {
                    String sn = tmpList.getJSONObject(i).optString("st_name", "").trim();
                    if (!sn.isEmpty() && !alarmStNames.contains(sn)) {
                        alarmStNames.add(sn);
                    }
                }
            }
            appendLog("📋 运维站名列表: " + alarmStNames.size() + " 个站");

            if (!alarmStNames.isEmpty() && !s.shuyunPcToken.isEmpty()) {
                // 逐一用运维站名查询数运，并发最多5个
                java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(5);
                java.util.concurrent.ConcurrentHashMap<String, String> concurrentMap =
                        new java.util.concurrent.ConcurrentHashMap<>();
                List<java.util.concurrent.Future<?>> futures = new ArrayList<>();

                for (String stName : alarmStNames) {
                    futures.add(pool.submit(() -> {
                        try {
                            String resp = AccessControlApi.getLanyaInfo(stName);
                            if (resp.isEmpty()) return;
                            JSONObject obj = new JSONObject(resp);
                            JSONArray arr = null;
                            JSONObject d = obj.optJSONObject("data");
                            if (d != null) {
                                arr = d.optJSONArray("rows");
                                if (arr == null) arr = d.optJSONArray("list");
                                if (arr == null) arr = d.optJSONArray("records");
                            }
                            if (arr == null) arr = obj.optJSONArray("data");
                            if (arr == null || arr.length() == 0) return;

                            // 取最新的一条时间
                            String bestTime = "";
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject row = arr.getJSONObject(i);
                                String[] timeKeys = {"come_in_time", "comeInTime", "in_time", "inTime",
                                                     "come_out_time", "comeOutTime", "outTime"};
                                for (String key : timeKeys) {
                                    String val = row.optString(key, "").trim();
                                    if (!val.isEmpty() && !val.equals("null")) {
                                        if (bestTime.isEmpty() || val.compareTo(bestTime) > 0) {
                                            bestTime = val;
                                        }
                                        break;
                                    }
                                }
                            }
                            if (!bestTime.isEmpty()) {
                                concurrentMap.put(stName, bestTime);
                                appendLog("  ✓ 蓝牙匹配: 「" + stName + "」→ " + bestTime);
                            } else {
                                appendLog("  ✗ 无时间字段: 「" + stName + "」");
                            }
                        } catch (Exception ex) {
                            appendLog("  ⚠ 查询失败: 「" + stName + "」" + ex.getMessage());
                        }
                    }));
                }
                // 等待所有查询完成，最多30秒
                pool.shutdown();
                try { pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
                bluetoothMap.putAll(concurrentMap);
                appendLog("📋 蓝牙记录汇总: " + bluetoothMap.size() + "/" + alarmStNames.size() + " 站匹配成功");
            } else if (s.shuyunPcToken.isEmpty()) {
                appendLog("⚠ 数运未登录，跳过蓝牙查询");
            } else {
                appendLog("⚠ 告警列表为空，跳过蓝牙查询");
            }
        } catch (Exception ex) {
            appendLog("⚠ 按站名查询蓝牙失败，将使用全量模式兜底: " + ex.getMessage());
            // 兜底：全量拉取
            lanyaJson = AccessControlApi.getLanyaInfo("");
        }

        // ③ 解析数据
        List<AccessControlItem> itemList = new ArrayList<>();
        // bluetoothMap 已在上面的"按站名查询"阶段填充（key=运维站名，value=蓝牙进站时间）
        // 如果走了全量兜底（lanyaJson非空），则在此补充解析
        if (!lanyaJson.isEmpty()) {
            try {
                JSONObject lanyaObj = new JSONObject(lanyaJson);
                JSONArray rows = null;
                JSONObject dataObj = lanyaObj.optJSONObject("data");
                if (dataObj != null) {
                    rows = dataObj.optJSONArray("rows");
                    if (rows == null) rows = dataObj.optJSONArray("list");
                    if (rows == null) rows = dataObj.optJSONArray("records");
                }
                if (rows == null && lanyaObj.optJSONArray("data") != null) {
                    rows = lanyaObj.optJSONArray("data");
                }
                if (rows != null && rows.length() > 0) {
                    appendLog("🔍 兜底全量第一条字段: " + rows.getJSONObject(0).toString()
                            .substring(0, Math.min(300, rows.getJSONObject(0).toString().length())));
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.getJSONObject(i);
                        String[] stNameKeys = {"station_name", "stationName", "siteName", "site_name",
                                               "name", "sname", "st_name", "stName"};
                        String stName = "";
                        for (String key : stNameKeys) {
                            String val = row.optString(key, "").trim();
                            if (!val.isEmpty() && !val.equals("null")) { stName = val; break; }
                        }
                        String[] timeKeys = {"come_in_time", "comeInTime", "in_time", "inTime",
                                             "come_out_time", "comeOutTime", "outTime"};
                        String btTime = "";
                        for (String key : timeKeys) {
                            String val = row.optString(key, "").trim();
                            if (!val.isEmpty() && !val.equals("null")) { btTime = val; break; }
                        }
                        if (stName.isEmpty()) continue;
                        String existing = bluetoothMap.get(stName);
                        if (existing == null || btTime.compareTo(existing) > 0) {
                            bluetoothMap.put(stName, btTime);
                        }
                    }
                    appendLog("📋 兜底补充后蓝牙Map: " + bluetoothMap.size() + " 站");
                }
            } catch (Exception e) {
                appendLog("⚠ 兜底解析失败: " + e.getMessage());
            }
        }

        try {
            // 解析告警数据
            // 铁塔APP FSU_ALARM_LIST 接口返回结构：
            //   { "data": { "rows": [...], "total": N }, "success": true }
            // 每条告警字段：
            //   st_name, objid(=fsuid), entrance_guard_id(=doorId),
            //   alarm_begin_time(告警开始时间), firstsystemtime(首次系统时间)
            JSONObject alarmObj = new JSONObject(alarmJson);
            // 兼容 data.rows / data.alarmList / alarmList / rows 四种结构
            JSONArray alarmList = null;
            JSONObject dataObj2 = alarmObj.optJSONObject("data");
            if (dataObj2 != null) {
                alarmList = dataObj2.optJSONArray("rows");
                if (alarmList == null) alarmList = dataObj2.optJSONArray("alarmList");
                if (alarmList == null) alarmList = dataObj2.optJSONArray("list");
            }
            if (alarmList == null) alarmList = alarmObj.optJSONArray("alarmList");
            if (alarmList == null) alarmList = alarmObj.optJSONArray("rows");

            if (alarmList == null || alarmList.length() == 0) {
                appendLog("ℹ 当前无门禁告警数据");
                appendLog("  原始响应: " + alarmJson.substring(0, Math.min(300, alarmJson.length())));
                // 无告警也循环，等待60-120秒后再查
                scheduleNextRound();
                return;
            }
            appendLog("🚨 门禁告警: " + alarmList.length() + " 条，开始分析...");
            // 打印第一条所有字段帮助确认
            if (alarmList.length() > 0) {
                JSONObject first = alarmList.getJSONObject(0);
                appendLog("🔍 告警第一条字段: " + first.toString().substring(0, Math.min(300, first.toString().length())));
            }
            // 打印前5条运维站名，与蓝牙站名对比
            appendLog("── 运维站名样本 ──");
            for (int i = 0; i < Math.min(5, alarmList.length()); i++) {
                appendLog("  运维站[" + (i+1) + "]: 「" + alarmList.getJSONObject(i).optString("st_name","") + "」");
            }
            appendLog("──────────────");

            for (int i = 0; i < alarmList.length(); i++) {
                JSONObject alarm = alarmList.getJSONObject(i);
                AccessControlItem item = new AccessControlItem();
                item.setIndex(i + 1);
                item.setStCode(alarm.optString("st_code", ""));
                item.setStName(alarm.optString("st_name", ""));
                // 告警时间：优先 alarm_begin_time，再 firstsystemtime
                String aTime = alarm.optString("alarm_begin_time",
                               alarm.optString("firstsystemtime",
                               alarm.optString("beginTime", "")));
                item.setAlarmTime(aTime);
                item.setAlarmCause(alarm.optString("cause", alarm.optString("alarmname", "")));
                item.setObjId(alarm.optString("objid", alarm.optString("fsu_objid", "")));
                // ★ 门禁ID：entrance_guard_id（不是subobjid！）
                String doorId = alarm.optString("entrance_guard_id",
                                alarm.optString("door_id",
                                alarm.optString("subobjid", "")));
                item.setDoorId(doorId);

                // 匹配蓝牙记录
                // 策略：精确匹配 → 包含匹配 → 关键词相似匹配（去除"站"/"基站"/"铁塔"等后缀）
                String alarmStName = item.getStName().trim();
                String btTime = bluetoothMap.getOrDefault(alarmStName, null);
                if (btTime == null) {
                    // 模糊匹配：蓝牙站名包含告警站名，或告警站名包含蓝牙站名
                    for (Map.Entry<String, String> entry : bluetoothMap.entrySet()) {
                        String btKey = entry.getKey();
                        if (!btKey.isEmpty() && (btKey.contains(alarmStName) || alarmStName.contains(btKey))) {
                            btTime = entry.getValue();
                            break;
                        }
                    }
                }
                if (btTime == null) {
                    // 深度模糊：去除常见后缀后再比较（"基站"/"站"/"铁塔"）
                    String normAlarm = alarmStName.replaceAll("基站|铁塔|站$", "").trim();
                    for (Map.Entry<String, String> entry : bluetoothMap.entrySet()) {
                        String normBt = entry.getKey().replaceAll("基站|铁塔|站$", "").trim();
                        if (!normBt.isEmpty() && !normAlarm.isEmpty()
                                && (normBt.contains(normAlarm) || normAlarm.contains(normBt))) {
                            btTime = entry.getValue();
                            break;
                        }
                    }
                }
                if (btTime == null) btTime = "无蓝牙记录";
                item.setBluetoothOutTime(btTime);
                // 调试：打印匹配结果
                if ("无蓝牙记录".equals(btTime)) {
                    appendLog("  ✗ 未匹配: 「" + alarmStName + "」");
                } else {
                    appendLog("  ✓ 匹配: 「" + alarmStName + "」→ " + btTime);
                }

                // 计算蓝牙间隔
                if (!"无蓝牙记录".equals(btTime) && !btTime.isEmpty() && item.getAlarmTime().length() > 10) {
                    int btInterval = AccessControlApi.minutesBetween(item.getAlarmTime(), btTime);
                    item.setBluetoothInterval(String.valueOf(btInterval));
                }

                // 计算距今时间
                if (item.getAlarmTime().length() > 10) {
                    int curr = AccessControlApi.minutesFromNow(item.getAlarmTime());
                    item.setCurrentInterval(curr);
                }

                item.setStatus("查询中...");
                item.setRemoteOpenTime("查询中...");
                itemList.add(item);
            }
        } catch (Exception e) {
            appendLog("⚠ 解析告警数据失败: " + e.getMessage());
            scheduleNextRound();
            return;
        }

        if (itemList.isEmpty()) {
            appendLog("ℹ 无有效告警数据");
            scheduleNextRound();
            return;
        }

        // 按告警时间降序预排序（最新告警排最前，等阶段一完成后按距今分钟数精确排序）
        itemList.sort((a, b) -> {
            String ta = a.getAlarmTime() == null ? "" : a.getAlarmTime();
            String tb = b.getAlarmTime() == null ? "" : b.getAlarmTime();
            return tb.compareTo(ta);
        });
        // 重新设置序号
        for (int i = 0; i < itemList.size(); i++) {
            itemList.get(i).setIndex(i + 1);
        }

        // ④ 更新 UI（显示初始数据）
        final List<AccessControlItem> finalList = new ArrayList<>(itemList);
        ThreadManager.runOnUiThread(() -> {
            adapter.setData(finalList);
            appendLog("📊 列表已更新，开始并发查询远程开门时间...");
        });

        // ════════════════════════════════════════════════════════════════
        // 阶段一：并发查询远程开门时间（最多20线程）
        // ════════════════════════════════════════════════════════════════
        AtomicInteger finishedCount = new AtomicInteger(0);
        int total = itemList.size();

        for (int i = 0; i < itemList.size(); i++) {
            if (!isRunning) break;
            final int idx = i;
            final AccessControlItem item = itemList.get(i);

            queryPool.execute(() -> {
                try {
                    querySemaphore.acquire(); // 最多20并发
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    finishedCount.incrementAndGet();
                    return;
                }

                try {
                    // 仿生随机延迟 10-1500ms
                    Thread.sleep(10 + rnd.nextInt(1490));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                if (!isRunning) {
                    querySemaphore.release();
                    finishedCount.incrementAndGet();
                    return;
                }

                // ★ 三重重试查询远程开门时间
                String remoteTime = "";
                for (int retry = 0; retry < 3 && isRunning; retry++) {
                    remoteTime = AccessControlApi.getRemoteOpenTime(item.getObjId());
                    remoteTime = remoteTime.trim();
                    if (!remoteTime.isEmpty() && !remoteTime.contains("null")) break;
                    // 临时限流，等待随机时间
                    try { Thread.sleep(800 + rnd.nextInt(700)); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                }

                if (remoteTime.isEmpty() || remoteTime.contains("null")) remoteTime = "无记录";

                // 计算远程间隔
                int remoteInterval = -9999;
                if (remoteTime.length() > 10 && remoteTime.contains("-") && item.getAlarmTime().length() > 10) {
                    remoteInterval = AccessControlApi.minutesBetween(remoteTime, item.getAlarmTime());
                }

                item.setRemoteOpenTime(remoteTime);
                item.setRemoteInterval(String.valueOf(remoteInterval));
                item.setCurrentInterval(AccessControlApi.minutesFromNow(item.getAlarmTime()));

                // 判断状态
                boolean autoOpen = cbAutoOpen != null && cbAutoOpen.isChecked();
                int curr = item.getCurrentInterval();
                int randThreshold = 5 + rnd.nextInt(10); // 5-15分钟随机阈值

                // 蓝牙间隔判断：-9999 表示无蓝牙记录，直接跳过蓝牙条件
                int btInterval = -9999;
                try {
                    String btStr = item.getBluetoothInterval();
                    if (btStr != null && !btStr.isEmpty()) btInterval = Integer.parseInt(btStr);
                } catch (NumberFormatException ignored) {}

                boolean shouldOpen = autoOpen
                        && curr >= randThreshold
                        && curr < 35
                        && Math.abs(remoteInterval) >= 30
                        && !item.getDoorId().isEmpty()   // ★ doorId 必须非空才能开门
                        && (btInterval == -9999 || Math.abs(btInterval) >= 30);

                String status;
                if (shouldOpen) {
                    status = "待开门";
                } else if (item.isBluetoothValid() || Math.abs(remoteInterval) < 30) {
                    status = "合格";
                } else {
                    status = "不合格";
                }
                item.setStatus(status);

                final int finalIdx = idx;
                final AccessControlItem finalItem = item;
                final String finalStatus = status;
                ThreadManager.runOnUiThread(() -> {
                    adapter.updateItem(finalIdx, finalItem);
                    if ("待开门".equals(finalStatus)) {
                        appendLog("⏳ [" + finalItem.getStName() + "] 加入开门队列");
                    }
                });

                querySemaphore.release();
                finishedCount.incrementAndGet();
            });
        }

        // 等待阶段一全部完成（超时 150 秒）
        long deadline = System.currentTimeMillis() + 150_000L;
        while (finishedCount.get() < total && isRunning && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        if (!isRunning) return;

        // 按距今时间升序排序（距现在最近的排最前，即告警时间最新的排最前）
        Collections.sort(itemList, (a, b) -> {
            int ia = a.getCurrentInterval() < 0 ? Integer.MAX_VALUE : a.getCurrentInterval();
            int ib = b.getCurrentInterval() < 0 ? Integer.MAX_VALUE : b.getCurrentInterval();
            if (ia != ib) return Integer.compare(ia, ib); // 距今分钟数小的排前
            // 相同时用告警时间字符串降序兜底
            String ta = a.getAlarmTime() == null ? "" : a.getAlarmTime();
            String tb = b.getAlarmTime() == null ? "" : b.getAlarmTime();
            return tb.compareTo(ta);
        });
        // 重新编号
        for (int i = 0; i < itemList.size(); i++) itemList.get(i).setIndex(i + 1);
        ThreadManager.runOnUiThread(() -> adapter.setData(new ArrayList<>(itemList)));

        appendLog("✅ 阶段一查询完成，共 " + finishedCount.get() + "/" + total + " 条");

        // ════════════════════════════════════════════════════════════════
        // 阶段二：单行道排队开门（绝对安全，每次开一个）
        // ════════════════════════════════════════════════════════════════
        runOpenDoorQueue(itemList);

        if (!isRunning) return;

        // ════════════════════════════════════════════════════════════════
        // 本轮完成后等待 60-120 秒再循环（仿生随机，避免被封）
        // ════════════════════════════════════════════════════════════════
        int waitSec = 60 + rnd.nextInt(61); // 60~120 秒随机
        String nextTime = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date(System.currentTimeMillis() + waitSec * 1000L));
        ThreadManager.runOnUiThread(() -> appendLog("⏳ 本轮完成，" + waitSec + "秒后下次刷新（" + nextTime + "）"));

        // 分段等待，允许中途 stopMonitor 打断
        for (int i = 0; i < waitSec; i++) {
            if (!isRunning) return;
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }

        if (!isRunning) return;

        // 重新获取 OMMS ViewState，保持会话活跃
        AccessControlApi.refreshViewState();

        // 循环继续下一轮
        ThreadManager.runOnUiThread(() -> appendLog("🔄 开始新一轮监控..."));
        runMainTask();
    }

    // ── 阶段二：排队开门 ──────────────────────────────────────────────────

    private void runOpenDoorQueue(List<AccessControlItem> itemList) {
        int openCount = 0;
        for (int i = 0; i < itemList.size(); i++) {
            if (!isRunning) break;
            AccessControlItem item = itemList.get(i);
            if (!"待开门".equals(item.getStatus())) continue;

            final int finalI = i;
            final AccessControlItem finalItem = item;

            // 模拟人工审核延迟
            ThreadManager.runOnUiThread(() -> {
                finalItem.setRemoteOpenTime("● 模拟人工审核...");
                adapter.updateItem(finalI, finalItem);
            });
            try { Thread.sleep(2500 + rnd.nextInt(2000)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (!isRunning) break;

            ThreadManager.runOnUiThread(() -> {
                finalItem.setRemoteOpenTime("● 执行开门指令...");
                adapter.updateItem(finalI, finalItem);
                appendLog("🔓 正在开门: " + finalItem.getStName());
            });

            // 发送开门指令
            // ★ 第一参数：objId（FSU对象ID = fsuid）
            // ★ 第二参数：doorId（entrance_guard_id，门禁设备ID）
            AccessControlApi.doOpenDoor(item.getObjId(), item.getDoorId());

            // 更新为已开门
            item.setStatus("已开门");
            item.setRemoteOpenTime("√ 已下发开门指令");
            item.setRemoteInterval("0");
            final AccessControlItem doneItem = item;
            final int doneIdx = i;
            ThreadManager.runOnUiThread(() -> {
                adapter.updateItem(doneIdx, doneItem);
                appendLog("✓ 开门成功: " + doneItem.getStName());
            });

            openCount++;

            // ★ 终极防封冷却（最后一条不用等）
            if (i < itemList.size() - 1) {
                ThreadManager.runOnUiThread(() -> {
                    finalItem.setRemoteOpenTime("★ 系统冷却中...");
                    adapter.updateItem(finalI, finalItem);
                });
                try { Thread.sleep(4000 + rnd.nextInt(3000)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                ThreadManager.runOnUiThread(() -> {
                    finalItem.setRemoteOpenTime("√ 已下发开门指令");
                    adapter.updateItem(finalI, finalItem);
                });
            }
        }
        final int finalOpenCount = openCount;
        ThreadManager.runOnUiThread(() -> appendLog("🏁 阶段二完成，共开门 " + finalOpenCount + " 个站点"));
    }

    // ── 收尾（只打日志，不停止循环）──────────────────────────────────────

    private void finishMonitor() {
        // ★ 不再设 isRunning=false，循环由 stopMonitor 或异常打断
        String time = new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        appendLog("✅ 本轮监控完成 " + time);
    }

    /**
     * 等待 60-120 秒后在子线程重新执行 runMainTask（循环入口）
     * 用于"无告警/解析失败"等早退场景的循环
     */
    private void scheduleNextRound() {
        if (!isRunning) return;
        int waitSec = 60 + rnd.nextInt(61);
        String nextTime = new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(new java.util.Date(System.currentTimeMillis() + waitSec * 1000L));
        ThreadManager.runOnUiThread(() -> appendLog("⏳ " + waitSec + "秒后下次刷新（" + nextTime + "）"));
        queryPool.execute(() -> {
            for (int i = 0; i < waitSec; i++) {
                if (!isRunning) return;
                try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
            if (!isRunning) return;
            ThreadManager.runOnUiThread(() -> appendLog("🔄 开始新一轮监控..."));
            runMainTask();
        });
    }

    // ── 日志追加 ──────────────────────────────────────────────────────────

    private void appendLog(String msg) {
        if (tvLog == null) return;
        ThreadManager.runOnUiThread(() -> {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String current = tvLog.getText().toString();
            // 最多保留 80 行
            String[] lines = current.split("\n");
            if (lines.length > 80) {
                StringBuilder sb = new StringBuilder();
                for (int i = lines.length - 60; i < lines.length; i++) sb.append(lines[i]).append("\n");
                current = sb.toString();
            }
            tvLog.setText(current + "[" + time + "] " + msg + "\n");
            // 自动滚到底部
            int scrollY = tvLog.getLayout() != null
                    ? tvLog.getLayout().getLineTop(tvLog.getLineCount()) - tvLog.getHeight()
                    : 0;
            if (scrollY > 0) tvLog.scrollTo(0, scrollY);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isRunning = false;
        if (queryPool != null && !queryPool.isShutdown()) queryPool.shutdownNow();
    }
}
