# ReminderApp v0 视觉 Demo

## 打开方式

直接打开 `index.html`，或在仓库根目录启动本地静态服务：

```bash
python3 -m http.server 8765
```

然后访问：

```text
http://localhost:8765/design/v0/index.html
```

## 页面

- 首页
- 整理模式
- 新建 / 编辑
- 已删除
- 设置
- 诊断
- 强提醒

左侧切换页面；右侧可关闭动效，用于检查 reduced-motion 下的视觉结果。

## 文件

- `index.html`：可点击的 v0 视觉原型。
- `brand-spec.md`：颜色、字体、间距、图标、动效和页面应用规范。

本 Demo 是 Android Compose 的 v0 视觉基线；v1.17 已将首页排序入口、紧凑列表、相对日期、琥珀色主题和强提醒页面核心文案落到 APK。原型仍不改变提醒调度和权限逻辑，未覆盖所有真机厂商差异。
