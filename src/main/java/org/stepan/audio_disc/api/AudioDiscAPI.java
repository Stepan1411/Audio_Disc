package org.stepan.audio_disc.api;

import net.minecraft.item.ItemStack;
import org.stepan.audio_disc.model.AudioMetadata;

import java.util.Optional;

/**
 * Public API for the Audio Disc mod.
 * Allows addon developers to interact with the audio system.
 */
public interface AudioDiscAPI {
    
    /**
     * Registers an event listener for audio events.
     * 
     * @param listener The listener to register
     */
    void registerListener(AudioEventListener listener);
    
    /**
     * Unregisters an event listener.
     * 
     * @param listener The listener to unregister
     */
    void unregisterListener(AudioEventListener listener);
    
    /**
     * Registers a stream listener for receiving audio packets.
     * 
     * <p>Stream listeners receive audio data packets during playback, allowing other mods
     * to intercept and retransmit audio regardless of player proximity.</p>
     * 
     * @param listener The stream listener to register
     */
    void registerStreamListener(AudioStreamListener listener);
    
    /**
     * Unregisters a stream listener.
     * 
     * @param listener The stream listener to unregister
     */
    void unregisterStreamListener(AudioStreamListener listener);
    
    /**
     * Gets the metadata for an audio file.
     * 
     * @param audioId The unique audio identifier
     * @return An Optional containing the metadata if found
     */
    Optional<AudioMetadata> getAudioMetadata(String audioId);
    
    /**
     * Checks if a music disc has custom audio attached.
     * 
     * @param disc The music disc ItemStack
     * @return true if the disc has custom audio, false otherwise
     */
    boolean hasCustomAudio(ItemStack disc);
    
    /**
     * Gets the audio ID from a music disc.
     * 
     * @param disc The music disc ItemStack
     * @return An Optional containing the audio ID if present
     */
    Optional<String> getAudioId(ItemStack disc);
}
