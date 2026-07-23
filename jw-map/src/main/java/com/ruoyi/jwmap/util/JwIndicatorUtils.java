package com.ruoyi.jwmap.util;

import com.ruoyi.jwmap.domain.JwIndicatorConfig;

import java.util.*;
import java.util.stream.Collectors;

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

    /**
     * 获取指定根节点下的所有叶子指标编码
     */
    public static List<String> getLeafCodesUnder(String rootCode, Map<String, JwIndicatorConfig> configMap) {
        Set<String> leafCodes = new LinkedHashSet<>();
        for (JwIndicatorConfig config : configMap.values()) {
            if (config.isLeaf(configMap) && isUnderRoot(config, rootCode, configMap)) {
                leafCodes.add(config.getIndicatorCode());
            }
        }
        return new ArrayList<>(leafCodes);
    }

    /**
     * 判断节点是否属于指定根节点下的子树
     */
    public static boolean isUnderRoot(JwIndicatorConfig node, String rootCode, Map<String, JwIndicatorConfig> configMap) {
        String parent = node.getParentCode();
        while (parent != null && !parent.isEmpty()) {
            if (parent.equals(rootCode)) return true;
            JwIndicatorConfig p = configMap.get(parent);
            parent = p != null ? p.getParentCode() : null;
        }
        return false;
    }

    /**
     * 获取根节点名称（一级分类名）
     */
    public static String getRootName(String indicatorCode, Map<String, JwIndicatorConfig> configMap) {
        JwIndicatorConfig config = configMap.get(indicatorCode);
        if (config == null) return "";
        String code = indicatorCode;
        String name = config.getIndicatorName();
        String parentCode = config.getParentCode();
        while (parentCode != null && !parentCode.isEmpty()) {
            JwIndicatorConfig parent = configMap.get(parentCode);
            if (parent == null) break;
            code = parentCode;
            name = parent.getIndicatorName();
            parentCode = parent.getParentCode();
        }
        return name != null ? name : "";
    }

    /**
     * 将 config 列表转为 code→config 映射（重复 key 时保留第一个）
     */
    public static Map<String, JwIndicatorConfig> toConfigMap(List<JwIndicatorConfig> configs) {
        return configs.stream()
            .collect(Collectors.toMap(JwIndicatorConfig::getIndicatorCode, c -> c, (a, b) -> a));
    }

    private JwIndicatorUtils() {}
}
