package org.stepan.audio_disc.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stepan.audio_disc.model.AudioMetadata;
import org.stepan.audio_disc.model.ValidationResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

public class AudioProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger("AudioDisc");
    private static final Set<String> SUPPORTED_FORMATS = Set.of("mp3", "wav", "ogg", "webm", "m4a");
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    // Magic numbers for format detection
    private static final byte[] MP3_MAGIC = {(byte) 0xFF, (byte) 0xFB}; // MP3 frame sync
    private static final byte[] MP3_MAGIC_ALT = {(byte) 0xFF, (byte) 0xF3}; // MP3 frame sync alternative
    private static final byte[] MP3_ID3 = {0x49, 0x44, 0x33}; // "ID3"
    private static final byte[] OGG_MAGIC = {0x4F, 0x67, 0x67, 0x53}; // "OggS"
    private static final byte[] WAV_RIFF = {0x52, 0x49, 0x46, 0x46}; // "RIFF"
    private static final byte[] WAV_WAVE = {0x57, 0x41, 0x56, 0x45}; // "WAVE"
    private static final byte[] WEBM_MAGIC = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3}; // WebM/Matroska container
    private static final byte[] M4A_FTYP = {0x66, 0x74, 0x79, 0x70}; // "ftyp" - MP4/M4A container

    /**
     * Validates the format of an audio file.
     *
     * @param audioData The audio file data
     * @return ValidationResult indicating success or failure with details
     */
    public ValidationResult validateFormat(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return ValidationResult.failure("Audio data is empty");
        }

        if (audioData.length > MAX_FILE_SIZE) {
            return ValidationResult.failure("File size exceeds maximum allowed size of 50MB");
        }

        String detectedFormat = detectFormat(audioData);
        if (detectedFormat == null) {
            return ValidationResult.failure("Unsupported or unrecognized audio format");
        }

        if (!SUPPORTED_FORMATS.contains(detectedFormat)) {
            return ValidationResult.failure("Format '" + detectedFormat + "' is not supported");
        }

        LOGGER.debug("Audio format validated: {}", detectedFormat);
        return ValidationResult.success();
    }

    /**
     * Detects the audio format based on magic numbers.
     *
     * @param audioData The audio file data
     * @return The detected format (mp3, ogg, wav) or null if unknown
     */
    private String detectFormat(byte[] audioData) {
        if (audioData.length < 12) {
            return null;
        }

        // Check for MP3 (ID3 tag or frame sync)
        if (startsWith(audioData, MP3_ID3)) {
            return "mp3";
        }
        if (startsWith(audioData, MP3_MAGIC) || startsWith(audioData, MP3_MAGIC_ALT)) {
            return "mp3";
        }

        // Check for OGG
        if (startsWith(audioData, OGG_MAGIC)) {
            return "ogg";
        }

        // Check for WAV (RIFF header with WAVE format)
        if (startsWith(audioData, WAV_RIFF) && hasWaveFormat(audioData)) {
            return "wav";
        }

        // Check for WebM/Matroska container
        if (startsWith(audioData, WEBM_MAGIC)) {
            return "webm";
        }

        // Check for M4A (MP4 container with ftyp box)
        if (hasM4AFormat(audioData)) {
            return "m4a";
        }

        return null;
    }

    /**
     * Checks if byte array starts with a specific pattern.
     */
    private boolean startsWith(byte[] data, byte[] pattern) {
        if (data.length < pattern.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (data[i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if a RIFF file contains WAVE format identifier.
     */
    private boolean hasWaveFormat(byte[] data) {
        if (data.length < 12) {
            return false;
        }
        // WAVE identifier is at offset 8
        for (int i = 0; i < WAV_WAVE.length; i++) {
            if (data[8 + i] != WAV_WAVE[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if file is M4A format (MP4 container with audio).
     */
    private boolean hasM4AFormat(byte[] data) {
        if (data.length < 12) {
            return false;
        }
        
        // M4A files start with a size field (4 bytes) followed by "ftyp"
        // Check for "ftyp" at offset 4
        for (int i = 0; i < M4A_FTYP.length; i++) {
            if (data[4 + i] != M4A_FTYP[i]) {
                return false;
            }
        }
        
        // Additional check: look for M4A brand identifiers
        if (data.length >= 16) {
            // Common M4A brands: "M4A ", "mp41", "mp42", "isom"
            String brand = new String(data, 8, 4);
            return brand.equals("M4A ") || brand.equals("mp41") || 
                   brand.equals("mp42") || brand.equals("isom");
        }
        
        return true; // If we found "ftyp", assume it's M4A
    }

    /**
     * Extracts metadata from audio file.
     *
     * @param audioData The audio file data
     * @return AudioMetadata containing format, duration, bitrate, etc.
     */
    public AudioMetadata extractMetadata(byte[] audioData) {
        String format = detectFormat(audioData);
        if (format == null) {
            format = "unknown";
        }

        // Basic metadata extraction
        // For a full implementation, you would use libraries like JAudioTagger or similar
        // This is a simplified version that provides basic information
        
        long duration = estimateDuration(audioData, format);
        int bitrate = estimateBitrate(audioData, format);
        int sampleRate = estimateSampleRate(audioData, format);
        String title = extractTitle(audioData, format);

        LOGGER.debug("Extracted metadata - Format: {}, Duration: {}ms, Bitrate: {}kbps", 
                    format, duration, bitrate);

        return new AudioMetadata(format, duration, bitrate, sampleRate, title);
    }

    /**
     * Estimates audio duration in milliseconds.
     * This is a simplified estimation. For accurate results, use proper audio libraries.
     */
    private long estimateDuration(byte[] audioData, String format) {
        // Simplified estimation based on file size and typical bitrates
        long fileSize = audioData.length;
        
        switch (format) {
            case "mp3":
                // Assume average bitrate of 128kbps
                return (fileSize * 8) / 128;
            case "ogg":
                // Assume average bitrate of 128kbps
                return (fileSize * 8) / 128;
            case "m4a":
                // M4A typically has good compression, assume 128kbps
                return (fileSize * 8) / 128;
            case "webm":
                // WebM typically has good compression, assume 128kbps
                return (fileSize * 8) / 128;
            case "wav":
                // WAV is uncompressed, calculate from header if possible
                return estimateWavDuration(audioData);
            default:
                return 0;
        }
    }

    /**
     * Estimates WAV file duration from header.
     */
    private long estimateWavDuration(byte[] audioData) {
        if (audioData.length < 44) {
            return 0;
        }
        
        try {
            // Read sample rate (bytes 24-27)
            int sampleRate = readLittleEndianInt(audioData, 24);
            // Read byte rate (bytes 28-31)
            int byteRate = readLittleEndianInt(audioData, 28);
            
            if (byteRate > 0) {
                long dataSize = audioData.length - 44; // Subtract header size
                return (dataSize * 1000) / byteRate;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to extract WAV duration: {}", e.getMessage());
        }
        
        return 0;
    }

    /**
     * Reads a 32-bit little-endian integer from byte array.
     */
    private int readLittleEndianInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) |
               ((data[offset + 1] & 0xFF) << 8) |
               ((data[offset + 2] & 0xFF) << 16) |
               ((data[offset + 3] & 0xFF) << 24);
    }

    /**
     * Estimates bitrate in kbps.
     */
    private int estimateBitrate(byte[] audioData, String format) {
        // Simplified estimation
        switch (format) {
            case "mp3":
            case "ogg":
            case "m4a":
            case "webm":
                return 128; // Assume standard quality
            case "wav":
                return 1411; // CD quality uncompressed
            default:
                return 0;
        }
    }

    /**
     * Estimates sample rate in Hz.
     */
    private int estimateSampleRate(byte[] audioData, String format) {
        if ("wav".equals(format) && audioData.length >= 28) {
            try {
                return readLittleEndianInt(audioData, 24);
            } catch (Exception e) {
                LOGGER.warn("Failed to extract WAV sample rate: {}", e.getMessage());
            }
        }
        
        // Default to CD quality
        return 44100;
    }

    /**
     * Attempts to extract title from audio metadata.
     */
    private String extractTitle(byte[] audioData, String format) {
        // Simplified - would need proper tag parsing libraries for full implementation
        if ("mp3".equals(format) && startsWith(audioData, MP3_ID3)) {
            // ID3 tag present, but parsing it properly requires a library
            return "Custom Audio";
        }
        return "Custom Audio";
    }

    /**
     * Processes audio data (placeholder for future enhancements like format conversion).
     *
     * @param audioData The raw audio data
     * @return Processed audio data
     */
    public byte[] processAudio(byte[] audioData) {
        // Currently just returns the original data
        // Future enhancements could include:
        // - Format conversion to OGG for consistency
        // - Audio normalization
        // - Compression optimization
        
        LOGGER.debug("Processing audio data: {} bytes", audioData.length);
        return audioData;
    }

    /**
     * Gets the set of supported audio formats.
     *
     * @return Set of supported format extensions
     */
    public static Set<String> getSupportedFormats() {
        return SUPPORTED_FORMATS;
    }

    /**
     * Gets the maximum allowed file size.
     *
     * @return Maximum file size in bytes
     */
    public static long getMaxFileSize() {
        return MAX_FILE_SIZE;
    }
}
