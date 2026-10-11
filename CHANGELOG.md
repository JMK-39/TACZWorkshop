2026年10月12日 — 26.10.12 — ContentStudio owns recipes / ContentStudio 接管配方

- Removed the remaining recipe preflight, recipe issue registry and automatic legacy converter. Workshop now provides data editing and data repair only; ContentStudio owns recipe creation, editing, saving and loading.
- Preserve legacy recipe files for separate conversion. No conversion, deletion or migration backup is performed at startup.
- 移除剩余的配方预检、配方错误登记和旧配方自动转换。工坊只保留数据修改与数据修复；新增、编辑、保存和加载配方均由 ContentStudio 接管。
- 保留旧配方文件供单独转换，启动时不转换、不删除，也不生成迁移备份。

---
2026年10月11日 — Recipe editing moved to ContentStudio / 配方编辑统一迁至 ContentStudio

- Removed Workshop's duplicate recipe editor, recipe manager, custom workbench routing and their network handlers. Data editing, batch saving, disabled-state controls, and data repair remain available.
- Added one-time automatic migration of enabled legacy recipes and original-recipe removals to ContentStudio datapack files. Existing ContentStudio edits take priority; successful migration deletes the legacy source and old migration backups without creating new backups. Recipe files are grouped under `data/contentstudio/recipe/`. Completed migration never overwrites subsequent edits or recreates deleted recipes. File errors are logged and can be retried after repair; unsupported legacy custom workbench restrictions are reported without changing their scope.
- Use ContentStudio's TACZ recipe category to create or edit recipes. JEI is required for third-party visual editing; saved datapack recipes still load without it. Multiplayer clients and servers must both update Workshop to this version.

- 移除工坊内重复的配方编辑器、配方管理器、自定义工作台分配及对应网络处理；保留数据修改、批量保存、禁用状态管理与数据修复。
- 新增旧配方的一次性自动迁移：将已启用的配方与原始配方移除规则导入 ContentStudio 数据包文件。同 ID 的已有新编辑器修改优先保留，迁移成功后删除旧文件和旧迁移备份，不生成新备份，配方文件统一存放在 `data/contentstudio/recipe/`；成功后不再覆盖后续修改，也不会重新生成已删除的配方。文件错误会记录日志，修复后可重启重试；不支持的旧自定义工作台限定会明确报告，不擅自扩大制作范围。
- 新增或编辑配方请使用 ContentStudio 的 TACZ 分类；第三方可视化编辑需要 JEI，已保存的数据包配方不依赖 JEI 加载。多人游戏的客户端与服务端需同时更新至本版本。

---
2026年10月10日 — Consistent Back navigation / 统一返回导航

- Recipe and data managers place Back at the upper left beside the search controls. Recipe editing, data details and workbench selection use the lower-left corner because their headers are occupied. Existing control widths, grids, fields and saving actions are preserved; counts scroll within the space beside neighboring controls.
- Recipe and workbench lists use API checkerboard slots. List and detail item icons use their original 1.0 scale.

- 配方与资料管理页将返回按钮放到左上角、搜索控件之前；配方编辑、资料详情和工作台选择页因顶栏已有内容，改用左下角。保留原有控件宽度、网格、字段与保存操作，计数文字在相邻控件之间的区域内滚动。
- 配方与工作台列表使用 API 棋盘格；列表及资料详情中的物品图标统一使用 1.0 原始大小。

---

2026年10月08日 — Native recipe backgrounds / 原版配方背景

- Recipe materials, results, recipe lists and workbench selection use native slot textures. TaCZ workbench backgrounds, ingredients and 3D result previews retain their original appearance. Compact icons in custom data lists keep clear themed backgrounds and space inside their borders.

- 配方材料、结果、配方列表和工作台选择使用原版槽位贴图；TaCZ 工作台背景、材料及三维结果预览保留原有外观。自绘资料列表的小图标保留清晰的格子背景，并在边框内留出间距。

---

2026年10月06日 18时01分 — Labels centred on their fields / 标签与输入框垂直居中

- In the data detail page and the recipe editor, field labels (Gun Damage and the other data fields, Material Count, Output Count, Group, Loaded Ammo Count) sat 2 px below the middle of their input boxes; they are now centred on them.

- 数据详情页与配方编辑器中，各输入框的标签（枪械威力等数据字段、材料数量、输出数量、分类、预装弹药数量）比输入框中线低 2 像素；现在与输入框垂直居中。

---

2026年10月05日 23时08分 — Selection colors / 选择颜色

- In the workbench selector, chosen workbenches are outlined green, because several can be chosen; yellow is reserved for a single current choice.
- The data category, result type and recipe category menus mark their current option yellow as single choices; the attachment-slot menu, where several types can be on, marks enabled types green.

- 工作台选择界面中已选的工作台改为绿色边框，因为可以多选；黄色只用于单一的当前选择。
- 数据分类、产物类型与配方分类菜单作为单选，用黄色标出当前选项；配件槽位菜单可同时开启多种类型，开启的类型显示为绿色。

---

2026年10月05日 — Screen-fitted tooltips / 提示框屏幕宽度适配

- Tooltips for recipe materials/results/workbenches, recipe rows, data rows, fields, attachment summaries and header items wrap to fit the available screen width while preserving text styling and line order. Requires KineticCore 26.10.4 or newer.

- 配方材料/结果/工作台、配方列表、资料列表、字段、配件摘要与顶栏物品提示框按屏幕可用宽度换行，保留文字样式与行顺序。最低依赖 KineticCore 26.10.4。

---

2026年10月05日 — Vanilla recipe slot textures / 原版配方槽位贴图

- Use vanilla slot textures for recipe materials, results and workbenches. Preserve existing slot positions, size, hover/selection outlines, layout and interactions.

- 配方材料、结果与工作台使用原版槽位贴图，保留既有槽位位置、尺寸、悬停/选中描边、布局与交互。

---

2026年10月04日 21时09分 — Bounded GUI text / 界面长文本适配

- Keep recipe labels, counts, summaries and data status text scrolling inside their own areas; stop data names and IDs before header buttons. Preserve styled error messages.
- Requires KineticCore 26.10.3 or newer.

- 配方标签、计数、摘要和资料状态在各自区域内滚动；资料名称和 ID 在顶栏按钮前停止，保留错误提示的样式。
- 最低依赖 KineticCore 26.10.3。

---

2026年09月29日（原记录未标注小时、分钟）

- Updated the firearm data, data management, recipe, and workbench selection interfaces.
- Improved recipe editor navigation and refresh behavior when synchronized data arrives.
- Updated compatibility with KineticCore 26.9.29.

- 更新枪械数据、数据管理、配方和工作台选择界面。
- 改进配方编辑器页面导航，以及收到同步数据后的刷新行为。
- 更新对 KineticCore 26.9.29 的兼容。
