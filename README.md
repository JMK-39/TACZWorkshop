# TACZ Workshop

[English](#english) | [简体中文](#简体中文)

## English

### Overview

A visual TACZ data and gunsmith-workbench authoring toolkit for firearm, attachment, ammunition, melee, supply and recipe configuration. It is intended for server operators and modpack authors who need direct in-game access to TACZ data.

The project is designed around in-game administration. Where a feature changes shared gameplay data or server rules, the server remains authoritative; client-only presentation features stay local to the client. Configuration screens use KineticCore's UI and configuration infrastructure.

### Key Features

- Visual TACZ data browsing and editing with search, category filters and quick actions.
- Firearm, attachment, ammunition, melee and supply data management.
- Gunsmith workbench recipe creation, editing, routing and removal.
- NBT-aware item handling and reusable KineticCore item-return/inspection utilities.
- Optional Sophisticated Backpacks integration.
- Server-authoritative save flow for shared content.

### Requirements and Compatibility

| Type | Dependency |
|---|---|
| Required | Minecraft 1.20.1 |
| Required | Minecraft Forge 47+ |
| Required | KineticCore 26.9.8+ |
| Required | Timeless and Classics Zero (TACZ) |
| Optional | Sophisticated Backpacks |
| Optional | Sophisticated Core |

### Access and Configuration

- Open the KineticCore configuration center with its configured F6 entry and select **TACZ Workshop**.
- Server-owned settings are saved by the server and synchronized where the feature requires client awareness.
- Client-only presentation settings remain local.
- Individual feature areas document their own data/configuration paths below.
- Search, list selection, item/entity inspection, tooltips and return/navigation controls reuse KineticCore UI components where available.

## Detailed Feature Reference

### TACZ Data & Workbench Tools

#### Overview

**TACZ Workshop** provides a visual authoring environment for TACZ firearm data and Gunsmith Table recipes. It exposes the editable data fields, category filters, workbench routing, recipe controls and server-side save workflow through in-game interfaces.

### Feature Reference
#### Config Details
| Item | Description |
|---|---|
| **TACZ Management** | Manage TACZ data and Gunsmith Table recipes through KineticCore. |
| **Gun, Attachment and Ammo Data** | Open the TACZ visual data manager for gun, attachment, ammo values and removal states. |
| **Gunsmith Table Recipes** | Open the visual TACZ Gunsmith Table recipe manager. |

#### GUI and Editors
| Item | Description |
|---|---|
| **Note** | Optional note |
| **search** | Search property name or data field... |
| **search** | Search name, item ID, or data ID... |
| **Group** | Optional group ID |
| **Search** | Search recipe ID, result ID, type, or note |
| **grid** | Left-click to edit, Right-click for the quick menu. |
| **search** | Search workbench name or workbench ID... |
| **Gunsmith Workbench Routing** | Available Workbenches: %s |

#### Editable Options
- Magazine Capacity
- Armor Ignore
- Projectiles per Shot
- Bullet Speed
- Gun Damage
- Extended Mag Level
- Bullet Gravity
- Headshot Multiplier
- Knockback
- Fire Rate RPM
- Sort Value
- Aiming Spread
- Prone Spread
- Moving Spread
- Sneaking Spread
- Standing Spread
- Stack Size
- Weight
- Ammo
- Attachments
- Guns
- Type: Ammo
- Type: Attachment
- Type: Item
- Type: Gun
- Active
- Modified
- Disabled
- Ammo ID
- Bolt Type
- Bullet Lifetime
- Bullet Friction
- Pierce
- Ignite Target
- Tracer Interval
- Extended Mag Capacity %s
- Fire Mode %s
- Draw Time
- Put Away Time
- Aim Time
- Sprint Transition Time
- Reload Type
- Empty Reload Feed Time
- Tactical Reload Feed Time
- Empty Reload Cooldown
- Tactical Reload Cooldown
- Base Move Speed Multiplier
- Aiming Move Speed Multiplier
- Reload Move Speed Multiplier
- Damage Curve %s Distance
- Damage Curve %s Multiplier
- Vertical Recoil %s Time
- Vertical Recoil %s Minimum
- Vertical Recoil %s Maximum
- Horizontal Recoil %s Time
- Horizontal Recoil %s Minimum
- Horizontal Recoil %s Maximum
- Damage Multiplier
- Damage Addend
- Damage Percent
- ADS Speed Addend
- ADS Speed Multiplier
- ADS Speed Percent
- Spread Addend
- Spread Multiplier
- Spread Percent
- Aiming Spread Addend
- Aiming Spread Multiplier
- Aiming Spread Percent
- Vertical Recoil Modifier
- Horizontal Recoil Modifier
- Vertical Recoil Multiplier
- Horizontal Recoil Multiplier
- RPM Multiplier
- RPM Addend
- RPM Percent
- Armor Ignore Addend
- Armor Ignore Multiplier
- Armor Ignore Percent
- Headshot Addend
- Pierce Addend
- Pierce Multiplier
- Ammo Speed Addend
- Ammo Speed Multiplier
- Ammo Speed Percent
- Attachment Slots: %s / %s Enabled
- Attachment Slot Status
- %s: %s
- All
- Original
- Created Recipes
- Removed Recipes
- Melee
- Throwables
- Supplies
- %s - %s
- Primary Attack
- Secondary Attack
- Attack Cooldown
- Damage Delay
- Durability Cost
- Hitbox Type
- Maximum Range
- Maximum Angle
- Exclude Self
- Penetration Range
- Base Attack Damage
- Movement Speed Modifier
- Movement Speed Operation
- Enchantability
- Maximum Durability
- Cooldown
- Cooldown Category
- Durability Cost Per Use
- Food Restored
- Health Restored
- Saturation Restored
- Use Duration
- Use Mode
- Effect %s - Amplifier
- Effect %s - Chance
- Effect %s - Duration
- Effect %s - ID
- Removed Effect %s
- Prepare Time
- Cookable
- Initial Throw Speed
- Entity Lifetime
- Impact Damage
- Can Bounce
- Break on Ground
- Bounce Factor
- Trail Particles
- Explosion Radius
- Explosion Damage
- Destroy Blocks
- Block Destruction Multiplier
- Screen Shake Time
- Screen Shake Strength
- Trigger When Exploded
- Remote Detonation
- Stun Radius
- Blind Maximum Angle
- Blind Maximum Duration
- Blind Minimum Duration
- View Angle Factor
- Deafen Maximum Duration
- Deafen Minimum Duration
- Area Cloud
- Cloud Duration
- Smoke Extinguishes Fire
- Ignite Area
- Ignite Duration
- Cloud Particles
- Cloud Radius
- Radius Change Per Tick
- Cloud Wait Time
- Cloud Effect %s - Amplifier
- Cloud Effect %s - Duration
- Cloud Effect %s - Type
- Cloud Effect %s - Visible
- Effect %s - Ambient
- Effect %s - Visible Particles
- Effect %s - Show Icon
- Cloud Effect %s - Show Icon
- Hitbox Half Width
- Hitbox Half Height
- Hitbox Roll
- Movement Delay
- Movement Speed
- Enable Explosion
- Explosion Knockback
- Explosion Destroys Blocks
- Explosion Delay
- Allow Crawling
- Allow Sliding
- Bolt Action Time
- Bolt Feed Time
- Crawl Recoil Multiplier
- Hurt Bob Tweak Multiplier
- Ignite Entity
- Ignite Block
- Entity Ignite Time
- Infinite Feed
- Fire Sound Multiplier
- Silenced Sound Multiplier
- Continuous Burst Fire
- Burst Shot Count
- Burst Rate
- Burst Minimum Interval
- Gun Melee Distance
- Gun Melee Cooldown
- Default Melee Animation
- Default Melee Distance
- Default Melee Range Angle
- Default Melee Cooldown
- Default Melee Damage
- Default Melee Knockback
- Default Melee Prep Time
- Maximum Heat
- Heat Per Shot
- Cooling Multiplier
- Cooling Delay
- Overheat Time
- Minimum Heat Inaccuracy
- Maximum Heat Inaccuracy
- Minimum Heat RPM Multiplier
- Maximum Heat RPM Multiplier
- Ammo Type %s
- Ammo %1$s · %2$s
- %1$s %2$s
- Custom Field: %s
- Ammo Type
- Ammo Types
- Switch Ammo
- Switch Ammo Type
- Ammo Switch Time
- Ammo Switch Sound
- Ammo Switch Animation
- Type
- ID
- Name
- Damage
- Speed
- Lifetime
- Gravity
- Friction
- RPM
- Explosion
- Radius
- Delay
- Enabled
- Enable
- Ignite
- Entity
- Block
- Distance
- Range
- Range Angle
- Preparation Time
- Infinite
- Feed
- Tactical Reload
- Empty Reload
- Multiplier
- Addend
- Percent
- Count
- Interval
- Time
- Duration
- Amount
- Value
- Minimum
- Maximum
- Chance
- Recoil
- Vertical Recoil
- Horizontal Recoil
- Inaccuracy
- Aim
- ADS
- Reload
- Fire Mode
- Default
- Projectile
- Extra Damage
- Entity Burn Time
- Burst RPM
- Minimum Interval
- Continuous Fire
- Screen Shake Amplitude
- Trigger on Explosion
- Switch
- Mode
- Sound
- Animation
- Rate
- Factor
- Base
- Movement
- Crawl
- Slide
- Sprint
- Standing
- Sneak
- Prone
- Shot
- Head
- Ignore
- Armor
- Destroy
- Fire
- Silenced
- Heat
- Cooling
- Over
- Per
- Modifier
- Initial
- Stock
- Scope
- Muzzle
- Grip
- Laser
- Extended
- Magazine
- Level
- Feed Time
- Action
- Bolt Action
- Bolt Feed
- Velocity
- Explosive
- Power
- Force
- Size
- Capacity
- Multiple
- %1$s Mode · %2$s

#### Data Paths
Primary configuration/data paths:

- `config/kineticcore/taczdata.json`
- `config/kineticcore/taczrecipes.json`

### Building from Source

- Minecraft: `1.20.1`
- Java: `17`
- ForgeGradle: `6.0.24`
- Gradle: the project is pinned to the `8.1.1` Wrapper; do not import it with Gradle 9 directly.
- Local development JARs are controlled by `local_libs_dir` and can be overridden in `gradle.properties` or with a project property.
- Typical build command: `gradlew.bat build` on Windows or `./gradlew build` on Linux/macOS.
- Development and release artifacts use `taczworkshop` as the current project identifier.

## 简体中文

### 模组定位

面向 TACZ 数据与枪匠工作台内容制作的可视化工具，可编辑枪械、配件、弹药、近战、补给与配方，适合服务器管理者与整合包作者直接在游戏内维护 TACZ 数据。

本项目以游戏内管理为核心。涉及共享玩法数据、世界规则或服务器规则的功能由服务端权威处理；仅影响显示的客户端功能保持本地生效。配置界面统一使用 KineticCore 提供的 GUI 与配置基础设施。

### 主要功能

- 提供 TACZ 数据浏览、搜索、分类筛选与快捷编辑。
- 支持枪械、配件、弹药、近战与补给数据管理。
- 支持枪匠工作台配方创建、编辑、工作台路由与移除。
- 支持 NBT 物品处理，并复用 KineticCore 的物品返回/识别工具。
- 可选兼容 Sophisticated Backpacks。
- 共享内容采用服务端权威保存流程。

### 运行环境与兼容

| 类型 | 依赖 |
|---|---|
| 必需 | Minecraft 1.20.1 |
| 必需 | Minecraft Forge 47+ |
| 必需 | KineticCore 26.9.8+ |
| 必需 | Timeless and Classics Zero (TACZ) |
| 可选 | Sophisticated Backpacks |
| 可选 | Sophisticated Core |

### 打开方式与配置

- 使用 KineticCore 配置中心对应的 F6 入口，选择 **TACZ Workshop**。
- 服务端规则由服务端保存，并在需要时同步给客户端。
- 纯显示类客户端设置只在本地生效。
- 各功能自己的配置/数据路径在下方详细功能说明中列出。
- 搜索、列表选择、物品/实体信息读取、悬浮提示、返回与导航等操作尽可能复用 KineticCore GUI 组件。

## 完整功能参考

### TACZ 数据与工作台工具

TACZ Workshop 是 KineticCore 的 TACZ 管理附属模组，为 TACZ 提供服务端权威的可视化数据与枪匠工作台配方管理。

### 基础信息

- Java：17
- Mod ID：`taczworkshop`
- 作者：XYAT
- 版本：构建时按日期自动生成，格式为 `yy.M.d`
- 必需依赖：KineticCore、TACZ
- 可选依赖：Sophisticated Backpacks、Sophisticated Core

### 打开方式

TACZ Workshop 使用 KineticCore 的统一模块配置入口，不提供独立配置入口。

打开 KineticCore 的“已安装插件”页面后，选择 `TACZ Workshop`，可进入：

- TACZ 枪械、配件与子弹数据管理
- TACZ 枪匠工作台配方管理

服务端权威页面需要进入世界并具有对应的管理员权限后才能编辑。

### TACZ 数据管理

数据管理器直接从 TACZ 当前已加载的枪包资源读取枪械、配件和子弹原始数据，不需要先在 `taczdata.json` 中手动建立条目。

支持：

- 搜索枪械、配件和子弹，可按名称、物品 ID 与数据 ID 查找
- 查看并编辑 TACZ 数据字段
- 修改枪械威力、弹丸数量、射速、弹匣容量、子弹速度、重力、击退、护甲无视、爆头倍率和散布等数据
- 开启或关闭枪械允许使用的配件槽
- 编辑配件与子弹数据
- 启用条目、已修改条目、已禁用条目分开筛选显示
- 左键物品格进入详细编辑，右键物品格打开快捷菜单
- 枪械配件槽使用悬浮状态说明与右键快捷开关菜单
- 移除或恢复枪械、配件和子弹
- 已禁用条目不会重新加入正常游戏获取/创造标签页，但仍可在管理器中显示原物品预览、编辑并恢复
- 修改过的条目自动置顶并显示绿色边框
- 一键清除单条覆盖并恢复枪包原始数据

数据覆盖保存在：

`config/kineticcore/taczdata.json`

### 枪匠工作台配方管理

配方管理器会直接读取当前 RecipeManager 中所有已加载的 `tacz:gun_smith_table_crafting` 原始配方，包括 TACZ 本体、枪包和数据包提供的配方，不需要先复制到 `taczrecipes.json`。

支持：

- 枪械输出
- 配件输出
- 子弹输出
- 普通物品输出
- 普通物品材料
- Tag 材料
- `forge:partial_nbt` 材料
- 材料数量
- 输出数量
- 枪械预装配件
- 输出 NBT
- 配方 ID
- 启用与禁用
- 自动识别原配方当前允许使用的 TACZ 枪匠工作台
- 可视化选择创建配方允许使用的枪匠工作台
- 原始配方、创建的配方、已移除配方分开筛选
- 从枪械、配件或子弹数据页面直接查找对应输出配方

编辑枪包或数据包提供的原始配方并保存时，TACZ Workshop 不会覆盖第三方文件。原配方会进入“已移除配方”，编辑后的内容会作为一条新的 TACZ Workshop 配方保存。恢复原始配方只会重新启用原配方，不会删除已经创建的配方。

配方覆盖保存在：

`config/kineticcore/taczrecipes.json`

TACZ Workshop 不会因为读取到枪包原始数据或原始配方而自动生成预置配置文件。只有实际保存修改、新建内容或禁用项目时才会写入对应配置文件。需要预设数据时，也可以自行将配置文件复制到上述目录。

### 精妙背包支持

安装 Sophisticated Backpacks 与 Sophisticated Core 后，TACZ 可使用兼容的背包弹药完成换弹、弹药消耗与 HUD 弹药统计。

### 完整功能参考

#### 配置项详细说明

| 项目 | 说明 |
|---|---|
| **TACZ 管理** | 通过 KineticCore 统一管理 TACZ 数据与枪匠工作台配方。 |
| **枪械、配件与子弹数据管理** | 打开 TACZ 数据可视化管理器，修改枪械、配件、子弹数值以及移除状态。 |
| **枪匠工作台配方管理** | 打开 TACZ 枪匠工作台可视化配方管理器。 |

#### 界面操作与编辑器说明

| 项目 | 说明 |
|---|---|
| **备注** | 可选备注 |
| **search** | 搜索属性名称或数据字段…… |
| **search** | 搜索名称、物品 ID 或数据 ID…… |
| **分类** | 可选分类ID |
| **搜索** | 搜索配方ID、结果ID、类型或备注 |
| **grid** | 左键编辑，右键打开快捷菜单。 |
| **search** | 搜索工作台名称或工作台 ID…… |
| **枪匠工作台路由** | 可用工作台：%s |

#### 可编辑字段、模式与分类索引

- 弹匣容量
- 护甲无视
- 单次弹丸数量
- 子弹速度
- 枪械威力
- 扩容弹匣等级
- 子弹重力
- 爆头倍率
- 击退
- 射速 RPM
- 排序值
- 瞄准散布
- 卧姿散布
- 移动散布
- 潜行散布
- 站立散布
- 堆叠上限
- 重量
- 子弹
- 配件
- 枪械
- 类型：子弹
- 类型：配件
- 类型：普通物品
- 类型：枪械
- 启用
- 已修改
- 已禁用
- 使用弹药
- 枪机类型
- 子弹存活时间
- 子弹空气阻力
- 穿透值
- 点燃目标
- 曳光弹间隔
- 扩容弹匣容量 %s
- 射击模式 %s
- 拔枪时间
- 收枪时间
- 瞄准时间
- 冲刺切换时间
- 换弹类型
- 空仓装填时间
- 战术装填时间
- 空仓换弹收尾时间
- 战术换弹收尾时间
- 基础移动速度倍率
- 瞄准移动速度倍率
- 换弹移动速度倍率
- 伤害曲线 %s 距离
- 伤害曲线 %s 倍率
- 垂直后坐力 %s 时间
- 垂直后坐力 %s 最小值
- 垂直后坐力 %s 最大值
- 水平后坐力 %s 时间
- 水平后坐力 %s 最小值
- 水平后坐力 %s 最大值
- 伤害乘数
- 伤害加值
- 伤害百分比
- 瞄准速度加值
- 瞄准速度乘数
- 瞄准速度百分比
- 散布加值
- 散布乘数
- 散布百分比
- 瞄准散布加值
- 瞄准散布乘数
- 瞄准散布百分比
- 垂直后坐力修正
- 水平后坐力修正
- 垂直后坐力乘数
- 水平后坐力乘数
- 射速乘数
- 射速加值
- 射速百分比
- 护甲无视加值
- 护甲无视乘数
- 护甲无视百分比
- 爆头倍率加值
- 爆头倍率乘数
- 穿透加值
- 穿透乘数
- 弹速加值
- 弹速乘数
- 弹速百分比
- 配件槽：%s / %s 已开启
- 配件槽状态
- %s：%s
- 全部
- 原始
- 创建的配方
- 已移除配方
- 近战
- 投掷物
- 补给
- %s - %s
- 左键攻击
- 右键攻击
- 伤害倍率
- 击退强度
- 攻击冷却
- 出伤延迟
- 耐久消耗
- 判定类型
- 最大距离
- 最大角度
- 排除自身
- 穿透距离
- 基础攻击伤害
- 移动速度修正
- 移动速度运算
- 附魔能力
- 最大耐久
- 冷却时间
- 冷却类别
- 每次使用耐久消耗
- 饱食度恢复
- 生命恢复
- 饱和度恢复
- 使用时长
- 使用模式
- 效果 %s - 等级
- 效果 %s - 概率
- 效果 %s - 时长
- 效果 %s - ID
- 移除效果 %s
- 准备时间
- 允许温雷
- 初始投掷速度
- 实体存活时长
- 撞击伤害
- 允许反弹
- 落地破碎
- 反弹系数
- 尾迹粒子
- 爆炸半径
- 爆心伤害
- 破坏方块
- 方块破坏倍率
- 屏幕震动时间
- 屏幕震动强度
- 受爆炸时触发
- 遥控起爆
- 眩晕影响半径
- 致盲最大角度
- 致盲最长时长
- 致盲最短时长
- 视角影响系数
- 失聪最长时长
- 失聪最短时长
- 区域云效果
- 区域持续时间
- 烟雾可灭火
- 区域点燃
- 点燃时长
- 区域粒子
- 区域半径
- 每刻半径变化
- 区域等待时间
- 区域效果 %s - 等级
- 区域效果 %s - 时长
- 区域效果 %s - 类型
- 区域效果 %s - 可见
- 效果 %s - 环境效果
- 效果 %s - 可见粒子
- 效果 %s - 显示图标
- 区域效果 %s - 显示图标
- 判定半宽
- 判定半高
- 判定翻滚角
- 位移延迟
- 位移速度
- 启用爆炸
- 爆炸伤害
- 爆炸击退
- 爆炸破坏方块
- 爆炸延迟
- 允许匍匐
- 允许滑铲
- 拉栓动作时间
- 拉栓供弹时间
- 匍匐后坐力倍率
- 受伤晃动修正倍率
- 点燃实体
- 点燃方块
- 实体燃烧时间
- 无限供弹
- 开火声音倍率
- 消音声音倍率
- 连发组持续射击
- 连发组弹数
- 连发组射速
- 连发组最小间隔
- 枪械近战距离
- 枪械近战冷却
- 默认近战动画
- 默认近战距离
- 默认近战范围角度
- 默认近战冷却
- 默认近战伤害
- 默认近战击退
- 默认近战准备时间
- 最大热量
- 每发热量
- 散热倍率
- 散热延迟
- 过热时间
- 最低热量散布
- 最高热量散布
- 最低热量射速倍率
- 最高热量射速倍率
- 弹种 %s
- 弹种 %1$s · %2$s
- %1$s %2$s
- 自定义属性：%s
- 弹药
- 弹药 ID
- 弹药类型
- 切换弹药
- 切换弹药类型
- 切换弹药时间
- 切换弹药音效
- 切换弹药动画
- 类型
- ID
- 名称
- 伤害
- 速度
- 存活时间
- 重力
- 摩擦力
- 穿透
- 射速
- 单次弹丸数
- 爆炸
- 半径
- 延迟
- 点燃
- 实体
- 方块
- 距离
- 范围
- 范围角度
- 前摇时间
- 无限
- 供弹
- 战术换弹
- 空仓换弹
- 倍率
- 附加值
- 百分比
- 数量
- 间隔
- 时间
- 持续时间
- 数值
- 最小值
- 最大值
- 概率
- 后坐力
- 垂直后坐力
- 水平后坐力
- 散布
- 瞄准
- 换弹
- 射击模式
- 默认
- 弹丸
- 额外伤害
- 最小间隔
- 连续射击
- 远程引爆
- 爆炸后触发
- %1$s%2$s
- 切换
- 模式
- 音效
- 动画
- 速率
- 基础
- 移动
- 匍匐
- 滑铲
- 冲刺
- 站立
- 潜行
- 卧倒
- 射击
- 头部
- 无视
- 护甲
- 破坏
- 开火
- 消音
- 热量
- 散热
- 过热
- 每次
- 修正
- 初始
- 枪托
- 瞄具
- 枪口
- 握把
- 激光
- 扩容
- 弹匣
- 等级
- 供弹时间
- 动作
- 拉栓动作
- 拉栓供弹
- 威力
- 力度
- 大小
- 容量
- 多重
- %1$s模式 · %2$s
- %1$s蓄力 · %2$s

#### 配置与数据路径

主要配置/数据路径：

- `config/kineticcore/taczdata.json`
- `config/kineticcore/taczrecipes.json`

### 从源码构建

- Minecraft：`1.20.1`
- Java：`17`
- ForgeGradle：`6.0.24`
- Gradle：项目固定使用 `8.1.1` Wrapper，请不要使用 Gradle 9 直接导入。
- 默认本地依赖目录由 `local_libs_dir` 控制，可在 `gradle.properties` 或命令行参数中覆盖。
- 常用构建命令：`gradlew.bat build`（Windows）或 `./gradlew build`（Linux/macOS）。
- 生成的开发/发布文件以 `taczworkshop` 作为当前工程标识。
