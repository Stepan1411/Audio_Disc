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
