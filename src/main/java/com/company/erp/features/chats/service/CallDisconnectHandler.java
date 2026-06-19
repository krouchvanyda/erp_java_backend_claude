package com.company.erp.features.chats.service;

import com.company.erp.core.config.AppProperties;
import com.company.erp.features.chats.entity.ChatCall;
import com.company.erp.features.chats.presence.PresenceService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Ends an unanswered ring fast when the callee's connection drops, instead of
 * waiting for the {@link CallTimeoutScheduler} ring-timeout sweep.
 *
 * <p>Scenario: X calls Z; Z is online (foreground, in-app overlay) and then
 * force-quits the app. Z's STOMP socket drops, producing a
 * {@code SessionDisconnectEvent}; {@code WebSocketSessionListener} routes the
 * now-possibly-gone user here via {@link #onUserConnectionDropped(Long)}.</p>
 *
 * <p>Why a grace re-check and not an immediate cancel:</p>
 * <ul>
 *   <li><b>Transient blip</b> — a brief network drop also fires a disconnect;
 *       the client reconnects within a second or two. After the grace window we
 *       re-check {@link PresenceService#hasLiveSession} and skip the cancel if
 *       they came back.</li>
 *   <li><b>Intentional minimize</b> — backgrounding also drops the socket, but
 *       the client first sends the "I minimized" beacon. If the user is
 *       {@link PresenceService#isBackgrounded}, we leave the call to the
 *       VoIP-ring / ring-timeout flow rather than cancelling it here.</li>
 * </ul>
 *
 * <p>A callee who was already OFFLINE at call time (minimized/killed, rung via
 * VoIP push) never had a STOMP session, so they never trigger this path — their
 * ring is still governed solely by the ring timeout. This handler only reacts to
 * the online→gone transition.</p>
 */
@Component
public class CallDisconnectHandler {

    private static final Logger log = LoggerFactory.getLogger(CallDisconnectHandler.class);

    private final PresenceService presence;
    private final ChatCallService callService;
    private final CallEndNotifier notifier;
    private final AppProperties props;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "call-disconnect-grace");
                t.setDaemon(true);
                return t;
            });

    public CallDisconnectHandler(PresenceService presence,
                                 ChatCallService callService,
                                 CallEndNotifier notifier,
                                 AppProperties props) {
        this.presence = presence;
        this.callService = callService;
        this.notifier = notifier;
        this.props = props;
    }

    private long graceSeconds() {
        return (props.chat() == null || props.chat().call() == null
                || props.chat().call().disconnectGraceSeconds() < 0)
                ? 5L
                : props.chat().call().disconnectGraceSeconds();
    }

    /**
     * A STOMP session for {@code userId} just dropped. Schedule a grace re-check;
     * if the user is still gone (didn't reconnect) and didn't merely minimize,
     * auto-end any call they were ringing for and tell the caller.
     */
    public void onUserConnectionDropped(Long userId) {
        if (userId == null) return;
        log.info("[call-disconnect] user={} socket dropped — re-check in {}s",
                userId, graceSeconds());
        scheduler.schedule(() -> {
            try {
                resolve(userId);
            } catch (Exception ex) {
                log.warn("[call-disconnect] re-check failed for user={}: {}",
                        userId, ex.getMessage());
            }
        }, graceSeconds(), TimeUnit.SECONDS);
    }

    private void resolve(Long userId) {
        if (presence.hasLiveSession(userId)) {
            // Reconnected within the grace window — transient blip, leave the ring.
            log.info("[call-disconnect] SKIP user={} — reconnected within grace (live session)",
                    userId);
            return;
        }
        // NOTE: we deliberately do NOT skip on isBackgrounded here. Android sends
        // a "backgrounded" beacon on swipe-away too, which would wrongly suppress
        // the cancel. The real distinction — "is this callee still ringing via a
        // VoIP push?" — is made per-call inside endRingingForDisconnectedCallee
        // (push-rung callees are left to the ring timeout; STOMP-only callees,
        // whose only ring channel just died, are cancelled).
        List<ChatCall> ended = callService.endRingingForDisconnectedCallee(userId);
        if (ended.isEmpty()) {
            log.info("[call-disconnect] user={} gone but no RINGING call to end (nothing to do)",
                    userId);
            return;
        }
        log.info("[call-disconnect] callee={} gone — auto-ended {} ringing call(s)",
                userId, ended.size());
        for (ChatCall c : ended) {
            notifier.notifyNoAnswer(c, "disconnect");
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
