package com.ludoarena.repository;

import com.ludoarena.model.Feedback;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    
    // Get top feedbacks for landing page
    @Query("SELECT f FROM Feedback f ORDER BY f.rating DESC, f.createdAt DESC")
    List<Feedback> findTopFeedbacks(Pageable pageable);
}
