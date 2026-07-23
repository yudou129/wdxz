package com.ruoyi.jwmap.service;

import com.ruoyi.jwmap.domain.JwBranchInfo;
import com.ruoyi.jwmap.domain.JwPeerBankInfo;
import com.ruoyi.jwmap.domain.JwPoiInfo;

import java.util.List;
import java.util.Map;

/**
 * 数据查询服务接口 — 封装从 Controller 层移出的 Mapper 调用
 */
public interface IJwDataQueryService {

    // ===== POI =====

    List<JwPoiInfo> queryPoiList(String city);

    List<Map<String, Object>> queryPoiWithinRange(Map<String, Object> params);

    // ===== Population =====

    List<String> queryDistinctGridCodes(String city);

    // ===== Peer Bank =====

    List<JwPeerBankInfo> queryPeerBankList(String city);

    List<Map<String, Object>> queryPeerBankDistance(Long branchId, double radius);

    // ===== Analysis =====

    List<Map<String, Object>> queryQuadrantData(String city, Integer year);

    List<Map<String, Object>> queryDimensionStats(String city, Integer year, String dimension);

    Map<String, Object> queryThreeFocusRanking(String city, Integer year);
}
