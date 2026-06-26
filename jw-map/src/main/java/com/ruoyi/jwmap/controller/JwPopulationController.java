package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.jwmap.mapper.JwPopulationHeatMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 人口热力数据查询控制器
 */
@RestController
@RequestMapping("/jwmap/data/population")
public class JwPopulationController extends BaseController {

    @Autowired
    private JwPopulationHeatMapper populationHeatMapper;

    @GetMapping("/grids/{city}")
    public AjaxResult getDistinctGrids(@PathVariable String city) {
        return success(populationHeatMapper.selectDistinctGridCodesByCity(city));
    }
}
