package org.stepan.audio_disc.model;

import java.util.Set;

/**
 * Represents the result of audio format validation.
 * 
 * @param valid Whether the validation passed
 * @param errorMessage The error message if validation failed, null otherwise
 * @param supportedFormats The set of supported audio formats
 */
public record ValidationResult(
    boolean valid,
    String errorMessage,
    Set<String> supportedFormats
) {
    /**
     * Creates a successful validation result.
     * 
     * @return A ValidationResult indicating success
     */
    public static ValidationResult success() {
        return new ValidationResult(true, null, Set.of());
    }
    
    /**
     * Creates a failed validation result with an error message.
     * 
     * @param message The error message describing why validation failed
     * @return A ValidationResult indicating failure
     */
    public static ValidationResult failure(String message) {
        return new ValidationResult(false, message, Set.of("mp3", "ogg", "wav"));
    }
    
    /**
     * Creates a failed validation result with a custom set of supported formats.
     * 
     * @param message The error message describing why validation failed
     * @param supportedFormats The set of supported formats
     * @return A ValidationResult indicating failure
     */
    public static ValidationResult failure(String message, Set<String> supportedFormats) {
        return new ValidationResult(false, message, supportedFormats);
    }
}
