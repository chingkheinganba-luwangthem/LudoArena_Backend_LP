package com.ludoarena.repository;

import com.ludoarena.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * User Repository - Data access layer for User entity.
 *
 * Spring Data JPA auto-generates the implementation at runtime.
 * Each method runs within a Tomcat HTTP thread's transactional context.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Find user by email (for login) */
    Optional<User> findByEmail(String email);

    /** Check if email is already registered */
    Boolean existsByEmail(String email);

    /** Find user by name */
    Optional<User> findByName(String name);

    /** Find user by OAuth provider ID */
    Optional<User> findByProviderIdAndAuthProvider(String providerId, com.ludoarena.model.AuthProvider authProvider);
}
