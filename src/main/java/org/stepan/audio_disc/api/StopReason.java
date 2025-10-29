package org.stepan.audio_disc.api;

/**
 * Reasons why audio playback stopped.
 */
public enum StopReason {
    /**
     * The disc was removed from the jukebox.
     */
    DISC_REMOVED,
    
    /**
     * The audio playback completed naturally.
     */
    PLAYBACK_COMPLETE,
    
    /**
     * The jukebox was broken.
     */
    JUKEBOX_BROKEN,
    
    /**
     * Playback was manually stopped.
     */
    MANUAL_STOP
}
