package org.stepan.audio_disc.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.item.ItemStack;
import org.stepan.audio_disc.Audio_disc;
import org.stepan.audio_disc.model.AudioData;
import org.stepan.audio_disc.model.AudioMetadata;
import org.stepan.audio_disc.util.NbtUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages persistent storage and retrieval of audio files.
 */
public class AudioStorageManager {
    private static final String AUDIO_DIR = "audio";
    private static final String METADATA_FILE = "metadata.json";
    private static final int CACHE_SIZE = 50; // LRU cache size
    
    private final Path storageDirectory;
    private final Path audioDirectory;
    private final Path metadataFile;
    private final Gson gson;
    
    // LRU cache for frequently accessed audio
    private final Map<String, AudioData> audioCache;
    private final LinkedHashMap<String, Long> accessOrder;
    
    // Metadata index: audioId -> metadata info
    private final Map<String, MetadataEntry> metadataIndex;

    /**
     * Creates a new AudioStorageManager.
     * 
     * @param storageDirectory The base directory for audio storage
     */
    public AudioStorageManager(Path storageDirectory) {
        this.storageDirectory = storageDirectory;
        this.audioDirectory = storageDirectory.resolve(AUDIO_DIR);
        this.metadataFile = storageDirectory.resolve(METADATA_FILE);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        // Initialize LRU cache
        this.audioCache = new ConcurrentHashMap<>();
        this.accessOrder = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                if (size() > CACHE_SIZE) {
                    audioCache.remove(eldest.getKey());
                    return true;
                }
                return false;
            }
        };
        
        this.metadataIndex = new ConcurrentHashMap<>();
        
        initializeStorage();
        loadMetadataIndex();
    }

    /**
     * Initializes the storage directory structure.
     */
    private void initializeStorage() {
        try {
            Files.createDirectories(audioDirectory);
            Audio_disc.LOGGER.info("Audio storage directory initialized at: {}", audioDirectory);
        } catch (IOException e) {
            Audio_disc.LOGGER.error("Failed to create audio storage directory", e);
        }
    }

    /**
     * Loads the metadata index from disk.
     */
    private void loadMetadataIndex() {
        if (!Files.exists(metadataFile)) {
            Audio_disc.LOGGER.info("No existing metadata file found, starting fresh");
            return;
        }
        
        try {
            String json = Files.readString(metadataFile);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            
            root.entrySet().forEach(entry -> {
                String audioId = entry.getKey();
                JsonObject entryObj = entry.getValue().getAsJsonObject();
                
                MetadataEntry metadata = new MetadataEntry(
                    entryObj.get("uploadedBy").getAsString(),
                    entryObj.get("uploadTime").getAsLong(),
                    entryObj.get("format").getAsString(),
                    entryObj.get("duration").getAsLong(),
                    entryObj.get("bitrate").getAsInt(),
                    entryObj.get("sampleRate").getAsInt(),
                    entryObj.get("title").getAsString()
                );
                
                metadataIndex.put(audioId, metadata);
            });
            
            Audio_disc.LOGGER.info("Loaded {} audio entries from metadata index", metadataIndex.size());
        } catch (Exception e) {
            Audio_disc.LOGGER.error("Failed to load metadata index", e);
        }
    }

    /**
     * Saves the metadata index to disk.
     */
    private void saveMetadataIndex() {
        try {
            JsonObject root = new JsonObject();
            
            metadataIndex.forEach((audioId, entry) -> {
                JsonObject entryObj = new JsonObject();
                entryObj.addProperty("uploadedBy", entry.uploadedBy);
                entryObj.addProperty("uploadTime", entry.uploadTime);
                entryObj.addProperty("format", entry.format);
                entryObj.addProperty("duration", entry.duration);
                entryObj.addProperty("bitrate", entry.bitrate);
                entryObj.addProperty("sampleRate", entry.sampleRate);
                entryObj.addProperty("title", entry.title);
                
                root.add(audioId, entryObj);
            });
            
            Files.writeString(metadataFile, gson.toJson(root));
        } catch (IOException e) {
            Audio_disc.LOGGER.error("Failed to save metadata index", e);
        }
    }

    /**
     * Stores audio data and returns a unique identifier.
     * 
     * @param audioData The raw audio bytes
     * @param metadata The audio metadata
     * @param uploadedBy The username of the uploader
     * @return The unique audio ID
     * @throws IOException if storage fails
     */
    public String storeAudio(byte[] audioData, AudioMetadata metadata, String uploadedBy) throws IOException {
        String audioId = UUID.randomUUID().toString();
        long uploadTime = System.currentTimeMillis();
        
        // Determine file extension from format
        String extension = metadata.format().toLowerCase();
        Path audioFile = audioDirectory.resolve(audioId + "." + extension);
        
        // Write audio file
        Files.write(audioFile, audioData);
        
        // Create AudioData object
        AudioData data = new AudioData(audioId, audioData, metadata, uploadedBy, uploadTime);
        
        // Add to cache
        synchronized (accessOrder) {
            audioCache.put(audioId, data);
            accessOrder.put(audioId, System.currentTimeMillis());
        }
        
        // Add to metadata index
        metadataIndex.put(audioId, new MetadataEntry(
            uploadedBy,
            uploadTime,
            metadata.format(),
            metadata.duration(),
            metadata.bitrate(),
            metadata.sampleRate(),
            metadata.title()
        ));
        
        // Save metadata index
        saveMetadataIndex();
        
        Audio_disc.LOGGER.info("Stored audio file: {} ({})", audioId, metadata.title());
        
        return audioId;
    }

    /**
     * Retrieves audio data by ID.
     * 
     * @param audioId The unique audio identifier
     * @return An Optional containing the AudioData if found, empty otherwise
     */
    public Optional<AudioData> getAudio(String audioId) {
        if (audioId == null || audioId.isBlank()) {
            return Optional.empty();
        }
        
        // Check cache first
        AudioData cached = audioCache.get(audioId);
        if (cached != null) {
            synchronized (accessOrder) {
                accessOrder.put(audioId, System.currentTimeMillis());
            }
            return Optional.of(cached);
        }
        
        // Check if metadata exists
        MetadataEntry metadataEntry = metadataIndex.get(audioId);
        if (metadataEntry == null) {
            return Optional.empty();
        }
        
        // Load from disk
        String extension = metadataEntry.format.toLowerCase();
        Path audioFile = audioDirectory.resolve(audioId + "." + extension);
        
        if (!Files.exists(audioFile)) {
            Audio_disc.LOGGER.warn("Audio file not found: {}", audioId);
            return Optional.empty();
        }
        
        try {
            byte[] audioData = Files.readAllBytes(audioFile);
            AudioMetadata metadata = new AudioMetadata(
                metadataEntry.format,
                metadataEntry.duration,
                metadataEntry.bitrate,
                metadataEntry.sampleRate,
                metadataEntry.title
            );
            
            AudioData data = new AudioData(
                audioId,
                audioData,
                metadata,
                metadataEntry.uploadedBy,
                metadataEntry.uploadTime
            );
            
            // Add to cache
            synchronized (accessOrder) {
                audioCache.put(audioId, data);
                accessOrder.put(audioId, System.currentTimeMillis());
            }
            
            return Optional.of(data);
        } catch (IOException e) {
            Audio_disc.LOGGER.error("Failed to load audio file: {}", audioId, e);
            return Optional.empty();
        }
    }

    /**
     * Attaches audio to a music disc item.
     * 
     * @param disc The music disc ItemStack
     * @param audioId The audio identifier
     */
    public void attachToDisc(ItemStack disc, String audioId) {
        MetadataEntry metadataEntry = metadataIndex.get(audioId);
        if (metadataEntry == null) {
            throw new IllegalArgumentException("Audio ID not found: " + audioId);
        }
        
        AudioMetadata metadata = new AudioMetadata(
            metadataEntry.format,
            metadataEntry.duration,
            metadataEntry.bitrate,
            metadataEntry.sampleRate,
            metadataEntry.title
        );
        
        NbtUtils.attachAudioToItem(
            disc,
            audioId,
            metadataEntry.uploadedBy,
            metadataEntry.uploadTime,
            metadata
        );
    }

    /**
     * Attaches audio to a music disc item with uploader name.
     * 
     * @param disc The music disc ItemStack
     * @param audioId The audio identifier
     * @param uploadedBy The name of the uploader
     */
    public void attachToDisc(ItemStack disc, String audioId, String uploadedBy) {
        attachToDisc(disc, audioId);
    }

    /**
     * Clears audio data from a music disc item.
     * 
     * @param disc The music disc ItemStack
     */
    public void clearDiscAudio(ItemStack disc) {
        NbtUtils.clearAudioData(disc);
    }

    /**
     * Retrieves the audio ID from a music disc item.
     * 
     * @param disc The music disc ItemStack
     * @return An Optional containing the audio ID if present, empty otherwise
     */
    public Optional<String> getDiscAudioId(ItemStack disc) {
        return NbtUtils.getAudioId(disc);
    }

    /**
     * Sets a custom title for a music disc.
     * 
     * @param disc The music disc ItemStack
     * @param customTitle The custom title to set
     */
    public void setCustomTitle(ItemStack disc, String customTitle) {
        NbtUtils.setCustomTitle(disc, customTitle);
    }

    /**
     * Cleans up unused audio files.
     * This method should be called periodically to remove orphaned files.
     * 
     * @param referencedAudioIds Set of audio IDs that are currently in use
     * @return The number of files removed
     */
    public int cleanup(Set<String> referencedAudioIds) {
        int removedCount = 0;
        
        try {
            // Get all audio IDs from metadata index
            Set<String> allAudioIds = new HashSet<>(metadataIndex.keySet());
            
            // Find orphaned audio IDs
            Set<String> orphanedIds = new HashSet<>(allAudioIds);
            orphanedIds.removeAll(referencedAudioIds);
            
            // Remove orphaned files
            for (String audioId : orphanedIds) {
                MetadataEntry entry = metadataIndex.get(audioId);
                if (entry != null) {
                    String extension = entry.format.toLowerCase();
                    Path audioFile = audioDirectory.resolve(audioId + "." + extension);
                    
                    if (Files.exists(audioFile)) {
                        Files.delete(audioFile);
                        removedCount++;
                    }
                }
                
                // Remove from metadata index
                metadataIndex.remove(audioId);
                
                // Remove from cache
                audioCache.remove(audioId);
                synchronized (accessOrder) {
                    accessOrder.remove(audioId);
                }
            }
            
            if (removedCount > 0) {
                saveMetadataIndex();
                Audio_disc.LOGGER.info("Cleaned up {} unused audio files", removedCount);
            }
        } catch (IOException e) {
            Audio_disc.LOGGER.error("Error during cleanup", e);
        }
        
        return removedCount;
    }

    /**
     * Gets the total number of stored audio files.
     * 
     * @return The count of audio files
     */
    public int getAudioCount() {
        return metadataIndex.size();
    }

    /**
     * Gets all audio IDs.
     * 
     * @return A set of all audio IDs
     */
    public Set<String> getAllAudioIds() {
        return new HashSet<>(metadataIndex.keySet());
    }

    /**
     * Internal class for storing metadata index entries.
     */
    private static class MetadataEntry {
        final String uploadedBy;
        final long uploadTime;
        final String format;
        final long duration;
        final int bitrate;
        final int sampleRate;
        final String title;

        MetadataEntry(String uploadedBy, long uploadTime, String format, long duration,
                     int bitrate, int sampleRate, String title) {
            this.uploadedBy = uploadedBy;
            this.uploadTime = uploadTime;
            this.format = format;
            this.duration = duration;
            this.bitrate = bitrate;
            this.sampleRate = sampleRate;
            this.title = title;
        }
    }
}
