# TACZ Workshop

[English](#english) | [简体中文](#chinese) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/taczworkshop)

<a id="english"></a>

## English

TACZ Workshop provides in-game tools for adjusting installed TACZ content and managing gunsmith-workbench recipes. Pack authors can tune weapon data, control available entries, build recipes with NBT-bearing results, and choose which workbenches offer them.

### Installation and access

| Component | Requirement |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.2 or newer |
| KineticCore | 26.9.20 or newer; required |
| Timeless and Classics Zero (TACZ) | Required |
| Sophisticated Backpacks / Sophisticated Core | Optional backpack compatibility |
| LR Tactical content | Needed for the corresponding melee, throwable, and consumable data categories |

Install the mod and required dependencies on both client and server for multiplayer use. Press **F6** to open KineticCore's configuration center, then open TACZ Workshop's data manager or recipe manager. F6 is the default shared configuration key.

The editors read and modify server data. Opening server snapshots and submitting modifications require permission level **2**. A local preview is not proof that a server change has been accepted.

### Browse and tune content data

The data manager searches by display name, item ID, or data ID and separates active, modified, and disabled entries. Modified entries are marked so they can be found again among the original gun-pack content.

| Category | Examples of editable data when present |
| --- | --- |
| Guns | Damage, RPM, magazine capacity, projectile speed/gravity, headshot multiplier, spread, reload and handling timings |
| Attachments | Their existing numeric, text, and boolean data fields |
| Ammunition | Existing ammo data such as stack size and related properties |
| LR Tactical melee | Attack damage/multipliers, cooldowns, durability costs, movement, and hitbox properties |
| LR Tactical throwables | Throw speed, fuse/lifetime, bouncing, explosions, stun, and cloud properties |
| LR Tactical consumables | Use time, food/healing values, effects, cooldowns, and durability costs |

Fields are derived from the selected entry's data. The detail screen supports property search and displays data paths, helping distinguish similarly named fields. Available properties depend on the installed content; this tool edits existing definitions rather than supplying new models, textures, or animations.

For guns, attachment-type controls can allow or block supported attachment categories, such as scopes, muzzles, stocks, grips, lasers, and extended magazines.

**Disable** hides an entry from the corresponding content index after the change is applied. **Restore** reverses the disabled state. **Reset** clears the Workshop override and restores the original pack data; it is different from simply enabling an edited entry again.

### Save data as a batch

1. Open an entry and change the required fields.
2. Return to the list and edit other entries as needed. Detail edits are staged in the client editing session.
3. Use **Save All** in the data manager to submit the staged changes together.
4. Wait for server validation, file writing, and the reload result before testing the content.

The batch flow keeps pending edits distinct from saved server data. It also checks whether another reload is already pending. Reset and disabled-state changes participate in the same staged workflow.

### Create gunsmith-workbench recipes

The recipe editor supports gun, attachment, ammo, normal-item, and available LR Tactical melee/throwable/consumable outputs. Choose a real output item, its count, a recipe ID, and optional group and note.

- Add item or item-tag inputs with separate required counts.
- Give a concrete item input a partial-NBT condition; tag inputs do not directly carry NBT conditions.
- Set extra output NBT for customized results.
- For gun outputs, configure loaded ammunition and preinstalled attachments.
- Validate attachment types and result types against the selected content.
- Copy a recipe to start a new recipe ID, edit an existing record, or remove it.
- Disable a custom recipe while retaining its saved definition for later reuse.

Recipe searches include recipe ID, result ID, type, and note. The manager distinguishes original recipes, Workshop-created recipes, and removed recipes.

### Workbench routing and original recipes

The workbench selector lists available gunsmith workbenches by name and ID. Toggle the workbenches where a recipe may appear and be crafted. Recipes using native routing follow their original workbench rules.

Editing an original recipe saves a Workshop replacement and places the original in the removed set. This preserves a way to restore the original recipe instead of requiring changes to the original gun-pack archive. Use the recipe manager's restore action when you want the original back.

**Recipe saves apply immediately:** after server validation, the recipe store is updated and the runtime recipe/routing state is synchronized. This is separate from the data manager's staged **Save All** workflow.

### Validation and compatibility

Recipe validation checks IDs, material/result structure, counts, result types, and NBT. A preflight pass also isolates malformed TACZ recipe candidates before recipe deserialization and records the affected recipe IDs and reasons for diagnosis. This does not guarantee that every third-party gun pack is compatible.

With Sophisticated Backpacks installed, the compatibility layer includes ammunition held in supported inventory backpacks in TACZ reload checks, consumption, and ammo display. The optional integration is more than an editor item picker; it affects ammunition availability during play.

### Storage and pack distribution

| File | Purpose |
| --- | --- |
| `config/kineticcore/taczdata.json` | Content-data overrides and disabled states |
| `config/kineticcore/taczrecipes.json` | Workshop recipes and original-recipe removal state |
| Corresponding `.json.bak` files | Backup copies maintained by the stores when applicable |

These are instance-level configuration files. Keep the original TACZ/LR Tactical packs installed: the overrides refer to their content IDs. Distribute the matching content packs and Workshop configuration together when reproducing a setup.

If a save fails, inspect the server message/log and check IDs, material counts, NBT, and whether the referenced content is loaded. If a recipe is missing from a bench, also check its enabled state, original/replacement status, and workbench selection.

[Back to language selection](#tacz-workshop)

<a id="chinese"></a>

## 简体中文

TACZ Workshop 提供游戏内 TACZ 内容调整与枪匠工作台配方管理工具。整合包作者可以调整已安装内容的数据、控制条目是否可用、制作带 NBT 的产物，并指定配方出现在哪些工作台中。

### 安装与入口

| 组件 | 要求 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.2 或更新版本 |
| KineticCore | 必需，26.9.20 或更新版本 |
| Timeless and Classics Zero（TACZ） | 必需 |
| Sophisticated Backpacks / Sophisticated Core | 可选，用于精妙背包兼容 |
| LR Tactical 内容 | 近战、投掷物和补给品等对应数据分类需要这些内容 |

多人游戏中，在客户端和服务端安装模组及必需前置。按 **F6** 打开 KineticCore 配置中心，进入 TACZ Workshop 的数据管理器或配方管理器。F6 是共享配置中心的默认按键。

编辑器读取和修改服务端数据。请求服务端列表和提交修改需要 **2 级管理权限**；本地预览变化不等于服务端已经接受修改。

### 浏览与调整内容数据

数据管理器支持按显示名称、物品 ID、数据 ID 搜索，并区分正常、已修改、已禁用条目。修改过的条目会有标记，便于从大量枪包内容中重新找到。

| 分类 | 数据存在时可编辑的示例 |
| --- | --- |
| 枪械 | 伤害、射速、弹匣容量、弹速/重力、爆头倍率、散布、换弹与操作时间 |
| 配件 | 条目中已有的数值、文本与布尔数据字段 |
| 弹药 | 堆叠数量等已有弹药属性 |
| LR Tactical 近战 | 攻击伤害/倍率、冷却、耐久消耗、移动与碰撞箱属性 |
| LR Tactical 投掷物 | 初速度、引信/存在时间、反弹、爆炸、震撼与区域云属性 |
| LR Tactical 补给品 | 使用时间、饱食度/治疗值、效果、冷却与耐久消耗 |

字段来自所选条目的实际数据。详情界面可搜索属性，并显示数据路径，便于区分名称相近的字段。具体可编辑内容由已安装资源决定；编辑器用于调整已有定义，不负责生成模型、贴图或动画资源。

枪械还可控制允许安装的配件类型，例如瞄具、枪口、枪托、握把、激光和扩容弹匣。

**禁用**会在应用后将条目从对应内容索引中隐藏；**恢复**取消禁用；**重置**则清除 Workshop 对该条目的覆盖，恢复原始包数据。重置与重新启用一个已经修改的条目含义不同。

### 批量保存数据

1. 打开条目，修改所需字段。
2. 返回列表后可继续编辑其他条目；详情修改先暂存在客户端编辑会话中。
3. 在数据管理器点击 **全部保存**，统一提交待保存内容。
4. 等待服务端校验、写入文件和重载完成，再实际测试。

批量流程会区分待保存修改与服务端已保存状态，也会检查是否已有重载任务等待执行。重置和禁用状态修改同样进入暂存流程。

### 制作枪匠工作台配方

配方编辑器支持枪械、配件、弹药、普通物品，以及可用的 LR Tactical 近战、投掷物和补给品产物。选择真实产物、数量、配方 ID，并按需填写分组和备注。

- 添加具体物品或物品标签作为输入，分别设置所需数量。
- 为具体物品输入设置部分 NBT 匹配条件；标签材料不能直接携带 NBT 条件。
- 为产物附加 NBT，制作定制物品。
- 枪械产物可设置已装填弹药数量和预装配件。
- 根据所选内容校验配件类型与产物类型。
- 复制已有配方并生成新 ID，也可编辑或删除记录。
- 暂时禁用自建配方，同时保留文件中的定义，便于以后重新启用。

配方搜索覆盖配方 ID、产物 ID、类型和备注。管理界面区分原始配方、Workshop 新建配方与已移除配方。

### 工作台分配与原始配方

工作台选择器按名称和 ID 展示可用枪匠工作台。勾选哪些工作台允许显示并制作当前配方；采用原生分配规则的配方则遵循原始工作台规则。

修改原始配方时，会保存一份 Workshop 替代配方，并把原配方放入已移除集合。这样无需修改原枪包压缩文件，也保留了恢复原配方的途径。需要恢复时，使用配方管理器的恢复操作。

**配方保存立即应用：**服务端校验通过后更新配方文件，并同步运行时配方与工作台分配状态。这与数据管理器的暂存、全部保存流程不同。

### 校验与兼容

配方校验涉及 ID、材料/产物结构、数量、类型和 NBT。反序列化前的预检查还会隔离格式异常的 TACZ 配方候选项，记录相关配方 ID 与原因，便于定位问题。这不代表所有第三方枪包都已验证兼容。

安装精妙背包后，兼容层会将支持的背包中的弹药纳入 TACZ 换弹检查、弹药消耗和数量显示。它不仅用于编辑器选物品，也影响实际游戏中的弹药可用性。

### 文件与整合包分发

| 文件 | 用途 |
| --- | --- |
| `config/kineticcore/taczdata.json` | 内容数据覆盖与禁用状态 |
| `config/kineticcore/taczrecipes.json` | Workshop 配方及原始配方移除状态 |
| 对应的 `.json.bak` 文件 | 存储逻辑在适用情况下维护的备份 |

这些文件属于游戏或服务器实例配置。原有 TACZ/LR Tactical 内容包仍需保留，因为覆盖记录引用其中的内容 ID。分发整合包时，应同时携带对应内容包和 Workshop 配置。

保存失败时，检查服务端提示/日志、ID、材料数量、NBT，以及引用内容是否已加载。若工作台中找不到配方，还应检查启用状态、原始/替代关系和工作台勾选范围。

[返回语言选择](#tacz-workshop)
