# TACZ Workshop

[![🇺🇸 English](https://img.shields.io/badge/%F0%9F%87%BA%F0%9F%87%B8_English-2F81F7?style=for-the-badge)](#english) [![🇨🇳 简体中文](https://img.shields.io/badge/%F0%9F%87%A8%F0%9F%87%B3_%E7%AE%80%E4%BD%93%E4%B8%AD%E6%96%87-DC2828?style=for-the-badge)](#chinese) [![CurseForge](https://img.shields.io/badge/CurseForge-Open-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/taczworkshop)

<a id="english"></a>

## English

TACZ Workshop provides in-game data editing and repair for installed TACZ content. Recipe creation, editing and removal now belong to ContentStudio.

### Installation and access

| Component | Requirement |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.2 or newer |
| Java | 17 |
| KineticCore | 26.10.7 or newer; required |
| Timeless and Classics Zero (TACZ) | Required |
| Sophisticated Backpacks / Sophisticated Core | Optional backpack compatibility |
| LR Tactical content | Needed for the corresponding melee, throwable, and consumable data categories |

Install the mod and required dependencies on both client and server for multiplayer use. Press **F6** to open KineticCore's configuration center, then open TACZ Workshop's data manager. F6 is the default shared configuration key.

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

### Recipe tools have moved to ContentStudio

From **26.10.11**, the separate Workshop recipe editor and workbench-routing controls are removed. Use **ContentStudio → Recipe Manager → TACZ** to edit recipes or create a recipe using an existing registered type. Third-party visual editing requires **JEI**; saved datapack recipes continue loading without JEI. ContentStudio and JEI are optional for Workshop's data editing and repair.

From 26.10.12, Workshop contains no recipe editor, loader, repair hook, or automatic converter. Existing legacy recipe files are preserved. Convert them separately when needed; new recipes use ContentStudio and TACZ's native workbench rules.

### Validation and compatibility

Data validation and data repair remain available. Recipe loading and editing are handled by ContentStudio.

With Sophisticated Backpacks installed, the compatibility layer includes ammunition held in supported inventory backpacks in TACZ reload checks, consumption, and ammo display. The optional integration is more than an editor item picker; it affects ammunition availability during play.

When **TaCZ addon (`taczaddon`)** is installed, it owns the overlapping backpack-ammunition and attachment-tooltip features. Workshop does not inject those hooks a second time; its data editing, repair, and reload hooks remain active. This is distinct from SomeTaczAddon (`sometaczaddon`). Dedicated-server validation covers TaCZ addon **1.1.8.3**, TaCZ **1.1.8-hotfix2**, ContentStudio **26.10.12**, and backpack-only ammunition with Sophisticated Backpacks **3.26.9.2194** / Core **1.5.6.2383** on Forge 1.20.1. Client GUI verification is still pending.

### Storage and pack distribution

| File | Purpose |
| --- | --- |
| `config/kineticcore/taczdata.json` | Content-data overrides and disabled states |
| `config/kineticcore/taczrecipes.json` | Legacy recipe file; preserved and no longer loaded by Workshop |
| Corresponding `.json.bak` files | Backup copies maintained by the stores when applicable |

These are instance-level configuration files. Keep the original TACZ/LR Tactical packs installed: the overrides refer to their content IDs. Distribute the matching content packs and Workshop configuration together when reproducing a setup.

If a data save fails, inspect the server message/log and confirm the referenced content packs are installed. Manage recipes in ContentStudio.


<a id="chinese"></a>

## 简体中文

TACZ Workshop 提供游戏内 TACZ 内容的数据修改与修复功能。配方新增、修改与移除已统一交给 ContentStudio。

### 安装与入口

| 组件 | 要求 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.2 或更新版本 |
| Java | 17 |
| KineticCore | 必需，26.10.7 或更新版本 |
| Timeless and Classics Zero（TACZ） | 必需 |
| Sophisticated Backpacks / Sophisticated Core | 可选，用于精妙背包兼容 |
| LR Tactical 内容 | 近战、投掷物和补给品等对应数据分类需要这些内容 |

多人游戏中，在客户端和服务端安装模组及必需前置。按 **F6** 打开 KineticCore 配置中心，进入 TACZ Workshop 的数据管理器。F6 是共享配置中心的默认按键。

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

### 配方功能已迁至 ContentStudio

从 **26.10.11** 起，移除工坊内独立的配方编辑器和工作台分配功能。请使用 **ContentStudio → 配方管理器 → TACZ** 修改配方，或选择已有配方类型新增自己的配方。第三方可视化编辑需要安装 **JEI**；已保存的数据包配方在没有 JEI 时仍会加载。工坊的数据修改与修复不依赖 ContentStudio 或 JEI。

从 26.10.12 起，工坊不再包含配方编辑、加载、修复钩子或自动转换功能。旧配方文件保持原样，需要时单独转换；新配方使用 ContentStudio，并遵循 TACZ 自身的工作台规则。

### 校验与兼容

保留数据校验与数据修复，配方加载与编辑由 ContentStudio 负责。

安装精妙背包后，兼容层会将支持的背包中的弹药纳入 TACZ 换弹检查、弹药消耗和数量显示。它不仅用于编辑器选物品，也影响实际游戏中的弹药可用性。

安装 **TaCZ addon（模组 ID：`taczaddon`）** 时，重复的背包弹药与配件提示功能交由 addon 处理，工坊不再重复注入；数据修改、修复和重载仍正常启用。它与 SomeTaczAddon（`sometaczaddon`）是不同模组。专用服务端已验证 Forge 1.20.1 下的 TaCZ addon **1.1.8.3**、TaCZ **1.1.8-hotfix2**、ContentStudio **26.10.12** 组合，并验证精妙背包 **3.26.9.2194** / 精妙核心 **1.5.6.2383** 的背包独占弹药检查与消耗。客户端 GUI 仍待验证。

### 文件与整合包分发

| 文件 | 用途 |
| --- | --- |
| `config/kineticcore/taczdata.json` | 内容数据覆盖与禁用状态 |
| `config/kineticcore/taczrecipes.json` | 旧配方文件，保留原文件，工坊不再加载 |
| 对应的 `.json.bak` 文件 | 存储逻辑在适用情况下维护的备份 |

这些文件属于游戏或服务器实例配置。原有 TACZ/LR Tactical 内容包仍需保留，因为覆盖记录引用其中的内容 ID。分发整合包时，应同时携带对应内容包和 Workshop 配置。

数据保存失败时，检查服务端提示或日志，并确认引用的内容包已经安装。配方相关操作请使用 ContentStudio。
