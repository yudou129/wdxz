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

            int rowNum = 0;

            // 第1行：指标名称行
            Row headerRow = sheet.createRow(rowNum++);
            headerRow.createCell(0).setCellValue("网格编码");
            headerRow.getCell(0).setCellStyle(headerStyle);
            headerRow.createCell(1).setCellValue("经度");
            headerRow.getCell(1).setCellStyle(headerStyle);
            headerRow.createCell(2).setCellValue("纬度");
            headerRow.getCell(2).setCellStyle(headerStyle);
            headerRow.createCell(3).setCellValue("所属区");
            headerRow.getCell(3).setCellStyle(headerStyle);

            int colIdx = 4;
            for (String code : codeList) {
                Cell cell = headerRow.createCell(colIdx++);
                cell.setCellValue(indicatorNameMap.getOrDefault(code, code));
                cell.setCellStyle(headerStyle);
            }

            // 第2行：实际权重
            Row weightRow = sheet.createRow(rowNum++);
            weightRow.createCell(0).setCellValue("实际权重");
            weightRow.getCell(0).setCellStyle(weightStyle);
            weightRow.createCell(1).setCellStyle(maxMinStyle);
            weightRow.createCell(2).setCellStyle(maxMinStyle);
            weightRow.createCell(3).setCellStyle(maxMinStyle);
            colIdx = 4;
            for (String code : codeList) {
                JwGridSummary summary = summaryMap.get(code);
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
            maxRow.createCell(2).setCellStyle(maxMinStyle);
            maxRow.createCell(3).setCellStyle(maxMinStyle);
            colIdx = 4;
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

            // 第4行：MIN
            Row minRow = sheet.createRow(rowNum++);
            minRow.createCell(0).setCellValue("MIN");
            minRow.getCell(0).setCellStyle(maxMinStyle);
            minRow.createCell(1).setCellStyle(maxMinStyle);
            minRow.createCell(2).setCellStyle(maxMinStyle);
            minRow.createCell(3).setCellStyle(maxMinStyle);
            colIdx = 4;
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

            // 第5行起：数据行
            for (JwGridMeta meta : gridMetas) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(meta.getGridCode());
                row.getCell(0).setCellStyle(dataStyle);
                row.createCell(1).setCellValue(meta.getLongitude() != null ? meta.getLongitude() : 0);
                row.getCell(1).setCellStyle(dataStyle);
                row.createCell(2).setCellValue(meta.getLatitude() != null ? meta.getLatitude() : 0);
                row.getCell(2).setCellStyle(dataStyle);
                row.createCell(3).setCellValue(meta.getDistrict() != null ? meta.getDistrict() : "");
                row.getCell(3).setCellStyle(dataStyle);

                Map<String, Double> indicatorValues = gridIndicatorMap.get(meta.getGridCode());
                colIdx = 4;
                for (String code : codeList) {
                    Cell cell = row.createCell(colIdx++);
                    if (indicatorValues != null && indicatorValues.containsKey(code)) {
                        cell.setCellValue(indicatorValues.get(code));
                    }
                    cell.setCellStyle(dataStyle);
                }
            }

            // 自适应列宽（SXSSF需要先track列）
            try {
                if (sheet instanceof SXSSFSheet) {
                    ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();
                }
                for (int i = 0; i < codeList.size() + 4; i++) {
                    sheet.autoSizeColumn(i);
                }
            } catch (Exception e) {
                log.warn("自动调整列宽失败，跳过: {}", e.getMessage());
            }

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

            // 数据行
            for (JwBranchInfo branch : branches) {
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

        List<String> codeList = new ArrayList<>(indicatorCodes);
        Map<String, String> indicatorNameMap = new LinkedHashMap<>();
        for (String code : codeList) {
            JwIndicatorConfig config = indicatorConfigMapper.selectByCode(code);
            indicatorNameMap.put(code, config != null ? config.getIndicatorName() : code);
        }

        int scoreColCount = (scoreMap != null) ? 3 : 0;
        int fixedCols = 4; // 网格编码, 经度, 纬度, 所属区
        int totalCols = fixedCols + codeList.size() + scoreColCount;

        int rowNum = 0;

        // 第1行：指标名称行
        Row headerRow = sheet.createRow(rowNum++);
        String[] headerLabels = {"网格编码", "经度", "纬度", "所属区"};
        for (int i = 0; i < fixedCols; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headerLabels[i]);
            c.setCellStyle(headerStyle);
        }
        int colIdx = fixedCols;
        for (String code : codeList) {
            Cell c = headerRow.createCell(colIdx++);
            c.setCellValue(indicatorNameMap.getOrDefault(code, code));
            c.setCellStyle(headerStyle);
        }
        // 得分列头
        if (scoreMap != null) {
            for (String label : new String[]{"D+", "D-", "选址得分"}) {
                Cell c = headerRow.createCell(colIdx++);
                c.setCellValue(label);
                c.setCellStyle(headerStyle);
            }
        }

        // 第2行：实际权重
        Row weightRow = sheet.createRow(rowNum++);
        weightRow.createCell(0).setCellValue("实际权重");
        weightRow.getCell(0).setCellStyle(weightStyle);
        for (int i = 1; i < totalCols; i++) weightRow.createCell(i).setCellStyle(maxMinStyle);
        colIdx = fixedCols;
        for (String code : codeList) {
            JwGridSummary summary = summaryMap.get(code);
            Cell c = weightRow.createCell(colIdx++);
            if (summary != null && summary.getActualWeight() != null) c.setCellValue(summary.getActualWeight());
            c.setCellStyle(weightStyle);
        }

        // 第3行：MAX
        Row maxRow = sheet.createRow(rowNum++);
        maxRow.createCell(0).setCellValue("MAX");
        maxRow.getCell(0).setCellStyle(maxMinStyle);
        for (int i = 1; i < totalCols; i++) maxRow.createCell(i).setCellStyle(maxMinStyle);
        colIdx = fixedCols;
        for (String code : codeList) {
            JwGridSummary summary = summaryMap.get(code);
            Cell c = maxRow.createCell(colIdx++);
            if (summary != null) {
                c.setCellValue(isNormalized
                        ? (summary.getMaxNorm() != null ? summary.getMaxNorm() : 0)
                        : (summary.getMaxRaw() != null ? summary.getMaxRaw() : 0));
            }
            c.setCellStyle(maxMinStyle);
        }

        // 第4行：MIN
        Row minRow = sheet.createRow(rowNum++);
        minRow.createCell(0).setCellValue("MIN");
        minRow.getCell(0).setCellStyle(maxMinStyle);
        for (int i = 1; i < totalCols; i++) minRow.createCell(i).setCellStyle(maxMinStyle);
        colIdx = fixedCols;
        for (String code : codeList) {
            JwGridSummary summary = summaryMap.get(code);
            Cell c = minRow.createCell(colIdx++);
            if (summary != null) {
                c.setCellValue(isNormalized
                        ? (summary.getMinNorm() != null ? summary.getMinNorm() : 0)
                        : (summary.getMinRaw() != null ? summary.getMinRaw() : 0));
            }
            c.setCellStyle(maxMinStyle);
        }

        // 第5行起：数据行
        for (JwGridMeta meta : gridMetas) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(meta.getGridCode());
            row.getCell(0).setCellStyle(dataStyle);
            row.createCell(1).setCellValue(meta.getLongitude() != null ? meta.getLongitude() : 0);
            row.getCell(1).setCellStyle(dataStyle);
            row.createCell(2).setCellValue(meta.getLatitude() != null ? meta.getLatitude() : 0);
            row.getCell(2).setCellStyle(dataStyle);
            row.createCell(3).setCellValue(meta.getDistrict() != null ? meta.getDistrict() : "");
            row.getCell(3).setCellStyle(dataStyle);

            Map<String, Double> vals = gridIndicatorMap.get(meta.getGridCode());
            colIdx = fixedCols;
            for (String code : codeList) {
                Cell c = row.createCell(colIdx++);
                if (vals != null && vals.containsKey(code)) c.setCellValue(vals.get(code));
                c.setCellStyle(dataStyle);
            }
            // TOPSIS得分
            if (scoreMap != null) {
                JwGridScore sc = scoreMap.get(meta.getGridCode());
                Cell dc = row.createCell(colIdx++);
                if (sc != null && sc.getPositiveDistance() != null) dc.setCellValue(sc.getPositiveDistance());
                dc.setCellStyle(dataStyle);
                Cell nc = row.createCell(colIdx++);
                if (sc != null && sc.getNegativeDistance() != null) nc.setCellValue(sc.getNegativeDistance());
                nc.setCellStyle(dataStyle);
                Cell sc_c = row.createCell(colIdx++);
                if (sc != null && sc.getSiteScore() != null) sc_c.setCellValue(sc.getSiteScore());
                sc_c.setCellStyle(dataStyle);
            }
        }

        // 自适应列宽
        try {
            if (sheet instanceof SXSSFSheet) ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();
            for (int i = 0; i < totalCols; i++) sheet.autoSizeColumn(i);
        } catch (Exception e) {
            log.warn("自动调整列宽失败，跳过: {}", e.getMessage());
        }
    }

    // ==================== 网点组合导出（一个文件多个Sheet） ====================

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

    // 分类边界 (startIndex inclusive, endIndex exclusive)
    private static final int[] CAT_BOUNDARIES = {0, 2, 12, 19, 22}; // revenue, indicator, customer, operation

    /**
     * 导出网点组合数据：基础数据 + 数据计算表 + 归一化处理（含TOPSIS得分）
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

        List<JwBranchIndicator> baseIndicators;
        List<JwBranchIndicator> calcIndicators;
        List<JwBranchIndicator> normIndicators;
        List<JwBranchSummary> summaries;
        List<JwBranchScore> scores;
        try {
            baseIndicators = branchIndicatorMapper.selectByCityAndSheetType(city, "基础数据");
            calcIndicators = branchIndicatorMapper.selectByCityAndYear(city, dataYear, "数据计算表");
            normIndicators = branchIndicatorMapper.selectByCityAndYear(city, dataYear, "数据计算表归一化");
            summaries = branchSummaryMapper.selectByCityAndYear(city, dataYear);
            scores = branchScoreMapper.selectByCityAndYear(city, dataYear);
            log.info("网点导出数据加载完成: baseIndicators={}, calc={}, norm={}, summaries={}, scores={}",
                    baseIndicators != null ? baseIndicators.size() : 0,
                    calcIndicators != null ? calcIndicators.size() : 0,
                    normIndicators != null ? normIndicators.size() : 0,
                    summaries != null ? summaries.size() : 0,
                    scores != null ? scores.size() : 0);
        } catch (Exception e) {
            log.error("获取网点指标数据失败 city={}, year={}", city, dataYear, e);
            throw e;
        }

        // 预加载所有可能用到的指标名称
        Set<String> allCodes = new LinkedHashSet<>();
        if (calcIndicators != null) calcIndicators.forEach(i -> allCodes.add(i.getIndicatorCode()));
        if (normIndicators != null) normIndicators.forEach(i -> allCodes.add(i.getIndicatorCode()));
        allCodes.addAll(ALL_CALC_INDICATORS);
        Map<String, String> nameMap = new LinkedHashMap<>();
        for (String code : allCodes) {
            JwIndicatorConfig cfg = indicatorConfigMapper.selectByCode(code);
            nameMap.put(code, cfg != null ? cfg.getIndicatorName() : code);
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
            // Sheet 1: 基础数据
            writeBranchBaseSheet(workbook.createSheet("基础数据"), branches, baseIndicators, nameMap);

            // Sheet 2: 数据计算表
            writeBranchCalcSheet(workbook.createSheet("数据计算表"), branches, calcIndicators,
                    summaryMap, nameMap, dataYear, false, null);

            // Sheet 3: 数据计算表（归一化处理）
            writeBranchCalcSheet(workbook.createSheet("数据计算表（归一化处理）"), branches, normIndicators,
                    summaryMap, nameMap, dataYear, true, scoreMap);

            workbook.write(outputStream);
        } finally {
            workbook.close();
        }
    }

    /**
     * 写入基础数据Sheet
     * 8个固定列 + 各年份指标列
     */
    private void writeBranchBaseSheet(Sheet sheet,
                                       List<JwBranchInfo> branches,
                                       List<JwBranchIndicator> indicators,
                                       Map<String, String> nameMap) {
        CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());
        CellStyle dataStyle = createDataStyle(sheet.getWorkbook());

        // 固定列
        String[] fixedHeaders = {"一级支行", "二级支行", "机构号", "行政区", "街道", "地址", "经度", "纬度"};

        // 从指标数据中提取所有年份和指标编码
        Set<Integer> years = new TreeSet<>();
        Set<String> indicatorCodes = new LinkedHashSet<>();
        Map<Long, Map<String, Map<Integer, Double>>> branchData = new LinkedHashMap<>(); // branchId -> code -> year -> value
        if (indicators != null) {
            for (JwBranchIndicator ind : indicators) {
                years.add(ind.getDataYear());
                indicatorCodes.add(ind.getIndicatorCode());
                branchData.computeIfAbsent(ind.getBranchId(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(ind.getIndicatorCode(), k -> new LinkedHashMap<>())
                        .put(ind.getDataYear(), ind.getIndicatorValue());
            }
        }

        List<String> codeList = new ArrayList<>(indicatorCodes);
        List<Integer> yearList = new ArrayList<>(years);
        int totalCols = fixedHeaders.length + codeList.size() * yearList.size();

        int rowNum = 0;

        // Row0: 分类行（合并单元格）
        Row catRow = sheet.createRow(rowNum++);
        for (int i = 0; i < fixedHeaders.length; i++) {
            Cell c = catRow.createCell(i);
            c.setCellValue(i == 0 ? "网点信息" : "");
            c.setCellStyle(headerStyle);
        }
        if (fixedHeaders.length > 0) {
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, fixedHeaders.length - 1));
        }

        // 按分类合并指标列
        int colIdx = fixedHeaders.length;
        Map<String, Integer> catRangeStart = new LinkedHashMap<>();
        Map<String, String> catLabels = new LinkedHashMap<>();
        catLabels.put("经营概况", "经营概况");
        catLabels.put("业务指标", "业务指标");
        catLabels.put("客户发展", "客户发展");
        catLabels.put("业务运营", "业务运营");
        String activeCat = null;
        for (String code : codeList) {
            String displayName = nameMap.getOrDefault(code, code);
            String cat = "业务指标";
            if (ALL_CALC_INDICATORS.indexOf(code) >= 0) {
                int idx = ALL_CALC_INDICATORS.indexOf(code);
                if (idx < 2) cat = "经营概况";
                else if (idx < 12) cat = "业务指标";
                else if (idx < 19) cat = "客户发展";
                else cat = "业务运营";
            }
            if (!cat.equals(activeCat)) {
                catRangeStart.put(cat, colIdx);
                activeCat = cat;
            }
            for (int yi = 0; yi < yearList.size(); yi++) {
                Cell c = catRow.createCell(colIdx++);
                c.setCellValue(cat);
                c.setCellStyle(headerStyle);
            }
        }
        // 注册合并区域
        for (String cat : new String[]{"经营概况", "业务指标", "客户发展", "业务运营"}) {
            Integer start = catRangeStart.get(cat);
            if (start != null) {
                // 找到该分类的结束列（下一个分类的start - 1，或总列数 - 1）
                int end = colIdx - 1;
                String nextCat = null;
                for (String nc : new String[]{"经营概况", "业务指标", "客户发展", "业务运营"}) {
                    Integer ns = catRangeStart.get(nc);
                    if (ns != null && ns > start) {
                        end = ns - 1;
                        break;
                    }
                }
                if (end >= start) {
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, start, end));
                }
            }
        }

        // Row1: 子分类行（空行，保持格式）
        Row subRow = sheet.createRow(rowNum++);
        for (int i = 0; i < totalCols; i++) {
            subRow.createCell(i).setCellStyle(headerStyle);
        }

        // Row2: 指标名称行
        Row nameRow = sheet.createRow(rowNum++);
        for (int i = 0; i < fixedHeaders.length; i++) {
            nameRow.createCell(i).setCellStyle(headerStyle);
        }
        colIdx = fixedHeaders.length;
        for (String code : codeList) {
            String displayName = nameMap.getOrDefault(code, code);
            for (int yi = 0; yi < yearList.size(); yi++) {
                Cell c = nameRow.createCell(colIdx++);
                c.setCellValue(displayName);
                c.setCellStyle(headerStyle);
            }
        }

        // Row2: 年份行
        Row yearRow = sheet.createRow(rowNum++);
        for (int i = 0; i < fixedHeaders.length; i++) {
            Cell c = yearRow.createCell(i);
            c.setCellStyle(headerStyle);
        }
        colIdx = fixedHeaders.length;
        for (String ignored : codeList) {
            for (Integer y : yearList) {
                Cell c = yearRow.createCell(colIdx++);
                c.setCellValue(y);
                c.setCellStyle(headerStyle);
            }
        }

        // Row3+: 数据行
        for (JwBranchInfo branch : branches) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(branch.getPrimaryBranch() != null ? branch.getPrimaryBranch() : "");
            row.getCell(0).setCellStyle(dataStyle);
            row.createCell(1).setCellValue(branch.getSecondaryBranch() != null ? branch.getSecondaryBranch() : "");
            row.getCell(1).setCellStyle(dataStyle);
            row.createCell(2).setCellValue(branch.getBranchCode() != null ? branch.getBranchCode() : "");
            row.getCell(2).setCellStyle(dataStyle);
            row.createCell(3).setCellValue(branch.getDistrictName() != null ? branch.getDistrictName() : "");
            row.getCell(3).setCellStyle(dataStyle);
            row.createCell(4).setCellValue(branch.getStreet() != null ? branch.getStreet() : "");
            row.getCell(4).setCellStyle(dataStyle);
            row.createCell(5).setCellValue(branch.getAddress() != null ? branch.getAddress() : "");
            row.getCell(5).setCellStyle(dataStyle);
            row.createCell(6).setCellValue(branch.getLongitude() != null ? branch.getLongitude() : 0);
            row.getCell(6).setCellStyle(dataStyle);
            row.createCell(7).setCellValue(branch.getLatitude() != null ? branch.getLatitude() : 0);
            row.getCell(7).setCellStyle(dataStyle);

            Map<String, Map<Integer, Double>> bd = branchData.get(branch.getBranchId());
            colIdx = fixedHeaders.length;
            for (String code : codeList) {
                Map<Integer, Double> yearVals = bd != null ? bd.get(code) : null;
                for (Integer ignored : yearList) {
                    Cell c = row.createCell(colIdx++);
                    if (yearVals != null && yearVals.containsKey(ignored)) {
                        c.setCellValue(yearVals.get(ignored));
                    }
                    c.setCellStyle(dataStyle);
                }
            }
        }

        // 自适应列宽
        for (int i = 0; i < totalCols; i++) sheet.autoSizeColumn(i);
    }

    /**
     * 写入数据计算表Sheet（含多级表头、实际权值/MAX/MIN行）
     */
    private void writeBranchCalcSheet(Sheet sheet,
                                       List<JwBranchInfo> branches,
                                       List<JwBranchIndicator> indicators,
                                       Map<String, JwBranchSummary> summaryMap,
                                       Map<String, String> nameMap,
                                       Integer dataYear,
                                       boolean isNormalized,
                                       Map<Long, Map<String, JwBranchScore>> scoreMap) {
        CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());
        CellStyle weightStyle = createWeightStyle(sheet.getWorkbook());
        CellStyle maxMinStyle = createMaxMinStyle(sheet.getWorkbook());
        CellStyle dataStyle = createDataStyle(sheet.getWorkbook());

        // 5个固定列 + 22个指标
        int fixedCols = 5;
        String[] fixedHeaders = {"一级支行", "二级支行", "机构号", "经度", "纬度"};

        List<String> codeList = new ArrayList<>(ALL_CALC_INDICATORS);

        // 构建 branchId -> code -> value 映射
        Map<Long, Map<String, Double>> branchValueMap = new LinkedHashMap<>();
        if (indicators != null) {
            for (JwBranchIndicator ind : indicators) {
                branchValueMap.computeIfAbsent(ind.getBranchId(), k -> new LinkedHashMap<>())
                        .put(ind.getIndicatorCode(), ind.getIndicatorValue());
            }
        }

        // 分类名称和列范围 (col index from fixedCols)
        String[][] categories = {
            {"经营概况", "0", "2"},   // revenue: 2 indicators
            {"业务指标", "2", "12"},  // indicator: 10
            {"客户发展", "12", "19"}, // customer: 7
            {"业务运营", "19", "22"}  // operation: 3
        };

        int totalCols = fixedCols + codeList.size();
        int rowNum = 0;

        // ===== Row0: 分类名称（合并单元格） =====
        Row row0 = sheet.createRow(rowNum++);
        for (int i = 0; i < fixedCols; i++) {
            Cell c = row0.createCell(i);
            c.setCellValue(i == 0 ? "网点信息" : "");
            c.setCellStyle(headerStyle);
        }
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, fixedCols - 1));
        for (String[] cat : categories) {
            int start = fixedCols + Integer.parseInt(cat[1]);
            int end = fixedCols + Integer.parseInt(cat[2]) - 1; // CellRangeAddress end inclusive
            for (int i = start; i <= end; i++) {
                Cell c = row0.createCell(i);
                c.setCellValue(i == start ? cat[0] : "");
                c.setCellStyle(headerStyle);
            }
            if (end >= start) {
                sheet.addMergedRegion(new CellRangeAddress(0, 0, start, end));
            }
        }

        // ===== Row1: 子分类（空行，保持格式对齐） =====
        Row subCatRow = sheet.createRow(rowNum++);
        for (int i = 0; i < totalCols; i++) {
            Cell c = subCatRow.createCell(i);
            c.setCellStyle(headerStyle);
        }

        // ===== Row2: 指标名称 =====
        Row indicatorRow = sheet.createRow(rowNum++);
        for (int i = 0; i < fixedCols; i++) {
            Cell c = indicatorRow.createCell(i);
            c.setCellStyle(headerStyle);
        }
        int colIdx = fixedCols;
        for (String code : codeList) {
            Cell c = indicatorRow.createCell(colIdx++);
            c.setCellValue(nameMap.getOrDefault(code, code));
            c.setCellStyle(headerStyle);
        }

        // ===== Row3: 年份 =====
        Row yearRow = sheet.createRow(rowNum++);
        for (int i = 0; i < fixedCols; i++) {
            Cell c = yearRow.createCell(i);
            c.setCellStyle(headerStyle);
        }
        colIdx = fixedCols;
        for (String ignored : codeList) {
            Cell c = yearRow.createCell(colIdx++);
            if (dataYear != null) c.setCellValue(dataYear.doubleValue());
            c.setCellStyle(headerStyle);
        }

        // ===== Row3: 实际权值 =====
        Row weightRow = sheet.createRow(rowNum++);
        weightRow.createCell(0).setCellValue("实际权值");
        weightRow.getCell(0).setCellStyle(weightStyle);
        for (int i = 1; i < fixedCols; i++) weightRow.createCell(i).setCellStyle(maxMinStyle);
        colIdx = fixedCols;
        for (String code : codeList) {
            Cell c = weightRow.createCell(colIdx++);
            if (summaryMap != null) {
                JwBranchSummary s = summaryMap.get(code);
                if (s != null && s.getActualWeight() != null) c.setCellValue(s.getActualWeight());
            }
            c.setCellStyle(weightStyle);
        }

        // ===== Row4: MAX =====
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
            c.setCellStyle(maxMinStyle);
        }

        // ===== Row5: MIN =====
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
            c.setCellStyle(maxMinStyle);
        }

        // ===== Row6+: 数据行 =====
        for (JwBranchInfo branch : branches) {
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
                if (vals != null && vals.containsKey(code)) c.setCellValue(vals.get(code));
                c.setCellStyle(dataStyle);
            }
        }

        // 如果归一化且有得分数据，在右侧追加TOPSIS五类得分列
        if (isNormalized && scoreMap != null && !scoreMap.isEmpty()) {
            String[] scoreCategories = {"revenue", "indicator", "customer", "operation", "overall"};
            String[] scoreLabels = {"营收得分", "指标得分", "客户得分", "运营得分", "总分"};
            String[] distLabels = {"D+", "D-", "得分"};
            int scoreStartCol = totalCols;

            // 增加得分列头到header行 (Row1)
            colIdx = totalCols;
            for (int si = 0; si < scoreCategories.length; si++) {
                for (String dl : distLabels) {
                    Cell c = indicatorRow.createCell(colIdx++);
                    c.setCellValue(scoreLabels[si] + dl);
                    c.setCellStyle(headerStyle);
                }
            }

            // 更新year/weight/MAX/MIN行 — 添加占位
            for (int i = colIdx; i < scoreStartCol + scoreCategories.length * 3; i++) {
                yearRow.createCell(i).setCellStyle(headerStyle);
                weightRow.createCell(i).setCellStyle(maxMinStyle);
                maxRow.createCell(i).setCellStyle(maxMinStyle);
                minRow.createCell(i).setCellStyle(maxMinStyle);
            }

            // 数据行填充得分
            int scoreColCount = scoreCategories.length * 3;
            int dataRowStart = rowNum - branches.size(); // 当前已写到rowNum行，数据从rowNum - branches.size()开始
            int dataIdx = 0;
            for (JwBranchInfo branch : branches) {
                Row row = sheet.getRow(dataRowStart + dataIdx++);
                Map<String, JwBranchScore> branchScores = scoreMap.get(branch.getBranchId());
                colIdx = scoreStartCol;
                for (String cat : scoreCategories) {
                    JwBranchScore sc = branchScores != null ? branchScores.get(cat) : null;
                    Cell dc = row.createCell(colIdx++);
                    if (sc != null && sc.getPositiveDistance() != null) dc.setCellValue(sc.getPositiveDistance());
                    dc.setCellStyle(dataStyle);
                    Cell nc = row.createCell(colIdx++);
                    if (sc != null && sc.getNegativeDistance() != null) nc.setCellValue(sc.getNegativeDistance());
                    nc.setCellStyle(dataStyle);
                    Cell sc_c = row.createCell(colIdx++);
                    if (sc != null && sc.getCategoryScore() != null) sc_c.setCellValue(sc.getCategoryScore());
                    sc_c.setCellStyle(dataStyle);
                }
            }
            totalCols += scoreColCount;
        }

        // 自适应列宽
        for (int i = 0; i < totalCols; i++) sheet.autoSizeColumn(i);
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
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }
}
