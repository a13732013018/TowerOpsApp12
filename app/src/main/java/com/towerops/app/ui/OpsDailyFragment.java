package com.towerops.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.towerops.app.R;

/**
 * 运维日常 Tab 容器
 * 子Tab：安全打卡 / 日常维修 / 隐患工单 / 门禁数据
 */
public class OpsDailyFragment extends Fragment {

    private static final String[] SUB_TAB_NAMES = {
            "安全打卡",
            "日常维修",
            "隐患工单",
            "门禁数据"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ops_daily, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TabLayout tabOps = view.findViewById(R.id.tabOps);
        ViewPager2 vpOps = view.findViewById(R.id.vpOps);

        vpOps.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                if (position == 1) return new RepairFragment();
                if (position == 2) return new HiddenFragment();
                if (position == 3) return new DoorDataFragment();
                return new SafetyCheckFragment();
            }

            @Override
            public int getItemCount() {
                return SUB_TAB_NAMES.length;
            }
        });

        new TabLayoutMediator(tabOps, vpOps,
                (tab, position) -> tab.setText(SUB_TAB_NAMES[position])
        ).attach();
    }
}
