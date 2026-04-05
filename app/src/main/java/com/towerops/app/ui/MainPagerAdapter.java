package com.towerops.app.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewPager2适配器 - 用于工单监控、停电监控和智联工单Tab切换
 */
public class MainPagerAdapter extends FragmentStateAdapter {

    private static final int TAB_COUNT = 9;  // 工单/停电/门禁/智联/巡检/数运(含设备离线子Tab)/指标查询(含省内工单子Tab)/运维日常/我的待办
    private final List<Fragment> fragments = new ArrayList<>();

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Fragment fragment;
        switch (position) {
            case 0:
                fragment = WorkOrderFragment.newInstance();  // 工单监控
                break;
            case 1:
                fragment = PowerOutageFragment.newInstance();  // 停电监控
                break;
            case 2:
                fragment = new AccessControlFragment(); // 门禁系统
                break;
            case 3:
                fragment = new ZhilianFragment();  // 智联工单
                break;
            case 4:
                fragment = new XunjianFragment();  // 巡检工单
                break;
            case 5:
                fragment = new ShuyunFragment();   // 数运工单（含设备离线子Tab）
                break;
            case 6:
                fragment = new MetricsFragment();  // 指标查询（含省内工单处理及时率子Tab）
                break;
            case 7:
                fragment = new OpsDailyFragment(); // 运维日常
                break;
            case 8:
                fragment = new TodoFragment();     // 我的待办
                break;
            default:
                fragment = WorkOrderFragment.newInstance();
        }
        // 保存 fragment 引用以便后续获取
        while (fragments.size() <= position) {
            fragments.add(null);
        }
        fragments.set(position, fragment);
        return fragment;
    }

    @Override
    public int getItemCount() {
        return TAB_COUNT;
    }

    /**
     * 根据位置获取 Fragment
     */
    public Fragment getFragment(int position) {
        if (position >= 0 && position < fragments.size()) {
            return fragments.get(position);
        }
        return null;
    }

    /**
     * 获取 PowerOutageFragment 的 adapter
     */
    public PowerOutageAdapter getPowerOutageAdapter() {
        Fragment fragment = getFragment(1);
        if (fragment instanceof PowerOutageFragment) {
            return ((PowerOutageFragment) fragment).getAdapter();
        }
        return null;
    }

    /**
     * 获取 WorkOrderFragment 的 adapter
     */
    public WorkOrderAdapter getWorkOrderAdapter() {
        Fragment fragment = getFragment(0);
        if (fragment instanceof WorkOrderFragment) {
            return ((WorkOrderFragment) fragment).getAdapter();
        }
        return null;
    }
}
