package com.agilerisk.repository;

import com.agilerisk.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByStoryId(Long storyId);
}
