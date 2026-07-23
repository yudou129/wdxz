package com.ruoyi.jwmap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * jw-map 模块配置
 */
@Component
@ConfigurationProperties(prefix = "jwmap")
public class JwMapConfig {

    /** 文件上传/报告存储路径 */
    private String profile = "./uploadPath";

    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }
}
