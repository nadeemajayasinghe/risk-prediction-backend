package com.agilerisk.repository;

import com.agilerisk.domain.UserStory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserStoryRepository extends JpaRepository<UserStory, Long> {
    List<UserStory> findBySprintId(Long sprintId);
}
