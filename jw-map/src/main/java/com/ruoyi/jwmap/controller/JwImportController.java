package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.jwmap.service.impl.ExcelImportService;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 数据导入控制器
 */
@RestController
@RequestMapping("/jwmap/import")
public class JwImportController extends BaseController {

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    private static final Set<String> ALLOWED_EXTENSIONS = new java.util.HashSet<>(java.util.Arrays.asList("xls", "xlsx"));

    @Autowired
    private ExcelImportService excelImportService;

    /** 校验文件 */
    private AjaxResult validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return error("上传文件为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return error("文件大小超过限制（最大50MB）");
        }
        String ext = FilenameUtils.getExtension(file.getOriginalFilename());
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            return error("不支持的文件格式，仅支持 .xls / .xlsx");
        }
        return null;
    }

    /**
     * 导入POI信息
     */
    @PostMapping("/poi")
    public AjaxResult importPoi(
            @RequestParam("file") MultipartFile file,
            @RequestParam("city") String city) {
        AjaxResult fileErr = validateFile(file);
        if (fileErr != null) return fileErr;
        try {
            String username = getUsernameSafely();
            int count = excelImportService.importPoiInfo(file.getInputStream(), city, username);
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
        AjaxResult fileErr = validateFile(file);
        if (fileErr != null) return fileErr;
        try {
            int count = excelImportService.importPopulationHeat(file.getInputStream(), city);
            return success("成功导入 " + count + " 条人口热力数据");
        } catch (Exception e) {
            logger.error("导入人口热力异常", e);
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
        AjaxResult fileErr = validateFile(file);
        if (fileErr != null) return fileErr;
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
        AjaxResult fileErr = validateFile(file);
        if (fileErr != null) return fileErr;
        try {
            int count = excelImportService.importExistingBranch(file.getInputStream(), city);
            return success("成功导入 " + count + " 条存量网点数据");
        } catch (Exception e) {
            logger.error("导入存量网点异常", e);
            return error("导入失败：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /**
     * 导入同业银行信息
     */
    @PostMapping("/peerBank")
    public AjaxResult importPeerBank(
            @RequestParam("file") MultipartFile file,
            @RequestParam("city") String city) {
        AjaxResult fileErr = validateFile(file);
        if (fileErr != null) return fileErr;
        try {
            int count = excelImportService.importPeerBank(file.getInputStream(), city);
            return success("成功导入 " + count + " 条同业银行数据");
        } catch (Exception e) {
            logger.error("导入同业银行异常", e);
            return error("导入失败：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /**
     * 安全获取当前登录用户名（在未认证时返回"system"而非抛异常）
     */
    private String getUsernameSafely() {
        try {
            return getUsername();
        } catch (Exception e) {
            return "system";
        }
    }
}
