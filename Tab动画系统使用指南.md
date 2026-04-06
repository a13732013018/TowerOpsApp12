# Tab切换动画系统 - 使用指南

## 概述

本系统为TowerOpsApp11提供了一套完整的Tab切换动画解决方案，包含6种页面滑动动画、Tab指示器弹性效果、触摸反馈和Fragment内容动画。

## 动画效果列表

### 1. ViewPager2页面滑动动画（6种效果）

| 类型 | 效果描述 | 适用场景 |
|------|----------|----------|
| **DepthPageTransformer** | 深度缩放效果，类似Google Photos | 推荐，视觉层次感强 |
| **ZoomOutPageTransformer** | 缩放+轻微位移 | 简洁流畅 |
| **CubePageTransformer** | 3D立方体旋转切换 | 炫酷，适配器性能要求高 |
| **AccordionPageTransformer** | 折叠展开效果 | 创意展示 |
| **StackPageTransformer** | 卡片堆叠滑动 | 现代卡片式UI |
| **FidgetPageTransformer** | 轻微弹性旋转 | 轻松活泼 |

### 2. Tab动画效果

- **指示器弹性滑动**：Material Design 3弹性指示器动画
- **Tab选中放大**：选中Tab放大1.15倍
- **Tab点击涟漪**：触摸时有缩放反馈
- **颜色渐变过渡**：选中/未选中颜色平滑过渡

### 3. 触摸反馈

- **触觉反馈**：短震动（需设备支持）
- **视觉反馈**：按压缩放动画
- **成功/警告/错误**：不同模式的震动反馈

## 使用方法

### 方法1：代码切换动画

```java
// 在MainActivity中
// 切换到深度缩放效果
((MainActivity) context).setPageAnimation(0);

// 切换到3D旋转效果
((MainActivity) context).setPageAnimation(2);

// 禁用动画
((MainActivity) context).setPageAnimation(-1); // 或 disablePageAnimation()
```

### 方法2：预设快捷方法

```java
// 切换到指定Tab
mainActivity.switchToTab(3);

// 快速跳转（无动画）
mainActivity.switchToTabImmediate(3);

// 切换到下一个/上一个Tab
mainActivity.switchToNextTab();
mainActivity.switchToPrevTab();

// 获取当前Tab位置
int position = mainActivity.getCurrentTabPosition();
```

### 方法3：设置触觉反馈

```java
// 启用/禁用震动反馈
mainActivity.setHapticFeedbackEnabled(true);

// 播放自定义反馈
touchFeedbackHelper.performClickFeedback();    // 点击
touchFeedbackHelper.performSuccessFeedback(); // 成功
touchFeedbackHelper.performWarningFeedback(); // 警告
touchFeedbackHelper.performErrorFeedback();   // 错误
```

## 代码文件说明

| 文件 | 说明 |
|------|------|
| `PageTransformers.java` | 6种页面滑动动画转换器 |
| `TabAnimationHelper.java` | Tab动画增强助手 |
| `TouchFeedbackHelper.java` | 触摸反馈处理器 |
| `FragmentAnimationManager.java` | Fragment动画管理器 |
| `MainActivity.java` | 集成所有动画功能 |
| `anim/*.xml` | Fragment进入/退出动画资源 |

## Fragment内容动画

在Fragment中使用：

```java
// 进入动画
FragmentAnimationManager.animateFragmentEnter(view);

// 列表项交错动画
FragmentAnimationManager.animateRecyclerViewItemsStaggered(recyclerView, itemCount);

// 空状态动画
FragmentAnimationManager.animateEmptyState(emptyView);
```

## 自定义配置

### 修改动画时长

在各个Helper类中修改常量值：

```java
// TabAnimationHelper.java
private static final int TAB_SELECT_ANIM_DURATION = 200;

// PageTransformers.java
private static final float MIN_SCALE = 0.75f;
```

### 修改触摸反馈强度

```java
// TouchFeedbackHelper.java
touchFeedbackHelper.setTouchScale(0.95f); // 按压缩放比例
```

## 性能注意事项

1. **Fragment数量**：offscreenPageLimit=8会保持8个Fragment活跃
2. **动画复杂度**：CubePageTransformer较耗性能，老设备慎用
3. **触觉反馈**：部分设备可能不支持高频震动
4. **内存优化**：动画完成后及时释放资源

## 默认配置

- **页面动画**：DepthPageTransformer（深度缩放）
- **指示器动画**：弹性模式（elastic）
- **触摸反馈**：启用
- **Fragment动画**：启用

## 更新记录

- 2026-04-06：初始版本，包含6种页面动画、Tab动画增强、触摸反馈
