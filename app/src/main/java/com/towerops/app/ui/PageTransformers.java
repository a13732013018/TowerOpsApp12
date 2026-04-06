package com.towerops.app.ui;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.viewpager2.widget.ViewPager2;

/**
 * ViewPager2 页面切换动画转换器集合
 * 提供多种专业的滑动效果
 */
public class PageTransformers {

    /**
     * 深度缩放效果 (Depth Transformer)
     * - 当前页面保持正常
     * - 滑出页面缩小+淡出
     * - 新页面从右侧淡入
     * 类似Google Photos的切换效果
     */
    public static class DepthPageTransformer implements ViewPager2.PageTransformer {
        private static final float MIN_SCALE = 0.75f;
        private static final float MIN_ALPHA = 0.5f;

        @Override
        public void transformPage(@NonNull View page, float position) {
            int pageWidth = page.getWidth();

            if (position < -1) {
                // 页面已经滑出左侧屏幕
                page.setAlpha(0f);
            } else if (position <= 0) {
                // 当前页面（即将滑出）
                page.setAlpha(1f);
                page.setTranslationX(0);
                page.setTranslationZ(0);
                page.setScaleX(1);
                page.setScaleY(1);
            } else if (position <= 1) {
                // 新页面（正在滑入）
                page.setAlpha(1 - position);
                page.setTranslationX(pageWidth * -position);
                // 向下滑动以增加深度感
                page.setTranslationZ(-1);

                float scaleFactor = MIN_SCALE + (1 - MIN_SCALE) * (1 - Math.abs(position));
                page.setScaleX(scaleFactor);
                page.setScaleY(scaleFactor);
            } else {
                // 页面已经滑出右侧屏幕
                page.setAlpha(0f);
            }
        }
    }

    /**
     * 缩放+旋转效果 (Zoom Out Transformer)
     * - 页面缩小并淡出
     * - 添加轻微的Z轴旋转
     */
    public static class ZoomOutPageTransformer implements ViewPager2.PageTransformer {
        private static final float MIN_SCALE = 0.85f;
        private static final float MIN_ALPHA = 0.5f;

        @Override
        public void transformPage(@NonNull View page, float position) {
            int pageWidth = page.getWidth();
            int pageHeight = page.getHeight();

            if (position < -1) {
                page.setAlpha(0f);
            } else if (position <= 1) {
                float scaleFactor = Math.max(MIN_SCALE, 1 - Math.abs(position) * 0.15f);
                float vertMargin = pageHeight * (1 - scaleFactor) / 2;
                float horzMargin = pageWidth * (1 - scaleFactor) / 2;

                if (position < 0) {
                    page.setTranslationX(horzMargin - vertMargin / 2);
                } else {
                    page.setTranslationX(-horzMargin + vertMargin / 2);
                }

                page.setScaleX(scaleFactor);
                page.setScaleY(scaleFactor);

                page.setAlpha(MIN_ALPHA + (scaleFactor - MIN_SCALE) / (1 - MIN_SCALE) * (1 - MIN_ALPHA));
            } else {
                page.setAlpha(0f);
            }
        }
    }

    /**
     * 3D旋转效果 (Cube Transformer)
     * - 页面以3D立方体方式旋转切换
     * - 当前页面沿Y轴旋转翻页
     */
    public static class CubePageTransformer implements ViewPager2.PageTransformer {
        private static final float MIN_SCALE = 0.9f;

        @Override
        public void transformPage(@NonNull View page, float position) {
            if (position < -1) {
                page.setAlpha(0f);
            } else if (position <= 0) {
                page.setAlpha(1f);
                page.setPivotX(page.getWidth());
                page.setRotationY(-90 * position);
                page.setTranslationX(0);
                page.setScaleX(1);
                page.setScaleY(1);
            } else if (position <= 1) {
                page.setAlpha(1f);
                page.setPivotX(0);
                page.setRotationY(90 * position);
                page.setTranslationX(-page.getWidth() * position);
                float scaleFactor = MIN_SCALE + (1 - MIN_SCALE) * (1 - Math.abs(position));
                page.setScaleX(scaleFactor);
                page.setScaleY(scaleFactor);
            } else {
                page.setAlpha(0f);
            }
        }
    }

    /**
     * 折叠效果 (Accordion Transformer)
     * - 页面从右侧边缘折叠展开
     * - 类似于手风琴折叠效果
     */
    public static class AccordionPageTransformer implements ViewPager2.PageTransformer {
        @Override
        public void transformPage(@NonNull View page, float position) {
            if (position < -1) {
                page.setAlpha(0f);
            } else if (position <= 0) {
                page.setAlpha(1f);
                page.setPivotX(page.getWidth());
                page.setScaleY(1 + position * 0.4f);
            } else if (position <= 1) {
                page.setAlpha(1f);
                page.setPivotX(0);
                page.setScaleY(1 - position * 0.4f);
            } else {
                page.setAlpha(0f);
            }
        }
    }

    /**
     * 堆叠效果 (Stack Transformer)
     * - 新页面堆叠在旧页面之上
     * - 类似于ViewPager2默认效果但更明显
     */
    public static class StackPageTransformer implements ViewPager2.PageTransformer {
        @Override
        public void transformPage(@NonNull View page, float position) {
            if (position < -1) {
                page.setAlpha(0f);
            } else if (position <= 0) {
                page.setAlpha(1f);
                page.setTranslationX(0);
                page.setTranslationZ(0);
                page.setScaleX(1);
                page.setScaleY(1);
            } else if (position <= 1) {
                page.setAlpha(1 - position);
                page.setTranslationX(-page.getWidth() * position * 0.3f);
                page.setTranslationZ(-1);

                float scaleFactor = 0.85f + (1 - 0.85f) * (1 - Math.abs(position));
                page.setScaleX(scaleFactor);
                page.setScaleY(scaleFactor);
            } else {
                page.setAlpha(0f);
            }
        }
    }

    /**
     * 弹性效果 (Fidget Spinner Transformer)
     * - 轻微的旋转效果
     * - 增加动感但不过度
     */
    public static class FidgetPageTransformer implements ViewPager2.PageTransformer {
        private static final float MIN_SCALE = 0.9f;

        @Override
        public void transformPage(@NonNull View page, float position) {
            if (position < -1) {
                page.setAlpha(0f);
            } else if (position <= 1) {
                float scaleFactor = Math.max(MIN_SCALE, 1 - Math.abs(position) * 0.2f);
                page.setScaleX(scaleFactor);
                page.setScaleY(scaleFactor);

                // 轻微旋转
                page.setRotationY(position * 8);

                page.setAlpha(1 - Math.abs(position) * 0.3f);
            } else {
                page.setAlpha(0f);
            }
        }
    }

    /**
     * 视差效果 (Parallax Transformer)
     * - 页面内容以不同速度移动
     * - 适合有背景图片的页面
     */
    public static class ParallaxPageTransformer implements ViewPager2.PageTransformer {
        @Override
        public void transformPage(@NonNull View page, float position) {
            if (position < -1 || position > 1) {
                page.setAlpha(0f);
            } else if (position <= 0) {
                page.setAlpha(1f);
                page.setTranslationX(page.getWidth() * -position * 0.5f);
            } else {
                page.setAlpha(1f);
                page.setTranslationX(page.getWidth() * -position * 0.5f);
            }
        }
    }
}
