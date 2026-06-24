package com.ruoyi.jwmap.constant;

/**
 * 数据查看申请状态常量
 */
public class AccessStatus {
    /** 待审批 */
    public static final String PENDING = "0";
    /** 已通过 */
    public static final String APPROVED = "1";
    /** 已拒绝 */
    public static final String REJECTED = "2";
    /** 已撤销 */
    public static final String CANCELLED = "3";
    /** 已过期 */
    public static final String EXPIRED = "4";

    private AccessStatus() {}
}
