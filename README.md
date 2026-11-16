# Ruby Transformer

Windows x64 便携版。解压整个 ZIP，双击 `Ruby Transformer.exe`。程序附带 Java 运行时和思源黑体。将 JAR 拖入文件选择区即可读取 Mod 信息和匹配源版本。

选择 Fabric Mod 的 JAR、该文件对应的 Minecraft 版本，以及目标版本。支持 1.21.10、1.21.11、26.1.2、26.2 和 26.3。点击“选择输出并开始”，选择一个新的文件名。为目标游戏实例安装相应的 Fabric Loader 和 Mod 依赖，再将输出 JAR 放入该实例的 mods 文件夹。同一 Mod 只应保留一份。

## 两种切换方式

**本地字节码转换**适用于使用 Fabric 初始化入口、Java 标准库和受支持 Minecraft 公共成员的简单 Mod。程序读取 class 文件，在旧版本的 Intermediary 名称与新版本的游戏符号之间重映射，并检查声明类、访问性、方法描述符和字段类型。随后更新 fabric.mod.json 中的 Minecraft 约束。转换在本机完成。

索引来自这五个版本的客户端。方法行为变化、反射、资源格式、第三方运行环境和服务端专有代码需要单独测试。当前实现对继承、Mixin、access widener、嵌套 JAR、Fabric API 和其他外部依赖采取保守处理，遇到这类输入可能要求使用作者发布包。26.x 降到 1.21.x 时，输入 class 必须能在 Java 21 上加载；Java 25 字节码通常需要源码重编译。

**获取作者发布包**适用于 Modrinth 上已有目标版本的项目。程序向 Modrinth 发送输入文件的 SHA-512，用返回的项目 ID 查询目标 Fabric 正式发布版。下载时校验 SHA-512、Mod ID 及 Minecraft 依赖声明，并显示新包依赖。项目可能同时更新功能和配置格式。未经收录的本地构建、缺少正式目标版本的项目和网络故障会显示错误。

## 命令行

在解压目录的 PowerShell 中运行。路径可以包含空格。

```powershell
.\RubyCLI.exe convert ".\example.jar" 1.21.11 26.3 ".\example-26.3.jar"
.\RubyCLI.exe release ".\example.jar" 26.3 1.21.10 ".\example-1.21.10.jar"
```

退出码 0 表示输出成功。程序保留输入文件，并拒绝覆盖已有目标文件。

## 构建和测试

仓库含应用源码、运行依赖和五个压缩符号索引。在 Windows x64 上使用 JDK 25 和系统 tar 运行 `powershell -ExecutionPolicy Bypass -File scripts/package.ps1`，便携包生成于 dist 目录。

`scripts/BuildIndex.java` 从对应客户端 JAR 读取类头和成员签名。对于两个 1.21.x 版本，生成器还读取 Mojang client_mappings 与 Fabric Intermediary v2 映射。发布包只包含筛选后的索引。

`tests/Matrix.java` 对五个版本生成 Java 21 Fabric 测试 Mod，覆盖 20 个有向组合。每个输出通过 Fabric 初始化接口调用对应真实游戏类中的伪随机数运算，并与预期值对比。测试单独装载入口，未启动游戏客户端。另有字节码版本、缺失方法和源版本不匹配的失败测试。

