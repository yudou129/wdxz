package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.jwmap.service.IJwDataQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 人口热力数据查询控制器
 */
@RestController
@RequestMapping("/jwmap/data/population")
public class JwPopulationController extends BaseController {

    @Autowired
    private IJwDataQueryService dataQueryService;

    @GetMapping("/grids/{city}")
    public AjaxResult getDistinctGrids(@PathVariable String city) {
        return success(dataQueryService.queryDistinctGridCodes(city));
    }
}
