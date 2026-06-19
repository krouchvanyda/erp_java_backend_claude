package com.company.erp.features.chats.service;

import com.company.erp.features.chats.entity.ChatCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Periodically auto-ends calls that stay in RINGING longer than
 * {@code app.chat.call.ring-timeout-seconds}, and fans the resulting
 * {@code call.hangup} envelope out over STOMP plus a {@code call.cancel}
 * FCM push so backgrounded ringers dismiss too.
 *
 * <p>Runs every 5 seconds. A 5s tick is fine because the timeout is 60s —
 * worst-case extra ring length is 5 seconds past the deadline.</p>
 */
@Component
public class CallTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(CallTimeoutScheduler.class);

    private final ChatCallService callService;
    private final CallEndNotifier notifier;

    public CallTimeoutScheduler(ChatCallService callService, CallEndNotifier notifier) {
        this.callService = callService;
        this.notifier = notifier;
    }

    @Scheduled(fixedDelayString = "${app.chat.call.sweep-interval-ms:5000}")
    @Transactional
    public void sweep() {
        List<ChatCall> ended;
        try {
            ended = callService.sweepStaleRinging();
        } catch (Exception ex) {
            log.warn("[call-sweep] failure during sweepStaleRinging: {}", ex.getMessage());
            return;
        }
        if (ended.isEmpty()) return;
        log.info("[call-sweep] auto-ended {} stale RINGING call(s)", ended.size());

        for (ChatCall c : ended) {
            notifier.notifyNoAnswer(c, "timeout");
        }
    }
}
