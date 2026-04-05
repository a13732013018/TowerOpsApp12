package com.towerops.app.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.towerops.app.R;
import com.towerops.app.model.Session;
import com.towerops.app.model.TodoItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 我的待办 Fragment
 *
 * - Tab 0：待办（未完成）
 * - Tab 1：已办（已完成）
 * - 短按条目 → 弹出编辑对话框（修改标题/备注/截止时间/紧急程度）
 * - 长按条目 → 切换完成/待办状态
 * - 卡片左侧色条 + 标签颜色区分紧急程度 & 截止状态
 * - 数据通过 Session.saveTodos / loadTodos 持久化到 SharedPreferences（todo_prefs_{userid}）
 */
public class TodoFragment extends Fragment {

    // ── 全量数据（按创建时间倒序） ───────────────────────────────────────
    private final List<TodoItem> allItems    = new ArrayList<>();
    // ── 当前展示的子列表（由 tab 决定） ─────────────────────────────────
    private final List<TodoItem> displayList = new ArrayList<>();

    private RecyclerView   rvTodo;
    private TextView       tvEmpty;
    private TabLayout      tabTodo;
    private TodoAdapter    adapter;

    private int currentTab = 0; // 0=待办, 1=已办

    // ── 生命周期 ────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_todo, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvTodo  = view.findViewById(R.id.rvTodo);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        tabTodo = view.findViewById(R.id.tabTodo);

        // 初始化 RecyclerView
        adapter = new TodoAdapter(displayList,
                this::onItemClick,      // 短按 → 编辑
                this::onItemLongClick,  // 长按 → 切换状态
                this::onDeleteClick);

        rvTodo.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTodo.setAdapter(adapter);

        // Tab 切换
        tabTodo.addTab(tabTodo.newTab().setText("待办"));
        tabTodo.addTab(tabTodo.newTab().setText("已办"));
        tabTodo.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                refreshDisplay();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        // 新增按钮
        view.findViewById(R.id.btnAddTodo).setOnClickListener(v -> showAddDialog());

        // 从持久化加载
        allItems.clear();
        allItems.addAll(Session.get().loadTodos(requireContext()));
        refreshDisplay();
    }

    // ── 展示刷新 ────────────────────────────────────────────────────────

    private void refreshDisplay() {
        displayList.clear();
        boolean showCompleted = (currentTab == 1);
        for (int i = allItems.size() - 1; i >= 0; i--) {
            TodoItem item = allItems.get(i);
            if (item.isCompleted() == showCompleted) {
                displayList.add(item);
            }
        }
        // 待办 Tab 排序：超时在最前，紧急次之，今天到期，其余按创建时间倒序
        if (!showCompleted) {
            displayList.sort((a, b) -> {
                int pa = itemSortPriority(a);
                int pb = itemSortPriority(b);
                if (pa != pb) return Integer.compare(pa, pb); // 数字小的排前面
                return Long.compare(b.getCreateTime(), a.getCreateTime());
            });
        }
        adapter.notifyDataSetChanged();

        // 空状态提示
        if (displayList.isEmpty()) {
            rvTodo.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(showCompleted ? "暂无已办事项" : "暂无待办，点击「+ 新增」添加");
        } else {
            rvTodo.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
        }
    }

    /** 排序权重：数字越小排越前 */
    private int itemSortPriority(TodoItem item) {
        if (item.isOverdue())           return 0; // 超时最优先
        if (item.getPriority() == TodoItem.PRIORITY_URGENT) return 1;
        if (item.isDueToday())          return 2; // 今天到期
        if (item.getPriority() == TodoItem.PRIORITY_HIGH)   return 3;
        return 4;
    }

    // ── 短按：弹出编辑对话框 ──────────────────────────────────────────────

    private void onItemClick(TodoItem item) {
        showEditDialog(item);
    }

    // ── 长按：切换完成/待办状态 ───────────────────────────────────────────

    private void onItemLongClick(TodoItem item) {
        boolean nowCompleted = !item.isCompleted();
        item.setCompleted(nowCompleted);
        item.setCompletedTime(nowCompleted ? System.currentTimeMillis() : 0);
        persist();
        refreshDisplay();

        String msg = nowCompleted ? "已标记为完成 ✓" : "已移回待办";
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    // ── 删除按钮 ────────────────────────────────────────────────────────

    private void onDeleteClick(TodoItem item) {
        new AlertDialog.Builder(requireContext())
                .setTitle("删除确认")
                .setMessage("确定删除「" + item.getTitle() + "」？")
                .setPositiveButton("删除", (d, w) -> {
                    allItems.remove(item);
                    persist();
                    refreshDisplay();
                    Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ── 新增对话框 ───────────────────────────────────────────────────────

    private void showAddDialog() {
        showTodoDialog(null);
    }

    // ── 编辑对话框 ───────────────────────────────────────────────────────

    private void showEditDialog(TodoItem item) {
        showTodoDialog(item);
    }

    /**
     * 统一的新增/编辑对话框
     * @param editItem null = 新增模式；非 null = 编辑模式
     */
    private void showTodoDialog(@Nullable TodoItem editItem) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_todo, null);

        EditText etTitle      = dialogView.findViewById(R.id.etTodoTitle);
        EditText etContent    = dialogView.findViewById(R.id.etTodoContent);
        TextView tvDueTime    = dialogView.findViewById(R.id.tvDueTime);
        TextView btnNormal    = dialogView.findViewById(R.id.btnPriorityNormal);
        TextView btnHigh      = dialogView.findViewById(R.id.btnPriorityHigh);
        TextView btnUrgent    = dialogView.findViewById(R.id.btnPriorityUrgent);

        // 当前选中的优先级和截止时间（用数组包装，方便 lambda 修改）
        final int[]  selectedPriority = { editItem != null ? editItem.getPriority() : TodoItem.PRIORITY_NORMAL };
        final long[] selectedDueTime  = { editItem != null ? editItem.getDueTime()  : 0L };

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());

        // 预填
        if (editItem != null) {
            etTitle.setText(editItem.getTitle());
            etContent.setText(editItem.getContent());
            etTitle.setSelection(etTitle.getText().length());
            if (editItem.getDueTime() > 0) {
                tvDueTime.setText(sdf.format(new Date(editItem.getDueTime())));
            }
        }

        // 刷新优先级按钮外观
        Runnable refreshPriorityBtns = () -> {
            int p = selectedPriority[0];
            // 全部重置为未选中
            btnNormal.setBackgroundResource(R.drawable.bg_priority_unselected);
            btnNormal.setTextColor(0xFF94A3B8);
            btnHigh.setBackgroundResource(R.drawable.bg_priority_unselected);
            btnHigh.setTextColor(0xFF94A3B8);
            btnUrgent.setBackgroundResource(R.drawable.bg_priority_unselected);
            btnUrgent.setTextColor(0xFF94A3B8);
            // 选中当前的
            if (p == TodoItem.PRIORITY_NORMAL) {
                btnNormal.setBackgroundResource(R.drawable.bg_priority_normal_selected);
                btnNormal.setTextColor(0xFFFFFFFF);
            } else if (p == TodoItem.PRIORITY_HIGH) {
                btnHigh.setBackgroundResource(R.drawable.bg_priority_high_selected);
                btnHigh.setTextColor(0xFFFFFFFF);
            } else {
                btnUrgent.setBackgroundResource(R.drawable.bg_priority_urgent_selected);
                btnUrgent.setTextColor(0xFFFFFFFF);
            }
        };
        refreshPriorityBtns.run();

        // 优先级点击事件
        btnNormal.setOnClickListener(v -> { selectedPriority[0] = TodoItem.PRIORITY_NORMAL; refreshPriorityBtns.run(); });
        btnHigh.setOnClickListener(v ->   { selectedPriority[0] = TodoItem.PRIORITY_HIGH;   refreshPriorityBtns.run(); });
        btnUrgent.setOnClickListener(v -> { selectedPriority[0] = TodoItem.PRIORITY_URGENT; refreshPriorityBtns.run(); });

        // 截止时间点击：只选日期，时间固定为当天 23:59:59
        tvDueTime.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (selectedDueTime[0] > 0) cal.setTimeInMillis(selectedDueTime[0]);
            new DatePickerDialog(requireContext(),
                (dp, year, month, day) -> {
                    Calendar c2 = Calendar.getInstance();
                    c2.set(year, month, day, 23, 59, 59);
                    c2.set(Calendar.MILLISECOND, 0);
                    selectedDueTime[0] = c2.getTimeInMillis();
                    tvDueTime.setText(sdf.format(new Date(selectedDueTime[0])));
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show();
        });

        // 长按截止时间 → 清除
        tvDueTime.setOnLongClickListener(v -> {
            selectedDueTime[0] = 0;
            tvDueTime.setText("");
            tvDueTime.setHint("点击选择截止日期");
            Toast.makeText(requireContext(), "截止时间已清除", Toast.LENGTH_SHORT).show();
            return true;
        });

        boolean isEdit = (editItem != null);
        new AlertDialog.Builder(requireContext())
                .setTitle(isEdit ? "编辑待办" : "新增待办")
                .setView(dialogView)
                .setPositiveButton(isEdit ? "保存" : "确定", (d, w) -> {
                    String title   = etTitle.getText().toString().trim();
                    String content = etContent.getText().toString().trim();
                    if (TextUtils.isEmpty(title)) {
                        Toast.makeText(requireContext(), "标题不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (isEdit) {
                        editItem.setTitle(title);
                        editItem.setContent(content);
                        editItem.setPriority(selectedPriority[0]);
                        editItem.setDueTime(selectedDueTime[0]);
                        persist();
                        refreshDisplay();
                        Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show();
                    } else {
                        long now = System.currentTimeMillis();
                        String id = now + "_" + (int)(Math.random() * 1000);
                        TodoItem newItem = new TodoItem(id, title, content, now);
                        newItem.setPriority(selectedPriority[0]);
                        newItem.setDueTime(selectedDueTime[0]);
                        allItems.add(newItem);
                        persist();
                        if (currentTab != 0) {
                            tabTodo.selectTab(tabTodo.getTabAt(0));
                        } else {
                            refreshDisplay();
                        }
                        Toast.makeText(requireContext(), "已添加：" + title, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ── 持久化 ──────────────────────────────────────────────────────────

    private void persist() {
        Session.get().saveTodos(requireContext(), allItems);
    }

    // ════════════════════════════════════════════════════════════════════
    //  内部 RecyclerView Adapter
    // ════════════════════════════════════════════════════════════════════

    private static class TodoAdapter extends RecyclerView.Adapter<TodoAdapter.VH> {

        interface OnItemClick     { void on(TodoItem item); }
        interface OnItemLongClick { void on(TodoItem item); }
        interface OnDeleteClick   { void on(TodoItem item); }

        private final List<TodoItem>   list;
        private final OnItemClick      onItemClick;
        private final OnItemLongClick  onItemLongClick;
        private final OnDeleteClick    onDeleteClick;
        private final SimpleDateFormat sdf    = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
        private final SimpleDateFormat sdfDay = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());

        TodoAdapter(List<TodoItem> list,
                    OnItemClick onItemClick,
                    OnItemLongClick onItemLongClick,
                    OnDeleteClick onDeleteClick) {
            this.list            = list;
            this.onItemClick     = onItemClick;
            this.onItemLongClick = onItemLongClick;
            this.onDeleteClick   = onDeleteClick;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_todo, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            TodoItem item = list.get(position);

            // ── 标题 ──
            h.tvTitle.setText(item.getTitle());

            // ── 备注 ──
            if (!TextUtils.isEmpty(item.getContent())) {
                h.tvContent.setVisibility(View.VISIBLE);
                h.tvContent.setText(item.getContent());
            } else {
                h.tvContent.setVisibility(View.GONE);
            }

            // ── 时间行 ──
            if (item.isCompleted() && item.getCompletedTime() > 0) {
                h.tvTime.setText("完成于 " + sdf.format(new Date(item.getCompletedTime())));
            } else {
                h.tvTime.setText("创建于 " + sdf.format(new Date(item.getCreateTime())));
            }

            // ── 状态圆圈 ──
            if (item.isCompleted()) {
                h.tvStatus.setBackgroundResource(R.drawable.bg_todo_status_done);
                h.tvStatus.setText("✓");
                h.tvStatus.setTextColor(0xFFFFFFFF);
                h.tvTitle.setAlpha(0.45f);
                h.tvTitle.setPaintFlags(h.tvTitle.getPaintFlags()
                        | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                h.tvStatus.setBackgroundResource(R.drawable.bg_todo_status_pending);
                h.tvStatus.setText("");
                h.tvTitle.setAlpha(1.0f);
                h.tvTitle.setPaintFlags(h.tvTitle.getPaintFlags()
                        & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            }

            // ── 颜色逻辑（已完成条目不着色） ──
            if (item.isCompleted()) {
                h.viewPriorityBar.setBackgroundResource(R.drawable.bg_todo_status_done);
                h.tvPriorityTag.setVisibility(View.GONE);
                h.tvDueTag.setVisibility(View.GONE);
            } else {
                // 左侧色条
                if (item.getPriority() == TodoItem.PRIORITY_URGENT) {
                    h.viewPriorityBar.setBackgroundResource(R.drawable.bg_priority_bar_urgent);
                } else if (item.getPriority() == TodoItem.PRIORITY_HIGH) {
                    h.viewPriorityBar.setBackgroundResource(R.drawable.bg_priority_bar_high);
                } else {
                    h.viewPriorityBar.setBackgroundResource(R.drawable.bg_priority_bar_normal);
                }

                // 优先级标签（非普通才显示）
                if (item.getPriority() == TodoItem.PRIORITY_URGENT) {
                    h.tvPriorityTag.setVisibility(View.VISIBLE);
                    h.tvPriorityTag.setText("紧急");
                    h.tvPriorityTag.setBackgroundResource(R.drawable.bg_tag_urgent);
                    h.tvPriorityTag.setTextColor(0xFFDC2626);
                } else if (item.getPriority() == TodoItem.PRIORITY_HIGH) {
                    h.tvPriorityTag.setVisibility(View.VISIBLE);
                    h.tvPriorityTag.setText("重要");
                    h.tvPriorityTag.setBackgroundResource(R.drawable.bg_tag_high);
                    h.tvPriorityTag.setTextColor(0xFFEA580C);
                } else {
                    h.tvPriorityTag.setVisibility(View.GONE);
                }

                // 截止时间标签
                if (item.getDueTime() > 0) {
                    h.tvDueTag.setVisibility(View.VISIBLE);
                    if (item.isOverdue()) {
                        // 超时：红色警示
                        h.tvDueTag.setText("⚠ 已超时 " + sdf.format(new Date(item.getDueTime())));
                        h.tvDueTag.setBackgroundResource(R.drawable.bg_tag_overdue);
                        h.tvDueTag.setTextColor(0xFFDC2626);
                    } else if (item.isDueToday()) {
                        // 今天到期：黄色提醒
                        h.tvDueTag.setText("⏰ 今日到期 " + sdf.format(new Date(item.getDueTime())));
                        h.tvDueTag.setBackgroundResource(R.drawable.bg_tag_today);
                        h.tvDueTag.setTextColor(0xFF854D0E);
                    } else {
                        // 未来截止：蓝灰色
                        h.tvDueTag.setText("📅 " + sdfDay.format(new Date(item.getDueTime())) + " 前");
                        h.tvDueTag.setBackgroundResource(R.drawable.bg_tag_normal);
                        h.tvDueTag.setTextColor(0xFF4F46E5);
                    }
                } else {
                    h.tvDueTag.setVisibility(View.GONE);
                }

                // 卡片整体背景：超时加淡红底
                if (item.isOverdue()) {
                    h.itemView.setCardBackgroundColor(0xFFFFF5F5);
                } else {
                    h.itemView.setCardBackgroundColor(0xFFFFFFFF);
                }
            }

            // ── 点击事件 ──
            h.itemView.setOnClickListener(v -> onItemClick.on(item));
            h.itemView.setOnLongClickListener(v -> {
                onItemLongClick.on(item);
                return true;
            });
            h.btnDelete.setOnClickListener(v -> onDeleteClick.on(item));
        }

        @Override
        public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final View     viewPriorityBar;
            final TextView tvStatus, tvTitle, tvContent, tvTime, tvPriorityTag, tvDueTag, btnDelete;
            final androidx.cardview.widget.CardView itemView;

            VH(@NonNull View v) {
                super(v);
                itemView       = (androidx.cardview.widget.CardView) v;
                viewPriorityBar = v.findViewById(R.id.viewPriorityBar);
                tvStatus       = v.findViewById(R.id.tvStatus);
                tvTitle        = v.findViewById(R.id.tvTitle);
                tvContent      = v.findViewById(R.id.tvContent);
                tvTime         = v.findViewById(R.id.tvTime);
                tvPriorityTag  = v.findViewById(R.id.tvPriorityTag);
                tvDueTag       = v.findViewById(R.id.tvDueTag);
                btnDelete      = v.findViewById(R.id.btnDelete);
            }
        }
    }
}
