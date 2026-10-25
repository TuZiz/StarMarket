# StarMarket 1.3.5 · 星海玩家现货交易所

1.3.5：所有有效物品均可寄售，无需配置材质白名单。名称、附魔、耐久及自定义数据保留，不同数据分别展示；只有配置正数回收价且允许回收的标准物品可卖给系统。寄售单价大于零、符合金币最小单位，单价最高10亿金币（Vault现有安全边界），不再使用商品旧价格区间。未配置物品使用内置原版中文名，分类为“其他物品”。上架税规则不变。`/market help` 和 `/market admin help` 提供分类帮助；错误后仅显示简短重试提示。语言文件自动迁移到schema3并备份，保留未知字段和注释。

1.3.4 简化上架：点击首页“出售物品”会关闭 GUI，聊天提示玩家手持物品并输入单个售价；输入即确认按当前主手格子的整叠数量上架、立即扣除配置的上架税，不再打开选物或调价/确认页面。空手、价格无效或不符合物品规则时提示并允许重试；输入 `cancel` 返回首页。输入前会明确告知税率与撤单/到期不退税规则，提交时显示物品、数量、单价、挂牌总额及税额。

系统回收是首页独立按钮，打开 `gui/recycle.yml`：点击背包选整叠，确认后按固定保底价立即付款，免税且不进入市场库存。原 `gui/stack-sale.yml`、`gui/select-item.yml` 不再加载，服务器磁盘上的旧配置不删除。首页 `gui/browse.yml` 从 schema1迁至2，先保存 `.v1-chat-sale-时间戳.bak`；只在导航行空白或边框格加入 B 回收入口，保留其他布局、文案、注释和未知字段。若没有可用格则提示文件和导航行，不覆盖其他按钮。

1.3.5 验证：55项JUnit测试通过；隔离Leaf的119项运行检查通过，包含未配置泥土真实上架、自由售价、3%扣税、元数据分组与回收拒绝，使用测试玩家替身和临时Vault提供者；生产服未重启、真人客户端待验证。

1.3.4 验证：51 项 JUnit、隔离 Leaf26.3-4 上111项运行检查通过。聊天上架覆盖主手选择、整叠与单价、3%税额、重复消息/回调、空手/错误价格重试、cancel、断线、重载清理、经济服务缺失与明确扣税失败后的可靠返还；回收覆盖固定免税付款、特殊物品拒绝、旧确认失效且不新增寄售库存。测试使用实际聊天事件、库存、SQLite和临时Vault提供者，玩家连接为测试替身；生产服未重启、真人操作尚待确认。

1.3.2 修复普通商品显示英文 ID：默认三种商品缺少 name 时，按材质使用随包配置中的中文名称；已有订单也按当前商品配置显示，历史库存数据无需重写。自定义物品优先保留 display_name 或 item_name。其他扩展物品请在 products/*.yml 中设置 name。

1.3.1 修复“我的上架”撤单成功后残留旧按钮：点击后立即禁用该订单操作，完成后移除旧图标并刷新当前分页；最后一页删空时回到有效页。已撤回或售完的旧订单点击后同样刷新；关闭或切换菜单后，迟到结果不会抢回旧界面。

玩家通过聊天输入单价，固定寄售当前主手格子的整叠真实物品；买家按价格从低到高、同价按上架时间购买。没有真实玩家现货时，首页不展示系统配置的空商品。没有求购、买单挂单、预购或系统无限出售。

1.3.0 取消玩家领取仓库和手动领取命令。购买、撤单、到期和已确认失败返还的物品自动进入背包，空间不足的部分直接掉在玩家脚下；离线物品上线后自动补发。卖家收益通过 Vault 自动结算，支持离线到账。内部保留可靠的待交付流水，已有未领取资产继续自动交付；不删除玩家资产。未知外部操作结果继续冻结、人工对账。

沿用玩家修改的 GUI Shape：商品预览跟随 I 符号，内容按中间行空格分页，四行可显示 28 件。精简购买页的全部价格档位在汇总 Lore 中展示，不覆盖支付/取消，不补回已删除按钮。

## 安装与升级

当前依据实际服务端：Leaf 26.3-4，Minecraft 26.3，Java 25，Vault API 1.7.1。Purpur 和其他版本尚未独立运行验证，Folia不声明兼容。

1. 服务器安装 Vault 及现有 Vault Economy 提供者。无需 CraftEngine、Quest 或其他业务插件。
2. 停服备份 `plugins/StarMarket`。仅保留一个 StarMarket JAR，替换为 `StarMarket-1.3.5.jar`，启动。
3. 首次升级自动迁移配置与数据库。旧 `products.yml` 保留为原件，额外保存 `products.yml.pre-catalog.bak`；实际加载改为 `products/`。数据库 schema 1/2 升至 3 前通过 SQLite VACUUM 保存包含已提交 WAL 数据的备份。
4. 原有订单按“旧最低价与旧市场参考价的较大值”固定为售价，数量、归属、待交付资产和异常流水保留。语言文件自动升至 schema 3，保留备份、未知字段和注释。旧仓库菜单不再加载；旧首页或订单页的仓库 C 按钮不再渲染。未知配置或数据库版本拒绝启动，不覆盖原文件。

测试辅助插件不随生产 JAR 交付。源码仓库不包含服务器数据、经济账本、资源包实验、构建产物或第三方依赖 JAR。

## 上架税（本次确认的规则）

确认上架时立即按“数量 × 单个售价 × listing-tax-rate”通过Vault扣税，默认3%；撤单与到期不退税。成交收入全额结算，不再扣成交税；领取也不收费。系统保底回收按配置金额全额付款，不产生税单。

税额以金币最小单位向上取整；例如总挂牌640金币，税19.2金币。小额税不足0.01金币时（最小单位0.01）按0.01金币收取，避免通过拆小单让税额被舍去。允许将税率设为0关闭上架税。

`trading.yml` 升为schema-version 2，新增 `listing-tax-rate: 0.03`。旧文件先保存 `trading.yml.v1.bak`，保留原字段和注释；旧fee-rate字段停用，不会同时扣两种税。已有上架订单不追补税；升级前已预留的购买仍沿用其已保存分账快照，避免破坏未决交易。

上架使用聊天输入，输入价格即确认，不再使用上架 GUI；提示明确说明税率、立即扣税及撤单/过期不退规则。`gui/recycle.yml` 只负责系统回收。旧上架菜单文件保留但不使用。

上架失败不视为撤单：明确扣税失败时返还物品、不创建订单；扣款结果不确定时保留税单与交易冻结，不自动重扣或退款。独立listing_taxes表保存挂牌总额、税率快照、应扣税额与外部操作状态。管理员可用 `/market admin taxes [玩家UUID]` 查询最近50笔税单。

新税单冻结对账（必须先核对玩家物品和外部经济账本）：

- `admin reconcile 交易ID tax-paid 证据说明`：确认物品已扣且税已付，完成上架；不再扣款。
- `admin reconcile 交易ID tax-unpaid 证据说明`：确认物品已扣但税未付，取消上架、返仓物品。
- `admin reconcile 交易ID not-removed 证据说明`：确认物品未扣且税未付，关闭流水。
- `admin reconcile 交易ID tax-paid-not-removed 证据说明`：确认税已付但背包保存回滚导致原物品仍在，关闭失败上架，建立自动退款流水，不重复返物。

旧版无税单的未决上架仍使用原有removed/not-removed对账选项。错误的人工证明会造成重复资产，不能猜测后执行。

## 玩家操作

- `/market`：打开首页，只有实际可购买的玩家现货。相同完整物品数据合并一行；自己的库存不计入可购买数量。
- 点击“出售物品”，关闭菜单后手持需要上架的物品，在聊天输入单个售价；输入即确认寄售整叠物品并支付上架税，输入 `cancel` 取消。可以在输入前切换主手物品，不要求点击按钮时已经持有它。
- 金额必须符合金币最小单位及该商品的配置上下限。收到价格后在主线程读取并校验主手物品，后续扣物时再次校验该原始格子的完整数据和数量；发生变化就取消，不从其他格子凑数。重复输入及迟到回调不会再次上架；重新打开菜单、退出或重载会取消旧输入。
- 点击首页“系统回收”进入独立回收页，选择背包整叠物品并确认：仅对无特殊元数据且配置允许回收的资源生效，固定价免税付款后物品永久消耗，不进入市场库存。
- 买家通过分类或搜索物品名/材质，选择数量，查看各价格档位、平均单价和总额。档位超过21条可翻页查看全部。
- 报价有效15秒。确认时重新检查库存分配、售价、权限、金币；发生变化要求重新报价，不擅自接受更高价。
- 成功购买后自动放入背包，放不下的部分掉在玩家脚下；地面物品按服务器正常掉落规则处理。离线时保留内部待交付流水，上线后自动发放。
- “我的上架”查看剩余、已售、净收入与有效期，点击撤回未售物品；撤单、到期和明确失败返还均按背包优先、溢出掉脚下自动交付。订单涉及未决付款时暂停撤回/过期返还，先人工对账。
- 卖家离线仍可成交；成交收入持久化后自动通过 Vault 付款，不需要领取。明确拒付保留流水并冷却后重试；超时、异常和结果不确定冻结，不盲目重付。
- 后台每秒检查可执行的交付；每轮最多派发 32 名玩家、每人 8 笔，每笔物品最多 4 叠、256 个。大单分批自动发放，避免一次购买阻塞主线程或瞬间生成大量掉落物。

辅助命令：`/market sell`、`/market list` 进入聊天上架；`/market recycle` 打开系统回收；`/market mine`、`/market history`、`/market search 关键词`、`/market category 分类名`、`/market confirm`、`/market cancel 订单ID`。不再提供 `/market warehouse` 或 `/market claim`。

普通功能统一权限 `starmarket.use`（默认允许），管理权限 `starmarket.admin`（默认OP）。

## 配置结构

```text
StarMarket/
  config.yml                # 金币最小单位
  trading.yml               # 上架税率、个人订单/物品上限、有效期
  products/                 # 递归读取 .yml/.yaml
    cobblestone.yml         # 旧配置自动按商品拆分；也可手工按主题合并
    oak_log.yml
    iron_ingot.yml
    equipment/
      swords.yml            # 可以自行添加
  gui/                      # 每个文件一个菜单，可配置中文、Shape、材质和文案
    browse.yml
    recycle.yml
    item.yml
    purchase.yml
    listings.yml
    records.yml
    categories.yml
  lang/zh_CN.yml
  market.db
```

旧版 `gui/main.yml` 等文件会保留但不再使用。语言文件按版本迁移旧提示并补充缺少的键，保留未知字段、未迁移的自定义文案与注释。旧 `config.yml` 中上限首次迁入 `trading.yml`，此后以新文件为准。

`products/` 一个文件可以放一个或多个规则。所有文件必须有 `schema-version: 1`，商品ID和材质不能重复；同一材质只配置一次，其元数据变体自动分别分组。目录任意嵌套，不扫描其他扩展名。配置错误会指出文件、路径和值，不会静默跳过。

普通资源示例：

```yaml
schema-version: 1
products:
  cobblestone:
    name: '<#88CCFF>圆石'
    material: 'COBBLESTONE'
    category: '建筑材料'
    reference-price: 1
    min-price: 0.2
    max-price: 5
    recycle-price: 0.1
    allow-metadata: false
```

装备/自定义物品载体示例（自行添加，不会默认生成库存）：

```yaml
schema-version: 1
products:
  diamond_sword:
    name: '<#88CCFF>钻石剑与自定义装备'
    material: 'DIAMOND_SWORD'
    category: '装备'
    reference-price: 100
    min-price: 1
    max-price: 10000
    recycle-price: 0
    recycle-enabled: false
    allow-metadata: true
```

`allow-metadata` 保留兼容读取，但不再限制寄售。所有材质都可以携带完整数据交易，包括名称、Lore、附魔、耐久、属性、PDC及可序列化组件；不同完整数据不会合并。系统回收仍只接受配置的标准物品。`products/` 用于回收价、名称、分类及参考参数，不是寄售白名单；`min-price`/`max-price` 不再限制挂牌单价。已有商品ID关联历史订单，请勿直接删除或改材质。

所有金额单位为Vault金币，必须是 `config.yml` 中 `money-unit` 的整数倍。数据库使用Long最小单位，换算使用BigDecimal；Vault边界依照提供者小数精度校验。单次外部支付额限制10亿金币，超出拒绝；不要在已有数据库上修改money-unit。参考价可选调节参数：`sensitivity` 默认0.3、`max-change` 默认0.05、`target-stock` 默认4096。它们只影响参考价。

`trading.yml`：`listing-tax-rate` 为上架税率（0至小于1）；`order-limit` 为每玩家未售完订单数；`stock-limit` 为每玩家托管数量及单次请求上限；`listing-hours` 为有效小时数。上架税按整张订单总额计算并向上取整到最小单位，成交不重复计税。

`/market admin reload` 重载商品规则、交易参数、语言与GUI，并清理旧界面/报价。重载不允许移除旧商品ID、改变原材质或金币最小单位；订单售价不会跟随参考价/配置变化。已有订单到期时间保留，上限降低影响后续上架。

## 交易安全与恢复

SQLite由唯一数据库执行器串行持有，背包/GUI/Vault操作回到Leaf主线程。真实库存先预留、持久化外部操作阶段，再调用Vault。明确扣款失败恢复库存；未知异常冻结，禁止自动重复付款。卖家收益、成交记录、买家物品和累计金额在同一数据库事务落盘；上架税在上架准备阶段固定；成交不再扣税，旧预留分账按原快照处理。

托管和自动交付均保存完整物品数据。内部交付流水沿用 claims 表，与玩家仓库功能无关。先写 EXTERNAL 再发货、生成溢出掉落物或付款，成功后写 DONE。崩溃后未完成的 ITEMS/EXTERNAL 转 FROZEN，不猜测背包、地面实体或外部金币是否已变更。任何外部效果开始后的异常均冻结，并记录交付 ID 与归属；掉落被其他插件取消也需对账，防止误判后重复发货。背包、世界持久化和外部经济无法与 SQLite 构成统一事务，因此这些窗口必须通过人工账本/玩家物品核验，不能声称跨系统 exactly-once。

管理员命令：`admin status`、`orders 玩家UUID`、`exceptions`、`turnover 玩家UUID`、`pause 商品ID`、`resume 商品ID`、`price 商品ID 参考价`、`reset 商品ID`、`reload`。管理操作记入控制台与audit表。

冻结对账先停服备份数据库并查阅经济提供者账本/玩家物品，确认事实后使用：

- `admin reconcile 交易ID paid 证据说明`：购买确已扣款则完成分账/入仓；回收确已付款则关闭流水。
- `admin reconcile 交易ID not-paid 证据说明`：购买确认未扣款，恢复库存。
- `admin reconcile 交易ID removed 证据说明`：上架确认物品已扣除，完成托管。
- `admin reconcile 交易ID not-removed 证据说明`：上架/回收确认没有扣物品，关闭流水。
- `admin reconcile 交易ID removed-unpaid 证据说明`：回收已扣物但未付款，建立待发金币。
- `admin claim-resolve 交付ID delivered 证据说明` / `not-delivered`：确认已交付则关闭，确认未交付则恢复自动交付。该管理对账命令保留，普通玩家无需操作。

不要凭猜测解除冻结；错误的人工证明仍可能引起重复资产。旧1.0版未决交易未保存独立手续费快照，处理这类历史异常时还需核对当时手续费，不应直接按新配置猜测。

## API与验证

入口实现 `MarketAccess.openMarket(Player)`，可由NPC插件调用。`MarketEvent` 主线程发布CONSIGN、SALVAGE、BUY、SOLD、TURNOVER，包含玩家、商品、数量、最小金额单位和交易ID。累计金额写入turnover表，可通过管理命令查询。未接入Quest、求购或预购。

### 从源码构建

需要 Java 25 和 Maven。本版本使用已核实的 Leaf API `26.3.build.4-alpha`，不把 Minecraft 版本号当成 Paper/Bukkit API 版本号，也不声明其他服务端已验证。

1. 从匹配的 Leaf 服务端依赖目录取得 `leaf-api-26.3.build.4-alpha.jar`，放到 `libs/leaf-api.jar`；或在构建时用 `-Dleaf.api.jar=/absolute/path/to/leaf-api.jar` 指定绝对路径。
2. 准备 VaultAPI 1.7.1 编译依赖，放入本地 `libs/VaultAPI-1.7.1.jar`，然后安装到 Maven 本地仓库：

```shell
mvn install:install-file -Dfile=libs/VaultAPI-1.7.1.jar -DgroupId=com.github.MilkBowl -DartifactId=VaultAPI -Dversion=1.7.1 -Dpackaging=jar -DgeneratePom=true
mvn clean package
```

输出 `target/v1.3.5/StarMarket-1.3.5.jar`；`mvn test` 运行单元测试。`libs/` 中的第三方 JAR 需自行准备，未上传仓库。

运行库仅 Kotlin、SQLite 等必要依赖；Leaf API 和 VaultAPI 不进入生产 JAR，服务端仍需安装 Vault 及正式经济提供者。

底层回归包含真实背包填充与脚下实体掉落、部分溢出、离线保留与上线补发、预留后断线、离线金币到账、明确拒付与未知结果冻结、重复派发、发货后异常、掉落被取消、RPG名称/附魔/PDC和非堆叠物品分批；本版检查数量与新增范围见文首。测试辅助代码位于 `tools/RuntimeProbe.java`、`tools/HandListingProbe.java`、`tools/ListingRefreshProbe.java` 和 `tools/AutomaticDeliveryProbe.java`，不进入生产JAR。

尚未执行真实玩家客户端完整点击验收、生产经济提供者端到端扣款和故障注入、Purpur独立运行验证。隔离测试使用临时Vault测试提供者，未修改正式经济余额。
