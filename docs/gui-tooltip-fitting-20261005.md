# Tooltip screen fitting verification / 提示框屏幕适配验证

Scope: eight tooltip requests in `TaczRecipeEditorPage` (material, workbench,
result), `TaczRecipeListPage`, `TaczDataManagerPage`, and `TaczDataDetailPage`
(field, attachment summary, header item). Only tooltip component collection and
submission change. Original logical lines, component styles, order, branch
conditions, preferred widths (workbench 280, others 320), page geometry and
interactions are retained. The attachment-summary helper now returns raw
Components. Core remains read-only. The declared minimum is 26.10.4, which
provides screen/cursor fitting for `showTooltip(lines, preferredWidth)`.

范围：四个页面中的八条提示框请求（材料、工作台、结果、配方列表、资料列表、
字段、配件摘要、顶栏物品）。仅修改提示框文本收集和提交方式；原逻辑行、样式、
顺序、分支条件、首选宽度（工作台 280，其余 320）、页面几何与交互均保留。
配件摘要返回原始 Component；核心只读，最低版本提升至提供屏幕适配的 26.10.4。

## Renderer regression / 渲染器回归

```powershell
& D:/APPs/FormatFactory/FFModules/python/python.exe gradle/check-tooltip-routing.py --java-home D:/APPs/java/java21
```

The tracked script reads the actual production renderer bodies and compiles
them into a recording page host. It substitutes only model/translation/host
boundaries and writes generated Java/classes under `build/tooltip-routing-check`.
It never writes or restores production files. RED: all 11 content scenarios
covering the eight requests failed because they submitted formatted text rather
than raw Components; logical lines/styles/order were intact. GREEN: 19 cases
pass, including the eight paths, error/native/missing variants and overlay/empty
suppression. This proves addon submission behavior, not Core GPU rendering or
actual screen/cursor line widths. Full-pack hover verification is recorded below.

脚本读取并编译实际生产渲染方法，仅替换模型、翻译和宿主边界；生成文件只写入
build，不会回写或恢复生产源码。RED：覆盖八条请求的 11 个内容分支均因绕过
原始文本适配而失败，逻辑行/样式/顺序检查通过。GREEN：19 个案例通过，包括
错误、原生工作台、缺失资源以及 overlay/空悬停抑制。此检查验证附属的提交行为，
不验证核心 GPU 渲染或实际窗口/光标的行宽；完整包悬停验证见下文。

## Offline build / 离线构建

```powershell
.\gradlew.bat -g D:/IDEA_Caches/Gradle -I build/tooltip-routing-check/core-dev.init.gradle build runtimeValidationJar --offline --no-daemon '-Pkineticcore_version=26.10.4' '-Pkineticcore_jar=D:/NEWMODS/kineticcore-forge-1.20.1-26.10.4.jar' '-Poutput_mods_dir=build/libs' --console=plain
```

ForgeGradle 6.0.24 cannot deobfuscate the Java 21 Core release (class version 65).
The ignored local init replaces only the Core compile dependency with the
existing mapped `D:/IDEAWork/KineticCore/versions/1.20.1-forge/build/devlibs/kineticcore-26.10.4.jar`.
Final reobfuscated addon references are checked against the release Core 26.10.4
JAR supplied by `kineticcore_jar`. No ForgeGradle migration or Core edits are
needed. Outputs remain in this worktree's `build/libs`; no deployment occurs.

FG 6.0.24 无法反混淆 Java 21 核心发布包（字节码版本 65）；忽略目录内的本地
init 仅将核心编译依赖替换为现有同版本映射开发包。最终附属反混淆引用仍针对
26.10.4 发布核心检查，无需迁移 FG 或修改 Core。输出仅位于本工作树 build/libs。

The local init used above is:

```groovy
allprojects {
    afterEvaluate { p ->
        p.configurations.each { c ->
            c.dependencies.removeAll { d -> d.group == 'dev.xyat.kineticcore' }
        }
        p.dependencies.add('implementation', p.files('D:/IDEAWork/KineticCore/versions/1.20.1-forge/build/devlibs/kineticcore-26.10.4.jar'))
    }
}
```

Removing the Core module from `implementation` alone leaves ForgeGradle's
`__obfuscated` configuration trying to resolve it; removal from all local
configurations is necessary. The explicit release-JAR property keeps the final
API check independent of this compile substitution.

仅移除 implementation 中的核心模块仍会让 FG 的 __obfuscated 配置尝试解析它；
本地 init 需要移除各配置中的核心模块。显式发布包参数确保最终 API 检查独立于
编译替换。

Result: `BUILD SUCCESSFUL in 57s`, final Core-reference check: zero problems.
The standard `test` task ran but discovered no JUnit XML results; the 19 renderer
cases are the separately executed routing regression, not claimed JUnit tests.
Release bytecode has fitted tooltip calls 3/1/1/3 in the four pages and zero
formatted calls. Release metadata requires Core `[26.10.4,)`, keeps JAVA_17,
refmap and MixinConfigs, and excludes validation classes. The separate opt-in
validation JAR was built but neither JAR was deployed by this agent.

结果：完整构建 57 秒成功，最终 Core 引用零问题。标准 test 任务执行但没有发现
JUnit XML；19 项渲染案例来自独立路由回归，不计作 JUnit 测试。四页发布字节码
适配调用数为 3/1/1/3，格式化绕过调用为零；元数据最低核心 [26.10.4,)，保留
JAVA_17/refmap/MixinConfigs，发布包不含测试夹具。独立验证包已构建，均未部署。

Release artifact: `build/libs/taczworkshop-26.10.5.jar`, 291772 bytes.
SHA-256: `51A2E583F4549EEF016C2AF449E114D9B1A5E452163B3F03BDDA312B7CBCFB3B`.

## Final release and full-pack verification / 最终发布包与完整整合包验证

Rebuilt with the same command and `-Poutput_mods_dir=D:/NEWMODS` (without the
optional `runtimeValidationJar` task): `BUILD SUCCESSFUL in 53s`; final Core
reference check: zero problems. Final `D:/NEWMODS/taczworkshop-26.10.5.jar` is
291773 bytes, SHA-256 `FB87D8EA157175C7E849E74D8DD20448525C0BC07B1FDDA3C61B12CAF01155B6`.
The installed OTHERWORLD CLASH artifact has the same hash. Its two locales have
632 matching keys; refmap, JAVA_17 and MixinConfigs are present, with no test
fixture or bundled Core classes.

The complete existing Forge 1.20.1 pack was launched with its original 8GiB
arguments and a copied save. The old package failed the first material request
as `FormattedTooltip`. The final package passed all eight actual hover paths
in English/Chinese at 854×480 and 1536×864, automatic GUI scale: **32 captures,
zero fixture failures**. Each request was `WrappedTextTooltip`; the measured
wrapped text stayed within the available cursor-side width. All eight small
window tooltips in both languages and representative large-window images were
visually inspected, including long unbroken IDs, NBT text and styled names.
The test changes only the owned client's cached mouse coordinates; it does not
move the user's real mouse or save edited recipe/data drafts.

Evidence is in EntityControl's linked GUI-preservation worktree under
`build/gui-preservation-check/fullpack/tooltip-final/client2.out.log` and PNGs.
An intermediate fixture run pointed its header hover at the search box; the
fixture coordinate was corrected before the final complete run. Recorded
clients exited normally. Temporary fixture removed; original options and the
temporarily disabled log-deduplication configuration restored with exact hash
checks. Core source and JAR remain unchanged. TACZWorkshop is Forge-only here;
this does not claim 1.21.1/26.1.2 support.

使用相同命令改为输出 D:/NEWMODS（省略可选夹具任务），53 秒构建成功，最终核心
引用零问题。最终发布包大小 291773 字节，SHA256 如上，与完整整合包内的安装包
一致；中英文各 632 个键一致，refmap、JAVA_17、MixinConfigs 齐全，无夹具或核心类。

使用既有 Forge 1.20.1 完整包、原 8GiB 参数和存档副本：旧包第一条材料提示因
FormattedTooltip 绕过而失败；最终包八类真实悬停均使用 WrappedTextTooltip。
中英文、854×480 与 1536×864、自动 GUI 缩放共 32 张截图、零夹具失败，实际行宽
均未超过光标两侧可用空间。检查两种语言的全部小窗口提示及大窗口代表截图，
包括长连续 ID、NBT 与带颜色名称。测试只调整自启客户端内部缓存坐标，不移动
真实鼠标，不保存配方/数据草稿。中途夹具顶栏坐标误指搜索框，修正后完成整轮。
测试客户端正常退出，夹具已移除，原选项及日志去重配置已逐一恢复并核对哈希。
核心源码及 JAR 未修改；TACZWorkshop 此处仍仅支持 Forge，不代表高版本适配完成。
