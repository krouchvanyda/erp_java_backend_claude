package com.company.erp.features.chats.repository;

import com.company.erp.features.chats.entity.ChatCall;
import com.company.erp.features.chats.entity.CallStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatCallRepository extends JpaRepository<ChatCall, Long> {

    @EntityGraph(attributePaths = {"participants"})
    Optional<ChatCall> findWithParticipantsById(Long id);

    @EntityGraph(attributePaths = {"participants"})
    @Query("""
            SELECT c FROM ChatCall c
            JOIN ChatCallParticipant p ON p.call = c
            WHERE p.id.userId = :userId
            ORDER BY c.startedAt DESC
           """)
    Page<ChatCall> findAllForUser(@Param("userId") Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"participants"})
    Page<ChatCall> findByConversationIdOrderByStartedAtDesc(Long conversationId, Pageable pageable);

    /** Calls still RINGING that were started before the cutoff — auto-cancel candidates. */
    @EntityGraph(attributePaths = {"participants"})
    @Query("SELECT c FROM ChatCall c WHERE c.status = com.company.erp.features.chats.entity.CallStatus.RINGING " +
           "AND c.startedAt < :cutoff")
    List<ChatCall> findStaleRinging(@Param("cutoff") Instant cutoff);

    /**
     * RINGING calls in which the given user is still a RINGING participant —
     * i.e. an unanswered incoming ring for that user. Used to auto-end the ring
     * when their connection drops. Excludes calls the user is the caller of.
     */
    @EntityGraph(attributePaths = {"participants"})
    @Query("""
            SELECT DISTINCT c FROM ChatCall c
            JOIN ChatCallParticipant p ON p.call = c
            WHERE c.status = com.company.erp.features.chats.entity.CallStatus.RINGING
              AND p.id.userId = :userId
              AND p.status = com.company.erp.features.chats.entity.ParticipantStatus.RINGING
              AND c.callerId <> :userId
           """)
    List<ChatCall> findRingingForCallee(@Param("userId") Long userId);

    /** Used by the busy-signal check: is this user mid-call right now? */
    @Query("""
            SELECT COUNT(c) > 0 FROM ChatCall c
            JOIN ChatCallParticipant p ON p.call = c
            WHERE p.id.userId = :userId
              AND c.status IN :openStatuses
              AND p.status IN :openParticipantStatuses
           """)
    boolean existsActiveCallForUser(@Param("userId") Long userId,
                                    @Param("openStatuses") java.util.Set<CallStatus> openStatuses,
                                    @Param("openParticipantStatuses") java.util.Set<com.company.erp.features.chats.entity.ParticipantStatus> openParticipantStatuses);
}
