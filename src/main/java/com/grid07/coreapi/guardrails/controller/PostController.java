package com.grid07.coreapi.guardrails.controller;

import com.grid07.coreapi.guardrails.dto.request.CommentRequest;
import com.grid07.coreapi.guardrails.dto.request.PostRequest;
import com.grid07.coreapi.guardrails.dto.response.CommentResponse;
import com.grid07.coreapi.guardrails.dto.response.PostResponse;
import com.grid07.coreapi.guardrails.service.CommentService;
import com.grid07.coreapi.guardrails.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Validated
public class PostController {

    private final PostService postService;
    private final CommentService commentService;

    @PostMapping
    public ResponseEntity<PostResponse> createPost(@Valid @RequestBody PostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(request));
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<PostResponse> likePost(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.likePost(postId));
    }

    @PostMapping("/{postId}/comments")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.addComment(postId, request));
    }
}
