package com.ruoyi.jwmap.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Date;

/**
 * 数据查看申请对象 jw_data_access_request
 */
public class JwDataAccessRequest extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long requestId;
    private Long applicantId;
    private Long targetDeptId;
    private String reason;
    private Integer validDays;
    private String status;
    private Long reviewerId;
    private String reviewComment;
    private Date reviewTime;
    private Date validDateFrom;
    private Date validDateTo;
    private String delFlag;

    // ===== 非数据库字段（前端展示用） =====
    /** 申请人名称 */
    private String applicantName;
    /** 目标支行名称 */
    private String targetDeptName;
    /** 审核人名称 */
    private String reviewerName;

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    public Long getTargetDeptId() { return targetDeptId; }
    public void setTargetDeptId(Long targetDeptId) { this.targetDeptId = targetDeptId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getValidDays() { return validDays; }
    public void setValidDays(Integer validDays) { this.validDays = validDays; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public Date getReviewTime() { return reviewTime; }
    public void setReviewTime(Date reviewTime) { this.reviewTime = reviewTime; }
    public Date getValidDateFrom() { return validDateFrom; }
    public void setValidDateFrom(Date validDateFrom) { this.validDateFrom = validDateFrom; }
    public Date getValidDateTo() { return validDateTo; }
    public void setValidDateTo(Date validDateTo) { this.validDateTo = validDateTo; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public String getTargetDeptName() { return targetDeptName; }
    public void setTargetDeptName(String targetDeptName) { this.targetDeptName = targetDeptName; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("requestId", getRequestId())
            .append("applicantId", getApplicantId())
            .append("targetDeptId", getTargetDeptId())
            .append("status", getStatus())
            .toString();
    }
}
