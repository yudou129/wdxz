package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.jwmap.domain.JwBranchInfo;
import com.ruoyi.jwmap.domain.JwPeerBankInfo;
import com.ruoyi.jwmap.mapper.JwBranchInfoMapper;
import com.ruoyi.jwmap.mapper.JwPeerBankInfoMapper;
import com.ruoyi.jwmap.util.JwGeoUtils;
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
    private JwPeerBankInfoMapper peerBankInfoMapper;

    @Autowired
    private JwBranchInfoMapper branchInfoMapper;

    @GetMapping("/list")
    public AjaxResult peerBankList(@RequestParam(required = false) String city) {
        if (city == null || city.isEmpty()) return success(new ArrayList<>());
        return success(peerBankInfoMapper.selectByCity(city));
    }

    @GetMapping("/distance/{branchId}")
    public AjaxResult getPeerBankDistance(@PathVariable Long branchId,
                                           @RequestParam(defaultValue = "1") double radius) {
        JwBranchInfo branch = branchInfoMapper.selectJwBranchInfoById(branchId);
        if (branch == null) return error("网点不存在");

        List<JwPeerBankInfo> peers = peerBankInfoMapper.selectByCity(branch.getCity());
        List<Map<String, Object>> result = new ArrayList<>();
        for (JwPeerBankInfo p : peers) {
            if (p.getLongitude() == null || p.getLatitude() == null) continue;
            double dist = JwGeoUtils.haversine(branch.getLatitude(), branch.getLongitude(),
                                                 p.getLatitude(), p.getLongitude());
            if (dist <= radius) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("orgName", p.getOrgName());
                m.put("bankName", p.getBankName());
                m.put("distance", Math.round(dist * 1000.0) / 1000.0);
                m.put("longitude", p.getLongitude());
                m.put("latitude", p.getLatitude());
                result.add(m);
            }
        }
        result.sort(Comparator.comparingDouble(m -> ((Number) m.get("distance")).doubleValue()));
        return success(result);
    }
}
