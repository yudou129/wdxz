package com.ruoyi.jwmap.service.impl;

import com.ruoyi.jwmap.domain.JwBranchInfo;
import com.ruoyi.jwmap.domain.JwBranchScore;
import com.ruoyi.jwmap.domain.JwPeerBankInfo;
import com.ruoyi.jwmap.domain.JwPoiInfo;
import com.ruoyi.jwmap.domain.JwGridDataRaw;
import com.ruoyi.jwmap.mapper.*;
import com.ruoyi.jwmap.service.IJwDataQueryService;
import com.ruoyi.jwmap.util.JwGeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据查询服务实现 — 封装 Controller 中的 Mapper 调用和业务逻辑
 */
@Service
public class JwDataQueryServiceImpl implements IJwDataQueryService {

    @Autowired private JwPoiInfoMapper poiInfoMapper;
    @Autowired private JwPopulationHeatMapper populationHeatMapper;
    @Autowired private JwPeerBankInfoMapper peerBankInfoMapper;
    @Autowired private JwBranchInfoMapper branchInfoMapper;
    @Autowired private JwBranchScoreMapper branchScoreMapper;
    @Autowired private JwGridDataRawMapper gridDataRawMapper;

    // ===== POI =====

    @Override
    public List<JwPoiInfo> queryPoiList(String city) {
        JwPoiInfo query = new JwPoiInfo();
        if (city != null && !city.isEmpty()) query.setCity(city);
        return poiInfoMapper.selectPoiInfoList(query);
    }

    @Override
    public List<Map<String, Object>> queryPoiWithinRange(Map<String, Object> params) {
        String city = (String) params.get("city");
        String shapeType = (String) params.get("shapeType");
        double centerLng = toDouble(params.get("centerLng"));
        double centerLat = toDouble(params.get("centerLat"));
        double radius = toDouble(params.get("radius"));

        double halfSide = radius;
        double latDelta = halfSide / 111320.0;
        double lngDelta = halfSide / (111320.0 * Math.cos(Math.toRadians(centerLat)));

        List<JwPoiInfo> withinBounds = poiInfoMapper.selectWithinBounds(city,
            centerLng - lngDelta, centerLng + lngDelta,
            centerLat - latDelta, centerLat + latDelta);

        if ("circle".equals(shapeType)) {
            return withinBounds.stream()
                .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                .filter(p -> JwGeoUtils.haversine(centerLat, centerLng, p.getLatitude(), p.getLongitude()) * 1000 <= radius)
                .map(this::poiToMap)
                .collect(Collectors.toList());
        }
        return withinBounds.stream()
            .map(this::poiToMap)
            .collect(Collectors.toList());
    }

    // ===== Population =====

    @Override
    public List<String> queryDistinctGridCodes(String city) {
        return populationHeatMapper.selectDistinctGridCodesByCity(city);
    }

    // ===== Peer Bank =====

    @Override
    public List<JwPeerBankInfo> queryPeerBankList(String city) {
        return peerBankInfoMapper.selectByCity(city);
    }

    @Override
    public List<Map<String, Object>> queryPeerBankDistance(Long branchId, double radius) {
        JwBranchInfo branch = branchInfoMapper.selectJwBranchInfoById(branchId);
        if (branch == null) return Collections.emptyList();

        List<JwPeerBankInfo> peers = peerBankInfoMapper.selectByCity(branch.getCity());
        List<Map<String, Object>> result = new ArrayList<>();
        for (JwPeerBankInfo p : peers) {
            if (p.getLongitude() == null || p.getLatitude() == null) continue;
            double dist = JwGeoUtils.haversine(branch.getLatitude(), branch.getLongitude(),
                                                 p.getLatitude(), p.getLongitude());
            if (dist <= radius) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("orgName", p.getOrgName());
                m.put("bankName", p.getBankName());
                m.put("distance", Math.round(dist * 1000.0) / 1000.0);
                m.put("longitude", p.getLongitude());
                m.put("latitude", p.getLatitude());
                result.add(m);
            }
        }
        result.sort(Comparator.comparingDouble(m -> ((Number) m.get("distance")).doubleValue()));
        return result;
    }

    // ===== Analysis =====

    @Override
    public List<Map<String, Object>> queryQuadrantData(String city, Integer year) {
        return branchInfoMapper.selectQuadrantData(city, year);
    }

    @Override
    public List<Map<String, Object>> queryDimensionStats(String city, Integer year, String dimension) {
        List<JwBranchInfo> branches = branchInfoMapper.selectByCity(city);
        List<JwBranchScore> scores = branchScoreMapper.selectByCityAndYearAndCategory(city, year, "overall");
        Map<Long, Double> scoreMap = new HashMap<>();
        for (JwBranchScore s : scores) {
            if (s.getCategoryScore() != null) scoreMap.put(s.getBranchId(), s.getCategoryScore());
        }

        Map<String, List<Double>> groups = new LinkedHashMap<>();
        for (JwBranchInfo b : branches) {
            String key;
            switch (dimension) {
                case "branchType":
                    key = b.getBranchType() != null ? b.getBranchType() : "未知";
                    break;
                case "propertyRight":
                    key = b.getPropertyRight() != null ? b.getPropertyRight() : "未知";
                    break;
                default:
                    key = b.getDistrictName() != null ? b.getDistrictName() : "未知";
                    break;
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>());
            Double score = scoreMap.get(b.getBranchId());
            if (score != null) groups.get(key).add(score);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Double>> e : groups.entrySet()) {
            List<Double> vals = e.getValue();
            double sum = vals.stream().mapToDouble(Double::doubleValue).sum();
            double avg = vals.isEmpty() ? 0 : sum / vals.size();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("dimension", e.getKey());
            item.put("count", vals.size());
            item.put("avgScore", avg);
            item.put("maxScore", vals.isEmpty() ? 0 : Collections.max(vals));
            item.put("minScore", vals.isEmpty() ? 0 : Collections.min(vals));
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> queryThreeFocusRanking(String city, Integer year) {
        List<JwBranchInfo> branches = branchInfoMapper.selectByCity(city);
        Map<String, String> gridToBranch = new HashMap<>();
        List<String> gridCodes = new ArrayList<>();
        for (JwBranchInfo b : branches) {
            if (b.getGridCode() != null) {
                gridToBranch.put(b.getGridCode(), b.getSecondaryBranch());
                gridCodes.add(b.getGridCode());
            }
        }
        List<JwGridDataRaw> allGridData = gridCodes.isEmpty()
            ? new ArrayList<>() : gridDataRawMapper.selectByGridCodes(gridCodes);

        Map<String, Double> popScores = new LinkedHashMap<>();
        Map<String, Double> entScores = new LinkedHashMap<>();
        Map<String, Double> bizScores = new LinkedHashMap<>();
        for (JwGridDataRaw d : allGridData) {
            String code = d.getIndicatorCode();
            if (code == null) continue;
            double val = d.getIndicatorValue() != null ? d.getIndicatorValue().doubleValue() : 0;
            if (code.startsWith("pop_") || code.startsWith("age_") || code.startsWith("income_")
                || code.startsWith("consume_") || code.startsWith("education_")) {
                popScores.merge(d.getGridCode(), val, Double::sum);
            } else if (code.contains("公司") || code.contains("写字楼") || code.contains("企业")) {
                entScores.merge(d.getGridCode(), val, Double::sum);
            } else if (code.contains("商圈") || code.contains("购物") || code.contains("商业街")) {
                bizScores.merge(d.getGridCode(), val, Double::sum);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("population", buildFocusRanking(popScores, gridToBranch));
        result.put("enterprise", buildFocusRanking(entScores, gridToBranch));
        result.put("business", buildFocusRanking(bizScores, gridToBranch));
        return result;
    }

    // ===== 辅助方法 =====

    private double toDouble(Object obj) {
        return obj instanceof Number ? ((Number) obj).doubleValue() : 0;
    }

    private Map<String, Object> poiToMap(JwPoiInfo p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("poiId", p.getPoiId());
        m.put("poiName", p.getPoiName());
        m.put("poiType", p.getPoiType());
        m.put("address", p.getAddress());
        m.put("longitude", p.getLongitude());
        m.put("latitude", p.getLatitude());
        return m;
    }

    private List<Map<String, Object>> buildFocusRanking(Map<String, Double> scores, Map<String, String> gridToBranch) {
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(20)
            .map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("gridCode", e.getKey());
                m.put("branchName", gridToBranch.getOrDefault(e.getKey(), ""));
                m.put("score", e.getValue());
                return m;
            })
            .collect(Collectors.toList());
    }
}
