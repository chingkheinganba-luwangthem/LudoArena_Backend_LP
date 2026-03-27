package com.ludoarena.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @NotBlank(message = "Name cannot be empty")
    private String name;
    private String avatarUrl;
}
