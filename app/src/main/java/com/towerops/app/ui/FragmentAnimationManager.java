package com.towerops.app.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment动画管理器
 * 管理ViewPager2中Fragment的进入/退出动画
 */
public class FragmentAnimationManager {

    private final ViewPager2 viewPager;
    private final FragmentManager fragmentManager;
    private FragmentLifecycleListener lifecycleListener;

    public FragmentAnimationManager(@NonNull ViewPager2 viewPager) {
        this.viewPager = viewPager;
        this.fragmentManager = viewPager.getAdapter() != null ?
            ((FragmentStateAdapter) viewPager.getAdapter()).get.FragmentManager() : null;

        setupFragmentLifecycleCallback();
    }

    private void setupFragmentLifecycleCallback() {
        if (fragmentManager != null) {
            lifecycleListener = new FragmentLifecycleListener();
            fragmentManager.registerFragmentLifecycleCallbacks(lifecycleListener, false);
        }
    }

    /**
     * 释放资源
     */
    public void release() {
        if (fragmentManager != null && lifecycleListener != null) {
            fragmentManager.unregisterFragmentLifecycleCallbacks(lifecycleListener);
        }
    }

    /**
     * Fragment生命周期监听器
     */
    private static class FragmentLifecycleListener extends FragmentManager.FragmentLifecycleCallbacks {
        @Override
        public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment f,
                                          @NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onFragmentViewCreated(fm, f, view, savedInstanceState);
            // Fragment创建时的动画
            animateFragmentEnter(view);
        }

        @Override
        public void onFragmentViewDestroyed(@NonNull FragmentManager fm, @NonNull Fragment f) {
            super.onFragmentViewDestroyed(fm, f);
            // Fragment销毁时的清理
        }
    }

    /**
     * Fragment进入动画
     */
    private static void animateFragmentEnter(View view) {
        if (view == null) return;

        // 初始状态
        view.setAlpha(0f);
        view.setTranslationX(50);
        view.setScaleX(0.95f);
        view.setScaleY(0.95f);

        // 动画序列
        AnimatorSet animatorSet = new AnimatorSet();
        AnimatorSet.Builder builder = animatorSet.playSequentially(
            // 第一阶段：快速弹入
            createSpringAnimator(view, "alpha", 0f, 1f),
            createSpringAnimator(view, "translationX", 50, 0f),
            createSpringAnimator(view, "scaleX", 0.95f, 1.02f),
            createSpringAnimator(view, "scaleY", 0.95f, 1.02f)
        );

        // 微调回正常大小
        AnimatorSet adjustSet = new AnimatorSet();
        adjustSet.playTogether(
            ObjectAnimator.ofFloat(view, "scaleX", 1.02f, 1f),
            ObjectAnimator.ofFloat(view, "scaleY", 1.02f, 1f)
        );
        adjustSet.setStartDelay(50);

        animatorSet.playTogether(animatorSet, adjustSet);
        animatorSet.start();
    }

    /**
     * Fragment退出动画
     */
    private static void animateFragmentExit(View view, Runnable onComplete) {
        if (view == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
            ObjectAnimator.ofFloat(view, "alpha", 1f, 0f),
            ObjectAnimator.ofFloat(view, "translationX", 0f, -50),
            ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f),
            ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f)
        );
        animatorSet.setDuration(200);
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onComplete != null) onComplete.run();
            }
        });
        animatorSet.start();
    }

    /**
     * 创建弹性动画
     */
    private static Animator createSpringAnimator(View view, String property, float from, float to) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, property, from, to);
        animator.setDuration(300);
        animator.setInterpolator(new OvershootInterpolator(1.5f));
        return animator;
    }

    /**
     * 设置Fragment内容淡入动画
     */
    public static void setContentFadeIn(View contentView) {
        if (contentView == null) return;

        contentView.setAlpha(0f);
        contentView.animate()
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    /**
     * 设置RecyclerView列表项动画
     */
    public static void animateRecyclerViewItems(View recyclerView, int position) {
        if (recyclerView == null) return;

        View itemView = recyclerView.findViewHolderForAdapterPosition(position) != null ?
            recyclerView.findViewHolderForAdapterPosition(position).itemView : null;

        if (itemView != null) {
            itemView.setAlpha(0f);
            itemView.setTranslationY(50);

            itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .setStartDelay(position * 50) // 交错动画
                .start();
        }
    }

    /**
     * 设置列表项交错动画
     */
    public static void animateRecyclerViewItemsStaggered(View recyclerView, int itemCount) {
        if (recyclerView == null) return;

        for (int i = 0; i < Math.min(itemCount, 20); i++) { // 最多20个
            View itemView = recyclerView.findViewHolderForAdapterPosition(i) != null ?
                recyclerView.findViewHolderForAdapterPosition(i).itemView : null;

            if (itemView != null) {
                final int delay = i * 30;
                itemView.setAlpha(0f);
                itemView.setTranslationY(30);
                itemView.setRotation(-5);

                itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .rotation(0f)
                    .setDuration(350)
                    .setInterpolator(new OvershootInterpolator(0.8f))
                    .setStartDelay(delay)
                    .start();
            }
        }
    }

    /**
     * Tab切换时的内容动画
     */
    public static void animateContentOnTabSwitch(View contentView, boolean entering) {
        if (contentView == null) return;

        if (entering) {
            contentView.setAlpha(0f);
            contentView.setScaleX(0.9f);
            contentView.setScaleY(0.9f);

            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                ObjectAnimator.ofFloat(contentView, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(contentView, "scaleX", 0.9f, 1f),
                ObjectAnimator.ofFloat(contentView, "scaleY", 0.9f, 1f)
            );
            set.setDuration(280);
            set.setInterpolator(new DecelerateInterpolator());
            set.start();
        } else {
            contentView.animate()
                .alpha(0f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(150)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
        }
    }

    /**
     * 空状态视图动画
     */
    public static void animateEmptyState(View emptyView) {
        if (emptyView == null) return;

        emptyView.setAlpha(0f);
        emptyView.setScaleX(0.8f);
        emptyView.setScaleY(0.8f);

        emptyView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .setInterpolator(new OvershootInterpolator())
            .start();
    }

    /**
     * 加载中状态动画
     */
    public static void animateLoading(View loadingView) {
        if (loadingView == null) return;

        loadingView.setAlpha(1f);
        loadingView.animate()
            .alpha(0.7f)
            .setDuration(800)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .withEndAction(() -> animateLoading(loadingView)) // 循环
            .start();
    }

    /**
     * 停止加载动画
     */
    public static void stopLoadingAnimation(View loadingView) {
        if (loadingView == null) return;
        loadingView.animate().cancel();
        loadingView.setAlpha(1f);
    }
}
