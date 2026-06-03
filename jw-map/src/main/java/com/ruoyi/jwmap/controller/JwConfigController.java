package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.jwmap.domain.JwIndicatorConfig;
import com.ruoyi.jwmap.mapper.JwIndicatorConfigMapper;
import com.ruoyi.jwmap.service.impl.JwIndicatorConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * jw-map 配置管理 Controller
 * 提供指标 CRUD、层级树、编码自动生成等接口
 */
@RestController
@RequestMapping("/jwmap/config")
public class JwConfigController extends BaseController {

    @Autowired
    private JwIndicatorConfigMapper indicatorConfigMapper;

    @Autowired
    private JwIndicatorConfigService indicatorConfigService;

    /** 查询所有分类（根节点即评分分类） */
    @GetMapping("/categories")
    public AjaxResult getCategories() {
        List<JwIndicatorConfig> roots = indicatorConfigMapper.selectRoots(null);
        Map<String, Object> output = new LinkedHashMap<>();
        for (JwIndicatorConfig root : roots) {
            Map<String, Object> catData = new LinkedHashMap<>();
            catData.put("name", root.getIndicatorName());
            catData.put("indicatorType", root.getIndicatorType());
            catData.put("indicatorCode", root.getIndicatorCode());
            output.put(root.getIndicatorCode(), catData);
        }
        return success(output);
    }

    /** 查询指标列表（支持类型关键词过滤） */
    @GetMapping("/indicators")
    public AjaxResult getIndicators(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String indicatorType) {
        List<JwIndicatorConfig> list;
        if (indicatorType != null && !indicatorType.isEmpty()) {
            list = indicatorConfigMapper.selectByType(indicatorType);
        } else {
            list = indicatorConfigMapper.selectJwIndicatorConfigList(new JwIndicatorConfig());
        }
        return success(list.stream()
            .filter(i -> keyword == null || keyword.isEmpty()
                || i.getIndicatorCode().contains(keyword)
                || (i.getIndicatorName() != null && i.getIndicatorName().contains(keyword)))
            .map(this::toMap)
            .collect(Collectors.toList()));
    }

    /** 获取指标层级树 */
    @GetMapping("/indicators/tree")
    public AjaxResult getIndicatorTree(@RequestParam(required = false) String indicatorType) {
        List<JwIndicatorConfig> all;
        if (indicatorType != null && !indicatorType.isEmpty()) {
            if (indicatorType.contains(",")) {
                String[] types = indicatorType.split(",");
                all = indicatorConfigMapper.selectByTypes(Arrays.asList(types));
            } else {
                all = indicatorConfigMapper.selectByType(indicatorType);
            }
        } else {
            all = indicatorConfigMapper.selectJwIndicatorConfigList(new JwIndicatorConfig());
        }
        Map<String, JwIndicatorConfig> configMap = all.stream()
            .collect(Collectors.toMap(JwIndicatorConfig::getIndicatorCode, c -> c, (a, b) -> a));

        // 构建树：根节点 → 递归子节点
        List<Map<String, Object>> roots = new ArrayList<>();
        for (JwIndicatorConfig c : all) {
            if (c.getParentCode() == null || c.getParentCode().isEmpty()) {
                roots.add(buildTreeNode(c, configMap));
            }
        }
        return success(roots);
    }

    private Map<String, Object> buildTreeNode(JwIndicatorConfig node, Map<String, JwIndicatorConfig> configMap) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("indicatorId", node.getIndicatorId());
        item.put("indicatorCode", node.getIndicatorCode());
        item.put("indicatorName", node.getIndicatorName());
        item.put("indicatorType", node.getIndicatorType());
        item.put("parentCode", node.getParentCode());
        item.put("isDerived", node.getIsDerived());
        item.put("computationPattern", node.getComputationPattern());
        item.put("inputCodes", node.getInputCodes());
        item.put("calculationWeight", node.getCalculationWeight());
        item.put("sortOrder", node.getSortOrder());
        item.put("isLeaf", node.isLeaf(configMap));
        // 递归子节点
        List<Map<String, Object>> children = new ArrayList<>();
        for (JwIndicatorConfig c : configMap.values()) {
            if (node.getIndicatorCode().equals(c.getParentCode())) {
                children.add(buildTreeNode(c, configMap));
            }
        }
        item.put("children", children);
        return item;
    }

    /** 根据名称自动生成 indicator_code */
    @GetMapping("/indicators/code/generate")
    public AjaxResult generateCode(
            @RequestParam String name,
            @RequestParam(required = false, defaultValue = "branch") String indicatorType) {
        String code = autoGenerateCode(name, indicatorType);
        String baseCode = code;
        int suffix = 0;
        while (indicatorConfigMapper.selectByCode(code) != null) {
            suffix++;
            code = baseCode + "_" + suffix;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        return success(result);
    }

    private String autoGenerateCode(String name, String type) {
        // 简单拼音首字母简化
        Map<String, String> wordMap = new LinkedHashMap<>();
        wordMap.put("人均", "per_capita");
        wordMap.put("单位面积", "per_area");
        wordMap.put("日均增幅", "avg_growth");
        wordMap.put("日均余额", "avg_balance");
        wordMap.put("日均增量", "avg_growth");
        wordMap.put("全量个人金融资产", "total_asset");
        wordMap.put("储蓄存款", "saving");
        wordMap.put("公司客户存款", "corp_dep");
        wordMap.put("机构客户存款", "inst_dep");
        wordMap.put("普惠贷款", "inclusive_loan");
        wordMap.put("个人贷款", "personal_loan");
        wordMap.put("利息净收入", "interest_income");
        wordMap.put("手续费净收入", "fee_income");
        wordMap.put("柜台", "counter");
        wordMap.put("自助终端", "terminal");
        wordMap.put("ATM", "atm");
        wordMap.put("交易笔数", "txn");
        wordMap.put("客户数", "cust");
        wordMap.put("营销额", "amount");
        wordMap.put("发放额", "amount");
        wordMap.put("营收", "rev");
        wordMap.put("人口", "pop");
        wordMap.put("企业", "ent");
        wordMap.put("商圈", "biz");

        String result = name.replaceAll("[（()）\\s]", "");
        for (Map.Entry<String, String> e : wordMap.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        result = result.replaceAll("[^a-zA-Z0-9_]", "");
        result = result.replaceAll("_+", "_").replaceAll("^_|_$", "").toLowerCase();

        if (result.isEmpty()) {
            result = type + "_" + System.currentTimeMillis() % 10000;
        }
        return result;
    }

    /** 查某指标的子指标 */
    @GetMapping("/indicators/{indicatorCode}/children")
    public AjaxResult getChildren(@PathVariable String indicatorCode) {
        return success(indicatorConfigMapper.selectByParent(indicatorCode).stream()
            .map(this::toMap).collect(Collectors.toList()));
    }

    /** 新增指标 */
    @PostMapping("/indicators")
    public AjaxResult createIndicator(@RequestBody JwIndicatorConfig config) {
        config.setCreateTime(new Date());
        config.setUpdateTime(new Date());
        if (config.getIsDerived() == null) config.setIsDerived("0");
        if (config.getSortOrder() == null) config.setSortOrder(0);
        int rows = indicatorConfigMapper.insertIndicatorConfig(config);
        return rows > 0 ? success("新增成功") : error("新增失败");
    }

    /** 更新指标（若indicator_code变更则同步更新所有依赖表） */
    @PutMapping("/indicators/{indicatorId}")
    public AjaxResult updateIndicator(@PathVariable Long indicatorId, @RequestBody JwIndicatorConfig config) {
        JwIndicatorConfig old = indicatorConfigMapper.selectJwIndicatorConfigById(indicatorId);
        if (old == null) return error("指标不存在");

        String oldCode = old.getIndicatorCode();
        String newCode = config.getIndicatorCode();

        config.setIndicatorId(indicatorId);
        int rows = indicatorConfigMapper.updateJwIndicatorConfig(config);
        // 如果编码变更了，同步更新所有依赖表中的引用
        if (newCode != null && !newCode.equals(oldCode)) {
            indicatorConfigService.updateDependentCodes(oldCode, newCode);
        }
        return rows > 0 ? success("更新成功") : error("更新失败");
    }

    /** 删除指标（级联删除子节点 + 清理所有依赖表中的数据） */
    @DeleteMapping("/indicators/{indicatorId}")
    public AjaxResult deleteIndicator(@PathVariable Long indicatorId) {
        JwIndicatorConfig config = indicatorConfigMapper.selectJwIndicatorConfigById(indicatorId);
        if (config == null) return error("指标不存在");
        indicatorConfigService.deleteWithCascadeData(config.getIndicatorCode());
        return success("删除成功");
    }

    /** 批量删除指标 */
    @PostMapping("/indicators/batchDelete")
    public AjaxResult batchDelete(@RequestBody Map<String, List<String>> body) {
        List<String> codes = body.get("codes");
        if (codes == null || codes.isEmpty()) return error("请选择要删除的指标");
        for (String code : codes) {
            JwIndicatorConfig config = indicatorConfigMapper.selectByCode(code);
            if (config != null) {
                indicatorConfigService.deleteWithCascadeData(code);
            }
        }
        return success("批量删除成功");
    }

    private Map<String, Object> toMap(JwIndicatorConfig i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("indicatorId", i.getIndicatorId());
        m.put("indicatorCode", i.getIndicatorCode());
        m.put("indicatorName", i.getIndicatorName());
        m.put("indicatorType", i.getIndicatorType());
        m.put("parentCode", i.getParentCode());
        m.put("isDerived", i.getIsDerived());
        m.put("computationPattern", i.getComputationPattern());
        m.put("inputCodes", i.getInputCodes());
        m.put("calculationWeight", i.getCalculationWeight());
        m.put("sortOrder", i.getSortOrder());
        return m;
    }
}
