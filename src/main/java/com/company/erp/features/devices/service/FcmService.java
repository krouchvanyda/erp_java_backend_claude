package com.company.erp.features.devices.service;

import com.company.erp.core.config.AppProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper over Firebase Admin SDK. Pushes are <strong>data-only</strong>
 * (no {@code notification} block) so the Flutter background handler runs and
 * the call sheet can be drawn with Accept/Reject buttons. Including a
 * notification block would cause Android to auto-display a plain banner and
 * skip the handler.
 *
 * <p>All sends are {@code @Async} and exception-swallowing — a failed FCM
 * delivery must never block the underlying call/REST flow.</p>
 */
@Service
public class FcmService {

    private static final Logger log = LoggerFactory.getLogger(FcmService.class);

    /**
     * The Android notification channel for chat banners (5.7). The app
     * creates it at start-up ({@code core/push/chat_notification_channel.dart});
     * the SAME id is in the manifest as FCM's default channel.
     */
    public static final String CHAT_CHANNEL_ID = "chat_messages";

    private final AppProperties props;
    private boolean ready = false;

    public FcmService(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        AppProperties.Fcm fcm = props.fcm();
        if (fcm == null || !fcm.enabled()) {
            log.warn("[fcm] disabled (FCM_ENABLED=false). Call invites will not push.");
            return;
        }
        String saPath = fcm.serviceAccountJsonPath();
        if (saPath == null || saPath.isBlank()) {
            log.warn("[fcm] FCM_ENABLED=true but FCM_SERVICE_ACCOUNT_JSON_PATH is missing — disabling.");
            return;
        }
        try (FileInputStream in = new FileInputStream(saPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .build();
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            ready = true;
            log.info("[fcm] initialised from {}", saPath);
        } catch (Exception ex) {
            log.error("[fcm] failed to initialise from {}: {}", saPath, ex.getMessage());
        }
    }

    public boolean isReady() {
        return ready;
    }

    /**
     * Fire-and-forget data-only push to every supplied token. Runs on a separate
     * thread; failures are logged at WARN and never propagated.
     */
    @Async
    public void sendDataToTokens(List<String> tokens, Map<String, String> data) {
        if (!ready) {
            log.debug("[fcm] skip send — service not ready (tokens={})", tokens.size());
            return;
        }
        if (tokens.isEmpty()) return;

        Map<String, String> safeData = new HashMap<>();
        data.forEach((k, v) -> safeData.put(k, v == null ? "" : v));

        for (String token : tokens) {
            try {
                Message msg = Message.builder()
                        .setToken(token)
                        .putAllData(safeData)
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .build())
                        .setApnsConfig(ApnsConfig.builder()
                                .putHeader("apns-priority", "10")
                                .putHeader("apns-push-type", "alert")
                                .setAps(Aps.builder()
                                        .setContentAvailable(true)
                                        .build())
                                .build())
                        .build();
                String id = FirebaseMessaging.getInstance().send(msg);
                log.info("[fcm] sent type={} to token=…{} id={}",
                        safeData.get("type"), tokenTail(token), id);
            } catch (Exception ex) {
                log.warn("[fcm] send failed type={} token=…{}: {}",
                        safeData.get("type"), tokenTail(token), ex.getMessage());
            }
        }
    }

    /**
     * A VISIBLE push (5.7): the phone's OS draws the banner itself, even when
     * the app is killed. iOS needs this: a data-only push often doesn't wake
     * the app at all. The {@code data} still rides along, so a tap can open
     * the right screen ({@code onMessageOpenedApp} / {@code getInitialMessage}).
     *
     * <p>{@code group} = one notification per chat: Android replaces the old
     * banner with the same {@code tag}; iOS stacks the same {@code thread-id}.</p>
     */
    @Async
    public void sendNotificationToTokens(List<String> tokens, String title, String body,
                                         Map<String, String> data, String group) {
        if (!ready) {
            log.debug("[fcm] skip notification — service not ready (tokens={})", tokens.size());
            return;
        }
        if (tokens.isEmpty()) return;

        Map<String, String> safeData = new HashMap<>();
        data.forEach((k, v) -> safeData.put(k, v == null ? "" : v));

        for (String token : tokens) {
            try {
                Message msg = Message.builder()
                        .setToken(token)
                        .putAllData(safeData)
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setNotification(AndroidNotification.builder()
                                        .setTitle(title)
                                        .setBody(body)
                                        .setChannelId(CHAT_CHANNEL_ID)
                                        .setTag(group)            // newest replaces older
                                        .setSound("default")
                                        .build())
                                .build())
                        .setApnsConfig(ApnsConfig.builder()
                                .putHeader("apns-priority", "10")
                                .putHeader("apns-push-type", "alert")
                                .setAps(Aps.builder()
                                        .setAlert(ApsAlert.builder()
                                                .setTitle(title)
                                                .setBody(body)
                                                .build())
                                        .setSound("default")
                                        .setThreadId(group)       // stacked per chat
                                        .build())
                                .build())
                        .build();
                String id = FirebaseMessaging.getInstance().send(msg);
                log.info("[fcm] sent NOTIFICATION type={} to token=…{} id={}",
                        safeData.get("type"), tokenTail(token), id);
            } catch (Exception ex) {
                log.warn("[fcm] notification failed type={} token=…{}: {}",
                        safeData.get("type"), tokenTail(token), ex.getMessage());
            }
        }
    }

    private static String tokenTail(String t) {
        return (t == null || t.length() < 6) ? "?" : t.substring(t.length() - 6);
    }
}
