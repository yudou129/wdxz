package com.ruoyi.jwmap.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 同业银行信息对象 jw_peer_bank_info
 */
public class JwPeerBankInfo extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long peerBankId;
    private String orgCode;
    private String orgName;
    private String orgAddress;
    private Double longitude;
    private Double latitude;
    private String bankName;
    private String province;
    private String city;
    private String district;
    private String town;
    private String gridCode;
    private String delFlag;

    public Long getPeerBankId() { return peerBankId; }
    public void setPeerBankId(Long peerBankId) { this.peerBankId = peerBankId; }
    public String getOrgCode() { return orgCode; }
    public void setOrgCode(String orgCode) { this.orgCode = orgCode; }
    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public String getOrgAddress() { return orgAddress; }
    public void setOrgAddress(String orgAddress) { this.orgAddress = orgAddress; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getTown() { return town; }
    public void setTown(String town) { this.town = town; }
    public String getGridCode() { return gridCode; }
    public void setGridCode(String gridCode) { this.gridCode = gridCode; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("peerBankId", getPeerBankId())
            .append("orgCode", getOrgCode())
            .append("gridCode", getGridCode())
            .toString();
    }
}
