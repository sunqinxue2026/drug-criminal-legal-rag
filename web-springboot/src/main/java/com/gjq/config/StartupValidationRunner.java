package com.gjq.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class StartupValidationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupValidationRunner.class);
    private static final String DEFAULT_JWT_SECRET = "change-me-in-production-change-me-in-production";
    private static final String DEFAULT_INTERNAL_TOKEN = "change-me";
    private static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production");

    private final Environment environment;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${app.security.allowed-origins:}")
    private String allowedOrigins;

    @Value("${app.security.internal-service-token:}")
    private String internalServiceToken;

    @Value("${external.algorithm-service.base-url:}")
    private String algorithmServiceUrl;

    @Value("${external.file-service.base-url:}")
    private String fileServiceUrl;

    public StartupValidationRunner(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean productionProfile = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(PRODUCTION_PROFILES::contains);

        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        validateJwtSecret(productionProfile, warnings, errors);
        validateInternalToken(productionProfile, warnings, errors);
        validateDatasource(productionProfile, warnings, errors);
        validateAllowedOrigins(productionProfile, warnings, errors);
        validateRemoteService("ALGORITHM_SERVICE_BASE_URL", algorithmServiceUrl, productionProfile, warnings, errors);
        validateRemoteService("FILE_SERVICE_BASE_URL", fileServiceUrl, productionProfile, warnings, errors);

        for (String warning : warnings) {
            log.warn("Startup config warning: {}", warning);
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("启动配置校验失败：" + String.join("；", errors));
        }

        log.info("Startup configuration validation passed with {} warning(s)", warnings.size());
    }

    private void validateJwtSecret(boolean productionProfile, List<String> warnings, List<String> errors) {
        if (!StringUtils.hasText(jwtSecret) || DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            String message = "JWT_SECRET 仍在使用默认值";
            (productionProfile ? errors : warnings).add(message);
            return;
        }

        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            String message = "JWT_SECRET 长度不足 32 字节";
            (productionProfile ? errors : warnings).add(message);
        }
    }

    private void validateInternalToken(boolean productionProfile, List<String> warnings, List<String> errors) {
        if (!StringUtils.hasText(internalServiceToken) || DEFAULT_INTERNAL_TOKEN.equals(internalServiceToken)) {
            String message = "INTERNAL_SERVICE_TOKEN 未配置为安全值";
            (productionProfile ? errors : warnings).add(message);
        }
    }

    private void validateDatasource(boolean productionProfile, List<String> warnings, List<String> errors) {
        if (!StringUtils.hasText(datasourceUrl)) {
            errors.add("SPRING_DATASOURCE_URL 未配置");
            return;
        }

        if (productionProfile && !StringUtils.hasText(datasourcePassword)) {
            errors.add("生产环境必须配置 SPRING_DATASOURCE_PASSWORD");
        }

        if (productionProfile && containsLocalhost(datasourceUrl)) {
            warnings.add("生产环境仍在使用 localhost 数据库地址，请确认部署拓扑");
        }
    }

    private void validateAllowedOrigins(boolean productionProfile, List<String> warnings, List<String> errors) {
        if (!StringUtils.hasText(allowedOrigins)) {
            errors.add("APP_ALLOWED_ORIGINS 不能为空");
            return;
        }

        if (productionProfile && containsLocalhost(allowedOrigins)) {
            warnings.add("生产环境仍允许 localhost 跨域来源，请按实际域名收敛");
        }
    }

    private void validateRemoteService(
            String propertyName,
            String serviceUrl,
            boolean productionProfile,
            List<String> warnings,
            List<String> errors
    ) {
        if (!StringUtils.hasText(serviceUrl)) {
            errors.add(propertyName + " 未配置");
            return;
        }

        if (productionProfile && containsLocalhost(serviceUrl)) {
            warnings.add("生产环境仍指向 localhost 服务地址：" + propertyName);
        }
    }

    private boolean containsLocalhost(String value) {
        String lowerValue = value.toLowerCase(Locale.ROOT);
        return lowerValue.contains("localhost") || lowerValue.contains("127.0.0.1");
    }
}
