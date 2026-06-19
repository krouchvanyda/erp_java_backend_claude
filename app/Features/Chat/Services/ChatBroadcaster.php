<?php

namespace App\Features\Chat\Services;

use App\Features\Chat\Events\CallBroadcast;
use App\Features\Chat\Events\ConversationBroadcast;
use App\Features\Chat\Events\PresenceBroadcast;
use App\Features\Chat\Events\UserBroadcast;

/**
 * Port of the Spring ChatBroadcaster. Centralises every realtime fan-out:
 * services call these methods and never touch broadcast()/event() directly.
 *
 * STOMP destination → Laravel-WebSockets (Pusher protocol) channel mapping:
 *   /topic/conversations/{id}        → conversations.{id}          (public)
 *   /topic/conversations/{id}/call   → conversations.{id}.call     (public)
 *   /topic/presence                  → presence                    (public)
 *   /user/queue/inbox                → user.{userId}.inbox          (private)
 *   /user/queue/calls                → user.{userId}.calls          (private)
 *
 * The wire envelope { "event": "<name>", "payload": <dto> } is preserved.
 */
class ChatBroadcaster
{
    /** Public conversation topic — anyone subscribed sees this. */
    public function toConversation(int $conversationId, string $event, array $payload): void
    {
        broadcast(new ConversationBroadcast($conversationId, $event, $payload));
    }

    /** Per-call topic for ringing → answered → ended state transitions. */
    public function toCall(int $conversationId, string $event, array $payload): void
    {
        broadcast(new CallBroadcast($conversationId, $event, $payload));
    }

    /** Private fan-out to a single user on one of their per-user destinations. */
    public function toUser(int $userId, string $destination, string $event, array $payload): void
    {
        broadcast(new UserBroadcast($userId, $destination, $event, $payload));
    }

    /**
     * Convenience: notify every user in a collection on a private destination.
     *
     * @param iterable<int> $userIds
     */
    public function toUsers($userIds, string $destination, string $event, array $payload): void
    {
        foreach ($userIds as $id) {
            $this->toUser((int) $id, $destination, $event, $payload);
        }
    }

    /** Public presence topic — presence.update for any user's status change. */
    public function presence(array $payload): void
    {
        broadcast(new PresenceBroadcast($payload));
    }
}
