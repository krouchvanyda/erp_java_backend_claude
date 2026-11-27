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
        Uploads uploads,
        Chat chat
) {

    public record Uploads(Avatar avatar, ChatAttachment chatAttachment) {
        public record Avatar(
                String dir,
                String publicBaseUrl,
                long maxFileSize,
                String allowedContentTypes
        ) {}

        /** Storage for chat message attachments (image / voice / file). */
        public record ChatAttachment(
                String dir,
                String publicBaseUrl,
                long maxFileSize,
                String allowedContentTypes
        ) {}
    }

    public record Chat(Call call) {
        public record Call(
                /** How long a RINGING call survives before the sweeper auto-ends it. */
                long ringTimeoutSeconds,
                /** Late-accept tolerance — accepts arriving within this window after auto-cancel revive the call. */
                long acceptGraceSeconds,
                /**
                 * Grace window after an online, ringing callee's STOMP socket drops before
                 * we treat them as gone and auto-end their RINGING call (instead of waiting
                 * for {@code ringTimeoutSeconds}). Absorbs transient network blips — if the
                 * client reconnects within this window the cancel is skipped.
                 */
                long disconnectGraceSeconds,
                /**
                 * Grace window after a user who is IN a call (ANSWERED) loses their
                 * STOMP socket before the server hangs up for them. Longer than
                 * {@code disconnectGraceSeconds}: dropping a live call by mistake is
                 * worse than ending a ring. Without this, an app that crashed / was
                 * killed mid-call left the call ANSWERED forever and the user could
                 * never start another one ("Caller is already in an active call").
                 */
                long answeredDisconnectGraceSeconds,
                /**
                 * Grace for a user IN a call whose line was CLOSED (not silent):
                 * the phone's OS closes the socket at once when the app is killed /
                 * swiped away, while a network drop just goes silent. Closed =
                 * the app is gone → hang up after this short grace (enough for a
                 * quick reconnect). Silent lines keep
                 * {@code answeredDisconnectGraceSeconds}. Plain door only (R6).
                 */
                long closedDisconnectGraceSeconds
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
