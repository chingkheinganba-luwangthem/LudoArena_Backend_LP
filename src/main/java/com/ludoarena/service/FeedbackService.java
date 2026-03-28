package com.ludoarena.service;

import com.ludoarena.model.Feedback;
import com.ludoarena.model.User;
import com.ludoarena.repository.FeedbackRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class FeedbackService {

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${admin.email:chingkheinganbaluwangthem@gmail.com}")
    private String adminEmail;

    public Feedback submitFeedback(User user, int rating, String comment) {
        Feedback feedback = Feedback.builder()
                .user(user)
                .rating(rating)
                .comment(comment)
                .build();
        
        Feedback savedFeedback = feedbackRepository.save(feedback);
        
        // Send email notification to admin asynchronously to avoid blocking
        if (mailSender != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    SimpleMailMessage message = new SimpleMailMessage();
                    message.setTo(adminEmail);
                    message.setSubject("New LudoArena Feedback: " + rating + " Stars");
                    message.setText("Player: " + user.getName() + " (" + (user.getEmail() != null ? user.getEmail() : "Guest") + ")\n" +
                                   "Rating: " + rating + "/5\n" +
                                   "Comment: " + comment + "\n" +
                                   "Time: " + savedFeedback.getCreatedAt());
                    mailSender.send(message);
                } catch (Exception e) {
                    System.err.println("Failed to send feedback email: " + e.getMessage());
                }
            });
        }
        
        return savedFeedback;
    }

    public List<Feedback> getPublicFeedbacks() {
        return feedbackRepository.findTopFeedbacks(PageRequest.of(0, 3));
    }

    public List<Feedback> getAllFeedbacks() {
        return feedbackRepository.findAll();
    }
}
