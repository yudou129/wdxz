package com.ruoyi.jwmap.service.impl;

import com.ruoyi.jwmap.domain.JwIndicatorConfig;
import com.ruoyi.jwmap.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 指标配置变更时的级联数据清理服务
 */
@Service
public class JwIndicatorConfigService {

    @Autowired private JwPopulationHeatMapper populationHeatMapper;
    @Autowired private JwGridDataRawMapper gridDataRawMapper;
    @Autowired private JwGridDataNormalizedMapper gridDataNormalizedMapper;
    @Autowired private JwGridSummaryMapper gridSummaryMapper;
    @Autowired private JwBranchIndicatorMapper branchIndicatorMapper;
    @Autowired private JwBranchSummaryMapper branchSummaryMapper;
    @Autowired private JwIndicatorConfigMapper indicatorConfigMapper;

    /**
     * 删除指标时，递归收集所有后代编码，同步清理依赖表数据，最后删除指标配置
     */
    @Transactional
    public void deleteWithCascadeData(String indicatorCode) {
        List<String> allCodes = new ArrayList<>();
        allCodes.add(indicatorCode);
        collectDescendantCodes(indicatorCode, allCodes);

        for (String code : allCodes) {
            populationHeatMapper.deleteByIndicatorCode(code);
            gridDataRawMapper.deleteByIndicatorCode(code);
            gridDataNormalizedMapper.deleteByIndicatorCode(code);
            gridSummaryMapper.deleteByIndicatorCode(code);
            branchIndicatorMapper.deleteByIndicatorCode(code);
            branchSummaryMapper.deleteByIndicatorCode(code);
            indicatorConfigMapper.deleteByCode(code);
        }
    }

    private void collectDescendantCodes(String parentCode, List<String> result) {
        List<JwIndicatorConfig> children = indicatorConfigMapper.selectByParent(parentCode);
        for (JwIndicatorConfig child : children) {
            result.add(child.getIndicatorCode());
            collectDescendantCodes(child.getIndicatorCode(), result);
        }
    }

    /**
     * 修改 indicator_code 时，同步更新所有依赖表中的编码以及子节点的 parent_code
     */
    @Transactional
    public void updateDependentCodes(String oldCode, String newCode) {
        populationHeatMapper.updateIndicatorCode(oldCode, newCode);
        gridDataRawMapper.updateIndicatorCode(oldCode, newCode);
        gridDataNormalizedMapper.updateIndicatorCode(oldCode, newCode);
        gridSummaryMapper.updateIndicatorCode(oldCode, newCode);
        branchIndicatorMapper.updateIndicatorCode(oldCode, newCode);
        branchSummaryMapper.updateIndicatorCode(oldCode, newCode);
        // 同步更新子节点的 parent_code
        indicatorConfigMapper.updateParentCode(oldCode, newCode);
    }
}
