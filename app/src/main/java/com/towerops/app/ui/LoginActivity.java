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
            s.saveLogin(LoginActivity.this);

            tvStatus.setText("登录成功");
            try { tvStatus.setTextColor(getResources().getColor(R.color.success_neu)); } catch (Exception ignored) {}

            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
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
