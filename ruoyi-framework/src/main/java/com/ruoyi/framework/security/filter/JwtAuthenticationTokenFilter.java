package com.ruoyi.framework.security.filter;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.web.service.TokenService;

/**
 * token过滤器 验证token有效性
 *
 * @author ruoyi
 */
@Component
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter
{
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationTokenFilter.class);

    @Autowired
    private TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        LoginUser loginUser = tokenService.getLoginUser(request);
        if (StringUtils.isNotNull(loginUser))
        {
            // 检查现有认证的 principal 是否已经是有效的 LoginUser
            boolean alreadyValid = false;
            try {
                Object existingAuth = SecurityUtils.getAuthentication();
                if (existingAuth instanceof org.springframework.security.core.Authentication) {
                    alreadyValid = ((org.springframework.security.core.Authentication) existingAuth).getPrincipal() instanceof LoginUser;
                }
            } catch (Exception ignored) {}

            if (!alreadyValid)
            {
                Object existingAuth = SecurityUtils.getAuthentication();
                log.info("JwtFilter: replacing auth (was {}) with LoginUser userId={}, uri={}",
                        existingAuth != null ? existingAuth.getClass().getSimpleName() : "null",
                        loginUser.getUserId(), request.getRequestURI());
                tokenService.verifyToken(loginUser);
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        }
        else
        {
            log.warn("JwtFilter: loginUser is null, uri={}", request.getRequestURI());
        }
        chain.doFilter(request, response);
    }
}
