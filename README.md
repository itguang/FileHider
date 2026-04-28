# File Hider

一款 IntelliJ IDEA 插件，用于在 **项目视图（Project Tree）** 中按名称隐藏指定的文件和目录，**不会**修改磁盘文件，也不会影响索引、版本控制、全局搜索、构建以及已经打开的编辑器标签页。

- 插件 ID：`local.filehider`
- 兼容版本：IntelliJ Platform 2024.2+（`sinceBuild = 242`）
- 构建工具链：Java 21 + Gradle + `org.jetbrains.intellij.platform` 2.14.0

## 功能特性

- **精确名称匹配**：按文件/目录名严格匹配，区分大小写，不支持通配符与路径。
- **两组规则**：
  - **Default rules**：内置默认规则，可一键恢复出厂值。
  - **User rules**：用户自定义规则。
- **规则类型**：每条规则可指定作用范围 —— `FILE`（仅文件）、`DIR`（仅目录）、`BOTH`（两者均匹配）。
- **全局开关**：可在设置页一键启用/禁用整个插件。
- **按项目临时显示**：每个项目可通过 `Show Hidden Files` 切换按钮临时显示被隐藏的文件，便于排查。
- **导入 / 导出**：规则支持以 JSON 格式导入或导出，便于团队共享。
- **安全保护**：项目根、模块根、内容根、库节点等关键节点 **永远不会被隐藏**，避免误配置导致项目树折叠。
- **不影响 “Project Files” 面板**：仅过滤 `Project` 视图，方便在需要时查看完整结构。

## 安装

### 方式一：从源码构建

```bash
./gradlew buildPlugin
```

构建产物位于 `build/distributions/file-hider-<version>.zip`。

在 IDEA 中通过 **Settings → Plugins → ⚙️ → Install Plugin from Disk...** 选择该 zip 文件即可。

### 方式二：开发沙箱启动

```bash
./gradlew runIde
```

会启动一个已加载本插件的沙箱 IDE 实例，便于本地调试。

## 使用方式

### 配置规则

进入 **Settings (Preferences) → Tools → File Hider**：

1. 勾选 **Enable File Hider** 启用插件。
2. 在 **Default rules** 或 **User rules** 标签页中：
   - **Add**：添加一行规则，填入名称并选择 `FILE` / `DIR` / `BOTH`。
   - **Remove**：删除选中的规则。
   - **Import JSON / Export JSON**：与 JSON 文件交换规则。
   - **Reset to factory**（仅 Default rules 可见）：恢复内置默认规则。
3. 点击 **Apply** 后，所有打开项目的项目视图会立即刷新。

> 规则不允许包含 `/`、`\`、`*`、`?`，也不允许在同一规则组内重复。

### 出厂默认规则

```text
.classpath        FILE
.factorypath      FILE
.project          FILE
flattened-pom.xml FILE
```

### JSON 规则格式

```json
[
  { "name": ".idea",   "type": "DIR" },
  { "name": "target",  "type": "DIR" },
  { "name": "*.iml",   "type": "FILE" }
]
```

> 注意：示例中的 `*.iml` 包含 `*`，导入时会被校验拒绝；本插件不支持通配符，请使用精确文件名。

### 临时显示被隐藏的文件

在 **Project 工具窗口** 右上角的视图选项菜单中，勾选 **Show Hidden Files** 即可在当前项目中临时显示所有被规则匹配到的文件 / 目录。该状态为项目级、不会持久化，重启 IDE 后会重置。

## 工作原理

插件由三部分协作组成（声明在 `src/main/resources/META-INF/plugin.xml`）：

1. **`FileHiderSettings`**（Application 级 `PersistentStateComponent`）
   - 持久化文件：`fileHider.xml`
   - 维护 `enabled` + `defaultRules` + `userRules`，每次更新时归一化（去空白、去重、丢弃非法名称），并构建一份不可变的 `RuleSnapshot`（按文件名 / 目录名拆分为两个 `Set<String>`）。
   - 通过应用消息总线发布 `FileHiderSettingsChanged` 事件。
2. **`FileHiderTreeStructureProvider`**（`treeStructureProvider` 扩展点，`order="last"`）
   - 在项目视图渲染子节点时按 `RuleSnapshot` 做精确名称过滤。
   - 跳过：项目级 “Show Hidden Files” 开启时、`ProjectFilesPane` 面板、项目/模块/库/内容根节点、内容根之外的文件、库内文件。
3. **`ProjectViewRefreshListener`**
   - 监听设置变更事件，在 EDT 上刷新所有已打开项目的 `ProjectView`，让新规则立即生效。

## 开发指南

### 常用命令

```bash
./gradlew buildPlugin          # 打包插件
./gradlew runIde               # 启动沙箱 IDE
./gradlew test                 # 运行所有单元测试
./gradlew verifyPlugin         # 兼容性校验
```

运行单个测试：

```bash
./gradlew test --tests "local.filehider.settings.FileHiderSettingsTest.normalizeRulesTrimsAndDeduplicatesWithinGroup"
```

### 项目结构

```
src/
├── main/
│   ├── java/local/filehider/
│   │   ├── actions/ShowHiddenFilesAction.java        # 项目级临时显示开关
│   │   ├── settings/FileHiderSettings.java           # 持久化 + RuleSnapshot
│   │   ├── settings/FileHiderConfigurable.java       # 设置页 UI
│   │   ├── settings/FileHiderRule.java / RuleType    # 规则模型
│   │   ├── settings/RuleValidator.java               # 名称校验
│   │   ├── settings/FileHiderSettingsListener.java
│   │   ├── settings/ProjectViewRefreshListener.java  # 设置变更后刷新视图
│   │   └── tree/FileHiderTreeStructureProvider.java  # 项目树过滤
│   └── resources/META-INF/plugin.xml
└── test/java/local/filehider/settings/FileHiderSettingsTest.java
```

### 注意事项

- 规则匹配仅作用于 **项目视图渲染**，不会改动磁盘、索引、VCS、搜索或构建。
- 规则采用 **精确名称匹配**，不支持通配符或路径片段。如需扩展通配符，请同时调整 `RuleSnapshot` 的数据结构与查询逻辑。
- `FileHiderTreeStructureProvider` 必须保持只读、零副作用，且不得在 EDT 上做阻塞操作。
- `buildSearchableOptions` 已在 `build.gradle.kts` 中禁用，以避免每次构建都启动无头 IDE，请勿随意开启。

## License

本仓库未声明开源协议，如需对外发布请先补充 License 文件。
