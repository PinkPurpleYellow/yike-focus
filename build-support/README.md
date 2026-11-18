# 本次 Windows 沙箱构建说明

普通环境请直接使用 Android Studio 或根目录的 Gradle Wrapper，无需使用此目录。

本次执行环境允许读取工程文件，但 JDK 的 `Path.toRealPath()` 无法逐级解析 Documents 下的路径，导致 javac 文件管理器拒绝读取。为完成构建，外部 Gradle init script 用 Eclipse ECJ 3.39.0 替换生成的 Room Java 源码的编译动作。Kotlin 应用代码仍由 Kotlin 2.0.21 编译，APK 的打包、DEX、lint、资源处理和签名仍由 Android Gradle Plugin 执行。

`ecj.init.gradle` 保留了所用的构建兼容方案。将 Android SDK 放在 `工程父目录/tools/android-sdk`，ECJ 放在 `工程父目录/tools/ecj-3.39.0.jar`，或修改脚本中的这两项路径。ECJ 可从 Maven Central 获取：

https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.39.0/ecj-3.39.0.jar

```powershell
.\gradlew.bat -I "$PWD\build-support\ecj.init.gradle" assembleRelease testDebugUnitTest lintRelease assembleDebugAndroidTest
```

此兼容方案仅将自动生成的普通 Java 源码输出为 Java 8 字节码，Android DEX 可正常处理；项目的标准配置仍为 JDK 17。签名仍需根目录的私密 `signing.properties` 和对应密钥库。ECJ 不会打包到应用中。
