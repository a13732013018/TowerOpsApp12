package com.towerops.app.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;

/**
 * 统一动画工具类 v2.0
 * 提供简洁、高效、一致的动画效果
 *
 * 核心理念：一行代码实现专业动画
 */
public class AnimationUtils {

    // ═══════════════════════════════════════════════════════════════
    // 常量配置
    // ═══════════════════════════════════════════════════════════════
    private static final long DURATION_SHORT = 120;
    private static final long DURATION_MEDIUM = 200;
    private static final long DURATION_LONG = 300;

    // ═══════════════════════════════════════════════════════════════
    // 淡入淡出动画
    // ═══════════════════════════════════════════════════════════════

    /**
     * 淡入动画
     */
    public static void fadeIn(@NonNull View view) {
        fadeIn(view, DURATION_MEDIUM);
    }

    public static void fadeIn(@NonNull View view, long duration) {
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate()
                .alpha(1f)
                .setDuration(duration)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /**
     * 淡出动画
     */
    public static void fadeOut(@NonNull View view) {
        fadeOut(view, DURATION_MEDIUM);
    }

    public static void fadeOut(@NonNull View view, long duration) {
        view.animate()
                .alpha(0f)
                .setDuration(duration)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setVisibility(View.INVISIBLE);
                    }
                })
                .start();
    }

    /**
     * 切换显示/隐藏
     */
    public static void toggleVisibility(@NonNull View view) {
        if (view.getVisibility() == View.VISIBLE) {
            fadeOut(view);
        } else {
            fadeIn(view);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 缩放动画
    // ═══════════════════════════════════════════════════════════════

    /**
     * 点击缩放反馈 - 快速弹跳效果
     * 模拟真实按钮按压感
     */
    public static void scaleOnClick(@NonNull View view) {
        scaleOnClick(view, 0.92f, 1.05f);
    }

    public static void scaleOnClick(@NonNull View view, float pressScale, float releaseScale) {
        // 按下：快速缩小
        view.animate()
                .scaleX(pressScale)
                .scaleY(pressScale)
                .setDuration(80)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    // 释放：弹性放大回弹
                    view.animate()
                            .scaleX(releaseScale)
                            .scaleY(releaseScale)
                            .setDuration(100)
                            .setInterpolator(new OvershootInterpolator(2f))
                            .withEndAction(() -> {
                                view.animate()
                                        .scaleX(1f)
                                        .scaleY(1f)
                                        .setDuration(60)
                                        .start();
                            })
                            .start();
                })
                .start();
    }

    /**
     * 脉冲动画 - 用于提示注意
     */
    public static void pulse(@NonNull View view) {
        pulse(view, 1.08f);
    }

    public static void pulse(@NonNull View view, float scale) {
        AnimatorSet set = new AnimatorSet();
        set.playSequentially(
                createScaleAnimator(view, 1f, scale, DURATION_SHORT),
                createScaleAnimator(view, scale, 1f, DURATION_SHORT)
        );
        set.start();
    }

    /**
     * 弹跳入场动画
     */
    public static void bounceIn(@NonNull View view) {
        bounceIn(view, DURATION_MEDIUM);
    }

    public static void bounceIn(@NonNull View view, long duration) {
        view.setScaleX(0f);
        view.setScaleY(0f);
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(view, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(view, "scaleX", 0f, 1.1f, 1f),
                ObjectAnimator.ofFloat(view, "scaleY", 0f, 1.1f, 1f)
        );
        set.setDuration(duration);
        set.setInterpolator(new OvershootInterpolator(1.5f));
        set.start();
    }

    // ═══════════════════════════════════════════════════════════════
    // 位移动画
    // ═══════════════════════════════════════════════════════════════

    /**
     * 从底部滑入
     */
    public static void slideInFromBottom(@NonNull View view) {
        slideInFromBottom(view, DURATION_MEDIUM);
    }

    public static void slideInFromBottom(@NonNull View view, long duration) {
        view.setTranslationY(view.getHeight());
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);

        view.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(duration)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /**
     * 滑出到底部
     */
    public static void slideOutToBottom(@NonNull View view) {
        slideOutToBottom(view, DURATION_MEDIUM);
    }

    public static void slideOutToBottom(@NonNull View view, long duration) {
        view.animate()
                .translationY(view.getHeight())
                .alpha(0f)
                .setDuration(duration)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setVisibility(View.GONE);
                        view.setTranslationY(0f);
                    }
                })
                .start();
    }

    /**
     * 从左侧滑入
     */
    public static void slideInFromLeft(@NonNull View view) {
        view.setTranslationX(-view.getWidth());
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);

        view.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(DURATION_MEDIUM)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // ═══════════════════════════════════════════════════════════════
    // 组合动画
    // ═══════════════════════════════════════════════════════════════

    /**
     * 入场动画组合：缩放 + 淡入
     */
    public static void scaleFadeIn(@NonNull View view) {
        view.setScaleX(0.8f);
        view.setScaleY(0.8f);
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(view, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(view, "scaleX", 0.8f, 1f),
                ObjectAnimator.ofFloat(view, "scaleY", 0.8f, 1f)
        );
        set.setDuration(DURATION_MEDIUM);
        set.setInterpolator(new DecelerateInterpolator());
        set.start();
    }

    /**
     * 震动效果 - 用于错误提示
     */
    public static void shake(@NonNull View view) {
        view.animate()
                .translationX(0f)
                .setDuration(50)
                .withStartAction(() -> {
                    // 快速左右震动
                    for (int i = 0; i < 3; i++) {
                        int offset = (i % 2 == 0) ? 10 : -10;
                        view.animate()
                                .translationX(offset)
                                .setDuration(50)
                                .start();
                    }
                })
                .start();
    }

    /**
     * 旋转一圈
     */
    public static void rotateOnce(@NonNull View view) {
        view.animate()
                .rotation(view.getRotation() + 360f)
                .setDuration(DURATION_LONG)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // ═══════════════════════════════════════════════════════════════
    // 列表项动画
    // ═══════════════════════════════════════════════════════════════

    /**
     * 列表项添加动画
     */
    public static void itemAddAnimation(@NonNull View view, int position) {
        view.setAlpha(0f);
        view.setTranslationY(50f);

        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(DURATION_MEDIUM)
                .setStartDelay(position * 30L) // 逐个延迟入场
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /**
     * 列表项移除动画
     */
    public static void itemRemoveAnimation(@NonNull View view, Runnable onEnd) {
        view.animate()
                .alpha(0f)
                .translationX(-view.getWidth())
                .setDuration(DURATION_SHORT)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (onEnd != null) onEnd.run();
                })
                .start();
    }

    // ═══════════════════════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════════════════════

    private static ObjectAnimator createScaleAnimator(View view, float from, float to, long duration) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, "scaleX", from, to);
        animator.setDuration(duration);
        return animator;
    }

    /**
     * 取消视图上的所有动画
     */
    public static void cancelAll(@NonNull View view) {
        view.animate().cancel();
    }

    /**
     * 重置视图的所有变换属性
     */
    public static void reset(@NonNull View view) {
        view.setAlpha(1f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        view.setRotation(0f);
    }
}
