package com.towerops.app.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;

/**
 * 触摸反馈助手
 * 提供视觉反馈、触觉反馈和动画效果
 */
public class TouchFeedbackHelper {

    private final Context context;
    private final Vibrator vibrator;
    private boolean hapticEnabled = true;
    private float touchScale = 0.95f;

    public TouchFeedbackHelper(@NonNull Context context) {
        this.context = context;
        this.vibrator = getVibrator();
    }

    private Vibrator getVibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vibratorManager.getDefaultVibrator();
        } else {
            return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    /**
     * 为View添加触摸反馈
     */
    public void attachToView(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    onTouchDown(v);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    onTouchUp(v);
                    break;
            }
            return false;
        });
    }

    /**
     * 为ViewGroup添加触摸反馈（包含子View）
     */
    public void attachToViewGroup(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            attachToView(child);
        }
    }

    /**
     * 触摸按下效果
     */
    private void onTouchDown(View view) {
        // 缩放动画
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
            ObjectAnimator.ofFloat(view, "scaleX", 1f, touchScale),
            ObjectAnimator.ofFloat(view, "scaleY", 1f, touchScale)
        );
        set.setDuration(100);
        set.setInterpolator(new OvershootInterpolator(2f));
        set.start();

        // 触觉反馈
        if (hapticEnabled) {
            performHapticFeedback(view);
        }
    }

    /**
     * 触摸抬起效果
     */
    private void onTouchUp(View view) {
        // 恢复动画
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
            ObjectAnimator.ofFloat(view, "scaleX", touchScale, 1.05f, 1f),
            ObjectAnimator.ofFloat(view, "scaleY", touchScale, 1.05f, 1f)
        );
        set.setDuration(200);
        set.setInterpolator(new OvershootInterpolator(1.5f));
        set.start();
    }

    /**
     * 执行触觉反馈
     */
    private void performHapticFeedback(View view) {
        if (vibrator == null || !vibrator.hasVibrator()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 更精细的触觉反馈
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8+ 使用 VibrationEffect
            vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            // 旧版本
            vibrator.vibrate(10);
        }
    }

    /**
     * 设置触摸缩放比例
     */
    public void setTouchScale(float scale) {
        this.touchScale = scale;
    }

    /**
     * 设置触觉反馈开关
     */
    public void setHapticEnabled(boolean enabled) {
        this.hapticEnabled = enabled;
    }

    /**
     * 播放点击反馈（无需关联View）
     */
    public void performClickFeedback() {
        if (hapticEnabled && vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 更强的点击反馈
                if (vibrator.hasAmplitudeControl()) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(15);
            }
        }
    }

    /**
     * 播放成功反馈
     */
    public void performSuccessFeedback() {
        if (hapticEnabled && vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
            } else {
                // 双击震动
                vibrator.vibrate(new long[]{0, 20, 50, 20}, -1);
            }
        }
    }

    /**
     * 播放警告反馈
     */
    public void performWarningFeedback() {
        if (hapticEnabled && vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
            } else {
                vibrator.vibrate(new long[]{0, 30, 100, 30}, -1);
            }
        }
    }

    /**
     * 播放错误反馈
     */
    public void performErrorFeedback() {
        if (hapticEnabled && vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK));
            } else {
                vibrator.vibrate(new long[]{0, 50, 50, 50, 50, 50}, -1);
            }
        }
    }

    /**
     * 自定义震动模式
     */
    public void performCustomPattern(long[] pattern) {
        if (hapticEnabled && vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        }
    }

    /**
     * Ripple触摸效果封装
     */
    public static class RippleEffect {
        public static void applyTo(View view, int rippleColor) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(rippleColor));
            }
            // 使用系统自带的ripple效果
        }
    }

    /**
     * Tab专用触摸反馈
     */
    public static class TabTouchFeedback {
        public static void apply(View tabView, boolean isSelected) {
            if (isSelected) {
                // 选中Tab的触摸效果
                AnimatorSet set = new AnimatorSet();
                set.playTogether(
                    ObjectAnimator.ofFloat(tabView, "scaleX", 1f, 1.1f, 1f),
                    ObjectAnimator.ofFloat(tabView, "scaleY", 1f, 1.1f, 1f)
                );
                set.setDuration(200);
                set.setInterpolator(new OvershootInterpolator());
                set.start();
            } else {
                // 未选中Tab的触摸效果
                AnimatorSet set = new AnimatorSet();
                set.playTogether(
                    ObjectAnimator.ofFloat(tabView, "scaleX", 1f, 0.95f, 1f),
                    ObjectAnimator.ofFloat(tabView, "scaleY", 1f, 0.95f, 1f)
                );
                set.setDuration(150);
                set.setInterpolator(new OvershootInterpolator());
                set.start();
            }
        }
    }
}
