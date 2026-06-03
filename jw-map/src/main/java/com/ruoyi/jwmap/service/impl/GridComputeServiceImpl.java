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
 * 1. computeGridMeta — 网格包围盒 + POI统计
 * 2. computeGridRawData — 人口热力值 + POI计数
 * 3. computeGridSummary — 权重来自 indicator_config.getEffectiveWeight()
 * 4. computeGridNormalized — 归一化
 * 5. computeGridScore — 按根节点分类 + overall 分别TOPSIS
 */
@Service
public class GridComputeServiceImpl implements IGridComputeService {

    @Autowired private JwGridMetaMapper gridMetaMapper;
    @Autowired private JwGridDataRawMapper gridDataRawMapper;
    @Autowired private JwGridSummaryMapper gridSummaryMapper;
    @Autowired private JwGridDataNormalizedMapper gridDataNormalizedMapper;
    @Autowired private JwGridScoreMapper gridScoreMapper;
    @Autowired private JwPopulationHeatMapper populationHeatMapper;
    @Autowired private JwPoiInfoMapper poiInfoMapper;
    @Autowired private JwIndicatorConfigMapper indicatorConfigMapper;

    @Autowired private JwBranchInfoMapper branchInfoMapper;

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
        List<String> allGridCodes = populationHeatMapper.selectDistinctGridCodes();
        if (allGridCodes == null || allGridCodes.isEmpty()) return 0;

        List<JwGridMeta> cityMetas = gridMetaMapper.selectByCity(city);
        Map<String, JwGridMeta> metaMap = cityMetas.stream()
            .collect(Collectors.toMap(JwGridMeta::getGridCode, m -> m, (a, b) -> a));

        List<JwGridMeta> metaList = new ArrayList<>();
        for (String gridCode : allGridCodes) {
            JwGridMeta existingMeta = metaMap.get(gridCode);
            if (existingMeta == null) continue;

            double lng = existingMeta.getLongitude();
            double lat = existingMeta.getLatitude();
            double cosLat = Math.cos(Math.toRadians(lat));
            double lngOffset = 0.5 / (KM_PER_DEGREE_LAT * cosLat);
            double latOffset = 0.5 / KM_PER_DEGREE_LAT;

            existingMeta.setWestLongitude(lng - lngOffset);
            existingMeta.setEastLongitude(lng + lngOffset);
            existingMeta.setNorthLatitude(lat + latOffset);
            existingMeta.setSouthLatitude(lat - latOffset);

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
        gridDataRawMapper.deleteByCity(city);

        List<JwGridMeta> metas = gridMetaMapper.selectByCity(city);
        if (metas.isEmpty()) return;
        List<JwPoiInfo> poiList = poiInfoMapper.selectByCity(city);

        // 预计算每个网格的POI类型计数
        Map<String, Map<String, Integer>> gridPoiTypeCount = new LinkedHashMap<>();
        for (JwPoiInfo poi : poiList) {
            if (poi.getPoiType() == null || poi.getPoiType().isEmpty()) continue;
            String poiType = poi.getPoiType().trim();
            for (JwGridMeta meta : metas) {
                if (meta.getWestLongitude() == null || meta.getEastLongitude() == null) continue;
                if (poi.getLongitude() != null && poi.getLatitude() != null
                    && poi.getLongitude() >= meta.getWestLongitude()
                    && poi.getLongitude() <= meta.getEastLongitude()
                    && poi.getLatitude() >= meta.getSouthLatitude()
                    && poi.getLatitude() <= meta.getNorthLatitude()) {
                    gridPoiTypeCount.computeIfAbsent(meta.getGridCode(), k -> new LinkedHashMap<>())
                        .merge(poiType, 1, Integer::sum);
                }
            }
        }

        // 预加载所有 grid 相关类型指标配置，只取叶子节点用于 POI 匹配
        List<JwIndicatorConfig> allGridConfigs = indicatorConfigMapper.selectByTypes(Arrays.asList("grid", "grid_auto", "grid_raw"));
        Map<String, JwIndicatorConfig> gridConfigMap = allGridConfigs.stream()
            .collect(Collectors.toMap(JwIndicatorConfig::getIndicatorCode, c -> c, (a, b) -> a));
        Set<String> leafCodes = allGridConfigs.stream()
            .filter(c -> c.isLeaf(gridConfigMap))
            .map(JwIndicatorConfig::getIndicatorCode)
            .collect(Collectors.toSet());

        for (JwGridMeta meta : metas) {
            // 写入人口热力指标
            List<JwPopulationHeat> heatData = populationHeatMapper.selectByGridCode(meta.getGridCode());
            for (JwPopulationHeat heat : heatData) {
                JwGridDataRaw raw = new JwGridDataRaw();
                raw.setGridCode(meta.getGridCode());
                raw.setIndicatorCode(heat.getIndicatorCode());
                raw.setIndicatorValue(heat.getIndicatorValue());
                gridDataRawMapper.upsertGridData(raw);
            }

            // 写入POI类型指标：查 indicator_config 中已配置的 POI 指标，按名称匹配
            Map<String, Integer> typeCounts = gridPoiTypeCount.get(meta.getGridCode());
            if (typeCounts != null) {
                for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
                    String poiType = entry.getKey().trim();
                    // 标准化后匹配叶子节点 indicator_name
                    String indicatorCode = matchPoiIndicator(poiType, gridConfigMap, leafCodes);
                    if (indicatorCode != null) {
                        JwGridDataRaw poiRaw = new JwGridDataRaw();
                        poiRaw.setGridCode(meta.getGridCode());
                        poiRaw.setIndicatorCode(indicatorCode);
                        poiRaw.setIndicatorValue(entry.getValue().doubleValue());
                        gridDataRawMapper.upsertGridData(poiRaw);
                    }
                }
            }
        }
    }

    /**
     * 将POI类型名称匹配到 jw_indicator_config 中已配置的叶子指标
     * 策略：精确匹配 → 标准化后缀匹配 → 关键词包含匹配
     * 只匹配叶子节点，非叶子（分类节点）不参与数据匹配
     */
    private String matchPoiIndicator(String poiType, Map<String, JwIndicatorConfig> gridConfigMap,
                                      Set<String> leafCodes) {
        if (poiType == null || poiType.isEmpty()) return null;

        // 1. 精确匹配叶子 indicator_name
        for (JwIndicatorConfig cfg : gridConfigMap.values()) {
            if (poiType.equals(cfg.getIndicatorName()) && leafCodes.contains(cfg.getIndicatorCode())) {
                return cfg.getIndicatorCode();
            }
        }

        // 2. 标准化编码匹配 (poi_type_xxx 格式，兼容旧数据)
        String code = "poi_type_" + poiType.replaceAll("[\\s()（）、,，/]", "_");
        if (leafCodes.contains(code)) return code;

        // 3. 关键词包含匹配（仅叶子）
        for (JwIndicatorConfig cfg : gridConfigMap.values()) {
            if (leafCodes.contains(cfg.getIndicatorCode())
                && cfg.getIndicatorName() != null
                && (cfg.getIndicatorName().contains(poiType) || poiType.contains(cfg.getIndicatorName()))) {
                return cfg.getIndicatorCode();
            }
        }

        return null;
    }

    private void computeGridSummary(String city) {
        gridSummaryMapper.deleteByCity(city);

        // 加载 grid 相关类型所有指标，构建 code → config 映射
        List<JwIndicatorConfig> allGrid = indicatorConfigMapper.selectByTypes(Arrays.asList("grid", "grid_auto", "grid_raw"));
        Map<String, JwIndicatorConfig> configMap = allGrid.stream()
            .collect(Collectors.toMap(JwIndicatorConfig::getIndicatorCode, c -> c, (a, b) -> a));

        // 只对叶子节点计算汇总
        List<JwIndicatorConfig> leaves = allGrid.stream()
            .filter(c -> c.isLeaf(configMap))
            .collect(Collectors.toList());

        List<JwGridMeta> metas = gridMetaMapper.selectByCity(city);
        if (metas.isEmpty()) return;

        for (JwIndicatorConfig leaf : leaves) {
            String code = leaf.getIndicatorCode();
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
            double weight = leaf.getEffectiveWeight(configMap);

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
        gridDataNormalizedMapper.deleteByCity(city);

        List<JwGridMeta> metas = gridMetaMapper.selectByCity(city);
        List<JwGridSummary> summaries = gridSummaryMapper.selectByCity(city);
        if (metas.isEmpty() || summaries.isEmpty()) return;

        Map<String, JwGridSummary> summaryMap = summaries.stream()
            .collect(Collectors.toMap(JwGridSummary::getIndicatorCode, s -> s, (a, b) -> a));

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
            double maxNorm = normValues.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double minNorm = normValues.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            summary.setMaxNorm(maxNorm);
            summary.setMinNorm(minNorm);
            gridSummaryMapper.updateGridSummary(summary);

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
        gridScoreMapper.deleteByCity(city);

        List<JwGridMeta> metas = gridMetaMapper.selectByCity(city);
        List<JwGridSummary> summaries = gridSummaryMapper.selectByCity(city);
        if (metas.isEmpty() || summaries.isEmpty()) return 0;

        Map<String, JwGridSummary> summaryMap = summaries.stream()
            .collect(Collectors.toMap(JwGridSummary::getIndicatorCode, s -> s, (a, b) -> a));

        // 构建 code → config 映射，按根节点分组
        List<JwIndicatorConfig> allGrid = indicatorConfigMapper.selectByTypes(Arrays.asList("grid", "grid_auto", "grid_raw"));
        Map<String, JwIndicatorConfig> configMap = allGrid.stream()
            .collect(Collectors.toMap(JwIndicatorConfig::getIndicatorCode, c -> c, (a, b) -> a));
        List<JwIndicatorConfig> roots = allGrid.stream()
            .filter(c -> c.getParentCode() == null || c.getParentCode().isEmpty())
            .collect(Collectors.toList());

        int count = 0;
        for (JwGridMeta meta : metas) {
            // 按根节点分类分别 TOPSIS
            for (JwIndicatorConfig root : roots) {
                List<String> leafCodes = getLeafCodesUnder(root.getIndicatorCode(), configMap);
                computeCategoryScore(meta, city, root.getIndicatorCode(), leafCodes, summaryMap);
            }
            // overall 用所有叶子
            List<JwIndicatorConfig> allLeaves = allGrid.stream()
                .filter(c -> c.isLeaf(configMap))
                .collect(Collectors.toList());
            List<String> allLeafCodes = allLeaves.stream()
                .map(JwIndicatorConfig::getIndicatorCode).collect(Collectors.toList());
            computeCategoryScore(meta, city, "overall", allLeafCodes, summaryMap);
            count++;
        }
        return count;
    }

    private List<String> getLeafCodesUnder(String rootCode, Map<String, JwIndicatorConfig> configMap) {
        Set<String> leafCodes = new LinkedHashSet<>();
        for (JwIndicatorConfig config : configMap.values()) {
            if (config.isLeaf(configMap) && isUnderRoot(config, rootCode, configMap)) {
                leafCodes.add(config.getIndicatorCode());
            }
        }
        return new ArrayList<>(leafCodes);
    }

    private boolean isUnderRoot(JwIndicatorConfig node, String rootCode, Map<String, JwIndicatorConfig> configMap) {
        String parent = node.getParentCode();
        while (parent != null && !parent.isEmpty()) {
            if (parent.equals(rootCode)) return true;
            JwIndicatorConfig p = configMap.get(parent);
            parent = p != null ? p.getParentCode() : null;
        }
        return false;
    }

    private void computeCategoryScore(JwGridMeta meta, String city, String category,
                                      List<String> indicatorCodes, Map<String, JwGridSummary> summaryMap) {
        List<Double> normValues = new ArrayList<>();
        List<Double> maxNorms = new ArrayList<>();
        List<Double> minNorms = new ArrayList<>();
        List<Double> weights = new ArrayList<>();

        for (String code : indicatorCodes) {
            JwGridSummary summary = summaryMap.get(code);
            if (summary == null || summary.getMaxNorm() == null || summary.getMinNorm() == null) {
                normValues.add(0.0); maxNorms.add(0.0); minNorms.add(0.0); weights.add(0.0);
                continue;
            }
            JwGridDataNormalized norm = gridDataNormalizedMapper.selectByGridAndIndicator(meta.getGridCode(), code);
            normValues.add(norm != null ? norm.getNormalizedValue() : 0.0);
            maxNorms.add(summary.getMaxNorm());
            minNorms.add(summary.getMinNorm());
            weights.add(summary.getActualWeight());
        }

        double[] result = TopsisCalculator.calcTopsis(normValues, maxNorms, minNorms, weights);

        JwGridScore score = new JwGridScore();
        score.setGridCode(meta.getGridCode());
        score.setCity(city);
        score.setScoreCategory(category);
        score.setPositiveDistance(result[0]);
        score.setNegativeDistance(result[1]);
        score.setSiteScore(result[2]);
        gridScoreMapper.upsertGridScore(score);
    }

    @Override
    public Map<String, Object> getCityDataStatus(String city) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("city", city);
        status.put("hasPoi", poiInfoMapper.selectByCity(city).size() > 0);
        status.put("hasPopulation", populationHeatMapper.selectDistinctGridCodesByCity(city).size() > 0);
        List<JwIndicatorConfig> leaves = indicatorConfigMapper.selectLeavesByType("grid");
        status.put("hasWeight", !leaves.isEmpty());
        status.put("gridCount", gridMetaMapper.selectByCity(city).size());
        status.put("hasScore", gridScoreMapper.countByCity(city) > 0);
        boolean ready = (boolean) status.get("hasPoi") && (boolean) status.get("hasPopulation") && (boolean) status.get("hasWeight");
        status.put("ready", ready);
        return status;
    }

    @Override
    public List<Map<String, Object>> getAllCityStatus() {
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
