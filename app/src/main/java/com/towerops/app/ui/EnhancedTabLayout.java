package com.towerops.app.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.tabs.TabLayout;

import java.util.HashMap;
import java.util.Map;

/**
 * 增强型TabLayout
 * 支持：徽章、动态颜色过渡、选中放大动画
 */
public class EnhancedTabLayout extends TabLayout {

    // 动画配置
    private static final int COLOR_ANIM_DURATION = 200;

    // 画笔
    private Paint badgePaint;

    // 颜色
    private int selectedColor;
    private int unselectedColor;

    // 角标数据
    private Map<Integer, Integer> badgeCounts = new HashMap<>();

    public EnhancedTabLayout(@NonNull Context context) {
        super(context);
        init();
    }

    public EnhancedTabLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EnhancedTabLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // 使用 obtainStyledAttributes 获取主题颜色
        int[] attrs = {android.R.attr.colorPrimary, android.R.attr.textColorPrimary};
        TypedArray ta = getContext().obtainStyledAttributes(attrs);
        selectedColor = ta.getColor(0, Color.parseColor("#2563EB"));
        unselectedColor = ta.getColor(1, Color.GRAY);
        ta.recycle();

        // 设置指示器动画模式
        setSelectedTabIndicatorAnimationMode(TabLayout.INDICATOR_ANIMATION_MODE_ELASTIC);
        setTabIndicatorFullWidth(false);

        // 初始化画笔
        badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgePaint.setColor(Color.RED);

        // 添加Tab选中监听
        addOnTabSelectedListener(new OnTabSelectedListener() {
            @Override
            public void onTabSelected(Tab tab) {
                animateTabColor(tab, true);
                animateTabScale(tab, true);
            }

            @Override
            public void onTabUnselected(Tab tab) {
                animateTabColor(tab, false);
                animateTabScale(tab, false);
            }

            @Override
            public void onTabReselected(Tab tab) {
                // 再次点击的动画效果
            }
        });
    }

    /**
     * 设置Tab徽章数量
     */
    public void setBadgeCount(int position, int count) {
        badgeCounts.put(position, count);
        invalidate();
    }

    /**
     * 清除指定Tab的徽章
     */
    public void clearBadge(int position) {
        badgeCounts.remove(position);
        invalidate();
    }

    /**
     * Tab颜色动画
     */
    private void animateTabColor(Tab tab, boolean selected) {
        int targetColor = selected ? selectedColor : unselectedColor;

        ValueAnimator colorAnim = ValueAnimator.ofObject(new ArgbEvaluator(),
            selected ? unselectedColor : selectedColor,
            targetColor);
        colorAnim.setDuration(COLOR_ANIM_DURATION);
        colorAnim.addUpdateListener(animation -> {
            int color = (int) animation.getAnimatedValue();
            // 通过反射设置文字颜色
            try {
                View tabView = (View) tab.getClass().getMethod("getView").invoke(tab);
                if (tabView != null) {
                    tabView.setBackgroundColor(color);
                }
            } catch (Exception e) {
                // 忽略
            }
        });
        colorAnim.start();
    }

    /**
     * Tab缩放动画
     */
    private void animateTabScale(Tab tab, boolean selected) {
        View tabView = null;
        try {
            tabView = (View) tab.getClass().getMethod("getView").invoke(tab);
        } catch (Exception e) {
            // 忽略
        }

        if (tabView != null) {
            float targetScale = selected ? 1.1f : 1.0f;
            tabView.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(150)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.5f))
                .start();
        }
    }

    /**
     * 切换到指定Tab，带动画
     */
    public void selectTabWithAnimation(int position) {
        Tab tab = getTabAt(position);
        if (tab != null && !tab.isSelected()) {
            tab.select();
        }
    }

    /**
     * 获取当前选中的Tab位置
     */
    public int getSelectedPosition() {
        Tab tab = getSelectedTab();
        return tab != null ? tab.getPosition() : 0;
    }

    /**
     * 设置指示器颜色
     */
    public void setIndicatorColor(int color) {
        setSelectedTabIndicatorColor(color);
    }
}
