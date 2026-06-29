package com.ruoyi.jwmap.constant;

/**
 * 数据查看申请状态常量
 * 使用 final String 常量以兼容 MyBatis XML 中 status = '0' 的字符串比较
 */
public final class AccessStatus {
    private AccessStatus() {}

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
}
