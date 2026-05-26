package com.ruoyi.jwmap.service.impl;

import com.ruoyi.jwmap.domain.*;
import com.ruoyi.jwmap.mapper.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
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
        List<JwIndicatorConfig> indicators = indicatorConfigMapper.selectBySourceTable("网格数据");
        List<JwGridMeta> gridMetas = gridMetaMapper.selectByCity(city);
        List<JwGridSummary> summaries = gridSummaryMapper.selectByCity(city);
        List<JwGridDataRaw> dataList = gridDataRawMapper.selectAllByCity(city);

        Map<String, JwGridSummary> summaryMap = summaries.stream()
                .collect(Collectors.toMap(JwGridSummary::getIndicatorCode, s -> s, (a, b) -> a));

        // 构建网格-指标-值 映射
        Map<String, Map<String, Double>> gridIndicatorMap = new LinkedHashMap<>();
        for (JwGridDataRaw data : dataList) {
            gridIndicatorMap.computeIfAbsent(data.getGridCode(), k -> new LinkedHashMap<>())
                    .put(data.getIndicatorCode(), data.getIndicatorValue());
        }

        // 筛选有数据的指标
        List<JwIndicatorConfig> activeIndicators = indicators.stream()
                .filter(ind -> summaryMap.containsKey(ind.getIndicatorCode()))
                .collect(Collectors.toList());

        writePivotGridWorkbook(outputStream, "原始指标数据", gridMetas, activeIndicators,
                summaryMap, gridIndicatorMap, false, false);
    }

    /**
     * 导出网格归一化指标数据
     */
    public void exportGridDataNormalized(String city, OutputStream outputStream) throws IOException {
        List<JwIndicatorConfig> indicators = indicatorConfigMapper.selectBySourceTable("网格数据");
        List<JwGridMeta> gridMetas = gridMetaMapper.selectByCity(city);
        List<JwGridSummary> summaries = gridSummaryMapper.selectByCity(city);
        List<JwGridDataNormalized> dataList = gridDataNormalizedMapper.selectAllByCity(city);

        Map<String, JwGridSummary> summaryMap = summaries.stream()
                .collect(Collectors.toMap(JwGridSummary::getIndicatorCode, s -> s, (a, b) -> a));

        // 构建网格-指标-归一化值 映射
        Map<String, Map<String, Double>> gridIndicatorMap = new LinkedHashMap<>();
        for (JwGridDataNormalized data : dataList) {
            gridIndicatorMap.computeIfAbsent(data.getGridCode(), k -> new LinkedHashMap<>())
                    .put(data.getIndicatorCode(), data.getNormalizedValue());
        }

        List<JwIndicatorConfig> activeIndicators = indicators.stream()
                .filter(ind -> summaryMap.containsKey(ind.getIndicatorCode()))
                .collect(Collectors.toList());

        writePivotGridWorkbook(outputStream, "归一化指标数据", gridMetas, activeIndicators,
                summaryMap, gridIndicatorMap, true, false);
    }

    /**
     * 写入网格透视Workbook
     *
     * @param isNormalized 是否已归一化（影响summary中取max/min/norm字段名）
     */
    private void writePivotGridWorkbook(OutputStream outputStream, String sheetName,
                                         List<JwGridMeta> gridMetas,
                                         List<JwIndicatorConfig> indicators,
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
            for (JwIndicatorConfig ind : indicators) {
                Cell cell = headerRow.createCell(colIdx++);
                cell.setCellValue(ind.getIndicatorName());
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
            for (JwIndicatorConfig ind : indicators) {
                JwGridSummary summary = summaryMap.get(ind.getIndicatorCode());
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
            for (JwIndicatorConfig ind : indicators) {
                JwGridSummary summary = summaryMap.get(ind.getIndicatorCode());
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
            for (JwIndicatorConfig ind : indicators) {
                JwGridSummary summary = summaryMap.get(ind.getIndicatorCode());
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
                for (JwIndicatorConfig ind : indicators) {
                    Cell cell = row.createCell(colIdx++);
                    if (indicatorValues != null && indicatorValues.containsKey(ind.getIndicatorCode())) {
                        cell.setCellValue(indicatorValues.get(ind.getIndicatorCode()));
                    }
                    cell.setCellStyle(dataStyle);
                }
            }

            // 自适应列宽
            for (int i = 0; i < indicators.size() + 4; i++) {
                sheet.autoSizeColumn(i);
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

    // ==================== 样式创建 ====================

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
