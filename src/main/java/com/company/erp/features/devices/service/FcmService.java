package com.company.erp.features.devices.service;

import com.company.erp.core.config.AppProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
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

    private final AppProperties props;
    private final DeviceService devices;
    private boolean ready = false;

    public FcmService(AppProperties props, DeviceService devices) {
        this.props = props;
        this.devices = devices;
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
                send(msg, token, safeData.get("type"), "data");
            } catch (Exception ex) {
                log.warn("[fcm] send failed type={} token=…{}: {}",
                        safeData.get("type"), tokenTail(token), ex.getMessage());
            }
        }
    }

    /**
     * TEST BRANCH (test/ios-local-call-notification): a VISIBLE push for iOS.
     * iOS draws the banner itself (no app code runs), so it shows even when
     * the app is minimized or swiped away, and with sandbox (flutter run)
     * builds. A silent data push is not woken reliably on iOS. The data keys
     * ride along: tapping the banner gives them to the app.
     */
    /** Same id as the app's CallNotifications.iosCategory. */
    public static final String IOS_CALL_CATEGORY = "incoming_call";

    @Async
    public void sendAlertToTokens(List<String> tokens, String title, String body,
                                  Map<String, String> data) {
        if (!ready) {
            log.debug("[fcm] skip alert — service not ready (tokens={})", tokens.size());
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
                        .setApnsConfig(ApnsConfig.builder()
                                .putHeader("apns-priority", "10")
                                .putHeader("apns-push-type", "alert")
                                .setAps(Aps.builder()
                                        .setAlert(ApsAlert.builder()
                                                .setTitle(title)
                                                .setBody(body)
                                                .build())
                                        .setSound("default")
                                        // the app registers this category
                                        // (Decline / Answer buttons)
                                        .setCategory(IOS_CALL_CATEGORY)
                                        .build())
                                .build())
                        .build();
                send(msg, token, safeData.get("type"), "ALERT");
            } catch (Exception ex) {
                log.warn("[fcm] send failed type={} token=…{}: {}",
                        safeData.get("type"), tokenTail(token), ex.getMessage());
            }
        }
    }

    /**
     * Send one message. A dead token (FCM {@code UNREGISTERED}: APNs "device
     * token is disabled", app removed / reinstalled) is deleted from
     * {@code devices}: next time we don't wait for it (~0.4 s each) before
     * reaching the live phones.
     */
    private void send(Message msg, String token, String type, String kind) {
        try {
            String id = FirebaseMessaging.getInstance().send(msg);
            log.info("[fcm] sent {} type={} to token=…{} id={}", kind, type, tokenTail(token), id);
        } catch (FirebaseMessagingException ex) {
            if (isDeadToken(ex.getMessagingErrorCode())) {
                long removed = devices.forgetToken(token);
                log.info("[fcm] dead token=…{} ({}) → removed {} device row(s)",
                        tokenTail(token), ex.getMessage(), removed);
            } else {
                log.warn("[fcm] {} failed type={} token=…{}: {}",
                        kind, type, tokenTail(token), ex.getMessage());
            }
        }
    }

    /** Only UNREGISTERED means "this token will never work again". */
    static boolean isDeadToken(MessagingErrorCode code) {
        return code == MessagingErrorCode.UNREGISTERED;
    }

    private static String tokenTail(String t) {
        return (t == null || t.length() < 6) ? "?" : t.substring(t.length() - 6);
    }
}
