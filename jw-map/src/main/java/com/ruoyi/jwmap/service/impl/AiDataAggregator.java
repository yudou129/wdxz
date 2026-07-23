package com.ruoyi.jwmap.service.impl;

import com.ruoyi.jwmap.domain.*;
import com.ruoyi.jwmap.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ruoyi.jwmap.util.JwGeoUtils;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 数据聚合器
 * 从多个 Mapper 查询数据并组装为结构化上下文字符串
 */
@Component
public class AiDataAggregator {

    @Autowired
    private JwGridMetaMapper gridMetaMapper;
    @Autowired
    private JwGridScoreMapper gridScoreMapper;
    @Autowired
    private JwGridDataRawMapper gridDataRawMapper;
    @Autowired
    private JwBranchInfoMapper branchInfoMapper;
    @Autowired
    private JwBranchScoreMapper branchScoreMapper;
    @Autowired
    private JwBranchIndicatorMapper branchIndicatorMapper;
    @Autowired
    private JwIndicatorConfigMapper indicatorConfigMapper;
    @Autowired
    private JwPeerBankInfoMapper peerBankInfoMapper;
    @Autowired
    private JwPoiInfoMapper poiInfoMapper;
    @Autowired
    private JwGridSummaryMapper gridSummaryMapper;
    @Autowired
    private JwBranchSummaryMapper branchSummaryMapper;

    private static final DecimalFormat DF2 = new DecimalFormat("#0.00");
    private static final DecimalFormat DF4 = new DecimalFormat("#0.0000");

    /**
     * 按 branchId 查询 BranchInfo
     * 使用现有 selectJwBranchInfoList 方法，传入branchId作为筛选条件
     */
    private JwBranchInfo getBranchInfoByBranchId(Long branchId) {
        JwBranchInfo param = new JwBranchInfo();
        param.setBranchId(branchId);
        List<JwBranchInfo> list = branchInfoMapper.selectJwBranchInfoList(param);
        // 如果 param 的 branchId 未起作用（XML 不包含 branchId 过滤），find first match manually
        if (list != null && !list.isEmpty()) {
            for (JwBranchInfo b : list) {
                if (branchId.equals(b.getBranchId())) return b;
            }
        }
        return null;
    }

    // ==================== 通用查询工具方法 ====================

    /**
     * 获取所有指标配置的 Map
     */
    private Map<String, JwIndicatorConfig> getIndicatorMap() {
        List<JwIndicatorConfig> all = indicatorConfigMapper.selectJwIndicatorConfigList(new JwIndicatorConfig());
        Map<String, JwIndicatorConfig> map = new HashMap<>();
        for (JwIndicatorConfig c : all) {
            map.put(c.getIndicatorCode(), c);
        }
        return map;
    }

    /**
     * 获取指标的中文名（通过代码映射）
     */
    private String getIndicatorName(String code, Map<String, JwIndicatorConfig> configMap) {
        JwIndicatorConfig cfg = configMap.get(code);
        return cfg != null ? cfg.getIndicatorName() : code;
    }

    // ==================== 功能1&4：选址建议/网格分析 数据聚合 ====================

    public String buildGridContextData(String gridCode) {
        Map<String, JwIndicatorConfig> configMap = getIndicatorMap();
        JwGridMeta meta = gridMetaMapper.selectByGridCode(gridCode);
        if (meta == null) return "网格不存在：" + gridCode;
        String city = meta.getCity();
        String district = meta.getDistrict();

        StringBuilder sb = new StringBuilder();
        sb.append("## 网格基本信息\n");
        sb.append("- 网格编码：").append(gridCode).append("\n");
        sb.append("- 城市：").append(nullSafe(meta.getCity())).append("，区县：").append(nullSafe(meta.getDistrict())).append("\n");
        sb.append("- 中心坐标：(").append(meta.getLongitude()).append(", ").append(meta.getLatitude()).append(")\n");

        // TOPSIS 得分与三大支柱
        List<JwGridScore> allGridScores = gridScoreMapper.selectScoresByGridCode(gridCode);
        Map<String, JwGridScore> gridScoreMap = new HashMap<>();
        if (allGridScores != null) {
            for (JwGridScore gs : allGridScores) {
                gridScoreMap.put(gs.getScoreCategory(), gs);
            }
        }
        JwGridScore overallScore = gridScoreMap.get("overall");
        if (overallScore != null) {
            sb.append("- 综合选址得分：").append(DF4.format(overallScore.getSiteScore())).append("/1.0\n");
        }

        // 三大支柱得分
        sb.append("\n## 三支柱得分\n");
        List<String> pillars = Arrays.asList("pop", "grid_biz", "ent");
        String[] pillarNames = {"人口聚集", "商业聚集", "企业聚集"};
        for (int i = 0; i < pillars.size(); i++) {
            JwGridScore pScore = gridScoreMap.get(pillars.get(i));
            if (pScore != null) {
                sb.append("- ").append(pillarNames[i]).append("：").append(DF4.format(pScore.getSiteScore())).append("/1.0\n");
            }
        }

        // 全市排名
        if (city != null) {
            List<JwGridScore> cityScores = gridScoreMapper.selectByCity(city);
            if (cityScores != null && !cityScores.isEmpty()) {
                int rankInCity = 1;
                for (JwGridScore s : cityScores) {
                    if (gridCode.equals(s.getGridCode())) break;
                    rankInCity++;
                }
                sb.append("\n## 全市排名\n");
                sb.append("- 全市共 ").append(cityScores.size()).append(" 个网格\n");
                sb.append("- 该网格综合得分排名：第 ").append(rankInCity).append(" 名\n");
                sb.append("- 全市最高得分：").append(DF4.format(cityScores.get(0).getSiteScore())).append("/1.0\n");
                sb.append("- 全市平均得分：").append(DF4.format(
                        cityScores.stream().mapToDouble(JwGridScore::getSiteScore).average().orElse(0))).append("/1.0\n");
            }
        }

        // 人口指标 — 后端预计算摘要
        List<JwGridDataRaw> rawData = gridDataRawMapper.selectByGridCode(gridCode);
        if (rawData != null && !rawData.isEmpty()) {
            sb.append("\n## 人口规模\n");

            // 人口规模摘要
            double totalResident = 0;
            for (JwGridDataRaw d : rawData) {
                if (d.getIndicatorValue() == null || d.getIndicatorValue() == 0) continue;
                if (isDescendant(d.getIndicatorCode(), "grid_pop_resident", configMap)) {
                    String name = getIndicatorName(d.getIndicatorCode(), configMap);
                    sb.append("- ").append(name).append("：").append(DF2.format(d.getIndicatorValue())).append("\n");
                    // 求和人口总数（取第一个较大的值作为总人口）
                    if (totalResident == 0) totalResident = d.getIndicatorValue();
                }
            }

            // 年龄结构摘要
            sb.append("\n## 年龄结构\n");
            double youngTotal = 0, midTotal = 0, oldTotal = 0, ageTotal = 0;
            for (JwGridDataRaw d : rawData) {
                if (d.getIndicatorValue() == null || d.getIndicatorValue() == 0) continue;
                if (isDescendant(d.getIndicatorCode(), "grid_pop_age", configMap)) {
                    String name = getIndicatorName(d.getIndicatorCode(), configMap);
                    sb.append("- ").append(name).append("：").append(DF2.format(d.getIndicatorValue())).append("\n");
                }
            }

            // 收入结构摘要（后端算好分层）
            sb.append("\n## 收入水平\n");
            double lowIncome = 0, midIncome = 0, highIncome = 0;
            for (JwGridDataRaw d : rawData) {
                if (d.getIndicatorValue() == null || d.getIndicatorValue() == 0) continue;
                if (isDescendant(d.getIndicatorCode(), "grid_pop_income", configMap)) {
                    String name = getIndicatorName(d.getIndicatorCode(), configMap);
                    sb.append("- ").append(name).append("：").append(DF2.format(d.getIndicatorValue())).append("人\n");
                    String code = d.getIndicatorCode();
                    if (code.contains("2499") || code.contains("below") || code.contains("2500") || code.contains("3999")) {
                        lowIncome += d.getIndicatorValue();
                    } else if (code.contains("4000") || code.contains("5999")) {
                        midIncome += d.getIndicatorValue();
                    } else {
                        highIncome += d.getIndicatorValue();
                    }
                }
            }
            double totalIncome = lowIncome + midIncome + highIncome;
            if (totalIncome > 0) {
                sb.append("- 低收入（≤3999元）占比：").append(DF2.format(lowIncome / totalIncome * 100)).append("%\n");
                sb.append("- 中等收入（4000~5999元）占比：").append(DF2.format(midIncome / totalIncome * 100)).append("%\n");
                sb.append("- 高收入（≥6000元）占比：").append(DF2.format(highIncome / totalIncome * 100)).append("%\n");
            }
        }

        // 商业环境摘要（后端预计算）
        if (rawData != null && !rawData.isEmpty()) {
            sb.append("\n## 商业环境\n");
            // 主要POI类型TOP3
            Map<String, Double> poiCounts = new LinkedHashMap<>();
            for (JwGridDataRaw d : rawData) {
                if (d.getIndicatorValue() == null || d.getIndicatorValue() == 0) continue;
                if (isDescendant(d.getIndicatorCode(), "grid_biz_poi", configMap)) {
                    String name = getIndicatorName(d.getIndicatorCode(), configMap);
                    poiCounts.put(name, d.getIndicatorValue());
                }
            }
            if (!poiCounts.isEmpty()) {
                // 取TOP3
                List<Map.Entry<String, Double>> sortedPoi = new ArrayList<>(poiCounts.entrySet());
                sortedPoi.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
                sb.append("- 主要设施类型：");
                int limit = 0;
                for (Map.Entry<String, Double> e : sortedPoi) {
                    if (limit >= 3) break;
                    sb.append(e.getKey()).append(" ").append(DF2.format(e.getValue())).append("个");
                    if (limit < Math.min(3, sortedPoi.size()) - 1) sb.append("、");
                    limit++;
                }
                sb.append("\n");
                long totalPoi = Math.round(poiCounts.values().stream().mapToDouble(Double::doubleValue).sum());
                sb.append("- POI总量：约").append(totalPoi).append("个\n");
            }

            // 消费能力
            for (JwGridDataRaw d : rawData) {
                if (d.getIndicatorValue() == null || d.getIndicatorValue() == 0) continue;
                if (isDescendant(d.getIndicatorCode(), "grid_biz_consume", configMap)) {
                    String name = getIndicatorName(d.getIndicatorCode(), configMap);
                    sb.append("- ").append(name).append("：").append(DF2.format(d.getIndicatorValue())).append("\n");
                }
            }
        }

        // 企业指标摘要
        if (rawData != null && !rawData.isEmpty()) {
            sb.append("\n## 企业聚集\n");
            boolean hasEnt = false;
            for (JwGridDataRaw d : rawData) {
                if (d.getIndicatorValue() == null || d.getIndicatorValue() == 0) continue;
                JwIndicatorConfig cfg = configMap.get(d.getIndicatorCode());
                if (cfg == null) continue;
                String parent = cfg.getParentCode();
                if ("ent".equals(parent) || "grid_ent_company".equals(parent) || "grid_ent_office".equals(parent)) {
                    sb.append("- ").append(getIndicatorName(d.getIndicatorCode(), configMap)).append("：").append(DF2.format(d.getIndicatorValue())).append("\n");
                    hasEnt = true;
                }
            }
            if (!hasEnt) sb.append("- 企业数据较少\n");
        }

        // 网格内同业统计
        if (city != null) {
            JwPeerBankInfo peerParam = new JwPeerBankInfo();
            peerParam.setGridCode(gridCode);
            List<JwPeerBankInfo> gridPeers = peerBankInfoMapper.selectJwPeerBankInfoList(peerParam);
            sb.append("\n## 同业竞争\n");
            if (gridPeers != null && !gridPeers.isEmpty()) {
                sb.append("- 网格内同业网点共 ").append(gridPeers.size()).append(" 家\n");
                for (JwPeerBankInfo p : gridPeers) {
                    sb.append("  - ").append(nullSafe(p.getBankName())).append("：").append(nullSafe(p.getOrgName())).append("\n");
                }
            } else {
                sb.append("- 网格内无同业网点\n");
            }
        }

        // 与相邻网格的对比（后端预计算均值对比）
        // 查找附近网格的基准：优先用 city，其次用 district，最后查全部
        List<JwGridMeta> neighborCandidates = null;
        if (city != null) {
            neighborCandidates = gridMetaMapper.selectByCity(city);
        }
        if (neighborCandidates == null || neighborCandidates.size() <= 1) {
            if (district != null) {
                JwGridMeta param = new JwGridMeta();
                param.setDistrict(district);
                neighborCandidates = gridMetaMapper.selectJwGridMetaList(param);
            }
        }
        if (neighborCandidates == null || neighborCandidates.size() <= 1) {
            neighborCandidates = gridMetaMapper.selectJwGridMetaList(new JwGridMeta());
        }
        if (neighborCandidates != null && neighborCandidates.size() > 1) {
            JwGridMeta currentMeta = neighborCandidates.stream()
                    .filter(m -> gridCode.equals(m.getGridCode())).findFirst().orElse(null);
                if (currentMeta != null && currentMeta.getLatitude() != null) {
                    // 找最近8个网格
                    List<NeighborGrid> neighbors = new ArrayList<>();
                    for (JwGridMeta m : neighborCandidates) {
                        if (gridCode.equals(m.getGridCode())) continue;
                        if (m.getLatitude() == null || m.getLongitude() == null) continue;
                        double dist = JwGeoUtils.haversine(currentMeta.getLatitude(), currentMeta.getLongitude(), m.getLatitude(), m.getLongitude());
                        neighbors.add(new NeighborGrid(m.getGridCode(), m.getDistrict(), dist));
                    }
                    neighbors.sort(Comparator.comparingDouble(n -> n.distance));
                    if (neighbors.isEmpty()) {
                        sb.append("\n## 与相邻网格对比\n- 未找到相邻网格（附近无坐标完整的网格）\n");
                    } else {
                    int neighborCount = Math.min(8, neighbors.size());
                    List<NeighborGrid> topNeighbors = neighbors.subList(0, neighborCount);
                    List<String> neighborCodes = topNeighbors.stream().map(n -> n.gridCode).collect(Collectors.toList());

                    // 查相邻网格的评分和数据
                    List<JwGridScore> neighborScores = gridScoreMapper.selectScoresByGridCodes(neighborCodes);
                    Map<String, Double> neighborOverallMap = new HashMap<>();
                    Map<String, Double> neighborPopMap = new HashMap<>();
                    Map<String, Double> neighborBizMap = new HashMap<>();
                    if (neighborScores != null) {
                        for (JwGridScore ns : neighborScores) {
                            if ("overall".equals(ns.getScoreCategory())) neighborOverallMap.put(ns.getGridCode(), ns.getSiteScore());
                            else if ("pop".equals(ns.getScoreCategory())) neighborPopMap.put(ns.getGridCode(), ns.getSiteScore());
                            else if ("grid_biz".equals(ns.getScoreCategory())) neighborBizMap.put(ns.getGridCode(), ns.getSiteScore());
                        }
                    }

                    List<JwGridDataRaw> neighborRawData = gridDataRawMapper.selectByGridCodes(neighborCodes);
                    // 计算相邻网格均值
                    double sumPop = 0, sumLowIncome = 0, sumMidIncome = 0, sumHighIncome = 0, sumPoi = 0;
                    double sumOverall = 0, sumBiz = 0;
                    int validCount = neighborCodes.size();
                    for (String nc : neighborCodes) {
                        Double os = neighborOverallMap.get(nc);
                        if (os != null) sumOverall += os;
                        Double bs = neighborBizMap.get(nc);
                        if (bs != null) sumBiz += bs;

                        if (neighborRawData != null) {
                            for (JwGridDataRaw d : neighborRawData) {
                                if (!nc.equals(d.getGridCode())) continue;
                                if (d.getIndicatorValue() == null || d.getIndicatorValue() == 0) continue;
                                if (isDescendant(d.getIndicatorCode(), "grid_pop_resident", configMap)) {
                                    sumPop += d.getIndicatorValue();
                                }
                                if (isDescendant(d.getIndicatorCode(), "grid_biz_poi", configMap)) {
                                    sumPoi += d.getIndicatorValue();
                                }
                                String code = d.getIndicatorCode();
                                if (isDescendant(code, "grid_pop_income", configMap)) {
                                    if (code.contains("2499") || code.contains("below") || code.contains("2500") || code.contains("3999")) {
                                        sumLowIncome += d.getIndicatorValue();
                                    } else if (code.contains("4000") || code.contains("5999")) {
                                        sumMidIncome += d.getIndicatorValue();
                                    } else {
                                        sumHighIncome += d.getIndicatorValue();
                                    }
                                }
                            }
                        }
                    }

                    // 当前网格的数据
                    double curPop = 0, curLowIncome = 0, curMidIncome = 0, curHighIncome = 0, curPoi = 0;
                    if (rawData != null) {
                        for (JwGridDataRaw d : rawData) {
                            if (d.getIndicatorValue() == null || d.getIndicatorValue() == 0) continue;
                            if (isDescendant(d.getIndicatorCode(), "grid_pop_resident", configMap)) {
                                curPop += d.getIndicatorValue();
                            }
                            if (isDescendant(d.getIndicatorCode(), "grid_biz_poi", configMap)) {
                                curPoi += d.getIndicatorValue();
                            }
                            String code = d.getIndicatorCode();
                            if (isDescendant(code, "grid_pop_income", configMap)) {
                                if (code.contains("2499") || code.contains("below") || code.contains("2500") || code.contains("3999")) {
                                    curLowIncome += d.getIndicatorValue();
                                } else if (code.contains("4000") || code.contains("5999")) {
                                    curMidIncome += d.getIndicatorValue();
                                } else {
                                    curHighIncome += d.getIndicatorValue();
                                }
                            }
                        }
                    }
                    double curOverall = overallScore != null ? overallScore.getSiteScore() : 0;

                    sb.append("\n## 与相邻网格对比\n");
                    sb.append("以下是与本网格最近的 ").append(neighborCount).append(" 个相邻网格的三聚集得分明细：\n\n");
                    for (int ni = 0; ni < topNeighbors.size(); ni++) {
                        NeighborGrid ng = topNeighbors.get(ni);
                        Double nOverall = neighborOverallMap.getOrDefault(ng.gridCode, 0.0);
                        Double nPop = neighborPopMap.getOrDefault(ng.gridCode, 0.0);
                        Double nBiz = neighborBizMap.getOrDefault(ng.gridCode, 0.0);
                        sb.append(ni + 1).append(". 网格 ").append(ng.gridCode).append("（").append(ng.district).append("）：");
                        sb.append("综合 ").append(DF4.format(nOverall)).append(" | 人口 ").append(DF4.format(nPop)).append(" | 商业 ").append(DF4.format(nBiz));
                        if (ng.distance < 1) {
                            sb.append(" | 距本网格约").append(DF2.format(ng.distance * 1000)).append("m");
                        } else {
                            sb.append(" | 距本网格约").append(DF2.format(ng.distance)).append("km");
                        }
                        sb.append("\n");
                    }
                    sb.append("\n#### 本网格 vs 相邻均值对比\n");
                    if (curPop > 0 && sumPop > 0) {
                        double avgPop = sumPop / validCount;
                        sb.append("- 人口规模：当前 ").append(DF2.format(curPop)).append(" | 相邻均值 ").append(DF2.format(avgPop)).append(" | 差值 ").append(formatPercent(curPop, avgPop)).append("\n");
                    }
                    double curLowTotal = curLowIncome + curMidIncome + curHighIncome;
                    double avgLowTotal = (sumLowIncome + sumMidIncome + sumHighIncome) / validCount;
                    if (curLowTotal > 0 && avgLowTotal > 0) {
                        sb.append("- 低收入人群：当前 ").append(DF2.format(curLowIncome)).append("人（占比").append(DF2.format(curLowIncome/curLowTotal*100)).append("%）| 相邻均值 ").append(DF2.format(sumLowIncome/validCount)).append("人（占比").append(DF2.format(sumLowIncome/avgLowTotal/validCount*100)).append("%）\n");
                        sb.append("- 中高收入人群：当前 ").append(DF2.format(curMidIncome + curHighIncome)).append("人（占比").append(DF2.format((curMidIncome+curHighIncome)/curLowTotal*100)).append("%）| 相邻均值 ").append(DF2.format((sumMidIncome+sumHighIncome)/validCount)).append("人\n");
                    }
                    if (curPoi > 0 && sumPoi > 0) {
                        sb.append("- 商业配套POI：当前 约").append(Math.round(curPoi)).append("个 | 相邻均值 约").append(Math.round(sumPoi/validCount)).append("个\n");
                    }
                    sb.append("- 综合得分：当前 ").append(DF4.format(curOverall)).append(" | 相邻均值 ").append(DF4.format(sumOverall/validCount)).append(" | 差值 ").append(formatPercent(curOverall, sumOverall/validCount)).append("\n");
                }
            } // end-else: neighbors not empty
            }

        return sb.toString();
    }

    private boolean isDescendant(String code, String ancestorCode, Map<String, JwIndicatorConfig> configMap) {
        JwIndicatorConfig cfg = configMap.get(code);
        if (cfg == null) return false;
        String parent = cfg.getParentCode();
        if (parent == null) return false;
        if (parent.equals(ancestorCode)) return true;
        return isDescendant(parent, ancestorCode, configMap);
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    // ==================== 功能2：网点分析 数据聚合 ====================

    public String buildBranchContextData(Long branchId, Integer year) {
        JwBranchInfo info = getBranchInfoByBranchId(branchId);
        if (info == null) return "网点不存在：" + branchId;

        StringBuilder sb = new StringBuilder();
        sb.append("## 网点基本信息\n");
        sb.append("- 网点名称：").append(nullSafe(info.getSecondaryBranch())).append("\n");
        sb.append("- 所属一级支行：").append(nullSafe(info.getPrimaryBranch())).append("\n");
        sb.append("- 网点类型：").append(nullSafe(info.getBranchType())).append("\n");
        sb.append("- 所属区县：").append(nullSafe(info.getDistrictName())).append("\n");
        sb.append("- 地址：").append(nullSafe(info.getAddress())).append("\n");

        sb.append("\n## 人员配置\n");
        sb.append("- 总人数：").append(info.getTotalStaff() != null ? info.getTotalStaff() : 0).append("\n");
        sb.append("- 个人客户经理：").append(info.getPersonalManager() != null ? info.getPersonalManager() : 0).append("人\n");
        sb.append("- 对公客户经理：").append(info.getCorporateManager() != null ? info.getCorporateManager() : 0).append("人\n");
        sb.append("- 柜员：").append(info.getCounterStaff() != null ? info.getCounterStaff() : 0).append("人\n");
        sb.append("- 大堂人员：").append(info.getLobbyStaff() != null ? info.getLobbyStaff() : 0).append("人\n");

        sb.append("\n## 物理条件\n");
        sb.append("- 总面积：").append(info.getTotalArea() != null ? info.getTotalArea() : 0).append("平方米\n");
        sb.append("- 现金柜台：").append(info.getCashCounter() != null ? info.getCashCounter() : 0).append("个\n");
        sb.append("- 非现金柜台：").append(info.getNonCashCounter() != null ? info.getNonCashCounter() : 0).append("个\n");
        sb.append("- 产权类型：").append(nullSafe(info.getPropertyRight())).append("\n");

        // TOPSIS 五维度得分（后端判断强弱维度）
        List<JwBranchScore> scores = branchScoreMapper.selectByBranchIdsAndYear(Collections.singletonList(branchId), year);
        sb.append("\n## 效能评估\n");
        JwBranchScore overallScoreBranch = null;
        String strongDim = "", weakDim = "";
        double strongVal = -1, weakVal = 999;
        for (JwBranchScore s : scores) {
            String catName = getScoreCategoryName(s.getScoreCategory());
            if ("overall".equals(s.getScoreCategory())) {
                overallScoreBranch = s;
                sb.append("- 综合排名：第").append(s.getRankNum() != null ? s.getRankNum() : "N/A");
                // 查询该城市该年份的总网点数
                List<JwBranchScore> cityScores = branchScoreMapper.selectByCityAndYearAndCategory(
                        info.getCity() != null ? info.getCity() : "", year, "overall");
                if (cityScores != null) sb.append("/共").append(cityScores.size()).append("家网点");
                sb.append("\n");
            } else {
                sb.append("- ").append(catName).append("：").append(DF4.format(s.getCategoryScore()));
                if (s.getRankNum() != null) sb.append("（排名第").append(s.getRankNum()).append("）");
                sb.append("\n");
                if (s.getCategoryScore() != null) {
                    if (s.getCategoryScore() > strongVal) { strongVal = s.getCategoryScore(); strongDim = catName; }
                    if (s.getCategoryScore() < weakVal) { weakVal = s.getCategoryScore(); weakDim = catName; }
                }
            }
        }
        if (!strongDim.isEmpty() && !weakDim.isEmpty()) {
            sb.append("- 优势维度：").append(strongDim).append("\n");
            sb.append("- 薄弱维度：").append(weakDim).append("\n");
        }

        // 关键经营指标摘要（只输出与市均对比突出的）
        sb.append("\n## 经营指标摘要\n");
        List<JwBranchIndicator> indicators = branchIndicatorMapper.selectByBranchAndYear(branchId, year, "数据计算表");
        if (indicators != null) {
            Map<String, JwIndicatorConfig> indicatorCfgMap = getIndicatorMap();
            indicators.sort((a, b) -> {
                JwIndicatorConfig ca = indicatorCfgMap.get(a.getIndicatorCode());
                JwIndicatorConfig cb = indicatorCfgMap.get(b.getIndicatorCode());
                double wa = ca != null && ca.getCalculationWeight() != null ? ca.getCalculationWeight() : 0;
                double wb = cb != null && cb.getCalculationWeight() != null ? cb.getCalculationWeight() : 0;
                return Double.compare(wb, wa);
            });
            // 只输出权重最高的5个核心指标
            int count = 0;
            for (JwBranchIndicator ind : indicators) {
                if (count >= 5) break;
                String name = getIndicatorName(ind.getIndicatorCode(), indicatorCfgMap);
                double val = ind.getIndicatorValue() != null ? ind.getIndicatorValue() : 0;
                sb.append("- ").append(name).append("：").append(DF2.format(val));
                // 与全市均值对比（如果有 summary 数据）
                JwBranchSummary summary = null;
                try {
                    JwBranchSummary param = new JwBranchSummary();
                    param.setCity(info.getCity());
                    param.setDataYear(year);
                    param.setIndicatorCode(ind.getIndicatorCode());
                    List<JwBranchSummary> summaries = branchSummaryMapper.selectJwBranchSummaryList(param);
                    if (summaries != null && !summaries.isEmpty()) {
                        summary = summaries.get(0);
                    }
                } catch (Exception e) { /* 忽略查询异常 */ }
                if (summary != null && summary.getMaxValue() != null && summary.getMaxValue() > 0 && val > 0) {
                    double ratio = val / summary.getMaxValue();
                    if (ratio > 0.8) sb.append("（优于市均）");
                    else if (ratio > 0.5) sb.append("（接近市均）");
                    else sb.append("（低于市均）");
                }
                sb.append("\n");
                count++;
            }
        }

        return sb.toString();
    }

    /**
     * 从网格评分列表中提取综合得分
     */
    private double getOverallScore(List<JwGridScore> scores) {
        if (scores == null) return 0;
        return scores.stream()
                .filter(s -> "overall".equals(s.getScoreCategory()))
                .mapToDouble(JwGridScore::getSiteScore)
                .findFirst().orElse(0);
    }

    /**
     * 相邻网格辅助类
     */
    private static class NeighborGrid {
        final String gridCode;
        final String district;
        final double distance;
        NeighborGrid(String gridCode, String district, double distance) {
            this.gridCode = gridCode;
            this.district = district;
            this.distance = distance;
        }
    }

    private static class BlankCandidate {
        final String code;
        final double score;
        final double distance;
        final JwGridMeta meta;
        BlankCandidate(String code, double score, double distance, JwGridMeta meta) {
            this.code = code;
            this.score = score;
            this.distance = distance;
            this.meta = meta;
        }
    }

    /**
     * 计算差值百分比（如 +12.3%、-5.1%）
     */
    private String formatPercent(double current, double avg) {
        if (avg == 0) return "N/A";
        double pct = (current - avg) / avg * 100;
        return (pct >= 0 ? "+" : "") + DF2.format(pct) + "%";
    }

    private String getScoreCategoryName(String category) {
        Map<String, String> names = new HashMap<>();
        names.put("overall", "综合得分");
        names.put("revenue", "经营情况");
        names.put("indicator", "业绩表现");
        names.put("customer", "客户发展");
        names.put("operation", "业务运营");
        return names.getOrDefault(category, category);
    }

    // ==================== 功能3：多网点对比 数据聚合 ====================

    public String buildComparisonContextData(List<Long> branchIds, String city, Integer year) {
        StringBuilder sb = new StringBuilder();
        Map<String, JwIndicatorConfig> configMap = getIndicatorMap();
        List<JwBranchInfo> infos = new ArrayList<>();
        for (Long id : branchIds) {
            JwBranchInfo info = getBranchInfoByBranchId(id);
            if (info != null) infos.add(info);
        }

        sb.append("## 对比网点列表（").append(infos.size()).append("个）\n");
        sb.append("网点名称 | 综合得分 | 排名 | 经营情况 | 业绩表现 | 客户发展 | 业务运营 | 总人数\n");
        for (JwBranchInfo info : infos) {
            List<JwBranchScore> scores = branchScoreMapper.selectByBranchIdsAndYear(Collections.singletonList(info.getBranchId()), year);
            Map<String, JwBranchScore> scoreMap = scores.stream().collect(Collectors.toMap(JwBranchScore::getScoreCategory, s -> s, (a, b) -> a));
            JwBranchScore overall = scoreMap.get("overall");
            JwBranchScore rev = scoreMap.get("revenue");
            JwBranchScore ind = scoreMap.get("indicator");
            JwBranchScore cust = scoreMap.get("customer");
            JwBranchScore oper = scoreMap.get("operation");
            sb.append(nullSafe(info.getSecondaryBranch())).append(" | ")
              .append(overall != null ? DF4.format(overall.getCategoryScore()) : "N/A").append(" | ")
              .append(overall != null && overall.getRankNum() != null ? overall.getRankNum() : "N/A").append(" | ")
              .append(rev != null ? DF4.format(rev.getCategoryScore()) : "N/A").append(" | ")
              .append(ind != null ? DF4.format(ind.getCategoryScore()) : "N/A").append(" | ")
              .append(cust != null ? DF4.format(cust.getCategoryScore()) : "N/A").append(" | ")
              .append(oper != null ? DF4.format(oper.getCategoryScore()) : "N/A").append(" | ")
              .append(info.getTotalStaff() != null ? info.getTotalStaff() : 0).append("\n");
        }

        // 关键指标对比
        sb.append("\n## 关键指标对比\n");
        String[] coreCodes = {"per_capita", "per_area", "avg_balance_4", "avg_growth_4", "per_capita600cust", "per_areacounter"};
        String[] coreNames = {"人均营业收入", "每单位面积营业收入", "户日均余额", "日均增幅", "人均高净值客户数", "每单位面积柜台工作量"};
        for (int i = 0; i < coreCodes.length; i++) {
            String code = coreCodes[i];
            sb.append("- ").append(coreNames[i]).append("：\n");
            for (JwBranchInfo info : infos) {
                List<JwBranchIndicator> indicators = branchIndicatorMapper.selectByBranchAndYear(info.getBranchId(), year, "数据计算表");
                double val = 0;
                if (indicators != null) {
                    for (JwBranchIndicator ind : indicators) {
                        if (code.equals(ind.getIndicatorCode()) && ind.getIndicatorValue() != null) {
                            val = ind.getIndicatorValue();
                            break;
                        }
                    }
                }
                sb.append("  - ").append(nullSafe(info.getSecondaryBranch())).append("：").append(DF2.format(val)).append("\n");
            }
        }

        return sb.toString();
    }

    // ==================== 功能7：四象限分析 数据聚合 ====================

    public String buildQuadrantContextData(String city, Integer year) {
        StringBuilder sb = new StringBuilder();
        List<JwBranchScore> allScores = branchScoreMapper.selectByCityAndYearAndCategory(city, year, "overall");
        if (allScores == null || allScores.isEmpty()) {
            return "该城市暂无网点得分数据";
        }

        sb.append("## 四象限分布（").append(city).append("市 ").append(year).append("年）\n");
        sb.append("- 纳入分析网点总数：").append(allScores.size()).append("\n");

        // 计算中位数
        List<JwBranchScore> sortedByScore = new ArrayList<>(allScores);
        sortedByScore.sort((a, b) -> Double.compare(a.getCategoryScore(), b.getCategoryScore()));
        int mid = sortedByScore.size() / 2;
        double medianScore = sortedByScore.get(mid).getCategoryScore();
        sb.append("- 效能得分中位数：").append(DF4.format(medianScore)).append("\n");

        // 按象限分组
        int q1 = 0, q2 = 0, q3 = 0, q4 = 0;
        for (JwBranchScore s : allScores) {
            // 简单四象限：用排名中位数为界
            int rank = s.getRankNum() != null ? s.getRankNum() : 50;
            if (rank <= sortedByScore.size() / 2) {
                q1++;
            } else {
                q4++;
            }
        }
        sb.append("\n## 各象限概况\n");
        sb.append("- 高分段（效能排名靠前）：").append(q1).append("个\n");
        sb.append("- 低分段（效能排名靠后）：").append(q4).append("个\n");

        // 低效能网点详情（升序排列 index 0 开始 = 得分最低）
        sb.append("\n## 低效能网点详情\n");
        int limit = 0;
        for (int i = 0; i < sortedByScore.size() && limit < 5; i++) {
            JwBranchScore bs = sortedByScore.get(i);
            JwBranchInfo info = getBranchInfoByBranchId(bs.getBranchId());
            sb.append("- ").append(info != null ? nullSafe(info.getSecondaryBranch()) : "未知网点")
              .append(" | 效能得分：").append(DF4.format(bs.getCategoryScore()))
              .append(" | 排名第").append(bs.getRankNum() != null ? bs.getRankNum() : "N/A").append("\n");
            limit++;
        }

        return sb.toString();
    }


    // ==================== 功能8：迁址建议 数据聚合 ====================

    public String buildRelocationContextData(Long branchId, Integer year, String city) {
        JwBranchInfo info = getBranchInfoByBranchId(branchId);
        if (info == null) return "网点不存在：" + branchId;

        StringBuilder sb = new StringBuilder();
        String gridCode = info.getGridCode();
        Map<String, JwIndicatorConfig> configMap = getIndicatorMap();
        List<JwGridDataRaw> rawData = gridCode != null ? gridDataRawMapper.selectByGridCode(gridCode) : null;

        // ========== 第一部分：网格数据 ==========
        sb.append("## 当前网格信息\n");
        if (gridCode != null) {
            JwGridMeta meta = gridMetaMapper.selectByGridCode(gridCode);
            if (meta != null) {
                sb.append("- 网格编码：").append(gridCode).append("\n");
                sb.append("- 区县：").append(nullSafe(meta.getDistrict())).append("\n");
                sb.append("- 中心坐标：(").append(meta.getLongitude()).append(", ").append(meta.getLatitude()).append(")\n");

                // 网格评分
                List<JwGridScore> allGridScores = gridScoreMapper.selectScoresByGridCode(gridCode);
                Map<String, JwGridScore> gsMap = new HashMap<>();
                if (allGridScores != null) {
                    for (JwGridScore gs : allGridScores) gsMap.put(gs.getScoreCategory(), gs);
                }
                JwGridScore overall = gsMap.get("overall");
                if (overall != null) {
                    sb.append("- 综合选址得分：").append(DF4.format(overall.getSiteScore())).append("/1.0\n");
                }
                // 三支柱
                List<String> pillars = Arrays.asList("pop", "grid_biz", "ent");
                String[] pillarNames = {"人口聚集", "商业聚集", "企业聚集"};
                for (int i = 0; i < pillars.size(); i++) {
                    JwGridScore ps = gsMap.get(pillars.get(i));
                    if (ps != null) sb.append("- ").append(pillarNames[i]).append("：").append(DF4.format(ps.getSiteScore())).append("/1.0\n");
                }

                // 全市排名
                List<JwGridScore> cityOverall = gridScoreMapper.selectByCity(city);
                if (cityOverall != null && !cityOverall.isEmpty()) {
                    int rank = 1;
                    for (JwGridScore s : cityOverall) {
                        if (gridCode.equals(s.getGridCode())) break;
                        rank++;
                    }
                    sb.append("- 全市排名：第 ").append(rank).append(" / ").append(cityOverall.size()).append(" 名\n");
                }

                // 同区对比
                if (meta.getDistrict() != null) {
                    List<JwGridScore> districtScores = gridScoreMapper.selectByCityAndDistrict(city, meta.getDistrict());
                    if (districtScores != null && districtScores.size() > 1) {
                        sb.append("- 同区共 ").append(districtScores.size()).append(" 个网格\n");
                        double avg = districtScores.stream().mapToDouble(JwGridScore::getSiteScore).average().orElse(0);
                        sb.append("- 同区平均得分：").append(DF4.format(avg)).append("/1.0\n");
                    }
                }

                // 网格内同业
                JwPeerBankInfo peerParam = new JwPeerBankInfo();
                peerParam.setGridCode(gridCode);
                List<JwPeerBankInfo> gridPeers = peerBankInfoMapper.selectJwPeerBankInfoList(peerParam);
                if (gridPeers != null && !gridPeers.isEmpty()) {
                    sb.append("## 网格内同业网点\n");
                    for (JwPeerBankInfo p : gridPeers) {
                        sb.append("- ").append(nullSafe(p.getOrgName())).append("（").append(nullSafe(p.getBankName())).append("）\n");
                    }
                }

                // POI 统计
                if (meta.getWestLongitude() != null && meta.getEastLongitude() != null
                        && meta.getSouthLatitude() != null && meta.getNorthLatitude() != null) {
                    List<JwPoiInfo> poiList = poiInfoMapper.selectWithinBounds(city,
                            meta.getWestLongitude(), meta.getEastLongitude(),
                            meta.getSouthLatitude(), meta.getNorthLatitude());
                    if (poiList != null && !poiList.isEmpty()) {
                        Map<String, Long> poiGroup = poiList.stream()
                                .filter(p -> p.getPoiType() != null)
                                .collect(Collectors.groupingBy(JwPoiInfo::getPoiType, Collectors.counting()));
                        sb.append("\n## POI 设施统计\n");
                        poiGroup.forEach((type, cnt) ->
                                sb.append("- ").append(type).append("：").append(cnt).append("个\n"));
                    }
                }

                // 人口指标摘要（优化格式）
                if (rawData != null && !rawData.isEmpty()) {
                    sb.append("\n## 人口规模\n");
                    for (JwGridDataRaw d : rawData) {
                        if (d.getIndicatorValue() == null || d.getIndicatorValue() == 0) continue;
                        if (isDescendant(d.getIndicatorCode(), "grid_pop_resident", configMap)) {
                            sb.append("- ").append(getIndicatorName(d.getIndicatorCode(), configMap)).append("：").append(DF2.format(d.getIndicatorValue())).append("\n");
                        }
                    }
                    sb.append("\n## 年龄结构\n");
                    for (JwGridDataRaw d : rawData) {
                        if (d.getIndicatorValue() == null || d.getIndicatorValue() == 0) continue;
                        if (isDescendant(d.getIndicatorCode(), "grid_pop_age", configMap)) {
                            sb.append("- ").append(getIndicatorName(d.getIndicatorCode(), configMap)).append("：").append(DF2.format(d.getIndicatorValue())).append("\n");
                        }
                    }
                    sb.append("\n## 收入水平\n");
                    for (JwGridDataRaw d : rawData) {
                        if (d.getIndicatorValue() == null || d.getIndicatorValue() == 0) continue;
                        if (isDescendant(d.getIndicatorCode(), "grid_pop_income", configMap)) {
                            sb.append("- ").append(getIndicatorName(d.getIndicatorCode(), configMap)).append("：").append(DF2.format(d.getIndicatorValue())).append("人\n");
                        }
                    }
                }
            }
        } else {
            sb.append("- 该网点未关联网格\n");
        }

        // ========== 第二部分：网点数据 ==========
        sb.append("\n## 网点信息\n");
        sb.append("- 网点名称：").append(nullSafe(info.getSecondaryBranch())).append("\n");
        sb.append("- 所属一级支行：").append(nullSafe(info.getPrimaryBranch())).append("\n");
        sb.append("- 网点类型：").append(nullSafe(info.getBranchType())).append("\n");
        sb.append("- 地址：").append(nullSafe(info.getAddress())).append("\n");
        sb.append("- 总人数：").append(info.getTotalStaff() != null ? info.getTotalStaff() : 0).append("人\n");
        sb.append("- 总面积：").append(info.getTotalArea() != null ? info.getTotalArea() : 0).append("㎡\n");
        sb.append("- 产权类型：").append(nullSafe(info.getPropertyRight())).append("\n");

        // 效能得分
        sb.append("\n## 效能得分（").append(year).append("年）\n");
        List<JwBranchScore> scores = branchScoreMapper.selectByBranchIdsAndYear(Collections.singletonList(branchId), year);
        for (JwBranchScore s : scores) {
            String catName = getScoreCategoryName(s.getScoreCategory());
            sb.append("- ").append(catName).append("：").append(DF4.format(s.getCategoryScore()));
            if (s.getRankNum() != null) sb.append("（排名第").append(s.getRankNum()).append("）");
            sb.append("\n");
        }

        // ========== 第三部分：与相邻网格对比 ==========
        if (gridCode != null) {
            List<JwGridMeta> neighborCandidates = null;
            if (city != null) {
                neighborCandidates = gridMetaMapper.selectByCity(city);
            }
            if (neighborCandidates == null || neighborCandidates.size() <= 1) {
                neighborCandidates = gridMetaMapper.selectJwGridMetaList(new JwGridMeta());
            }
            if (neighborCandidates != null && neighborCandidates.size() > 1) {
                JwGridMeta currentMeta = neighborCandidates.stream()
                        .filter(m -> gridCode.equals(m.getGridCode())).findFirst().orElse(null);
                if (currentMeta != null && currentMeta.getLatitude() != null) {
                    List<NeighborGrid> neighbors = new ArrayList<>();
                    for (JwGridMeta m : neighborCandidates) {
                        if (gridCode.equals(m.getGridCode())) continue;
                        if (m.getLatitude() == null || m.getLongitude() == null) continue;
                        double dist = JwGeoUtils.haversine(currentMeta.getLatitude(), currentMeta.getLongitude(), m.getLatitude(), m.getLongitude());
                        neighbors.add(new NeighborGrid(m.getGridCode(), m.getDistrict(), dist));
                    }
                    neighbors.sort(Comparator.comparingDouble(n -> n.distance));
                    int neighborCount = Math.min(8, neighbors.size());
                    List<NeighborGrid> topNeighbors = neighbors.subList(0, neighborCount);
                    List<String> neighborCodes = topNeighbors.stream().map(n -> n.gridCode).collect(Collectors.toList());

                    // 评分
                    List<JwGridScore> neighborScores = gridScoreMapper.selectScoresByGridCodes(neighborCodes);
                    Map<String, Double> neighborOverallMap = new HashMap<>();
                    if (neighborScores != null) {
                        for (JwGridScore ns : neighborScores) {
                            if ("overall".equals(ns.getScoreCategory())) neighborOverallMap.put(ns.getGridCode(), ns.getSiteScore());
                        }
                    }

                    // 原始数据
                    List<JwGridDataRaw> neighborRawData = gridDataRawMapper.selectByGridCodes(neighborCodes);

                    // 计算相邻网格均值
                    double sumPop = 0, sumLowIncome = 0, sumMidIncome = 0, sumHighIncome = 0, sumPoi = 0;
                    double sumOverall = 0;
                    int validCount = neighborCodes.size();
                    for (String nc : neighborCodes) {
                        Double os = neighborOverallMap.get(nc);
                        if (os != null) sumOverall += os;
                        if (neighborRawData != null) {
                            for (JwGridDataRaw d : neighborRawData) {
                                if (!nc.equals(d.getGridCode())) continue;
                                if (d.getIndicatorValue() == null || d.getIndicatorValue() == 0) continue;
                                if (isDescendant(d.getIndicatorCode(), "grid_pop_resident", configMap)) sumPop += d.getIndicatorValue();
                                if (isDescendant(d.getIndicatorCode(), "grid_biz_poi", configMap)) sumPoi += d.getIndicatorValue();
                                String code = d.getIndicatorCode();
                                if (isDescendant(code, "grid_pop_income", configMap)) {
                                    if (code.contains("2499") || code.contains("below") || code.contains("2500") || code.contains("3999")) sumLowIncome += d.getIndicatorValue();
                                    else if (code.contains("4000") || code.contains("5999")) sumMidIncome += d.getIndicatorValue();
                                    else sumHighIncome += d.getIndicatorValue();
                                }
                            }
                        }
                    }

                    // 当前网格数据
                    double curPop = 0, curLowIncome = 0, curMidIncome = 0, curHighIncome = 0, curPoi = 0;
                    if (rawData != null) {
                        for (JwGridDataRaw d : rawData) {
                            if (d.getIndicatorValue() == null || d.getIndicatorValue() == 0) continue;
                            if (isDescendant(d.getIndicatorCode(), "grid_pop_resident", configMap)) curPop += d.getIndicatorValue();
                            if (isDescendant(d.getIndicatorCode(), "grid_biz_poi", configMap)) curPoi += d.getIndicatorValue();
                            String code = d.getIndicatorCode();
                            if (isDescendant(code, "grid_pop_income", configMap)) {
                                if (code.contains("2499") || code.contains("below") || code.contains("2500") || code.contains("3999")) curLowIncome += d.getIndicatorValue();
                                else if (code.contains("4000") || code.contains("5999")) curMidIncome += d.getIndicatorValue();
                                else curHighIncome += d.getIndicatorValue();
                            }
                        }
                    }
                    double curOverall = 0;
                    List<JwGridScore> curGridScores = gridScoreMapper.selectScoresByGridCode(gridCode);
                    if (curGridScores != null) {
                        for (JwGridScore s : curGridScores) {
                            if ("overall".equals(s.getScoreCategory())) { curOverall = s.getSiteScore(); break; }
                        }
                    }

                    sb.append("\n## 与相邻网格对比\n");
                    sb.append("对比基础：周围").append(neighborCount).append("个网格的指标均值\n");
                    if (curPop > 0 && sumPop > 0) {
                        sb.append("- 人口规模：当前 ").append(DF2.format(curPop)).append(" | 相邻均值 ").append(DF2.format(sumPop/validCount)).append(" | 差值 ").append(formatPercent(curPop, sumPop/validCount)).append("\n");
                    }
                    double curLowTotal = curLowIncome + curMidIncome + curHighIncome;
                    double avgLowTotal = (sumLowIncome + sumMidIncome + sumHighIncome) / validCount;
                    if (curLowTotal > 0 && avgLowTotal > 0) {
                        sb.append("- 低收入人群：当前 ").append(DF2.format(curLowIncome)).append("人（占比").append(DF2.format(curLowIncome/curLowTotal*100)).append("%）| 相邻均值 ").append(DF2.format(sumLowIncome/validCount)).append("人\n");
                        sb.append("- 中高收入人群：当前 ").append(DF2.format(curMidIncome+curHighIncome)).append("人（占比").append(DF2.format((curMidIncome+curHighIncome)/curLowTotal*100)).append("%）| 相邻均值 ").append(DF2.format((sumMidIncome+sumHighIncome)/validCount)).append("人\n");
                    }
                    if (curPoi > 0 && sumPoi > 0) {
                        sb.append("- 商业配套POI：当前 约").append(Math.round(curPoi)).append("个 | 相邻均值 约").append(Math.round(sumPoi/validCount)).append("个\n");
                    }
                    sb.append("- 综合得分：当前 ").append(DF4.format(curOverall)).append(" | 相邻均值 ").append(DF4.format(sumOverall/validCount)).append(" | 差值 ").append(formatPercent(curOverall, sumOverall/validCount)).append("\n");
                }
            }
        }

        // ========== 第四部分：候选推荐 ==========
        sb.append("\n## 候选空白网格推荐\n");
        if (gridCode == null) {
            sb.append("- 当前网点未关联网格，无法推荐候选\n");
        } else {
            // 获取当前网格评分
            JwGridScore curOverallScore = null;
            List<JwGridScore> curScores = gridScoreMapper.selectScoresByGridCode(gridCode);
            if (curScores != null) {
                for (JwGridScore s : curScores) {
                    if ("overall".equals(s.getScoreCategory())) {
                        curOverallScore = s;
                        break;
                    }
                }
            }
            if (curOverallScore == null) {
                sb.append("- 当前网格无评分，无法推荐候选\n");
            } else {
                double currentScore = curOverallScore.getSiteScore();

                // 获取当前网格中心坐标（用于距离计算）
                JwGridMeta currentMeta = gridMetaMapper.selectByGridCode(gridCode);
                if (currentMeta == null || currentMeta.getLatitude() == null || currentMeta.getLongitude() == null) {
                    sb.append("- 当前网格坐标信息缺失，无法推荐候选\n");
                } else {
                    // 查评分高于当前网格的空白网格（不限制数量）
                    List<String> betterBlankGrids = gridScoreMapper.selectBetterBlankCodes(city, currentScore);
                    if (betterBlankGrids != null && !betterBlankGrids.isEmpty()) {
                        // 批量查询评分和元数据
                        List<JwGridScore> blankScores = gridScoreMapper.selectScoresByGridCodesAndCategory(betterBlankGrids, "overall");
                        Map<String, JwGridScore> blankScoreMap = new HashMap<>();
                        if (blankScores != null) {
                            for (JwGridScore bs : blankScores) blankScoreMap.put(bs.getGridCode(), bs);
                        }

                        // 计算每个候选网格的距离（从当前网格中心到候选网格中心）
                        List<BlankCandidate> candidates = new ArrayList<>();
                        for (String code : betterBlankGrids) {
                            JwGridScore bs = blankScoreMap.get(code);
                            if (bs == null) continue;
                            JwGridMeta bm = gridMetaMapper.selectByGridCode(code);
                            if (bm == null || bm.getLatitude() == null || bm.getLongitude() == null) continue;

                            double dist = JwGeoUtils.haversine(
                                    currentMeta.getLatitude(), currentMeta.getLongitude(),
                                    bm.getLatitude(), bm.getLongitude());

                            // 过滤太远（>3km 不相关）
                            if (dist > 3) continue;

                            candidates.add(new BlankCandidate(code, bs.getSiteScore(), dist, bm));
                        }

                        // 按距离排序（近的优先），距离相近的评分高的优先
                        candidates.sort((a, b) -> {
                            int cmp = Double.compare(a.distance, b.distance);
                            if (cmp == 0) return Double.compare(b.score, a.score);
                            return cmp;
                        });

                        int count = 0;
                        for (BlankCandidate c : candidates) {
                            if (count >= 3) break;
                            // 获取三支柱得分
                            List<JwGridScore> pillarScores = gridScoreMapper.selectScoresByGridCode(c.code);
                            Map<String, JwGridScore> psMap = new HashMap<>();
                            if (pillarScores != null) {
                                for (JwGridScore ps : pillarScores) psMap.put(ps.getScoreCategory(), ps);
                            }

                            sb.append("- 候选").append(count + 1).append("：[查看 ").append(c.code).append("](grid:").append(c.code).append(")\n");
                            sb.append("  选址得分：").append(DF4.format(c.score)).append("/1.0");
                            JwGridScore pop = psMap.get("pop");
                            JwGridScore biz = psMap.get("grid_biz");
                            JwGridScore ent = psMap.get("ent");
                            if (pop != null) sb.append("，人口 ").append(DF4.format(pop.getSiteScore()));
                            if (biz != null) sb.append("，商业 ").append(DF4.format(biz.getSiteScore()));
                            if (ent != null) sb.append("，企业 ").append(DF4.format(ent.getSiteScore()));
                            sb.append("\n");
                            sb.append("  距本网点：").append(DF2.format(c.distance)).append("km\n");
                            sb.append("  区县：").append(nullSafe(c.meta.getDistrict())).append("\n");
                            count++;
                        }
                        if (count == 0) {
                            sb.append("- 未找到距离合适的候选空白网格\n");
                        }
                    } else {
                        sb.append("- 全市无评分高于当前网格的空白网格\n");
                    }
                }
            }
        }

        return sb.toString();
    }

    // ==================== 功能7增强：单网点四象限分析 数据聚合 ====================

    public String buildPerBranchQuadrantContextData(Long branchId, Integer year) {
        JwBranchInfo info = getBranchInfoByBranchId(branchId);
        if (info == null) return "网点不存在：" + branchId;

        StringBuilder sb = new StringBuilder();
        sb.append("## 网点基本信息\n");
        sb.append("- 网点名称：").append(nullSafe(info.getSecondaryBranch())).append("\n");
        sb.append("- 所属一级支行：").append(nullSafe(info.getPrimaryBranch())).append("\n");
        sb.append("- 网点类型：").append(nullSafe(info.getBranchType())).append("\n");
        sb.append("- 总人数：").append(info.getTotalStaff() != null ? info.getTotalStaff() : 0).append("人\n");
        sb.append("- 总面积：").append(info.getTotalArea() != null ? info.getTotalArea() : 0).append("㎡\n");
        sb.append("- 所属网格编码：").append(nullSafe(info.getGridCode())).append("\n");

        // 四象限定位
        sb.append("\n## 四象限定位\n");
        String gridCode = info.getGridCode();
        if (gridCode != null) {
            List<JwGridScore> gridScores = gridScoreMapper.selectScoresByGridCode(gridCode);
            Map<String, JwGridScore> gsMap = new HashMap<>();
            if (gridScores != null) {
                for (JwGridScore gs : gridScores) {
                    gsMap.put(gs.getScoreCategory(), gs);
                }
            }
            JwGridScore overallGrid = gsMap.get("overall");
            if (overallGrid != null) {
                sb.append("- 选址得分：").append(DF4.format(overallGrid.getSiteScore())).append("/1.0\n");
            }
            // 三支柱得分
            List<String> pillars = Arrays.asList("pop", "grid_biz", "ent");
            String[] pillarNames = {"人口聚集", "商业聚集", "企业聚集"};
            for (int i = 0; i < pillars.size(); i++) {
                JwGridScore pScore = gsMap.get(pillars.get(i));
                if (pScore != null) {
                    sb.append("  - ").append(pillarNames[i]).append("：").append(DF4.format(pScore.getSiteScore())).append("/1.0\n");
                }
            }
        }

        // 效能得分（五维度）
        sb.append("\n## 效能得分（").append(year).append("年）\n");
        List<JwBranchScore> branchScores = branchScoreMapper.selectByBranchIdsAndYear(Collections.singletonList(branchId), year);
        Map<String, String> catNames = new HashMap<>();
        catNames.put("overall", "综合得分"); catNames.put("revenue", "经营情况");
        catNames.put("indicator", "业绩表现"); catNames.put("customer", "客户发展");
        catNames.put("operation", "业务运营");
        for (JwBranchScore s : branchScores) {
            String cn = catNames.getOrDefault(s.getScoreCategory(), s.getScoreCategory());
            sb.append("- ").append(cn).append("：").append(DF4.format(s.getCategoryScore()));
            if (s.getRankNum() != null) sb.append("（排名第").append(s.getRankNum()).append("）");
            sb.append("\n");
        }

        // 全市排名
        sb.append("\n## 全市对比\n");
        JwBranchScore overallScore = null;
        for (JwBranchScore s : branchScores) {
            if ("overall".equals(s.getScoreCategory())) { overallScore = s; break; }
        }
        if (overallScore != null) {
            sb.append("- 效能得分全市排名：第").append(overallScore.getRankNum() != null ? overallScore.getRankNum() : "N/A").append("名\n");
        }
        sb.append("- 所属区县：").append(nullSafe(info.getDistrictName())).append("\n");

        return sb.toString();
    }
}