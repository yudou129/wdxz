package com.ruoyi.jwmap.util;

import com.ruoyi.jwmap.domain.JwIndicatorConfig;

import java.util.Map;

/**
 * 指标工具类 — 共享的指标树操作方法
 */
public class JwIndicatorUtils {

    /**
     * 沿 parentCode 链追溯指标所属的一级根节点 code
     */
    public static String findLevel1Code(String indicatorCode, Map<String, JwIndicatorConfig> configMap) {
        JwIndicatorConfig config = configMap.get(indicatorCode);
        if (config == null) return null;
        String code = indicatorCode;
        String parentCode = config.getParentCode();
        while (parentCode != null && !parentCode.isEmpty()) {
            code = parentCode;
            JwIndicatorConfig parent = configMap.get(parentCode);
            parentCode = parent != null ? parent.getParentCode() : null;
        }
        return code;
    }
}
