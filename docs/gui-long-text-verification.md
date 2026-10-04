# TACZWorkshop GUI text verification

## English

Five existing Forge 1.20.1 pages now use KineticCore's bounded scrolling text for fixed-space labels, headings, counts, statuses and summaries. Data names and item IDs stop four pixels before the first header button. Error warnings keep their Component styling. Existing wrapped descriptions, item rendering, numerical overlays and save behavior are unchanged.

The isolated branch is codex/gui-long-text-20261004, based on 71fb46d. The original checkout has unrelated changes and is left untouched. The existing ForgeGradle architecture remains in place; Java 21 and minimum Core 26.10.3 are necessary to compile against the available scrolling API.

### Build and artifact

- Offline build and separate runtimeValidationJar passed. The final pre-commit build took six seconds. Architecture and final-JAR Core reference checks passed with zero reported reference problems.
- English and Chinese source language keys: 632 each; no language text was changed. Source and packaged language validation pass.
- Release JAR: taczworkshop-26.10.4.jar, 93 classes, all class version 65, one refmap and the original MixinConfigs entry. The validation mod is a separate JAR and contributes zero release classes.
- SHA-256: A83C438EE0CB346E3947058355EB6049E481707A3495C273D8BF396FA1530524.
- No dedicated addon Mixin target checker exists in this baseline. Runtime logs are checked separately; this report does not claim such a checker ran.

### Runtime evidence

The first full-pack launch, with its existing 8G setting, failed before GUI validation because Windows could not commit more native memory. The pack's addon, options, 5,390 backed-up config files and original world/core hashes were verified after restoration; five changed config files were restored. No user process was stopped.

GUI validation instead reuses the installed Forge 47.4.23 client, Java executable, libraries and assets in an isolated temporary game directory containing the installed Core 26.10.4, TACZ and the two validation artifacts. The 8G heap setting remains unchanged; no Minecraft download was performed. Only the copied test world's metadata was reduced to installed dimensions and data packs. The original save was never opened by this reduced client.

Before-fix screenshots reproduce labels crossing fields and panel boundaries. The fixed build completed 18 states and 108 captures with zero fixture failures: English and Chinese at 854×480 and 1536×864 with automatic GUI scale, plus extended-English start/scroll captures. All captures were visually reviewed, including colored item names, every result type and active/modified/disabled details. Hover and invalid-editor follow-up completed another 30 captures with zero failures; all were reviewed. Total: 19 distinct states, 138 fixed-build captures. Both clients exited normally. The final build differs from the runtime-tested JAR only in its timestamp manifest; all other entries were compared byte-for-byte.

The opt-in fixture creates unsaved local samples, restores language/window settings and stops its own client normally. It never clicks Save or changes inventory. Screenshots and private launch commands remain under ignored .gradle/gui-long-text-20261004. Long tooltip height is a known Core 26.10.4 limitation documented in the earlier Core handoff; this addon does not implement a replacement tooltip renderer.

## 中文

现有五个 Forge 1.20.1 页面使用核心限宽滚动文字处理固定区域内的标签、标题、计数、状态和摘要。资料名称及物品 ID 在第一个顶栏按钮前留四像素，错误提示保留 Component 样式。已换行说明、物品绘制、数字叠层和保存行为不变。

独立分支 codex/gui-long-text-20261004 基于 71fb46d；原工作区的其他改动保持原样。本次保留 ForgeGradle 架构，仅使用 Java 21 并提高最低核心依赖至 26.10.3，以便编译现有滚动 API。

离线完整构建及独立验证 JAR 构建通过，提交前最终完整构建耗时六秒。架构检查与最终 JAR 核心引用检查通过，引用问题为零。中英文各 632 个完整语言键，源码和最终资源检查通过，没有修改语言文本。发布 JAR 含 93 个 class，字节码版本均为 65，包含 refmap 和原 MixinConfigs，验证类数量为零。此基线没有独立的附属 Mixin 目标检查任务，不能把日志检查描述为运行过该任务。

完整整合包第一次启动因 Windows 原生内存提交失败而退出，保持原有 8G。已恢复并校验原附属、选项、核心和存档；检查 5,390 个配置文件，恢复其中五个变化文件。没有结束用户进程。

界面验证复用已安装 Forge 47.4.23、Java、库和资源，在临时目录只加载核心、TACZ、附属和独立测试 JAR。内存仍为 8G，没有下载 Minecraft。仅调整临时副本存档的维度和数据包引用，精简客户端从未打开原存档。

修复前截图已复现标签跨越输入框及面板边界。修复后 18 个状态、108 张截图通过，测试失败数为零：中英文、854×480 与 1536×864、自动 GUI 缩放，以及英文超长文本的开始和滚动帧。全部截图已人工检查，覆盖彩色名称、所有结果类型与启用/已修改/已禁用状态。悬停和错误编辑页补查另有 30 张截图，失败数为零，全部已检查。合计 19 个不同状态、138 张修复后截图，两次客户端均正常退出。

验证代码只建立未保存的本地样例，恢复窗口及语言设置并正常退出自己启动的客户端，不点击保存或改动物品栏。截图和私有启动命令存于忽略的 .gradle/gui-long-text-20261004。过高悬浮提示仍是已交接的核心 26.10.4 限制，附属没有另行实现提示渲染器。
