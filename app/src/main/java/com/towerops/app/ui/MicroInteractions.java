package com.towerops.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.towerops.app.R;

/**
 * 微交互工具类 v2.0
 * 提供现代化、丝滑的触摸反馈效果
 *
 * 核心理念：让每一次触摸都有回应
 */
public class MicroInteractions {

    // ═══════════════════════════════════════════════════════════════
    // 触摸涟漪效果
    // ═══════════════════════════════════════════════════════════════

    /**
     * 为View添加涟漪触摸效果
     */
    public static void addRippleEffect(@NonNull View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int color = ContextCompat.getColor(view.getContext(), R.color.primary_light);
            view.setBackground(createRippleDrawable(color, null));
        }
    }

    /**
     * 为View添加涟漪效果（带边框）
     */
    public static void addRippleEffect(@NonNull View view, int rippleColor, int bgColor, float cornerRadius) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(cornerRadius);
            bg.setColor(bgColor);
            view.setBackground(createRippleDrawable(rippleColor, bg));
        }
    }

    private static Drawable createRippleDrawable(int color, Drawable content) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new RippleDrawable(
                    ColorStateList.valueOf(color),
                    content,
                    content
            );
        }
        return content != null ? content : new ColorDrawable(color);
    }

    // ═══════════════════════════════════════════════════════════════
    // 卡片悬浮效果
    // ═══════════════════════════════════════════════════════════════

    /**
     * 为CardView添加悬浮效果监听
     */
    public static void setupElevatedCard(@NonNull CardView cardView) {
        cardView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    animatePress(cardView);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    animateRelease(cardView);
                    break;
            }
            return false;
        });
    }

    private static void animatePress(View view) {
        view.animate()
                .scaleX(0.98f)
                .scaleY(0.98f)
                .setDuration(80)
                .start();
    }

    private static void animateRelease(View view) {
        view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(150)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.5f))
                .start();
    }

    // ═══════════════════════════════════════════════════════════════
    // 按钮状态切换
    // ═══════════════════════════════════════════════════════════════

    /**
     * 设置开关按钮样式
     */
    public static void setupToggleButton(@NonNull TextView button, boolean isChecked) {
        Context ctx = button.getContext();
        if (isChecked) {
            button.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_button_primary_gradient));
            button.setTextColor(Color.WHITE);
        } else {
            button.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_tag_inactive));
            button.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
        }
    }

    /**
     * 切换按钮状态（带动画）
     */
    public static void toggleWithAnimation(@NonNull TextView button, boolean isChecked) {
        AnimationUtils.scaleOnClick(button, 0.95f, 1.02f);
        setupToggleButton(button, isChecked);
    }

    // ═══════════════════════════════════════════════════════════════
    // 状态指示器
    // ═══════════════════════════════════════════════════════════════

    /**
     * 创建动态状态点
     */
    public static void pulseStatusDot(@NonNull View dotView) {
        AnimationUtils.pulse(dotView, 1.3f);
    }

    /**
     * 设置状态点颜色
     */
    public static void setStatusColor(@NonNull View dotView, int color) {
        if (dotView.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) dotView.getBackground()).setColor(color);
        } else {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(color);
            dotView.setBackground(drawable);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 进度指示器
    // ═══════════════════════════════════════════════════════════════

    /**
     * 数字递增动画（用于统计数字变化）
     */
    public static void animateNumberChange(@NonNull TextView textView, int from, int to) {
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofInt(from, to);
        animator.setDuration(300);
        animator.addUpdateListener(animation -> {
            textView.setText(String.valueOf(animation.getAnimatedValue()));
        });
        animator.start();
    }

    /**
     * 百分比变化动画
     */
    public static void animatePercentageChange(@NonNull TextView textView, int percent) {
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofInt(0, percent);
        animator.setDuration(500);
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            textView.setText(animation.getAnimatedValue() + "%");
        });
        animator.start();
    }

    // ═══════════════════════════════════════════════════════════════
    // 加载骨架屏
    // ═══════════════════════════════════════════════════════════════

    /**
     * 显示骨架屏加载占位
     */
    public static void showSkeleton(@NonNull View skeletonView) {
        skeletonView.setVisibility(View.VISIBLE);
        AnimationUtils.fadeIn(skeletonView, 150);
    }

    /**
     * 隐藏骨架屏
     */
    public static void hideSkeleton(@NonNull View skeletonView) {
        AnimationUtils.fadeOut(skeletonView, 150);
    }

    /**
     * 创建骨架屏渐变Drawable
     */
    public static GradientDrawable createSkeletonDrawable(int width, int height, float cornerRadius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(cornerRadius);

        // 创建渐变颜色模拟骨架效果
        int[] colors = new int[]{
                Color.parseColor("#E8EAF0"),
                Color.parseColor("#F5F6FA"),
                Color.parseColor("#E8EAF0")
        };
        drawable.setColors(colors);
        drawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);

        drawable.setSize(width, height);
        return drawable;
    }

    // ═══════════════════════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 批量设置涟漪效果
     */
    public static void addRippleToChildren(@NonNull ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.isClickable() || child.isFocusable()) {
                addRippleEffect(child);
            }
        }
    }

    /**
     * 创建圆角背景
     */
    public static GradientDrawable createRoundedBackground(int color, float cornerRadius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(cornerRadius);
        drawable.setColor(color);
        return drawable;
    }

    /**
     * 创建渐变背景
     */
    public static GradientDrawable createGradientBackground(int startColor, int endColor, float cornerRadius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(cornerRadius);
        drawable.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        drawable.setColors(new int[]{startColor, endColor});
        return drawable;
    }
}
