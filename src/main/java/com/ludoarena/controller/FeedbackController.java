package com.ludoarena.controller;

import com.ludoarena.model.Feedback;
import com.ludoarena.model.User;
import com.ludoarena.repository.UserRepository;
import com.ludoarena.service.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> submitFeedback(Authentication authentication, @RequestBody Map<String, Object> payload) {
        if (authentication == null) {
            return ResponseEntity.status(401).body("User not authenticated");
        }

        String userEmail = authentication.getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        int rating = (int) payload.getOrDefault("rating", 0);
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
