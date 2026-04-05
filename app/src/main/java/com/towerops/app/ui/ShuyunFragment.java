package com.towerops.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.towerops.app.R;

/**
 * 数运工单Fragment - 主容器，对应"数运工单"Tab
 * 包含三个子Tab：数运监控、数运审核、省内待办
 */
public class ShuyunFragment extends Fragment {

    private static final String TAG = "ShuyunFragment";

    private TabLayout tabLayoutShuyunSub;
    private ViewPager2 viewPagerShuyunSub;
    private ShuyunSubPagerAdapter pagerAdapter;

    private static final String[] TAB_TITLES = {"数运监控", "数运审核", "省内待办", "任务工单", "设备离线"};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shuyun, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupViewPager();
        setupCallbacks();
    }

    private void initViews(View view) {
        tabLayoutShuyunSub = view.findViewById(R.id.tabLayoutShuyunSub);
        viewPagerShuyunSub = view.findViewById(R.id.viewPagerShuyunSub);
    }

    private void setupViewPager() {
        pagerAdapter = new ShuyunSubPagerAdapter(requireActivity());
        viewPagerShuyunSub.setAdapter(pagerAdapter);

        // 关联TabLayout和ViewPager2
        new TabLayoutMediator(tabLayoutShuyunSub, viewPagerShuyunSub, (tab, position) -> {
            tab.setText(TAB_TITLES[position]);
        }).attach();
    }

    private void setupCallbacks() {
        // 设置回调，让监控页面登录后可以同步状态到审核页面
        ShuyunMonitorFragment monitorFragment = pagerAdapter.getMonitorFragment();
        ShuyunAuditFragment auditFragment = pagerAdapter.getAuditFragment();

        if (monitorFragment != null && auditFragment != null) {
            monitorFragment.setCallback(new ShuyunMonitorFragment.ShuyunMonitorCallback() {
                @Override
                public void onLoginStatusChanged(boolean pcLoggedIn, boolean appLoggedIn) {
                    // 登录状态变化时可以更新UI
                }

                @Override
                public void onMonitorStatusChanged(boolean isRunning) {
                    // 监控状态变化
                }
            });

            auditFragment.setCallback(new ShuyunAuditFragment.ShuyunAuditCallback() {
                @Override
                public String getPcToken() {
                    if (monitorFragment != null) {
                        return monitorFragment.getPcToken();
                    }
                    return "";
                }

                @Override
                public String getUserId() {
                    if (monitorFragment != null) {
                        return monitorFragment.getAppUserId();
                    }
                    return "";
                }

                @Override
                public boolean isPcLoggedIn() {
                    if (monitorFragment != null) {
                        return monitorFragment.isPcLoggedIn();
                    }
                    return false;
                }
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}
