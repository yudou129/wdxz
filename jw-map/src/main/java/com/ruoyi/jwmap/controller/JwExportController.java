package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.jwmap.service.impl.ExcelExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
/**
 * 数据导出控制器
 */
@RestController
@RequestMapping("/jwmap/export")
public class JwExportController extends BaseController {

    @Autowired
    private ExcelExportService excelExportService;

    /**
     * 导出网格数据表（原始数据表）
     */
    @GetMapping("/gridRaw/{city}")
    public void exportGridRaw(@PathVariable String city, HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("网格数据表（原始数据表）_" + city + ".xlsx", "UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
            excelExportService.exportGridDataRaw(city, response.getOutputStream());
        } catch (Exception e) {
            throw new RuntimeException("导出失败：" + e.getMessage());
        }
    }

    /**
     * 导出网格数据表（归一化得分处理表）
     */
    @GetMapping("/gridNormalized/{city}")
    public void exportGridNormalized(@PathVariable String city, HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("网格数据表（归一化得分处理表）_" + city + ".xlsx", "UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
            excelExportService.exportGridDataNormalized(city, response.getOutputStream());
        } catch (Exception e) {
            throw new RuntimeException("导出失败：" + e.getMessage());
        }
    }

    /**
     * 导出网点基础数据
     */
    @GetMapping("/branchBase/{city}")
    public void exportBranchBase(@PathVariable String city, HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("网点信息_基础数据_" + city + ".xlsx", "UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
            excelExportService.exportBranchBaseData(city, response.getOutputStream());
        } catch (Exception e) {
            throw new RuntimeException("导出失败：" + e.getMessage());
        }
    }

    /**
     * 导出网点数据计算表（指定年份）
     */
    @GetMapping("/branchCalc/{city}/{year}")
    public void exportBranchCalc(@PathVariable String city, @PathVariable Integer year,
                                  HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("网点信息_数据计算表_" + city + "_" + year + ".xlsx", "UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
            excelExportService.exportBranchCalcData(city, year, response.getOutputStream());
        } catch (Exception e) {
            throw new RuntimeException("导出失败：" + e.getMessage());
        }
    }

    /**
     * 导出网点归一化处理表（指定年份）
     */
    @GetMapping("/branchNormalized/{city}/{year}")
    public void exportBranchNormalized(@PathVariable String city, @PathVariable Integer year,
                                        HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("网点信息_归一化处理_" + city + "_" + year + ".xlsx", "UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
            excelExportService.exportBranchNormalized(city, year, response.getOutputStream());
        } catch (Exception e) {
            throw new RuntimeException("导出失败：" + e.getMessage());
        }
    }
}
