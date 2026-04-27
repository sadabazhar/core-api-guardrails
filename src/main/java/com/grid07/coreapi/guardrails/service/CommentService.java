package com.grid07.coreapi.guardrails.service;

import com.grid07.coreapi.guardrails.common.InteractionType;
import com.grid07.coreapi.guardrails.common.RedisKeys;
import com.grid07.coreapi.guardrails.dto.request.CommentRequest;
import com.grid07.coreapi.guardrails.dto.response.CommentResponse;
import com.grid07.coreapi.guardrails.entity.AuthorType;
import com.grid07.coreapi.guardrails.entity.Comment;
import com.grid07.coreapi.guardrails.entity.Post;
import com.grid07.coreapi.guardrails.exception.GuardrailException;
import com.grid07.coreapi.guardrails.exception.ResourceNotFoundException;
import com.grid07.coreapi.guardrails.exception.TooManyRequestsException;
import com.grid07.coreapi.guardrails.repository.BotRepository;
import com.grid07.coreapi.guardrails.repository.CommentRepository;
import com.grid07.coreapi.guardrails.repository.PostRepository;
import com.grid07.coreapi.guardrails.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final BotRepository botRepository;
    private final GuardrailService guardrailService;
    private final ViralityService viralityService;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public CommentResponse addComment(Long postId, CommentRequest request) {

        log.info("Adding comment to postId={} by authorId={} type={}",
                postId, request.authorId(), request.authorType());

        // Make sure the author (User or Bot) exists
        validateAuthorExists(request.authorId(), request.authorType());

        // Fetch the post, throw error if it doesn't exist
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> {
                    log.warn("Post not found postId={}", postId);
                   return new ResourceNotFoundException("Post not found: " + postId);
                });

        // Compute depth
        int depthLevel = 0;
        Comment parent = null;

        if (request.parentCommentId() != null) {

            parent = commentRepository.findById(request.parentCommentId())
                    .orElseThrow(() -> {
                        log.warn("Parent comment not found id={}", request.parentCommentId());
                        return new ResourceNotFoundException("Parent comment not found");
                    });

            // Ensure parent belongs to same post
            if (!parent.getPost().getId().equals(postId)) {
                log.warn("Parent comment {} does not belong to post {}",
                        request.parentCommentId(), postId);
                throw new GuardrailException("Parent comment does not belong to this post");
            }

            if (parent.getDepthLevel() >= RedisKeys.MAX_DEPTH) {
                throw new GuardrailException("Max depth exceeded");
            }
            depthLevel = parent.getDepthLevel() + 1;
        }

        // Vertical cap
        if (!guardrailService.isDepthAllowed(depthLevel)) {
            log.warn("Depth limit exceeded postId={} depth={}", postId, depthLevel);
            throw new GuardrailException("Max depth 20 exceeded");
        }

        // TRACK STATE FOR ROLLBACK
        boolean botCountIncremented = false;
        boolean cooldownAcquired = false;
        Long targetHumanId = null;

        try{

            // Bot-specific guardrails
            if (request.authorType() == AuthorType.BOT) {

                // HORIZONTAL CAP — reject if post already has 100 bot replies
                if (!guardrailService.tryIncrementBotCount(postId)) {
                    log.warn("Bot reply limit exceeded postId={}", postId);
                    throw new TooManyRequestsException(
                            "Post " + postId + " has reached the maximum of 100 bot replies");
                }
                botCountIncremented = true;

                // COOLDOWN CAP — find the human who owns the post, check cooldown
                targetHumanId = resolveTargetHumanId(post, parent);
                if (targetHumanId != null) {
                    if (!guardrailService.tryAcquireCooldown(request.authorId(), targetHumanId)) {
                        log.warn("Cooldown active botId={} userId={}",
                                request.authorId(), targetHumanId);
                        throw new TooManyRequestsException(
                                "Bot " + request.authorId() + " is on cooldown for this user");
                    }
                    cooldownAcquired = true;
                }

            }

            Comment comment = Comment.builder()
                    .post(post)
                    .parentComment(parent)
                    .authorId(request.authorId())
                    .authorType(request.authorType())
                    .content(request.content())
                    .depthLevel(depthLevel)
                    .build();

            // Ensure DB and Redis consist
            Comment saved = commentRepository.save(comment);

            // Update virality score in Redis
            InteractionType type = (request.authorType() == AuthorType.BOT)
                    ? InteractionType.BOT_REPLY
                    : InteractionType.HUMAN_COMMENT;

            try{
                viralityService.incrementViralityScore(postId, type);
            }catch (Exception e) {
                log.error("Failed to update virality score", e);
            }

            return toCommentResponse(saved);

        } catch (Exception ex) {
            log.error("Error while adding comment, rolling back Redis state", ex);

            // ROLLBACK BOT COUNT
            if (botCountIncremented) {
                redisTemplate.opsForValue().decrement(RedisKeys.botCount(postId));
            }

            // ROLLBACK COOLDOWN
            if (cooldownAcquired && targetHumanId != null) {
                redisTemplate.delete(
                        RedisKeys.cooldown(request.authorId(), targetHumanId)
                );
            }

            throw ex;
        }
    }


    // Helper methods

    // Check if author exists (USER or BOT)
    private void validateAuthorExists(Long authorId, AuthorType authorType) {

        // Choose repository based on author type
        boolean exists = switch (authorType) {
            case USER -> userRepository.existsById(authorId);
            case BOT  -> botRepository.existsById(authorId);
        };

        // Throw error if author not found
        if (!exists) {
            throw new ResourceNotFoundException(authorType + " not found: " + authorId);
        }
    }

    private Long resolveTargetHumanId(Post post, Comment parentComment) {
        // If replying to a comment, the target is that comment's author (if human)
        if (parentComment != null && parentComment.getAuthorType() == AuthorType.USER) {
            return parentComment.getAuthorId();
        }
        // Top-level reply — target is the post author (if human)
        if (post.getAuthorType() == AuthorType.USER) {
            return post.getAuthorId();
        }
        return null; // bot-owned post with no human in the chain — no cooldown needed
    }

    // Convert Comment entity to CommentResponse DTO
    private CommentResponse toCommentResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getPost().getId(),
                comment.getAuthorId(),
                comment.getAuthorType(),
                comment.getContent(),
                comment.getDepthLevel(),
                comment.getCreatedAt()
        );
    }
}
