package com.ludoarena.model;

/**
 * Enum representing the authentication provider used by a user.
 * Supports local email/password, Google OAuth, Facebook OAuth, and guest access.
 */
public enum AuthProvider {
    LOCAL,      // Email + Password registration
    GOOGLE,     // Google OAuth2 login
    FACEBOOK,   // Facebook OAuth2 login
    GUEST       // Guest login (no account required)
}
