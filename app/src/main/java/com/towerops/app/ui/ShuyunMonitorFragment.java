package com.towerops.app.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.towerops.app.R;
import com.towerops.app.api.ShuyunApi;
import com.towerops.app.model.Session;
import com.towerops.app.shuyun.ShuyunAccountConfig;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 数运监控子Fragment - 对应"数运监控"Tab
 */
public class ShuyunMonitorFragment extends Fragment {

    private static final String TAG = "ShuyunMonitorFragment";

    // UI控件
    private TextView tvShuyunStatus, tvPendingCount, tvProcessingCount, tvLog, tvCurrentTime;
    private TextView tvPcLoginStatus, tvAppLoginStatus, tvImei;
    private CheckBox cbAutoAccept, cbAutoRevert;
    private Button btnStartShuyun, btnStopShuyun;
    private Button btnPcLogin, btnAppLogin, btnRefreshCaptcha;
    private TabLayout tabLayoutShuyun;
    private RecyclerView rvPending, rvProcessing;
    private ScrollView svLog;
    private View tvEmpty;

    // 登录控件
    private Spinner spinnerPcAccount, spinnerAppAccount, spinnerCounty;
    private ImageView imgPcCaptcha;
    private EditText etPcCaptcha;
    private View layoutPcCaptcha;

    // 区县代码（用于数运智联工单接单）
    private static final String[] COUNTY_CODES = {"330326", "330329", "330302", "330327", "330328", "330381", "330382", "330303", "330305", "330324", "330383"};
    private static final String[] COUNTY_NAMES = {"平阳", "泰顺", "鹿城", "苍南", "文成", "瑞安", "乐清", "龙湾", "洞头", "永嘉", "龙港"};
    private int selectedCountyIndex = 0;

    // 适配器
    private ShuyunAdapter pendingAdapter;
    private ShuyunAdapter processingAdapter;

    // 主线程Handler
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 当前选中的Tab
    private int currentTab = 0;

    // 时间更新Handler
    private Handler timeUpdateHandler;
    private Runnable timeUpdateRunnable;

    // 监控状态
    private volatile boolean isRunning = false;
    private Thread monitorThread;

    // 登录状态
    private String pcToken = "";
    private String pcIp = "";
    private String appToken = "";
    private String appUserId = "";
    private int selectedPcAccountIndex = 0;
    private int selectedAppAccountIndex = 0;
    private boolean isPcLoggedIn = false;
    private boolean isAppLoggedIn = false;

    // 验证码
    private String currentPcIp = "";
    private Bitmap currentCaptchaBitmap = null;

    // 接口回调
    private ShuyunMonitorCallback callback;

    public interface ShuyunMonitorCallback {
        void onLoginStatusChanged(boolean pcLoggedIn, boolean appLoggedIn);
        void onMonitorStatusChanged(boolean isRunning);
    }

    public void setCallback(ShuyunMonitorCallback callback) {
        this.callback = callback;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public String getPcToken() {
        return pcToken;
    }

    public String getAppToken() {
        return appToken;
    }

    public String getAppUserId() {
        return appUserId;
    }

    public boolean isPcLoggedIn() {
        return isPcLoggedIn;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shuyun_monitor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 恢复登录状态
        Session s = Session.get();
        s.loadConfig(requireContext());

        // 从Session恢复登录状态
        if (!s.shuyunPcToken.isEmpty()) {
            pcToken = s.shuyunPcToken;
            pcIp = s.shuyunPcIp;
            isPcLoggedIn = true;
        }
        if (!s.shuyunAppToken.isEmpty()) {
            appToken = s.shuyunAppToken;
            appUserId = s.shuyunAppUserId;
            isAppLoggedIn = true;
        }

        initViews(view);
        setupListeners();
        setupTimeUpdate();

        // 更新登录状态显示
        updateLoginStatus();

        loadCaptcha();
    }

    private void initViews(View view) {
        tvShuyunStatus = view.findViewById(R.id.tvShuyunStatus);
        tvPendingCount = view.findViewById(R.id.tvPendingCount);
        tvProcessingCount = view.findViewById(R.id.tvProcessingCount);
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime);
        tvPcLoginStatus = view.findViewById(R.id.tvPcLoginStatus);
        tvAppLoginStatus = view.findViewById(R.id.tvAppLoginStatus);
        tvImei = view.findViewById(R.id.tvImei);

        cbAutoAccept = view.findViewById(R.id.cbAutoAccept);
        cbAutoRevert = view.findViewById(R.id.cbAutoRevert);

        btnStartShuyun = view.findViewById(R.id.btnStartShuyun);
        btnStopShuyun = view.findViewById(R.id.btnStopShuyun);
        btnPcLogin = view.findViewById(R.id.btnPcLogin);
        btnAppLogin = view.findViewById(R.id.btnAppLogin);
        btnRefreshCaptcha = view.findViewById(R.id.btnRefreshCaptcha);

        tabLayoutShuyun = view.findViewById(R.id.tabLayoutShuyun);

        rvPending = view.findViewById(R.id.rvPending);
        rvProcessing = view.findViewById(R.id.rvProcessing);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        svLog = view.findViewById(R.id.svLog);

        // 登录控件（PC账号选择器已移除）
        spinnerAppAccount = view.findViewById(R.id.spinnerAppAccount);
        spinnerCounty = view.findViewById(R.id.spinnerCounty);
        imgPcCaptcha = view.findViewById(R.id.imgPcCaptcha);
        etPcCaptcha = view.findViewById(R.id.etPcCaptcha);
        layoutPcCaptcha = view.findViewById(R.id.layoutPcCaptcha);

        // 初始化RecyclerView
        rvPending.setLayoutManager(new LinearLayoutManager(getContext()));
        rvProcessing.setLayoutManager(new LinearLayoutManager(getContext()));

        // 使用默认构造方法
        pendingAdapter = new ShuyunAdapter();
        processingAdapter = new ShuyunAdapter();

        pendingAdapter.setPendingList(true);
        processingAdapter.setPendingList(false);

        rvPending.setAdapter(pendingAdapter);
        rvProcessing.setAdapter(processingAdapter);

        // 设置点击事件
        pendingAdapter.setOnItemClickListener(new ShuyunAdapter.OnItemClickListener() {
            @Override
            public void onAcceptClick(int position, ShuyunApi.ShuyunTaskInfo item) {
                acceptTask(item.id);
            }

            @Override
            public void onRevertClick(int position, ShuyunApi.ShuyunTaskInfo item) {
                revertTask(item.id);
            }
        });

        processingAdapter.setOnItemClickListener(new ShuyunAdapter.OnItemClickListener() {
            @Override
            public void onAcceptClick(int position, ShuyunApi.ShuyunTaskInfo item) {}

            @Override
            public void onRevertClick(int position, ShuyunApi.ShuyunTaskInfo item) {
                revertTask(item.id);
            }
        });

        // 初始化账号选择器
        initAccountSpinners();
    }

    private void initAccountSpinners() {
        // APP账号
        String[] appAccounts = ShuyunAccountConfig.getAPPDisplayNames();
        ArrayAdapter<String> appAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, appAccounts);
        appAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAppAccount.setAdapter(appAdapter);

        // 更新默认IMEI显示
        updateImeiDisplay(0);

        // 初始化区县选择器
        initCountySpinner();
    }

    private void initCountySpinner() {
        // 读取保存的区县选择
        Session s = Session.get();
        ArrayAdapter<String> countyAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, COUNTY_NAMES);
        countyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCounty.setAdapter(countyAdapter);

        // 【修复】从 shuyunCityArea 恢复行政区划选择（不能用 countyManagerCode，那是审核员userId）
        if (s.shuyunCityArea != null) {
            for (int i = 0; i < COUNTY_CODES.length; i++) {
                if (COUNTY_CODES[i].equals(s.shuyunCityArea)) {
                    spinnerCounty.setSelection(i);
                    selectedCountyIndex = i;
                    break;
                }
            }
        }

        // 区县选择事件
        spinnerCounty.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedCountyIndex = position;
                // 【修复】保存行政区划代码到 shuyunCityArea，不覆盖 countyManagerCode（审核员userId）
                Session s = Session.get();
                s.shuyunCityArea = COUNTY_CODES[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    /**
     * 获取当前选择的区县代码
     */
    public String getSelectedCountyCode() {
        return COUNTY_CODES[selectedCountyIndex];
    }

    private void setupListeners() {
        // Tab切换
        tabLayoutShuyun.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                updateTabContent();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // APP账号选择
        spinnerAppAccount.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedAppAccountIndex = position;
                updateImeiDisplay(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // PC登录按钮
        btnPcLogin.setOnClickListener(v -> doPcLogin());

        // APP登录按钮
        btnAppLogin.setOnClickListener(v -> doAppLogin());

        // 刷新验证码
        btnRefreshCaptcha.setOnClickListener(v -> loadCaptcha());

        // 启动/停止监控
        btnStartShuyun.setOnClickListener(v -> startMonitor());
        btnStopShuyun.setOnClickListener(v -> stopMonitor());
    }

    private void updateImeiDisplay(int position) {
        ShuyunAccountConfig.Account account = ShuyunAccountConfig.getAPPAccount(position);
        if (account != null) {
            tvImei.setText("IMEI: " + account.imei.substring(0, Math.min(8, account.imei.length())) + "...");
        }
    }

    private void setupTimeUpdate() {
        timeUpdateHandler = new Handler(Looper.getMainLooper());
        timeUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                tvCurrentTime.setText(sdf.format(new Date()));
                timeUpdateHandler.postDelayed(this, 1000);
            }
        };
        timeUpdateHandler.post(timeUpdateRunnable);
    }

    private void updateTabContent() {
        if (currentTab == 0) {
            rvPending.setVisibility(View.VISIBLE);
            rvProcessing.setVisibility(View.GONE);
        } else {
            rvPending.setVisibility(View.GONE);
            rvProcessing.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 加载验证码图片
     */
    private void loadCaptcha() {
        mainHandler.post(() -> {
            btnRefreshCaptcha.setEnabled(false);
            btnPcLogin.setEnabled(false);
        });

        new Thread(() -> {
            try {
                ShuyunApi.CaptchaResult result = ShuyunApi.getCaptcha();
                currentPcIp = result.ip;
                currentCaptchaBitmap = result.image;

                mainHandler.post(() -> {
                    if (currentCaptchaBitmap != null) {
                        imgPcCaptcha.setImageBitmap(currentCaptchaBitmap);
                        layoutPcCaptcha.setVisibility(View.VISIBLE);
                    } else {
                        Toast.makeText(getContext(), "验证码加载失败", Toast.LENGTH_SHORT).show();
                    }
                    btnRefreshCaptcha.setEnabled(true);
                    btnPcLogin.setEnabled(true);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(getContext(), "验证码加载异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnRefreshCaptcha.setEnabled(true);
                    btnPcLogin.setEnabled(true);
                });
            }
        }).start();
    }

    /**
     * PC登录
     * 【核心】同时获取 Authorization token 和 Cookie 中的 towerNumber-Token
     */
    private void doPcLogin() {
        String imgcode = etPcCaptcha.getText().toString().trim();
        if (imgcode.isEmpty()) {
            Toast.makeText(getContext(), "请输入验证码", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentPcIp.isEmpty()) {
            Toast.makeText(getContext(), "请先获取验证码", Toast.LENGTH_SHORT).show();
            return;
        }

        btnPcLogin.setEnabled(false);
        btnPcLogin.setText("登录中...");

        new Thread(() -> {
            try {
                // 使用第一个PC账号
                ShuyunAccountConfig.Account account = ShuyunAccountConfig.getPCAccount(0);
                if (account == null) {
                    mainHandler.post(() -> {
                        Toast.makeText(getContext(), "未配置PC账号", Toast.LENGTH_SHORT).show();
                        btnPcLogin.setEnabled(true);
                        btnPcLogin.setText("登录");
                    });
                    return;
                }

                // 【核心】使用新的登录方法，同时获取 token 和 setCookie
                ShuyunApi.PcLoginResult loginResult = ShuyunApi.loginByPcWithCookie(account.username, account.password, imgcode, currentPcIp);

                if (loginResult.success) {
                    pcToken = loginResult.token;
                    pcIp = currentPcIp;
                    isPcLoggedIn = true;

                    // 保存到Session并持久化
                    Session s = Session.get();
                    s.shuyunPcToken = loginResult.token;           // Authorization 用的 token
                    s.shuyunPcTokenCookie = loginResult.cookieToken; // Cookie 用的 towerNumber-Token
                    s.shuyunPcIp = pcIp;
                    s.saveShuyunLogin(getContext());

                    System.out.println("[ShuyunMonitorFragment] PC登录成功");
                    System.out.println("[ShuyunMonitorFragment] authToken: " + (loginResult.token.length() > 20 ? loginResult.token.substring(0, 20) + "..." : loginResult.token));
                    System.out.println("[ShuyunMonitorFragment] cookieToken: " + (loginResult.cookieToken.length() > 20 ? loginResult.cookieToken.substring(0, 20) + "..." : loginResult.cookieToken));

                    mainHandler.post(() -> {
                        updateLoginStatus();
                        Toast.makeText(getContext(), "PC登录成功", Toast.LENGTH_SHORT).show();
                        btnPcLogin.setEnabled(true);
                        btnPcLogin.setText("PC登录");

                        if (callback != null) {
                            callback.onLoginStatusChanged(isPcLoggedIn, isAppLoggedIn);
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        Toast.makeText(getContext(), "PC登录失败", Toast.LENGTH_SHORT).show();
                        btnPcLogin.setEnabled(true);
                        btnPcLogin.setText("登录");
                        // 登录失败后刷新验证码
                        loadCaptcha();
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(getContext(), "PC登录异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnPcLogin.setEnabled(true);
                    btnPcLogin.setText("登录");
                });
            }
        }).start();
    }

    /**
     * APP登录
     */
    private void doAppLogin() {
        btnAppLogin.setEnabled(false);
        btnAppLogin.setText("登录中...");

        new Thread(() -> {
            try {
                ShuyunAccountConfig.Account account = ShuyunAccountConfig.getAPPAccount(selectedAppAccountIndex);
                if (account == null) {
                    mainHandler.post(() -> {
                        Toast.makeText(getContext(), "请选择APP账号", Toast.LENGTH_SHORT).show();
                        btnAppLogin.setEnabled(true);
                        btnAppLogin.setText("APP登录");
                    });
                    return;
                }

                String result = ShuyunApi.loginByApp(account.username, account.password, account.imei);
                ShuyunApi.ShuyunLoginResult loginResult = ShuyunApi.parseAppLogin(result);

                if (loginResult.success) {
                    appToken = loginResult.token;
                    appUserId = loginResult.userId;
                    isAppLoggedIn = true;

                    // 保存到Session并持久化
                    Session s = Session.get();
                    s.shuyunAppToken = appToken;
                    s.shuyunAppUserId = appUserId;
                    // 保存当前登录账号的IMEI（用于省级审核权限验证）
                    if (account != null) {
                        s.shuyunAppImei = account.imei;
                    }
                    s.saveShuyunLogin(getContext());

                    mainHandler.post(() -> {
                        updateLoginStatus();
                        Toast.makeText(getContext(), "APP登录成功", Toast.LENGTH_SHORT).show();
                        btnAppLogin.setEnabled(true);
                        btnAppLogin.setText("APP登录");

                        if (callback != null) {
                            callback.onLoginStatusChanged(isPcLoggedIn, isAppLoggedIn);
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        Toast.makeText(getContext(), "APP登录失败: " + loginResult.message, Toast.LENGTH_SHORT).show();
                        btnAppLogin.setEnabled(true);
                        btnAppLogin.setText("APP登录");
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(getContext(), "APP登录异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnAppLogin.setEnabled(true);
                    btnAppLogin.setText("APP登录");
                });
            }
        }).start();
    }

    private void updateLoginStatus() {
        tvPcLoginStatus.setText(isPcLoggedIn ? "已登录" : "未登录");
        tvPcLoginStatus.setTextColor(isPcLoggedIn ? 0xFF10B981 : 0xFFEF4444);

        tvAppLoginStatus.setText(isAppLoggedIn ? "已登录" : "未登录");
        tvAppLoginStatus.setTextColor(isAppLoggedIn ? 0xFF10B981 : 0xFFEF4444);
    }

    private void startMonitor() {
        if (getContext() == null) return;

        if (!isAppLoggedIn || appToken.isEmpty()) {
            Toast.makeText(getContext(), "请先登录APP", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isRunning) return;

        isRunning = true;

        // UI更新加空值检查
        if (btnStartShuyun != null) btnStartShuyun.setEnabled(false);
        if (btnStopShuyun != null) btnStopShuyun.setEnabled(true);
        if (tvShuyunStatus != null) {
            tvShuyunStatus.setText("监控运行中");
            tvShuyunStatus.setTextColor(0xFF10B981);
        }

        // 按钮禁用并修改标题
        if (btnStartShuyun != null) {
            btnStartShuyun.setEnabled(false);
            btnStartShuyun.setText("监控中");
        }

        appendLog("监控已启动，已登录APP账号");

        if (callback != null) {
            callback.onMonitorStatusChanged(true);
        }

        // 启动监控线程
        monitorThread = new Thread(() -> {
            while (isRunning && getContext() != null) {
                try {
                    final String statusText = "监控运行中...";
                    mainHandler.post(() -> {
                        if (tvShuyunStatus != null) tvShuyunStatus.setText(statusText);
                    });

                    // 自动接单
                    if (cbAutoAccept != null && cbAutoAccept.isChecked()) {
                        checkAndAutoAccept();
                    }

                    // 自动回单
                    if (cbAutoRevert != null && cbAutoRevert.isChecked()) {
                        checkAndAutoRevert();
                    }

                    // 刷新列表
                    refreshTaskList();

                    // 等待下次轮询 (30-60秒)
                    int sleepTime = (int) (Math.random() * 30000) + 30000;
                    Thread.sleep(sleepTime);

                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    appendLog("监控异常: " + e.getMessage());
                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            }

            mainHandler.post(() -> {
                if (getContext() == null) return;
                isRunning = false;
                if (btnStartShuyun != null) {
                    btnStartShuyun.setEnabled(true);
                    btnStartShuyun.setText("启动监控");
                }
                if (btnStopShuyun != null) btnStopShuyun.setEnabled(false);
                if (tvShuyunStatus != null) {
                    tvShuyunStatus.setText("监控已停止");
                    tvShuyunStatus.setTextColor(0xFF6B7280);
                }
                appendLog("监控已停止");

                if (callback != null) {
                    callback.onMonitorStatusChanged(false);
                }
            });
        });
        monitorThread.start();
    }

    private void stopMonitor() {
        isRunning = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
    }

    private void checkAndAutoAccept() {
        if (getContext() == null) return;
        appendLog("检查待接单...");
        try {
            String jsonStr = ShuyunApi.getTaskList(appToken, appUserId);
            List<ShuyunApi.ShuyunTaskInfo> tasks = ShuyunApi.parseTaskList(jsonStr);

            if (tasks == null || tasks.isEmpty()) {
                appendLog("待接单为空");
                return;
            }

            // 筛选待处理的工单
            int pendingCount = 0;
            for (ShuyunApi.ShuyunTaskInfo task : tasks) {
                if ("待处理".equals(task.status) || "0".equals(task.status)) {
                    pendingCount++;
                }
            }

            appendLog("待处理工单: " + pendingCount);

            if (pendingCount > 0 && pendingCount < 10) {
                // 仿生延迟
                int delay = (int) (Math.random() * 3500) + 2500;
                Thread.sleep(delay);

                // 接单
                for (ShuyunApi.ShuyunTaskInfo task : tasks) {
                    if ("待处理".equals(task.status) || "0".equals(task.status)) {
                        acceptTask(task.id);
                    }
                }
            }
        } catch (Exception e) {
            appendLog("接单异常: " + e.getMessage());
        }
    }

    private void acceptTask(String taskId) {
        try {
            String result = ShuyunApi.acceptTask(appToken, appUserId, taskId);
            if (ShuyunApi.isSuccess(result)) {
                appendLog("✓ 接单成功: " + taskId);
            } else {
                appendLog("✗ 接单失败: " + taskId);
            }
        } catch (Exception e) {
            appendLog("接单异常: " + e.getMessage());
        }
    }

    private void checkAndAutoRevert() {
        if (getContext() == null) return;
        appendLog("检查处理中工单...");
        try {
            String jsonStr = ShuyunApi.getTaskList(appToken, appUserId);
            List<ShuyunApi.ShuyunTaskInfo> tasks = ShuyunApi.parseTaskList(jsonStr);

            if (tasks == null || tasks.isEmpty()) {
                appendLog("处理中为空");
                return;
            }

            // 筛选处理中的工单
            for (ShuyunApi.ShuyunTaskInfo task : tasks) {
                if ("处理中".equals(task.status) || "1".equals(task.status)) {
                    // 简单处理：直接回单
                    revertTask(task.id);
                }
            }
        } catch (Exception e) {
            appendLog("回单异常: " + e.getMessage());
        }
    }

    private void revertTask(String taskId) {
        try {
            String result = ShuyunApi.revertTask(appToken, appUserId, taskId, "自动回单");
            if (ShuyunApi.isSuccess(result)) {
                appendLog("✓ 回单成功: " + taskId);
            } else {
                appendLog("✗ 回单失败: " + taskId);
            }
        } catch (Exception e) {
            appendLog("回单异常: " + e.getMessage());
        }
    }

    private void refreshTaskList() {
        if (getContext() == null) return;
        try {
            String jsonStr = ShuyunApi.getTaskList(appToken, appUserId);
            List<ShuyunApi.ShuyunTaskInfo> tasks = ShuyunApi.parseTaskList(jsonStr);

            // 分离待处理和处理中
            java.util.List<ShuyunApi.ShuyunTaskInfo> pending = new java.util.ArrayList<>();
            java.util.List<ShuyunApi.ShuyunTaskInfo> processing = new java.util.ArrayList<>();

            for (ShuyunApi.ShuyunTaskInfo task : tasks) {
                if ("待处理".equals(task.status) || "0".equals(task.status)) {
                    pending.add(task);
                } else if ("处理中".equals(task.status) || "1".equals(task.status)) {
                    processing.add(task);
                }
            }

            if (pendingAdapter != null) pendingAdapter.setData(pending);
            if (processingAdapter != null) processingAdapter.setData(processing);

            mainHandler.post(() -> {
                if (getContext() == null) return;
                try {
                    if (tvPendingCount != null) tvPendingCount.setText("待处理: " + pending.size());
                    if (tvProcessingCount != null) tvProcessingCount.setText("处理中: " + processing.size());

                    if (tvEmpty != null) {
                        if (pending.isEmpty() && processing.isEmpty()) {
                            tvEmpty.setVisibility(View.VISIBLE);
                        } else {
                            tvEmpty.setVisibility(View.GONE);
                        }
                    }
                } catch (Exception e) {
                    // 忽略
                }
            });
        } catch (Exception e) {
            appendLog("刷新列表异常: " + e.getMessage());
        }
    }

    private void appendLog(String msg) {
        if (getContext() == null) return;
        mainHandler.post(() -> {
            if (getContext() == null || tvLog == null || svLog == null) return;
            try {
                String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                String log = tvLog.getText().toString();
                tvLog.setText(log + "\n[" + time + "] " + msg);
                svLog.post(() -> svLog.fullScroll(View.FOCUS_DOWN));
            } catch (Exception e) {
                // 忽略
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timeUpdateHandler != null && timeUpdateRunnable != null) {
            timeUpdateHandler.removeCallbacks(timeUpdateRunnable);
        }
        stopMonitor();
    }
}
