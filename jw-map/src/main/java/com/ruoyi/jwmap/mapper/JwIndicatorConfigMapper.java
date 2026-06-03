package com.ruoyi.jwmap.mapper;

import com.ruoyi.jwmap.domain.JwIndicatorConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 指标配置Mapper接口
 */
@Mapper
public interface JwIndicatorConfigMapper {

    List<JwIndicatorConfig> selectJwIndicatorConfigList(JwIndicatorConfig config);

    JwIndicatorConfig selectJwIndicatorConfigById(Long indicatorId);

    /** 按类型查全部指标 */
    List<JwIndicatorConfig> selectByType(@Param("indicatorType") String indicatorType);

    /** 按多个类型查全部指标 */
    List<JwIndicatorConfig> selectByTypes(@Param("types") List<String> types);

    /** 按类型查所有叶子节点（没有子节点的指标） */
    List<JwIndicatorConfig> selectLeavesByType(@Param("indicatorType") String indicatorType);

    /** 按 parent_code 查子指标 */
    List<JwIndicatorConfig> selectByParent(@Param("parentCode") String parentCode);

    /** 查所有根节点（parent_code IS NULL） */
    List<JwIndicatorConfig> selectRoots(@Param("indicatorType") String indicatorType);

    JwIndicatorConfig selectByCode(@Param("indicatorCode") String code);

    JwIndicatorConfig selectByIndicatorName(@Param("indicatorName") String name);

    List<JwIndicatorConfig> selectByCodes(@Param("list") List<String> codes);

    int insertIndicatorConfig(JwIndicatorConfig config);

    int updateJwIndicatorConfig(JwIndicatorConfig config);

    int deleteJwIndicatorConfigById(Long id);

    int deleteJwIndicatorConfigByIds(Long[] ids);

    /** 根据编码删除单个指标 */
    int deleteByCode(@Param("indicatorCode") String indicatorCode);

    /** 更新子节点的 parent_code（父节点编码变更时） */
    int updateParentCode(@Param("oldCode") String oldCode, @Param("newCode") String newCode);

    int batchInsert(List<JwIndicatorConfig> list);

    int upsertJwIndicatorConfig(JwIndicatorConfig config);
}
