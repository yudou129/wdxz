package com.ruoyi.jwmap.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 网点基本信息对象 jw_branch_info
 */
public class JwBranchInfo extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long branchId;
    private String primaryBranch;
    private String secondaryBranch;
    private String branchCode;
    private String gridCode;
    private String districtName;
    private String street;
    private String address;
    private Double longitude;
    private Double latitude;
    private Integer totalStaff;
    private Integer personalManager;
    private Integer corporateManager;
    private Integer counterStaff;
    private Integer lobbyStaff;
    private String branchManager;
    private String managerTenure;
    private String managerResume;
    private String managerHistory;
    private Double totalArea;
    private Double otherFloorArea;
    private Integer cashCounter;
    private Integer nonCashCounter;
    private Integer managerSeat;
    private String propertyRight;
    private String leaseExpire;
    private String lastRenovation;
    private String branchType;
    private String relocation;
    private String city;
    private String dataSource;
    private String delFlag;

    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public String getPrimaryBranch() { return primaryBranch; }
    public void setPrimaryBranch(String primaryBranch) { this.primaryBranch = primaryBranch; }
    public String getSecondaryBranch() { return secondaryBranch; }
    public void setSecondaryBranch(String secondaryBranch) { this.secondaryBranch = secondaryBranch; }
    public String getBranchCode() { return branchCode; }
    public void setBranchCode(String branchCode) { this.branchCode = branchCode; }
    public String getGridCode() { return gridCode; }
    public void setGridCode(String gridCode) { this.gridCode = gridCode; }
    public String getDistrictName() { return districtName; }
    public void setDistrictName(String districtName) { this.districtName = districtName; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Integer getTotalStaff() { return totalStaff; }
    public void setTotalStaff(Integer totalStaff) { this.totalStaff = totalStaff; }
    public Integer getPersonalManager() { return personalManager; }
    public void setPersonalManager(Integer personalManager) { this.personalManager = personalManager; }
    public Integer getCorporateManager() { return corporateManager; }
    public void setCorporateManager(Integer corporateManager) { this.corporateManager = corporateManager; }
    public Integer getCounterStaff() { return counterStaff; }
    public void setCounterStaff(Integer counterStaff) { this.counterStaff = counterStaff; }
    public Integer getLobbyStaff() { return lobbyStaff; }
    public void setLobbyStaff(Integer lobbyStaff) { this.lobbyStaff = lobbyStaff; }
    public String getBranchManager() { return branchManager; }
    public void setBranchManager(String branchManager) { this.branchManager = branchManager; }
    public String getManagerTenure() { return managerTenure; }
    public void setManagerTenure(String managerTenure) { this.managerTenure = managerTenure; }
    public String getManagerResume() { return managerResume; }
    public void setManagerResume(String managerResume) { this.managerResume = managerResume; }
    public String getManagerHistory() { return managerHistory; }
    public void setManagerHistory(String managerHistory) { this.managerHistory = managerHistory; }
    public Double getTotalArea() { return totalArea; }
    public void setTotalArea(Double totalArea) { this.totalArea = totalArea; }
    public Double getOtherFloorArea() { return otherFloorArea; }
    public void setOtherFloorArea(Double otherFloorArea) { this.otherFloorArea = otherFloorArea; }
    public Integer getCashCounter() { return cashCounter; }
    public void setCashCounter(Integer cashCounter) { this.cashCounter = cashCounter; }
    public Integer getNonCashCounter() { return nonCashCounter; }
    public void setNonCashCounter(Integer nonCashCounter) { this.nonCashCounter = nonCashCounter; }
    public Integer getManagerSeat() { return managerSeat; }
    public void setManagerSeat(Integer managerSeat) { this.managerSeat = managerSeat; }
    public String getPropertyRight() { return propertyRight; }
    public void setPropertyRight(String propertyRight) { this.propertyRight = propertyRight; }
    public String getLeaseExpire() { return leaseExpire; }
    public void setLeaseExpire(String leaseExpire) { this.leaseExpire = leaseExpire; }
    public String getLastRenovation() { return lastRenovation; }
    public void setLastRenovation(String lastRenovation) { this.lastRenovation = lastRenovation; }
    public String getBranchType() { return branchType; }
    public void setBranchType(String branchType) { this.branchType = branchType; }
    public String getRelocation() { return relocation; }
    public void setRelocation(String relocation) { this.relocation = relocation; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("branchId", getBranchId())
            .append("branchCode", getBranchCode())
            .append("gridCode", getGridCode())
            .toString();
    }
}
