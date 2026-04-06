package com.towerops.app.ui;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

/**
 * Fragment动画管理器
 * 处理Fragment进入/退出动画和列表项动画
 */
public class FragmentAnimationManager {

    private final ViewPager2 viewPager;
    private final FragmentManager fragmentManager;

    // 动画时长
    private static final int ENTER_DURATION = 300;
    private static final int EXIT_DURATION = 200;
    private static final int ITEM_DURATION = 150;

    public FragmentAnimationManager(@NonNull ViewPager2 viewPager) {
        this.viewPager = viewPager;
        this.fragmentManager = null;
    }

    /**
     * Fragment进入动画
     */
    public void animateEnter(@NonNull View view) {
        view.setAlpha(0f);
        view.setTranslationX(60f);
        view.setScaleX(0.92f);
        view.setScaleY(0.92f);

        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f);
        ObjectAnimator translateAnim = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 60f, 0f);
        ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.92f, 1f);
        ObjectAnimator scaleYAnim = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.92f, 1f);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(alphaAnim, translateAnim, scaleXAnim, scaleYAnim);
        animatorSet.setDuration(ENTER_DURATION);
        animatorSet.setInterpolator(new OvershootInterpolator(1.2f));
        animatorSet.start();
    }

    /**
     * Fragment退出动画
     */
    public void animateExit(@NonNull View view) {
        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.6f);
        ObjectAnimator translateAnim = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, -30f);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(alphaAnim, translateAnim);
        animatorSet.setDuration(EXIT_DURATION);
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.start();
    }

    /**
     * RecyclerView item进入动画
     */
    public void animateRecyclerViewItem(@NonNull View itemView, int position) {
        itemView.setAlpha(0f);
        itemView.setTranslationY(30f);

        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(itemView, View.ALPHA, 0f, 1f);
        ObjectAnimator translateAnim = ObjectAnimator.ofFloat(itemView, View.TRANSLATION_Y, 30f, 0f);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(alphaAnim, translateAnim);
        animatorSet.setDuration(ITEM_DURATION + position * 30);
        animatorSet.setStartDelay(Math.min(position * 30, 300));
        animatorSet.setInterpolator(new OvershootInterpolator(0.8f));
        animatorSet.start();
    }

    /**
     * 批量动画（staggered）
     */
    public void animateViewsWithStagger(@NonNull View[] views) {
        AnimatorSet.Builder builder = null;

        for (int i = 0; i < views.length; i++) {
            View view = views[i];
            view.setAlpha(0f);
            view.setTranslationY(20f);

            ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f);
            ObjectAnimator translateAnim = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 20f, 0f);

            AnimatorSet itemSet = new AnimatorSet();
            itemSet.playTogether(alphaAnim, translateAnim);
            itemSet.setDuration(ITEM_DURATION);
            itemSet.setStartDelay(i * 50);
            itemSet.setInterpolator(new OvershootInterpolator(1.0f));

            if (builder == null) {
                AnimatorSet outerSet = new AnimatorSet();
                outerSet.play(itemSet);
                builder = outerSet.play(itemSet);
            } else {
                builder.with(itemSet);
            }
        }
    }

    /**
     * 脉冲动画（用于提示）
     */
    public void pulseAnimation(@NonNull View view) {
        ObjectAnimator scaleXUp = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.15f);
        ObjectAnimator scaleYUp = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.15f);
        ObjectAnimator scaleXDown = ObjectAnimator.ofFloat(view, View.SCALE_X, 1.15f, 1f);
        ObjectAnimator scaleYDown = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1.15f, 1f);

        AnimatorSet upSet = new AnimatorSet();
        upSet.playTogether(scaleXUp, scaleYUp);
        upSet.setDuration(100);

        AnimatorSet downSet = new AnimatorSet();
        downSet.playTogether(scaleXDown, scaleYDown);
        downSet.setDuration(100);

        AnimatorSet fullSet = new AnimatorSet();
        fullSet.playSequentially(upSet, downSet);
        fullSet.start();
    }

    /**
     * 震动动画（用于错误提示）
     */
    public void shakeAnimation(@NonNull View view) {
        ObjectAnimator translateX = ObjectAnimator.ofFloat(view, View.TRANSLATION_X,
            0f, 10f, -10f, 10f, -10f, 5f, -5f, 2f, 0f);
        translateX.setDuration(400);
        translateX.setInterpolator(new AccelerateDecelerateInterpolator());
        translateX.start();
    }

    /**
     * 淡入动画
     */
    public void fadeIn(@NonNull View view) {
        view.setAlpha(0f);
        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f);
        alphaAnim.setDuration(ENTER_DURATION);
        alphaAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        alphaAnim.start();
    }

    /**
     * 淡出动画
     */
    public void fadeOut(@NonNull View view) {
        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f);
        alphaAnim.setDuration(EXIT_DURATION);
        alphaAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        alphaAnim.start();
    }

    /**
     * 获取RecyclerView指定位置的item view
     */
    public View getRecyclerViewItem(@NonNull RecyclerView recyclerView, int position) {
        RecyclerView.ViewHolder viewHolder = recyclerView.findViewHolderForAdapterPosition(position);
        return viewHolder != null ? viewHolder.itemView : null;
    }
}
