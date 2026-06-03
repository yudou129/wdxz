package com.ruoyi.jwmap.mapper;

import com.ruoyi.jwmap.domain.JwScoreCategoryConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评分分类指标映射配置 Mapper
 */
@Mapper
public interface JwScoreCategoryConfigMapper {

    /** 查询所有活跃的分类配置 */
    List<JwScoreCategoryConfig> selectAllActive();

    /** 查询指定分类的活跃指标 */
    List<JwScoreCategoryConfig> selectByCategory(@Param("categoryCode") String categoryCode);

    /** 查询所有分类编码 */
    List<String> selectDistinctCategories();

    /** 插入 */
    int insertCategoryConfig(JwScoreCategoryConfig config);

    /** 更新 */
    int updateCategoryConfig(JwScoreCategoryConfig config);

    /** 按分类删除 */
    int deleteByCategory(@Param("categoryCode") String categoryCode);

    int deleteByIndicatorCode(@Param("indicatorCode") String indicatorCode);
    int updateIndicatorCode(@Param("oldCode") String oldCode, @Param("newCode") String newCode);
}
