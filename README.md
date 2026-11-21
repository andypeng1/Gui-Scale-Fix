# GUI Scale Fix (Minecraft 1.12.2 / Forge)

修复原版界面尺寸（GUI Scale）在非标准分辨率下“调到某些档位却没有效果”的问题，并把该选项改成滑块。

## 问题根因

1. `ScaledResolution` 在计算缩放时要求虚拟分辨率不小于 **320x240**：

   ```java
   while (scaleFactor < guiScale && width / (scaleFactor + 1) >= 320 && height / (scaleFactor + 1) >= 240)
       scaleFactor++;
   ```

   在非标准分辨率/较小窗口下，后面几档会被这个限制吃掉，例如 1024x600 下“普通(2)”和“大(3)”都只会得到 2。

2. 当语言被判定为 Unicode 字体时（中文客户端默认就会这样），还有一个隐藏的“奇数取偶”逻辑：

   ```java
   if (isUnicode && scaleFactor % 2 != 0 && scaleFactor != 1)
       scaleFactor--;
   ```

   也就是 3、5、7 等档位会被悄悄减 1，和相邻档位完全一样。

## 修复方式

- 用一个 coremod（ASM）重写 `ScaledResolution(Minecraft)` 的构造逻辑：
  - `guiScale == 0`（自动）保持原版行为（320x240 规则 + Unicode 奇数取偶），默认外观不变。
  - `guiScale > 0`（手动）**严格使用玩家选择的数值**，不再被 320x240 或 Unicode 奇数逻辑截断。奇数档在 Unicode 字体下文字可能略微发虚，但设置会真正生效。
- 把“界面尺寸”的循环按钮替换成整数滑块：
  - 位置 `0` = 自动（Auto）。
  - `1..N` = 实际缩放倍数；`N` 根据当前窗口大小计算（最小虚拟分辨率 160x120），保证滑块的每一格都会产生可见变化。
  - 松开鼠标时提交数值，并触发表层重新布局。

## 构建

需要：

- Gradle 守护进程必须用 **JDK 25**（RetroFuturaGradle 2.0.0 的插件 class 是 Java 25 字节码，JDK 17 会报 `UnsupportedClassVersionError`）。
- 编译/运行 1.12.2 用 **JDK 8**，路径配置在 `gradle.properties` 的
  `org.gradle.java.installations.paths`。

```bash
./gradlew build
```

产物：

- `build/libs/GuiScaleFix-1.12.2-1.0.0.jar` —— 可放入 `mods/` 的生产 jar（已 reobf 到 SRG）。
- `build/libs/GuiScaleFix-1.12.2-1.0.0-dev.jar` —— 开发环境 jar，无需使用。

在开发环境启动：

```bash
./gradlew runClient
```

（coremod 通过 `-Dfml.coreMods.load` 注册；生产 jar 则通过 manifest 的 `FMLCorePlugin` 注册。）

## 兼容性

- Minecraft 1.12.2
- Forge 14.23.5.2847+（编译基线 2847，`mcmod.info` 声明 `forge@[14.23.5.2847,)`；在 14.23.5.2864 上正常工作）
- 仅客户端
