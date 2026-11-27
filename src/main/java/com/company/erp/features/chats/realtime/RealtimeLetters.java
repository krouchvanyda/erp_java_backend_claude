package com.company.erp.features.chats.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The server's letters of the plain-WebSocket protocol (Step R2), as JSON
 * text. One place, so every letter has the same shape:
 *
 * <pre>
 *   {"type":"ready"}
 *   {"type":"pong"}
 *   {"type":"error","message":"…"}                       (+ "destination" when about one)
 *   {"type":"message","destination":"…","event":"…","payload":{…}}
 * </pre>
 */
public final class RealtimeLetters {

    private final ObjectMapper json;

    public RealtimeLetters(ObjectMapper json) {
        this.json = json;
    }

    public String ready() {
        return write(letter("ready"));
    }

    public String pong() {
        return write(letter("pong"));
    }

    public String error(String message) {
        Map<String, Object> m = letter("error");
        m.put("message", message);
        return write(m);
    }

    public String error(String message, String destination) {
        Map<String, Object> m = letter("error");
        m.put("message", message);
        m.put("destination", destination);
        return write(m);
    }

    /** A letter for subscribers of [destination] (R3: what ChatBroadcaster sends). */
    public String message(String destination, String event, Object payload) {
        Map<String, Object> m = letter("message");
        m.put("destination", destination);
        m.put("event", event);
        m.put("payload", payload);
        return write(m);
    }

    /** "type" always FIRST, so a letter is easy to read in logs. */
    private static Map<String, Object> letter(String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        return m;
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write a realtime letter", e);
        }
    }
}
