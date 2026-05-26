package com.ruoyi.jwmap.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 网格元信息对象 jw_grid_meta
 */
public class JwGridMeta extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private String gridCode;
    private Double longitude;
    private Double latitude;
    private Double westLongitude;
    private Double eastLongitude;
    private Double northLatitude;
    private Double southLatitude;
    private String province;
    private String city;
    private String district;
    private Integer poiCount;

    public String getGridCode() { return gridCode; }
    public void setGridCode(String gridCode) { this.gridCode = gridCode; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getWestLongitude() { return westLongitude; }
    public void setWestLongitude(Double westLongitude) { this.westLongitude = westLongitude; }
    public Double getEastLongitude() { return eastLongitude; }
    public void setEastLongitude(Double eastLongitude) { this.eastLongitude = eastLongitude; }
    public Double getNorthLatitude() { return northLatitude; }
    public void setNorthLatitude(Double northLatitude) { this.northLatitude = northLatitude; }
    public Double getSouthLatitude() { return southLatitude; }
    public void setSouthLatitude(Double southLatitude) { this.southLatitude = southLatitude; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public Integer getPoiCount() { return poiCount; }
    public void setPoiCount(Integer poiCount) { this.poiCount = poiCount; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("gridCode", getGridCode())
            .append("city", getCity())
            .append("poiCount", getPoiCount())
            .toString();
    }
}
