package com.ludoarena.controller;

import com.ludoarena.model.Feedback;
import com.ludoarena.model.User;
import com.ludoarena.repository.UserRepository;
import com.ludoarena.service.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<?> submitFeedback(@AuthenticationPrincipal User user, @RequestBody Map<String, Object> payload) {
        if (user == null) {
            return ResponseEntity.status(401).body("User not authenticated");
        }

        Object ratingObj = payload.getOrDefault("rating", 0);
        int rating = (ratingObj instanceof Number) ? ((Number) ratingObj).intValue() : 0;
        String comment = (String) payload.getOrDefault("comment", "");

        if (rating < 1 || rating > 5) {
            return ResponseEntity.badRequest().body("Rating must be between 1 and 5");
        }

        Feedback feedback = feedbackService.submitFeedback(user, rating, comment);
        return ResponseEntity.ok(feedback);
    }

    @GetMapping("/public")
    public ResponseEntity<List<Feedback>> getPublicFeedbacks() {
        return ResponseEntity.ok(feedbackService.getPublicFeedbacks());
    }

    @GetMapping("/all")
    public ResponseEntity<List<Feedback>> getAllFeedbacks() {
        return ResponseEntity.ok(feedbackService.getAllFeedbacks());
    }
}
