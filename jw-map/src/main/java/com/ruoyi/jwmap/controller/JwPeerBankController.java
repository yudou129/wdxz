package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.jwmap.service.IJwDataQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 同业银行数据查询控制器
 */
@RestController
@RequestMapping("/jwmap/data/peerBank")
public class JwPeerBankController extends BaseController {

    @Autowired
    private IJwDataQueryService dataQueryService;

    @GetMapping("/list")
    public AjaxResult peerBankList(@RequestParam(required = false) String city) {
        if (city == null || city.isEmpty()) return success(new ArrayList<>());
        return success(dataQueryService.queryPeerBankList(city));
    }

    @GetMapping("/distance/{branchId}")
    public AjaxResult getPeerBankDistance(@PathVariable Long branchId,
                                           @RequestParam(defaultValue = "1") double radius) {
        List<Map<String, Object>> result = dataQueryService.queryPeerBankDistance(branchId, radius);
        return success(result);
    }
}
