package com.grid07.coreapi.guardrails.service;

import com.grid07.coreapi.guardrails.common.InteractionType;
import com.grid07.coreapi.guardrails.dto.request.PostRequest;
import com.grid07.coreapi.guardrails.dto.response.PostResponse;
import com.grid07.coreapi.guardrails.entity.AuthorType;
import com.grid07.coreapi.guardrails.entity.Post;
import com.grid07.coreapi.guardrails.exception.ResourceNotFoundException;
import com.grid07.coreapi.guardrails.repository.BotRepository;
import com.grid07.coreapi.guardrails.repository.PostRepository;
import com.grid07.coreapi.guardrails.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final BotRepository botRepository;
    private final ViralityService viralityService;

    public PostResponse createPost(PostRequest request) {

        log.info("Creating post for authorId={} type={}",
                request.authorId(), request.authorType());

        // Make sure the author (User or Bot) exists before creating post
        validateAuthorExists(request.authorId(), request.authorType());

        Post post = Post.builder()
                .content(request.content())
                .authorId(request.authorId())
                .authorType(request.authorType())
                .build();

        Post saved = postRepository.save(post);

        log.info("Post created successfully with id={}", saved.getId());

        return toPostResponse(saved);
    }

    @Transactional
    public PostResponse likePost(Long postId) {

        log.info("Liking postId={}", postId);

        // Increment like count in DB
        int updatedRows = postRepository.incrementLikeCount(postId);

        if (updatedRows == 0) {
            log.warn("Post not found while liking postId={}", postId);
            throw new ResourceNotFoundException("Post not found: " + postId);
        }

        // Fetch updated post, throw error if not found
        Post updated = postRepository.findById(postId).orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        // Update virality score in Redis
        viralityService.incrementViralityScore(postId, InteractionType.HUMAN_LIKE);

        log.info("Post liked successfully postId={} likeCount={}",
                postId, updated.getLikeCount());

        return toPostResponse(updated);
    }


    // Helper methods

    // Check if author exists based on type (USER or BOT)
    private void validateAuthorExists(Long authorId, AuthorType authorType) {

        // Decide which repository to check using switch expression
        boolean exists = switch (authorType) {
            case USER -> userRepository.existsById(authorId);
            case BOT  -> botRepository.existsById(authorId);
        };

        // If author doesn't exist, throw exception
        if (!exists) {
            throw new ResourceNotFoundException(authorType + " not found: " + authorId);
        }
    }

    // Convert Post entity to PostResponse DTO
    private PostResponse toPostResponse(Post post) {
        return new PostResponse(
                post.getId(),
                post.getContent(),
                post.getAuthorId(),
                post.getAuthorType(),
                post.getLikeCount(),
                post.getCreatedAt()
        );
    }
}
