package com.towerops.app.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;

import java.lang.reflect.Field;

/**
 * Tab动画增强助手
 * 提供弹性指示器动画、Tab选中放大效果、触摸反馈
 */
public class TabAnimationHelper {

    private final TabLayout tabLayout;
    private final ViewPager2 viewPager;
    private int lastSelectedPosition = 0;
    private boolean isUserScrolling = false;

    // 动画时长配置
    private static final int INDICATOR_ANIM_DURATION = 300;
    private static final int TAB_SELECT_ANIM_DURATION = 200;
    private static final float TAB_SELECTED_SCALE = 1.15f;
    private static final float TAB_NORMAL_SCALE = 1.0f;

    public TabAnimationHelper(@NonNull TabLayout tabLayout, @NonNull ViewPager2 viewPager) {
        this.tabLayout = tabLayout;
        this.viewPager = viewPager;
        setupAnimations();
    }

    private void setupAnimations() {
        // 设置ViewPager2页面切换监听
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    isUserScrolling = true;
                    animateTabIndicator(true); // 开始拖动时启用平滑动画
                } else if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    isUserScrolling = false;
                    // 滚动结束后，确保指示器到位
                    updateIndicatorPosition(viewPager.getCurrentItem(), false);
                }
            }

            @Override
            public void onPageSelected(int position) {
                // Tab选中动画
                animateTabSelection(lastSelectedPosition, position);
                animateIndicatorToTab(position);
                lastSelectedPosition = position;
            }

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                if (isUserScrolling && tabLayout.getTabCount() > position + 1) {
                    // 滑动过程中实时更新指示器位置
                    TabLayout.Tab currentTab = tabLayout.getTabAt(position);
                    TabLayout.Tab nextTab = tabLayout.getTabAt(position + 1);
                    if (currentTab != null && nextTab != null) {
                        View currentView = currentTab.getCustomView();
                        View nextView = nextTab.getCustomView();
                        if (currentView != null && nextView != null) {
                            // 计算指示器应该移动的距离
                            float offset = positionOffset;
                            // 应用到指示器位置
                            moveIndicatorWithOffset(currentTab, nextTab, offset);
                        }
                    }
                }
            }
        });

        // Tab点击监听
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                if (position != viewPager.getCurrentItem()) {
                    // 点击切换页面
                    viewPager.setCurrentItem(position, true);
                }
                // Tab点击涟漪反馈
                animateTabClick(tab);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // 恢复正常大小
                resetTabScale(tab);
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // 再次点击时的反馈动画
                animateTabReselect(tab);
            }
        });
    }

    /**
     * 指示器移动到指定Tab
     */
    private void animateIndicatorToTab(int position) {
        TabLayout.Tab tab = tabLayout.getTabAt(position);
        if (tab != null) {
            tab.select();
        }
    }

    /**
     * 更新指示器位置
     */
    private void updateIndicatorPosition(int position, boolean animate) {
        TabLayout.Tab tab = tabLayout.getTabAt(position);
        if (tab != null) {
            if (animate) {
                tab.select();
            } else {
                // 立即设置，不动画
                try {
                    tab.select();
                } catch (Exception e) {
                    // 忽略
                }
            }
        }
    }

    /**
     * 带偏移量的指示器移动
     */
    private void moveIndicatorWithOffset(TabLayout.Tab currentTab, TabLayout.Tab nextTab, float offset) {
        // 使用TabLayout内置的指示器动画
    }

    /**
     * Tab选中/取消选中动画
     */
    private void animateTabSelection(int oldPosition, int newPosition) {
        // 取消选中的Tab恢复正常
        TabLayout.Tab oldTab = tabLayout.getTabAt(oldPosition);
        if (oldTab != null) {
            animateTabDeselect(oldTab);
        }

        // 选中的Tab放大
        TabLayout.Tab newTab = tabLayout.getTabAt(newPosition);
        if (newTab != null) {
            animateTabSelect(newTab);
        }
    }

    /**
     * Tab选中放大动画
     */
    private void animateTabSelect(TabLayout.Tab tab) {
        View view = tab.getCustomView();
        if (view == null) {
            // 如果没有自定义View，尝试查找默认的TextView
            view = getTabView(tab);
        }
        if (view != null) {
            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", TAB_NORMAL_SCALE, TAB_SELECTED_SCALE),
                ObjectAnimator.ofFloat(view, "scaleY", TAB_NORMAL_SCALE, TAB_SELECTED_SCALE)
            );
            set.setDuration(TAB_SELECT_ANIM_DURATION);
            set.setInterpolator(new OvershootInterpolator(2f));
            set.start();
        }
    }

    /**
     * Tab取消选中恢复正常动画
     */
    private void animateTabDeselect(TabLayout.Tab tab) {
        View view = tab.getCustomView();
        if (view == null) {
            view = getTabView(tab);
        }
        if (view != null) {
            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", view.getScaleX(), TAB_NORMAL_SCALE),
                ObjectAnimator.ofFloat(view, "scaleY", view.getScaleY(), TAB_NORMAL_SCALE)
            );
            set.setDuration(TAB_SELECT_ANIM_DURATION);
            set.setInterpolator(new AccelerateDecelerateInterpolator());
            set.start();
        }
    }

    /**
     * Tab点击涟漪反馈动画
     */
    private void animateTabClick(TabLayout.Tab tab) {
        View view = getTabView(tab);
        if (view != null) {
            // 快速缩放反馈
            AnimatorSet set = new AnimatorSet();
            set.playSequentially(
                ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.9f, 1.1f, 1f),
                ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.9f, 1.1f, 1f)
            );
            set.setDuration(250);
            set.setInterpolator(new OvershootInterpolator());
            set.start();
        }
    }

    /**
     * Tab再次点击反馈
     */
    private void animateTabReselect(TabLayout.Tab tab) {
        View view = getTabView(tab);
        if (view != null) {
            // 轻微弹跳效果
            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                ObjectAnimator.ofFloat(view, "translationY", 0f, -4f, 0f)
            );
            set.setDuration(300);
            set.setInterpolator(new OvershootInterpolator());
            set.start();
        }
    }

    /**
     * 重置Tab大小
     */
    private void resetTabScale(TabLayout.Tab tab) {
        View view = tab.getCustomView();
        if (view == null) {
            view = getTabView(tab);
        }
        if (view != null) {
            view.setScaleX(TAB_NORMAL_SCALE);
            view.setScaleY(TAB_NORMAL_SCALE);
        }
    }

    /**
     * 获取Tab的View
     */
    private View getTabView(TabLayout.Tab tab) {
        try {
            // 通过反射获取Tab的视图
            Field viewField = TabLayout.Tab.class.getDeclaredField("view");
            viewField.setAccessible(true);
            return (View) viewField.get(tab);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 开始/停止拖动时调用
     */
    private void animateTabIndicator(boolean isDragging) {
        // 这里可以添加指示器宽度变化的动画
        // Material TabLayout的指示器会自动跟随
    }

    /**
     * 设置自定义Tab View（需要在TabLayout初始化后调用）
     */
    public void setupCustomTabViews() {
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null) {
                // 为每个Tab设置自定义View
                // 可以在这里添加图标、徽章等
            }
        }
    }

    /**
     * 带弹性效果的指示器移动
     */
    public void animateIndicatorWithBounce(int toPosition) {
        TabLayout.Tab tab = tabLayout.getTabAt(toPosition);
        if (tab != null) {
            tab.select();

            // 额外的弹性反馈
            View indicator = getIndicatorView();
            if (indicator != null) {
                ValueAnimator bounceAnim = ValueAnimator.ofFloat(1f, 1.1f, 0.95f, 1.02f, 1f);
                bounceAnim.setDuration(400);
                bounceAnim.setInterpolator(new OvershootInterpolator(3f));
                bounceAnim.addUpdateListener(animation -> {
                    float scale = (float) animation.getAnimatedValue();
                    indicator.setScaleX(scale);
                });
                bounceAnim.start();
            }
        }
    }

    /**
     * 获取指示器View
     */
    private View getIndicatorView() {
        try {
            Field indicatorField = TabLayout.class.getDeclaredField("indicatorView");
            indicatorField.setAccessible(true);
            return (View) indicatorField.get(tabLayout);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 震动反馈（需要Vibrator权限）
     */
    public void performHapticFeedback() {
        // 可选：添加震动反馈
        // 在支持触觉反馈的设备上效果更好
    }
}
