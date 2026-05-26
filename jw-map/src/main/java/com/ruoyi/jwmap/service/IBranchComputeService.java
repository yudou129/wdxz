package com.ruoyi.jwmap.service;

/**
 * 网点数据计算服务接口
 * 负责：数据计算表（人均/单位面积）→ 汇总行 → 归一化 → 五类TOPSIS得分
 */
public interface IBranchComputeService {

    /**
     * 对指定市的指定年份执行完整网点计算管线
     * @param city 市名
     * @param dataYear 数据年份
     * @return 计算的网点数量
     */
    int computeBranchData(String city, Integer dataYear);

    /**
     * 仅计算数据计算表（人均/单位面积指标）
     */
    int computeBranchIndicators(String city, Integer dataYear);

    /**
     * 仅执行归一化和TOPSIS五类得分计算
     */
    int computeBranchScore(String city, Integer dataYear);

    /**
     * 导入网点基础数据后，自动计算所属网格
     */
    int assignGridToBranch(String city);

    /**
     * 获取网点数据就绪状态
     */
    java.util.Map<String, Object> getBranchDataStatus(String city);
}
