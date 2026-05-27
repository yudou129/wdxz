package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.jwmap.service.impl.ExcelImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据导入控制器
 */
@RestController
@RequestMapping("/jwmap/import")
public class JwImportController extends BaseController {

    @Autowired
    private ExcelImportService excelImportService;

    /**
     * 导入POI信息
     */
    @PostMapping("/poi")
    public AjaxResult importPoi(
            @RequestParam("file") MultipartFile file,
            @RequestParam("city") String city) {
        try {
            int count = excelImportService.importPoiInfo(file.getInputStream(), city, getUsername());
            return success("成功导入 " + count + " 条POI数据");
        } catch (Exception e) {
            logger.error("导入POI异常", e);
            return error("导入失败：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /**
     * 导入人口热力
     */
    @PostMapping("/populationHeat")
    public AjaxResult importPopulationHeat(
            @RequestParam("file") MultipartFile file,
            @RequestParam("city") String city) {
        try {
            int count = excelImportService.importPopulationHeat(file.getInputStream(), city);
            return success("成功导入 " + count + " 条人口热力数据");
        } catch (Exception e) {
            logger.error("导入人口热力异常", e);
            return error("导入失败：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /**
     * 导入外部资源权重表
     */
    @PostMapping("/externalWeight")
    public AjaxResult importExternalWeight(@RequestParam("file") MultipartFile file) {
        try {
            int count = excelImportService.importExternalWeight(file.getInputStream());
            return success("成功导入 " + count + " 条权重数据");
        } catch (Exception e) {
            logger.error("导入外部资源权重异常", e);
            return error("导入失败：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /**
     * 导入网点效能权重表
     */
    @PostMapping("/branchEfficiencyWeight")
    public AjaxResult importBranchEfficiencyWeight(@RequestParam("file") MultipartFile file) {
        try {
            int count = excelImportService.importBranchEfficiencyWeight(file.getInputStream());
            return success("成功导入 " + count + " 条权重数据");
        } catch (Exception e) {
            logger.error("导入网点效能权重异常", e);
            return error("导入失败：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /**
     * 导入网点信息表（基础数据Sheet）
     */
    @PostMapping("/branchInfo")
    public AjaxResult importBranchInfo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("city") String city,
            @RequestParam(value = "dataSource", defaultValue = "网点信息") String dataSource) {
        try {
            int count = excelImportService.importBranchInfo(file.getInputStream(), city, dataSource);
            return success("成功导入 " + count + " 条网点数据");
        } catch (Exception e) {
            logger.error("导入网点信息异常", e);
            return error("导入失败：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /**
     * 导入存量网点基本信息表
     */
    @PostMapping("/existingBranch")
    public AjaxResult importExistingBranch(
            @RequestParam("file") MultipartFile file,
            @RequestParam("city") String city) {
        try {
            int count = excelImportService.importExistingBranch(file.getInputStream(), city);
            return success("成功导入 " + count + " 条存量网点数据");
        } catch (Exception e) {
            logger.error("导入存量网点异常", e);
            return error("导入失败：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }
}
