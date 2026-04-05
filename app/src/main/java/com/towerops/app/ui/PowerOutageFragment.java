package com.towerops.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.model.PowerOutage;

import java.util.List;

/**
 * 停电监控Fragment
 */
public class PowerOutageFragment extends Fragment {

    private PowerOutageAdapter adapter;
    private RecyclerView recyclerView;
    private TextView btnSortVoltage, btnSortLoadCurrent, btnSortAlarmTime;

    public static PowerOutageFragment newInstance() {
        return new PowerOutageFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_power_outage, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.recyclerPowerOutages);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            adapter = new PowerOutageAdapter();
            recyclerView.setAdapter(adapter);
        }
        setupSortButtons(view);
    }

    /**
     * 设置排序按钮点击事件
     */
    private void setupSortButtons(View view) {
        if (adapter == null) return;

        btnSortVoltage = view.findViewById(R.id.btnSortVoltage);
        btnSortLoadCurrent = view.findViewById(R.id.btnSortLoadCurrent);
        btnSortAlarmTime = view.findViewById(R.id.btnSortAlarmTime);

        // 按钮背景资源
        int bgPrimary = R.drawable.bg_tag_primary;
        int bgSecondary = R.drawable.bg_tag_secondary;

        // 电压排序按钮
        btnSortVoltage.setOnClickListener(v -> {
            if (adapter == null) return;
            PowerOutageAdapter.SortMode cur = adapter.getSortMode();
            if (cur == PowerOutageAdapter.SortMode.VOLTAGE_DESC) {
                adapter.setSortMode(PowerOutageAdapter.SortMode.VOLTAGE_ASC);
            } else {
                adapter.setSortMode(PowerOutageAdapter.SortMode.VOLTAGE_DESC);
            }
            // 更新按钮样式
            updateSortButtonStyles(btnSortVoltage, btnSortLoadCurrent, btnSortAlarmTime, bgPrimary, bgSecondary);
        });

        // 负载电流排序按钮
        btnSortLoadCurrent.setOnClickListener(v -> {
            if (adapter == null) return;
            PowerOutageAdapter.SortMode cur = adapter.getSortMode();
            if (cur == PowerOutageAdapter.SortMode.LOAD_CURRENT_DESC) {
                adapter.setSortMode(PowerOutageAdapter.SortMode.LOAD_CURRENT_ASC);
            } else {
                adapter.setSortMode(PowerOutageAdapter.SortMode.LOAD_CURRENT_DESC);
            }
            // 更新按钮样式
            updateSortButtonStyles(btnSortVoltage, btnSortLoadCurrent, btnSortAlarmTime, bgPrimary, bgSecondary);
        });

        // 告警时间排序按钮
        btnSortAlarmTime.setOnClickListener(v -> {
            if (adapter == null) return;
            PowerOutageAdapter.SortMode cur = adapter.getSortMode();
            if (cur == PowerOutageAdapter.SortMode.ALARM_TIME_DESC) {
                adapter.setSortMode(PowerOutageAdapter.SortMode.ALARM_TIME_ASC);
            } else {
                adapter.setSortMode(PowerOutageAdapter.SortMode.ALARM_TIME_DESC);
            }
            // 更新按钮样式
            updateSortButtonStyles(btnSortVoltage, btnSortLoadCurrent, btnSortAlarmTime, bgPrimary, bgSecondary);
        });
    }

    /** 更新排序按钮样式，高亮当前选中的排序按钮 */
    private void updateSortButtonStyles(TextView btnVoltage, TextView btnLoadCurrent, TextView btnAlarmTime,
                                         int bgPrimary, int bgSecondary) {
        if (adapter == null) return;
        PowerOutageAdapter.SortMode cur = adapter.getSortMode();

        // 先全部设为次要样式
        btnVoltage.setBackgroundResource(bgSecondary);
        btnVoltage.setTextColor(requireContext().getColor(R.color.text_secondary));
        btnLoadCurrent.setBackgroundResource(bgSecondary);
        btnLoadCurrent.setTextColor(requireContext().getColor(R.color.text_secondary));
        btnAlarmTime.setBackgroundResource(bgSecondary);
        btnAlarmTime.setTextColor(requireContext().getColor(R.color.text_secondary));

        // 当前选中的按钮设为主要样式
        switch (cur) {
            case VOLTAGE_DESC:
            case VOLTAGE_ASC:
                btnVoltage.setBackgroundResource(bgPrimary);
                btnVoltage.setTextColor(requireContext().getColor(R.color.text_inverse));
                btnVoltage.setText(cur == PowerOutageAdapter.SortMode.VOLTAGE_ASC ? "电压 ↑" : "电压 ↓");
                break;
            case LOAD_CURRENT_DESC:
            case LOAD_CURRENT_ASC:
                btnLoadCurrent.setBackgroundResource(bgPrimary);
                btnLoadCurrent.setTextColor(requireContext().getColor(R.color.text_inverse));
                btnLoadCurrent.setText(cur == PowerOutageAdapter.SortMode.LOAD_CURRENT_ASC ? "负载电流 ↑" : "负载电流 ↓");
                break;
            case ALARM_TIME_DESC:
            case ALARM_TIME_ASC:
                btnAlarmTime.setBackgroundResource(bgPrimary);
                btnAlarmTime.setTextColor(requireContext().getColor(R.color.text_inverse));
                btnAlarmTime.setText(cur == PowerOutageAdapter.SortMode.ALARM_TIME_ASC ? "告警时间 ↑" : "告警时间 ↓");
                break;
        }
    }

    /**
     * 设置停电数据（同时重置排序为电压升序）
     */
    public void setData(List<PowerOutage> powerOutages) {
        if (adapter != null) {
            adapter.setData(powerOutages);
        }
    }

    /**
     * 清空列表数据（停止监控时调用）
     */
    public void clearData() {
        if (adapter != null) {
            adapter.clearData();
        }
    }



    /**
     * 更新单条状态
     */
    public void updateStatus(int rowIndex, String billsn, String content) {
        if (adapter != null) {
            adapter.updateStatus(rowIndex, billsn, content);
        }
    }

    /**
     * 获取Adapter
     */
    public PowerOutageAdapter getAdapter() {
        return adapter;
    }

    /**
     * 设置Adapter(由Activity注入)
     */
    public void setAdapter(PowerOutageAdapter adapter) {
        this.adapter = adapter;
        // 如果RecyclerView已经初始化,立即设置adapter
        if (recyclerView != null) {
            recyclerView.setAdapter(adapter);
        }
    }
}
