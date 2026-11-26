package com.company.erp.features.chats.dto;

import com.company.erp.features.chats.entity.CallStatus;
import com.company.erp.features.chats.entity.CallType;
import com.company.erp.features.chats.entity.ChatCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The call carries WHO is calling (name + photo), so the callee's ringing
 * screen shows it at once — no extra conversation lookup on the phone.
 */
class ChatCallDtoTest {

    private static ChatCall call() {
        ChatCall c = new ChatCall();
        c.setConversationId(3L);
        c.setCallerId(2L);
        c.setType(CallType.VOICE);
        c.setStatus(CallStatus.RINGING);
        return c;
    }

    @Test
    void carriesCallerNameAndAvatar() {
        ChatCallDto dto = ChatCallDto.from(call(), List.of(),
                "/uploads/avatars/2.jpg", "Mr Bin");
        assertThat(dto.callerName()).isEqualTo("Mr Bin");
        assertThat(dto.callerAvatarUrl()).isEqualTo("/uploads/avatars/2.jpg");
        assertThat(dto.callerId()).isEqualTo(2L);
    }

    @Test
    void withoutCallerInfo_bothNull() {
        ChatCallDto dto = ChatCallDto.from(call(), List.of());
        assertThat(dto.callerName()).isNull();
        assertThat(dto.callerAvatarUrl()).isNull();
    }
}
