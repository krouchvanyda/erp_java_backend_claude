package com.company.erp.features.chats.presence;

import com.company.erp.features.chats.service.CallDisconnectHandler;
import com.company.erp.features.chats.ws.ChatBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * R4: {@link UserConnections} with a REAL {@link PresenceService} — both doors
 * mixed. "Offline" and the call rule only when the user's LAST connection
 * (on either door) closes.
 */
class UserConnectionsTest {

    private PresenceService presence;
    private CallDisconnectHandler callDisconnect;
    private UserConnections connections;

    @BeforeEach
    void setUp() {
        presence = new PresenceService(mock(ChatBroadcaster.class));
        callDisconnect = mock(CallDisconnectHandler.class);
        connections = new UserConnections(presence, callDisconnect);
    }

    @Test
    void plainDoor_connect_online_then_close_offline_andTheCallRuleRuns() {
        connections.connected(5L, "rt:a");
        assertThat(presence.statusOf(5L)).isEqualTo(PresenceStatus.ONLINE);

        assertThat(connections.disconnected("rt:a")).isEqualTo(5L);

        assertThat(presence.statusOf(5L)).isEqualTo(PresenceStatus.OFFLINE);
        verify(callDisconnect).onUserConnectionDropped(5L);
    }

    @Test
    void stompPhone_plusPlainTablet_closingOne_isStillOnline_noCallRule() {
        connections.connected(5L, "stomp-session-1");   // old app on the phone (/ws)
        connections.connected(5L, "rt:b");              // Track B app on the tablet (/realtime)

        connections.disconnected("rt:b");

        assertThat(presence.statusOf(5L)).isEqualTo(PresenceStatus.ONLINE);
        verify(callDisconnect, never()).onUserConnectionDropped(anyLong());

        connections.disconnected("stomp-session-1");    // now the LAST one
        assertThat(presence.statusOf(5L)).isEqualTo(PresenceStatus.OFFLINE);
        verify(callDisconnect, times(1)).onUserConnectionDropped(5L);
    }

    @Test
    void unknownConnection_closing_doesNothing() {
        assertThat(connections.disconnected("rt:never-logged-in")).isNull();

        verify(callDisconnect, never()).onUserConnectionDropped(anyLong());
    }

    @Test
    void otherUsers_areNotTouched() {
        connections.connected(5L, "rt:a");
        connections.connected(7L, "rt:c");

        connections.disconnected("rt:a");

        assertThat(presence.statusOf(7L)).isEqualTo(PresenceStatus.ONLINE);
        verify(callDisconnect, never()).onUserConnectionDropped(7L);
    }
}
