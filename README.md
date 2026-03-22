# MikuMikuHook

A Xposed module for HiBy M500, enhancing the built-in Launcher3 with extra controls.

|                  English                   |                     中文                     |                    日本語                     |
|:------------------------------------------:|:------------------------------------------:|:------------------------------------------:|
| <img src="doc/setting_en.png" width="200"> | <img src="doc/setting_zh.png" width="200"> | <img src="doc/setting_jp.png" width="200"> |

## Features

- Hide apps from the launcher drawer
- Toggle home screen rotation
- Control Miku pet visibility by orientation
- Set a dedicated wallpaper for landscape mode
- Fix Miku drag boundary in landscape mode

## Requirements

- HiBy M500
- Xposed Framework (LSPosed recommended)
- Scope: `com.android.launcher3`

## Build

```bash
./gradlew assembleRelease
```

## License

[MIT](LICENSE)

---

一个为 HiBy M500 设计的 Xposed 模块，为内置 Launcher3 添加额外功能。

## 功能

- 在启动器中隐藏指定应用
- 开关桌面自动旋转
- 按横竖屏分别控制桌面 Miku 显示
- 为横屏模式单独设置壁纸
- 修复横屏下 Miku 拖拽边界异常

## 环境要求

- HiBy M500
- Xposed 框架（推荐 LSPosed）
- 作用域：`com.android.launcher3`

## 构建

```bash
./gradlew assembleRelease
```

## 许可

[MIT](LICENSE)

---

HiBy M500 向けの Xposed モジュールです。内蔵 Launcher3 に追加機能を提供します。

## 機能

- ランチャーから指定アプリを非表示
- ホーム画面の自動回転トグル
- 縦画面・横画面それぞれでミク表示を制御
- 横向き時に専用壁紙を設定
- 横画面でのミクドラッグ境界バグを修正

## 動作環境

- HiBy M500
- Xposed フレームワーク（LSPosed 推奨）
- スコープ：`com.android.launcher3`

## ビルド

```bash
./gradlew assembleRelease
```

## ライセンス

[MIT](LICENSE)