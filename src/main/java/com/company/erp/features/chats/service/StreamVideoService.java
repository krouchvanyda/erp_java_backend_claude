package com.company.erp.features.chats.service;

import com.company.erp.core.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Server-side calls to Stream's Video REST API.
 *
 * <p>The only thing the backend does here is <em>ring</em> the callees:
 * creating the Stream call with {@code ring: true} and a member list is what
 * makes Stream emit the VoIP push that surfaces the native CallKit /
 * ConnectionService incoming-call screen — even when the callee app is
 * minimized or killed. The Flutter client deliberately joins with
 * {@code ring: false} (ringing client-side breaks its audio), so this push
 * must originate server-side.</p>
 *
 * <p>Auth reuses the same per-user Stream token the mobile SDK uses
 * (HMAC-SHA256 over the API secret, {@code user_id} = caller). Calling
 * get-or-create as the caller makes the caller the creator, so Stream pushes
 * to every other member and not back to the caller. All sends are
 * {@code @Async} and exception-swallowing — a Stream hiccup must never break
 * call signalling.</p>
 */
@Service
public class StreamVideoService {

    private static final Logger log = LoggerFactory.getLogger(StreamVideoService.class);
    private static final String BASE_URL = "https://video.stream-io-api.com";

    private final AppProperties props;
    private final StreamTokenService tokens;
    private final RestClient http;

    public StreamVideoService(AppProperties props, StreamTokenService tokens) {
        this.props = props;
        this.tokens = tokens;
        this.http = RestClient.builder().baseUrl(BASE_URL).build();
    }

    /**
     * Fire-and-forget get-or-create of the Stream call with {@code ring: true}
     * so the callees' devices receive the VoIP push.
     *
     * @param streamCallCid the {@code type:id} CID returned to the app, e.g. {@code default:erp-call-1220}
     * @param callerId      the caller's user id (becomes the Stream creator; not rung)
     * @param memberUserIds every conversation participant — caller + all callees
     */
    @Async
    public void ring(String streamCallCid, Long callerId, Collection<Long> memberUserIds) {
        if (!tokens.isEnabled()) {
            log.warn("[stream] skip ring — Stream not configured (cid={})", streamCallCid);
            return;
        }
        if (streamCallCid == null || streamCallCid.isBlank()) {
            log.warn("[stream] skip ring — missing streamCallCid (caller={})", callerId);
            return;
        }

        // "default:erp-call-1220" -> type="default", id="erp-call-1220".
        int sep = streamCallCid.indexOf(':');
        String type = sep > 0 ? streamCallCid.substring(0, sep) : "default";
        String id = sep > 0 ? streamCallCid.substring(sep + 1) : streamCallCid;

        List<Map<String, Object>> members = memberUserIds.stream()
                .distinct()
                .map(uid -> Map.<String, Object>of("user_id", String.valueOf(uid)))
                .toList();
        // ring is top-level; members live under data. Caller token => caller is
        // the creator, so created_by_id is implicit and must NOT be sent.
        Map<String, Object> body = Map.of(
                "ring", true,
                "data", Map.of("members", members)
        );

        String token = tokens.issueFor(callerId).token();
        String apiKey = props.stream().apiKey();

        try {
            http.post()
                    .uri(uri -> uri.path("/api/v2/video/call/{type}/{id}")
                            .queryParam("api_key", apiKey)
                            .build(type, id))
                    .header("Authorization", token)
                    .header("stream-auth-type", "jwt")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[stream] rang call cid={} caller={} members={}",
                    streamCallCid, callerId, members.size());
        } catch (RestClientResponseException ex) {
            log.warn("[stream] ring failed cid={} status={} body={}",
                    streamCallCid, ex.getStatusCode(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.warn("[stream] ring error cid={}: {}", streamCallCid, ex.getMessage());
        }
    }
}
