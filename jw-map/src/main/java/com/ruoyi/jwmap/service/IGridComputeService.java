package com.ruoyi.jwmap.service;

/**
 * 网格数据计算服务接口
 * 负责：网格元信息构建 → 原始数据聚合 → 汇总行计算 → 归一化 → TOPSIS得分
 */
public interface IGridComputeService {

    /**
     * 对指定市执行完整的网格数据计算管线
     * @param city 市名
     * @return 计算的网格数量
     */
    int computeGridData(String city);

    /**
     * 仅计算网格元信息（坐标范围+POI数量）
     */
    int computeGridMeta(String city);

    /**
     * 仅执行归一化和TOPSIS得分计算
     */
    int computeGridScore(String city);

    /**
     * 获取指定市的数据就绪状态
     * @return map: {hasPoi, hasPopulation, hasWeight, gridCount}
     */
    java.util.Map<String, Object> getCityDataStatus(String city);

    /**
     * 获取所有数据就绪的市列表
     */
    java.util.List<java.util.Map<String, Object>> getAllCityStatus();
}
