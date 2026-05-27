package com.company.erp.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Security security,
        Cors cors,
        RateLimit rateLimit,
        Stream stream,
        Fcm fcm,
        Uploads uploads
) {

    public record Uploads(Avatar avatar) {
        public record Avatar(
                String dir,
                String publicBaseUrl,
                long maxFileSize,
                String allowedContentTypes
        ) {}
    }

    public record Security(Jwt jwt) {
        public record Jwt(
                String issuer,
                String secret,
                Duration accessTokenTtl,
                Duration refreshTokenTtl
        ) {}
    }

    public record Cors(
            String allowedHosts,
            String allowedMethods,
            String allowedHeaders
    ) {}

    public record RateLimit(
            boolean enabled,
            int perMinute,
            int authPerMinute
    ) {}

    public record Stream(
            String apiKey,
            String apiSecret,
            long tokenTtlMinutes
    ) {}

    public record Fcm(
            boolean enabled,
            String serviceAccountJsonPath
    ) {}
}
