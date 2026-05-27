package com.ruoyi.jwmap.service.impl;

import com.ruoyi.jwmap.domain.*;
import com.ruoyi.jwmap.mapper.*;
import com.ruoyi.jwmap.service.IGridComputeService;
import com.ruoyi.jwmap.util.TopsisCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 网格数据计算服务实现
 *
 * 管线步骤：
 * 1. 构建网格元信息（坐标范围 + POI统计）
 * 2. 从人口热力复制原始指标数据
 * 3. 计算汇总行（实际权重/MAX/MIN）
 * 4. 归一化处理
 * 5. TOPSIS选址得分
 */
@Service
public class GridComputeServiceImpl implements IGridComputeService {

    @Autowired
    private JwGridMetaMapper gridMetaMapper;
    @Autowired
    private JwGridDataRawMapper gridDataRawMapper;
    @Autowired
    private JwGridSummaryMapper gridSummaryMapper;
    @Autowired
    private JwGridDataNormalizedMapper gridDataNormalizedMapper;
    @Autowired
    private JwGridScoreMapper gridScoreMapper;
    @Autowired
    private JwPopulationHeatMapper populationHeatMapper;
    @Autowired
    private JwPoiInfoMapper poiInfoMapper;
    @Autowired
    private JwIndicatorConfigMapper indicatorConfigMapper;
    @Autowired
    private JwExternalResWeightMapper externalWeightMapper;

    @Autowired
    private JwBranchInfoMapper branchInfoMapper;

    private static final double KM_PER_DEGREE_LAT = 111.32;

    @Override
    @Transactional
    public int computeGridData(String city) {
        int count = computeGridMeta(city);
        if (count == 0) return 0;
        computeGridRawData(city);
        computeGridSummary(city);
        computeGridNormalized(city);
        computeGridScore(city);
        return count;
    }

    @Override
    @Transactional
    public int computeGridMeta(String city) {
        // 获取人口热力中所有不重复的网格（不JOIN grid_meta，避免循环依赖）
        List<String> allGridCodes = populationHeatMapper.selectDistinctGridCodes();
        if (allGridCodes == null || allGridCodes.isEmpty()) return 0;

        // 预加载该市所有已存在的grid_meta，避免逐条查询（N+1）
        List<JwGridMeta> cityMetas = gridMetaMapper.selectByCity(city);
        Map<String, JwGridMeta> metaMap = cityMetas.stream()
            .collect(Collectors.toMap(JwGridMeta::getGridCode, m -> m, (a, b) -> a));

        // 获取该市所有POI
        List<JwPoiInfo> poiList = poiInfoMapper.selectByCity(city);

        List<JwGridMeta> metaList = new ArrayList<>();
        for (String gridCode : allGridCodes) {
            JwGridMeta existingMeta = metaMap.get(gridCode);
            if (existingMeta == null) continue;

            double lng = existingMeta.getLongitude();
            double lat = existingMeta.getLatitude();

            // 计算四至范围（1KM×1KM网格）
            double cosLat = Math.cos(Math.toRadians(lat));
            double lngOffset = 0.5 / (KM_PER_DEGREE_LAT * cosLat);
            double latOffset = 0.5 / KM_PER_DEGREE_LAT;

            existingMeta.setWestLongitude(lng - lngOffset);
            existingMeta.setEastLongitude(lng + lngOffset);
            existingMeta.setNorthLatitude(lat + latOffset);
            existingMeta.setSouthLatitude(lat - latOffset);

            // 统计网格内POI数量
            int poiCount = 0;
            for (JwPoiInfo poi : poiList) {
                if (poi.getLongitude() != null && poi.getLatitude() != null
                    && poi.getLongitude() >= existingMeta.getWestLongitude()
                    && poi.getLongitude() <= existingMeta.getEastLongitude()
                    && poi.getLatitude() >= existingMeta.getSouthLatitude()
                    && poi.getLatitude() <= existingMeta.getNorthLatitude()) {
                    poiCount++;
                }
            }
            existingMeta.setPoiCount(poiCount);
            metaList.add(existingMeta);
        }

        if (!metaList.isEmpty()) {
            for (JwGridMeta meta : metaList) {
                gridMetaMapper.upsertGridMeta(meta);
            }
        }
        return metaList.size();
    }

    private void computeGridRawData(String city) {
        // 从人口热力复制指标数据到网格原始数据表
        // 先清理该市旧数据
        gridDataRawMapper.deleteByCity(city);

        // 查询该市所有网格
        List<JwGridMeta> metas = gridMetaMapper.selectByCity(city);
        if (metas.isEmpty()) return;

        for (JwGridMeta meta : metas) {
            // 查询该网格的人口热力数据
            List<JwPopulationHeat> heatData = populationHeatMapper.selectByGridCode(meta.getGridCode());

            // 写入POI数量
            JwGridDataRaw poiRow = new JwGridDataRaw();
            poiRow.setGridCode(meta.getGridCode());
            poiRow.setIndicatorCode("poi_count");
            poiRow.setIndicatorValue((double) meta.getPoiCount());
            gridDataRawMapper.upsertGridData(poiRow);

            // 写入人口热力指标
            for (JwPopulationHeat heat : heatData) {
                JwGridDataRaw raw = new JwGridDataRaw();
                raw.setGridCode(meta.getGridCode());
                raw.setIndicatorCode(heat.getIndicatorCode());
                raw.setIndicatorValue(heat.getIndicatorValue());
                gridDataRawMapper.upsertGridData(raw);
            }
        }
    }

    private void computeGridSummary(String city) {
        // 清理旧汇总
        gridSummaryMapper.deleteByCity(city);

        // 获取所有参与加权的指标
        List<JwIndicatorConfig> indicators = indicatorConfigMapper.selectActiveWeighted();
        // 获取权重映射
        List<JwWeightConfig> weights = externalWeightMapper.selectAll();
        Map<String, Double> weightMap = weights.stream()
            .filter(w -> w.getIndicatorCode() != null && !w.getIndicatorCode().isEmpty())
            .collect(Collectors.toMap(JwWeightConfig::getIndicatorCode, JwWeightConfig::getTotalWeight, (a, b) -> a));

        // 获取该市所有网格
        List<JwGridMeta> metas = gridMetaMapper.selectByCity(city);
        if (metas.isEmpty()) return;

        for (JwIndicatorConfig indicator : indicators) {
            String code = indicator.getIndicatorCode();

            // 查询该市所有网格的这个指标值
            List<Double> values = new ArrayList<>();
            for (JwGridMeta meta : metas) {
                JwGridDataRaw raw = gridDataRawMapper.selectByGridAndIndicator(meta.getGridCode(), code);
                if (raw != null) {
                    values.add(raw.getIndicatorValue());
                }
            }
            if (values.isEmpty()) continue;

            double maxVal = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double minVal = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double weight = weightMap.getOrDefault(code, 0.0);

            JwGridSummary summary = new JwGridSummary();
            summary.setCity(city);
            summary.setIndicatorCode(code);
            summary.setActualWeight(weight);
            summary.setMaxRaw(maxVal);
            summary.setMinRaw(minVal);
            gridSummaryMapper.insertGridSummary(summary);
        }
    }

    private void computeGridNormalized(String city) {
        // 清理旧归一化数据
        gridDataNormalizedMapper.deleteByCity(city);

        List<JwGridMeta> metas = gridMetaMapper.selectByCity(city);
        List<JwGridSummary> summaries = gridSummaryMapper.selectByCity(city);
        if (metas.isEmpty() || summaries.isEmpty()) return;

        Map<String, JwGridSummary> summaryMap = summaries.stream()
            .collect(Collectors.toMap(JwGridSummary::getIndicatorCode, s -> s, (a, b) -> a));

        // 按指标分组归一化（网格公式: value / SQRT(SUMSQ(column))）
        for (JwGridSummary summary : summaries) {
            String code = summary.getIndicatorCode();
            List<Double> colValues = new ArrayList<>();
            Map<String, Double> gridValueMap = new LinkedHashMap<>();
            for (JwGridMeta meta : metas) {
                JwGridDataRaw raw = gridDataRawMapper.selectByGridAndIndicator(meta.getGridCode(), code);
                double val = raw != null ? raw.getIndicatorValue() : 0;
                colValues.add(val);
                gridValueMap.put(meta.getGridCode(), val);
            }
            List<Double> normValues = TopsisCalculator.normalizeGridColumn(colValues);
            // 更新汇总的归一化MAX/MIN
            double maxNorm = normValues.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double minNorm = normValues.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            summary.setMaxNorm(maxNorm);
            summary.setMinNorm(minNorm);
            gridSummaryMapper.updateGridSummary(summary);

            // 保存归一化值
            int i = 0;
            for (Map.Entry<String, Double> entry : gridValueMap.entrySet()) {
                JwGridDataNormalized norm = new JwGridDataNormalized();
                norm.setGridCode(entry.getKey());
                norm.setIndicatorCode(code);
                norm.setNormalizedValue(normValues.get(i++));
                gridDataNormalizedMapper.upsertGridData(norm);
            }
        }
    }

    @Override
    @Transactional
    public int computeGridScore(String city) {
        List<JwGridMeta> metas = gridMetaMapper.selectByCity(city);
        List<JwGridSummary> summaries = gridSummaryMapper.selectByCity(city);
        List<JwIndicatorConfig> indicators = indicatorConfigMapper.selectActiveWeighted();
        if (metas.isEmpty() || summaries.isEmpty()) return 0;

        Map<String, JwGridSummary> summaryMap = summaries.stream()
            .collect(Collectors.toMap(JwGridSummary::getIndicatorCode, s -> s, (a, b) -> a));

        // 构建指标有序列表（与TOPSIS计算顺序一致）
        List<String> indicatorCodes = indicators.stream()
            .map(JwIndicatorConfig::getIndicatorCode)
            .filter(summaryMap::containsKey)
            .collect(Collectors.toList());

        if (indicatorCodes.isEmpty()) return 0;

        // 提取MAX/MIN/weight向量
        List<Double> maxNorms = indicatorCodes.stream().map(c -> summaryMap.get(c).getMaxNorm()).collect(Collectors.toList());
        List<Double> minNorms = indicatorCodes.stream().map(c -> summaryMap.get(c).getMinNorm()).collect(Collectors.toList());
        List<Double> weights = indicatorCodes.stream().map(c -> summaryMap.get(c).getActualWeight()).collect(Collectors.toList());

        // 对每个网格计算TOPSIS得分
        int count = 0;
        for (JwGridMeta meta : metas) {
            List<Double> normValues = new ArrayList<>();
            for (String code : indicatorCodes) {
                JwGridDataNormalized norm = gridDataNormalizedMapper.selectByGridAndIndicator(meta.getGridCode(), code);
                normValues.add(norm != null ? norm.getNormalizedValue() : 0);
            }
            double[] result = TopsisCalculator.calcTopsis(normValues, maxNorms, minNorms, weights);

            JwGridScore score = new JwGridScore();
            score.setGridCode(meta.getGridCode());
            score.setPositiveDistance(result[0]);
            score.setNegativeDistance(result[1]);
            score.setSiteScore(result[2]);
            gridScoreMapper.upsertGridScore(score);
            count++;
        }
        return count;
    }

    @Override
    public Map<String, Object> getCityDataStatus(String city) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("city", city);
        status.put("hasPoi", poiInfoMapper.selectByCity(city).size() > 0);
        status.put("hasPopulation", populationHeatMapper.selectDistinctGridCodesByCity(city).size() > 0);
        status.put("hasWeight", externalWeightMapper.selectAll().size() > 0);
        status.put("gridCount", gridMetaMapper.selectByCity(city).size());
        status.put("hasScore", gridScoreMapper.countByCity(city) > 0);
        boolean ready = (boolean) status.get("hasPoi") && (boolean) status.get("hasPopulation") && (boolean) status.get("hasWeight");
        status.put("ready", ready);
        return status;
    }

    @Override
    public List<Map<String, Object>> getAllCityStatus() {
        // 从所有数据源收集城市列表，保证即使某个表无数据也能检测到城市
        Set<String> citySet = new LinkedHashSet<>();
        citySet.addAll(populationHeatMapper.selectDistinctCities());
        citySet.addAll(gridMetaMapper.selectDistinctCities());
        citySet.addAll(poiInfoMapper.selectDistinctCities());
        citySet.addAll(branchInfoMapper.selectDistinctCities());

        List<Map<String, Object>> result = new ArrayList<>();
        for (String city : citySet) {
            if (city != null && !city.isEmpty()) {
                result.add(getCityDataStatus(city));
            }
        }
        return result;
    }
}
