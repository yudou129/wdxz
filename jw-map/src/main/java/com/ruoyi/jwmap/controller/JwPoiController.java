package com.ruoyi.jwmap.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.jwmap.domain.JwPoiInfo;
import com.ruoyi.jwmap.mapper.JwPoiInfoMapper;
import com.ruoyi.jwmap.util.JwGeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * POI 数据查询控制器
 */
@RestController
@RequestMapping("/jwmap/data/poi")
public class JwPoiController extends BaseController {

    @Autowired
    private JwPoiInfoMapper poiInfoMapper;

    @GetMapping("/list")
    public AjaxResult poiList(@RequestParam(required = false) String city) {
        JwPoiInfo query = new JwPoiInfo();
        if (city != null && !city.isEmpty()) query.setCity(city);
        return success(poiInfoMapper.selectPoiInfoList(query));
    }

    @PostMapping("/withinRange")
    public AjaxResult poiWithinRange(@RequestBody Map<String, Object> params) {
        String city = (String) params.get("city");
        if (city == null || city.isEmpty()) return error("城市参数不能为空");

        String shapeType = (String) params.get("shapeType");
        double centerLng = toDouble(params.get("centerLng"));
        double centerLat = toDouble(params.get("centerLat"));
        double radius = toDouble(params.get("radius"));

        if (radius <= 0) return error("半径必须大于0");

        double halfSide = radius;
        double latDelta = halfSide / 111320.0;
        double lngDelta = halfSide / (111320.0 * Math.cos(Math.toRadians(centerLat)));

        double westLng = centerLng - lngDelta;
        double eastLng = centerLng + lngDelta;
        double southLat = centerLat - latDelta;
        double northLat = centerLat + latDelta;

        List<JwPoiInfo> withinBounds = poiInfoMapper.selectWithinBounds(city, westLng, eastLng, southLat, northLat);

        List<Map<String, Object>> result;
        if ("circle".equals(shapeType)) {
            result = withinBounds.stream()
                .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                .filter(p -> JwGeoUtils.haversine(centerLat, centerLng, p.getLatitude(), p.getLongitude()) * 1000 <= radius)
                .map(this::poiToMap)
                .collect(Collectors.toList());
        } else {
            result = withinBounds.stream()
                .map(this::poiToMap)
                .collect(Collectors.toList());
        }

        return success(result);
    }

    private Map<String, Object> poiToMap(JwPoiInfo p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("poiId", p.getPoiId());
        m.put("poiName", p.getPoiName());
        m.put("poiType", p.getPoiType());
        m.put("address", p.getAddress());
        m.put("longitude", p.getLongitude());
        m.put("latitude", p.getLatitude());
        return m;
    }

    private double toDouble(Object obj) {
        return obj instanceof Number ? ((Number) obj).doubleValue() : 0;
    }
}
