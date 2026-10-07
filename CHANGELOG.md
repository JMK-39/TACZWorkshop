2026年10月06日 18时01分 — Labels centred on their fields / 标签与输入框垂直居中

- In the data detail page and the recipe editor, field labels (Gun Damage and the other data fields, Material Count, Output Count, Group, Loaded Ammo Count) sat 2 px below the middle of their input boxes; they are now centred on them.
- Checked with screenshots of all 19 screens at 1920×1080 and 854×480, in English and Chinese and with extra-long text, inside the 1.20.1 modpack.

- 数据详情页与配方编辑器中，各输入框的标签（枪械威力等数据字段、材料数量、输出数量、分类、预装弹药数量）比输入框中线低 2 像素；现在与输入框垂直居中。
- 已在 1.20.1 整合包中以 1920×1080 与 854×480、英文和中文以及超长文本对全部 19 个界面截图检查。

---

2026年10月05日 23时08分 — Selection colors / 选择颜色

- In the workbench selector, chosen workbenches are outlined green, because several can be chosen; yellow is reserved for a single current choice.
- The data category, result type and recipe category menus mark their current option yellow as single choices; the attachment-slot menu, where several types can be on, marks enabled types green.

- 工作台选择界面中已选的工作台改为绿色边框，因为可以多选；黄色只用于单一的当前选择。
- 数据分类、产物类型与配方分类菜单作为单选，用黄色标出当前选项；配件槽位菜单可同时开启多种类型，开启的类型显示为绿色。

---

2026年10月05日 — Screen-fitted tooltips / 提示框屏幕宽度适配

- Pass original styled tooltip Components to KineticCore screen fitting for recipe materials/results/workbenches, recipe rows, data rows, fields, attachment summaries and header items. Keep all logical lines, their order, the 280/320 preferred widths, layout and interactions; require KineticCore 26.10.4 or newer.

- 配方材料/结果/工作台、配方列表、资料列表、字段、配件摘要与顶栏物品提示框将原始带样式文本交给核心按屏幕宽度换行；保留全部逻辑行、顺序、280/320 首选宽度、布局和交互，最低依赖 KineticCore 26.10.4。

---

2026年10月05日 — Vanilla recipe slot textures / 原版配方槽位贴图

- Use vanilla slot textures for recipe materials, results and workbenches. Preserve existing slot positions, size, hover/selection outlines, layout and interactions.

- 配方材料、结果与工作台使用原版槽位贴图，保留既有槽位位置、尺寸、悬停/选中描边、布局与交互。

---

2026年10月04日 21时09分 — Bounded GUI text / 界面长文本适配

- Keep recipe labels, counts, summaries and data status text inside their own areas with KineticCore scrolling text; stop data names and IDs before header buttons. Preserve styled error messages.
- Compile against the existing scrolling API and require KineticCore 26.10.3 or newer. Keep local GUI validation in a separate opt-in JAR.

- 使用核心滚动文字限制配方标签、计数、摘要和资料状态的绘制范围；资料名称和 ID 在顶栏按钮前停止，保留错误提示的样式。
- 使用现有滚动 API，最低依赖 KineticCore 26.10.3；本地界面验证代码放入独立、显式启用的测试 JAR。

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
