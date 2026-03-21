# MikuMikuHook

A Xposed module for HiBy M500, enhancing the built-in Launcher3 with extra controls.

## Features

- Hide apps from the launcher drawer
- Toggle home screen rotation
- Control Miku pet visibility by orientation
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