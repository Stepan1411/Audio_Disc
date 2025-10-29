package org.stepan.audio_disc.api;

/**
 * Represents modifications to audio before playback.
 */
public class AudioModification {
    private final boolean modified;
    private final byte[] modifiedData;
    private final float volumeMultiplier;
    private final boolean cancelled;

    private AudioModification(boolean modified, byte[] modifiedData, float volumeMultiplier, boolean cancelled) {
        this.modified = modified;
        this.modifiedData = modifiedData;
        this.volumeMultiplier = volumeMultiplier;
        this.cancelled = cancelled;
    }

    /**
     * Creates a modification with no changes.
     * 
     * @return An AudioModification with no changes
     */
    public static AudioModification noChange() {
        return new AudioModification(false, null, 1.0f, false);
    }

    /**
     * Creates a modification with modified audio data.
     * 
     * @param modifiedData The modified audio data
     * @return An AudioModification with the new data
     */
    public static AudioModification withModifiedData(byte[] modifiedData) {
        return new AudioModification(true, modifiedData, 1.0f, false);
    }

    /**
     * Creates a modification with a volume multiplier.
     * 
     * @param volumeMultiplier The volume multiplier (1.0 = normal, 0.5 = half volume, 2.0 = double volume)
     * @return An AudioModification with the volume change
     */
    public static AudioModification withVolume(float volumeMultiplier) {
        return new AudioModification(true, null, volumeMultiplier, false);
    }

    /**
     * Creates a modification that cancels playback.
     * 
     * @return An AudioModification that cancels playback
     */
    public static AudioModification cancel() {
        return new AudioModification(false, null, 1.0f, true);
    }

    /**
     * Checks if the audio was modified.
     * 
     * @return true if modified, false otherwise
     */
    public boolean isModified() {
        return modified;
    }

    /**
     * Gets the modified audio data.
     * 
     * @return The modified data, or null if not modified
     */
    public byte[] getModifiedData() {
        return modifiedData;
    }

    /**
     * Gets the volume multiplier.
     * 
     * @return The volume multiplier
     */
    public float getVolumeMultiplier() {
        return volumeMultiplier;
    }

    /**
     * Checks if playback should be cancelled.
     * 
     * @return true if cancelled, false otherwise
     */
    public boolean isCancelled() {
        return cancelled;
    }
}
