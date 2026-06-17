package org.aiincubator.ilmai.profiles.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    @Query("select p.userId from Profile p")
    List<UUID> findAllUserIds();

    @Query("select p.userId from Profile p where p.dailyReminder is not null")
    List<UUID> findUserIdsWithDailyReminder();
}
