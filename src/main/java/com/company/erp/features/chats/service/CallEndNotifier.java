package com.company.erp.features.chats.service;

import com.company.erp.features.chats.dto.CallParticipantDto;
import com.company.erp.features.chats.dto.ChatCallDto;
import com.company.erp.features.chats.entity.ChatCall;
import com.company.erp.features.chats.entity.ChatCallParticipant;
import com.company.erp.features.chats.ws.ChatBroadcaster;
import com.company.erp.features.devices.entity.Device;
import com.company.erp.features.devices.service.DeviceService;
import com.company.erp.features.devices.service.FcmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fans a "this call is over (nobody answered)" notification out over both
 * transports, so EVERY device — caller, foreground callee, backgrounded
 * callee — learns the ring ended:
 *
 * <ul>
 *   <li>STOMP {@code call.hangup} on the per-call topic, attributed to the
 *       caller ({@code hangerUpperId = callerId}) so the caller's "Calling…"
 *       page closes and any connected callee drops its in-app overlay.</li>
 *   <li>FCM {@code call.cancel} data push to every participant's devices so a
 *       backgrounded / killed ringer dismisses the native CallKit /
 *       ConnectionService screen.</li>
 * </ul>
 *
 * <p>This is the single chokepoint shared by the ring-timeout sweep
 * ({@code CallTimeoutScheduler}) and the connection-drop cancel
 * ({@code CallDisconnectHandler}); both emit the identical envelope so the
 * client handles them the same way.</p>
 */
@Component
public class CallEndNotifier {

    private static final Logger log = LoggerFactory.getLogger(CallEndNotifier.class);

    private final ChatBroadcaster broadcaster;
    private final DeviceService devices;
    private final FcmService fcm;

    public CallEndNotifier(ChatBroadcaster broadcaster, DeviceService devices, FcmService fcm) {
        this.broadcaster = broadcaster;
        this.devices = devices;
        this.fcm = fcm;
    }

    /**
     * Notify all parties that {@code call} ended with nobody answering.
     *
     * @param fcmReason value placed on the FCM {@code reason} field for
     *                  diagnostics ({@code "timeout"} for the sweep,
     *                  {@code "disconnect"} for a dropped connection). The
     *                  client dismisses identically regardless.
     */
    public void notifyNoAnswer(ChatCall call, String fcmReason) {
        ChatCallDto dto = toDto(call);

        // STOMP: close the caller's "Calling…" page; drop any connected ringer.
        broadcaster.toCall(call.getConversationId(), "call.hangup", Map.of(
                "callId",        call.getId(),
                "hangerUpperId", call.getCallerId(),  // attributed to the caller
                "reason",        "no_answer",
                "call",          dto));

        // FCM: dismiss the ring on every participant's backgrounded device.
        List<Long> targetUserIds = call.getParticipants().stream()
                .map(ChatCallParticipant::getUserId)
                .toList();
        List<String> tokens = devices.listForUsers(targetUserIds).stream()
                .map(Device::getFcmToken)
                .toList();
        Map<String, String> data = new HashMap<>();
        data.put("type",   "call.cancel");
        data.put("callId", String.valueOf(call.getId()));
        data.put("reason", fcmReason);
        log.info("[fcm] call.cancel ({}) callId={} → users={} tokens={}",
                fcmReason, call.getId(), targetUserIds, tokens.size());
        fcm.sendDataToTokens(tokens, data);
    }

    private ChatCallDto toDto(ChatCall c) {
        List<CallParticipantDto> participants = c.getParticipants().stream()
                .filter(p -> p.getStatus() != null)  // defensive
                .map(CallParticipantDto::from).toList();
        return ChatCallDto.from(c, participants);
    }
}
