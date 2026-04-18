package com.towerops.app.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.towerops.app.R;
import com.towerops.app.api.LoginApi;
import com.towerops.app.model.AccountConfig;
import com.towerops.app.model.Session;
import com.towerops.app.util.CookieStore;
import com.towerops.app.util.HttpUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    // ── 共用控件 ──
    private Spinner   spinnerAccount;
    private ImageView ivCaptcha;
    private FrameLayout flCaptcha;
    private Button    btnRefreshCaptcha;
    private EditText  etVerifyCode;
    private Button    btnLogin;
    private TextView  tvStatus;
    private TextView  tvModeHint;

    // ── 模式切换 Tab ──
    private TextView   tabDirect;      // 直接登录 Tab
    private TextView   tabPin;         // PIN码登录 Tab
    private LinearLayout layoutPinMode; // PIN码区域（包含 etPin + btnGetSms）

    // ── PIN码模式专用控件 ──
    private EditText  etPin;
    private Button    btnGetSms;

    private static final String CAPTCHA_URL =
            "http://ywapp.chinatowercom.cn:58090/itower/mobile/app/verifyImg";

    /** true = 图形码直接登录（默认）；false = PIN码登录（原有） */
    private boolean isDirectMode = true;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private String cookie = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 隐藏标题栏
        try {
            if (getSupportActionBar() != null) getSupportActionBar().hide();
        } catch (Exception ignored) {}

        // 已登录则直接进主界面
        Session session = Session.get();
        session.loadConfig(this);
        if (session.token != null && !session.token.isEmpty()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        // 绑定视图
        spinnerAccount    = findViewById(R.id.spinnerAccount);
        ivCaptcha         = findViewById(R.id.ivCaptcha);
        flCaptcha         = findViewById(R.id.flCaptcha);
        btnRefreshCaptcha = findViewById(R.id.btnRefreshCaptcha);
        etVerifyCode      = findViewById(R.id.etVerifyCode);
        btnLogin          = findViewById(R.id.btnLogin);
        tvStatus          = findViewById(R.id.tvStatus);
        tvModeHint        = findViewById(R.id.tvModeHint);
        tabDirect         = findViewById(R.id.tabDirect);
        tabPin            = findViewById(R.id.tabPin);
        layoutPinMode     = findViewById(R.id.layoutPinMode);
        etPin             = findViewById(R.id.etPin);
        btnGetSms         = findViewById(R.id.btnGetSms);

        // 初始化账号 Spinner
        String[] names = new String[AccountConfig.ACCOUNTS.length];
        for (int i = 0; i < AccountConfig.ACCOUNTS.length; i++) {
            names[i] = AccountConfig.getRealname(i) + "（" + AccountConfig.ACCOUNTS[i][0] + "）";
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAccount.setAdapter(adapter);

        // 默认进入直接登录模式
        switchMode(true);

        // 模式切换
        tabDirect.setOnClickListener(v -> switchMode(true));
        tabPin.setOnClickListener(v -> switchMode(false));

        // 通用按钮
        btnRefreshCaptcha.setOnClickListener(v -> loadCaptcha());
        flCaptcha.setOnClickListener(v -> loadCaptcha());
        btnLogin.setOnClickListener(v -> doLogin());
        btnGetSms.setOnClickListener(v -> doGetSms());

        // 加载验证码
        loadCaptcha();
    }

    /** 切换登录模式 */
    private void switchMode(boolean direct) {
        isDirectMode = direct;
        if (direct) {
            // 直接登录模式：高亮左Tab，隐藏PIN区域
            tabDirect.setBackground(getResources().getDrawable(R.drawable.bg_button_primary_gradient));
            tabDirect.setTextColor(0xFFFFFFFF);
            tabPin.setBackgroundColor(0x00000000);
            tabPin.setTextColor(getResources().getColor(R.color.text_secondary));
            layoutPinMode.setVisibility(View.GONE);
            tvModeHint.setText("当前：图形验证码直接登录（无需手机短信）");
        } else {
            // PIN码登录模式：高亮右Tab，显示PIN区域
            tabPin.setBackground(getResources().getDrawable(R.drawable.bg_button_primary_gradient));
            tabPin.setTextColor(0xFFFFFFFF);
            tabDirect.setBackgroundColor(0x00000000);
            tabDirect.setTextColor(getResources().getColor(R.color.text_secondary));
            layoutPinMode.setVisibility(View.VISIBLE);
            tvModeHint.setText("当前：短信PIN码登录（获取验证码→输入PIN→登录）");
        }
        tvStatus.setText("");
    }

    private void loadCaptcha() {
        loadCaptcha(true);
    }

    private void loadCaptcha(boolean clearStatus) {
        executor.execute(() -> {
            try {
                byte[] bytes = HttpUtil.getBytes(CAPTCHA_URL);
                if (bytes != null && bytes.length > 0) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    cookie = CookieStore.getCookie();
                    runOnUiThread(() -> {
                        ivCaptcha.setImageBitmap(bitmap);
                        if (clearStatus) tvStatus.setText("");
                    });
                } else {
                    runOnUiThread(() -> tvStatus.setText("加载验证码失败"));
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> tvStatus.setText("加载验证码异常: " + e.getMessage()));
            }
        });
    }

    // ── 获取短信（仅 PIN 码模式） ──
    private void doGetSms() {
        int pos = spinnerAccount.getSelectedItemPosition();
        if (pos < 0 || pos >= AccountConfig.ACCOUNTS.length) { tvStatus.setText("请选择账号"); return; }

        String account  = AccountConfig.ACCOUNTS[pos][0];
        String password = AccountConfig.ACCOUNTS[pos][1];
        String vcode    = etVerifyCode.getText().toString().trim();

        if (vcode.isEmpty()) { tvStatus.setText("请输入图形验证码"); return; }

        tvStatus.setText("发送中...");
        executor.execute(() -> {
            try {
                LoginApi.SmsResult result = LoginApi.sendSmsCode(account, password, vcode, cookie);
                runOnUiThread(() -> {
                    tvStatus.setText(result.message);
                    if (!result.success) loadCaptcha(false);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> tvStatus.setText("发送失败: " + e.getMessage()));
            }
        });
    }

    // ── 登录（根据当前模式分支） ──
    private void doLogin() {
        int pos = spinnerAccount.getSelectedItemPosition();
        if (pos < 0 || pos >= AccountConfig.ACCOUNTS.length) { tvStatus.setText("请选择账号"); return; }

        String account  = AccountConfig.ACCOUNTS[pos][0];
        String password = AccountConfig.ACCOUNTS[pos][1];
        String vcode    = etVerifyCode.getText().toString().trim();

        if (vcode.isEmpty()) { tvStatus.setText("请输入图形验证码"); return; }

        if (!isDirectMode) {
            // PIN码模式还需要 PIN
            String pin = etPin.getText().toString().trim();
            if (pin.isEmpty()) { tvStatus.setText("请输入PIN码"); return; }
            tvStatus.setText("登录中...");
            final String pinFinal = pin;
            executor.execute(() -> {
                try {
                    LoginApi.LoginResult result = LoginApi.loginWithPin(account, password, vcode, pinFinal, cookie);
                    runOnUiThread(() -> handleLoginResult(result, pos));
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> { tvStatus.setText("登录异常: " + e.getMessage()); loadCaptcha(false); });
                }
            });
        } else {
            // 直接登录模式：图形验证码一步完成
            tvStatus.setText("登录中...");
            executor.execute(() -> {
                try {
                    LoginApi.LoginResult result = LoginApi.loginDirect(account, password, vcode, cookie);
                    runOnUiThread(() -> handleLoginResult(result, pos));
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> { tvStatus.setText("登录异常: " + e.getMessage()); loadCaptcha(false); });
                }
            });
        }
    }

    /** 统一处理登录结果（两种模式共用） */
    private void handleLoginResult(LoginApi.LoginResult result, int accountPos) {
        if (result.success) {
            Session s    = Session.get();
            s.token      = result.token;
            s.userid     = result.userid;
            s.mobilephone = result.mobilephone;
            s.username   = result.username;
            s.realname   = AccountConfig.getRealname(accountPos);

            // ★ 自动写入 X-Auth-Token 和 loginAcct（登录响应中已有）
            s.doorApprovalXAuthToken = result.token;
            s.doorApprovalLoginAcct  = result.loginname;

            tvStatus.setText("登录成功，正在获取审批权限...");
            try { tvStatus.setTextColor(getResources().getColor(R.color.success_neu)); } catch (Exception ignored) {}

            // ★ 在 UI 线程执行，等 OBTAIN_TOKEN_NEW 返回 appacctid 后再跳转
            //    确保 DoorApprovalFragment 检查时 acctId 已有值
            executor.execute(() -> {
                android.util.Log.d("LoginActivity", "========== 登录成功，开始保存认证信息 ==========");
                android.util.Log.d("LoginActivity", "result.token:       [" + result.token + "]");
                android.util.Log.d("LoginActivity", "result.userid:      [" + result.userid + "]");
                android.util.Log.d("LoginActivity", "result.loginname:   [" + result.loginname + "]");

                // 1. 写入 Session 字段
                s.doorApprovalXAuthToken = result.token;
                s.doorApprovalLoginAcct  = result.loginname;

                android.util.Log.d("LoginActivity", "Session 写入后 doorApprovalXAuthToken: [" + s.doorApprovalXAuthToken + "]");
                android.util.Log.d("LoginActivity", "Session 写入后 doorApprovalLoginAcct:  [" + s.doorApprovalLoginAcct + "]");

                // 2. 保存登录信息
                s.saveLogin(LoginActivity.this);
                android.util.Log.d("LoginActivity", "saveLogin() 完成");

                // 3. ★★★ 修复（2026-04-18）：先设置初始值，再调用 OBTAIN_TOKEN_NEW
                //    登录返回的 token 作为 Authorization 头发送给 OBTAIN_TOKEN_NEW
                //    OBTAIN_TOKEN_NEW 成功时返回的 token 会更新 doorApprovalXAuthToken
                //    失败时不覆盖，保持原有值
                android.util.Log.d("LoginActivity", "准备调用 OBTAIN_TOKEN_NEW");
                android.util.Log.d("LoginActivity", "  登录返回的 token (用于认证): " + result.token.substring(0, Math.min(30, result.token.length())) + "...");
                android.util.Log.d("LoginActivity", "  userid:   [" + result.userid + "]");
                android.util.Log.d("LoginActivity", "  loginname: [" + result.loginname + "]");

                String appAcctId = LoginApi.obtainAppAcctId(result.userid, result.loginname, result.token, cookie);
                android.util.Log.d("LoginActivity", "obtainAppAcctId 返回: [" + appAcctId + "]");

                // OBTAIN_TOKEN_NEW 失败时返回空字符串，不覆盖 acctId
                if (appAcctId != null && !appAcctId.isEmpty()) {
                    s.doorApprovalAcctId = appAcctId;
                    android.util.Log.d("LoginActivity", "已设置 doorApprovalAcctId = " + appAcctId);
                } else {
                    android.util.Log.w("LoginActivity", "appacctid 获取失败，使用后备值 203349045");
                    s.doorApprovalAcctId = "203349045";  // 后备值
                    android.util.Log.d("LoginActivity", "doorApprovalAcctId (后备值): " + s.doorApprovalAcctId);
                }

                // 验证 doorApprovalXAuthToken 是否已设置
                if (s.doorApprovalXAuthToken == null || s.doorApprovalXAuthToken.isEmpty()) {
                    android.util.Log.w("LoginActivity", "doorApprovalXAuthToken 为空，使用登录返回的 token");
                    s.doorApprovalXAuthToken = result.token;
                }
                android.util.Log.d("LoginActivity", "最终 doorApprovalXAuthToken: " + s.doorApprovalXAuthToken.substring(0, Math.min(30, s.doorApprovalXAuthToken.length())) + "...");

                // 4. 保存门禁审批认证
                android.util.Log.d("LoginActivity", "调用 saveDoorApprovalAuth()，acctId = " + s.doorApprovalAcctId);
                s.saveDoorApprovalAuth(LoginActivity.this);
                android.util.Log.d("LoginActivity", "saveDoorApprovalAuth() 完成");
                android.util.Log.d("LoginActivity", "saveDoorApprovalAuth() 完成");

                // 5. 跳转到主页（必须在主线程）
                runOnUiThread(() -> {
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                });
            });
        } else {
            tvStatus.setText(result.message);
            try { tvStatus.setTextColor(getResources().getColor(R.color.error_neu)); } catch (Exception ignored) {}
            loadCaptcha(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
