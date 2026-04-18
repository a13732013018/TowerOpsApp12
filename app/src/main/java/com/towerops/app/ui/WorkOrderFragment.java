package com.towerops.app.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.api.WorkOrderApi;
import com.towerops.app.model.Session;
import com.towerops.app.model.WorkOrder;
import com.towerops.app.util.Logger;
import com.towerops.app.util.ThreadManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 工单监控Fragment
 * 包含：配置控制面板（开关+按钮+阈值输入）、排序工具栏、工单列表
 * 4A登录已移至「门禁系统」Tab
 */
public class WorkOrderFragment extends Fragment {

    private WorkOrderAdapter adapter;
    private RecyclerView recyclerView;

    // ===== 配置区控件 =====
    private CheckBox cbAutoFeedback;
    private CheckBox cbAutoAccept;
    private CheckBox cbAutoRevert;
    private Button   btnStartMonitor;
    private Button   btnStopMonitor;
    private EditText etIntervalMin;
    private EditText etIntervalMax;
    private EditText etFeedbackMin;
    private EditText etFeedbackMax;
    private EditText etAcceptMin;
    private EditText etAcceptMax;

    /** 后台线程池，用于 OMMS 告警清除请求 */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static WorkOrderFragment newInstance() {
        return new WorkOrderFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_work_order, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 绑定配置区控件
        cbAutoFeedback  = view.findViewById(R.id.cbAutoFeedback);
        cbAutoAccept    = view.findViewById(R.id.cbAutoAccept);
        cbAutoRevert    = view.findViewById(R.id.cbAutoRevert);
        btnStartMonitor = view.findViewById(R.id.btnStartMonitor);
        btnStopMonitor  = view.findViewById(R.id.btnStopMonitor);
        etIntervalMin   = view.findViewById(R.id.etIntervalMin);
        etIntervalMax   = view.findViewById(R.id.etIntervalMax);
        etFeedbackMin   = view.findViewById(R.id.etFeedbackMin);
        etFeedbackMax   = view.findViewById(R.id.etFeedbackMax);
        etAcceptMin     = view.findViewById(R.id.etAcceptMin);
        etAcceptMax     = view.findViewById(R.id.etAcceptMax);

        // 绑定列表
        recyclerView = view.findViewById(R.id.recyclerWorkOrders);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            adapter = new WorkOrderAdapter();
            recyclerView.setAdapter(adapter);
        }
        // 注册长按回调：弹出告警清除确认框
        if (adapter != null) {
            adapter.setOnItemLongClickListener((position, order) ->
                    showClearAlarmDialog(order));
        }
        setupSortButtons(view);
    }

    // ─────────────────────────────────────────────────────────────────────

    private void setupSortButtons(View view) {
        TextView btnSortBillTime = view.findViewById(R.id.btnSortBillTime);
        TextView btnSortFeedbackTime = view.findViewById(R.id.btnSortFeedbackTime);
        TextView btnSortAlertTime = view.findViewById(R.id.btnSortAlertTime);
        TextView btnSortAcceptor = view.findViewById(R.id.btnSortAcceptor);
        TextView btnSortAlertStatus = view.findViewById(R.id.btnSortAlertStatus);

        int bgPrimary   = R.drawable.bg_tag_primary;
        int bgSecondary = R.drawable.bg_tag_secondary;

        if (btnSortBillTime != null) {
            btnSortBillTime.setOnClickListener(v -> {
                if (adapter == null) return;
                WorkOrderAdapter.SortMode cur = adapter.getSortMode();
                adapter.setSortMode(cur == WorkOrderAdapter.SortMode.BILL_TIME_DESC
                        ? WorkOrderAdapter.SortMode.BILL_TIME_ASC
                        : WorkOrderAdapter.SortMode.BILL_TIME_DESC);
                updateSortButtonStyles(btnSortBillTime, btnSortFeedbackTime, btnSortAlertTime, btnSortAcceptor, btnSortAlertStatus, bgPrimary, bgSecondary);
            });
        }
        if (btnSortFeedbackTime != null) {
            btnSortFeedbackTime.setOnClickListener(v -> {
                if (adapter == null) return;
                WorkOrderAdapter.SortMode cur = adapter.getSortMode();
                adapter.setSortMode(cur == WorkOrderAdapter.SortMode.FEEDBACK_TIME_DESC
                        ? WorkOrderAdapter.SortMode.FEEDBACK_TIME_ASC
                        : WorkOrderAdapter.SortMode.FEEDBACK_TIME_DESC);
                updateSortButtonStyles(btnSortBillTime, btnSortFeedbackTime, btnSortAlertTime, btnSortAcceptor, btnSortAlertStatus, bgPrimary, bgSecondary);
            });
        }
        if (btnSortAlertTime != null) {
            btnSortAlertTime.setOnClickListener(v -> {
                if (adapter == null) return;
                WorkOrderAdapter.SortMode cur = adapter.getSortMode();
                adapter.setSortMode(cur == WorkOrderAdapter.SortMode.ALERT_TIME_DESC
                        ? WorkOrderAdapter.SortMode.ALERT_TIME_ASC
                        : WorkOrderAdapter.SortMode.ALERT_TIME_DESC);
                updateSortButtonStyles(btnSortBillTime, btnSortFeedbackTime, btnSortAlertTime, btnSortAcceptor, btnSortAlertStatus, bgPrimary, bgSecondary);
            });
        }
        if (btnSortAcceptor != null) {
            btnSortAcceptor.setOnClickListener(v -> {
                if (adapter == null) return;
                WorkOrderAdapter.SortMode cur = adapter.getSortMode();
                adapter.setSortMode(cur == WorkOrderAdapter.SortMode.ACCEPTOR_DESC
                        ? WorkOrderAdapter.SortMode.ACCEPTOR_ASC
                        : WorkOrderAdapter.SortMode.ACCEPTOR_DESC);
                updateSortButtonStyles(btnSortBillTime, btnSortFeedbackTime, btnSortAlertTime, btnSortAcceptor, btnSortAlertStatus, bgPrimary, bgSecondary);
            });
        }
        if (btnSortAlertStatus != null) {
            btnSortAlertStatus.setOnClickListener(v -> {
                if (adapter == null) return;
                WorkOrderAdapter.SortMode cur = adapter.getSortMode();
                adapter.setSortMode(cur == WorkOrderAdapter.SortMode.ALERT_STATUS_ALARM
                        ? WorkOrderAdapter.SortMode.ALERT_STATUS_RECOVER
                        : WorkOrderAdapter.SortMode.ALERT_STATUS_ALARM);
                updateSortButtonStyles(btnSortBillTime, btnSortFeedbackTime, btnSortAlertTime, btnSortAcceptor, btnSortAlertStatus, bgPrimary, bgSecondary);
            });
        }
    }

    private void updateSortButtonStyles(TextView btnBill, TextView btnFeedback, TextView btnAlert, TextView btnAcceptor, TextView btnStatus,
                                        int bgPrimary, int bgSecondary) {
        if (adapter == null) return;
        WorkOrderAdapter.SortMode cur = adapter.getSortMode();

        btnBill.setBackgroundResource(bgSecondary);
        btnBill.setTextColor(requireContext().getColor(R.color.text_secondary));
        btnFeedback.setBackgroundResource(bgSecondary);
        btnFeedback.setTextColor(requireContext().getColor(R.color.text_secondary));
        btnAlert.setBackgroundResource(bgSecondary);
        btnAlert.setTextColor(requireContext().getColor(R.color.text_secondary));
        btnAcceptor.setBackgroundResource(bgSecondary);
        btnAcceptor.setTextColor(requireContext().getColor(R.color.text_secondary));
        btnStatus.setBackgroundResource(bgSecondary);
        btnStatus.setTextColor(requireContext().getColor(R.color.text_secondary));

        switch (cur) {
            case BILL_TIME_DESC:
            case BILL_TIME_ASC:
                btnBill.setBackgroundResource(bgPrimary);
                btnBill.setTextColor(requireContext().getColor(R.color.text_inverse));
                btnBill.setText(cur == WorkOrderAdapter.SortMode.BILL_TIME_ASC ? "工单历时 ↑" : "工单历时 ↓");
                break;
            case FEEDBACK_TIME_DESC:
            case FEEDBACK_TIME_ASC:
                btnFeedback.setBackgroundResource(bgPrimary);
                btnFeedback.setTextColor(requireContext().getColor(R.color.text_inverse));
                btnFeedback.setText(cur == WorkOrderAdapter.SortMode.FEEDBACK_TIME_ASC ? "反馈历时 ↑" : "反馈历时 ↓");
                break;
            case ALERT_TIME_DESC:
                btnAlert.setBackgroundResource(bgPrimary);
                btnAlert.setTextColor(requireContext().getColor(R.color.text_inverse));
                btnAlert.setText("告警时间 ↓");
                break;
            case ALERT_TIME_ASC:
                btnAlert.setBackgroundResource(bgPrimary);
                btnAlert.setTextColor(requireContext().getColor(R.color.text_inverse));
                btnAlert.setText("告警时间 ↑");
                break;
            case ACCEPTOR_DESC:
            case ACCEPTOR_ASC:
                btnAcceptor.setBackgroundResource(bgPrimary);
                btnAcceptor.setTextColor(requireContext().getColor(R.color.text_inverse));
                btnAcceptor.setText(cur == WorkOrderAdapter.SortMode.ACCEPTOR_ASC ? "接单人 ↑" : "接单人 ↓");
                break;
            case ALERT_STATUS_ALARM:
            case ALERT_STATUS_RECOVER:
                btnStatus.setBackgroundResource(bgPrimary);
                btnStatus.setTextColor(requireContext().getColor(R.color.text_inverse));
                btnStatus.setText(cur == WorkOrderAdapter.SortMode.ALERT_STATUS_ALARM ? "告警状态 ⚡" : "告警状态 ✓");
                break;
        }
    }

    public void setData(List<WorkOrder> orders) {
        if (adapter != null) adapter.setData(orders);
    }

    public void updateStatus(int rowIndex, String billsn, String content) {
        if (adapter != null) adapter.updateStatus(rowIndex, billsn, content);
    }

    public WorkOrderAdapter getAdapter()                       { return adapter; }
    public void setAdapter(WorkOrderAdapter adapter) {
        this.adapter = adapter;
        if (recyclerView != null) recyclerView.setAdapter(adapter);
        // 重新注册长按回调
        if (adapter != null) {
            adapter.setOnItemLongClickListener((position, order) ->
                    showClearAlarmDialog(order));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 告警清除
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 弹出确认框：提示用户是否清除该工单的 OMMS 告警
     * 需要从 OMMS 先查询告警列表，找到对应 billsn 的告警 ID，
     * 再执行「告警确认」→「告警清除」两步操作。
     */
    private void showClearAlarmDialog(WorkOrder order) {
        if (getContext() == null) return;

        // 检查 ommsCookie 是否已配置
        String ommsCookie = Session.get().ommsCookie;
        if (ommsCookie == null || ommsCookie.isEmpty()) {
            Toast.makeText(requireContext(),
                    "请先在「门禁系统」Tab 完成 OMMS 登录，获取 ommsCookie 后再操作",
                    Toast.LENGTH_LONG).show();
            return;
        }

        String title = order.stationname + "  " + order.billtitle;
        String msg = "工单号：" + order.billsn
                + "\n站点：" + order.stationname
                + "\n告警状态：" + (order.alertStatus == null ? "未知" : order.alertStatus)
                + "\n\n确认要清除该工单的 OMMS 告警吗？";

        new AlertDialog.Builder(requireContext())
                .setTitle("🗑 清除告警：" + order.stationname)
                .setMessage(msg)
                .setPositiveButton("确认清除", (dialog, which) -> doQueryAndClearAlarm(order))
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 后台执行：
     *   策略A：用精确站点名查询 OMMS 告警列表，从 HTML 解析 alarmId + ViewState
     *   策略B：全量查询（35条），按站点名匹配
     *   Fallback：弹对话框让用户手动输入 alarmId
     *
     *   关键：ViewState 是 RichFaces 的会话状态令牌（j_id12 等），
     *         每次查询后从响应里提取最新值，传给确认/清除接口，否则服务器拒绝操作。
     */
    private void doQueryAndClearAlarm(WorkOrder order) {
        if (getContext() == null) return;

        if (adapter != null) {
            adapter.updateStatus(0, order.billsn, "⏳查询告警中...");
        }

        executor.submit(() -> {
            try {
                // ── 策略A：用精确站点名向 OMMS 发起告警查询，缩小结果集 ──────────
                runOnUi(() -> {
                    if (adapter != null) adapter.updateStatus(0, order.billsn, "⏳策略A:精确查询...");
                });
                String listRespA = WorkOrderApi.getOmmsAlarmListBySite(order.stationname);
                Logger.d("ClearAlarm", "策略A响应长度=" + listRespA.length());

                // 策略A返回的如果很小（<1500字节）且有登录表单特征 → Cookie已失效，立即报错
                // 登录表单特征：password/username输入框、dologin URL、uac/login 重定向
                // 注意：不能只凭"login"字符串判断，因为OMMS正常页面里"login"到处出现
                boolean aIsLogin = listRespA.length() > 0 && listRespA.length() < 1500
                        && (listRespA.contains("name=\"password\"")
                            || listRespA.contains("name=\"username\"")
                            || listRespA.contains("name=\"j_password\"")
                            || listRespA.contains("name=\"j_username\"")
                            || listRespA.contains("dologin")
                            || (listRespA.contains("uac") && listRespA.contains("login")));
                if (aIsLogin) {
                    Logger.w("ClearAlarm", "策略A检测到登录页，Cookie已失效");
                    runOnUi(() -> {
                        if (adapter != null) adapter.updateStatus(0, order.billsn, "⚠️ OMMS登录已失效");
                        showToast(order.stationname + " ⚠️ OMMS登录已失效，请重新获取Cookie！");
                    });
                    return;
                }

                String alarmId   = parseAlarmIdFromHtml(listRespA, order.stationname, order.billsn);
                // 同时提取本次响应的 ViewState（用于后续确认/清除）
                String viewState = extractViewState(listRespA);
                Logger.d("ClearAlarm", "策略A viewState=" + viewState + " alarmId=" + alarmId);

                // ── 策略B：全量查询（页1，35条）──────────────────────────────────
                if (alarmId == null || alarmId.isEmpty()) {
                    runOnUi(() -> {
                        if (adapter != null) adapter.updateStatus(0, order.billsn, "⏳策略B:全量查询...");
                    });
                    String listRespB = WorkOrderApi.getOmmsAlarmList();
                    Logger.d("ClearAlarm", "策略B响应长度=" + listRespB.length());
                    alarmId   = parseAlarmIdFromHtml(listRespB, order.stationname, order.billsn);
                    viewState = extractViewState(listRespB);
                    Logger.d("ClearAlarm", "策略B viewState=" + viewState + " alarmId=" + alarmId);
                }

                if (alarmId == null || alarmId.isEmpty()) {
                    // 告警ID为空有两种可能：
                    //  1. 真的没有告警（正常情况）
                    //  2. OMMS Cookie已失效，返回的是登录页（响应很小+含login关键词）
                    String listRespDbg = WorkOrderApi.getOmmsAlarmList();
                    int dbgLen = listRespDbg != null ? listRespDbg.length() : 0;
                    // 打印实际响应内容（截取前200字符），方便调试
                    String dbgPreview = listRespDbg != null
                            ? listRespDbg.substring(0, Math.min(200, listRespDbg.length())).replace("\n", " ").replace("\r", "")
                            : "null";
                    Logger.w("ClearAlarm", "未找到告警ID，响应len=" + dbgLen + " 内容=" + dbgPreview);
                    // 真正的登录页特征：password/username输入框、dologin URL
                    // 不能只凭"login"字符串，因为OMMS正常页面里到处都是（如"logged in", "loginTime"）
                    boolean likelyLoginPage = dbgLen > 0 && dbgLen < 1500
                            && (listRespDbg.contains("name=\"password\"")
                                || listRespDbg.contains("name=\"username\"")
                                || listRespDbg.contains("name=\"j_password\"")
                                || listRespDbg.contains("name=\"j_username\"")
                                || listRespDbg.contains("dologin")
                                || (listRespDbg.contains("uac") && listRespDbg.contains("login")));
                    Logger.w("ClearAlarm", "未找到告警ID，响应len=" + dbgLen + " 可能是登录页=" + likelyLoginPage + " 内容=" + dbgPreview);

                    if (likelyLoginPage) {
                        // Cookie已失效 → 明确提示用户去刷新Cookie
                        runOnUi(() -> {
                            if (adapter != null) adapter.updateStatus(0, order.billsn, "⚠️ OMMS登录已失效");
                            showToast(order.stationname + " ⚠️ OMMS登录已失效，请重新获取Cookie！");
                        });
                        return;
                    }

                    // 真的没有告警数据 → 弹对话框让用户手动输入alarmId
                    String debugInfo   = extractAlarmRowsDebugInfo(listRespDbg);
                    final String fd    = debugInfo;
                    runOnUi(() -> {
                        if (adapter != null) adapter.updateStatus(0, order.billsn, "❌ 未找到告警ID");
                        showAlarmNotFoundDialog(order, fd);
                    });
                    return;
                }

                final String foundAlarmId  = alarmId;
                // viewState 用本次查询拿到的最新值；若提取失败则回退到 j_id12
                final String finalViewState = (viewState != null && !viewState.isEmpty()) ? viewState : "j_id12";
                Logger.d("ClearAlarm", "找到alarmId=" + foundAlarmId + " viewState=" + finalViewState);

                // ★★★ 在 confirm/clear 前先调用 refreshAlarmViewState() ★★★
                // JSF j_id 是动态的，必须从完整 HTML 页面提取最新值
                // 这会同步更新 confirmTrigger 和 clearTrigger
                Logger.d("ClearAlarm", "刷新告警页，解析最新j_id触发字段...");
                WorkOrderApi.refreshAlarmViewState();

                runOnUi(() -> {
                    if (adapter != null) adapter.updateStatus(0, order.billsn, "⏳告警确认中...");
                });

                // Step3: 告警确认（传入当前 ViewState）
                String confirmResp = WorkOrderApi.confirmOmmsAlarm(foundAlarmId, finalViewState);
                Logger.d("ClearAlarm", "告警确认len=" + confirmResp.length()
                        + " preview=" + confirmResp.substring(0, Math.min(300, confirmResp.length())));

                // ── 判断是否需要继续清除 ───────────────────────────────────────────
                // confirm是AJAX请求，返回RichFaces XML片段，不是完整HTML页面。
                // 旧逻辑：试图从XML中找checkbox title → 永远找不到 → 永远失败
                // 新逻辑：直接跳过验证（confirm已执行），让clear的报错告诉我们结果
                // AJAX响应含重定向URL是正常的（如RichFaces返回window.location.href配置）
                // 只有当响应很小(<1000字节)且有明确登录表单特征时才判为登录页
                boolean isLoginPage = confirmResp != null
                        && confirmResp.length() < 1000
                        && (confirmResp.contains("name=\"password\"")
                            || confirmResp.contains("name=\"username\"")
                            || confirmResp.contains("name=\"j_password\"")
                            || confirmResp.contains("name=\"j_username\"")
                            || confirmResp.contains("dologin"));
                if (isLoginPage) {
                    Logger.w("ClearAlarm", "confirm响应含登录页，Cookie失效");
                    runOnUi(() -> {
                        if (adapter != null) adapter.updateStatus(0, order.billsn, "⚠️ OMMS登录已失效");
                        showToast(order.stationname + " ⚠️ OMMS登录已失效，请重新获取Cookie");
                    });
                    return;
                }
                // confirm执行完成 → 直接进入清除流程

                // confirm 完成后，RichFaces 会返回新的 ViewState，必须提取出来给 clear 用
                String vsAfterConfirm = extractViewState(confirmResp);
                final String clearViewState = (vsAfterConfirm != null && !vsAfterConfirm.isEmpty())
                        ? vsAfterConfirm : finalViewState;
                Logger.d("ClearAlarm", "confirm后ViewState=" + clearViewState);

                runOnUi(() -> {
                    if (adapter != null) adapter.updateStatus(0, order.billsn, "⏳告警清除中...");
                });

                // Step4: 告警清除（等待500ms模拟人工操作，用 confirm 后的最新 ViewState）
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                String clearResp = WorkOrderApi.clearOmmsAlarm(foundAlarmId, order.billsn, clearViewState);
                Logger.d("ClearAlarm", "告警清除len=" + clearResp.length()
                        + " preview=" + clearResp.substring(0, Math.min(200, clearResp.length())));

                boolean success = isClearSuccess(clearResp, foundAlarmId);
                String result = success ? "✅ 告警已清除" : "❌ 清除失败(告警仍存在)";

                runOnUi(() -> {
                    if (adapter != null) adapter.updateStatus(0, order.billsn, result);
                    showToast(order.stationname + " " + result);
                });

            } catch (Exception e) {
                Logger.e("ClearAlarm", "清除异常: " + e.getMessage(), e);
                runOnUi(() -> {
                    if (adapter != null) adapter.updateStatus(0, order.billsn, "❌ 异常: " + e.getMessage());
                    showToast("清除告警异常: " + e.getMessage());
                });
            }
        });
    }

    /**
     * 未找到告警ID时弹出对话框，显示调试信息，提示用户手动输入 alarmId
     */
    private void showAlarmNotFoundDialog(WorkOrder order, String debugInfo) {
        if (getContext() == null) return;

        android.widget.EditText et = new android.widget.EditText(requireContext());
        et.setHint("手动输入 alarmId（32位十六进制）");

        new AlertDialog.Builder(requireContext())
                .setTitle("⚠️ 未能自动找到告警ID")
                .setMessage("站点：" + order.stationname
                        + "\n工单：" + order.billsn
                        + "\n\n" + debugInfo
                        + "\n\n如需强制清除，请手动输入告警ID：")
                .setView(et)
                .setPositiveButton("手动清除", (d, w) -> {
                    String manualId = et.getText().toString().trim();
                    if (manualId.length() == 32) {
                        doManualClearAlarm(order, manualId);
                    } else {
                        showToast("告警ID格式不正确（应为32位十六进制）");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 使用手动输入的 alarmId 执行清除（先查一次列表拿最新 ViewState）
     */
    private void doManualClearAlarm(WorkOrder order, String alarmId) {
        if (adapter != null) adapter.updateStatus(0, order.billsn, "⏳手动清除中...");
        executor.submit(() -> {
            try {
                // ★★★ 刷新 j_id 触发字段 ★★★
                WorkOrderApi.refreshAlarmViewState();
                // 查询一次获取最新 ViewState
                String listResp  = WorkOrderApi.getOmmsAlarmList();
                String viewState = extractViewState(listResp);
                if (viewState == null || viewState.isEmpty()) viewState = "j_id12";
                String confirmResp2 = WorkOrderApi.confirmOmmsAlarm(alarmId, viewState);
                // confirm 后提取最新 ViewState
                String vsAfter2 = extractViewState(confirmResp2);
                String clearVs  = (vsAfter2 != null && !vsAfter2.isEmpty()) ? vsAfter2 : viewState;
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                String clearResp = WorkOrderApi.clearOmmsAlarm(alarmId, order.billsn, clearVs);
                boolean success = isClearSuccess(clearResp, alarmId);
                String result = success ? "✅ 手动清除成功" : "❌ 清除失败(告警仍存在)";
                runOnUi(() -> {
                    if (adapter != null) adapter.updateStatus(0, order.billsn, result);
                    showToast(order.stationname + " " + result);
                });
            } catch (Exception e) {
                runOnUi(() -> {
                    if (adapter != null) adapter.updateStatus(0, order.billsn, "❌ " + e.getMessage());
                });
            }
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    // OMMS HTML 解析工具
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 从 OMMS listAlarm HTML 页面中解析 alarmId。
     *
     * 查询方式：RichFaces AJAX POST（触发器 queryForm:j_id179）
     *   → 服务器返回 XML 片段，格式：
     *     <ajax-response>
     *       <response type="object" id="listForm:list:tb"><![CDATA[...HTML片段...]]></response>
     *       ...
     *     </ajax-response>
     *   需先提取所有 CDATA 内容拼合成 HTML，再找 selectFlag checkbox。
     *
     * 真实 HTML 结构（RichFaces 数据表格，tbody id="listForm:list:tb"）：
     *   <tr class="rich-table-row...">
     *     <td><input type="checkbox" name="selectFlag" value="HEX32" alt="站点名~isConfirm" /></td>
     *     <td>序列号</td>
     *     <td>...</td>
     *     <td><a href="javascript:showStationDetailFunc('siteId','urlenc')">站点名称</a></td>
     *     ...
     *   </tr>
     *
     * 解析策略（多层降级）：
     *   0. 【预处理】若响应是 RichFaces XML，提取所有 CDATA 片段拼合为 HTML
     *   1. 【主路径】解析 name="selectFlag" checkbox 的 value（32位十六进制）
     *      同时从 checkbox 的 alt 属性或 showStationDetailFunc 提取站点名
     *   2. 【备用】解析 <tr ondblclick="showAlarmDetail('HEX32')"> 格式
     *   3. 单个结果直接返回，多个结果按站点名匹配
     */
    private static String parseAlarmIdFromHtml(String html, String stationName, String billsn) {
        if (html == null || html.isEmpty()) return null;
        try {
            Logger.d("ClearAlarm", "parseAlarmIdFromHtml len=" + html.length()
                    + " preview=" + html.substring(0, Math.min(300, html.length())));

            // ── 步骤0：预处理响应格式 ─────────────────────────────────────────
            // OMMS AJAX 查询实际返回的是 XHTML：<?xml version="1.0"?><html ...>...</html>
            // 数据行直接包含在 XHTML 的 tbody 里，可直接正则解析，无需处理。
            //
            // 只有真正的 RichFaces ajax-response 格式才需要提取 CDATA：
            //   <ajax-response>...<response id="..."><![CDATA[HTML片段]]></response>...</ajax-response>
            // 注意：<?xml 开头但后面是 <html 的 XHTML 不属于这种格式，不应提取 CDATA！
            String workHtml = html;
            if (html.contains("<ajax-response")) {
                // 真正的 ajax-response 格式：提取所有 CDATA 片段
                StringBuilder cdataBuf = new StringBuilder();
                java.util.regex.Matcher cdataM = java.util.regex.Pattern.compile(
                        "<!\\[CDATA\\[(.*?)\\]\\]>",
                        java.util.regex.Pattern.DOTALL).matcher(html);
                while (cdataM.find()) {
                    cdataBuf.append(cdataM.group(1));
                }
                if (cdataBuf.length() > 0) {
                    workHtml = cdataBuf.toString();
                    Logger.d("ClearAlarm", "ajax-response格式，CDATA共"
                            + workHtml.length() + "字符");
                } else {
                    Logger.d("ClearAlarm", "ajax-response但无CDATA，用原始内容");
                }
            } else {
                // XHTML 或普通 HTML：直接用原始内容，不做任何转换
                Logger.d("ClearAlarm", "XHTML/HTML响应，直接解析，长度=" + html.length());
            }

            // ── 路径1：selectFlag checkbox ────────────────────────────────────
            // <input type="checkbox" name="selectFlag" value="HEX32" alt="站点名~Y" .../>
            // 先收集所有 selectFlag checkbox 及其在 workHtml 中的位置
            java.util.regex.Pattern sfPat = java.util.regex.Pattern.compile(
                    "<input[^>]+name=[\"']selectFlag[\"'][^>]*/?>",
                    java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher sfM = sfPat.matcher(workHtml);

            java.util.List<String> sfIds       = new java.util.ArrayList<>();
            java.util.List<String> sfStations  = new java.util.ArrayList<>();
            java.util.List<Integer> sfPositions = new java.util.ArrayList<>();

            while (sfM.find()) {
                String tag = sfM.group();
                // 提取 value（alarmId）
                java.util.regex.Matcher vm = java.util.regex.Pattern.compile(
                        "value=[\"']([0-9A-Fa-f]{32})[\"']",
                        java.util.regex.Pattern.CASE_INSENSITIVE).matcher(tag);
                if (!vm.find()) continue;
                String aid = vm.group(1);
                // 注意：alt 格式是 "序号~Y/N~状态"（如 "2~Y~2"），不含站点名
                // 站点名统一从后面的 showStationDetailFunc 链接文本提取
                sfIds.add(aid);
                sfStations.add("");
                sfPositions.add(sfM.start());
                Logger.d("ClearAlarm", "selectFlag[" + (sfIds.size()-1) + "] id="
                        + aid.substring(0, 8) + "...");
            }

            Logger.d("ClearAlarm", "selectFlag总数=" + sfIds.size()
                    + " stationName=" + stationName);

            if (!sfIds.isEmpty()) {
                // 单条结果直接返回（精确站点名查询时预期如此）
                if (sfIds.size() == 1) {
                    Logger.d("ClearAlarm", "selectFlag唯一命中 alarmId=" + sfIds.get(0));
                    return sfIds.get(0);
                }

                // 多条：先从 showStationDetailFunc 链接文本提取站点名，再做匹配
                if (stationName != null && !stationName.isEmpty()) {
                    // 完全相等（此时 sfStations 都是空，先填充）
                    for (int i = 0; i < sfIds.size(); i++) {
                        if (stationName.equals(sfStations.get(i))) {
                            Logger.d("ClearAlarm", "selectFlag完全匹配 alarmId=" + sfIds.get(i));
                            return sfIds.get(i);
                        }
                    }
                    // 先从 checkbox 周围 HTML 提取真实站点名（showStationDetailFunc 链接文本）
                    // alt="序号~Y/N~状态" 不含站点名，必须从链接文本里取
                    java.util.regex.Pattern siteNamePat = java.util.regex.Pattern.compile(
                            "showStationDetailFunc\\([^)]+\\)\">([^<]+)</a>",
                            java.util.regex.Pattern.CASE_INSENSITIVE);
                    for (int i = 0; i < sfIds.size(); i++) {
                        int start = sfPositions.get(i);
                        int end   = (i + 1 < sfPositions.size()) ? sfPositions.get(i + 1) : Math.min(start + 8000, workHtml.length());
                        String seg = workHtml.substring(start, end);
                        java.util.regex.Matcher snM = siteNamePat.matcher(seg);
                        if (snM.find()) {
                            String sn = snM.group(1).trim();
                            int paren = sn.indexOf(" (");
                            if (paren > 0) sn = sn.substring(0, paren).trim();
                            sfStations.set(i, sn);
                            Logger.d("ClearAlarm", "selectFlag[" + i + "] station=" + sn);
                        }
                    }
                    // 完全匹配
                    for (int i = 0; i < sfIds.size(); i++) {
                        if (stationName.equals(sfStations.get(i))) {
                            Logger.d("ClearAlarm", "selectFlag站点完全匹配 alarmId=" + sfIds.get(i));
                            return sfIds.get(i);
                        }
                    }
                    // 包含匹配
                    for (int i = 0; i < sfIds.size(); i++) {
                        String s = sfStations.get(i);
                        if (!s.isEmpty() && (s.contains(stationName) || stationName.contains(s))) {
                            Logger.d("ClearAlarm", "selectFlag站点包含匹配 alarmId=" + sfIds.get(i) + " station=" + s);
                            return sfIds.get(i);
                        }
                    }
                    // 后缀匹配
                    for (int i = 0; i < sfIds.size(); i++) {
                        String s = sfStations.get(i);
                        String sTail = s.length() > 4 ? s.substring(4) : s;
                        String qTail = stationName.length() > 4 ? stationName.substring(4) : stationName;
                        if (!sTail.isEmpty() && !qTail.isEmpty()
                                && (sTail.contains(qTail) || qTail.contains(sTail))) {
                            Logger.d("ClearAlarm", "selectFlag后缀匹配 alarmId=" + sfIds.get(i) + " station=" + s);
                            return sfIds.get(i);
                        }
                    }
                }

                // 站点名匹配失败，打日志列出所有站点
                StringBuilder sb = new StringBuilder("selectFlag多条无匹配，站点列表：\n");
                for (int i = 0; i < sfIds.size(); i++) {
                    sb.append(i+1).append(". ").append(sfStations.get(i))
                      .append(" → ").append(sfIds.get(i), 0, 8).append("...\n");
                }
                Logger.d("ClearAlarm", sb.toString());
                return null;
            }

            // ── 路径2：备用 ondblclick="showAlarmDetail('HEX32')" ─────────────
            Logger.d("ClearAlarm", "路径1未命中，尝试路径2 ondblclick");
            java.util.List<String> trAlarmIds = new java.util.ArrayList<>();
            java.util.List<int[]>  trPositions = new java.util.ArrayList<>();
            java.util.regex.Pattern trPat = java.util.regex.Pattern.compile(
                    "<tr[^>]+ondblclick=[\"']showAlarmDetail\\('([0-9A-Fa-f]{32})'\\)[\"']",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher trM = trPat.matcher(workHtml);
            while (trM.find()) {
                trAlarmIds.add(trM.group(1));
                trPositions.add(new int[]{trM.start(), trM.end()});
            }
            Logger.d("ClearAlarm", "路径2 ondblclick行数=" + trAlarmIds.size());

            if (!trAlarmIds.isEmpty()) {
                if (trAlarmIds.size() == 1) return trAlarmIds.get(0);

                // 多行时提取站点名匹配
                java.util.List<String> rowAlarmIds  = new java.util.ArrayList<>();
                java.util.List<String> rowStations  = new java.util.ArrayList<>();
                java.util.regex.Pattern siteNamePat2 = java.util.regex.Pattern.compile(
                        "showStationDetailFunc\\([^)]+\\)\">([^<]+)</a>",
                        java.util.regex.Pattern.CASE_INSENSITIVE);
                for (int i = 0; i < trPositions.size(); i++) {
                    int segStart = trPositions.get(i)[0];
                    int segEnd   = (i + 1 < trPositions.size()) ? trPositions.get(i + 1)[0] : workHtml.length();
                    String seg   = workHtml.substring(segStart, segEnd);
                    java.util.regex.Matcher snM = siteNamePat2.matcher(seg);
                    String sn = "";
                    if (snM.find()) {
                        sn = snM.group(1).trim();
                        int p = sn.indexOf(" (");
                        if (p > 0) sn = sn.substring(0, p).trim();
                    }
                    rowAlarmIds.add(trAlarmIds.get(i));
                    rowStations.add(sn);
                }
                if (stationName != null && !stationName.isEmpty()) {
                    for (int i = 0; i < rowAlarmIds.size(); i++) {
                        if (stationName.equals(rowStations.get(i))) return rowAlarmIds.get(i);
                    }
                    for (int i = 0; i < rowAlarmIds.size(); i++) {
                        String s = rowStations.get(i);
                        if (s.contains(stationName) || stationName.contains(s)) return rowAlarmIds.get(i);
                    }
                    for (int i = 0; i < rowAlarmIds.size(); i++) {
                        String s = rowStations.get(i);
                        String sTail = s.length() > 4 ? s.substring(4) : s;
                        String qTail = stationName.length() > 4 ? stationName.substring(4) : stationName;
                        if (!sTail.isEmpty() && !qTail.isEmpty()
                                && (sTail.contains(qTail) || qTail.contains(sTail))) return rowAlarmIds.get(i);
                    }
                }
            }

            // ── 路径3：openAIAssistant('HEX32','工单号') ──────────────────────
            // 图片截图真实格式：onclick="openAIAssistant('4E067C97...','FS-33-001-20260328-423852')"
            // 第1个参数=alarmId(32位hex)，第2个参数=billsn（工单号，可精确匹配）
            Logger.d("ClearAlarm", "路径3 openAIAssistant格式");
            java.util.regex.Pattern aiPat = java.util.regex.Pattern.compile(
                    "openAIAssistant\\('([0-9A-Fa-f]{32})'\\s*,\\s*'([^']*)'\\)",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher aiM = aiPat.matcher(workHtml);
            java.util.List<String> aiIds   = new java.util.ArrayList<>();
            java.util.List<String> aiBills = new java.util.ArrayList<>();
            while (aiM.find()) {
                aiIds.add(aiM.group(1));
                aiBills.add(aiM.group(2));
            }
            Logger.d("ClearAlarm", "路径3 openAIAssistant数=" + aiIds.size());

            if (!aiIds.isEmpty()) {
                // 优先用工单号精确匹配（billsn）
                if (billsn != null && !billsn.isEmpty()) {
                    for (int i = 0; i < aiIds.size(); i++) {
                        if (billsn.equals(aiBills.get(i))) {
                            Logger.d("ClearAlarm", "路径3 工单号精确匹配 alarmId=" + aiIds.get(i) + " bill=" + aiBills.get(i));
                            return aiIds.get(i);
                        }
                    }
                    // 包含匹配（工单号可能有前缀差异）
                    for (int i = 0; i < aiIds.size(); i++) {
                        String b = aiBills.get(i);
                        if (!b.isEmpty() && (b.contains(billsn) || billsn.contains(b))) {
                            Logger.d("ClearAlarm", "路径3 工单号包含匹配 alarmId=" + aiIds.get(i) + " bill=" + b);
                            return aiIds.get(i);
                        }
                    }
                }
                // 工单号匹配失败 → 单条直接返回
                if (aiIds.size() == 1) {
                    Logger.d("ClearAlarm", "路径3 唯一命中 alarmId=" + aiIds.get(0));
                    return aiIds.get(0);
                }
                // 多条 → 站点名匹配（openAIAssistant 没有直接的站点名，取附近文本）
                if (stationName != null && !stationName.isEmpty()) {
                    java.util.regex.Matcher aiM2 = aiPat.matcher(workHtml);
                    java.util.List<Integer> aiPositions = new java.util.ArrayList<>();
                    while (aiM2.find()) aiPositions.add(aiM2.start());
                    for (int i = 0; i < aiIds.size(); i++) {
                        int segS = aiPositions.get(i);
                        int segE = (i + 1 < aiPositions.size()) ? aiPositions.get(i+1) : Math.min(segS + 5000, workHtml.length());
                        String seg = workHtml.substring(segS, segE);
                        java.util.regex.Matcher snM = java.util.regex.Pattern.compile(
                                "showStationDetailFunc\\([^)]+\\)\">([^<]+)</a>",
                                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(seg);
                        if (snM.find()) {
                            String sn = snM.group(1).trim();
                            int p = sn.indexOf(" (");
                            if (p > 0) sn = sn.substring(0, p).trim();
                            if (stationName.equals(sn) || sn.contains(stationName) || stationName.contains(sn)) {
                                Logger.d("ClearAlarm", "路径3 站点匹配 alarmId=" + aiIds.get(i) + " station=" + sn);
                                return aiIds.get(i);
                            }
                        }
                    }
                }
                // 最后兜底：返回第一条
                Logger.d("ClearAlarm", "路径3 兜底返回第一条 alarmId=" + aiIds.get(0));
                return aiIds.get(0);
            }

        } catch (Exception e) {
            Logger.e("ClearAlarm", "parseAlarmIdFromHtml error: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * 从 HTML 中提取所有指定 name 的 checkbox input 的 value 列表
     */
    private static java.util.List<String> extractCheckboxValues(String html, String name) {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (html == null) return result;
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "(?:name=[\"']" + name + "[\"'][^>]*value=[\"']([^\"']+)[\"']"
                    + "|value=[\"']([^\"']+)[\"'][^>]*name=[\"']" + name + "[\"'])",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(html);
            while (m.find()) {
                String v = m.group(1) != null ? m.group(1) : m.group(2);
                result.add(v);
            }
        } catch (Exception e) {
            Logger.e("ClearAlarm", "extractCheckboxValues error: " + e.getMessage());
        }
        return result;
    }

    /**
     * 从 OMMS 告警列表响应中提取所有行的「站点名 → alarmId」映射，用于调试展示。
     * 注意：OMMS AJAX 实际返回 XHTML（<?xml?><html>），直接解析即可；
     *      仅真正的 <ajax-response> 才提取 CDATA。
     * alt 属性格式是 "序号~isConfirm~状态"，不是站点名，站点名要从 showStationDetailFunc 取。
     */
    private static String extractAlarmRowsDebugInfo(String html) {
        if (html == null || html.isEmpty()) return "响应为空";
        try {
            // 只有真正的 ajax-response 格式才提取 CDATA；XHTML 直接用原始内容
            String workHtml = html;
            if (html.contains("<ajax-response")) {
                StringBuilder cdataBuf = new StringBuilder();
                java.util.regex.Matcher cdataM = java.util.regex.Pattern.compile(
                        "<!\\[CDATA\\[(.*?)\\]\\]>",
                        java.util.regex.Pattern.DOTALL).matcher(html);
                while (cdataM.find()) cdataBuf.append(cdataM.group(1));
                if (cdataBuf.length() > 0) workHtml = cdataBuf.toString();
            }

            // 先找 selectFlag checkbox（主路径）
            // 注意：alt 属性格式是 "序号~Y/N~状态"，不是站点名！站点名要从 showStationDetailFunc 取。
            java.util.regex.Pattern sfPat = java.util.regex.Pattern.compile(
                    "<input[^>]+name=[\"']selectFlag[\"'][^>]*/?>",
                    java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher sfM = sfPat.matcher(workHtml);
            java.util.List<String> sfIds = new java.util.ArrayList<>();
            java.util.List<String> sfStations = new java.util.ArrayList<>();
            java.util.List<Integer> sfPos = new java.util.ArrayList<>();
            while (sfM.find()) {
                String tag = sfM.group();
                java.util.regex.Matcher vm = java.util.regex.Pattern.compile(
                        "value=[\"']([0-9A-Fa-f]{32})[\"']",
                        java.util.regex.Pattern.CASE_INSENSITIVE).matcher(tag);
                if (!vm.find()) continue;
                sfIds.add(vm.group(1));
                sfStations.add("");  // alt 不含站点名，后面从 showStationDetailFunc 补
                sfPos.add(sfM.start());
            }

            if (!sfIds.isEmpty()) {
                // 从 checkbox 周围 HTML 找 showStationDetailFunc 提取站点名
                java.util.regex.Pattern siteNamePat = java.util.regex.Pattern.compile(
                        "showStationDetailFunc\\([^)]+\\)\">([^<]+)</a>",
                        java.util.regex.Pattern.CASE_INSENSITIVE);
                for (int i = 0; i < sfIds.size(); i++) {
                    int start = sfPos.get(i);
                    int end = (i + 1 < sfPos.size()) ? sfPos.get(i + 1) : Math.min(start + 8000, workHtml.length());
                    java.util.regex.Matcher snM = siteNamePat.matcher(workHtml.substring(start, end));
                    if (snM.find()) {
                        String sn = snM.group(1).trim();
                        int p = sn.indexOf(" ("); if (p > 0) sn = sn.substring(0, p);
                        sfStations.set(i, sn);
                    }
                }
                StringBuilder sb = new StringBuilder("OMMS当前告警(" + sfIds.size() + "条)：\n");
                for (int i = 0; i < sfIds.size(); i++) {
                    sb.append(i + 1).append(". ").append(sfStations.get(i))
                      .append(" → ").append(sfIds.get(i), 0, 8).append("...\n");
                }
                return sb.toString();
            }

            // 备用：ondblclick 格式
            java.util.regex.Pattern trPat = java.util.regex.Pattern.compile(
                    "<tr[^>]+ondblclick=[\"']showAlarmDetail\\('([0-9A-Fa-f]{32})'\\)[\"']",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Pattern siteNamePat = java.util.regex.Pattern.compile(
                    "showStationDetailFunc\\([^)]+\\)\">([^<]+)</a>",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher trM = trPat.matcher(workHtml);

            java.util.List<String> ids    = new java.util.ArrayList<>();
            java.util.List<int[]>  pos    = new java.util.ArrayList<>();
            while (trM.find()) {
                ids.add(trM.group(1));
                pos.add(new int[]{trM.start()});
            }

            if (ids.isEmpty()) return "HTML中未找到数据行（共" + html.length() + "字节）";

            StringBuilder sb = new StringBuilder("OMMS当前告警(" + ids.size() + "条)：\n");
            for (int i = 0; i < ids.size(); i++) {
                int segStart = pos.get(i)[0];
                int segEnd   = (i + 1 < pos.size()) ? pos.get(i + 1)[0] : workHtml.length();
                String seg   = workHtml.substring(segStart, segEnd);
                java.util.regex.Matcher snM = siteNamePat.matcher(seg);
                String site = snM.find() ? snM.group(1).trim() : "?";
                int parenIdx = site.indexOf(" (");
                if (parenIdx > 0) site = site.substring(0, parenIdx);
                sb.append(i + 1).append(". ").append(site).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "解析出错: " + e.getMessage();
        }
    }

    /**
     * 从 OMMS 响应 HTML 中提取最新的 javax.faces.ViewState 值。
     *
     * 真实 HTML 里：
     *   <input type="hidden" name="javax.faces.ViewState" id="javax.faces.ViewState" value="j_id12" .../>
     *
     * RichFaces 每次 AJAX 响应都会刷新这个令牌，必须用最新值才能成功提交表单。
     */
    private static String extractViewState(String html) {
        if (html == null || html.isEmpty()) return null;
        try {
            // 优先匹配 ajax-view-state span 内的 hidden input（RichFaces AJAX 响应格式）
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "name=[\"']javax\\.faces\\.ViewState[\"'][^>]*value=[\"']([^\"']+)[\"']"
                    + "|value=[\"']([^\"']+)[\"'][^>]*name=[\"']javax\\.faces\\.ViewState[\"']",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(html);
            String last = null;
            while (m.find()) {
                // 取最后一个（Ajax 响应里可能出现两次，第二个是最新的）
                last = m.group(1) != null ? m.group(1) : m.group(2);
            }
            Logger.d("ClearAlarm", "extractViewState=" + last);
            return last;
        } catch (Exception e) {
            Logger.e("ClearAlarm", "extractViewState error: " + e.getMessage());
            return null;
        }
    }


    /**
     * 判断告警清除是否成功。
     * 
     * 可靠验证方式：清除操作后再查一次告警列表，确认 alarmId 是否真的消失了。
     * 这是最准确的方式，因为 AJAX 响应中 alarmId 可能残留在 ViewState 等字段中，
     * 不能作为判断依据。
     * 
     * 注意：本方法会发起一次额外的网络请求（查询+正则匹配约50-200ms），这是必要的。
     */
    private static boolean isClearSuccess(String clearResp, String alarmId) {
        if (clearResp == null || clearResp.isEmpty()) {
            Logger.w("ClearAlarm", "isClearSuccess: clearResp为空，跳过初步判断");
        } else {
            String lower = clearResp.toLowerCase();
            // 明显失败：响应很小(<500)且含登录关键词
            if (clearResp.length() < 500
                    && (lower.contains("sessionexpired") || lower.contains("uac/login")
                        || lower.contains("dologin") || lower.contains("请先登录"))) {
                Logger.w("ClearAlarm", "isClearSuccess: clearResp含登录页，Cookie失效");
                return false;
            }
            Logger.d("ClearAlarm", "isClearSuccess: clearResp初步分析 len=" + clearResp.length()
                    + " alarmIdContained=" + (alarmId != null && !alarmId.isEmpty() && clearResp.contains(alarmId)));
        }

        // ★★★ 可靠验证：查一次告警列表，确认 alarmId 是否真的消失了 ★★★
        // 即使 AJAX 响应里 alarmId 残留也不影响判断
        if (alarmId == null || alarmId.isEmpty()) {
            Logger.w("ClearAlarm", "isClearSuccess: alarmId为空，无法验证");
            return false;
        }

        try {
            String listResp = WorkOrderApi.getOmmsAlarmList();
            if (listResp == null || listResp.isEmpty()) {
                Logger.w("ClearAlarm", "isClearSuccess: 查询告警列表返回空");
                return false;
            }

            // 列表中仍有这个 alarmId → 清除未成功
            if (listResp.contains(alarmId)) {
                Logger.w("ClearAlarm", "isClearSuccess: 告警列表中仍有alarmId → 清除未成功！");
                return false;
            }

            // alarmId 已从列表中消失 → 清除成功
            Logger.d("ClearAlarm", "isClearSuccess: ✅ 告警列表中无alarmId，验证成功");
            return true;

        } catch (Exception e) {
            Logger.e("ClearAlarm", "isClearSuccess: 查询验证异常: " + e.getMessage());
            // 查询验证失败时，降级为原来的判断方式
            if (alarmId != null && !alarmId.isEmpty() && clearResp != null && clearResp.contains(alarmId)) {
                Logger.w("ClearAlarm", "isClearSuccess: 降级判断——alarmId仍在响应中，判定失败");
                return false;
            }
            Logger.d("ClearAlarm", "isClearSuccess: 降级判断——alarmId已消失，判定成功");
            return true;
        }
    }

    private void runOnUi(Runnable r) {
        ThreadManager.runOnUiThread(r);
    }

    private void showToast(String msg) {
        if (getContext() == null) return;
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
    }

    // ===== 配置区控件公共访问方法 =====
    public CheckBox getCbAutoFeedback()  { return cbAutoFeedback; }
    public CheckBox getCbAutoAccept()    { return cbAutoAccept; }
    public CheckBox getCbAutoRevert()    { return cbAutoRevert; }
    public Button   getBtnStartMonitor() { return btnStartMonitor; }
    public Button   getBtnStopMonitor()  { return btnStopMonitor; }
    public EditText getEtIntervalMin()   { return etIntervalMin; }
    public EditText getEtIntervalMax()   { return etIntervalMax; }
    public EditText getEtFeedbackMin()   { return etFeedbackMin; }
    public EditText getEtFeedbackMax()   { return etFeedbackMax; }
    public EditText getEtAcceptMin()     { return etAcceptMin; }
    public EditText getEtAcceptMax()     { return etAcceptMax; }
}
