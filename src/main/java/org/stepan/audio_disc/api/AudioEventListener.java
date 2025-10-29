package org.stepan.audio_disc.api;

/**
 * Listener interface for audio events.
 * Implement this interface to receive notifications about audio playback and uploads.
 */
public interface AudioEventListener {
    
    /**
     * Called when audio playback starts.
     * 
     * @param event The playback start event
     */
    default void onPlaybackStart(PlaybackStartEvent event) {
        // Default implementation does nothing
    }
    
    /**
     * Called when audio playback stops.
     * 
     * @param event The playback stop event
     */
    default void onPlaybackStop(PlaybackStopEvent event) {
        // Default implementation does nothing
    }
    
    /**
     * Called when a player uploads audio to a disc.
     * 
     * @param event The audio upload event
     */
    default void onAudioUpload(AudioUploadEvent event) {
        // Default implementation does nothing
    }
    
    /**
     * Called before audio playback starts, allowing modification of the audio.
     * Return an AudioModification to change the audio data, volume, or cancel playback.
     * 
     * @param context The modification context containing audio data and metadata
     * @return An AudioModification describing any changes to make
     */
    default AudioModification modifyAudio(AudioModificationContext context) {
        return AudioModification.noChange();
    }
}
