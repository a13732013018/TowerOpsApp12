package com.towerops.app.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.towerops.app.R;
import com.towerops.app.api.PowerOutageApi;
import com.towerops.app.model.PowerOutage;
import com.towerops.app.model.Session;
import com.towerops.app.model.WorkOrder;
import com.towerops.app.worker.MonitorService;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 主界面
 *
 * ══════════════ 已修复的 Bug 清单 ══════════════
 *
 * [BUG-5] onPause 置 setCallback(null) → 后台运行期间完全失联
 *   原代码：onPause 调 monitorService.setCallback(null)，
 *           导致后台运行时 callback=null，
 *           MonitorService 里直接调 callback.onXxx() 抛 NPE，
 *           接单/反馈结果全部静默丢失，且服务内有可能因 NPE 崩溃。
 *   修复：改为 setCallback(serviceCallback, silent=true)，
 *         保留引用、关闭 UI 更新，onResume 时传 silent=false 恢复显示。
 *
 * [BUG-17] onAllDone 回调调 syncConfigFromSession，覆盖用户手动设置
 *   原代码：每轮工单处理完后，把 Session.appConfig 里的值同步回 CheckBox，
 *           而 applyTimeSchedule（已删除）或其他逻辑修改了 appConfig，
 *           用户手动关掉的反馈/接单开关在下一轮完成后被强制恢复为开。
 *   修复：删除 syncConfigFromSession() 调用，onAllDone 只更新进度文字。
 *
 * [BUG-18] 每次键盘敲击都写 SharedPreferences（高频 IO）
 *   原代码：EditText TextWatcher.afterTextChanged → applyConfigNow → saveConfig，
 *           用户输入"120"要调3次 apply()。
 *   修复：加 300ms 防抖，连续输入停止后只写一次。
 *
 * [BUG-19] setCallback 接口改为双参数，调用处需同步更新
 *   修复：onServiceConnected / onResume / onPause 全部更新。
 *
 * [BUG-20] setInterval 改名为 setIntervalAndReschedule，调用处需同步更新
 *   修复：startMonitor / applyConfigNow 全部更新。
 */
public class MainActivity extends AppCompatActivity {

    // UI 控件 —— 配置区控件现已迁移至 WorkOrderFragment，通过 getter 懒获取
    private TextView        tvUserInfo, tvProgress, tvNextRun, tvPowerOutageCountStatus, tvRoundComplete, btnLogout;

    // ===== 停电监控相关控件 =====
    private TabLayout        tabLayout;
    private ViewPager2       viewPager;
    private MainPagerAdapter pagerAdapter;
    // 动画控制器
    private TabAnimationHelper tabAnimationHelper;
    private TouchFeedbackHelper touchFeedbackHelper;
    // 服务绑定
    private MonitorService  monitorService;
    private boolean         serviceBound = false;

    // [BUG-18] 防抖 Handler
    private final Handler  debounceHandler  = new Handler(Looper.getMainLooper());
    private       Runnable debounceRunnable;

    // 本地倒计时：前台时自己每秒递减，不依赖 Service 推送
    private CountDownTimer countDownTimer;

    // ── 服务连接 ──────────────────────────────────────────────────────────

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MonitorService.LocalBinder lb = (MonitorService.LocalBinder) service;
            monitorService = lb.getService();
            serviceBound   = true;
            // [BUG-19 修复] silent=false：Activity 在前台，开启 UI 更新
            monitorService.setCallback(serviceCallback, false);
            syncButtonState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound   = false;
            monitorService = null;
        }
    };

    // 服务回调（所有方法均在主线程执行）
    private final MonitorService.ServiceCallback serviceCallback =
            new MonitorService.ServiceCallback() {

        @Override
        public void onOrdersReady(List<WorkOrder> orders) {
            tvProgress.setText("共 " + orders.size() + " 条工单，处理中...");
            WorkOrderFragment wof = getWorkOrderFragment();
            if (wof != null) wof.setData(orders);
        }

        @Override
        public void onStatusUpdate(int rowIndex, String billsn, String content) {
            WorkOrderFragment wof = getWorkOrderFragment();
            if (wof != null) wof.updateStatus(rowIndex, billsn, content);
        }

        @Override
        public void onAllDone(int done, int total) {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            tvProgress.setText("就绪");
            tvRoundComplete.setText("本轮完成 " + done + "/" + total + " 条 " + time);
            // 工单轮询完成后刷新停电数据
            loadPowerOutageData();
        }

        @Override
        public void onError(String msg) {
            tvProgress.setText("错误：" + msg);
        }

        @Override
        public void onNextRun(int delaySec) {
            startCountDown(delaySec);
        }
    };

    // ── 生命周期 ──────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 恢复登录状态（APP和数运的token等）
        Session.get().loadConfig(this);

        bindViews();
        setupRecycler();
        setupTabLayout();         // 初始化Tab切换
        updateUserInfo();

        // 配置区按钮监听：Fragment ready 后由 bindControlsFromFragment() 设置
        // 注册 ViewPager2 页面切换回调，首次切到位置0时绑定控件
        viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    bindControlsFromFragment();
                }
            }
        });
        // 初始就在第0页，延迟一帧等 Fragment onViewCreated 执行完毕
        viewPager.post(this::bindControlsFromFragment);
        btnLogout.setOnClickListener(v -> doLogout());

        Intent intent = new Intent(this, MonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        bindService(intent, conn, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (serviceBound && monitorService != null) {
            // [BUG-5 修复] 恢复前台：取消静默模式，重新接收 UI 更新
            monitorService.setCallback(serviceCallback, false);
            syncButtonState();
            // 恢复倒计时：从 prefs 读取下次执行时间，重新开始本地倒数
            long nextAt   = monitorService.getNextRunAt();
            int  delaySec = (int) Math.max(0, (nextAt - System.currentTimeMillis()) / 1000);
            if (delaySec > 0) startCountDown(delaySec);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // [BUG-5 修复] 退后台：保留 callback 引用但静默，Service 继续全速运行
        if (serviceBound && monitorService != null) {
            monitorService.setCallback(serviceCallback, true);
        }
        // 后台不需要本地倒计时，停掉省资源
        stopCountDown();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        debounceHandler.removeCallbacks(debounceRunnable);
        stopCountDown();
        if (serviceBound) {
            unbindService(conn);
            serviceBound = false;
        }
        // 不 stopService，让服务继续后台运行
    }

    // ── 开始 / 停止 ───────────────────────────────────────────────────────

    private void startMonitor() {
        if (!serviceBound || monitorService == null) {
            Toast.makeText(this, "服务未就绪，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        // 已在监控中，忽略重复点击
        if (monitorService.isRunning()) return;
        WorkOrderFragment wof = getWorkOrderFragment();
        if (wof == null || wof.getEtIntervalMin() == null) {
            Toast.makeText(this, "界面未就绪，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        buildConfig();
        // [BUG-20 修复] 改用新方法名，触发立即重新调度
        monitorService.setIntervalAndReschedule(
                parseInt(wof.getEtIntervalMin().getText().toString(), 90),
                parseInt(wof.getEtIntervalMax().getText().toString(), 120));
        monitorService.startMonitor();
        syncButtonState();
        tvProgress.setText("等待首次执行...");
        // 立即加载停电监控数据,不需要延迟!
        loadPowerOutageData();
    }

    private void stopMonitor() {
        if (!serviceBound || monitorService == null) return;
        monitorService.stopMonitor();
        syncButtonState();
        tvProgress.setText("已停止");
        tvRoundComplete.setText("");
        tvPowerOutageCountStatus.setText("停电 0");
        tvNextRun.setText("");
        stopCountDown();
        // 清空停电列表，避免旧数据残留
        PowerOutageFragment pof = getPowerOutageFragment();
        if (pof != null) pof.clearData();
    }

    /** 退出登录：清除本地凭据，停止服务，跳回登录页 */
    private void doLogout() {
        // 停止监控服务
        if (serviceBound && monitorService != null) {
            monitorService.stopMonitor();
        }
        // 清除内存里的登录信息
        Session s = Session.get();
        s.token       = "";
        s.userid      = "";
        s.mobilephone = "";
        s.username    = "";
        s.realname    = "";
        s.appConfig   = "";
        // 清除 SharedPreferences 里的持久化登录信息
        getApplicationContext()
            .getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
            .edit().clear().apply();
        // 停止服务（退出登录后不需要继续后台跑）
        stopService(new Intent(this, MonitorService.class));
        // 跳回登录页
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void syncButtonState() {
        WorkOrderFragment wof = getWorkOrderFragment();
        if (wof == null || wof.getBtnStartMonitor() == null) return;
        Button btnStart = wof.getBtnStartMonitor();
        Button btnStop  = wof.getBtnStopMonitor();
        if (monitorService != null && monitorService.isRunning()) {
            // 监控中：只改文字，不禁用（避免系统变灰）
            btnStart.setText("监控中");
            btnStart.setEnabled(true);
            btnStop.setEnabled(true);
        } else {
            // 已停止：恢复"开启监控"
            btnStart.setText("开启监控");
            btnStart.setEnabled(true);
            btnStop.setEnabled(true);
        }
    }

    // ── 配置监听 ──────────────────────────────────────────────────────────

    private void setupConfigWatchers() {
        WorkOrderFragment wof = getWorkOrderFragment();
        if (wof == null) return;
        EditText etFbMin  = wof.getEtFeedbackMin();
        EditText etFbMax  = wof.getEtFeedbackMax();
        EditText etAccMin = wof.getEtAcceptMin();
        EditText etAccMax = wof.getEtAcceptMax();
        EditText etIntMin = wof.getEtIntervalMin();
        EditText etIntMax = wof.getEtIntervalMax();
        CheckBox cbFeedback = wof.getCbAutoFeedback();
        CheckBox cbAccept   = wof.getCbAutoAccept();
        CheckBox cbRevert   = wof.getCbAutoRevert();

        if (etFbMin == null) return; // 控件还未就绪，等下次触发

        // [BUG-18 修复] TextWatcher 通过防抖触发，连续输入停止300ms后才写入
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                scheduleApplyConfig();
            }
        };
        etFbMin.addTextChangedListener(watcher);
        etFbMax.addTextChangedListener(watcher);
        etAccMin.addTextChangedListener(watcher);
        etAccMax.addTextChangedListener(watcher);
        etIntMin.addTextChangedListener(watcher);
        etIntMax.addTextChangedListener(watcher);

        cbFeedback.setOnCheckedChangeListener((btn, checked) -> applyConfigNow());
        cbAccept.setOnCheckedChangeListener(  (btn, checked) -> applyConfigNow());
        cbRevert.setOnCheckedChangeListener(  (btn, checked) -> applyConfigNow());
    }

    private void scheduleApplyConfig() {
        if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
        debounceRunnable = this::applyConfigNow;
        debounceHandler.postDelayed(debounceRunnable, 300L);
    }

    /**
     * 立即将当前 UI 配置写入 Session 并通知 Service 重新调度轮询间隔。
     */
    private void applyConfigNow() {
        buildConfig();
        WorkOrderFragment wof = getWorkOrderFragment();
        if (wof == null || wof.getEtIntervalMin() == null) return;
        if (serviceBound && monitorService != null) {
            // [BUG-20 修复] 改用新方法名，阈值修改立即生效
            monitorService.setIntervalAndReschedule(
                    parseInt(wof.getEtIntervalMin().getText().toString(), 90),
                    parseInt(wof.getEtIntervalMax().getText().toString(), 120));
        }
    }

    private void buildConfig() {
        WorkOrderFragment wof = getWorkOrderFragment();
        if (wof == null || wof.getCbAutoFeedback() == null) return;
        Session s        = Session.get();
        String fb        = wof.getCbAutoFeedback().isChecked() ? "true" : "false";
        String acc       = wof.getCbAutoAccept().isChecked()   ? "true" : "false";
        String rev       = wof.getCbAutoRevert().isChecked()   ? "true" : "false";
        String fbMinStr  = defaultIfEmpty(wof.getEtFeedbackMin().getText().toString().trim(), "70");
        String fbMaxStr  = defaultIfEmpty(wof.getEtFeedbackMax().getText().toString().trim(), "90");
        String accMinStr = defaultIfEmpty(wof.getEtAcceptMin().getText().toString().trim(),   "60");
        String accMaxStr = defaultIfEmpty(wof.getEtAcceptMax().getText().toString().trim(),   "90");

        s.appConfig = fb  + "\u0001"
                    + acc + "\u0001"
                    + rev + "\u0001"
                    + fbMinStr  + "|" + fbMaxStr  + "\u0001"
                    + accMinStr + "|" + accMaxStr;
        s.saveConfig(this);
    }

    // ── 视图初始化 ────────────────────────────────────────────────────────

    private void bindViews() {
        tvUserInfo               = findViewById(R.id.tvUserInfo);
        tvProgress               = findViewById(R.id.tvProgress);
        tvNextRun                = findViewById(R.id.tvNextRun);
        tvPowerOutageCountStatus = findViewById(R.id.tvPowerOutageCountStatus);
        tvRoundComplete          = findViewById(R.id.tvRoundComplete);
        btnLogout                = findViewById(R.id.btnLogout);

        // ===== Tab / ViewPager =====
        tabLayout   = findViewById(R.id.tabLayout);
        viewPager   = findViewById(R.id.viewPager);

        // ===== 设置ViewPager2 =====
        pagerAdapter = new MainPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        // 设置 offscreenPageLimit 确保所有 Fragment 都保持活跃状态
        viewPager.setOffscreenPageLimit(8);

        // ===== 配置页面切换动画 =====
        setupViewPagerAnimations();

        // ===== 配置触摸反馈 =====
        setupTouchFeedback();

        // 连接TabLayout和ViewPager2，实现滑动切换
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("工单监控"); break;
                case 1: tab.setText("停电监控"); break;
                case 2: tab.setText("门禁系统"); break;
                case 3: tab.setText("巡检工单"); break;
                case 4: tab.setText("智联工单"); break;
                case 5: tab.setText("数运工单"); break;     // 含设备离线子Tab
                case 6: tab.setText("指标查询"); break;     // 含省内工单处理及时率子Tab
                case 7: tab.setText("运维日常"); break;
                case 8: tab.setText("我的待办"); break;
            }
        }).attach();

        // ===== 配置Tab动画效果 =====
        setupTabAnimations();
    }

    /**
     * 从 WorkOrderFragment 获取配置控件引用，设置按钮监听和输入监听。
     * 在 Fragment onViewCreated 完成后（通过 viewPager.post / 页面切换回调）调用。
     */
    private boolean controlsBound = false;

    private void bindControlsFromFragment() {
        if (controlsBound) return;
        WorkOrderFragment wof = getWorkOrderFragment();
        if (wof == null || wof.getBtnStartMonitor() == null) return;

        // 绑定按钮点击
        wof.getBtnStartMonitor().setOnClickListener(v -> startMonitor());
        wof.getBtnStopMonitor().setOnClickListener(v  -> stopMonitor());

        // 绑定输入监听（防抖写入配置）
        setupConfigWatchers();

        // 同步按钮状态
        syncButtonState();

        controlsBound = true;
    }

    private void setupRecycler() {
        // RecyclerViews 现在由各自的 Fragment 初始化，不再需要在 Activity 中设置
        // WorkOrderFragment 和 PowerOutageFragment 会在 onViewCreated 中初始化各自的 RecyclerView
    }

    // ===== 停电监控 Tab 切换（使用ViewPager2滑动切换） =====
    private void setupTabLayout() {
        // ViewPager2已经通过TabLayoutMediator自动关联
        // 不需要在这里手动刷新数据,因为:
        // 1. 点击"开启监控"按钮时会立即刷新
        // 2. 工单处理完成后会自动刷新
        // 3. 用户切换Tab时不需要额外刷新,避免"加载中"文字闪烁
    }

    // ══════════════════ Tab切换动画配置 ══════════════════

    /**
     * 配置ViewPager2页面切换动画
     * 提供多种专业动画效果可选
     * v2.0: 使用更丝滑的SilkyParallaxTransformer
     */
    private void setupViewPagerAnimations() {
        // 选择动画效果：
        // 1. SilkyParallaxTransformer - 丝滑视差（v2.0新增，默认推荐）
        // 2. AppleElasticTransformer - 苹果风弹性
        // 3. DepthPageTransformer - 深度缩放效果（类似Google Photos）
        // 4. ZoomOutPageTransformer - 缩放+旋转效果
        // 5. CubePageTransformer - 3D立方体旋转
        // 6. AccordionPageTransformer - 折叠效果
        // 7. StackPageTransformer - 堆叠效果
        // 8. FidgetPageTransformer - 弹性效果

        // v2.0 默认使用丝滑视差效果
        viewPager.setPageTransformer(new PageTransformers.SilkyParallaxTransformer());

        // 设置页面之间的间距，产生滑动分隔感
        viewPager.setOffscreenPageLimit(8);

        // 设置UserInputEnabled，允许/禁止手动滑动
        // viewPager.setUserInputEnabled(true); // 默认true

        // 注册页面切换回调（Fragment动画）
        setupFragmentAnimations();
    }

    /**
     * 配置Fragment进入/退出动画
     */
    private void setupFragmentAnimations() {
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            private int previousPosition = 0;

            @Override
            public void onPageSelected(int position) {
                // 获取当前Fragment并播放进入动画
                Fragment currentFragment = pagerAdapter.getFragment(position);
                if (currentFragment != null && currentFragment.getView() != null) {
                    animateFragmentEnter(currentFragment.getView());
                }

                // 获取前一个Fragment并播放退出动画
                if (previousPosition != position) {
                    Fragment prevFragment = pagerAdapter.getFragment(previousPosition);
                    if (prevFragment != null && prevFragment.getView() != null) {
                        animateFragmentExit(prevFragment.getView());
                    }
                }

                previousPosition = position;
            }
        });
    }

    /**
     * Fragment进入动画 v2.0 - 更丝滑
     */
    private void animateFragmentEnter(View view) {
        view.setAlpha(0f);
        view.setTranslationX(40);
        view.setScaleX(0.95f);
        view.setScaleY(0.95f);

        view.animate()
            .alpha(1f)
            .translationX(0)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(250)  // 优化：稍微加快，减少等待感
            .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f))
            .start();
    }

    /**
     * Fragment退出动画 v2.0 - 更丝滑
     */
    private void animateFragmentExit(View view) {
        view.animate()
            .alpha(0.7f)
            .translationX(-20)
            .scaleX(0.98f)
            .scaleY(0.98f)
            .setDuration(150)
            .setInterpolator(new android.view.animation.AccelerateInterpolator())
            .start();
    }

    /**
     * 配置Tab动画效果
     */
    private void setupTabAnimations() {
        // 初始化Tab动画助手
        tabAnimationHelper = new TabAnimationHelper(tabLayout, viewPager);

        // Tab指示器使用默认动画模式
    }

    /**
     * 配置触摸反馈
     */
    private void setupTouchFeedback() {
        touchFeedbackHelper = new TouchFeedbackHelper(this);

        // 为TabLayout添加触摸反馈
        touchFeedbackHelper.attachToView(tabLayout);

        // 为Tab的每个TabItem添加反馈
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            View tabView = tabLayout.getTabAt(i) != null ? tabLayout.getTabAt(i).view : null;
            if (tabView != null) {
                touchFeedbackHelper.attachToView(tabView);
            }
        }
    }

    /**
     * 切换到指定Tab（带动画）
     */
    public void switchToTab(int position) {
        if (position >= 0 && position < 9) {
            viewPager.setCurrentItem(position, true);
            // 播放触觉反馈
            if (touchFeedbackHelper != null) {
                touchFeedbackHelper.performClickFeedback();
            }
        }
    }

    /**
     * 切换到指定Tab（无动画，快速跳转）
     */
    public void switchToTabImmediate(int position) {
        if (position >= 0 && position < 9) {
            viewPager.setCurrentItem(position, false);
        }
    }

    /**
     * 获取当前Tab位置
     */
    public int getCurrentTabPosition() {
        return viewPager.getCurrentItem();
    }

    /**
     * 切换到下一个Tab
     */
    public void switchToNextTab() {
        int current = viewPager.getCurrentItem();
        if (current < 8) {
            switchToTab(current + 1);
        }
    }

    /**
     * 切换到上一个Tab
     */
    public void switchToPrevTab() {
        int current = viewPager.getCurrentItem();
        if (current > 0) {
            switchToTab(current - 1);
        }
    }

    /**
     * 设置页面切换动画类型 v2.0
     * @param type 0=丝滑视差(默认), 1=苹果风弹性, 2=深度缩放, 3=缩放旋转, 4=3D旋转, 5=折叠, 6=堆叠, 7=弹性
     */
    public void setPageAnimation(int type) {
        switch (type) {
            case 0:
                viewPager.setPageTransformer(new PageTransformers.SilkyParallaxTransformer());
                break;
            case 1:
                viewPager.setPageTransformer(new PageTransformers.AppleElasticTransformer());
                break;
            case 2:
                viewPager.setPageTransformer(new PageTransformers.DepthPageTransformer());
                break;
            case 3:
                viewPager.setPageTransformer(new PageTransformers.ZoomOutPageTransformer());
                break;
            case 4:
                viewPager.setPageTransformer(new PageTransformers.CubePageTransformer());
                break;
            case 5:
                viewPager.setPageTransformer(new PageTransformers.AccordionPageTransformer());
                break;
            case 6:
                viewPager.setPageTransformer(new PageTransformers.StackPageTransformer());
                break;
            case 7:
                viewPager.setPageTransformer(new PageTransformers.FidgetPageTransformer());
                break;
            default:
                viewPager.setPageTransformer(new PageTransformers.SilkyParallaxTransformer());
        }
    }

    /**
     * 禁用页面切换动画
     */
    public void disablePageAnimation() {
        viewPager.setPageTransformer(null);
    }

    /**
     * 设置触觉反馈开关
     */
    public void setHapticFeedbackEnabled(boolean enabled) {
        if (touchFeedbackHelper != null) {
            touchFeedbackHelper.setHapticEnabled(enabled);
        }
    }

    // ===== 获取 PowerOutageFragment =====
    private PowerOutageFragment getPowerOutageFragment() {
        if (pagerAdapter != null) {
            androidx.fragment.app.Fragment f = pagerAdapter.getFragment(1);  // 停电监控在 position 1
            if (f instanceof PowerOutageFragment) return (PowerOutageFragment) f;
        }
        return null;
    }

    // ===== 获取 WorkOrderFragment =====
    private WorkOrderFragment getWorkOrderFragment() {
        if (pagerAdapter != null) {
            androidx.fragment.app.Fragment f = pagerAdapter.getFragment(0);
            if (f instanceof WorkOrderFragment) return (WorkOrderFragment) f;
        }
        return null;
    }

    // ===== 加载停电数据 =====
    private void loadPowerOutageData() {
        new Thread(() -> {
            List<PowerOutage> result = fetchPowerOutageData();
            new Handler(Looper.getMainLooper()).post(() -> {
                PowerOutageFragment pof = getPowerOutageFragment();
                if (result != null) {
                    if (pof != null) pof.setData(result);
                    tvPowerOutageCountStatus.setText("当前停电 " + result.size() + " 个");
                } else {
                    tvPowerOutageCountStatus.setText("当前停电 0 个");
                }
            });
        }).start();
    }

    // ===== 获取停电数据（单线程顺序处理,避免并发问题）=====
    private List<PowerOutage> fetchPowerOutageData() {
        List<PowerOutage> result = new ArrayList<>();
        try {
            android.util.Log.d("MainActivity", "fetchPowerOutageData: 开始获取停电告警列表");

            // 1. 获取停电告警列表
            String jsonStr1 = PowerOutageApi.getPowerOutageList();
            List<JSONObject> alarmList = PowerOutageApi.parseAlarmList(jsonStr1);

            if (alarmList == null || alarmList.isEmpty()) {
                android.util.Log.d("MainActivity", "fetchPowerOutageData: 停电告警列表为空");
                return result;
            }

            android.util.Log.d("MainActivity", "fetchPowerOutageData: 获取到 " + alarmList.size() + " 个停电告警");

            // 2. 单线程顺序获取每个站点的详细数据
            int total = alarmList.size();
            for (int i = 0; i < alarmList.size(); i++) {
                try {
                    JSONObject alarm = alarmList.get(i);
                    String stCode = alarm.optString("st_code", "");
                    String stName = alarm.optString("st_name", "");
                    String alarmTime = alarm.optString("firstsystemtime", "");
                    String cause = alarm.optString("cause", "");
                    String deviceName = alarm.optString("devicename", "");
                    String stId = alarm.optString("st_id", "");

                    // 匹配分组
                    String groupName = PowerOutageApi.matchGroup(stName);

                    // 初始化默认值
                    String dcVoltage = "";
                    String dcLoadCurrent = "";

                    // 获取开关电源数据
                    String deviceListJson = PowerOutageApi.getStationDeviceList(stId);
                    String deviceCode = PowerOutageApi.findPowerSupplyDeviceCode(deviceListJson);

                    if (deviceCode != null && !deviceCode.isEmpty()) {
                        String deviceDataJson = PowerOutageApi.getPowerDeviceData(stId, deviceCode);
                        JSONObject dataObj = new JSONObject();
                        PowerOutageApi.parsePowerDeviceData(deviceDataJson, dataObj);
                        dcVoltage = dataObj.optString("dcVoltage", "");
                        dcLoadCurrent = dataObj.optString("dcLoadCurrent", "");
                    }

                    PowerOutage po = new PowerOutage();
                    po.setIndex(i + 1);
                    po.setGroupName(groupName);
                    po.setStCode(stCode);
                    po.setStName(stName);
                    po.setAlarmTime(alarmTime);
                    po.setCause(cause);
                    po.setDeviceName(deviceName);
                    po.setStId(stId);
                    po.setDcVoltage(dcVoltage);
                    po.setDcLoadCurrent(dcLoadCurrent);

                    result.add(po);
                    android.util.Log.d("MainActivity", "fetchPowerOutageData: 完成站点 " + stName + " (" + (i + 1) + "/" + total + ")");

                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "fetchPowerOutageData: 处理站点失败 " + i, e);
                    e.printStackTrace();
                    // 单个站点失败不影响其他站点,继续处理下一个
                }
            }

            android.util.Log.d("MainActivity", "fetchPowerOutageData: 所有站点数据完成, 总计 " + result.size() + " 个");

        } catch (Exception e) {
            android.util.Log.e("MainActivity", "fetchPowerOutageData: 发生异常", e);
            e.printStackTrace();
        }
        return result;
    }

    private void updateUserInfo() {
        Session s = Session.get();
        tvUserInfo.setText(s.username.isEmpty() ? "未登录" : s.username + " | " + s.userid);
    }


    // ── 静态工具 ──────────────────────────────────────────────────────────

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static String defaultIfEmpty(String s, String def) {
        return (s == null || s.isEmpty()) ? def : s;
    }

    /**
     * 启动本地倒计时，更新UI显示。
     * 多次调用时自动取消上次再重新开始。
     */
    private void startCountDown(int delaySec) {
        stopCountDown();
        tvNextRun.setText("剩余 " + delaySec + " 秒进入下一轮");
        countDownTimer = new CountDownTimer(delaySec * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvNextRun.setText("剩余 " + (millisUntilFinished / 1000) + " 秒进入下一轮");
            }
            @Override
            public void onFinish() {
                tvNextRun.setText("执行中...");
            }
        }.start();
    }

    /** 停止并销毁本地倒计时 */
    private void stopCountDown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    // ── 后台保活：厂商白名单 ──────────────────────────────────────────────

    /**
     * 跳转到对应厂商的后台管理/自启动白名单设置页面。
     * 国产 ROM 的后台杀进程不仅受电池优化控制，还有独立的"自启动"/"后台运行"开关，
     * 必须在厂商自己的设置页里手动开启，代码层面无法绕过。
     * 在"开始监控"按钮点击时调用，提示用户手动操作一次即可。
     */
    private void goToVendorBatterySettings() {
        String manufacturer = android.os.Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT);
        Intent intent = null;
        try {
            if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
                // 华为：受保护应用 / 应用启动管理
                intent = new Intent();
                intent.setComponent(new ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
            } else if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi")) {
                // 小米：自启动管理
                intent = new Intent();
                intent.setComponent(new ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            } else if (manufacturer.contains("oppo") || manufacturer.contains("realme")) {
                // OPPO/Realme：自启动管理
                intent = new Intent();
                intent.setComponent(new ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.privacypermissionsentry.PermissionTopActivity"));
            } else if (manufacturer.contains("vivo")) {
                // vivo：后台高耗电
                intent = new Intent();
                intent.setComponent(new ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
            } else if (manufacturer.contains("meizu")) {
                // 魅族：自启动管理
                intent = new Intent("com.meizu.safe.security.SHOW_APPSEC");
                intent.putExtra("packageName", getPackageName());
            } else if (manufacturer.contains("samsung")) {
                // 三星：设备维护 > 电池
                intent = new Intent();
                intent.setComponent(new ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"));
            }

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Toast.makeText(this,
                        "请在此页面找到本应用，\n开启\"允许后台运行\"或\"自启动\"",
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            // 厂商页面跳转失败（ROM 版本差异），降级到系统电池设置
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {}
        }
    }
}
