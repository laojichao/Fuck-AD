# Fuck-AD - 去广告 Xposed 模块

## 项目概述
Fuck-AD 是一个去广告的 Xposed 插件，支持多种广告 SDK 的去除。

## 技术栈
- **开发语言**: Java
- **Hook 框架**: [YukiHookAPI](https://github.com/fankes/YukiHookAPI)
- **UI 组件**: SwipeRefreshLayout

## 环境要求
- Android 7.0+
- 已安装 Xposed 框架 (LSPosed 等)

## 支持的广告类型
- 腾讯广告
- 穿山甲广告
- 快手广告
- 谷歌广告
- 自动点击跳过

## 适配应用
- 最右（开屏广告）
- 番茄小说（听书底部直播间广告）
- 堆糖（开屏广告，应用内信息流广告）
- 酷安（信息流广告）

## 构建命令
```bash
./gradlew assembleRelease
```

## 许可证
Apache-2.0

## 作者
hujiayucc
