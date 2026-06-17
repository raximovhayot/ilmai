package org.aiincubator.ilmai.telegram.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TelegramQuizPollRepository extends JpaRepository<TelegramQuizPoll, UUID> {

    Optional<TelegramQuizPoll> findByPollId(String pollId);
}
