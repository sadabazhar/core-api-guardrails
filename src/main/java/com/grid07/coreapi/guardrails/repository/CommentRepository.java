package com.grid07.coreapi.guardrails.repository;

import com.grid07.coreapi.guardrails.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPost_Id(Long postId);
    long countByPost_Id(Long postId);
}