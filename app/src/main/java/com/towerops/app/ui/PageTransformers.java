package com.towerops.app.ui;

import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.viewpager2.widget.ViewPager2;

/**
 * ViewPager2 页面切换动画转换器集合 v2.0
 * 提供更流畅、更丝滑的专业滑动效果
 *
 * 核心理念：让每一次滑动都成为视觉享受
 */
public class PageTransformers {

    // ═══════════════════════════════════════════════════════════════
    // 丝滑视差效果 (Silky Parallax) - 推荐默认使用
    // ═══════════════════════════════════════════════════════════════
    /**
     * 丝滑视差效果 - 现代感十足
     * - 页面以不同速度滑动产生视差
     * - 缩放平滑过渡
     * - 透明度渐变自然
     */
    public static class SilkyParallaxTransformer implements ViewPager2.PageTransformer {
        private static final float MIN_SCALE = 0.88f;
        private static final float MIN_ALPHA = 0.6f;
        private static final float PARALLAX_FACTOR = 0.15f;

        @Override
        public void transformPage(@NonNull View page, float position) {
            if (position < -1f || position > 1f) {
                page.setAlpha(0f);
                return;
            }

            // 缩放：边缘页略微缩小
            float scale = Math.max(MIN_SCALE, 1f - Math.abs(position) * 0.25f);
            page.setScaleX(scale);
            page.setScaleY(scale);

            // 透明度：边缘页渐隐
            float alpha = Math.max(MIN_ALPHA, 1f - Math.abs(position) * 0.8f);
            page.setAlpha(alpha);

            // 视差位移
            float offset = -position * page.getWidth() * PARALLAX_FACTOR;
            page.setTranslationX(offset);

            // Z轴阴影效果
            page.setTranslationZ(-Math.abs(position));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 苹果风弹性效果 (Apple Elastic) - 丝滑且有弹性
    // ═══════════════════════════════════════════════════════════════
    /**
     * 苹果风弹性效果
     * - 缩放+位移+旋转的完美组合
     * - 轻微的3D透视感
     */
    public static class AppleElasticTransformer implements ViewPager2.PageTransformer {
        private static final float MIN_SCALE = 0.9f;

        @Override
        public void transformPage(@NonNull View page, float position) {
            if (position < -1 || position > 1) {
                page.setAlpha(0f);
                return;
            }

            if (position <= 0) {
                // 左侧页面
                page.setAlpha(1f);
                page.setPivotX(page.getWidth());
                page.setRotationY(90 * position);
                page.setTranslationX(0);
            } else {
                // 右侧页面
                page.setAlpha(1f - position);
                page.setPivotX(0);
                page.setRotationY(90 * position);
                page.setTranslationX(-page.getWidth() * position);

                float scale = MIN_SCALE + (1 - MIN_SCALE) * (1 - position);
                page.setScaleX(scale);
                page.setScaleY(scale);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 深度缩放效果 (Depth) - 经典Google Photos风格
    // ═══════════════════════════════════════════════════════════════

    /**
     * 深度缩放效果 - Google Photos风格优化版
     * 特点：当前页保持正常，滑出页缩小淡出，新页从右侧淡入
     */
    public static class DepthPageTransformer implements ViewPager2.PageTransformer {
        private static final float MIN_SCALE = 0.78f;
        private static final float MIN_ALPHA = 0.55f;

        @Override
        public void transformPage(@NonNull View page, float position) {
            int pageWidth = page.getWidth();

            if (position < -1) {
                page.setAlpha(0f);
            } else if (position <= 0) {
                page.setAlpha(1f);
                page.setTranslationX(0);
                page.setScaleX(1);
                page.setScaleY(1);
            } else if (position <= 1) {
                page.setAlpha(1 - position);
                page.setTranslationX(pageWidth * -position);
                page.setTranslationZ(-1);
                float scale = MIN_SCALE + (1 - MIN_SCALE) * (1 - Math.abs(position));
                page.setScaleX(scale);
                page.setScaleY(scale);
            } else {
                page.setAlpha(0f);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 缩放+旋转效果 (Zoom Out) - 轻微3D透视
    // ═══════════════════════════════════════════════════════════════
    /**
     * 缩放+旋转效果 - 页面缩小并淡出，添加轻微透视
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
