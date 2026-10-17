# mall 数据库 ER 图说明

## 文件与范围

- `mall-er.drawio`：原生 draw.io 表格、字段行与业务分组容器，可直接编辑；所有页面均已删除表关联连线。
- `mall-er.png`：阅读导航页预览；全部字段与业务分组请打开 `.drawio`；字段关联见本文关系清单。
- 唯一结构来源：`document/sql/mall.sql`；只提取 DDL，不执行 SQL，不展示 INSERT 示例数据。
- 共 **76 张表、669 个字段**；文档保留 **83 条逻辑关联**，其中 **30 条经当前代码验证、53 条按 DDL 字段及业务语义推断**。DDL 没有显式外键。
- 覆盖该 DDL 的全部表（含兼容/预留表），不代表所有表都被当前业务代码使用。不扩展到 MongoDB、Redis 或 Elasticsearch。

## 页面与业务分组

| 页面 | 业务域 | 完整表数 | 字段数 |
|---|---|---:|---:|
| 00 | 阅读导航 | — | — |
| 01 | 全量分组总图 | 76 | 669 |
| 02 | 商品中心 | 18 | 170 |
| 03 | 交易与售后 | 8 | 137 |
| 04 | 营销中心 | 13 | 98 |
| 05 | 会员中心 | 13 | 108 |
| 06 | 内容中心 | 12 | 88 |
| 07 | 后台权限 | 12 | 68 |

总图与详情页均保留全部字段，不绘制任何关联连线。总图按业务域分组，各域内部按业务主题排列，主表、明细、记录及相关配置表就近摆放。详情页仅保留本业务域的完整表，已删除全部 14 张虚线跨域主键引用表；其他业务域的表请在总图或所属详情页查看。使用底部页面标签切换，也可点击导航页链接。

| 业务域 | 就近排列的业务组 |
|---|---|
| 商品中心 | 商品主档与 SKU、会员价、阶梯价、满减；品牌、运费与维护记录；分类与属性参数；评价与回复；相册与图片 |
| 交易与售后 | 订单与订单明细、操作历史；购物车与下单配置；退货申请与收发货地址、退货原因配置 |
| 营销中心 | 优惠券与领取使用历史、适用商品/分类；秒杀活动与场次、商品绑定、通知记录；首页推荐与广告 |
| 会员中心 | 会员与等级、统计、地址、登录日志；标签与偏好分类；积分、成长值与规则配置 |
| 内容中心 | 专题与分类、评论、商品关系；话题与分类、评论；帮助与分类；优选专区与商品关系 |
| 后台权限 | 用户与登录日志、角色分配；菜单及资源授权；兼容权限表及其绑定表 |

相邻摆放仅用于业务阅读，不新增关系、不表示数据库外键；例如退货原因配置与退货申请相邻，但申请的 `reason` 仍是文本字段。

## 表与字段展示

- 表头仅保留 **原始表名、原始表注释**。
- 字段区仅保留 **字段名｜类型｜注释** 三列，保留全部字段和原始顺序，字段名完整单行显示；长注释可换行、不截断。
- 全部表格统一使用清晰的实线网格：正文行列分割线为 **#64748B / 1.2**，字段标题行分割线为 **#475569 / 1.5**；业务域配色外框保持原样。
- 主键和唯一索引继续以字段名前的原生矢量图标表示：**金色钥匙＝主键，蓝色菱形＝唯一索引**，不依赖 emoji 字体。普通索引不再使用字段图标，统一列在所属表底部，列为 **普通索引名｜类型｜索引字段**，字段按 DDL 声明顺序展示。导航页和各页图例同步说明该规则。
- 当前 DDL 仅在 `sms_coupon_history` 声明两个普通索引：`idx_member_id(member_id)`、`idx_coupon_id(coupon_id)`，类型均为 `BTREE`。总图与营销详情页均展示该索引区；没有普通索引的表不添加空白索引区。
- 移除独立键属性列、可空性列、默认值列，以及表类型、存储引擎、字符集、自增及逻辑引用文字标记；逻辑关联仅在文档关系清单中保留，不能视为数据库外键。
- 单表宽度进一步从 700 缩为 **440**（再减少约 **37%**，相较最初 1,240 减少约 65%）；三列宽度分别为 185 / 95 / 160，正文 12 号、基础行高 24，字段名按原始名称单行显示，普通字段不预留图标空位；长注释完整换行。
- 表间水平间距为 **32**、垂直间距为 **24**；每个业务组采用固定列顺序，组间以小标题和留白区分，避免按最短列填充导致主子表分散。总图与详情页使用一致的业务分组。
- 已删除总图与详情页合计 **141 个连线对象**（同一逻辑关系可在不同页面重复出现），并移除空关联图层及连线图例；76 张表、669 个字段、主键和唯一索引图标均保留；14 张跨域引用表及其主键占位行已删除。
- `未注明`：DDL 缺失注释。各业务域详情页均展示完整表，不再显示虚线引用表。
- 原始字段声明附在字段行的 `dataDeclaration` 属性中；表来源附在 `dataSource` 属性中。

## 关联信息的保留方式

- 图中不再显示关联连线、鸟爪基数或 R 编号，也不保留可隐藏的关联连线图层。
- 下方关系清单保留原有 83 条字段映射及证据，便于需要时查询；“代码验证”和“DDL 推断”的证据分类不变。本次仅调整图形展示，没有重新验证业务执行路径。
- 主表与子表、关系表及业务配置表按主题相邻排列；邻近关系不表示强制 1:1、N:M 或数据库外键约束。
- 已删除全部虚线跨域引用表及相关图例，移除引用表后重新收紧业务组间距。

## 重要边界与原始 DDL 异常

- `cms_member_report.id` 未声明主键且允许 NULL，图中不擅自添加主键图标。
- `pms_product_operate_log` 没有表注释，明确标为“未注明（DDL 无表注释）”。
- 保留原始拼写：`pms_feight_template`、`pms_product_vertify_record`、`cms_prefrence_area`、`pms_comment_replay` 等。
- `cms_topic_comment` 的原始表注释为“专题评论表”，虽然属于话题评论，仍原样展示；成长值历史中的“积分”注释等也不擅自修正。
- `ums_member_statistics_info.member_id` 无唯一约束，不能按数据库保证画为强制 1:1；业务上通常预期每会员一份统计。
- 分类、菜单、权限的自引用可能使用 `0` 代表根节点；`0` 不表示必须存在 ID 为 0 的父记录。
- `ums_permission` 及其关系表在 DDL 中保留；当前已检查的授权 DAO 使用角色—菜单/资源链路。不据此删除旧权限表，也不将其全部宣称为正在使用。
- 不按昵称/用户名/操作人字符串构造会员或管理员外键；评论/举报没有明确的会员 ID 时不臆造关系。
- 退货申请的 `reason` 是文本，不连接到退货原因表 ID；退货申请没有 `order_item_id`，不擅自建立订单明细外键。
- 订单/购物车中的商品名、品牌名、收货地址、订单号等快照/冗余字段不是额外实体外键。优惠券订单关联采用 `order_id`，不重复使用非唯一 `order_sn` 建立关系。
- `pms_product_attribute_value.value`、SKU 的 `sp_data`、图片列表以及 `service_ids` 等不拆成 DDL 中不存在的表或字段。

## 关系清单与证据

“代码验证”表示具体 JOIN/赋值已核对；“DDL 推断”是设计关系，不等于物理约束。源码行号对应生成时的工作区。

| 编号 | 子表.字段 → 父表.字段 | 证据类型 | 来源 |
|---|---|---|---|
| R001 | `cms_help.category_id` → `cms_help_category.id` | DDL 推断 | `document/sql/mall.sql:26` |
| R002 | `cms_prefrence_area_product_relation.prefrence_area_id` → `cms_prefrence_area.id` | DDL 推断 | `document/sql/mall.sql:105` |
| R003 | `cms_prefrence_area_product_relation.product_id` → `pms_product.id` | 代码验证 | `mall-admin/src/main/resources/dao/PmsProductDao.xml:42` |
| R004 | `cms_subject.category_id` → `cms_subject_category.id` | DDL 推断 | `document/sql/mall.sql:128` |
| R005 | `cms_subject_comment.subject_id` → `cms_subject.id` | DDL 推断 | `document/sql/mall.sql:182` |
| R006 | `cms_subject_product_relation.subject_id` → `cms_subject.id` | DDL 推断 | `document/sql/mall.sql:201` |
| R007 | `cms_subject_product_relation.product_id` → `pms_product.id` | 代码验证 | `mall-admin/src/main/resources/dao/PmsProductDao.xml:39` |
| R008 | `cms_topic.category_id` → `cms_topic_category.id` | DDL 推断 | `document/sql/mall.sql:229` |
| R009 | `cms_topic_comment.topic_id` → `cms_topic.id` | DDL 推断 | `document/sql/mall.sql:272` |
| R010 | `oms_cart_item.product_id` → `pms_product.id` | DDL 推断 | `document/sql/mall.sql:290` |
| R011 | `oms_cart_item.product_sku_id` → `pms_sku_stock.id` | DDL 推断 | `document/sql/mall.sql:291` |
| R012 | `oms_cart_item.member_id` → `ums_member.id` | DDL 推断 | `document/sql/mall.sql:292` |
| R013 | `oms_cart_item.product_category_id` → `pms_product_category.id` | DDL 推断 | `document/sql/mall.sql:303` |
| R014 | `oms_order.member_id` → `ums_member.id` | 代码验证 | `mall-portal/src/main/java/com/macro/mall/portal/service/impl/OmsPortalOrderServiceImpl.java:188` |
| R015 | `oms_order.coupon_id` → `sms_coupon.id` | DDL 推断 | `document/sql/mall.sql:447` |
| R016 | `oms_order_item.order_id` → `oms_order.id` | 代码验证 | `mall-admin/src/main/resources/dao/OmsOrderDao.xml:84`；`mall-portal/src/main/resources/dao/PortalOrderDao.xml:24` |
| R017 | `oms_order_item.product_id` → `pms_product.id` | DDL 推断 | `document/sql/mall.sql:569` |
| R018 | `oms_order_item.product_sku_id` → `pms_sku_stock.id` | DDL 推断 | `document/sql/mall.sql:576` |
| R019 | `oms_order_item.product_category_id` → `pms_product_category.id` | DDL 推断 | `document/sql/mall.sql:578` |
| R020 | `oms_order_operate_history.order_id` → `oms_order.id` | 代码验证 | `mall-admin/src/main/resources/dao/OmsOrderDao.xml:85` |
| R021 | `oms_order_return_apply.order_id` → `oms_order.id` | DDL 推断 | `document/sql/mall.sql:751` |
| R022 | `oms_order_return_apply.company_address_id` → `oms_company_address.id` | 代码验证 | `mall-admin/src/main/resources/dao/OmsOrderReturnApplyDao.xml:56` |
| R023 | `oms_order_return_apply.product_id` → `pms_product.id` | DDL 推断 | `document/sql/mall.sql:753` |
| R024 | `pms_album_pic.album_id` → `pms_album.id` | DDL 推断 | `document/sql/mall.sql:874` |
| R025 | `pms_comment.product_id` → `pms_product.id` | DDL 推断 | `document/sql/mall.sql:924` |
| R026 | `pms_comment_replay.comment_id` → `pms_comment.id` | DDL 推断 | `document/sql/mall.sql:951` |
| R027 | `pms_member_price.product_id` → `pms_product.id` | 代码验证 | `mall-admin/src/main/resources/dao/PmsProductDao.xml:33` |
| R028 | `pms_member_price.member_level_id` → `ums_member_level.id` | DDL 推断 | `document/sql/mall.sql:991` |
| R029 | `pms_product.brand_id` → `pms_brand.id` | DDL 推断 | `document/sql/mall.sql:1087` |
| R030 | `pms_product.product_category_id` → `pms_product_category.id` | 代码验证 | `mall-admin/src/main/resources/dao/PmsProductDao.xml:30` |
| R031 | `pms_product.feight_template_id` → `pms_feight_template.id` | DDL 推断 | `document/sql/mall.sql:1089` |
| R032 | `pms_product.product_attribute_category_id` → `pms_product_attribute_category.id` | DDL 推断 | `document/sql/mall.sql:1090` |
| R033 | `pms_product_attribute.product_attribute_category_id` → `pms_product_attribute_category.id` | 代码验证 | `mall-admin/src/main/resources/dao/PmsProductAttributeCategoryDao.xml:16`；`mall-admin/src/main/resources/dao/PmsProductAttributeDao.xml:11` |
| R034 | `pms_product_attribute_value.product_id` → `pms_product.id` | 代码验证 | `mall-admin/src/main/resources/dao/PmsProductDao.xml:35`；`mall-search/src/main/resources/dao/EsProductDao.xml:39` |
| R035 | `pms_product_attribute_value.product_attribute_id` → `pms_product_attribute.id` | 代码验证 | `mall-search/src/main/resources/dao/EsProductDao.xml:40` |
| R036 | `pms_product_category.parent_id` → `pms_product_category.id` | 代码验证 | `mall-admin/src/main/resources/dao/PmsProductCategoryDao.xml:15` |
| R037 | `pms_product_category_attribute_relation.product_category_id` → `pms_product_category.id` | DDL 推断 | `document/sql/mall.sql:1494` |
| R038 | `pms_product_category_attribute_relation.product_attribute_id` → `pms_product_attribute.id` | 代码验证 | `mall-admin/src/main/resources/dao/PmsProductAttributeDao.xml:10` |
| R039 | `pms_product_full_reduction.product_id` → `pms_product.id` | 代码验证 | `mall-admin/src/main/resources/dao/PmsProductDao.xml:32`；`mall-portal/src/main/resources/dao/PortalProductDao.xml:68` |
| R040 | `pms_product_ladder.product_id` → `pms_product.id` | 代码验证 | `mall-admin/src/main/resources/dao/PmsProductDao.xml:31`；`mall-portal/src/main/resources/dao/PortalProductDao.xml:67` |
| R041 | `pms_product_operate_log.product_id` → `pms_product.id` | DDL 推断 | `document/sql/mall.sql:1618` |
| R042 | `pms_product_vertify_record.product_id` → `pms_product.id` | DDL 推断 | `document/sql/mall.sql:1642` |
| R043 | `pms_sku_stock.product_id` → `pms_product.id` | 代码验证 | `mall-admin/src/main/resources/dao/PmsProductDao.xml:34`；`mall-portal/src/main/resources/dao/PortalProductDao.xml:39` |
| R044 | `sms_coupon_history.coupon_id` → `sms_coupon.id` | 代码验证 | `mall-portal/src/main/resources/dao/SmsCouponHistoryDao.xml:30`；`mall-portal/src/main/resources/dao/SmsCouponHistoryDao.xml:42` |
| R045 | `sms_coupon_history.member_id` → `ums_member.id` | DDL 推断 | `document/sql/mall.sql:1817` |
| R046 | `sms_coupon_history.order_id` → `oms_order.id` | DDL 推断 | `document/sql/mall.sql:1824` |
| R047 | `sms_coupon_product_category_relation.coupon_id` → `sms_coupon.id` | 代码验证 | `mall-admin/src/main/resources/dao/SmsCouponDao.xml:24`；`mall-portal/src/main/resources/dao/PortalProductDao.xml:85` |
| R048 | `sms_coupon_product_category_relation.product_category_id` → `pms_product_category.id` | DDL 推断 | `document/sql/mall.sql:1858` |
| R049 | `sms_coupon_product_relation.coupon_id` → `sms_coupon.id` | 代码验证 | `mall-admin/src/main/resources/dao/SmsCouponDao.xml:23`；`mall-portal/src/main/resources/dao/PortalProductDao.xml:95` |
| R050 | `sms_coupon_product_relation.product_id` → `pms_product.id` | DDL 推断 | `document/sql/mall.sql:1876` |
| R051 | `sms_flash_promotion_log.member_id` → `ums_member.id` | DDL 推断 | `document/sql/mall.sql:1915` |
| R052 | `sms_flash_promotion_log.product_id` → `pms_product.id` | DDL 推断 | `document/sql/mall.sql:1916` |
| R053 | `sms_flash_promotion_product_relation.flash_promotion_id` → `sms_flash_promotion.id` | DDL 推断 | `document/sql/mall.sql:1934` |
| R054 | `sms_flash_promotion_product_relation.flash_promotion_session_id` → `sms_flash_promotion_session.id` | DDL 推断 | `document/sql/mall.sql:1935` |
| R055 | `sms_flash_promotion_product_relation.product_id` → `pms_product.id` | 代码验证 | `mall-admin/src/main/resources/dao/SmsFlashPromotionProductRelationDao.xml:24`；`mall-portal/src/main/resources/dao/HomeDao.xml:32` |
| R056 | `sms_home_brand.brand_id` → `pms_brand.id` | 代码验证 | `mall-portal/src/main/resources/dao/HomeDao.xml:15` |
| R057 | `sms_home_new_product.product_id` → `pms_product.id` | 代码验证 | `mall-portal/src/main/resources/dao/HomeDao.xml:42` |
| R058 | `sms_home_recommend_product.product_id` → `pms_product.id` | 代码验证 | `mall-portal/src/main/resources/dao/HomeDao.xml:55` |
| R059 | `sms_home_recommend_subject.subject_id` → `cms_subject.id` | 代码验证 | `mall-portal/src/main/resources/dao/HomeDao.xml:68` |
| R060 | `ums_admin_login_log.admin_id` → `ums_admin.id` | DDL 推断 | `document/sql/mall.sql:2188` |
| R061 | `ums_admin_permission_relation.admin_id` → `ums_admin.id` | DDL 推断 | `document/sql/mall.sql:2618` |
| R062 | `ums_admin_permission_relation.permission_id` → `ums_permission.id` | DDL 推断 | `document/sql/mall.sql:2619` |
| R063 | `ums_admin_role_relation.admin_id` → `ums_admin.id` | DDL 推断 | `document/sql/mall.sql:2634` |
| R064 | `ums_admin_role_relation.role_id` → `ums_role.id` | 代码验证 | `mall-admin/src/main/resources/dao/UmsAdminRoleRelationDao.xml:14`；`mall-admin/src/main/resources/dao/UmsAdminRoleRelationDao.xml:27` |
| R065 | `ums_growth_change_history.member_id` → `ums_member.id` | DDL 推断 | `document/sql/mall.sql:2655` |
| R066 | `ums_integration_change_history.member_id` → `ums_member.id` | DDL 推断 | `document/sql/mall.sql:2676` |
| R067 | `ums_member.member_level_id` → `ums_member_level.id` | 代码验证 | `mall-portal/src/main/java/com/macro/mall/portal/service/impl/UmsMemberServiceImpl.java:90` |
| R068 | `ums_member_login_log.member_id` → `ums_member.id` | DDL 推断 | `document/sql/mall.sql:2786` |
| R069 | `ums_member_member_tag_relation.member_id` → `ums_member.id` | DDL 推断 | `document/sql/mall.sql:2805` |
| R070 | `ums_member_member_tag_relation.tag_id` → `ums_member_tag.id` | DDL 推断 | `document/sql/mall.sql:2806` |
| R071 | `ums_member_product_category_relation.member_id` → `ums_member.id` | DDL 推断 | `document/sql/mall.sql:2820` |
| R072 | `ums_member_product_category_relation.product_category_id` → `pms_product_category.id` | DDL 推断 | `document/sql/mall.sql:2821` |
| R073 | `ums_member_receive_address.member_id` → `ums_member.id` | DDL 推断 | `document/sql/mall.sql:2835` |
| R074 | `ums_member_statistics_info.member_id` → `ums_member.id` | DDL 推断 | `document/sql/mall.sql:2881` |
| R075 | `ums_menu.parent_id` → `ums_menu.id` | DDL 推断 | `document/sql/mall.sql:2942` |
| R076 | `ums_permission.pid` → `ums_permission.id` | DDL 推断 | `document/sql/mall.sql:2987` |
| R077 | `ums_resource.category_id` → `ums_resource_category.id` | DDL 推断 | `document/sql/mall.sql:3031` |
| R078 | `ums_role_menu_relation.role_id` → `ums_role.id` | 代码验证 | `mall-admin/src/main/resources/dao/UmsRoleDao.xml:19` |
| R079 | `ums_role_menu_relation.menu_id` → `ums_menu.id` | 代码验证 | `mall-admin/src/main/resources/dao/UmsRoleDao.xml:20`；`mall-admin/src/main/resources/dao/UmsRoleDao.xml:40` |
| R080 | `ums_role_permission_relation.role_id` → `ums_role.id` | DDL 推断 | `document/sql/mall.sql:3170` |
| R081 | `ums_role_permission_relation.permission_id` → `ums_permission.id` | DDL 推断 | `document/sql/mall.sql:3171` |
| R082 | `ums_role_resource_relation.role_id` → `ums_role.id` | 代码验证 | `mall-admin/src/main/resources/dao/UmsAdminRoleRelationDao.xml:28` |
| R083 | `ums_role_resource_relation.resource_id` → `ums_resource.id` | 代码验证 | `mall-admin/src/main/resources/dao/UmsAdminRoleRelationDao.xml:29`；`mall-admin/src/main/resources/dao/UmsRoleDao.xml:57` |

## 结构校验

本次校验结果：

- 前轮已导出并检查全部 8 页；本轮重新导出各页及代表性表格局部，检查字段名完整显示与紧凑布局。
- 总图包含 76 张完整表、669 个字段；总图与详情页共保留 152 个完整表实例、1,338 个完整字段实例；无跨域引用表或占位字段。
- 已删除 141 个连线对象；逐页检查确认 `edge="1"` 对象数量均为 0。
- 已检查全部 1,338 个字段显示实例，去除 6 处字段名中的预置换行；按 Arial 12 号字体逐项测量，确认字段名、左右留白均能容纳于 185 宽字段列。仅因字段名换行撑高的 6 个字段行已缩短，注释文本及换行保留。主键、唯一索引图标与普通索引区均保留；表宽仍为 440。
- 每页 ID 唯一、父级引用有效；表格均位于容器内，同一容器内的表格没有重叠。页面导航链接保持不变且目标页面存在。
- 已检查业务分组标题、表格间距，以及订单/明细、优惠券/历史、会员/统计、评论/回复、相册/图片等表的邻近排列。
- `mall-er.png` 为 4,859 × 3,227 像素的导航页图片，不是全量 ER 图的扁平导出。完整字段请在 draw.io 中放大阅读。

本轮统一字段名单行显示，去除普通字段的图标占位缩进，按内容收紧行高和纵向间距，并同步导航预览及本说明。未修改业务代码或数据库；临时脚本、修改前备份、预览及校验记录位于 `/tmp/mall-er-nowrap/`。
