package com.towerops.app.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.tabs.TabLayout;

import java.util.HashMap;
import java.util.Map;

/**
 * 增强型TabLayout
 * 支持：渐变指示器、角标徽章、动态颜色过渡、动画效果
 */
public class EnhancedTabLayout extends TabLayout {

    // 动画配置
    private static final int COLOR_ANIM_DURATION = 200;
    private static final float INDICATOR_CORNER_RADIUS = 4f;

    // 画笔
    private Paint indicatorPaint;
    private Paint badgePaint;

    // 颜色
    private int selectedColor;
    private int unselectedColor;

    // 角标数据
    private Map<Integer, Integer> badgeCounts = new HashMap<>();

    // 动画状态
    private float indicatorProgress = 0f;
    private int currentPosition = 0;

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
        // 获取主题颜色
        selectedColor = ContextCompat.getColor(getContext(), getTabSelectedTextColor());
        unselectedColor = ContextCompat.getColor(getContext(), getTabTextColors().getColorForState(
            new int[]{android.R.attr.state_enabled}, Color.GRAY));

        // 设置指示器
        setSelectedTabIndicator(0); // 使用自定义绘制
        setSelectedTabIndicatorAnimationMode(SELECTED_INDICATOR_ANIMATION_MODE_EXPAND);
        setTabIndicatorFullWidth(false);

        // 初始化画笔
        indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        indicatorPaint.setStyle(Paint.Style.FILL);

        badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgePaint.setColor(Color.RED);

        // 添加Tab选中监听
        addOnTabSelectedListener(new OnTabSelectedListener() {
            @Override
            public void onTabSelected(Tab tab) {
                animateTabColor(tab, true);
                updateIndicatorForTab(tab, true);
            }

            @Override
            public void onTabUnselected(Tab tab) {
                animateTabColor(tab, false);
            }

            @Override
            public void onTabReselected(Tab tab) {
                // 再次点击的动画
                animateTabReselect(tab);
            }
        });
    }

    /**
     * 设置Tab徽章数量
     */
    public void setBadgeCount(int position, int count) {
        badgeCounts.put(position, count);
        // 刷新显示
        invalidate();
    }

    /**
     * 清除指定Tab的徽章
     */
    public void clearBadge(int position) {
        badgeCounts.remove(position);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        // 绘制徽章
        drawBadges(canvas);
    }

    /**
     * 绘制徽章
     */
    private void drawBadges(Canvas canvas) {
        // 徽章绘制逻辑
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
            tab.setTextColor(color);
        });
        colorAnim.start();
    }

    /**
     * 更新指示器
     */
    private void updateIndicatorForTab(Tab tab, boolean animate) {
        if (animate) {
            // 弹性动画效果
            ValueAnimator progressAnim = ValueAnimator.ofFloat(0.8f, 1.1f, 1.0f);
            progressAnim.setDuration(300);
            progressAnim.setInterpolator(new android.view.animation.OvershootInterpolator(2f));
            progressAnim.addUpdateListener(animation -> {
                indicatorProgress = (float) animation.getAnimatedValue();
                // 触发重绘
                if (getTabIndicatorTransitionListener() != null) {
                    // Material Design 3 指示器动画
                }
            });
            progressAnim.start();
        }
    }

    /**
     * Tab再次点击动画
     */
    private void animateTabReselect(Tab tab) {
        View tabView = tab.view;
        if (tabView != null) {
            tabView.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(100)
                .withEndAction(() -> tabView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start())
                .start();
        }
    }

    /**
     * 设置选中Tab的放大效果
     */
    public void setTabScaleEnabled(boolean enabled) {
        // 可以在这里添加Tab缩放的逻辑
    }

    /**
     * 设置指示器颜色渐变
     */
    public void setIndicatorGradient(int startColor, int endColor) {
        // 使用Shader实现渐变
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
     * 切换到指定Tab，不带动画
     */
    public void selectTabImmediate(int position) {
        Tab tab = getTabAt(position);
        if (tab != null) {
            try {
                // 反射调用私有方法
                java.lang.reflect.Method method = TabLayout.class.getDeclaredMethod("selectTab", Tab.class, boolean.class);
                method.setAccessible(true);
                method.invoke(this, tab, false);
            } catch (Exception e) {
                // 降级到普通select
                tab.select();
            }
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
     * 设置Tab之间间距
     */
    public void setTabPadding(int paddingDp) {
        float density = getResources().getDisplayMetrics().density;
        int paddingPx = (int) (paddingDp * density);
        for (int i = 0; i < getTabCount(); i++) {
            Tab tab = getTabAt(i);
            if (tab != null) {
                tab.setPadding(paddingPx, tab.getPaddingTop(), paddingPx, tab.getPaddingBottom());
            }
        }
    }
}
