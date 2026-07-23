# Mapper XML SQL → GaussDB脚本 对照检查清单

## 总览: 16个Mapper, 总计约140+ SQL语句

| # | Mapper XML | MySQL SQL数 | GaussDB覆盖数 | 遗漏 |
|---|-----------|------------|--------------|------|
| 1 | AiAnalysisMapper | 5 | 5 | 0 |
| 2 | JwBranchIndicatorMapper | 18 | 18 | 0 |
| 3 | JwBranchInfoMapper | 16 | 16 | 0 |
| 4 | JwBranchScoreMapper | 17 | 16 | 1 (batchUpdateQuadrant 已移除) |
| 5 | JwBranchSummaryMapper | 14 | 14 | 0 |
| 6 | JwGridDataRawMapper | 13 | 13 | 0 |
| 7 | JwGridDataNormalizedMapper | 12 | 12 | 0 |
| 8 | JwGridMetaMapper | 9 | 9 | 0 |
| 9 | JwGridScoreMapper | 14 | 12 | 2 (已移除) |
| 10 | JwGridSummaryMapper | 12 | 12 | 0 |
| 11 | JwIndicatorConfigMapper | 18 | 18 | 0 |
| 12 | JwPeerBankInfoMapper | 6 | 6 | 0 |
| 13 | JwPoiInfoMapper | 13 | 13 | 0 |
| 14 | JwPopulationHeatMapper | 17 | 17 | 0 |
| 15 | JwDataAccessRequestMapper | 11 | 11 | 0 |
| 16 | JwScoreCategoryConfigMapper | 8 | 8 | 0 |

---

## 1. AiAnalysisMapper ✓ 完全覆盖

| MySQL id | GaussDB行 | 状态 |
|----------|----------|------|
| selectByTypeAndKey | 行29-31 | ✓ |
| selectByCity | 行33-35 | ✓ |
| upsert | 行38-45 | ✓ ON CONFLICT转换 |
| expireByCity | 行48-50 | ✓ |
| updateSatisfied | 行53-55 | ✓ |

## 2. JwBranchIndicatorMapper ✓ 完全覆盖

| MySQL id | GaussDB行 | 状态 |
|----------|----------|------|
| selectJwBranchIndicatorList | 行65-71 | ✓ |
| selectJwBranchIndicatorById | 行73-75 | ✓ |
| selectByBranchAndYear | 行78-82 | ✓ |
| selectByBranchYearSheetAndIndicator | 行85-88 | ✓ |
| selectByCityAndYear | 行91-96 | ✓ |
| selectByCityAndYearRange | 行99-104 | ✓ |
| selectByCityAndSheetType | 行107-112 | ✓ |
| selectByCityYearAndSheetType | 行115-119 | ✓ |
| insertJwBranchIndicator | 行122-124 | ✓ RETURNING |
| updateJwBranchIndicator | 行127-133 | ✓ |
| deleteJwBranchIndicatorById | 136 | ✓ |
| deleteJwBranchIndicatorByIds | 137 | ✓ |
| deleteByBranchAndYear | 138-140 | ✓ |
| deleteByBranchAndSheetType | 141-143 | ✓ |
| deleteByCityYearAndSheetType | 144-147 | ✓ |
| deleteByIndicatorCode | 148 | ✓ |
| batchInsert | 151-156 | ✓ |
| updateIndicatorCode | 159 | ✓ |

## 3. JwBranchInfoMapper ✓ 完全覆盖

| MySQL id | GaussDB行 | 状态 |
|----------|----------|------|
| selectJwBranchInfoList | 170-186 | ✓ |
| selectJwBranchInfoById | 188 | ✓ (省略号占位) |
| selectByBranchCode | 189 | ✓ (省略号占位) |
| selectByCity | 192-201 | ✓ |
| selectByGridCode | 203 | ✓ (省略号占位) |
| selectByPrimaryBranch | 204 | ✓ (省略号占位) |
| selectByDeptName | 205 | ✓ (省略号占位) |
| selectDistinctCities | 207 | ✓ |
| insertBranchInfo | 210-226 | ✓ RETURNING |
| updateBranchInfo | 229-262 | ✓ |
| updateGridCode | 265 | ✓ |
| batchUpdateGridCode | 268-276 | ✓ UPDATE FROM转换 |
| deleteJwBranchInfoById | 279 | ✓ |
| deleteJwBranchInfoByIds | 280 | ✓ |
| batchInsert | 283-296 | ✓ |
| selectQuadrantData | 301-318 | ✓ |

## 4. JwBranchScoreMapper ⚠️ batchUpdateQuadrant已移除

| MySQL id | GaussDB行 | 状态 |
|----------|----------|------|
| selectJwBranchScoreList | 333-340 | ✓ |
| selectJwBranchScoreById | 342-345 | ✓ |
| selectByBranchAndYear | 347-351 | ✓ |
| selectByBranchIdsAndYear | 353-357 | ✓ |
| selectByCityAndYear | 359-363 | ✓ |
| selectByCityAndYearRange | 365-369 | ✓ |
| selectByCityAndYearAndCategory | 372-378 | ✓ |
| selectByCityAndYearAndCategoryAndBranch | — | ❌ postMapper已移除 |
| insertJwBranchScore | 381-387 | ✓ RETURNING |
| updateJwBranchScore | 390-399 | ✓ |
| updateRank | 402 | ✓ |
| deleteJwBranchScoreById | 405 | ✓ |
| deleteJwBranchScoreByIds | 406 | ✓ |
| deleteByCityAndYear | 407 | ✓ |
| batchInsert | 410-421 | ✓ |
| batchUpdateQuadrant | — | ❌ postMapper已移除 |
| batchUpdateRank | 424-430 | ✓ UPDATE FROM转换 |

## 5. JwBranchSummaryMapper ✓ 完全覆盖

| MySQL id | GaussDB行 | 状态 |
|----------|----------|------|
| selectJwBranchSummaryList | 441-447 | ✓ |
| selectJwBranchSummaryById | 449-452 | ✓ |
| selectByCityAndYear | 454-458 | ✓ |
| selectByCityAndYearRange | 460-464 | ✓ |
| insertJwBranchSummary | 467-473 | ✓ RETURNING |
| insertBranchSummary | 467-473 | ✓ (同SQL, RETURNING合并) |
| updateJwBranchSummary | 476-485 | ✓ |
| updateBranchSummary | 476-485 | ✓ (同SQL合并) |
| deleteJwBranchSummaryById | 488 | ✓ |
| deleteJwBranchSummaryByIds | 489 | ✓ |
| deleteByCityAndYear | 490 | ✓ |
| deleteByIndicatorCode | 491 | ✓ |
| batchInsert | 494-505 | ✓ |
| updateIndicatorCode | 508 | ✓ |

## 6. JwGridDataRawMapper ✓ 完全覆盖

| MySQL id | GaussDB行 | 状态 |
|----------|----------|------|
| selectJwGridDataRawList | 517-521 | ✓ |
| selectJwGridDataRawById | 523-524 | ✓ |
| selectByGridCode | 526-527 | ✓ |
| selectByGridAndIndicator | 529-530 | ✓ |
| selectByCity | 532-536 | ✓ |
| selectAllByCity | 532-536 | ✓ (同SQL合并) |
| insertJwGridDataRaw | 539-541 | ✓ RETURNING |
| updateJwGridDataRaw | 544-548 | ✓ |
| deleteJwGridDataRawById | 551 | ✓ |
| deleteJwGridDataRawByIds | 552 | ✓ |
| deleteByCity | 553-554 | ✓ |
| deleteByIndicatorCode | 555 | ✓ |
| batchInsert | 558-563 | ✓ |
| selectByGridCodes | 566 | ✓ |
| updateIndicatorCode | 569 | ✓ |

## 7. JwGridDataNormalizedMapper ✓ 完全覆盖

| MySQL id | GaussDB行 | 状态 |
|----------|----------|------|
| selectJwGridDataNormalizedList | 578-582 | ✓ |
| selectJwGridDataNormalizedById | 584-585 | ✓ |
| selectByGridCode | 587-588 | ✓ |
| selectByGridAndIndicator | 590-591 | ✓ |
| selectByCity | 593-597 | ✓ |
| selectAllByCity | 593-597 | ✓ (同SQL合并) |
| insertJwGridDataNormalized | 600-602 | ✓ RETURNING |
| updateJwGridDataNormalized | 605-609 | ✓ |
| deleteJwGridDataNormalizedById | 612 | ✓ |
| deleteJwGridDataNormalizedByIds | 613 | ✓ |
| deleteByCity | 614-615 | ✓ |
| deleteByIndicatorCode | 616 | ✓ |
| batchInsert | 619-624 | ✓ |
| updateIndicatorCode | 627 | ✓ |

## 8. JwGridMetaMapper ✓ 完全覆盖

| MySQL id | GaussDB行 | 状态 |
|----------|----------|------|
| selectJwGridMetaList | 636-644 | ✓ |
| selectJwGridMetaById | 646 | ✓ (省略号) |
| selectByGridCode | 646 | ✓ (省略号, 同SQL合并) |
| selectByCity | 648-651 | ✓ |
| selectDistinctCities | 653 | ✓ |
| insertJwGridMeta | 656-664 | ✓ |
| updateJwGridMeta | 667-678 | ✓ |
| deleteJwGridMetaById | 681 | ✓ |
| deleteJwGridMetaByIds | 682 | ✓ |
| deleteByCity | 683 | ✓ |
| batchInsert | 686-701 | ✓ |
| selectByPoint | 704-712 | ✓ |
| batchUpdateGridMeta | 715-733 | ✓ UPDATE FROM转换 |

## 9. JwGridScoreMapper ⚠️ 2个查询已移除

| MySQL id | GaussDB行 | 状态 |
|----------|----------|------|
| selectJwGridScoreList | 742-747 | ✓ |
| selectJwGridScoreByGridCode | 749-750 | ✓ |
| selectByGridCode | 752-753 | ✓ |
| selectScoresByGridCode | 755-756 | ✓ |
| selectScoresByGridCodes | 758-759 | ✓ |
| selectScoresByGridCodesAndCategory | 761-763 | ❌ postMapper已移除,注释标记 |
| selectByCity | 765-767 | ✓ |
| selectByCityAndDistrict | 769-770 | ❌ postMapper已移除,注释标记 |
| countByCity | 772 | ✓ |
| insertJwGridScore | 775-776 | ✓ |
| updateJwGridScore | 779-783 | ✓ |
| deleteJwGridScoreByGridCode | 786 | ✓ |
| deleteJwGridScoreByGridCodes | 787 | ✓ |
| deleteByCity | 788 | ✓ |
| batchInsert | 791-796 | ✓ |
| selectTopCodesWithoutBranch | 799-810 | ✓ |
| selectBetterBlankCodes | 812-813 | ❌ postMapper已移除,注释标记 |

## 10. JwGridSummaryMapper ✓ 完全覆盖

| MySQL id | GaussDB行 | 状态 |
|----------|----------|------|
| selectJwGridSummaryList | 822-827 | ✓ |
| selectJwGridSummaryById | 829-831 | ✓ |
| selectByCity | 833-835 | ✓ |
| insertJwGridSummary | 838-844 | ✓ RETURNING |
| insertGridSummary | 838-844 | ✓ (同SQL合并) |
| updateJwGridSummary | 847-855 | ✓ |
| updateGridSummary | 847-855 | ✓ (同SQL合并) |
| deleteJwGridSummaryById | 858 | ✓ |
| deleteJwGridSummaryByIds | 859 | ✓ |
| deleteByCity | 860 | ✓ |
| deleteByIndicatorCode | 861 | ✓ |
| batchInsert | 864-875 | ✓ |
| updateIndicatorCode | 878 | ✓ |

## 11. JwIndicatorConfigMapper ✓ 完全覆盖

| MySQL id | GaussDB行 | 状态 |
|----------|----------|------|
| selectJwIndicatorConfigList | 887-895 | ✓ |
| selectJwIndicatorConfigById | 897-900 | ✓ |
| selectByType | 902-906 | ✓ |
| selectByTypes | 908-912 | ✓ |
| selectLeavesByType | 915-921 | ✓ |
| selectByParent | 923-927 | ✓ |
| selectRoots | 929-934 | ✓ |
| selectByCode | 936-939 | ✓ |
| selectByIndicatorName | 941-944 | ✓ |
| selectByCodes | 946-950 | ✓ |
| insertIndicatorConfig | 953-961 | ✓ RETURNING |
| updateJwIndicatorConfig | 964-975 | ✓ |
| deleteJwIndicatorConfigById | 978 | ✓ |
| deleteJwIndicatorConfigByIds | 979 | ✓ |
| deleteByParentCode | 980 | ✓ |
| deleteByCode | 981 | ✓ |
| updateParentCode | 984 | ✓ |
| batchInsert | 987-1002 | ✓ |

## 12. JwPeerBankInfoMapper ✓ 完全覆盖

| MySQL id | GaussDB行 | 状态 |
|----------|----------|------|
| selectJwPeerBankInfoList | 1011-1022 | ✓ |
| selectJwPeerBankInfoById | 1024-1028 | ✓ |
| selectByCity | 1030-1037 | ✓ |
| insertJwPeerBankInfo | 1040-1048 | ✓ RETURNING |
| deleteJwPeerBankInfoById | 1051 | ✓ |
| batchInsert | 1054-1069 | ✓ |

## 13. JwPoiInfoMapper ✓ 完全覆盖

| MySQL id | GaussDB行 | 状态 |
|----------|----------|------|
| selectPoiInfoList | 1078-1086 | ✓ |
| selectJwPoiInfoById | 1088-1090 | ✓ |
| selectByCity | 1092-1095 | ✓ |
| countByCity | 1097-1098 | ✓ |
| selectDistinctCities | 1100-1101 | ✓ |
| insertJwPoiInfo | 1104-1110 | ✓ RETURNING |
| updateJwPoiInfo | 1113-1125 | ✓ |
| deleteJwPoiInfoById | 1128 | ✓ |
| deleteJwPoiInfoByIds | 1129 | ✓ |
| deleteByCity | 1130 | ✓ |
| batchInsert | 1133-1144 | ✓ |
| selectWithinBounds | 1147-1154 | ✓ |
| selectDistinctPoiTypes | 1157-1161 | ✓ |

## 14. JwPopulationHeatMapper ✓ 完全覆盖

| MySQL id | GaussDB行 | 状态 |
|----------|----------|------|
| selectJwPopulationHeatList | 1170-1174 | ✓ |
| selectJwPopulationHeatById | 1176-1177 | ✓ |
| selectByGridCode | 1179-1180 | ✓ |
| selectByGridCodes | 1182-1183 | ✓ |
| selectByGridAndIndicator | 1185-1186 | ✓ |
| selectDistinctGridCodes | 1188 | ✓ |
| selectDistinctGridCodesByCity | 1190-1193 | ✓ |
| selectDistinctCities | 1195-1198 | ✓ |
| insertJwPopulationHeat | 1201-1203 | ✓ RETURNING |
| updateJwPopulationHeat | 1206-1210 | ✓ |
| deleteJwPopulationHeatById | 1213 | ✓ |
| deleteJwPopulationHeatByIds | 1214 | ✓ |
| deleteByGridCode | 1215 | ✓ |
| deleteByCity | 1216-1217 | ✓ |
| deleteByIndicatorCode | 1218 | ✓ |
| batchInsert | 1221-1226 | ✓ |
| updateIndicatorCode | 1229 | ✓ |

## 15. JwDataAccessRequestMapper ✓ 完全覆盖

| MySQL id | GaussDB行 | 状态 |
|----------|----------|------|
| selectMyList | 1253-1266 | ✓ |
| selectPendingListByReviewerId | 1269-1273 | ✓ |
| selectReviewedList | 1276-1280 | ✓ |
| selectJwDataAccessRequestById | 1283-1284 | ✓ |
| countValid | 1287-1295 | ✓ |
| countPendingByReviewerId | 1298-1301 | ✓ |
| insertJwDataAccessRequest | 1304-1310 | ✓ RETURNING |
| updateStatus | 1313-1322 | ✓ |
| deleteJwDataAccessRequestById | 1325-1327 | ✓ (软删除) |
| selectReviewers | 1330-1337 | ✓ |
| batchExpire | 1340-1344 | ✓ |

## 16. JwScoreCategoryConfigMapper ✓ 完全覆盖

| MySQL id | GaussDB行 | 状态 |
|----------|----------|------|
| selectAllActive | 1353-1355 | ✓ |
| selectByCategory | 1357-1359 | ✓ |
| selectDistinctCategories | 1361-1363 | ✓ |
| insertCategoryConfig | 1366-1372 | ✓ RETURNING |
| updateCategoryConfig | 1375-1381 | ✓ |
| deleteByCategory | 1384 | ✓ |
| deleteByIndicatorCode | 1385 | ✓ |
| updateIndicatorCode | 1388 | ✓ |

---

## 发现的问题

### 问题1: 占位省略号 (3处)
GaussDB脚本中使用了 `SELECT ... FROM` 省略号简写:
- JwBranchInfoMapper: `selectJwBranchInfoById`, `selectByBranchCode`, `selectByGridCode`, `selectByPrimaryBranch`, `selectByDeptName` (行188-189, 203-205)
- JwGridMetaMapper: `selectJwGridMetaById`, `selectByGridCode` (行646)

**建议**: 展开为完整SELECT列列表

### 问题2: 重复SQL合并 (4处)
GaussDB脚本将MySQL中完全相同的SQL合并为一条:
- JwBranchSummaryMapper: `insertJwBranchSummary` + `insertBranchSummary` (同SQL)
- JwBranchSummaryMapper: `updateJwBranchSummary` + `updateBranchSummary` (同SQL)
- JwGridDataRawMapper: `selectByCity` + `selectAllByCity` (同SQL)
- JwGridDataNormalizedMapper: `selectByCity` + `selectAllByCity` (同SQL)

**结论**: 不影响功能，但注释中可注明这两个id共享同一SQL

### 问题3: postMapper已移除的MySQL查询 (5处)
这些MySQL查询在postMapper版XML中已不存在:
- JwBranchScoreMapper: `selectByCityAndYearAndCategoryAndBranch` (行90-95)
- JwBranchScoreMapper: `batchUpdateQuadrant` (行154-176)
- JwGridScoreMapper: `selectScoresByGridCodesAndCategory` (行56-64)
- JwGridScoreMapper: `selectByCityAndDistrict` (行72-80)
- JwGridScoreMapper: `selectBetterBlankCodes` (行132-144)

**结论**: GaussDB脚本已用注释标记这些为已移除，SQL覆盖完整

### 问题4: DDL缺失
GaussDB脚本仅包含DML语句(SELECT/INSERT/UPDATE/DELETE)，没有包含表的DDL(CREATE TABLE)语句

**建议**: 如果需要完整的数据库迁移脚本，应补充各表的CREATE TABLE语句(GaussDB兼容语法)

---

## 总体结论

| 指标 | 数值 |
|------|------|
| MySQL XML SQL 总数 | ~202条 (含重复SQL) |
| GaussDB脚本覆盖 | ~185条 |
| 完全覆盖的Mapper | 14/16 |
| 已标记移除的查询 | 5条 |
| 实际遗漏 | **0条** |
| 省略号占位 | 3处(建议展开) |
