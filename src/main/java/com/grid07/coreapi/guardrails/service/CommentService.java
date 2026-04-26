package com.grid07.coreapi.guardrails.service;

import com.grid07.coreapi.guardrails.dto.request.CommentRequest;
import com.grid07.coreapi.guardrails.dto.response.CommentResponse;
import com.grid07.coreapi.guardrails.entity.AuthorType;
import com.grid07.coreapi.guardrails.entity.Comment;
import com.grid07.coreapi.guardrails.entity.Post;
import com.grid07.coreapi.guardrails.exception.ResourceNotFoundException;
import com.grid07.coreapi.guardrails.repository.BotRepository;
import com.grid07.coreapi.guardrails.repository.CommentRepository;
import com.grid07.coreapi.guardrails.repository.PostRepository;
import com.grid07.coreapi.guardrails.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final BotRepository botRepository;

    @Transactional
    public CommentResponse addComment(Long postId, CommentRequest request) {

        // Fetch the post, throw error if it doesn't exist
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        // Make sure the author (User or Bot) exists
        validateAuthorExists(request.authorId(), request.authorType());

        Comment comment = Comment.builder()
                .post(post)
                .authorId(request.authorId())
                .authorType(request.authorType())
                .content(request.content())
                .depthLevel(request.depthLevel())
                .build();

        Comment saved = commentRepository.save(comment);
        return toCommentResponse(saved);
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
