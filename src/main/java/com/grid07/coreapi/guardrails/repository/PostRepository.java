package com.grid07.coreapi.guardrails.repository;

import com.grid07.coreapi.guardrails.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PostRepository extends JpaRepository<Post, Long> {

    // Atomic increment (Replace with redis)
    @Modifying
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :postId")
    int incrementLikeCount(Long postId);
}
