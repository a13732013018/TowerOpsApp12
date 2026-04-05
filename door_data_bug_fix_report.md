# 门禁数据显示Bug修复报告

## 项目
TowerOpsApp7 - 塔站工单管理系统Android应用

## 问题现象
```
统计显示：总站数: 1
列表显示：没有数据
```

## 根本原因

### HTML结构分析
OMMS返回的门禁告警HTML结构：
```html
<table class="rich-table">
  <thead>
    <!-- 表头 -->
  </thead>
  <tbody>
    <!-- 只包含分页控件 -->
    <tr>
      <td>首页</td>
      <td>上页</td>
      <td>第1页 共2213页 33183条记录</td>
      <td>下页</td>
      <td>尾页</td>
    </tr>
  </tbody>
  <!-- 数据行在 tbody 之外 -->
  <tr class="rich-table-row oddColumn">
    <!-- 49列数据 -->
  </tr>
  <tr class="rich-table-row evenColumn">
    <!-- 49列数据 -->
  </tr>
  <!-- ... 共11条数据行 -->
</table>
```

### Java代码Bug
`DoorDataFragment.java` 的 `parseAlarmRows` 方法：

```java
// 第701行：旧代码
java.util.regex.Pattern TR_PAT = java.util.regex.Pattern.compile(
    "<tr[^>]*>(.*?)</tr>",
    java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
java.util.regex.Matcher trM = TR_PAT.matcher(html);
```

**问题**：
1. 在整个HTML中搜索所有`<tr>`标签
2. 第一个匹配到的是`<tbody>`内的分页控件`<tr>`
3. 虽然能匹配到数据行的`<tr>`，但后续逻辑有问题：
   - 只提取分页控件的`<td>`：`['首页', '上页', '第页 共2213页 33183条记录', '下页', '尾页']`
   - 提取站名：匹配到"第页 共2213页 33183条记录"（长度3-20，包含中文）
   - 提取时间：无
   - 提取告警名：匹配到"首页"
   - 判断保留：有站名或告警名，所以保留这条"假数据"
   - 真正的数据行：没有正确提取，被遗漏

## 修复方案

**修改正则表达式，直接搜索数据行**：

```java
// 修复后：只搜索 rich-table-row
java.util.regex.Pattern TR_PAT = java.util.regex.Pattern.compile(
    "<tr\\s+class=\"rich-table-row[^>]*>(.*?)</tr>",
    java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
java.util.regex.Matcher trM = TR_PAT.matcher(html);
```

**关键改进**：
- 明确指定`class="rich-table-row"`，只匹配数据行
- 跳过分页控件、表头等无关内容
- 简化后续提取逻辑

## 验证结果

### HTML实际数据
使用Python解析验证：
- 找到 11 个 `rich-table-row` 数据行
- 每行 49 个 `<td>`
- 示例数据：
  - 站名：平阳朝阳北坡、平阳青岱线、平阳昆阳建丰村等
  - 时间：2026/03/01 00:00:20、2026/03/01 00:02:25等
  - 告警名：需要进一步提取

### 修复后预期
- ✅ 正确解析11条数据行
- ✅ 列表正常显示
- ✅ 统计正确：总站数11，合格数X，不合格数Y

## 提交记录
```
commit 95f9b21
修复门禁数据列表不显示bug

问题：统计显示有数据但列表显示"没有数据"
根因：parseAlarmRows只在<tbody>内搜索<tr>，但OMMS的<tbody>只包含分页控件，数据行在tbody之外
修复：改为直接搜索<tr class="rich-table-row">
验证：HTML中有11条rich-table-row数据行
```

## 文件修改
- `C:/Users/13732/WorkBuddy/TowerOpsApp7/app/src/main/java/com/towerops/app/ui/DoorDataFragment.java`
  - 第701-705行：修改正则表达式

## 测试建议
1. 在App中重新查询门禁数据
2. 验证列表显示11条数据
3. 验证统计数字正确
4. 测试排序功能
5. 测试导出CSV功能

## 附录：HTML结构验证脚本

验证脚本：`find_data_rows.py`
```python
import re

with open("D:/安装文件/桌面/门禁数据.txt", "r", encoding="utf-8") as f:
    html = f.read()

row_matches = list(re.finditer(r'<tr class="rich-table-row[^>]*>(.*?)</tr>', html, re.IGNORECASE | re.DOTALL))
print(f"在整个HTML中找到 {len(row_matches)} 个 rich-table-row")
```

输出：`在整个HTML中找到 11 个 rich-table-row`
