package com.agilerisk.repository;

import com.agilerisk.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByStoryId(Long storyId);
}
