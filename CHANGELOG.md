2026年10月05日 — Vanilla recipe slot textures / 原版配方槽位贴图

- Use vanilla slot textures for recipe materials, results and workbenches. Preserve existing slot positions, size, hover/selection outlines, layout and interactions.

- 配方材料、结果与工作台使用原版槽位贴图，保留既有槽位位置、尺寸、悬停/选中描边、布局与交互。

---

2026年10月04日 21时09分 — Bounded GUI text / 界面长文本适配

- Keep recipe labels, counts, summaries and data status text inside their own areas with KineticCore scrolling text; stop data names and IDs before header buttons. Preserve styled error messages.
- Compile against the existing scrolling API with Java 21 and require KineticCore 26.10.3 or newer. Keep local GUI validation in a separate opt-in JAR.

- 使用核心滚动文字限制配方标签、计数、摘要和资料状态的绘制范围；资料名称和 ID 在顶栏按钮前停止，保留错误提示的样式。
- 使用 Java 21 编译，最低依赖 KineticCore 26.10.3；本地界面验证代码放入独立、显式启用的测试 JAR。

---

2026年10月04日 — Language key validation / 语言键一致性检查

- Require identical authored English/Chinese keys and string values in source, version overrides and packaged resources; generated formatting keys are rejected during builds.

- 强制检查源码、版本覆盖与最终资源的中英文完整键名一致、值为字符串；构建禁止派生格式语言键。

---

2026年10月02日 13时53分

- Removed 7 unused imports without changing behavior.
- Enabled addon architecture validation. The full build and final-JAR API verification passed.

- 删除 7 个未使用引用，不改变功能。
- 接入附属架构检查，完整构建和最终 JAR API 检查通过。

---

2026年09月29日（原记录未标注小时、分钟）

- Updated the firearm data, data management, recipe, and workbench selection interfaces.
- Improved recipe editor navigation and refresh behavior when synchronized data arrives.
- Updated compatibility with KineticCore 26.9.29.

- 更新枪械数据、数据管理、配方和工作台选择界面。
- 改进配方编辑器页面导航，以及收到同步数据后的刷新行为。
- 更新对 KineticCore 26.9.29 的兼容。
