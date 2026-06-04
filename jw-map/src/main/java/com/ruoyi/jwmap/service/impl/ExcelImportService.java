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

    // BRANCH_INDICATOR_MAP 已移除，所有指标通过 indicator_name 匹配 jw_indicator_config 表

    @Autowired
    private JwPoiInfoMapper poiInfoMapper;

    @Autowired
    private JwPopulationHeatMapper populationHeatMapper;

    @Autowired
    private JwGridMetaMapper gridMetaMapper;

    @Autowired
    private JwIndicatorConfigMapper indicatorConfigMapper;

    @Autowired
    private JwBranchInfoMapper branchInfoMapper;

    @Autowired
    private JwBranchIndicatorMapper branchIndicatorMapper;

    @Autowired
    private JwPeerBankInfoMapper peerBankInfoMapper;

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

                // 读取各列: A=机构编码 B=POI名称 C=经度 D=纬度 E=省 F=市 G=区县 H=地址 I=POI类型
                poi.setOrgCode(getCellStringValue(row.getCell(0), formatter));
                poi.setPoiName(getCellStringValue(row.getCell(1), formatter));
                poi.setLongitude(getCellDoubleValue(row.getCell(2)));
                poi.setLatitude(getCellDoubleValue(row.getCell(3)));
                poi.setProvince(getCellStringValue(row.getCell(4), formatter));
                poi.setCity(getCellStringValue(row.getCell(5), formatter));
                poi.setDistrict(getCellStringValue(row.getCell(6), formatter));
                poi.setAddress(getCellStringValue(row.getCell(7), formatter));
                poi.setPoiType(getCellStringValue(row.getCell(8), formatter));

                poiInfoMapper.upsertPoiInfo(poi);
                count++;
            }
        }
        log.info("导入POI数据完成，共{}条", count);
        return count;
    }

    /**
     * 导入人口热力数据
     * Excel格式：第1行为一级分类名(h1,含合并单元格)，第2行为二级指标名(h2)，第3行起为数据
     * 列: 网格编号, 经度, 纬度, 省, 市, 区县, 指标列...
     * <p>
     * 处理分步：
     * 1. Row 0 读取 h1（大类名），对合并单元格做填充处理
     * 2. Row 1 读取 h2（子指标名）
     * 3. 组合 h1_h2 作为指标全名，自动注册到 indicator_config
     * 4. Row 2 起为数据行
     */
    @Transactional
    public int importPopulationHeat(InputStream inputStream, String city) throws IOException {
        int count = 0;
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            // 先清理该市旧人口热力数据（因指标编码会随表头解析改变）
            populationHeatMapper.deleteByCity(city);

            // Row 0: h1 一级分类行（含合并单元格）
            Row h1Row = sheet.getRow(0);
            if (h1Row == null) {
                throw new RuntimeException("Excel文件为空");
            }
            // Row 1: h2 二级指标行
            Row h2Row = sheet.getRow(1);

            // 逐列构建指标映射：h1=父分类, h2=子指标，分层匹配 indicator_config 树
            Map<Integer, String> colIndicatorMap = new LinkedHashMap<>();
            String currentH1 = null;
            int maxCol = Math.max(
                h1Row.getLastCellNum(),
                h2Row != null ? h2Row.getLastCellNum() : 0
            );

            for (int c = 3; c < maxCol; c++) {
                // 读取 h1，合并单元格时只有首格有值，后续列 null → 沿用 currentH1
                String h1 = getCellStringValue(h1Row.getCell(c), formatter);
                if (h1 != null) {
                    currentH1 = h1;
                }

                // 读取 h2（不存在时表示该列为单层表头）
                String h2 = getCellStringValue(h2Row != null ? h2Row.getCell(c) : null, formatter);

                String indicatorCode;
                if (h2 != null) {
                    // 双层表头：h1=父分类, h2=子指标 → 先规范化匹配，失败则创建
                    String parentCode = ensureIndicator(currentH1, "grid_auto", null);
                    indicatorCode = matchIndicatorCodeFromNorm(h2, parentCode);
                } else if (h1 != null) {
                    // 单层表头：h1 直接作为指标
                    indicatorCode = ensureIndicator(h1, "grid_auto", null);
                } else {
                    // h1、h2 均为空 → 无意义的列，跳过
                    continue;
                }

                if (indicatorCode != null) {
                    colIndicatorMap.put(c, indicatorCode);
                }
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
     * 导入网点基础数据（Excel的"基础数据"Sheet）
     * <p>
     * 格式A（推荐）：Row0-1为分类标题, Row2为列名, Row3为年份(全"2024"),
     * Row4-6为非数据行(实际权值/MAX/MIN), Row7+为数据
     * 列: 一级支行, 二级支行, 机构号, 经度, 纬度, 指标列(通过Row1子分类+Row2名定位)
     * <p>
     * 格式B（兼容旧格式）：Row3为列名行, Row4+为数据
     */
    @Transactional
    public int importBranchInfo(InputStream inputStream, String city, String dataSource) throws IOException {
        int count = 0;
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            DataFormatter formatter = new DataFormatter();

            // — 自动检测应读取哪个 Sheet —
            // 优先找"基础数据"格式的 sheet（Row3 为年份行），否则取第一个 sheet
            Sheet sheet = null;
            for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
                Sheet s = workbook.getSheetAt(si);
                if (s.getRow(3) != null && isYearOnlyRow(s.getRow(3), formatter)) {
                    sheet = s;
                    log.info("检测到基础数据格式sheet[{}]: {}", si, workbook.getSheetName(si));
                    break;
                }
            }
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            Row row0 = sheet.getRow(0);
            Row row1 = sheet.getRow(1);
            Row row2 = sheet.getRow(2);
            Row row3 = sheet.getRow(3);

            // 判断是否为"基础数据"格式：Row3 大部分非空单元格为 "2024"
            int headerRowIndex;
            int dataStartIndex;
            boolean isCalcDataFormat = isYearOnlyRow(row3, formatter);

            if (isCalcDataFormat) {
                // 格式A：基础数据格式
                headerRowIndex = 2;  // Row2 = 列名行
                // 动态检测标记行：Row4 第一格为"实际权值/实际值"说明有标记行
                boolean hasMarkerRows = false;
                Row r4 = sheet.getRow(4);
                if (r4 != null) {
                    String marker = getCellStringValue(r4.getCell(0), formatter);
                    hasMarkerRows = "实际权值".equals(marker) || "实际值".equals(marker);
                }
                dataStartIndex = hasMarkerRows ? 7 : 4;
                log.info("检测到基础数据格式，Row2为列名行，{}标记行，数据从Row{}开始",
                    hasMarkerRows ? "有" : "无", dataStartIndex);
            } else {
                // 格式B：旧格式，兼容
                headerRowIndex = 3;
                dataStartIndex = 4;
            }

            Row headerRow = sheet.getRow(headerRowIndex);
            if (headerRow == null) {
                throw new RuntimeException("Excel列名行（第" + (headerRowIndex + 1) + "行）为空");
            }

            // — 解析列头 —
            List<ColumnDef> columnDefs = new ArrayList<>();
            int maxCol = 0;
            for (int r = headerRowIndex; r <= headerRowIndex; r++) {
                Row hr = sheet.getRow(r);
                if (hr != null && hr.getLastCellNum() > maxCol) maxCol = hr.getLastCellNum();
            }
            // 对于格式A，还要看 row3 的 cell num
            if (isCalcDataFormat && row3 != null) {
                maxCol = Math.max(maxCol, row3.getLastCellNum());
            }
            // 确保至少扫描到 27 列（基础数据完整列数）
            if (maxCol < 27) maxCol = 27;

            // 跟踪最近的指标列名称和子分类（用于继承给后续年份列）
            String pendingIndicatorName = null;
            String pendingSubCategory = null;

            for (int c = 0; c < maxCol; c++) {
                // 获取列名（格式A取Row2，格式B取Row3）
                String colName = getCellStringValue(headerRow.getCell(c), formatter);
                boolean fromFallback = false;

                // 格式A：当Row2为空时，尝试从Row1或继承前一个指标列名
                if (colName == null && isCalcDataFormat) {
                    Integer maybeYear = null;
                    if (row3 != null) {
                        String ys = getCellStringValue(row3.getCell(c), formatter);
                        if (ys != null && ys.matches("\\d{4}")) maybeYear = Integer.parseInt(ys);
                    }
                    if (maybeYear != null) {
                        // 先尝试 Row1 的值作为列名（处理业务运营等无Row2列名的分类）
                        if (row1 != null) {
                            String r1 = getCellStringValue(row1.getCell(c), formatter);
                            if (r1 != null) {
                                colName = r1;
                                fromFallback = true;
                            }
                        }
                        // Row1也是合并单元格非首格(null)时，尝试继承前一个指标列名
                        if (colName == null && pendingIndicatorName != null) {
                            colName = pendingIndicatorName;
                            fromFallback = true;
                        }
                    }
                    // ★ Row2为null且不是年份列时，尝试从Row4(细分行)取固定字段列名
                    if (colName == null && row3 != null) {
                        String row4Name = getCellStringValue(row3.getCell(c), formatter);
                        if (row4Name != null) {
                            colName = row4Name;
                        }
                    }
                }

                if (colName == null) continue;

                // 去除列名中的 \n（Excel单元格内换行）
                colName = colName.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
                if (colName.isEmpty()) continue;

                ColumnDef def = new ColumnDef();
                def.colIndex = c;
                def.colName = colName;

                // 基础信息列：根据列名映射字段
                if (isCalcDataFormat && c <= 4) {
                    String fieldName = mapColumnToField(colName);
                    if (fieldName != null) {
                        def.colType = "branch_field";
                        def.fieldName = fieldName;
                        columnDefs.add(def);
                    }
                    continue;
                } else if (!isCalcDataFormat && c <= 3) {
                    def.colType = "skip";
                    columnDefs.add(def);
                    continue;
                }

                // 读取年份（格式A从Row3取，格式B从colName解析）
                Integer year = null;
                if (isCalcDataFormat && row3 != null) {
                    String yearStr = getCellStringValue(row3.getCell(c), formatter);
                    if (yearStr != null && yearStr.matches("\\d{4}")) {
                        year = Integer.parseInt(yearStr);
                    }
                }

                // 尝试匹配年份指标
                if (isCalcDataFormat && year != null) {
                    // 格式A：从 Row1 (子分类) + Row2 (名称) 组合成完整指标名
                    String subCategory;
                    if (fromFallback) {
                        // 继承来的列名：复用 pendingSubCategory
                        subCategory = pendingSubCategory;
                        // ★ 同时更新 pendingIndicatorName（业务运营等第2/3年列通过继承获取colName时）
                        pendingIndicatorName = colName;
                    } else {
                        // 读取 Row1 子分类，并更新 pending 值
                        if (row1 != null) {
                            String sc = getCellStringValue(row1.getCell(c), formatter);
                            subCategory = (sc != null) ? sc.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim() : null;
                            // ★ Row1是合并单元格时，同组内后续列的getCell返回null → 继承前一个子分类
                            if (subCategory == null && pendingSubCategory != null) {
                                subCategory = pendingSubCategory;
                            }
                        } else {
                            subCategory = null;
                        }
                        pendingIndicatorName = colName;
                        pendingSubCategory = subCategory;
                    }

                    String fullIndicatorName = (subCategory != null && !subCategory.isEmpty())
                            ? subCategory + " " + colName
                            : colName;

                    def.colType = "yearly_indicator";
                    def.year = year;
                    def.indicatorCode = matchBranchIndicator(fullIndicatorName, colName);
                    columnDefs.add(def);
                } else if (isYearColumn(colName)) {
                    // 格式B：旧格式年份列
                    def.colType = "yearly_indicator";
                    def.year = parseYear(colName);
                    def.indicatorCode = extractIndicatorCode(colName);
                    columnDefs.add(def);
                } else {
                    // 基础信息字段
                    String fieldName = mapColumnToField(colName);
                    // ★ 对于isCalcDataFormat，若Row3列名是分组标签（如"整体情况"）无法映射，
                    //    则尝试Row4（细分列名行）的字段名
                    if (fieldName == null && isCalcDataFormat && row3 != null) {
                        String row4Name = getCellStringValue(row3.getCell(c), formatter);
                        if (row4Name != null) {
                            row4Name = row4Name.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
                            fieldName = mapColumnToField(row4Name);
                            if (fieldName != null) {
                                def.colName = row4Name; // 更新为实际字段名
                            }
                        }
                    }
                    if (fieldName != null) {
                        def.colType = "branch_field";
                        def.fieldName = fieldName;
                        columnDefs.add(def);
                    } else if (!isCalcDataFormat) {
                        // 格式B：未识别的列名跳过
                        log.debug("跳过未识别的列: {} (col={})", colName, c);
                    }
                }
            }

            // 如果是格式A但没有产生任何 yearly_indicator，降级为旧格式
            if (isCalcDataFormat && columnDefs.stream().noneMatch(d -> "yearly_indicator".equals(d.colType))) {
                log.warn("基础数据格式未识别到指标列，降级为旧格式解析");
                headerRowIndex = 3;
                dataStartIndex = 4;
                // 重新解析（简化：走旧逻辑但用新代码路径）
                headerRow = sheet.getRow(3);
                if (headerRow != null) {
                    columnDefs.clear();
                    for (int c = 0; c < maxCol; c++) {
                        String colName = getCellStringValue(headerRow.getCell(c), formatter);
                        if (colName == null) continue;
                        ColumnDef def = new ColumnDef();
                        def.colIndex = c; def.colName = colName;
                        if (c <= 3) {
                            def.colType = "skip";
                        } else if (isYearColumn(colName)) {
                            def.colType = "yearly_indicator";
                            def.year = parseYear(colName);
                            def.indicatorCode = extractIndicatorCode(colName);
                        } else {
                            String fieldName = mapColumnToField(colName);
                            if (fieldName != null) {
                                def.colType = "branch_field";
                                def.fieldName = fieldName;
                            } else continue;
                        }
                        columnDefs.add(def);
                    }
                }
            }

            // — 读取数据行 —
            for (int i = dataStartIndex; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // 跳过汇总/标记行（实际权值、MAX、MIN）
                String firstCellVal = getCellStringValue(row.getCell(0), formatter);
                if (firstCellVal != null && ("MAX".equalsIgnoreCase(firstCellVal)
                        || "MIN".equalsIgnoreCase(firstCellVal)
                        || "实际权值".equals(firstCellVal)
                        || "实际值".equals(firstCellVal))) {
                    continue;
                }

                // 读取机构号（格式A：col2，格式B：col3）
                int branchCodeCol = isCalcDataFormat ? 2 : 3;
                String branchCode = getCellStringValue(row.getCell(branchCodeCol), formatter);
                if (branchCode == null || branchCode.isEmpty()) continue;

                // 构建JwBranchInfo基础信息
                JwBranchInfo branch = new JwBranchInfo();
                branch.setCity(city);
                branch.setDataSource(dataSource);
                branch.setBranchCode(branchCode);

                Map<Integer, Map<String, Double>> yearlyData = new LinkedHashMap<>();

                // 逐列填充
                for (ColumnDef def : columnDefs) {
                    String cellVal = getCellStringValue(row.getCell(def.colIndex), formatter);
                    if (cellVal == null || cellVal.isEmpty()) continue;

                    switch (def.colType) {
                        case "branch_field":
                            setBranchField(branch, def.fieldName, cellVal);
                            break;
                        case "yearly_indicator":
                            Double numericVal = getCellDoubleValue(row.getCell(def.colIndex));
                            if (numericVal != null && def.indicatorCode != null) {
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

            log.info("导入网点信息完成，共{}条网点", count);
            return count;
        }
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

    /**
     * 导入同业银行信息
     * <p>
     * Excel列格式: A=机构编码 B=机构名称 C=机构地址 D=经度 E=纬度
     *              F=银行名称 G=省 H=市 I=区县 J=乡镇/街道
     * <p>
     * 导入时自动根据经纬度判断所属网格（空间关联 jw_grid_meta）
     */
    @Transactional
    public int importPeerBank(InputStream inputStream, String city) throws IOException {
        int count = 0;
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                JwPeerBankInfo peer = new JwPeerBankInfo();
                peer.setOrgCode(getCellStringValue(row.getCell(0), formatter));
                peer.setOrgName(getCellStringValue(row.getCell(1), formatter));
                peer.setOrgAddress(getCellStringValue(row.getCell(2), formatter));
                peer.setLongitude(getCellDoubleValue(row.getCell(3)));
                peer.setLatitude(getCellDoubleValue(row.getCell(4)));
                peer.setBankName(getCellStringValue(row.getCell(5), formatter));
                peer.setProvince(getCellStringValue(row.getCell(6), formatter));
                peer.setCity(city);
                peer.setDistrict(getCellStringValue(row.getCell(8), formatter));
                peer.setTown(getCellStringValue(row.getCell(9), formatter));

                if (peer.getOrgCode() == null || peer.getOrgCode().isEmpty()) continue;

                // 过滤不需要的同业银行
                String bankName = peer.getBankName();
                if (bankName != null && ("其他银行".equals(bankName.trim()) || "工商银行".equals(bankName.trim()))) {
                    continue;
                }

                // ★ 自动判断所属网格：根据经纬度查找包含该点的网格
                if (peer.getLongitude() != null && peer.getLatitude() != null) {
                    JwGridMeta grid = gridMetaMapper.selectByPoint(peer.getLongitude(), peer.getLatitude());
                    if (grid != null) {
                        peer.setGridCode(grid.getGridCode());
                    }
                }

                peerBankInfoMapper.upsertJwPeerBankInfo(peer);
                count++;
            }
        }
        log.info("导入同业银行数据完成，共{}条", count);
        return count;
    }

    /**
     * 根据三级指标名称模糊匹配 indicator_code
     * 匹配策略（按优先级）：
     * 1. 在 indicator_config 中精确匹配指标名
     * 2. 去除"人口"后缀后做后缀匹配（h1_h2 格式的指标名以 _h2 结尾）
     * 3. 归一化特殊字符后匹配
     * 4. 包含关系匹配
     * 5. 动态创建新指标（权重0）
     */
    private String matchIndicatorCode(String level3Name) {
        if (level3Name == null || level3Name.isEmpty()) return null;

        // 加载所有指标
        List<JwIndicatorConfig> allIndicators = indicatorConfigMapper.selectJwIndicatorConfigList(new JwIndicatorConfig());
        if (allIndicators == null || allIndicators.isEmpty()) return null;

        // 构建 name→code 快速查找
        Map<String, String> nameToCode = new LinkedHashMap<>();
        List<String> allNames = new ArrayList<>();
        for (JwIndicatorConfig ind : allIndicators) {
            if (ind.getIndicatorName() != null) {
                nameToCode.put(ind.getIndicatorName(), ind.getIndicatorCode());
                allNames.add(ind.getIndicatorName());
            }
        }

        // 1. 精确匹配
        String direct = nameToCode.get(level3Name);
        if (direct != null) return direct;

        // 2. 处理"人口"后缀：工作人口 → 工作，居住人口 → 居住
        String trimmedName = level3Name;
        if (level3Name.endsWith("人口") && level3Name.length() > 2) {
            trimmedName = level3Name.substring(0, level3Name.length() - 2);
            String trimmedMatch = nameToCode.get(trimmedName);
            if (trimmedMatch != null) return trimmedMatch;
        }

        // 3. 后缀匹配：指标名以 _trimmedName 结尾（对应 h1_h2 格式）
        String suffix = "_" + trimmedName;
        for (String name : allNames) {
            if (name.endsWith(suffix)) {
                return nameToCode.get(name);
            }
        }

        // 4. 归一化后匹配（~ 与 - 视为等价）
        String norm = trimmedName.replaceAll("[~\\-]", "_");
        String normSuffix = "_" + norm;
        for (String name : allNames) {
            String nameNorm = name.replaceAll("[~\\-]", "_");
            if (nameNorm.equals(norm)) {
                return nameToCode.get(name);
            }
            if (nameNorm.endsWith(normSuffix)) {
                return nameToCode.get(name);
            }
        }

        // 5. 完整规范化：去所有特殊字符（括号、引号、破折号等），仅保留中文和数字
        String fullNorm = normalizeName(trimmedName);
        if (!fullNorm.isEmpty() && !fullNorm.equals(norm)) {
            for (String name : allNames) {
                String nameFullNorm = normalizeName(name);
                if (nameFullNorm.equals(fullNorm)) {
                    return nameToCode.get(name);
                }
            }
        }

        // 6. 包含匹配（处理"IT通信电子"→"IT"这类复合名称）
        for (String name : allNames) {
            // 取 h1_h2 格式中的 h2 部分
            String h2part = name.substring(name.lastIndexOf('_') + 1);
            if (!h2part.isEmpty() && (trimmedName.contains(h2part) || h2part.contains(trimmedName))) {
                return nameToCode.get(name);
            }
        }
        // 再用完整名尝试包含匹配
        for (String name : allNames) {
            if (name.contains(trimmedName) || trimmedName.contains(name)) {
                return nameToCode.get(name);
            }
        }

        // 6. 未匹配到 → 动态创建 grid 类型指标（权重默认为 0）
        log.info("未匹配到人口热力指标，动态创建: {}", level3Name);
        return ensureIndicator(level3Name, "grid");
    }

    /**
     * 规范化匹配人口热力指标名，失败则用原始名创建（挂在指定父节点下）
     */
    private String matchIndicatorCodeFromNorm(String name, String parentCode) {
        if (name == null || name.isEmpty()) return null;
        String norm = normalizeName(name);
        if (!norm.isEmpty()) {
            // 1. ★ 优先在指定父节点下查找（避免多父级同名指标的歧义）
            if (parentCode != null) {
                List<JwIndicatorConfig> children = indicatorConfigMapper.selectByParent(parentCode);
                for (JwIndicatorConfig child : children) {
                    if (child.getIndicatorName() != null) {
                        String childNorm = normalizeName(child.getIndicatorName());
                        if (norm.equals(childNorm)) {
                            return child.getIndicatorCode();
                        }
                    }
                }
            }
            // 2. 回退到全局查找（兼容未指定父节点或自动导入指标）
            Map<String, JwIndicatorConfig> normMap = buildNormalizedMap();
            JwIndicatorConfig cfg = normMap.get(norm);
            if (cfg != null) return cfg.getIndicatorCode();
        }
        return ensureIndicator(name, "grid_auto", parentCode);
    }

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
        // 必须以 2022/2023/2024 开头，且不能是日期范围如 "2023-2025年本网点历任行长"
        return (colName.startsWith("2022") || colName.startsWith("2023") || colName.startsWith("2024"))
            && !colName.matches("^20[0-9]{2}-20[0-9]{2}.*");
    }

    /**
     * 判断 Row3 是否为纯年份行（基础数据格式的特征）
     * 统计非空单元格中匹配 "2024"/"2023"/"2022" 的比例
     */
    private boolean isYearOnlyRow(Row row, DataFormatter formatter) {
        if (row == null) return false;
        int total = 0;
        int yearMatch = 0;
        for (int c = 0; c < row.getLastCellNum(); c++) {
            String val = getCellStringValue(row.getCell(c), formatter);
            if (val != null) {
                total++;
                if (val.matches("\\d{4}")) {
                    yearMatch++;
                }
            }
        }
        // 至少有一个非空单元格，且 60% 以上为 4 位数字（年份）
        return total > 0 && (yearMatch * 100 / total) >= 60;
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
        // 规范化匹配（去特殊符号）
        Map<String, JwIndicatorConfig> normMap = buildNormalizedMap();
        String norm = normalizeName(nameWithoutYear);
        if (!norm.isEmpty()) {
            JwIndicatorConfig cfg = normMap.get(norm);
            if (cfg != null) return cfg.getIndicatorCode();
        }
        // 未匹配 → 动态创建
        return ensureIndicator(nameWithoutYear, "branch_auto");
    }

    /**
     * 将Excel列名映射到JwBranchInfo字段名
     */
    private String mapColumnToField(String colName) {
        Map<String, String> mapping = new LinkedHashMap<>();
        // 网点名称（Row3）
        mapping.put("一级支行", "primaryBranch");
        mapping.put("二级支行", "secondaryBranch");
        mapping.put("网点号", "branchCode");
        // 地理位置（Row3）
        mapping.put("行政区", "districtName");
        mapping.put("街道", "street");
        mapping.put("具体地址", "address");
        mapping.put("经度", "longitude");
        mapping.put("纬度", "latitude");
        // 人员信息 - 整体情况（Row4）
        mapping.put("总人数", "totalStaff");
        mapping.put("个人客户经理人数", "personalManager");
        mapping.put("对公客户经理人数（专职）", "corporateManager");
        mapping.put("客服经理（柜面）人数", "counterStaff");
        mapping.put("客服经理（厅堂）人数", "lobbyStaff");
        // 人员信息 - 网点行长任职情况（Row4）
        mapping.put("网点行长姓名", "branchManager");
        mapping.put("在本网点任职时间", "managerTenure");
        mapping.put("完整履历信息（人力资源系统内格式）", "managerResume");
        mapping.put("本网点历任行长", "managerHistory");
        // 面积及功能分区（Row3 + Row4）
        mapping.put("总面积", "totalArea");
        mapping.put("其中：若为多层，请填写除首层以外面积", "otherFloorArea");
        mapping.put("现金柜台个数", "cashCounter");
        mapping.put("非现金柜台个数", "nonCashCounter");
        mapping.put("个人客户经理工位个数", "managerSeat");
        // 其他（Row3 + Row4）
        mapping.put("自有/租赁", "propertyRight");
        mapping.put("租赁到期时间", "leaseExpire");
        mapping.put("最近一次装修时间", "lastRenovation");
        mapping.put("网点业态分类", "branchType");
        mapping.put("迁并情况", "relocation");

        return mapping.getOrDefault(colName, null);
    }

    /**
     * 根据字段名设置JwBranchInfo属性
     */
    private void setBranchField(JwBranchInfo branch, String fieldName, String value) {
        if (value == null || fieldName == null) return;
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
     * 去除特殊符号，仅保留中文和数字
     */
    private String normalizeName(String name) {
        if (name == null) return null;
        return name.replaceAll("[^\\u4e00-\\u9fff0-9]", "");
    }

    /** 规范化名称到指标编码的缓存 */
    private Map<String, JwIndicatorConfig> normalizedNameCache = null;

    /**
     * 构建规范化名称到指标编码的映射（缓存，只构建一次）
     */
    private Map<String, JwIndicatorConfig> buildNormalizedMap() {
        if (normalizedNameCache != null) return normalizedNameCache;

        List<JwIndicatorConfig> all = indicatorConfigMapper.selectJwIndicatorConfigList(new JwIndicatorConfig());
        Map<String, JwIndicatorConfig> map = new LinkedHashMap<>();
        for (JwIndicatorConfig cfg : all) {
            if (cfg.getIndicatorName() != null) {
                String norm = normalizeName(cfg.getIndicatorName());
                if (!norm.isEmpty() && !map.containsKey(norm)) {
                    map.put(norm, cfg);
                }
            }
        }
        normalizedNameCache = map;
        return map;
    }

    /**
     * 将基础数据格式的指标列名匹配到 indicator_code
     * <p>
     * 策略：
     * 1. 规范化名称后匹配（先 fullName，再 shortName）
     * 2. 未匹配 → 动态创建 branch_auto 指标（权重默认 0）
     *
     * @param fullName  子分类 + 列名组合（如 "全量个人金融资产 日均余额 (万元)"）
     * @param shortName 仅列名（如 "日均余额 (万元)"）
     * @return indicatorCode
     */
    private String matchBranchIndicator(String fullName, String shortName) {
        if (fullName == null && shortName == null) return null;

        // 1. 规范化名称匹配（去特殊符号，仅保留中文+数字）
        Map<String, JwIndicatorConfig> normMap = buildNormalizedMap();

        if (fullName != null) {
            String normFull = normalizeName(fullName);
            if (!normFull.isEmpty()) {
                JwIndicatorConfig cfg = normMap.get(normFull);
                if (cfg != null) return cfg.getIndicatorCode();
            }
        }

        // 2. ★ 子分类上下文匹配：当多个指标同名但上级分类不同时，
        //    从 fullName 提取子分类，找到对应父指标，在其子节点中筛选
        if (fullName != null && shortName != null) {
            String subCategory = extractSubCategory(fullName, shortName);
            if (subCategory != null) {
                String normSubCat = normalizeName(subCategory);
                String normShort = normalizeName(shortName);
                if (!normSubCat.isEmpty() && !normShort.isEmpty()) {
                    JwIndicatorConfig parentCfg = normMap.get(normSubCat);
                    if (parentCfg != null) {
                        String parentCode = parentCfg.getIndicatorCode();
                        List<JwIndicatorConfig> children = indicatorConfigMapper.selectByParent(parentCode);
                        for (JwIndicatorConfig child : children) {
                            if (child.getIndicatorName() != null) {
                                String childNorm = normalizeName(child.getIndicatorName());
                                if (normShort.equals(childNorm)) {
                                    return child.getIndicatorCode();
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. 回退：按 shortName 全局匹配（兼容无子分类或自动导入指标）
        if (shortName != null) {
            String normShort = normalizeName(shortName);
            if (!normShort.isEmpty()) {
                JwIndicatorConfig cfg = normMap.get(normShort);
                if (cfg != null) return cfg.getIndicatorCode();
            }
        }

        // 4. 未匹配 → 动态创建 branch_auto 指标（权重默认 0）
        String nameToUse = fullName != null ? fullName : shortName;
        log.info("未匹配到网点指标，动态创建: {}", nameToUse);
        return ensureIndicator(nameToUse, "branch_auto");
    }

    /**
     * 从 fullName 中提取子分类部分（去掉 shortName 后缀）
     * 例如: extractSubCategory("全量个人金融资产 日均余额（万元）", "日均余额（万元）") → "全量个人金融资产"
     */
    private String extractSubCategory(String fullName, String shortName) {
        if (fullName == null || shortName == null) return null;
        int idx = fullName.lastIndexOf(shortName);
        if (idx > 0) {
            String cat = fullName.substring(0, idx).trim();
            return cat.isEmpty() ? null : cat;
        }
        return null;
    }

    // ========== 动态指标创建 ==========

    /** 自动导入指标的父节点编码（branch_auto 类型） */
    private static final String AUTO_PARENT_BRANCH = "auto_import_branch";
    /** 自动导入指标的父节点编码（grid_auto 类型） */
    private static final String AUTO_PARENT_GRID = "auto_import_grid";

    /**
     * 确保指标在 jw_indicator_config 中存在，不存在则创建
     * 自动创建的指标统一挂在专用父节点或指定父节点下
     * @param parentCode 指定父节点编码，null 表示挂在自动导入根节点下
     * @return indicator_code
     */
    private String ensureIndicator(String indicatorName, String indicatorType, String parentCode) {
        if (indicatorName == null || indicatorName.isEmpty()) return null;

        // 已存在则直接返回
        JwIndicatorConfig existing = indicatorConfigMapper.selectByIndicatorName(indicatorName);
        if (existing != null) {
            // 如果已有记录但 parentCode 为空而调用方指定了父节点，则更新父节点
            if (parentCode != null && (existing.getParentCode() == null || existing.getParentCode().isEmpty())) {
                existing.setParentCode(parentCode);
                indicatorConfigMapper.updateJwIndicatorConfig(existing);
            }
            return existing.getIndicatorCode();
        }

        // 生成编码（去重）
        String baseCode = generateCodeFromName(indicatorName);
        String code = baseCode;
        int suffix = 0;
        while (indicatorConfigMapper.selectByCode(code) != null) {
            suffix++;
            code = baseCode + "_" + suffix;
        }

        JwIndicatorConfig config = new JwIndicatorConfig();
        config.setIndicatorCode(code);
        config.setIndicatorName(indicatorName);
        config.setIndicatorType(indicatorType);
        config.setIsDerived("0");
        config.setCalculationWeight(0.0);
        config.setSortOrder(0);
        config.setParentCode(parentCode != null ? parentCode : ensureAutoImportParent(indicatorType));
        indicatorConfigMapper.insertIndicatorConfig(config);
        log.info("动态创建指标: name={}, code={}, type={}, parent={}", indicatorName, code, indicatorType, config.getParentCode());
        return code;
    }

    /**
     * 确保指标存在，自动挂在对应类型的自动导入根节点下
     */
    private String ensureIndicator(String indicatorName, String indicatorType) {
        return ensureIndicator(indicatorName, indicatorType, null);
    }

    /**
     * 确保自动导入分类父节点存在
     */
    private String ensureAutoImportParent(String indicatorType) {
        String parentCode, parentName;
        if ("grid_auto".equals(indicatorType)) {
            parentCode = AUTO_PARENT_GRID;
            parentName = "自动导入(网格)";
        } else {
            parentCode = AUTO_PARENT_BRANCH;
            parentName = "自动导入(网点)";
        }
        JwIndicatorConfig existing = indicatorConfigMapper.selectByCode(parentCode);
        if (existing != null) return parentCode;

        JwIndicatorConfig parent = new JwIndicatorConfig();
        parent.setIndicatorCode(parentCode);
        parent.setIndicatorName(parentName);
        parent.setIndicatorType(indicatorType);
        parent.setIsDerived("0");
        parent.setCalculationWeight(0.0);
        parent.setSortOrder(-1);
        indicatorConfigMapper.insertIndicatorConfig(parent);
        log.info("创建自动导入分类: {} ({})", parentName, parentCode);
        return parentCode;
    }

    /**
     * 从名称生成 ASCII 编码（移除中文字符和特殊符号）
     */
    private String generateCodeFromName(String name) {
        String code = name.replaceAll("[^a-zA-Z0-9]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "")
            .toLowerCase();
        if (code.isEmpty()) {
            code = "indicator_" + System.currentTimeMillis() % 100000;
        }
        return code;
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
