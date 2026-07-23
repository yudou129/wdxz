package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.jwmap.service.IJwDataQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * POI 数据查询控制器
 */
@RestController
@RequestMapping("/jwmap/data/poi")
public class JwPoiController extends BaseController {

    @Autowired
    private IJwDataQueryService dataQueryService;

    @GetMapping("/list")
    public AjaxResult poiList(@RequestParam(required = false) String city) {
        return success(dataQueryService.queryPoiList(city));
    }

    @PostMapping("/withinRange")
    public AjaxResult poiWithinRange(@RequestBody Map<String, Object> params) {
        String city = (String) params.get("city");
        if (city == null || city.isEmpty()) return error("城市参数不能为空");
        double radius = toDouble(params.get("radius"));
        if (radius <= 0) return error("半径必须大于0");
        return success(dataQueryService.queryPoiWithinRange(params));
    }

    private double toDouble(Object obj) {
        return obj instanceof Number ? ((Number) obj).doubleValue() : 0;
    }
}
