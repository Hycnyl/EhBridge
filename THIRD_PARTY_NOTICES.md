# Third-party notices / 第三方声明

EhBridge's original code is licensed under GPL-3.0-only; see [LICENSE](LICENSE). Existing third-party copyright and license notices remain applicable to their respective files.

EhBridge 原创代码采用 GPL-3.0-only，见 [LICENSE](LICENSE)。第三方文件原有的版权和许可声明继续适用于相应文件。

## Gradle Wrapper

The Gradle Wrapper JAR and launch scripts (`gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`) are distributed with this source snapshot. Copyright notices are retained in the original files. These components use the Apache License, Version 2.0; a copy is provided in [licenses/Apache-2.0.txt](licenses/Apache-2.0.txt).

本源码快照附带 Gradle Wrapper JAR 和启动脚本，保留其原始版权声明。上述组件适用 Apache License 2.0，协议副本见上方链接。

Upstream / 上游：https://github.com/gradle/gradle

## Build dependencies / 构建依赖

AndroidX, Jetpack Compose, Kotlin, kotlinx and test libraries are resolved through Gradle rather than vendored as source in this repository. Their respective licenses continue to apply. Dependency coordinates are listed in `app/build.gradle.kts` and `build.gradle.kts`.

AndroidX、Jetpack Compose、Kotlin、kotlinx 及测试库通过 Gradle 获取，未将其源码作为本项目源码打包；各自许可继续适用。依赖坐标见上述构建文件。
