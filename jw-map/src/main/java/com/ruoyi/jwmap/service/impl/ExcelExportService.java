package com.ruoyi.jwmap.service.impl;

import com.ruoyi.jwmap.domain.*;
import com.ruoyi.jwmap.mapper.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Excel数据导出服务
 * <p>
 * 导出格式为透视表（PIVOT）：将垂直存储的指标数据转为宽表格式
 * 第1行：指标名称
 * 第2行：实际权重
 * 第3行：MAX
 * 第4行：MIN
 * 第5行起：数据行
 */
@Service
public class ExcelExportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelExportService.class);

    @Autowired
    private JwGridDataRawMapper gridDataRawMapper;

    @Autowired
    private JwGridDataNormalizedMapper gridDataNormalizedMapper;

    @Autowired
    private JwGridSummaryMapper gridSummaryMapper;

    @Autowired
    private JwGridMetaMapper gridMetaMapper;

    @Autowired
    private JwGridScoreMapper gridScoreMapper;

    @Autowired
    private JwIndicatorConfigMapper indicatorConfigMapper;

    @Autowired
    private JwBranchInfoMapper branchInfoMapper;

    @Autowired
    private JwBranchIndicatorMapper branchIndicatorMapper;

    @Autowired
    private JwBranchSummaryMapper branchSummaryMapper;

    @Autowired
    private JwBranchScoreMapper branchScoreMapper;

    // ==================== 网格数据导出 ====================

    /**
     * 导出网格原始指标数据
     */
    public void exportGridDataRaw(String city, OutputStream outputStream) throws IOException {
        List<JwGridMeta> gridMetas = gridMetaMapper.selectByCity(city);
        List<JwGridSummary> summaries = gridSummaryMapper.selectByCity(city);
        List<JwGridDataRaw> dataList = gridDataRawMapper.selectAllByCity(city);

        Map<String, JwGridSummary> summaryMap = summaries.stream()
                .collect(Collectors.toMap(JwGridSummary::getIndicatorCode, s -> s, (a, b) -> a));

        // 从实际数据中提取指标编码（不依赖indicator_config，避免source_tables筛选问题）
        Set<String> indicatorCodes = new LinkedHashSet<>();
        Map<String, Map<String, Double>> gridIndicatorMap = new LinkedHashMap<>();
        for (JwGridDataRaw data : dataList) {
            indicatorCodes.add(data.getIndicatorCode());
            gridIndicatorMap.computeIfAbsent(data.getGridCode(), k -> new LinkedHashMap<>())
                    .put(data.getIndicatorCode(), data.getIndicatorValue());
        }

        writePivotGridWorkbook(outputStream, "原始指标数据", gridMetas, indicatorCodes,
                summaryMap, gridIndicatorMap, false, false);
    }

    /**
     * 导出网格归一化指标数据
     */
    public void exportGridDataNormalized(String city, OutputStream outputStream) throws IOException {
        List<JwGridMeta> gridMetas = gridMetaMapper.selectByCity(city);
        List<JwGridSummary> summaries = gridSummaryMapper.selectByCity(city);
        List<JwGridDataNormalized> dataList = gridDataNormalizedMapper.selectAllByCity(city);

        Map<String, JwGridSummary> summaryMap = summaries.stream()
                .collect(Collectors.toMap(JwGridSummary::getIndicatorCode, s -> s, (a, b) -> a));

        // 从实际数据中提取指标编码
        Set<String> indicatorCodes = new LinkedHashSet<>();
        Map<String, Map<String, Double>> gridIndicatorMap = new LinkedHashMap<>();
        for (JwGridDataNormalized data : dataList) {
            indicatorCodes.add(data.getIndicatorCode());
            gridIndicatorMap.computeIfAbsent(data.getGridCode(), k -> new LinkedHashMap<>())
                    .put(data.getIndicatorCode(), data.getNormalizedValue());
        }

        writePivotGridWorkbook(outputStream, "归一化指标数据", gridMetas, indicatorCodes,
                summaryMap, gridIndicatorMap, true, false);
    }

    /**
     * 写入网格透视Workbook
     *
     * @param isNormalized 是否已归一化（影响summary中取max/min/norm字段名）
     */
    private void writePivotGridWorkbook(OutputStream outputStream, String sheetName,
                                         List<JwGridMeta> gridMetas,
                                         Collection<String> indicatorCodes,
                                         Map<String, JwGridSummary> summaryMap,
                                         Map<String, Map<String, Double>> gridIndicatorMap,
                                         boolean isNormalized,
                                         boolean isWithScore) throws IOException {
        // 大网格数据用SXSSFWorkbook
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        try {
            Sheet sheet = workbook.createSheet(sheetName);

            // 创建样式
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle weightStyle = createWeightStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle maxMinStyle = createMaxMinStyle(workbook);

            // 预加载指标名称（查config，找不到就用编码本身）
            List<String> codeList = new ArrayList<>(indicatorCodes);
            Map<String, String> indicatorNameMap = new LinkedHashMap<>();
            for (String code : codeList) {
                JwIndicatorConfig config = indicatorConfigMapper.selectByCode(code);
                indicatorNameMap.put(code, config != null ? config.getIndicatorName() : code);
            }

            // ===== 固定列: 9列双层表头 (网格编号/经度/纬度/西经/东经/北纬/南纬/省/市) =====
            int FIXED_COLS = 9;
            String[] h1Labels = {"网格编号", "经度", "纬度", "经度范围", "", "纬度范围", "", "省", "市"};
            String[] h2Labels = {null,    null,  null,   "西经",  "东经", "北纬", "南纬", null, null};
            // Row1 horizontal merge: only 经度范围(D-E) and 纬度范围(F-G); 省(7)市(8)单独
            int[] h1MergeGroups = {0, 1, 2, 3, 5};
            int[] h1MergeCounts  = {1, 1, 1, 2, 2};
            int[] h12MergeSingle = {0, 1, 2, 7, 8};

            int rowNum = 0;

            Row h1Row = sheet.createRow(rowNum++);
            int col = 0;
            for (int g = 0; g < h1MergeGroups.length; g++) {
                int start = h1MergeGroups[g];
                int count = h1MergeCounts[g];
                for (int i = 0; i < count; i++) {
                    Cell c = h1Row.createCell(col++);
                    c.setCellStyle(headerStyle);
                    if (i == 0) c.setCellValue(h1Labels[start]);
                }
                if (count > 1) sheet.addMergedRegion(new CellRangeAddress(0, 0, col - count, col - 1));
            }
            h1Row.getCell(7).setCellValue("省");
            h1Row.getCell(8).setCellValue("市");
            for (int c : h12MergeSingle) {
                sheet.addMergedRegion(new CellRangeAddress(0, 1, c, c));
            }

            // Row 2: header level 2 (西经/东经/北纬/南纬)
            Row h2Row = sheet.createRow(rowNum++);
            for (int i = 0; i < FIXED_COLS; i++) {
                Cell c = h2Row.createCell(i);
                c.setCellStyle(headerStyle);
                if (h2Labels[i] != null) c.setCellValue(h2Labels[i]);
            }

            // indicator column headers
            int colIdx = FIXED_COLS;
            for (String code : codeList) {
                Cell cell = h1Row.createCell(colIdx);
                cell.setCellValue(indicatorNameMap.getOrDefault(code, code));
                cell.setCellStyle(headerStyle);
                h2Row.createCell(colIdx).setCellStyle(headerStyle);
                colIdx++;
            }

            // 第3行：实际权重
            Row weightRow = sheet.createRow(rowNum++);
            weightRow.createCell(0).setCellValue("实际权重");
            weightRow.getCell(0).setCellStyle(weightStyle);
            for (int i = 1; i < FIXED_COLS; i++) weightRow.createCell(i).setCellStyle(maxMinStyle);
            colIdx = FIXED_COLS;
            for (String code : codeList) {
                JwGridSummary summary = summaryMap.get(code);
                Cell cell = weightRow.createCell(colIdx++);
                if (summary != null && summary.getActualWeight() != null) cell.setCellValue(summary.getActualWeight());
                cell.setCellStyle(weightStyle);
            }

            // 第4行：MAX
            Row maxRow = sheet.createRow(rowNum++);
            maxRow.createCell(0).setCellValue("MAX");
            maxRow.getCell(0).setCellStyle(maxMinStyle);
            for (int i = 1; i < FIXED_COLS; i++) maxRow.createCell(i).setCellStyle(maxMinStyle);
            colIdx = FIXED_COLS;
            for (String code : codeList) {
                JwGridSummary summary = summaryMap.get(code);
                Cell cell = maxRow.createCell(colIdx++);
                if (summary != null) {
                    cell.setCellValue(isNormalized
                            ? (summary.getMaxNorm() != null ? summary.getMaxNorm() : 0)
                            : (summary.getMaxRaw() != null ? summary.getMaxRaw() : 0));
                }
                cell.setCellStyle(maxMinStyle);
            }

            // 第5行：MIN
            Row minRow = sheet.createRow(rowNum++);
            minRow.createCell(0).setCellValue("MIN");
            minRow.getCell(0).setCellStyle(maxMinStyle);
            for (int i = 1; i < FIXED_COLS; i++) minRow.createCell(i).setCellStyle(maxMinStyle);
            colIdx = FIXED_COLS;
            for (String code : codeList) {
                JwGridSummary summary = summaryMap.get(code);
                Cell cell = minRow.createCell(colIdx++);
                if (summary != null) {
                    cell.setCellValue(isNormalized
                            ? (summary.getMinNorm() != null ? summary.getMinNorm() : 0)
                            : (summary.getMinRaw() != null ? summary.getMinRaw() : 0));
                }
                cell.setCellStyle(maxMinStyle);
            }

            // 第6行起：数据行
            for (JwGridMeta meta : gridMetas) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(meta.getGridCode());
                row.getCell(1).setCellValue(meta.getLongitude() != null ? meta.getLongitude() : 0);
                row.getCell(2).setCellValue(meta.getLatitude() != null ? meta.getLatitude() : 0);
                row.getCell(3).setCellValue(meta.getWestLongitude() != null ? meta.getWestLongitude() : 0);
                row.getCell(4).setCellValue(meta.getEastLongitude() != null ? meta.getEastLongitude() : 0);
                row.getCell(5).setCellValue(meta.getNorthLatitude() != null ? meta.getNorthLatitude() : 0);
                row.getCell(6).setCellValue(meta.getSouthLatitude() != null ? meta.getSouthLatitude() : 0);
                row.getCell(7).setCellValue(meta.getProvince() != null ? meta.getProvince() : "");
                row.getCell(8).setCellValue(meta.getCity() != null ? meta.getCity() : "");
                for (int i = 0; i < FIXED_COLS; i++) row.getCell(i).setCellStyle(dataStyle);

                Map<String, Double> indicatorValues = gridIndicatorMap.get(meta.getGridCode());
                colIdx = FIXED_COLS;
                for (String code : codeList) {
                    Cell cell = row.createCell(colIdx++);
                    if (indicatorValues != null && indicatorValues.containsKey(code)) {
                        cell.setCellValue(indicatorValues.get(code));
                    }
                    cell.setCellStyle(dataStyle);
                }
            }

            // 固定列宽
            for (int i = 0; i < FIXED_COLS; i++) sheet.setColumnWidth(i, 14 * 256);
            for (int i = FIXED_COLS; i < FIXED_COLS + codeList.size(); i++) sheet.setColumnWidth(i, 12 * 256);

            workbook.write(outputStream);
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }

    // ==================== 网点数据导出 ====================

    /**
     * 导出网点基础数据
     * 根据控制器调用，方法签名为(city, outputStream)，内部使用当前最新年份和"基础数据"sheetType
     */
    public void exportBranchBaseData(String city, OutputStream outputStream) throws IOException {
        List<JwBranchInfo> branches = branchInfoMapper.selectByCity(city);
        if (branches.isEmpty()) {
            writeBranchPivotWorkbook(outputStream, "基础数据", branches, null, null, null);
            return;
        }

        // 获取该市所有基础数据的年份，取最新年份
        List<JwBranchIndicator> sample = branchIndicatorMapper.selectByCityAndSheetType(city, "基础数据");
        if (sample.isEmpty()) {
            writeBranchPivotWorkbook(outputStream, "基础数据", branches, null, null, null);
            return;
        }
        Integer maxYear = sample.stream()
                .map(JwBranchIndicator::getDataYear)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(2024);

        List<JwBranchIndicator> indicators = branchIndicatorMapper.selectByCityAndYear(city, maxYear, "基础数据");
        writeBranchPivotWorkbook(outputStream, "基础数据", branches, indicators, null, null);
    }

    /**
     * 导出网点计算数据（数据计算表）
     */
    public void exportBranchCalcData(String city, Integer dataYear, OutputStream outputStream) throws IOException {
        List<JwBranchInfo> branches = branchInfoMapper.selectByCity(city);
        List<JwBranchIndicator> indicators = branchIndicatorMapper.selectByCityAndYear(city, dataYear, "数据计算表");
        List<JwBranchSummary> summaries = branchSummaryMapper.selectByCityAndYear(city, dataYear);

        Map<String, JwBranchSummary> summaryMap = summaries.stream()
                .collect(Collectors.toMap(JwBranchSummary::getIndicatorCode, s -> s, (a, b) -> a));

        writeBranchPivotWorkbook(outputStream, "数据计算表", branches, indicators, summaryMap, null);
    }

    /**
     * 导出网点归一化数据（数据计算表归一化）
     */
    public void exportBranchNormalized(String city, Integer dataYear, OutputStream outputStream) throws IOException {
        List<JwBranchInfo> branches = branchInfoMapper.selectByCity(city);
        List<JwBranchIndicator> indicators = branchIndicatorMapper.selectByCityAndYear(city, dataYear, "数据计算表归一化");
        List<JwBranchSummary> summaries = branchSummaryMapper.selectByCityAndYear(city, dataYear);

        Map<String, JwBranchSummary> summaryMap = summaries.stream()
                .collect(Collectors.toMap(JwBranchSummary::getIndicatorCode, s -> s, (a, b) -> a));

        writeBranchPivotWorkbook(outputStream, "归一化数据", branches, indicators, summaryMap, null);
    }

    /**
     * 写网点透视Workbook
     */
    private void writeBranchPivotWorkbook(OutputStream outputStream, String sheetName,
                                           List<JwBranchInfo> branches,
                                           List<JwBranchIndicator> indicators,
                                           Map<String, JwBranchSummary> summaryMap,
                                           List<JwBranchScore> scores) throws IOException {
        // 网点数据量较小，用XSSFWorkbook
        XSSFWorkbook workbook = new XSSFWorkbook();
        try {
            Sheet sheet = workbook.createSheet(sheetName);

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle weightStyle = createWeightStyle(workbook);
            CellStyle maxMinStyle = createMaxMinStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            // 构建branchId -> indicatorCode -> value 映射
            Map<Long, Map<String, Double>> branchValueMap = new LinkedHashMap<>();
            Set<String> indicatorCodes = new LinkedHashSet<>();
            if (indicators != null) {
                for (JwBranchIndicator ind : indicators) {
                    branchValueMap.computeIfAbsent(ind.getBranchId(), k -> new LinkedHashMap<>())
                            .put(ind.getIndicatorCode(), ind.getIndicatorValue());
                    indicatorCodes.add(ind.getIndicatorCode());
                }
            }

            List<String> indicatorCodeList = new ArrayList<>(indicatorCodes);
            Map<String, String> indicatorNameMap = new LinkedHashMap<>();
            for (String code : indicatorCodeList) {
                JwIndicatorConfig config = indicatorConfigMapper.selectByCode(code);
                indicatorNameMap.put(code, config != null ? config.getIndicatorName() : code);
            }

            int rowNum = 0;

            // 第1行：表头
            Row headerRow = sheet.createRow(rowNum++);
            headerRow.createCell(0).setCellValue("网点编码");
            headerRow.getCell(0).setCellStyle(headerStyle);
            headerRow.createCell(1).setCellValue("网点名称");
            headerRow.getCell(1).setCellStyle(headerStyle);

            int colIdx = 2;
            for (String code : indicatorCodeList) {
                Cell cell = headerRow.createCell(colIdx++);
                cell.setCellValue(indicatorNameMap.getOrDefault(code, code));
                cell.setCellStyle(headerStyle);
            }

            // 第2-4行：汇总信息（仅在有summaryMap时）
            if (summaryMap != null && !summaryMap.isEmpty()) {
                // 第2行：实际权重
                Row weightRow = sheet.createRow(rowNum++);
                weightRow.createCell(0).setCellValue("实际权重");
                weightRow.getCell(0).setCellStyle(weightStyle);
                weightRow.createCell(1).setCellStyle(maxMinStyle);
                colIdx = 2;
                for (String code : indicatorCodeList) {
                    JwBranchSummary summary = summaryMap.get(code);
                    Cell cell = weightRow.createCell(colIdx++);
                    if (summary != null && summary.getActualWeight() != null) {
                        cell.setCellValue(summary.getActualWeight());
                    }
                    cell.setCellStyle(weightStyle);
                }

                // 第3行：MAX
                Row maxRow = sheet.createRow(rowNum++);
                maxRow.createCell(0).setCellValue("MAX");
                maxRow.getCell(0).setCellStyle(maxMinStyle);
                maxRow.createCell(1).setCellStyle(maxMinStyle);
                colIdx = 2;
                for (String code : indicatorCodeList) {
                    JwBranchSummary summary = summaryMap.get(code);
                    Cell cell = maxRow.createCell(colIdx++);
                    if (summary != null) {
                        // 优先取maxNorm，没有则取maxValue
                        Double val = summary.getMaxNorm() != null ? summary.getMaxNorm() : summary.getMaxValue();
                        if (val != null) cell.setCellValue(val);
                    }
                    cell.setCellStyle(maxMinStyle);
                }

                // 第4行：MIN
                Row minRow = sheet.createRow(rowNum++);
                minRow.createCell(0).setCellValue("MIN");
                minRow.getCell(0).setCellStyle(maxMinStyle);
                minRow.createCell(1).setCellStyle(maxMinStyle);
                colIdx = 2;
                for (String code : indicatorCodeList) {
                    JwBranchSummary summary = summaryMap.get(code);
                    Cell cell = minRow.createCell(colIdx++);
                    if (summary != null) {
                        Double val = summary.getMinNorm() != null ? summary.getMinNorm() : summary.getMinValue();
                        if (val != null) cell.setCellValue(val);
                    }
                    cell.setCellStyle(maxMinStyle);
                }
            }

            // 数据行（跳过表头类垃圾行）
            Set<String> headerLike = new HashSet<>(Arrays.asList("一级支行", "二级支行", "机构号"));
            for (JwBranchInfo branch : branches) {
                if (branch.getPrimaryBranch() != null && headerLike.contains(branch.getPrimaryBranch().trim())) {
                    continue;
                }
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(branch.getBranchCode() != null ? branch.getBranchCode() : "");
                row.getCell(0).setCellStyle(dataStyle);
                row.createCell(1).setCellValue(branch.getPrimaryBranch() != null ? branch.getPrimaryBranch() : "");
                row.getCell(1).setCellStyle(dataStyle);

                Map<String, Double> values = branchValueMap.get(branch.getBranchId());
                colIdx = 2;
                for (String code : indicatorCodeList) {
                    Cell cell = row.createCell(colIdx++);
                    if (values != null && values.containsKey(code)) {
                        cell.setCellValue(values.get(code));
                    }
                    cell.setCellStyle(dataStyle);
                }
            }

            // 自适应列宽
            for (int i = 0; i < indicatorCodeList.size() + 2; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
        } finally {
            workbook.close();
        }
    }

    // ==================== 网格组合导出（一个文件多个Sheet） ====================

    /**
     * 导出网格组合数据：原始数据 + 归一化得分（含TOPSIS得分）
     */
    public void exportGridCombined(String city, OutputStream outputStream) throws IOException {
        List<JwGridMeta> gridMetas = gridMetaMapper.selectByCity(city);
        List<JwGridSummary> summaries = gridSummaryMapper.selectByCity(city);
        List<JwGridScore> scores = gridScoreMapper.selectByCity(city);

        Map<String, JwGridScore> scoreMap = scores.stream()
                .collect(Collectors.toMap(JwGridScore::getGridCode, s -> s, (a, b) -> a));
        Map<String, JwGridSummary> summaryMap = summaries.stream()
                .collect(Collectors.toMap(JwGridSummary::getIndicatorCode, s -> s, (a, b) -> a));

        // Raw data
        List<JwGridDataRaw> rawList = gridDataRawMapper.selectAllByCity(city);
        Set<String> rawCodes = new LinkedHashSet<>();
        Map<String, Map<String, Double>> rawGridMap = new LinkedHashMap<>();
        for (JwGridDataRaw d : rawList) {
            rawCodes.add(d.getIndicatorCode());
            rawGridMap.computeIfAbsent(d.getGridCode(), k -> new LinkedHashMap<>()).put(d.getIndicatorCode(), d.getIndicatorValue());
        }

        // Normalized data
        List<JwGridDataNormalized> normList = gridDataNormalizedMapper.selectAllByCity(city);
        Set<String> normCodes = new LinkedHashSet<>();
        Map<String, Map<String, Double>> normGridMap = new LinkedHashMap<>();
        for (JwGridDataNormalized d : normList) {
            normCodes.add(d.getIndicatorCode());
            normGridMap.computeIfAbsent(d.getGridCode(), k -> new LinkedHashMap<>()).put(d.getIndicatorCode(), d.getNormalizedValue());
        }

        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        try {
            // Sheet 1: 原始指标数据
            writePivotGridSheet(workbook.createSheet("原始指标数据"), gridMetas, rawCodes, summaryMap, rawGridMap, false, null);

            // Sheet 2: 归一化得分（含TOPSIS得分列）
            Sheet normSheet = workbook.createSheet("归一化得分");
            writePivotGridSheet(normSheet, gridMetas, normCodes, summaryMap, normGridMap, true, scoreMap);

            workbook.write(outputStream);
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }

    private void writePivotGridSheet(Sheet sheet,
                                      List<JwGridMeta> gridMetas,
                                      Collection<String> indicatorCodes,
                                      Map<String, JwGridSummary> summaryMap,
                                      Map<String, Map<String, Double>> gridIndicatorMap,
                                      boolean isNormalized,
                                      Map<String, JwGridScore> scoreMap) {
        CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());
        CellStyle weightStyle = createWeightStyle(sheet.getWorkbook());
        CellStyle dataStyle = createDataStyle(sheet.getWorkbook());
        CellStyle maxMinStyle = createMaxMinStyle(sheet.getWorkbook());

        // ===== 1. 按 categoryLevel1 分组 =====
        Map<String, JwIndicatorConfig> configMap = new LinkedHashMap<>();
        for (String code : indicatorCodes) {
            if (!configMap.containsKey(code)) {
                JwIndicatorConfig config = indicatorConfigMapper.selectByCode(code);
                if (config != null) configMap.put(code, config);
            }
        }

        LinkedHashMap<String, List<String>> catGroups = new LinkedHashMap<>();
        for (String code : indicatorCodes) {
            JwIndicatorConfig cfg = configMap.get(code);
            String cat = "其他";
            if (cfg != null) {
                // 使用 parent_code 链找到根节点名称作为分类
                String rootName = getRootName(cfg, configMap);
                if (rootName != null && !rootName.isEmpty()) {
                    cat = rootName;
                }
            }
            catGroups.computeIfAbsent(cat, k -> new ArrayList<>()).add(code);
        }

        // 参考模板类别顺序
        String[] CATEGORY_ORDER = {
            "常住、流动人口", "年龄", "收入水平", "教育水平", "性别",
            "所在行业", "职业类别", "资产状况", "人生阶段", "消费水平", "POI"
        };

        List<String> orderedCodes = new ArrayList<>();
        List<String> catLabels = new ArrayList<>();
        List<Integer> catCounts = new ArrayList<>();

        for (String refCat : CATEGORY_ORDER) {
            List<String> codes = catGroups.remove(refCat);
            if (codes != null) {
                catLabels.add(refCat);
                catCounts.add(codes.size());
                orderedCodes.addAll(codes);
            }
        }
        for (Map.Entry<String, List<String>> e : catGroups.entrySet()) {
            catLabels.add(e.getKey());
            catCounts.add(e.getValue().size());
            orderedCodes.addAll(e.getValue());
        }

        // ===== 2. 布局常量 =====
        int FIXED_COLS = 10; // 新增 区县
        int scoreColCount = (scoreMap != null) ? 3 : 0;
        int totalCols = FIXED_COLS + orderedCodes.size() + scoreColCount;

        String[] fixedH1 = {"网格编号", "经度", "纬度", "经度范围", "", "纬度范围", "", "省", "市", "区县"};
        String[] fixedH2 = {null, null, null, "西经", "东经", "北纬", "南纬", null, null, null};
        // 需要合并R0-R1的固定列（单列垂直合并）
        int[] h12Singles = {0, 1, 2, 7, 8, 9};
        // 需要R0横向合并的分组
        int[][] fixedGroups = {{3, 4}, {5, 6}};

        int rowNum = 0;

        // ===== Row 0: 类别行 =====
        Row h0Row = sheet.createRow(rowNum++);
        for (int i = 0; i < FIXED_COLS; i++) {
            Cell c = h0Row.createCell(i);
            c.setCellStyle(headerStyle);
            String v = fixedH1[i];
            if (v != null && !v.isEmpty()) c.setCellValue(v);
        }
        for (int[] g : fixedGroups) {
            sheet.addMergedRegion(new CellRangeAddress(0, 0, g[0], g[1]));
        }

        // 指标类别
        int colIdx = FIXED_COLS;
        for (int gi = 0; gi < catLabels.size(); gi++) {
            String cat = catLabels.get(gi);
            int cnt = catCounts.get(gi);
            Cell c = h0Row.createCell(colIdx);
            c.setCellValue(cat);
            c.setCellStyle(headerStyle);
            if (cnt > 1) {
                sheet.addMergedRegion(new CellRangeAddress(0, 0, colIdx, colIdx + cnt - 1));
            }
            for (int j = 1; j < cnt; j++) {
                h0Row.createCell(colIdx + j).setCellStyle(headerStyle);
            }
            colIdx += cnt;
        }

        // 得分列标题
        if (scoreMap != null) {
            for (String lbl : new String[]{"D+", "D-", "选址得分"}) {
                Cell c = h0Row.createCell(colIdx++);
                c.setCellValue(lbl);
                c.setCellStyle(headerStyle);
            }
        }

        // ===== Row 1: 指标名行 =====
        Row h1Row = sheet.createRow(rowNum++);
        for (int i = 0; i < FIXED_COLS; i++) {
            Cell c = h1Row.createCell(i);
            c.setCellStyle(headerStyle);
            if (fixedH2[i] != null) c.setCellValue(fixedH2[i]);
        }
        // 单列垂直合并（固定列）
        for (int s : h12Singles) {
            sheet.addMergedRegion(new CellRangeAddress(0, 1, s, s));
        }

        colIdx = FIXED_COLS;
        for (String code : orderedCodes) {
            JwIndicatorConfig cfg = configMap.get(code);
            String name = (cfg != null && cfg.getIndicatorName() != null) ? cfg.getIndicatorName() : code;
            Cell c = h1Row.createCell(colIdx++);
            c.setCellValue(name);
            c.setCellStyle(headerStyle);
        }
        if (scoreMap != null) {
            for (int i = 0; i < 3; i++) {
                h1Row.createCell(colIdx++).setCellStyle(headerStyle);
            }
        }

        // ===== Row 2: 实际权重 =====
        Row weightRow = sheet.createRow(rowNum++);
        Cell wc0 = weightRow.createCell(0);
        wc0.setCellValue("实际权重");
        wc0.setCellStyle(weightStyle);
        for (int i = 1; i < FIXED_COLS; i++) weightRow.createCell(i).setCellStyle(maxMinStyle);
        colIdx = FIXED_COLS;
        for (String code : orderedCodes) {
            Cell c = weightRow.createCell(colIdx++);
            JwGridSummary summary = summaryMap.get(code);
            if (summary != null && summary.getActualWeight() != null) c.setCellValue(summary.getActualWeight());
            c.setCellStyle(weightStyle);
        }
        for (int i = 0; i < scoreColCount; i++) weightRow.createCell(colIdx++).setCellStyle(maxMinStyle);

        // ===== Row 3: MAX =====
        Row maxRow = sheet.createRow(rowNum++);
        Cell maxC0 = maxRow.createCell(0);
        maxC0.setCellValue("MAX");
        maxC0.setCellStyle(maxMinStyle);
        for (int i = 1; i < FIXED_COLS; i++) maxRow.createCell(i).setCellStyle(maxMinStyle);
        colIdx = FIXED_COLS;
        for (String code : orderedCodes) {
            Cell c = maxRow.createCell(colIdx++);
            JwGridSummary summary = summaryMap.get(code);
            if (summary != null) {
                c.setCellValue(isNormalized
                        ? (summary.getMaxNorm() != null ? summary.getMaxNorm() : 0)
                        : (summary.getMaxRaw() != null ? summary.getMaxRaw() : 0));
            }
            c.setCellStyle(maxMinStyle);
        }
        for (int i = 0; i < scoreColCount; i++) maxRow.createCell(colIdx++).setCellStyle(maxMinStyle);

        // ===== Row 4: MIN =====
        Row minRow = sheet.createRow(rowNum++);
        Cell minC0 = minRow.createCell(0);
        minC0.setCellValue("MIN");
        minC0.setCellStyle(maxMinStyle);
        for (int i = 1; i < FIXED_COLS; i++) minRow.createCell(i).setCellStyle(maxMinStyle);
        colIdx = FIXED_COLS;
        for (String code : orderedCodes) {
            Cell c = minRow.createCell(colIdx++);
            JwGridSummary summary = summaryMap.get(code);
            if (summary != null) {
                c.setCellValue(isNormalized
                        ? (summary.getMinNorm() != null ? summary.getMinNorm() : 0)
                        : (summary.getMinRaw() != null ? summary.getMinRaw() : 0));
            }
            c.setCellStyle(maxMinStyle);
        }
        for (int i = 0; i < scoreColCount; i++) minRow.createCell(colIdx++).setCellStyle(maxMinStyle);

        // ===== Row 5+: 数据行 =====
        for (JwGridMeta meta : gridMetas) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(meta.getGridCode());
            row.createCell(1).setCellValue(meta.getLongitude() != null ? meta.getLongitude() : 0);
            row.createCell(2).setCellValue(meta.getLatitude() != null ? meta.getLatitude() : 0);
            row.createCell(3).setCellValue(meta.getWestLongitude() != null ? meta.getWestLongitude() : 0);
            row.createCell(4).setCellValue(meta.getEastLongitude() != null ? meta.getEastLongitude() : 0);
            row.createCell(5).setCellValue(meta.getNorthLatitude() != null ? meta.getNorthLatitude() : 0);
            row.createCell(6).setCellValue(meta.getSouthLatitude() != null ? meta.getSouthLatitude() : 0);
            row.createCell(7).setCellValue(meta.getProvince() != null ? meta.getProvince() : "");
            row.createCell(8).setCellValue(meta.getCity() != null ? meta.getCity() : "");
            row.createCell(9).setCellValue(meta.getDistrict() != null ? meta.getDistrict() : "");
            for (int i = 0; i < FIXED_COLS; i++) row.getCell(i).setCellStyle(dataStyle);

            Map<String, Double> vals = gridIndicatorMap.get(meta.getGridCode());
            colIdx = FIXED_COLS;
            for (String code : orderedCodes) {
                Cell c = row.createCell(colIdx++);
                if (vals != null) {
                    Double v = vals.get(code);
                    if (v != null) c.setCellValue(v);
                }
                c.setCellStyle(dataStyle);
            }
            if (scoreMap != null) {
                JwGridScore sc = scoreMap.get(meta.getGridCode());
                Cell dc = row.createCell(colIdx++);
                if (sc != null && sc.getPositiveDistance() != null) dc.setCellValue(sc.getPositiveDistance());
                dc.setCellStyle(dataStyle);
                Cell nc = row.createCell(colIdx++);
                if (sc != null && sc.getNegativeDistance() != null) nc.setCellValue(sc.getNegativeDistance());
                nc.setCellStyle(dataStyle);
                Cell scCell = row.createCell(colIdx++);
                if (sc != null && sc.getSiteScore() != null) scCell.setCellValue(sc.getSiteScore());
                scCell.setCellStyle(dataStyle);
            }
        }

        // ===== 固定列宽（避免 SXSSF autoSize 卡死） =====
        for (int i = 0; i < FIXED_COLS; i++) sheet.setColumnWidth(i, 14 * 256);
        for (int i = FIXED_COLS; i < totalCols; i++) sheet.setColumnWidth(i, 12 * 256);
    }

    // ==================== 网点组合导出（一个文件多个Sheet） ====================

    // 计算指标显示名称（用于数据计算表/归一化Sheet的列头，严格匹配参考格式）
    private static final Map<String, String> CALC_DISPLAY_NAME_MAP = new LinkedHashMap<>();
    static {
        // revenue
        CALC_DISPLAY_NAME_MAP.put("branch_rev_per_capita", "人均营业收入\n(万元)");
        CALC_DISPLAY_NAME_MAP.put("branch_rev_per_area", "单位面积营业收入\n(万元)");
        // indicator / 全量个人金融资产
        CALC_DISPLAY_NAME_MAP.put("branch_asset_avg_balance", "户日均余额\n(万元)");
        CALC_DISPLAY_NAME_MAP.put("branch_asset_avg_growth", "日均增幅");
        // indicator / 储蓄存款
        CALC_DISPLAY_NAME_MAP.put("branch_saving_avg_balance", "户日均余额\n(万元)");
        CALC_DISPLAY_NAME_MAP.put("branch_saving_avg_growth", "日均增幅");
        // indicator / 公司客户存款
        CALC_DISPLAY_NAME_MAP.put("branch_corp_dep_avg_balance", "户日均余额\n(万元)");
        CALC_DISPLAY_NAME_MAP.put("branch_corp_dep_avg_growth", "日均增幅");
        // indicator / 机构客户存款
        CALC_DISPLAY_NAME_MAP.put("branch_inst_dep_avg_balance", "户日均余额\n(万元)");
        CALC_DISPLAY_NAME_MAP.put("branch_inst_dep_avg_growth", "日均增幅");
        // indicator / 普惠贷款 + 个人贷款
        CALC_DISPLAY_NAME_MAP.put("branch_incloan_per_capita", "人均营销额\n(万元)");
        CALC_DISPLAY_NAME_MAP.put("branch_perloan_per_capita", "人均发放额\n(万元)");
        // customer / 个人客户
        CALC_DISPLAY_NAME_MAP.put("branch_pcust_t1_per_capita", "人均服务日均0元(不含)-20万元(不含)客户数\n(单位：户)");
        CALC_DISPLAY_NAME_MAP.put("branch_pcust_t2_per_capita", "人均服务日均20万元(含)-600万元(不含)客户数\n(单位：户)");
        CALC_DISPLAY_NAME_MAP.put("branch_pcust_t3_per_capita", "人均服务日均大于等于600万元(含)客户数\n(单位：户)");
        // customer / 对公客户
        CALC_DISPLAY_NAME_MAP.put("branch_ccust_h_per_capita", "人均服务头部、中部对公客户数日均资产30万元(含)以上\n(单位：户)");
        CALC_DISPLAY_NAME_MAP.put("branch_ccust_l_per_capita", "人均服务底尾部对公客户数日均资产30万元(不含)以下\n(单位：户)");
        // customer / 机构客户
        CALC_DISPLAY_NAME_MAP.put("branch_icust_h_per_capita", "人均服务日均资产1万元(不含)以上机构客户数\n(单位：户)");
        CALC_DISPLAY_NAME_MAP.put("branch_icust_l_per_capita", "人均服务日均资产1万元(含)以下机构客户数\n(单位：户)");
        // operation
        CALC_DISPLAY_NAME_MAP.put("branch_counter_per_area", "每单位面积柜台日均工作量(笔)");
        CALC_DISPLAY_NAME_MAP.put("branch_terminal_per_area", "每单位面积自助终端日均交易笔数");
        CALC_DISPLAY_NAME_MAP.put("branch_atm_per_area", "每单位面积附行式ATM日均交易笔数");
    }

    // 22个计算指标编码（按分类顺序）
    private static final List<String> ALL_CALC_INDICATORS = Collections.unmodifiableList(Arrays.asList(
        // revenue (2)
        "branch_rev_per_capita", "branch_rev_per_area",
        // indicator (10)
        "branch_asset_avg_balance", "branch_asset_avg_growth",
        "branch_saving_avg_balance", "branch_saving_avg_growth",
        "branch_corp_dep_avg_balance", "branch_corp_dep_avg_growth",
        "branch_inst_dep_avg_balance", "branch_inst_dep_avg_growth",
        "branch_incloan_per_capita", "branch_perloan_per_capita",
        // customer (7)
        "branch_pcust_t1_per_capita", "branch_pcust_t2_per_capita", "branch_pcust_t3_per_capita",
        "branch_ccust_h_per_capita", "branch_ccust_l_per_capita",
        "branch_icust_h_per_capita", "branch_icust_l_per_capita",
        // operation (3)
        "branch_counter_per_area", "branch_terminal_per_area", "branch_atm_per_area"
    ));

    // 需要显示为百分比的指标编码（日均增幅列）
    private static final Set<String> PCT_INDICATORS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "branch_asset_avg_growth", "branch_saving_avg_growth",
        "branch_corp_dep_avg_growth", "branch_inst_dep_avg_growth"
    )));

    // 分类边界 (startIndex inclusive, endIndex exclusive)
    private static final int[] CAT_BOUNDARIES = {0, 2, 12, 19, 22}; // revenue, indicator, customer, operation

    private static int safeCompare(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }

    // 基础数据指标编码→中文名称（BRANCH_INDICATOR_MAP 的逆映射，用于导出时展示中文列名）
    private static final Map<String, String> BASE_INDICATOR_NAME_MAP = new LinkedHashMap<>();
    static {
        BASE_INDICATOR_NAME_MAP.put("interest_income", "利息净收入(万元)");
        BASE_INDICATOR_NAME_MAP.put("fee_income", "手续费净收入(万元)");
        BASE_INDICATOR_NAME_MAP.put("total_asset_balance", "全量个人金融资产 日均余额(万元)");
        BASE_INDICATOR_NAME_MAP.put("total_asset_growth", "全量个人金融资产 日均增量(万元)");
        BASE_INDICATOR_NAME_MAP.put("saving_balance", "储蓄存款 日均余额(万元)");
        BASE_INDICATOR_NAME_MAP.put("saving_growth", "储蓄存款 日均增量(万元)");
        BASE_INDICATOR_NAME_MAP.put("corp_dep_balance", "公司客户存款 日均余额(万元)");
        BASE_INDICATOR_NAME_MAP.put("corp_dep_growth", "公司客户存款 日均增量(万元)");
        BASE_INDICATOR_NAME_MAP.put("inst_dep_balance", "机构客户存款 日均余额(万元)");
        BASE_INDICATOR_NAME_MAP.put("inst_dep_growth", "机构客户存款 日均增量(万元)");
        BASE_INDICATOR_NAME_MAP.put("inclusive_loan_amount", "普惠贷款 营销额(万元)");
        BASE_INDICATOR_NAME_MAP.put("personal_loan_amount", "个人贷款 发放额(万元)");
        BASE_INDICATOR_NAME_MAP.put("pcust_t1", "日均0元（不含）-20万元（不含客户数）(单位：户)");
        BASE_INDICATOR_NAME_MAP.put("pcust_t2", "日均20万元（含）-600万元（不含客户数）(单位：户)");
        BASE_INDICATOR_NAME_MAP.put("pcust_t3", "日均大于等于600万（含）客户数(单位：户)");
        BASE_INDICATOR_NAME_MAP.put("ccust_h", "头部、中部对公客户数 日均资产50万元（含）以上（单位：户）");
        BASE_INDICATOR_NAME_MAP.put("ccust_l", "底尾部部对公客户数 日均资产50万元（不含）以下（单位：户）");
        BASE_INDICATOR_NAME_MAP.put("icust_h", "日均资产1万元（不含）以上机构客户数（单位：户）");
        BASE_INDICATOR_NAME_MAP.put("icust_l", "日均资产1万元（含）以下机构客户数（单位：户）");
        BASE_INDICATOR_NAME_MAP.put("inclusive_cust_total", "总量（单位：户）");
        BASE_INDICATOR_NAME_MAP.put("counter_txn", "柜台日均交易笔数");
        BASE_INDICATOR_NAME_MAP.put("terminal_txn", "自助终端日均交易笔数");
        BASE_INDICATOR_NAME_MAP.put("atm_txn", "附行式、网点自助ATM日均交易笔数");
    }

    // 基础数据Sheet Row3 简短指标名称（不带子分类前缀，匹配参考格式）
    private static final Map<String, String> BASE_INDICATOR_SHORT_NAMES = new LinkedHashMap<>();
    static {
        BASE_INDICATOR_SHORT_NAMES.put("interest_income", "利息净收入(万元)");
        BASE_INDICATOR_SHORT_NAMES.put("fee_income", "手续费净收入(万元)");
        BASE_INDICATOR_SHORT_NAMES.put("total_asset_balance", "日均余额(万元)");
        BASE_INDICATOR_SHORT_NAMES.put("total_asset_growth", "日均增量(万元)");
        BASE_INDICATOR_SHORT_NAMES.put("saving_balance", "日均余额(万元)");
        BASE_INDICATOR_SHORT_NAMES.put("saving_growth", "日均增量(万元)");
        BASE_INDICATOR_SHORT_NAMES.put("corp_dep_balance", "日均余额(万元)");
        BASE_INDICATOR_SHORT_NAMES.put("corp_dep_growth", "日均增量(万元)");
        BASE_INDICATOR_SHORT_NAMES.put("inst_dep_balance", "日均余额(万元)");
        BASE_INDICATOR_SHORT_NAMES.put("inst_dep_growth", "日均增量(万元)");
        BASE_INDICATOR_SHORT_NAMES.put("inclusive_loan_amount", "营销额(万元)");
        BASE_INDICATOR_SHORT_NAMES.put("personal_loan_amount", "发放额(万元)");
        BASE_INDICATOR_SHORT_NAMES.put("pcust_t1", "日均0元（不含）-20万元（不含客户数）(单位：户)");
        BASE_INDICATOR_SHORT_NAMES.put("pcust_t2", "日均20万元（含）-600万元（不含客户数）(单位：户)");
        BASE_INDICATOR_SHORT_NAMES.put("pcust_t3", "日均大于等于600万（含）客户数(单位：户)");
        BASE_INDICATOR_SHORT_NAMES.put("ccust_h", "头部、中部对公客户数 日均资产50万元（含）以上（单位：户）");
        BASE_INDICATOR_SHORT_NAMES.put("ccust_l", "底尾部部对公客户数 日均资产50万元（不含）以下（单位：户）");
        BASE_INDICATOR_SHORT_NAMES.put("icust_h", "日均资产1万元（不含）以上机构客户数（单位：户）");
        BASE_INDICATOR_SHORT_NAMES.put("icust_l", "日均资产1万元（含）以下机构客户数（单位：户）");
        BASE_INDICATOR_SHORT_NAMES.put("inclusive_cust_total", "总量（单位：户）");
        // counter_txn/terminal_txn/atm_txn short names not used—these are displayed via Row1-Row2 merge
    }

    /**
     * 导出网点组合数据：基础数据 + 数据计算表 + 归一化处理（含TOPSIS得分）
     * dataYear 为终点年份，往前取三年（含终点年）
     */
    public void exportBranchCombined(String city, Integer dataYear, OutputStream outputStream) throws IOException {
        List<JwBranchInfo> branches;
        try {
            branches = branchInfoMapper.selectByCity(city);
        } catch (Exception e) {
            log.error("获取网点信息失败 city={}", city, e);
            throw e;
        }
        if (branches.isEmpty()) {
            XSSFWorkbook emptyWb = new XSSFWorkbook();
            try { emptyWb.createSheet("基础数据"); emptyWb.write(outputStream); }
            finally { emptyWb.close(); }
            return;
        }

        // 从配置表动态加载 branch 类型的叶子指标，按层级排序
        List<JwIndicatorConfig> branchLeaves = indicatorConfigMapper.selectLeavesByType("branch");
        // 构建 code→config 映射用于查 parent chain
        Map<String, JwIndicatorConfig> allConfigMap = new LinkedHashMap<>();
        for (JwIndicatorConfig cfg : indicatorConfigMapper.selectJwIndicatorConfigList(new JwIndicatorConfig())) {
            if (cfg.getIndicatorCode() != null) allConfigMap.put(cfg.getIndicatorCode(), cfg);
        }
        // 按 (祖父名, 父名, sort_order) 排序
        branchLeaves.sort((a, b) -> {
            String ga = getRootName(a, allConfigMap);
            String gb = getRootName(b, allConfigMap);
            if (ga == null) ga = "";
            if (gb == null) gb = "";
            int cmp = ga.compareTo(gb);
            if (cmp != 0) return cmp;
            String pa = a.getParentCode() != null ? allConfigMap.getOrDefault(a.getParentCode(),
                new JwIndicatorConfig()).getIndicatorName() : "";
            String pb = b.getParentCode() != null ? allConfigMap.getOrDefault(b.getParentCode(),
                new JwIndicatorConfig()).getIndicatorName() : "";
            if (pa == null) pa = "";
            if (pb == null) pb = "";
            cmp = pa.compareTo(pb);
            if (cmp != 0) return cmp;
            Integer sa = a.getSortOrder() != null ? a.getSortOrder() : 0;
            Integer sb = b.getSortOrder() != null ? b.getSortOrder() : 0;
            return sa.compareTo(sb);
        });
        List<String> calcCodeList = new ArrayList<>();
        for (JwIndicatorConfig cfg : branchLeaves) {
            if (cfg.getIndicatorCode() != null) calcCodeList.add(cfg.getIndicatorCode());
        }

        int startYear = dataYear != null ? dataYear - 2 : 2023;
        int endYear = dataYear != null ? dataYear : 2025;

        List<JwBranchIndicator> baseIndicators;
        List<JwBranchIndicator> calcIndicators;
        List<JwBranchIndicator> normIndicators;
        List<JwBranchSummary> summaries;
        List<JwBranchScore> scores;
        try {
            baseIndicators = branchIndicatorMapper.selectByCityAndSheetType(city, "基础数据");
            calcIndicators = branchIndicatorMapper.selectByCityAndYearRange(city, startYear, endYear, "数据计算表");
            normIndicators = branchIndicatorMapper.selectByCityAndYearRange(city, startYear, endYear, "数据计算表归一化");
            summaries = branchSummaryMapper.selectByCityAndYearRange(city, startYear, endYear);
            scores = branchScoreMapper.selectByCityAndYearRange(city, startYear, endYear);
            log.info("网点导出数据加载完成({}-{}): baseIndicators={}, calc={}, norm={}, summaries={}, scores={}",
                    startYear, endYear,
                    baseIndicators != null ? baseIndicators.size() : 0,
                    calcIndicators != null ? calcIndicators.size() : 0,
                    normIndicators != null ? normIndicators.size() : 0,
                    summaries != null ? summaries.size() : 0,
                    scores != null ? scores.size() : 0);
        } catch (Exception e) {
            log.error("获取网点指标数据失败 city={}, year={}-{}", city, startYear, endYear, e);
            throw e;
        }

        // 预加载所有可能用到的指标名称（含基础数据指标）
        Set<String> allCodes = new LinkedHashSet<>();
        if (baseIndicators != null) baseIndicators.forEach(i -> allCodes.add(i.getIndicatorCode()));
        if (calcIndicators != null) calcIndicators.forEach(i -> allCodes.add(i.getIndicatorCode()));
        if (normIndicators != null) normIndicators.forEach(i -> allCodes.add(i.getIndicatorCode()));
        allCodes.addAll(calcCodeList);
        Map<String, String> nameMap = new LinkedHashMap<>();
        for (String code : allCodes) {
            JwIndicatorConfig cfg = indicatorConfigMapper.selectByCode(code);
            if (cfg != null) {
                nameMap.put(code, cfg.getIndicatorName());
            } else {
                // 基础数据指标可能不在 config 表中，用硬编码映射兜底
                nameMap.put(code, BASE_INDICATOR_NAME_MAP.getOrDefault(code, code));
            }
        }

        Map<String, JwBranchSummary> summaryMap = summaries.stream()
                .collect(Collectors.toMap(JwBranchSummary::getIndicatorCode, s -> s, (a, b) -> a));
        // scores by branchId -> category -> score
        Map<Long, Map<String, JwBranchScore>> scoreMap = new LinkedHashMap<>();
        for (JwBranchScore s : scores) {
            scoreMap.computeIfAbsent(s.getBranchId(), k -> new LinkedHashMap<>()).put(s.getScoreCategory(), s);
        }

        XSSFWorkbook workbook = new XSSFWorkbook();
        try {
            // Sheet 1: 基础数据（从数据中动态提取指标列）
            writeBranchBaseSheet(workbook.createSheet("基础数据"), branches, baseIndicators, nameMap);

            // Sheet 2-4: 数据计算表（每年一个sheet）
            for (int yr = startYear; yr <= endYear; yr++) {
                final int year = yr;
                String sheetName = "数据计算表_" + year;
                List<JwBranchIndicator> yrCalc = calcIndicators != null
                    ? calcIndicators.stream().filter(i -> i.getDataYear() != null && i.getDataYear() == year).collect(Collectors.toList())
                    : new ArrayList<>();
                Map<String, JwBranchSummary> yrSummaryMap = summaries != null
                    ? summaries.stream().filter(s -> s.getDataYear() != null && s.getDataYear() == year)
                        .collect(Collectors.toMap(JwBranchSummary::getIndicatorCode, s -> s, (a, b) -> a))
                    : new HashMap<>();
                writeBranchCalcSheet(workbook.createSheet(sheetName), branches, yrCalc,
                        yrSummaryMap, nameMap, year, false, null, calcCodeList);
            }

            // Sheet 5-7: 归一化处理（每年一个sheet）
            for (int yr = startYear; yr <= endYear; yr++) {
                final int year = yr;
                String sheetName = "归一化处理_" + year;
                List<JwBranchIndicator> yrNorm = normIndicators != null
                    ? normIndicators.stream().filter(i -> i.getDataYear() != null && i.getDataYear() == year).collect(Collectors.toList())
                    : new ArrayList<>();
                Map<String, JwBranchSummary> yrSummaryMap = summaries != null
                    ? summaries.stream().filter(s -> s.getDataYear() != null && s.getDataYear() == year)
                        .collect(Collectors.toMap(JwBranchSummary::getIndicatorCode, s -> s, (a, b) -> a))
                    : new HashMap<>();
                Map<Long, Map<String, JwBranchScore>> yrScoreMap = scores != null
                    ? scores.stream().filter(s -> s.getDataYear() != null && s.getDataYear() == year)
                        .collect(Collectors.groupingBy(JwBranchScore::getBranchId,
                            Collectors.toMap(JwBranchScore::getScoreCategory, s -> s, (a, b) -> a)))
                    : new HashMap<>();
                writeBranchCalcSheet(workbook.createSheet(sheetName), branches, yrNorm,
                        yrSummaryMap, nameMap, year, true, yrScoreMap, calcCodeList);
            }

            workbook.write(outputStream);
        } finally {
            workbook.close();
        }
    }

    /**
     * 写入基础数据Sheet
     * 参考格式: Row0=分类(合并) Row1=子分类 Row2=列名 Row3=细分/年份 Row4+=数据
     * 27个固定列(A-AA) + 各年份指标列(AB-CR)，共96列
     * 业务运营类的3个指标（柜台/自助终端/ATM），子分类即指标名，Row1跨Row2合并
     */
    private void writeBranchBaseSheet(Sheet sheet,
                                       List<JwBranchInfo> branches,
                                       List<JwBranchIndicator> indicators,
                                       Map<String, String> nameMap) {
        CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());
        CellStyle dataStyle = createDataStyle(sheet.getWorkbook());

        // ===== 27个固定列定义 =====
        int FIXED_COLS = 27; // A-AA
        String[] fRow2 = {
            "一级支行", "二级支行", "网点号", "行政区", "街道", "具体地址", "经度", "纬度",
            null, null, null, null, null,   // 8-12: 整体情况
            null, null, null, null,          // 13-16: 网点行长任职情况
            null, null,                      // 17-18: 营业面积（不含公摊）
            "现金柜台个数", "非现金柜台个数", "个人客户经理工位个数",
            null, null,                      // 22-23: 产权状态
            "最近一次装修时间", "网点业态分类", "迁并情况"
        };
        String[] fRow3 = {
            "", "", "", "", "", "", "", "",   // 0-7: 合并到Row2
            "总人数", "个人客户经理人数", "对公客户经理人数（专职）", "客服经理（柜面）人数", "客服经理（厅堂）人数",
            "网点行长姓名", "在本网点任职时间", "完整履历信息（人力资源系统内格式）", "本网点历任行长",
            "总面积", "其中：若为多层，请填写除首层以外面积",
            "", "", "",                     // 19-21: 合并到Row2
            "自有/租赁", "租赁到期时间",
            "", "", ""                      // 24-26: 合并到Row2
        };
        String[] fRow1Labels = {"网点名称", "地理位置", "人员信息", "面积及功能分区", "其他"};
        int[][] fRow1Ranges = {{0, 2}, {3, 7}, {8, 16}, {17, 21}, {22, 26}};
        String[] fRow2MergeLabels = {"整体情况", "网点行长任职情况", "营业面积（不含公摊）", "产权状态"};
        int[][] fRow2MergeRanges = {{8, 12}, {13, 16}, {17, 18}, {22, 23}};
        // 逐列Row2-Row3合并（替代之前的大块合并）
        int[][] fRow23Merges = {
            {0, 0}, {1, 1}, {2, 2}, {3, 3}, {4, 4}, {5, 5}, {6, 6}, {7, 7},
            {19, 19}, {20, 20}, {21, 21},
            {24, 24}, {25, 25}, {26, 26}
        };

        // ===== 指标列定义（从数据中动态提取，再从配置表查分类层级） =====
        // 从实际数据中收集所有唯一的 indicator_code
        Set<String> uniqueCodes = new LinkedHashSet<>();
        if (indicators != null) indicators.forEach(i -> {
            if (i.getIndicatorCode() != null) uniqueCodes.add(i.getIndicatorCode());
        });
        // 批量查配置表获取名称和层级
        Map<String, JwIndicatorConfig> dataCodeConfigMap = new LinkedHashMap<>();
        for (String code : uniqueCodes) {
            JwIndicatorConfig cfg = indicatorConfigMapper.selectByCode(code);
            if (cfg != null) dataCodeConfigMap.put(code, cfg);
        }
        // 构建 indicatorDefs: {code, row0Cat(大类), row1SubCat(子类), null}
        List<String[]> indicatorDefsList = new ArrayList<>();
        for (String code : uniqueCodes) {
            JwIndicatorConfig cfg = dataCodeConfigMap.get(code);
            String row1SubCat = code;
            String row0Cat = code;
            if (cfg != null) {
                row1SubCat = cfg.getIndicatorName() != null ? cfg.getIndicatorName() : code;
                if (cfg.getParentCode() != null && !cfg.getParentCode().isEmpty()) {
                    JwIndicatorConfig parent = indicatorConfigMapper.selectByCode(cfg.getParentCode());
                    if (parent != null) {
                        row1SubCat = parent.getIndicatorName() != null ? parent.getIndicatorName() : row1SubCat;
                        if (parent.getParentCode() != null && !parent.getParentCode().isEmpty()) {
                            JwIndicatorConfig grandparent = indicatorConfigMapper.selectByCode(parent.getParentCode());
                            if (grandparent != null && grandparent.getIndicatorName() != null) {
                                row0Cat = grandparent.getIndicatorName();
                            } else {
                                row0Cat = row1SubCat;
                            }
                        } else {
                            row0Cat = row1SubCat;
                        }
                    }
                }
            } else {
                row1SubCat = BASE_INDICATOR_NAME_MAP.getOrDefault(code, code);
                row0Cat = row1SubCat;
            }
            indicatorDefsList.add(new String[]{code, row0Cat, row1SubCat, null});
        }
        String[][] indicatorDefs = indicatorDefsList.toArray(new String[0][]);

        // 按 row0Cat(大类) -> row1SubCat(子类) 排序，使同类指标相邻
        Arrays.sort(indicatorDefs, (a, b) -> {
            int cmp0 = a[0].compareTo(b[0]); // fallback by code
            int cmpCat = safeCompare(a[1], b[1]); // row0Cat
            if (cmpCat != 0) return cmpCat;
            int cmpSub = safeCompare(a[2], b[2]); // row1SubCat
            if (cmpSub != 0) return cmpSub;
            return cmp0;
        });

        // ===== 提取数据 =====
        Set<Integer> years = new TreeSet<>();
        Map<Long, Map<String, Map<Integer, Double>>> branchData = new LinkedHashMap<>();
        if (indicators != null) {
            for (JwBranchIndicator ind : indicators) {
                years.add(ind.getDataYear());
                branchData.computeIfAbsent(ind.getBranchId(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(ind.getIndicatorCode(), k -> new LinkedHashMap<>())
                        .put(ind.getDataYear(), ind.getIndicatorValue());
            }
        }
        List<Integer> yearList = new ArrayList<>(years);
        int yearsPerIndicator = yearList.size();
        int indicatorCols = indicatorDefs.length * yearsPerIndicator;
        int totalCols = FIXED_COLS + indicatorCols;

        // ===== Row0: 分类行 =====
        Row row0 = sheet.createRow(0);
        for (int i = 0; i < totalCols; i++) {
            Cell c = row0.createCell(i);
            c.setCellStyle(headerStyle);
        }
        row0.getCell(0).setCellValue("基本信息");
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, FIXED_COLS - 1));

        int col = FIXED_COLS;
        String prevCat = null;
        int catStart = FIXED_COLS;
        for (String[] idef : indicatorDefs) {
            String cat = idef[1];
            if (!cat.equals(prevCat)) {
                if (prevCat != null && col - 1 >= catStart) {
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, catStart, col - 1));
                    row0.getCell(catStart).setCellValue(prevCat);
                }
                catStart = col;
                prevCat = cat;
            }
            col += yearsPerIndicator;
        }
        if (prevCat != null && col - 1 >= catStart) {
            sheet.addMergedRegion(new CellRangeAddress(0, 0, catStart, col - 1));
            row0.getCell(catStart).setCellValue(prevCat);
        }

        // ===== Row1: 子分类行 =====
        Row row1 = sheet.createRow(1);
        for (int i = 0; i < totalCols; i++) {
            Cell c = row1.createCell(i);
            c.setCellStyle(headerStyle);
        }
        for (int i = 0; i < fRow1Ranges.length; i++) {
            int[] rng = fRow1Ranges[i];
            if (rng[1] >= rng[0]) {
                sheet.addMergedRegion(new CellRangeAddress(1, 1, rng[0], rng[1]));
                row1.getCell(rng[0]).setCellValue(fRow1Labels[i]);
            }
        }

        // 指标子分类（业务运营类跨Row1-Row2合并）
        col = FIXED_COLS;
        String prevSubCat = null;
        int subStart = FIXED_COLS;
        for (String[] idef : indicatorDefs) {
            String sub = idef[2];
            if (!sub.equals(prevSubCat)) {
                if (prevSubCat != null && !prevSubCat.isEmpty() && col - 1 >= subStart) {
                    sheet.addMergedRegion(new CellRangeAddress(1, 1, subStart, col - 1));
                    row1.getCell(subStart).setCellValue(prevSubCat);
                }
                subStart = col;
                prevSubCat = sub;
            }
            col += yearsPerIndicator;
        }
        if (prevSubCat != null && !prevSubCat.isEmpty() && col - 1 >= subStart) {
            sheet.addMergedRegion(new CellRangeAddress(1, 1, subStart, col - 1));
            row1.getCell(subStart).setCellValue(prevSubCat);
        }

        // ===== Row2: 列名行 =====
        Row row2 = sheet.createRow(2);
        for (int i = 0; i < totalCols; i++) {
            Cell c = row2.createCell(i);
            c.setCellStyle(headerStyle);
        }
        for (int i = 0; i < fRow2MergeRanges.length; i++) {
            int[] rng = fRow2MergeRanges[i];
            if (rng[1] >= rng[0]) {
                sheet.addMergedRegion(new CellRangeAddress(2, 2, rng[0], rng[1]));
                row2.getCell(rng[0]).setCellValue(fRow2MergeLabels[i]);
            }
        }
        for (int i = 0; i < FIXED_COLS; i++) {
            if (fRow2[i] != null) row2.getCell(i).setCellValue(fRow2[i]);
        }

        // 指标列名：每个指标跨yearsPerIndicator列合并
        // 业务运营类(Row2作为Row1子分类合并区域一部分，不显示独立标签)
        col = FIXED_COLS;
        for (String[] idef : indicatorDefs) {
            String code = idef[0];
            boolean isOp = "op".equals(idef[3]);
            int endCol = col + yearsPerIndicator - 1;
            if (endCol > col && !isOp) {
                sheet.addMergedRegion(new CellRangeAddress(2, 2, col, endCol));
            }
            if (!isOp) {
                String displayName = BASE_INDICATOR_SHORT_NAMES.get(code);
                if (displayName == null) {
                    JwIndicatorConfig cfg = dataCodeConfigMap.get(code);
                    displayName = (cfg != null && cfg.getIndicatorName() != null) ? cfg.getIndicatorName() : code;
                }
                row2.getCell(col).setCellValue(displayName);
            }
            col += yearsPerIndicator;
        }

        // ===== Row3: 细分/年份行 =====
        Row row3 = sheet.createRow(3);
        for (int i = 0; i < totalCols; i++) {
            Cell c = row3.createCell(i);
            c.setCellStyle(headerStyle);
        }
        // 固定列Row2-Row3合并
        for (int[] mg : fRow23Merges) {
            if (mg[1] >= mg[0]) {
                sheet.addMergedRegion(new CellRangeAddress(2, 3, mg[0], mg[1]));
            }
        }
        for (int i = 0; i < FIXED_COLS; i++) {
            if (fRow3[i] != null && !fRow3[i].isEmpty()) {
                row3.getCell(i).setCellValue(fRow3[i]);
            }
        }
        // 年份行
        col = FIXED_COLS;
        for (String[] idef : indicatorDefs) {
            for (Integer y : yearList) {
                row3.getCell(col++).setCellValue(String.valueOf(y));
            }
        }

        // ===== Row4+: 数据行 =====
        int rowNum = 4;
        Set<String> headerLike = new HashSet<>(Arrays.asList(
            "一级支行", "二级支行", "机构号", "网点号", "行政区", "街道",
            "具体地址", "详细地址", "经度", "纬度"));
        for (JwBranchInfo branch : branches) {
            String p = branch.getPrimaryBranch();
            if (p == null) continue;
            String trimmed = p.trim();
            if (trimmed.isEmpty() || headerLike.contains(trimmed)) continue;
            // Also skip rows with header-like text in districtName or street
            String dn = branch.getDistrictName();
            if (dn != null && headerLike.contains(dn.trim())) continue;
            String st = branch.getStreet();
            if (st != null && headerLike.contains(st.trim())) continue;
            String ad = branch.getAddress();
            if (ad != null && (ad.trim().equals("具体地址") || ad.trim().equals("详细地址"))) continue;

            Row row = sheet.createRow(rowNum++);
            for (int i = 0; i < totalCols; i++) {
                Cell c = row.createCell(i);
                c.setCellStyle(dataStyle);
            }
            // 27个固定列值
            setCellValue(row.getCell(0), branch.getPrimaryBranch(), dataStyle);
            setCellValue(row.getCell(1), branch.getSecondaryBranch(), dataStyle);
            setCellValue(row.getCell(2), branch.getBranchCode(), dataStyle);
            setCellValue(row.getCell(3), branch.getDistrictName(), dataStyle);
            setCellValue(row.getCell(4), branch.getStreet(), dataStyle);
            setCellValue(row.getCell(5), branch.getAddress(), dataStyle);
            row.getCell(6).setCellValue(branch.getLongitude() != null ? branch.getLongitude() : 0);
            row.getCell(7).setCellValue(branch.getLatitude() != null ? branch.getLatitude() : 0);
            row.getCell(8).setCellValue(branch.getTotalStaff() != null ? branch.getTotalStaff().doubleValue() : 0);
            row.getCell(9).setCellValue(branch.getPersonalManager() != null ? branch.getPersonalManager().doubleValue() : 0);
            row.getCell(10).setCellValue(branch.getCorporateManager() != null ? branch.getCorporateManager().doubleValue() : 0);
            row.getCell(11).setCellValue(branch.getCounterStaff() != null ? branch.getCounterStaff().doubleValue() : 0);
            row.getCell(12).setCellValue(branch.getLobbyStaff() != null ? branch.getLobbyStaff().doubleValue() : 0);
            setCellValue(row.getCell(13), branch.getBranchManager(), dataStyle);
            setCellValue(row.getCell(14), branch.getManagerTenure(), dataStyle);
            setCellValue(row.getCell(15), branch.getManagerResume(), dataStyle);
            setCellValue(row.getCell(16), branch.getManagerHistory(), dataStyle);
            row.getCell(17).setCellValue(branch.getTotalArea() != null ? branch.getTotalArea() : 0);
            row.getCell(18).setCellValue(branch.getOtherFloorArea() != null ? branch.getOtherFloorArea() : 0);
            row.getCell(19).setCellValue(branch.getCashCounter() != null ? branch.getCashCounter().doubleValue() : 0);
            row.getCell(20).setCellValue(branch.getNonCashCounter() != null ? branch.getNonCashCounter().doubleValue() : 0);
            row.getCell(21).setCellValue(branch.getManagerSeat() != null ? branch.getManagerSeat().doubleValue() : 0);
            setCellValue(row.getCell(22), branch.getPropertyRight(), dataStyle);
            setCellValue(row.getCell(23), branch.getLeaseExpire(), dataStyle);
            setCellValue(row.getCell(24), branch.getLastRenovation(), dataStyle);
            setCellValue(row.getCell(25), branch.getBranchType(), dataStyle);
            setCellValue(row.getCell(26), branch.getRelocation(), dataStyle);

            // 指标数据
            Map<String, Map<Integer, Double>> bd = branchData.get(branch.getBranchId());
            col = FIXED_COLS;
            for (String[] idef : indicatorDefs) {
                Map<Integer, Double> yearVals = bd != null ? bd.get(idef[0]) : null;
                for (Integer y : yearList) {
                    Cell c = row.getCell(col++);
                    if (yearVals != null) {
                        Double yv = yearVals.get(y);
                        if (yv != null) c.setCellValue(yv);
                    }
                }
            }
        }

        // 统一列宽（最大18字符，避免某些列过宽）
        for (int i = 0; i < totalCols; i++) {
            sheet.autoSizeColumn(i);
            int w = sheet.getColumnWidth(i);
            if (w > 18 * 256) sheet.setColumnWidth(i, 18 * 256);
            if (w < 10 * 256) sheet.setColumnWidth(i, 10 * 256);
        }
    }

    /** 设置字符串单元格值，null时留空 */
    private void setCellValue(Cell cell, String value, CellStyle style) {
        if (value != null) cell.setCellValue(value);
    }

    // Map indicator code to category name (using dynamic code list)
    private String catForIndicatorCode(String code, List<String> codeList) {
        int idx = codeList != null ? codeList.indexOf(code) : -1;
        if (idx >= 0) {
            // 从配置表 parent chain 获取分类
            JwIndicatorConfig cfg = indicatorConfigMapper.selectByCode(code);
            if (cfg != null) {
                JwIndicatorConfig parent = null;
                if (cfg.getParentCode() != null && !cfg.getParentCode().isEmpty()) {
                    parent = indicatorConfigMapper.selectByCode(cfg.getParentCode());
                }
                if (parent != null && parent.getParentCode() != null && !parent.getParentCode().isEmpty()) {
                    JwIndicatorConfig grandparent = indicatorConfigMapper.selectByCode(parent.getParentCode());
                    if (grandparent != null) return grandparent.getIndicatorName();
                }
                if (parent != null) return parent.getIndicatorName();
                // 基础数据兜底
            }
            // 按 code 前缀映射兜底
            if (code.equals("interest_income") || code.equals("fee_income")) return "经营情况";
            if (code.startsWith("pcust_") || code.startsWith("ccust_") || code.startsWith("icust_")
                || code.equals("inclusive_cust_total")) return "客户发展";
        }
        // 按 code 前缀兜底
        if (code.equals("interest_income") || code.equals("fee_income")) return "经营情况";
        if (code.equals("counter_txn") || code.equals("terminal_txn") || code.equals("atm_txn")) return "业务运营";
        if (code.startsWith("pcust_") || code.startsWith("ccust_") || code.startsWith("icust_")
            || code.equals("inclusive_cust_total")) return "客户发展";
        return "业绩表现";
    }

    /**
     * 写入数据计算表Sheet（含多级表头、实际权值/MAX/MIN行）
     * 参考格式：Row0=分类(含跨行合并), Row1=子分类, Row2=指标名称, Row3=年份,
     * Row4=实际权值, Row5=MAX, Row6=MIN, Row7+=数据
     * 归一化sheet右侧追加TOPSIS五类得分(4列/类: D+, D-, 得分, 排名)
     */
    private void writeBranchCalcSheet(Sheet sheet,
                                       List<JwBranchInfo> branches,
                                       List<JwBranchIndicator> indicators,
                                       Map<String, JwBranchSummary> summaryMap,
                                       Map<String, String> nameMap,
                                       Integer dataYear,
                                       boolean isNormalized,
                                       Map<Long, Map<String, JwBranchScore>> scoreMap,
                                       List<String> calcCodeList) {
        CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());
        CellStyle weightStyle = createWeightStyle(sheet.getWorkbook());
        CellStyle maxMinStyle = createMaxMinStyle(sheet.getWorkbook());
        CellStyle dataStyle = createDataStyle(sheet.getWorkbook());
        CellStyle pctStyle = createPctStyle(sheet.getWorkbook());

        int fixedCols = 5;
        String[] fixedHeaders = {"一级支行", "二级支行", "网点号", "经度", "纬度"};

        // 固定列分组：A-C=网点信息(跨行0-1), D-E=基础信息(Row0)/地理位置(Row1)
        int[][] fixedR0 = {{0, 1, 0, 2}, {0, 0, 3, 4}};
        String[] fixedR0Labels = {"网点信息", "基础信息"};
        int[][] fixedR1 = {{1, 1, 3, 4}};
        String[] fixedR1Labels = {"地理位置"};

        List<String> codeList = new ArrayList<>(calcCodeList != null ? calcCodeList : new ArrayList<>());

        // 构建 branchId -> code -> value 映射
        Map<Long, Map<String, Double>> branchValueMap = new LinkedHashMap<>();
        if (indicators != null) {
            for (JwBranchIndicator ind : indicators) {
                branchValueMap.computeIfAbsent(ind.getBranchId(), k -> new LinkedHashMap<>())
                        .put(ind.getIndicatorCode(), ind.getIndicatorValue());
            }
        }

        // 指标分类布局: 从配置表动态获取
        // 按(大分类, 子分类)分组
        Map<String, Map<String, List<String>>> dynamicCats = new LinkedHashMap<>();
        for (String code : codeList) {
            JwIndicatorConfig cfg = indicatorConfigMapper.selectByCode(code);
            String cat = "其他";
            String subCat = code;
            if (cfg != null) {
                subCat = cfg.getIndicatorName() != null ? cfg.getIndicatorName() : code;
                if (cfg.getParentCode() != null && !cfg.getParentCode().isEmpty()) {
                    JwIndicatorConfig parent = indicatorConfigMapper.selectByCode(cfg.getParentCode());
                    if (parent != null) {
                        subCat = parent.getIndicatorName() != null ? parent.getIndicatorName() : subCat;
                        if (parent.getParentCode() != null && !parent.getParentCode().isEmpty()) {
                            JwIndicatorConfig gp = indicatorConfigMapper.selectByCode(parent.getParentCode());
                            if (gp != null) cat = gp.getIndicatorName();
                            else cat = parent.getIndicatorName();
                        } else {
                            cat = parent.getIndicatorName();
                        }
                    }
                }
            }
            dynamicCats.computeIfAbsent(cat, k -> new LinkedHashMap<>())
                .computeIfAbsent(subCat, k -> new ArrayList<>()).add(code);
        }

        // 构建分类/子分类布局
        List<String> catNames = new ArrayList<>(dynamicCats.keySet());
        List<int[]> subRanges = new ArrayList<>();
        List<String> subNames = new ArrayList<>();
        int idx = 0;
        for (Map.Entry<String, Map<String, List<String>>> catEntry : dynamicCats.entrySet()) {
            for (Map.Entry<String, List<String>> subEntry : catEntry.getValue().entrySet()) {
                int subCount = subEntry.getValue().size();
                subRanges.add(new int[]{idx, idx + subCount});
                subNames.add(subEntry.getKey());
                idx += subCount;
            }
        }

        // TOPSIS得分区(仅归一化sheet)
        String[] scoreCats = {"overall", "revenue", "indicator", "customer", "operation"};
        String[] scoreLabels = {"总分", "营收", "指标", "客户", "运营"};
        String[] distLabels = {"正理想解欧式距离", "负理想解欧式距离", "得分", "排名"};
        int colsPerCat = 4;
        int scoreTotal = isNormalized ? scoreCats.length * colsPerCat : 0;

        int indicatorCount = codeList.size();
        int totalCols = fixedCols + indicatorCount + scoreTotal;
        int rowNum = 0;

        // ===== Row0: 分类名称（合并单元格，部分跨行） =====
        Row row0 = sheet.createRow(rowNum++);

        // 固定列区域
        for (int i = 0; i < fixedCols; i++) row0.createCell(i).setCellStyle(headerStyle);
        row0.getCell(0).setCellValue("网点名称");
        row0.getCell(3).setCellValue("基础信息");
        sheet.addMergedRegion(new CellRangeAddress(0, 1, 0, 2));  // 网点信息 A-C
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 3, 4));  // 基础信息 D-E

        // 指标分类区域（动态布局）
        int r0Idx = 0;
        int catIdx = 0;
        for (Map.Entry<String, Map<String, List<String>>> catEntry : dynamicCats.entrySet()) {
            int catStart = fixedCols + r0Idx;
            int catEnd = catStart;
            for (Map.Entry<String, List<String>> subEntry : catEntry.getValue().entrySet()) {
                catEnd += subEntry.getValue().size();
            }
            catEnd -= 1;
            for (int i = catStart; i <= catEnd; i++) {
                Cell c = row0.createCell(i);
                c.setCellValue(i == catStart ? catEntry.getKey() : "");
                c.setCellStyle(headerStyle);
            }
            if (catEnd >= catStart) {
                sheet.addMergedRegion(new CellRangeAddress(0, 0, catStart, catEnd));
            }
            r0Idx = catEnd - fixedCols + 1;
            catIdx++;
        }

        // TOPSIS得分分类(Row0): 每个分类合并4列
        if (isNormalized) {
            int s0 = totalCols - scoreTotal;
            for (int si = 0; si < scoreCats.length; si++) {
                int s = s0 + si * colsPerCat;
                int e = s + colsPerCat - 1;
                for (int i = s; i <= e; i++) {
                    Cell c = row0.createCell(i);
                    c.setCellValue(i == s ? scoreLabels[si] : "");
                    c.setCellStyle(headerStyle);
                }
                sheet.addMergedRegion(new CellRangeAddress(0, 1, s, e));
            }
        }

        // ===== Row1: 子分类 =====
        Row row1 = sheet.createRow(rowNum++);

        // A-C: 网点信息跨行合并覆盖, 留空
        for (int i = 0; i < 3; i++) row1.createCell(i).setCellStyle(headerStyle);
        // D-E: 地理位置（确保合并区域的首单元格也设置样式）
        Cell cellD2 = row1.createCell(3);
        cellD2.setCellValue("地理位置");
        cellD2.setCellStyle(headerStyle);
        row1.createCell(4).setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 3, 4));

        // 指标子分类（动态）
        int r1Idx = 0;
        for (Map.Entry<String, Map<String, List<String>>> catEntry : dynamicCats.entrySet()) {
            for (Map.Entry<String, List<String>> subEntry : catEntry.getValue().entrySet()) {
                int start = fixedCols + r1Idx;
                int end = start + subEntry.getValue().size() - 1;
                for (int i = start; i <= end; i++) {
                    Cell c = row1.createCell(i);
                    c.setCellValue(i == start ? subEntry.getKey() : "");
                    c.setCellStyle(headerStyle);
                }
                if (end > start) {
                    sheet.addMergedRegion(new CellRangeAddress(1, 1, start, end));
                }
                r1Idx += subEntry.getValue().size();
            }
        }

        // 补全Row1空cell样式
        for (int i = 0; i < totalCols; i++) {
            if (row1.getCell(i) == null) row1.createCell(i).setCellStyle(headerStyle);
        }

        // ===== Row2: 指标名称 =====
        Row nameRow = sheet.createRow(rowNum++);
        for (int i = 0; i < fixedCols; i++) {
            Cell c = nameRow.createCell(i);
            c.setCellValue(fixedHeaders[i]);
            c.setCellStyle(headerStyle);
        }
        int colIdx = fixedCols;
        for (String code : codeList) {
            Cell c = nameRow.createCell(colIdx++);
            // 优先使用CALC_DISPLAY_NAME_MAP（含换行符，匹配参考格式）
            c.setCellValue(CALC_DISPLAY_NAME_MAP.getOrDefault(code,
                    nameMap.getOrDefault(code, code)));
            c.setCellStyle(headerStyle);
        }
        // TOPSIS得分列标签(Row2): 每个分类4列(正理想解欧式距离/负理想解欧式距离/得分/排名)
        if (isNormalized) {
            int s0 = totalCols - scoreTotal;
            for (int si = 0; si < scoreCats.length; si++) {
                for (int di = 0; di < colsPerCat; di++) {
                    Cell c = nameRow.createCell(s0 + si * colsPerCat + di);
                    c.setCellValue(distLabels[di]);
                    c.setCellStyle(headerStyle);
                }
            }
        }

        // ===== Row3: 年份 =====
        // 固定列Row2-Row3逐列合并（匹配参考格式 A3:A4, B3:B4, ...）
        for (int i = 0; i < fixedCols; i++) {
            sheet.addMergedRegion(new CellRangeAddress(2, 3, i, i));
        }
        Row yearRow = sheet.createRow(rowNum++);
        for (int i = 0; i < fixedCols; i++) {
            yearRow.createCell(i).setCellStyle(headerStyle);
        }
        colIdx = fixedCols;
        for (String ignored : codeList) {
            Cell c = yearRow.createCell(colIdx++);
            if (dataYear != null) c.setCellValue(String.valueOf(dataYear));
            c.setCellStyle(headerStyle);
        }
        for (int i = fixedCols + indicatorCount; i < totalCols; i++) {
            yearRow.createCell(i).setCellStyle(headerStyle);
        }

        // ===== Row4: 实际权重 =====
        Row weightRow = sheet.createRow(rowNum++);
        weightRow.createCell(0).setCellValue("实际权重");
        weightRow.getCell(0).setCellStyle(weightStyle);
        for (int i = 1; i < fixedCols; i++) weightRow.createCell(i).setCellStyle(maxMinStyle);
        colIdx = fixedCols;
        for (String code : codeList) {
            Cell c = weightRow.createCell(colIdx++);
            if (summaryMap != null) {
                JwBranchSummary s = summaryMap.get(code);
                if (s != null && s.getActualWeight() != null) c.setCellValue(s.getActualWeight());
            }
            c.setCellStyle(PCT_INDICATORS.contains(code) ? pctStyle : weightStyle);
        }
        for (int i = fixedCols + indicatorCount; i < totalCols; i++) {
            weightRow.createCell(i).setCellStyle(maxMinStyle);
        }

        // ===== Row5: MAX =====
        Row maxRow = sheet.createRow(rowNum++);
        maxRow.createCell(0).setCellValue("MAX");
        maxRow.getCell(0).setCellStyle(maxMinStyle);
        for (int i = 1; i < fixedCols; i++) maxRow.createCell(i).setCellStyle(maxMinStyle);
        colIdx = fixedCols;
        for (String code : codeList) {
            Cell c = maxRow.createCell(colIdx++);
            if (summaryMap != null) {
                JwBranchSummary s = summaryMap.get(code);
                if (s != null) {
                    Double val = isNormalized
                            ? (s.getMaxNorm() != null ? s.getMaxNorm() : 0)
                            : (s.getMaxValue() != null ? s.getMaxValue() : 0);
                    c.setCellValue(val);
                }
            }
            c.setCellStyle(PCT_INDICATORS.contains(code) ? pctStyle : maxMinStyle);
        }
        for (int i = fixedCols + indicatorCount; i < totalCols; i++) {
            maxRow.createCell(i).setCellStyle(maxMinStyle);
        }

        // ===== Row6: MIN =====
        Row minRow = sheet.createRow(rowNum++);
        minRow.createCell(0).setCellValue("MIN");
        minRow.getCell(0).setCellStyle(maxMinStyle);
        for (int i = 1; i < fixedCols; i++) minRow.createCell(i).setCellStyle(maxMinStyle);
        colIdx = fixedCols;
        for (String code : codeList) {
            Cell c = minRow.createCell(colIdx++);
            if (summaryMap != null) {
                JwBranchSummary s = summaryMap.get(code);
                if (s != null) {
                    Double val = isNormalized
                            ? (s.getMinNorm() != null ? s.getMinNorm() : 0)
                            : (s.getMinValue() != null ? s.getMinValue() : 0);
                    c.setCellValue(val);
                }
            }
            c.setCellStyle(PCT_INDICATORS.contains(code) ? pctStyle : maxMinStyle);
        }
        for (int i = fixedCols + indicatorCount; i < totalCols; i++) {
            minRow.createCell(i).setCellStyle(maxMinStyle);
        }

        // ===== Row7+: 数据行（跳过表头类垃圾行和空行） =====
        Set<String> headerLike = new HashSet<>(Arrays.asList("一级支行", "二级支行", "机构号"));
        for (JwBranchInfo branch : branches) {
            String p = branch.getPrimaryBranch();
            if (p != null) {
                String trimmed = p.trim();
                if (trimmed.isEmpty() || headerLike.contains(trimmed)) {
                    continue;
                }
            } else {
                continue;
            }
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(branch.getPrimaryBranch() != null ? branch.getPrimaryBranch() : "");
            row.getCell(0).setCellStyle(dataStyle);
            row.createCell(1).setCellValue(branch.getSecondaryBranch() != null ? branch.getSecondaryBranch() : "");
            row.getCell(1).setCellStyle(dataStyle);
            row.createCell(2).setCellValue(branch.getBranchCode() != null ? branch.getBranchCode() : "");
            row.getCell(2).setCellStyle(dataStyle);
            row.createCell(3).setCellValue(branch.getLongitude() != null ? branch.getLongitude() : 0);
            row.getCell(3).setCellStyle(dataStyle);
            row.createCell(4).setCellValue(branch.getLatitude() != null ? branch.getLatitude() : 0);
            row.getCell(4).setCellStyle(dataStyle);

            Map<String, Double> vals = branchValueMap.get(branch.getBranchId());
            colIdx = fixedCols;
            for (String code : codeList) {
                Cell c = row.createCell(colIdx++);
                if (vals != null) {
                    Double v = vals.get(code);
                    if (v != null) c.setCellValue(v);
                }
                c.setCellStyle(PCT_INDICATORS.contains(code) ? pctStyle : dataStyle);
            }

            // TOPSIS得分列(归一化sheet)
            if (isNormalized && scoreMap != null) {
                Map<String, JwBranchScore> branchScores = scoreMap.get(branch.getBranchId());
                for (String cat : scoreCats) {
                    JwBranchScore sc = branchScores != null ? branchScores.get(cat) : null;
                    Cell pc = row.createCell(colIdx++);
                    if (sc != null && sc.getPositiveDistance() != null) pc.setCellValue(sc.getPositiveDistance());
                    pc.setCellStyle(dataStyle);
                    Cell nc = row.createCell(colIdx++);
                    if (sc != null && sc.getNegativeDistance() != null) nc.setCellValue(sc.getNegativeDistance());
                    nc.setCellStyle(dataStyle);
                    Cell scc = row.createCell(colIdx++);
                    if (sc != null && sc.getCategoryScore() != null) scc.setCellValue(sc.getCategoryScore());
                    scc.setCellStyle(dataStyle);
                    Cell rc = row.createCell(colIdx++);
                    if (sc != null && sc.getRankNum() != null) rc.setCellValue(sc.getRankNum());
                    rc.setCellStyle(dataStyle);
                }
            }
        }

        // 统一列宽（最大18字符，最小10字符）
        for (int i = 0; i < totalCols; i++) {
            sheet.autoSizeColumn(i);
            int w = sheet.getColumnWidth(i);
            if (w > 18 * 256) sheet.setColumnWidth(i, 18 * 256);
            if (w < 10 * 256) sheet.setColumnWidth(i, 10 * 256);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createWeightStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createMaxMinStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /**
     * 百分比样式（保留2位小数，适用于日均增幅列）
     */
    private CellStyle createPctStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));
        return style;
    }

    /** 沿 parent_code 链找到根节点的 indicator_name 作为分类名 */
    private String getRootName(JwIndicatorConfig cfg, Map<String, JwIndicatorConfig> configMap) {
        String parent = cfg.getParentCode();
        if (parent == null || parent.isEmpty()) return cfg.getIndicatorName();
        String prev = parent;
        while (parent != null && !parent.isEmpty()) {
            JwIndicatorConfig p = configMap.get(parent);
            if (p == null) break;
            prev = parent;
            parent = p.getParentCode();
        }
        JwIndicatorConfig root = configMap.get(prev);
        return root != null ? root.getIndicatorName() : null;
    }
}
