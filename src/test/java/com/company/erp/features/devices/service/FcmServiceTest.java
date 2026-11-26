package com.company.erp.features.devices.service;

import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FcmServiceTest {

    @Test
    void onlyUnregisteredIsADeadToken() {
        // APNs "device token is disabled" / app removed → delete the row
        assertThat(FcmService.isDeadToken(MessagingErrorCode.UNREGISTERED)).isTrue();
        // temporary or server problems → keep the token
        assertThat(FcmService.isDeadToken(MessagingErrorCode.UNAVAILABLE)).isFalse();
        assertThat(FcmService.isDeadToken(MessagingErrorCode.INTERNAL)).isFalse();
        assertThat(FcmService.isDeadToken(MessagingErrorCode.QUOTA_EXCEEDED)).isFalse();
        assertThat(FcmService.isDeadToken(MessagingErrorCode.THIRD_PARTY_AUTH_ERROR)).isFalse();
        assertThat(FcmService.isDeadToken(null)).isFalse();
    }
}
