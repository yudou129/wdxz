package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.jwmap.service.IGridComputeService;
import com.ruoyi.jwmap.service.IBranchComputeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据计算控制器
 */
@RestController
@RequestMapping("/jwmap/compute")
public class JwComputeController extends BaseController {

    @Autowired
    private IGridComputeService gridComputeService;
    @Autowired
    private IBranchComputeService branchComputeService;

    // ===== 数据就绪状态查询 =====

    /**
     * 获取所有市的数据就绪状态
     */
    @GetMapping("/cityStatus")
    public AjaxResult getAllCityStatus() {
        List<Map<String, Object>> statusList = gridComputeService.getAllCityStatus();
        return success(statusList);
    }

    /**
     * 获取指定市的数据就绪状态
     */
    @GetMapping("/cityStatus/{city}")
    public AjaxResult getCityStatus(@PathVariable String city) {
        Map<String, Object> status = gridComputeService.getCityDataStatus(city);
        return success(status);
    }

    /**
     * 获取网点数据就绪状态
     */
    @GetMapping("/branchStatus/{city}")
    public AjaxResult getBranchStatus(@PathVariable String city) {
        Map<String, Object> status = branchComputeService.getBranchDataStatus(city);
        return success(status);
    }

    // ===== 网格计算 =====

    /**
     * 执行完整的网格数据计算（指定市）
     */
    @PostMapping("/grid/{city}")
    public AjaxResult computeGridData(@PathVariable String city) {
        try {
            int count = gridComputeService.computeGridData(city);
            return success("成功计算 " + count + " 个网格数据");
        } catch (Exception e) {
            return error("计算失败：" + e.getMessage());
        }
    }

    /**
     * 仅计算网格元信息
     */
    @PostMapping("/grid/meta/{city}")
    public AjaxResult computeGridMeta(@PathVariable String city) {
        try {
            int count = gridComputeService.computeGridMeta(city);
            return success("成功计算 " + count + " 个网格元信息");
        } catch (Exception e) {
            return error("计算失败：" + e.getMessage());
        }
    }

    /**
     * 仅计算网格得分
     */
    @PostMapping("/grid/score/{city}")
    public AjaxResult computeGridScore(@PathVariable String city) {
        try {
            int count = gridComputeService.computeGridScore(city);
            return success("成功计算 " + count + " 个网格得分");
        } catch (Exception e) {
            return error("计算失败：" + e.getMessage());
        }
    }

    // ===== 网点计算 =====

    /**
     * 执行完整的网点数据计算（指定市+年）
     */
    @PostMapping("/branch/{city}/{year}")
    public AjaxResult computeBranchData(@PathVariable String city, @PathVariable Integer year) {
        try {
            int count = branchComputeService.computeBranchData(city, year);
            return success("成功计算 " + count + " 个网点数据（" + year + "年）");
        } catch (Exception e) {
            return error("计算失败：" + e.getMessage());
        }
    }

    /**
     * 网点归属网格计算
     */
    @PostMapping("/branch/assignGrid/{city}")
    public AjaxResult assignGridToBranch(@PathVariable String city) {
        try {
            int count = branchComputeService.assignGridToBranch(city);
            return success("成功为 " + count + " 个网点分配网格");
        } catch (Exception e) {
            return error("计算失败：" + e.getMessage());
        }
    }
}
