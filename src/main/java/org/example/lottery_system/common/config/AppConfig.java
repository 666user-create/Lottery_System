package org.example.lottery_system.common.config;

import org.example.lottery_system.common.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class AppConfig implements WebMvcConfigurer {
    @Autowired
    private LoginInterceptor loginInterceptor;

    private final List<String> excludes = Arrays.asList(
            "/**/*.html",
            "/css/**",
            "/js/**",
            "/pic/**",
            "/*.jpg",
            "/favicon.ico",
            "/user/password/login",
            "/user/message/login",
            "/user/register",
            "/user/verification-code/send",
            "/draw/winning-records/show"
    );
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(excludes);
    }
}
