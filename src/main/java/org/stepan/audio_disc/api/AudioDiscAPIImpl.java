package org.stepan.audio_disc.api;

import net.minecraft.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stepan.audio_disc.Audio_disc;
import org.stepan.audio_disc.model.AudioData;
import org.stepan.audio_disc.model.AudioMetadata;
import org.stepan.audio_disc.storage.AudioStorageManager;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Implementation of the AudioDiscAPI.
 * This is a singleton that provides access to the audio system for addon developers.
 */
public class AudioDiscAPIImpl implements AudioDiscAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger("AudioDisc");
    private static AudioDiscAPIImpl instance;
    
    private final Set<AudioEventListener> listeners;

    private AudioDiscAPIImpl() {
        this.listeners = new CopyOnWriteArraySet<>();
    }

    /**
     * Gets the singleton instance of the API.
     * 
     * @return The API instance
     */
    public static AudioDiscAPIImpl getInstance() {
        if (instance == null) {
            synchronized (AudioDiscAPIImpl.class) {
                if (instance == null) {
                    instance = new AudioDiscAPIImpl();
                }
            }
        }
        return instance;
    }

    @Override
    public void registerListener(AudioEventListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        
        listeners.add(listener);
        LOGGER.info("Registered audio event listener: {}", listener.getClass().getName());
    }

    @Override
    public void unregisterListener(AudioEventListener listener) {
        if (listener == null) {
            return;
        }
        
        listeners.remove(listener);
        LOGGER.info("Unregistered audio event listener: {}", listener.getClass().getName());
    }

    @Override
    public Optional<AudioMetadata> getAudioMetadata(String audioId) {
        if (audioId == null || audioId.isBlank()) {
            return Optional.empty();
        }
        
        AudioStorageManager storageManager = Audio_disc.getStorageManager();
        if (storageManager == null) {
            return Optional.empty();
        }
        
        Optional<AudioData> audioData = storageManager.getAudio(audioId);
        return audioData.map(AudioData::metadata);
    }

    @Override
    public boolean hasCustomAudio(ItemStack disc) {
        if (disc == null || disc.isEmpty()) {
            return false;
        }
        
        AudioStorageManager storageManager = Audio_disc.getStorageManager();
        if (storageManager == null) {
            return false;
        }
        
        return storageManager.getDiscAudioId(disc).isPresent();
    }

    @Override
    public Optional<String> getAudioId(ItemStack disc) {
        if (disc == null || disc.isEmpty()) {
            return Optional.empty();
        }
        
        AudioStorageManager storageManager = Audio_disc.getStorageManager();
        if (storageManager == null) {
            return Optional.empty();
        }
        
        return storageManager.getDiscAudioId(disc);
    }

    /**
     * Fires a playback start event to all registered listeners.
     * 
     * @param event The event to fire
     */
    public void firePlaybackStartEvent(PlaybackStartEvent event) {
        for (AudioEventListener listener : listeners) {
            try {
                listener.onPlaybackStart(event);
            } catch (Exception e) {
                LOGGER.error("Error in listener {} handling playback start event", 
                           listener.getClass().getName(), e);
            }
        }
    }

    /**
     * Fires a playback stop event to all registered listeners.
     * 
     * @param event The event to fire
     */
    public void firePlaybackStopEvent(PlaybackStopEvent event) {
        for (AudioEventListener listener : listeners) {
            try {
                listener.onPlaybackStop(event);
            } catch (Exception e) {
                LOGGER.error("Error in listener {} handling playback stop event", 
                           listener.getClass().getName(), e);
            }
        }
    }

    /**
     * Fires an audio upload event to all registered listeners.
     * 
     * @param event The event to fire
     */
    public void fireAudioUploadEvent(AudioUploadEvent event) {
        for (AudioEventListener listener : listeners) {
            try {
                listener.onAudioUpload(event);
            } catch (Exception e) {
                LOGGER.error("Error in listener {} handling audio upload event", 
                           listener.getClass().getName(), e);
            }
        }
    }

    /**
     * Calls all listeners to potentially modify audio before playback.
     * 
     * @param context The modification context
     * @return The final audio modification to apply
     */
    public AudioModification callModifyAudio(AudioModificationContext context) {
        AudioModification finalModification = AudioModification.noChange();
        
        for (AudioEventListener listener : listeners) {
            try {
                AudioModification modification = listener.modifyAudio(context);
                
                if (modification.isCancelled()) {
                    LOGGER.info("Audio playback cancelled by listener: {}", 
                              listener.getClass().getName());
                    return modification;
                }
                
                // Apply modifications sequentially
                if (modification.isModified()) {
                    finalModification = modification;
                    LOGGER.debug("Audio modified by listener: {}", listener.getClass().getName());
                }
                
            } catch (Exception e) {
                LOGGER.error("Error in listener {} modifying audio", 
                           listener.getClass().getName(), e);
            }
        }
        
        return finalModification;
    }

    /**
     * Gets the number of registered listeners.
     * 
     * @return The listener count
     */
    public int getListenerCount() {
        return listeners.size();
    }

    /**
     * Clears all registered listeners.
     * This should only be used for testing or cleanup.
     */
    public void clearListeners() {
        listeners.clear();
        LOGGER.info("Cleared all audio event listeners");
    }
}
