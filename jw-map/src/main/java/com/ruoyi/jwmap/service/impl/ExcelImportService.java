package com.ruoyi.jwmap.service.impl;

import com.ruoyi.jwmap.domain.*;
import com.ruoyi.jwmap.mapper.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Excel数据导入服务
 */
@Service
public class ExcelImportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelImportService.class);

    private static final Map<String, String> BRANCH_INDICATOR_MAP = new LinkedHashMap<>();
    static {
        BRANCH_INDICATOR_MAP.put("利息净收入(万元)", "interest_income");
        BRANCH_INDICATOR_MAP.put("手续费净收入(万元)", "fee_income");
        BRANCH_INDICATOR_MAP.put("全量个人金融资产 日均余额(万元)", "total_asset_balance");
        BRANCH_INDICATOR_MAP.put("全量个人金融资产 日均增量(万元)", "total_asset_growth");
        BRANCH_INDICATOR_MAP.put("储蓄存款 日均余额(万元)", "saving_balance");
        BRANCH_INDICATOR_MAP.put("储蓄存款 日均增量(万元)", "saving_growth");
        BRANCH_INDICATOR_MAP.put("公司客户存款 日均余额(万元)", "corp_dep_balance");
        BRANCH_INDICATOR_MAP.put("公司客户存款 日均增量(万元)", "corp_dep_growth");
        BRANCH_INDICATOR_MAP.put("机构客户存款 日均余额(万元)", "inst_dep_balance");
        BRANCH_INDICATOR_MAP.put("机构客户存款 日均增量(万元)", "inst_dep_growth");
        BRANCH_INDICATOR_MAP.put("普惠贷款 营销额(万元)", "inclusive_loan_amount");
        BRANCH_INDICATOR_MAP.put("个人贷款 发放额(万元)", "personal_loan_amount");
        BRANCH_INDICATOR_MAP.put("日均0元（不含）-20万元（不含客户数）(单位：户)", "pcust_t1");
        BRANCH_INDICATOR_MAP.put("日均20万元（含）-600万元（不含客户数）(单位：户)", "pcust_t2");
        BRANCH_INDICATOR_MAP.put("日均大于等于600万（含）客户数(单位：户)", "pcust_t3");
        BRANCH_INDICATOR_MAP.put("头部、中部对公客户数 日均资产50万元（含）以上（单位：户）", "ccust_h");
        BRANCH_INDICATOR_MAP.put("底尾部部对公客户数 日均资产50万元（不含）以下（单位：户）", "ccust_l");
        BRANCH_INDICATOR_MAP.put("日均资产1万元（不含）以上机构客户数（单位：户）", "icust_h");
        BRANCH_INDICATOR_MAP.put("日均资产1万元（含）以下机构客户数（单位：户）", "icust_l");
        BRANCH_INDICATOR_MAP.put("总量（单位：户）", "inclusive_cust_total");
        BRANCH_INDICATOR_MAP.put("柜台日均交易笔数", "counter_txn");
        BRANCH_INDICATOR_MAP.put("自助终端日均交易笔数", "terminal_txn");
        BRANCH_INDICATOR_MAP.put("附行式、网点自助ATM日均交易笔试", "atm_txn");
    }

    @Autowired
    private JwPoiInfoMapper poiInfoMapper;

    @Autowired
    private JwPopulationHeatMapper populationHeatMapper;

    @Autowired
    private JwGridMetaMapper gridMetaMapper;

    @Autowired
    private JwIndicatorConfigMapper indicatorConfigMapper;

    @Autowired
    private JwExternalResWeightMapper externalResWeightMapper;

    @Autowired
    private JwBranchEffWeightMapper branchEffWeightMapper;

    @Autowired
    private JwBranchInfoMapper branchInfoMapper;

    @Autowired
    private JwBranchIndicatorMapper branchIndicatorMapper;

    /**
     * 导入POI信息
     */
    @Transactional
    public int importPoiInfo(InputStream inputStream, String city, String username) throws IOException {
        int count = 0;
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                JwPoiInfo poi = new JwPoiInfo();
                poi.setCity(city);
                poi.setCreateBy(username);

                // 读取各列（根据实际Excel列顺序调整）
                poi.setOrgCode(getCellStringValue(row.getCell(0), formatter));
                poi.setPoiName(getCellStringValue(row.getCell(1), formatter));
                poi.setLongitude(getCellDoubleValue(row.getCell(2)));
                poi.setLatitude(getCellDoubleValue(row.getCell(3)));
                poi.setProvince(getCellStringValue(row.getCell(4), formatter));
                poi.setDistrict(getCellStringValue(row.getCell(5), formatter));
                poi.setAddress(getCellStringValue(row.getCell(6), formatter));
                poi.setPoiType(getCellStringValue(row.getCell(7), formatter));

                poiInfoMapper.upsertPoiInfo(poi);
                count++;
            }
        }
        log.info("导入POI数据完成，共{}条", count);
        return count;
    }

    /**
     * 导入人口热力数据
     * Excel格式：第1行为中文指标名，第2行为空或单位行，第3行起为数据
     * 列: grid_code, 经度, 纬度, 指标1, 指标2, ...
     */
    @Transactional
    public int importPopulationHeat(InputStream inputStream, String city) throws IOException {
        int count = 0;
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            // 第1行（Row 0）：中文指标名称行，从第4列开始
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new RuntimeException("Excel文件为空");
            }

            // 构建指标映射：列索引 -> indicator_code
            Map<Integer, String> colIndicatorMap = new LinkedHashMap<>();
            for (int c = 3; c < headerRow.getLastCellNum(); c++) {
                Cell cell = headerRow.getCell(c);
                if (cell == null) continue;
                String chineseName = formatter.formatCellValue(cell).trim();
                if (chineseName.isEmpty()) continue;

                // 查找是否已注册
                JwIndicatorConfig config = indicatorConfigMapper.selectByIndicatorName(chineseName);
                if (config == null) {
                    // 自动注册新指标
                    String code = "pop_" + chineseName.replaceAll("[\\s()（）、]", "_");
                    config = new JwIndicatorConfig();
                    config.setIndicatorCode(code);
                    config.setIndicatorName(chineseName);
                    config.setSourceTables("人口热力");
                    config.setIsWeighted("1");
                    config.setIsActive("1");
                    config.setDataType("decimal");
                    indicatorConfigMapper.insertIndicatorConfig(config);
                    log.info("自动注册新指标: {} -> {}", chineseName, code);
                }
                colIndicatorMap.put(c, config.getIndicatorCode());
            }

            // 读取数据行（从第3行开始，跳过第2行空行/单位行）
            for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String gridCode = getCellStringValue(row.getCell(0), formatter);
                Double lng = getCellDoubleValue(row.getCell(1));
                Double lat = getCellDoubleValue(row.getCell(2));

                if (gridCode == null || gridCode.isEmpty()) continue;

                // 确保jw_grid_meta中有对应的元信息
                JwGridMeta existingMeta = gridMetaMapper.selectByGridCode(gridCode);
                if (existingMeta == null) {
                    JwGridMeta meta = new JwGridMeta();
                    meta.setGridCode(gridCode);
                    meta.setLongitude(lng);
                    meta.setLatitude(lat);
                    meta.setCity(city);
                    gridMetaMapper.upsertGridMeta(meta);
                }

                // 写入每个指标的值
                for (Map.Entry<Integer, String> entry : colIndicatorMap.entrySet()) {
                    int colIdx = entry.getKey();
                    String indicatorCode = entry.getValue();
                    Double value = getCellDoubleValue(row.getCell(colIdx));
                    if (value == null) continue;

                    JwPopulationHeat heat = new JwPopulationHeat();
                    heat.setGridCode(gridCode);
                    heat.setIndicatorCode(indicatorCode);
                    heat.setIndicatorValue(value);
                    populationHeatMapper.upsertPopulationHeat(heat);
                    count++;
                }
            }
        }
        log.info("导入人口热力数据完成，共{}条指标数据", count);
        return count;
    }

    /**
     * 导入外部资源权重表（替换策略：先清空再全量插入）
     */
    @Transactional
    public int importExternalWeight(InputStream inputStream) throws IOException {
        externalResWeightMapper.deleteAll();
        int count = 0;
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            List<JwWeightConfig> list = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                JwWeightConfig config = new JwWeightConfig();
                config.setLevel1Name(getCellStringValue(row.getCell(0), formatter));
                config.setLevel1Ratio(getCellDoubleValue(row.getCell(1)));
                config.setLevel2Name(getCellStringValue(row.getCell(2), formatter));
                config.setLevel2Ratio(getCellDoubleValue(row.getCell(3)));
                config.setLevel3Name(getCellStringValue(row.getCell(4), formatter));
                config.setLevel3Ratio(getCellDoubleValue(row.getCell(5)));
                config.setTotalWeight(getCellDoubleValue(row.getCell(6)));
                config.setIndicatorCode(getCellStringValue(row.getCell(7), formatter));
                list.add(config);
            }

            if (!list.isEmpty()) {
                externalResWeightMapper.batchInsert(list);
                count = list.size();
            }
        }
        log.info("导入外部资源权重完成，共{}条", count);
        return count;
    }

    /**
     * 导入网点效能权重表（替换策略：先清空再全量插入）
     */
    @Transactional
    public int importBranchEfficiencyWeight(InputStream inputStream) throws IOException {
        branchEffWeightMapper.deleteAll();
        int count = 0;
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            List<JwWeightConfig> list = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                JwWeightConfig config = new JwWeightConfig();
                config.setLevel1Name(getCellStringValue(row.getCell(0), formatter));
                config.setLevel1Ratio(getCellDoubleValue(row.getCell(1)));
                config.setLevel2Name(getCellStringValue(row.getCell(2), formatter));
                config.setLevel2Ratio(getCellDoubleValue(row.getCell(3)));
                config.setLevel3Name(getCellStringValue(row.getCell(4), formatter));
                config.setLevel3Ratio(getCellDoubleValue(row.getCell(5)));
                config.setTotalWeight(getCellDoubleValue(row.getCell(6)));
                config.setIndicatorCode(getCellStringValue(row.getCell(7), formatter));
                list.add(config);
            }

            if (!list.isEmpty()) {
                branchEffWeightMapper.batchInsert(list);
                count = list.size();
            }
        }
        log.info("导入网点效能权重完成，共{}条", count);
        return count;
    }

    /**
     * 导入网点信息表
     * Excel格式复杂，包含基础信息列和多年份的年度指标列
     * Row 3（第4行）为列名行，包含网点基础信息列名和年度指标列名
     * 数据行从Row 4（第5行）开始
     * <p>
     * 列结构：
     * - A(0): 序号
     * - B(1): 所属分行
     * - C(2): 所属支行
     * - D(3): 网点名称/机构号
     * - ... 基础信息列
     * - 然后是2024年、2023年、2022年各年份的指标列组
     */
    @Transactional
    public int importBranchInfo(InputStream inputStream, String city, String dataSource) throws IOException {
        int count = 0;
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            // 读取列名行（第4行，index=3）
            Row headerRow = sheet.getRow(3);
            if (headerRow == null) {
                throw new RuntimeException("Excel列名行（第4行）为空");
            }

            // 解析列头，识别基础信息列和年度指标列
            List<ColumnDef> columnDefs = new ArrayList<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                Cell cell = headerRow.getCell(c);
                if (cell == null) continue;
                String colName = formatter.formatCellValue(cell).trim();
                if (colName.isEmpty()) continue;

                ColumnDef def = new ColumnDef();
                def.colIndex = c;
                def.colName = colName;

                // 判断列类型：看colName是否包含年份（如"2024年"）
                // 或通过固定列索引判断
                if (c <= 3) {
                    def.colType = "skip"; // 序号、分行、支行、网点名称
                } else if (isYearColumn(colName)) {
                    def.colType = "yearly_indicator";
                    def.year = parseYear(colName);
                    def.indicatorCode = extractIndicatorCode(colName);
                } else {
                    def.colType = "branch_field";
                    def.fieldName = mapColumnToField(colName);
                }
                columnDefs.add(def);
            }

            // 读取数据行（从第5行开始，index=4）
            for (int i = 4; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String branchCode = getCellStringValue(row.getCell(3), formatter);
                if (branchCode == null || branchCode.isEmpty()) continue;

                // 构建JwBranchInfo基础信息
                JwBranchInfo branch = new JwBranchInfo();
                branch.setCity(city);
                branch.setDataSource(dataSource);
                branch.setBranchCode(branchCode);

                // 逐列填充
                Map<Integer, Map<String, Double>> yearlyData = new LinkedHashMap<>(); // year -> (indicatorCode -> value)

                for (ColumnDef def : columnDefs) {
                    String cellVal = getCellStringValue(row.getCell(def.colIndex), formatter);
                    if (cellVal == null || cellVal.isEmpty()) continue;

                    switch (def.colType) {
                        case "branch_field":
                            setBranchField(branch, def.fieldName, cellVal);
                            break;
                        case "yearly_indicator":
                            Double numericVal = getCellDoubleValue(row.getCell(def.colIndex));
                            if (numericVal != null) {
                                yearlyData.computeIfAbsent(def.year, k -> new LinkedHashMap<>())
                                        .put(def.indicatorCode, numericVal);
                            }
                            break;
                        default:
                            break;
                    }
                }

                // 插入或更新网点基础信息
                JwBranchInfo existing = branchInfoMapper.selectByBranchCode(branchCode);
                if (existing != null) {
                    branch.setBranchId(existing.getBranchId());
                    branchInfoMapper.updateBranchInfo(branch);
                } else {
                    branchInfoMapper.insertBranchInfo(branch);
                }

                // 插入年度指标数据
                Long branchId = branch.getBranchId();
                if (branchId != null && !yearlyData.isEmpty()) {
                    for (Map.Entry<Integer, Map<String, Double>> yearEntry : yearlyData.entrySet()) {
                        Integer year = yearEntry.getKey();
                        Map<String, Double> indicators = yearEntry.getValue();

                        // 先清理该网点该年基础数据
                        branchIndicatorMapper.deleteByBranchAndYear(branchId, year, "基础数据");

                        for (Map.Entry<String, Double> indEntry : indicators.entrySet()) {
                            JwBranchIndicator ind = new JwBranchIndicator();
                            ind.setBranchId(branchId);
                            ind.setDataYear(year);
                            ind.setSheetType("基础数据");
                            ind.setIndicatorCode(indEntry.getKey());
                            ind.setIndicatorValue(indEntry.getValue());
                            branchIndicatorMapper.insertJwBranchIndicator(ind);
                        }
                    }
                }

                count++;
            }
        }
        log.info("导入网点信息完成，共{}条网点", count);
        return count;
    }

    /**
     * 导入存量网点基本信息
     * 针对较简单的存量网点Excel导入
     */
    @Transactional
    public int importExistingBranch(InputStream inputStream, String city) throws IOException {
        int count = 0;
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            // 第一行为表头
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                JwBranchInfo branch = new JwBranchInfo();
                branch.setCity(city);
                branch.setDataSource("存量网点");

                branch.setPrimaryBranch(getCellStringValue(row.getCell(0), formatter));
                branch.setSecondaryBranch(getCellStringValue(row.getCell(1), formatter));
                branch.setBranchCode(getCellStringValue(row.getCell(2), formatter));
                branch.setDistrictName(getCellStringValue(row.getCell(3), formatter));
                branch.setStreet(getCellStringValue(row.getCell(4), formatter));
                branch.setAddress(getCellStringValue(row.getCell(5), formatter));
                branch.setLongitude(getCellDoubleValue(row.getCell(6)));
                branch.setLatitude(getCellDoubleValue(row.getCell(7)));
                branch.setTotalStaff(getCellIntValue(row.getCell(8)));
                branch.setTotalArea(getCellDoubleValue(row.getCell(9)));
                branch.setBranchType(getCellStringValue(row.getCell(10), formatter));
                branch.setPropertyRight(getCellStringValue(row.getCell(11), formatter));
                branch.setLeaseExpire(getCellStringValue(row.getCell(12), formatter));
                branch.setLastRenovation(getCellStringValue(row.getCell(13), formatter));

                branchInfoMapper.upsertJwBranchInfo(branch);
                count++;
            }
        }
        log.info("导入存量网点完成，共{}条", count);
        return count;
    }

    // ========== 辅助方法 ==========

    private String getCellStringValue(Cell cell, DataFormatter formatter) {
        if (cell == null) return null;
        String val = formatter.formatCellValue(cell).trim();
        return val.isEmpty() ? null : val;
    }

    private Double getCellDoubleValue(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return cell.getNumericCellValue();
            } else if (cell.getCellType() == CellType.STRING) {
                String val = cell.getStringCellValue().trim();
                if (val.isEmpty()) return null;
                return Double.parseDouble(val);
            }
        } catch (Exception e) {
            // ignore parse errors
        }
        return null;
    }

    private Integer getCellIntValue(Cell cell) {
        Double d = getCellDoubleValue(cell);
        return d != null ? d.intValue() : null;
    }

    /**
     * 判断列名是否为年份列
     */
    private boolean isYearColumn(String colName) {
        return colName.contains("2024") || colName.contains("2023") || colName.contains("2022");
    }

    /**
     * 从列名中提取年份
     */
    private int parseYear(String colName) {
        if (colName.contains("2024")) return 2024;
        if (colName.contains("2023")) return 2023;
        if (colName.contains("2022")) return 2022;
        return 2024;
    }

    /**
     * 从列名中提取指标代码
     * 列名格式如"2024年利息收入" -> indicatorCode = colName去掉年份部分
     */
    private String extractIndicatorCode(String colName) {
        // Strip year prefix: "2024年利息净收入(万元)" -> "利息净收入(万元)"
        String nameWithoutYear = colName.replaceAll("^[0-9]{4}年?", "").trim();
        // Look up in hardcoded map
        String code = BRANCH_INDICATOR_MAP.get(nameWithoutYear);
        if (code != null) return code;
        // Try fuzzy match (remove spaces and brackets)
        for (Map.Entry<String, String> entry : BRANCH_INDICATOR_MAP.entrySet()) {
            String keyStripped = entry.getKey().replaceAll("[\\s()（）、]", "");
            String compareKey = keyStripped.substring(0, Math.min(6, keyStripped.length()));
            if (nameWithoutYear.contains(compareKey)) {
                return entry.getValue();
            }
        }
        // Fallback: generate code from name
        return nameWithoutYear.replaceAll("[\\s()（）、]", "_");
    }

    /**
     * 将Excel列名映射到JwBranchInfo字段名
     */
    private String mapColumnToField(String colName) {
        // Check if this is an indicator column (with or without year prefix)
        String nameWithoutYear = colName.replaceAll("^[0-9]{4}年?", "").trim();
        if (BRANCH_INDICATOR_MAP.containsKey(nameWithoutYear)) {
            return null; // This is an indicator, not a field
        }

        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put("所属分行", "primaryBranch");
        mapping.put("所属支行", "secondaryBranch");
        mapping.put("网点名称", "branchCode");
        mapping.put("机构号", "branchCode");
        mapping.put("所属行政区", "districtName");
        mapping.put("所属街道", "street");
        mapping.put("地址", "address");
        mapping.put("经度", "longitude");
        mapping.put("纬度", "latitude");
        mapping.put("总人数", "totalStaff");
        mapping.put("个人客户经理", "personalManager");
        mapping.put("公司客户经理", "corporateManager");
        mapping.put("柜员", "counterStaff");
        mapping.put("大堂经理", "lobbyStaff");
        mapping.put("网点负责人", "branchManager");
        mapping.put("负责人年限", "managerTenure");
        mapping.put("负责人简历", "managerResume");
        mapping.put("负责人历史", "managerHistory");
        mapping.put("总面积", "totalArea");
        mapping.put("其他楼层面积", "otherFloorArea");
        mapping.put("现金柜台", "cashCounter");
        mapping.put("非现金柜台", "nonCashCounter");
        mapping.put("管户席位", "managerSeat");
        mapping.put("产权性质", "propertyRight");
        mapping.put("租赁到期日", "leaseExpire");
        mapping.put("最近装修", "lastRenovation");
        mapping.put("网点类型", "branchType");
        mapping.put("是否拟迁址", "relocation");

        return mapping.getOrDefault(colName, null);
    }

    /**
     * 根据字段名设置JwBranchInfo属性
     */
    private void setBranchField(JwBranchInfo branch, String fieldName, String value) {
        if (value == null) return;
        try {
            switch (fieldName) {
                case "primaryBranch": branch.setPrimaryBranch(value); break;
                case "secondaryBranch": branch.setSecondaryBranch(value); break;
                case "branchCode": branch.setBranchCode(value); break;
                case "districtName": branch.setDistrictName(value); break;
                case "street": branch.setStreet(value); break;
                case "address": branch.setAddress(value); break;
                case "branchManager": branch.setBranchManager(value); break;
                case "managerTenure": branch.setManagerTenure(value); break;
                case "managerResume": branch.setManagerResume(value); break;
                case "managerHistory": branch.setManagerHistory(value); break;
                case "propertyRight": branch.setPropertyRight(value); break;
                case "leaseExpire": branch.setLeaseExpire(value); break;
                case "lastRenovation": branch.setLastRenovation(value); break;
                case "branchType": branch.setBranchType(value); break;
                case "relocation": branch.setRelocation(value); break;
                case "longitude": branch.setLongitude(Double.parseDouble(value)); break;
                case "latitude": branch.setLatitude(Double.parseDouble(value)); break;
                case "totalStaff": branch.setTotalStaff(Integer.parseInt(value)); break;
                case "personalManager": branch.setPersonalManager(Integer.parseInt(value)); break;
                case "corporateManager": branch.setCorporateManager(Integer.parseInt(value)); break;
                case "counterStaff": branch.setCounterStaff(Integer.parseInt(value)); break;
                case "lobbyStaff": branch.setLobbyStaff(Integer.parseInt(value)); break;
                case "totalArea": branch.setTotalArea(Double.parseDouble(value)); break;
                case "otherFloorArea": branch.setOtherFloorArea(Double.parseDouble(value)); break;
                case "cashCounter": branch.setCashCounter(Integer.parseInt(value)); break;
                case "nonCashCounter": branch.setNonCashCounter(Integer.parseInt(value)); break;
                case "managerSeat": branch.setManagerSeat(Integer.parseInt(value)); break;
                default: break;
            }
        } catch (NumberFormatException e) {
            log.warn("字段{}的值{}转换失败: {}", fieldName, value, e.getMessage());
        }
    }

    /**
     * Excel列定义
     */
    private static class ColumnDef {
        int colIndex;
        String colName;
        String colType; // "skip", "branch_field", "yearly_indicator"
        String fieldName; // 对于branch_field类型
        Integer year; // 对于yearly_indicator类型
        String indicatorCode; // 对于yearly_indicator类型
    }
}
