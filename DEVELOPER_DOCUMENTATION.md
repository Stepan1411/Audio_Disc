# 🔧 Custom Audio Disc - Developer Documentation

Comprehensive documentation for developers working with the Custom Audio Disc mod API and extending its functionality.

## 📋 Table of Contents

1. [Getting Started](#getting-started)
2. [API Overview](#api-overview)
3. [Event System](#event-system)
4. [Audio Stream Interception](#audio-stream-interception)
5. [Storage System](#storage-system)
6. [Configuration](#configuration)
7. [Examples](#examples)
8. [Best Practices](#best-practices)
9. [Troubleshooting](#troubleshooting)

---

## 🚀 Getting Started

### Dependencies

Add to your `build.gradle`:

```gradle
repositories {
    maven {
        name = "Modrinth"
        url = "https://api.modrinth.com/maven"
    }
}

dependencies {
    modImplementation "maven.modrinth:custom-audio-disc:${audio_disc_version}"
    
    // Required dependencies
    modImplementation "de.maxhenkel.voicechat:voicechat-api:2.5.0"
    include "de.maxhenkel.voicechat:voicechat-api:2.5.0"
}
```

### Basic Setup

```java
@Mod("your_mod_id")
public class YourMod implements ModInitializer {
    
    @Override
    public void onInitialize() {
        // Register your event listeners
        AudioDiscAPI api = AudioDiscAPIImpl.getInstance();
        api.registerListener(new YourAudioListener());
    }
}
```

---

## 🎯 API Overview

### Core Interfaces

#### AudioDiscAPI
Main API interface for interacting with the Custom Audio Disc system.

```java
public interface AudioDiscAPI {
    /**
     * Registers an event listener for audio events.
     */
    void registerListener(AudioEventListener listener);
    
    /**
     * Unregisters an event listener.
     */
    void unregisterListener(AudioEventListener listener);
    
    /**
     * Gets audio metadata by ID.
     */
    Optional<AudioMetadata> getAudioMetadata(String audioId);
    
    /**
     * Checks if a disc has custom audio.
     */
    boolean hasCustomAudio(ItemStack disc);
    
    /**
     * Gets the audio ID from a custom disc.
     */
    Optional<String> getDiscAudioId(ItemStack disc);
    
    /**
     * Gets all active playbacks.
     */
    Map<BlockPos, ActivePlayback> getActivePlaybacks();
    
    /**
     * Gets playback at specific position.
     */
    Optional<ActivePlayback> getPlaybackAt(BlockPos position);
}
```

#### Accessing the API

```java
// Get the API instance
AudioDiscAPI api = AudioDiscAPIImpl.getInstance();

// Check if a disc has custom audio
ItemStack disc = player.getMainHandStack();
if (api.hasCustomAudio(disc)) {
    Optional<String> audioId = api.getDiscAudioId(disc);
    audioId.ifPresent(id -> {
        Optional<AudioMetadata> metadata = api.getAudioMetadata(id);
        // Do something with the metadata
    });
}
```

---

## 🎧 Event System

### AudioEventListener Interface

```java
public interface AudioEventListener {
    /**
     * Called when audio playback starts.
     */
    default void onPlaybackStart(PlaybackStartEvent event) {}
    
    /**
     * Called when audio playback stops.
     */
    default void onPlaybackStop(PlaybackStopEvent event) {}
    
    /**
     * Called when audio is uploaded to a disc.
     */
    default void onAudioUpload(AudioUploadEvent event) {}
    
    /**
     * Allows modification of audio before playback.
     * Return AudioModification.noChange() to leave unchanged.
     */
    default AudioModification modifyAudio(AudioModificationContext context) {
        return AudioModification.noChange();
    }
}
```

### Event Classes

#### PlaybackStartEvent
```java
public record PlaybackStartEvent(
    BlockPos jukeboxPos,           // Position of the jukebox
    ServerWorld world,             // World where playback started
    String audioId,                // Unique audio identifier
    AudioMetadata metadata,        // Audio metadata (title, duration, etc.)
    long timestamp                 // When playback started (System.currentTimeMillis())
) {}
```

#### PlaybackStopEvent
```java
public record PlaybackStopEvent(
    BlockPos jukeboxPos,           // Position of the jukebox
    ServerWorld world,             // World where playback stopped
    String audioId,                // Unique audio identifier
    long playbackDuration,         // How long it played (milliseconds)
    StopReason reason              // Why it stopped
) {}

public enum StopReason {
    DISC_REMOVED,      // Player removed the disc
    PLAYBACK_COMPLETE, // Audio finished naturally
    JUKEBOX_BROKEN,    // Jukebox was destroyed
    MANUAL_STOP        // Stopped programmatically
}
```

#### AudioUploadEvent
```java
public record AudioUploadEvent(
    ServerPlayerEntity player,     // Player who uploaded
    ItemStack disc,                // The disc item
    String audioId,                // Generated audio ID
    AudioMetadata metadata,        // Audio metadata
    long timestamp                 // Upload timestamp
) {}
```

### Example Event Listener

```java
public class MyAudioListener implements AudioEventListener {
    
    @Override
    public void onPlaybackStart(PlaybackStartEvent event) {
        System.out.println("Audio started playing at " + event.jukeboxPos());
        System.out.println("Title: " + event.metadata().title());
        System.out.println("Duration: " + formatDuration(event.metadata().duration()));
        
        // Send message to nearby players
        List<ServerPlayerEntity> nearbyPlayers = getNearbyPlayers(event.world(), event.jukeboxPos(), 64);
        for (ServerPlayerEntity player : nearbyPlayers) {
            player.sendMessage(Text.literal("♪ Now playing: " + event.metadata().title()), false);
        }
    }
    
    @Override
    public void onPlaybackStop(PlaybackStopEvent event) {
        System.out.println("Audio stopped. Reason: " + event.reason());
        System.out.println("Played for: " + formatDuration(event.playbackDuration()));
    }
    
    @Override
    public void onAudioUpload(AudioUploadEvent event) {
        System.out.println(event.player().getName().getString() + " uploaded: " + event.metadata().title());
        
        // Log to database, send notifications, etc.
        logAudioUpload(event);
    }
    
    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
```

---

## 🎵 Audio Stream Interception

### AudioModification System

The most powerful feature - intercept and modify audio streams before playback.

#### AudioModificationContext
```java
public class AudioModificationContext {
    private final String audioId;
    private final byte[] originalData;
    private final AudioMetadata metadata;
    private final BlockPos jukeboxPos;
    private final ServerWorld world;
    
    // Getters for all fields
    public String getAudioId() { return audioId; }
    public byte[] getOriginalData() { return originalData; }
    public AudioMetadata getMetadata() { return metadata; }
    public BlockPos getJukeboxPos() { return jukeboxPos; }
    public ServerWorld getWorld() { return world; }
    
    // Helper methods
    public List<ServerPlayerEntity> getNearbyPlayers(double radius) {
        // Returns players within radius of jukebox
    }
    
    public boolean isNightTime() {
        return world.getTimeOfDay() > 13000 && world.getTimeOfDay() < 23000;
    }
}
```

#### AudioModification
```java
public class AudioModification {
    private final boolean cancelled;
    private final boolean modified;
    private final byte[] modifiedData;
    private final AudioMetadata modifiedMetadata;
    
    // Static factory methods
    public static AudioModification noChange() {
        return new AudioModification(false, false, null, null);
    }
    
    public static AudioModification cancel() {
        return new AudioModification(true, false, null, null);
    }
    
    public static AudioModification modify(byte[] newData) {
        return new AudioModification(false, true, newData, null);
    }
    
    public static AudioModification modify(byte[] newData, AudioMetadata newMetadata) {
        return new AudioModification(false, true, newData, newMetadata);
    }
    
    // Getters
    public boolean isCancelled() { return cancelled; }
    public boolean isModified() { return modified; }
    public byte[] getModifiedData() { return modifiedData; }
    public AudioMetadata getModifiedMetadata() { return modifiedMetadata; }
}
```

### Advanced Audio Modification Examples

#### 1. Volume Control Based on Time
```java
@Override
public AudioModification modifyAudio(AudioModificationContext context) {
    // Reduce volume at night
    if (context.isNightTime()) {
        byte[] quieterAudio = reduceVolume(context.getOriginalData(), 0.5f);
        return AudioModification.modify(quieterAudio);
    }
    return AudioModification.noChange();
}

private byte[] reduceVolume(byte[] audioData, float volumeMultiplier) {
    // Simple volume reduction (this is a basic example)
    byte[] result = new byte[audioData.length];
    for (int i = 0; i < audioData.length; i += 2) {
        // Assuming 16-bit audio
        short sample = (short) ((audioData[i] & 0xFF) | (audioData[i + 1] << 8));
        sample = (short) (sample * volumeMultiplier);
        result[i] = (byte) (sample & 0xFF);
        result[i + 1] = (byte) (sample >> 8);
    }
    return result;
}
```

#### 2. Audio Effects Based on Location
```java
@Override
public AudioModification modifyAudio(AudioModificationContext context) {
    BlockPos pos = context.getJukeboxPos();
    ServerWorld world = context.getWorld();
    
    // Add reverb in caves
    if (pos.getY() < 40 && isInCave(world, pos)) {
        byte[] reverbAudio = addReverb(context.getOriginalData());
        return AudioModification.modify(reverbAudio);
    }
    
    // Muffle underwater
    if (world.getBlockState(pos.up()).getBlock() == Blocks.WATER) {
        byte[] muffledAudio = addLowPassFilter(context.getOriginalData());
        return AudioModification.modify(muffledAudio);
    }
    
    return AudioModification.noChange();
}
```

#### 3. Permission-Based Audio Filtering
```java
@Override
public AudioModification modifyAudio(AudioModificationContext context) {
    List<ServerPlayerEntity> nearbyPlayers = context.getNearbyPlayers(64);
    
    // Check if any nearby players have "explicit_content" permission
    boolean hasExplicitPermission = nearbyPlayers.stream()
        .anyMatch(player -> hasPermission(player, "audiodisc.explicit"));
    
    if (!hasExplicitPermission && isExplicitContent(context.getMetadata())) {
        // Replace with clean version or cancel
        return AudioModification.cancel();
    }
    
    return AudioModification.noChange();
}
```

#### 4. Dynamic Audio Replacement
```java
@Override
public AudioModification modifyAudio(AudioModificationContext context) {
    String audioId = context.getAudioId();
    
    // Replace specific audio with seasonal variants
    if (audioId.equals("christmas_song") && !isChristmasSeason()) {
        byte[] alternativeAudio = loadAlternativeAudio("winter_song");
        AudioMetadata newMetadata = new AudioMetadata(
            "ogg", 180000, 128, 44100, "Winter Song"
        );
        return AudioModification.modify(alternativeAudio, newMetadata);
    }
    
    return AudioModification.noChange();
}
```

---

## 💾 Storage System

### Accessing Audio Data

```java
public class AudioDataAccess {
    
    public void accessAudioData() {
        AudioStorageManager storage = Audio_disc.getStorageManager();
        
        // Get audio by ID
        Optional<AudioData> audioData = storage.getAudio("some-audio-id");
        audioData.ifPresent(data -> {
            System.out.println("Title: " + data.metadata().title());
            System.out.println("Size: " + data.data().length + " bytes");
            System.out.println("Uploaded by: " + data.uploadedBy());
        });
        
        // Get all audio IDs
        Set<String> allAudioIds = storage.getAllAudioIds();
        System.out.println("Total audio files: " + allAudioIds.size());
    }
}
```

### Custom Storage Extensions

```java
public class CustomStorageExtension {
    
    public void addCustomMetadata(String audioId, Map<String, Object> customData) {
        // Store custom metadata alongside audio
        Path customMetadataFile = getCustomMetadataPath(audioId);
        try {
            Files.writeString(customMetadataFile, gson.toJson(customData));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public Optional<Map<String, Object>> getCustomMetadata(String audioId) {
        Path customMetadataFile = getCustomMetadataPath(audioId);
        if (Files.exists(customMetadataFile)) {
            try {
                String json = Files.readString(customMetadataFile);
                return Optional.of(gson.fromJson(json, Map.class));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return Optional.empty();
    }
}
```

---

## ⚙️ Configuration

### Custom Configuration

```java
public class MyAudioConfig {
    
    @ConfigEntry.Gui.RequiresRestart
    public boolean enableAudioEffects = true;
    
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    public int nightVolumeReduction = 50;
    
    @ConfigEntry.Gui.CollapsibleObject
    public AudioEffectsConfig effects = new AudioEffectsConfig();
    
    public static class AudioEffectsConfig {
        public boolean reverbInCaves = true;
        public boolean underwaterMuffling = true;
        public float reverbStrength = 0.3f;
    }
}
```

### Reading Audio Disc Config

```java
public void readAudioDiscConfig() {
    AudioDiscConfig config = Audio_disc.getConfig();
    
    long maxFileSize = config.getMaxFileSize();
    int downloadTimeout = config.getDownloadTimeout();
    Set<String> supportedFormats = config.getSupportedFormats();
    
    System.out.println("Max file size: " + maxFileSize + " bytes");
    System.out.println("Download timeout: " + downloadTimeout + " seconds");
    System.out.println("Supported formats: " + supportedFormats);
}
```

---

## 🎮 Playback Control

### Programmatic Playback Control

```java
public class PlaybackController {
    
    public void controlPlayback() {
        PlaybackManager manager = Audio_disc.getPlaybackManager();
        
        // Start playback programmatically
        ServerWorld world = getServerWorld();
        BlockPos jukeboxPos = new BlockPos(100, 64, 100);
        ItemStack customDisc = createCustomDisc("my-audio-id");
        
        boolean started = manager.startPlayback(world, jukeboxPos, customDisc);
        if (started) {
            System.out.println("Playback started successfully");
        }
        
        // Stop playback
        manager.stopPlayback(jukeboxPos);
        
        // Check if playing
        if (manager.isPlaying(jukeboxPos)) {
            System.out.println("Audio is currently playing");
        }
        
        // Get active playback info
        Optional<ActivePlayback> playback = manager.getPlayback(jukeboxPos);
        playback.ifPresent(pb -> {
            System.out.println("Playing: " + pb.getMetadata().title());
            System.out.println("Elapsed: " + pb.getElapsedTime() + "ms");
        });
    }
}
```

### Custom Audio Sources

```java
public class CustomAudioSource {
    
    public void createCustomAudioDisc() {
        // Generate or load custom audio data
        byte[] audioData = generateSineWave(440, 5000); // 440Hz for 5 seconds
        
        AudioMetadata metadata = new AudioMetadata(
            "ogg",           // format
            5000,            // duration in ms
            128,             // bitrate
            44100,           // sample rate
            "Generated Tone" // title
        );
        
        // Store the audio
        AudioStorageManager storage = Audio_disc.getStorageManager();
        String audioId = storage.storeAudio(audioData, metadata, "system");
        
        // Create disc with the audio
        ItemStack disc = new ItemStack(Items.MUSIC_DISC_13);
        storage.attachToDisc(disc, audioId);
        
        System.out.println("Created custom disc with ID: " + audioId);
    }
    
    private byte[] generateSineWave(double frequency, int durationMs) {
        int sampleRate = 44100;
        int samples = (int) (sampleRate * durationMs / 1000.0);
        byte[] audioData = new byte[samples * 2]; // 16-bit audio
        
        for (int i = 0; i < samples; i++) {
            double time = i / (double) sampleRate;
            short sample = (short) (Short.MAX_VALUE * Math.sin(2 * Math.PI * frequency * time));
            
            audioData[i * 2] = (byte) (sample & 0xFF);
            audioData[i * 2 + 1] = (byte) (sample >> 8);
        }
        
        return audioData;
    }
}
```

---

## 🔌 Integration Examples

### 1. Music Bot Integration

```java
@Mod("music_bot_integration")
public class MusicBotIntegration implements AudioEventListener {
    
    private final Map<String, String> discordChannels = new HashMap<>();
    
    @Override
    public void onPlaybackStart(PlaybackStartEvent event) {
        // Notify Discord when music starts
        String channelId = getDiscordChannelForWorld(event.world());
        if (channelId != null) {
            sendDiscordMessage(channelId, 
                "🎵 Now playing: **" + event.metadata().title() + "** " +
                "at " + formatPosition(event.jukeboxPos()));
        }
    }
    
    @Override
    public AudioModification modifyAudio(AudioModificationContext context) {
        // Allow Discord users to vote on audio
        if (hasActiveVote(context.getAudioId())) {
            VoteResult vote = getVoteResult(context.getAudioId());
            if (vote.shouldSkip()) {
                return AudioModification.cancel();
            }
        }
        return AudioModification.noChange();
    }
}
```

### 2. Economy Integration

```java
@Mod("audio_economy")
public class AudioEconomyIntegration implements AudioEventListener {
    
    @Override
    public void onAudioUpload(AudioUploadEvent event) {
        // Charge player for uploading audio
        EconomyAPI economy = getEconomyAPI();
        UUID playerId = event.player().getUuid();
        
        if (economy.getBalance(playerId) >= 100) {
            economy.withdraw(playerId, 100);
            event.player().sendMessage(Text.literal("§a$100 charged for audio upload"), false);
        } else {
            // Cancel the upload somehow (this would need additional API)
            event.player().sendMessage(Text.literal("§cInsufficient funds for audio upload"), false);
        }
    }
    
    @Override
    public void onPlaybackStart(PlaybackStartEvent event) {
        // Pay the uploader when their music plays
        String audioId = event.audioId();
        Optional<AudioData> audioData = Audio_disc.getStorageManager().getAudio(audioId);
        
        audioData.ifPresent(data -> {
            String uploaderName = data.uploadedBy();
            ServerPlayerEntity uploader = getPlayerByName(uploaderName);
            if (uploader != null) {
                EconomyAPI economy = getEconomyAPI();
                economy.deposit(uploader.getUuid(), 10);
                uploader.sendMessage(Text.literal("§a+$10 for your music being played!"), false);
            }
        });
    }
}
```

### 3. Permissions Integration

```java
@Mod("audio_permissions")
public class AudioPermissionsIntegration implements AudioEventListener {
    
    @Override
    public AudioModification modifyAudio(AudioModificationContext context) {
        List<ServerPlayerEntity> nearbyPlayers = context.getNearbyPlayers(64);
        
        // Check permissions for explicit content
        boolean hasExplicitPerm = nearbyPlayers.stream()
            .allMatch(player -> hasPermission(player, "audiodisc.explicit"));
        
        if (!hasExplicitPerm && isExplicitContent(context.getMetadata())) {
            return AudioModification.cancel();
        }
        
        // Check volume permissions
        boolean hasLoudPerm = nearbyPlayers.stream()
            .allMatch(player -> hasPermission(player, "audiodisc.loud"));
        
        if (!hasLoudPerm && isLoudAudio(context.getOriginalData())) {
            byte[] quieterAudio = reduceVolume(context.getOriginalData(), 0.7f);
            return AudioModification.modify(quieterAudio);
        }
        
        return AudioModification.noChange();
    }
    
    private boolean hasPermission(ServerPlayerEntity player, String permission) {
        // Integration with your permission system
        return PermissionAPI.hasPermission(player, permission);
    }
}
```

### 4. Analytics Integration

```java
@Mod("audio_analytics")
public class AudioAnalyticsIntegration implements AudioEventListener {
    
    private final Map<String, AudioStats> audioStats = new ConcurrentHashMap<>();
    
    @Override
    public void onPlaybackStart(PlaybackStartEvent event) {
        String audioId = event.audioId();
        audioStats.computeIfAbsent(audioId, k -> new AudioStats())
                  .incrementPlayCount();
        
        // Log to analytics service
        logEvent("audio_play_start", Map.of(
            "audio_id", audioId,
            "title", event.metadata().title(),
            "world", event.world().getRegistryKey().getValue().toString(),
            "position", event.jukeboxPos().toString()
        ));
    }
    
    @Override
    public void onPlaybackStop(PlaybackStopEvent event) {
        String audioId = event.audioId();
        long duration = event.playbackDuration();
        
        audioStats.computeIfAbsent(audioId, k -> new AudioStats())
                  .addPlayDuration(duration);
        
        // Log completion rate
        Optional<AudioMetadata> metadata = Audio_disc.getAPI().getAudioMetadata(audioId);
        metadata.ifPresent(meta -> {
            double completionRate = (double) duration / meta.duration();
            logEvent("audio_play_stop", Map.of(
                "audio_id", audioId,
                "completion_rate", completionRate,
                "stop_reason", event.reason().toString()
            ));
        });
    }
    
    public AudioStats getAudioStats(String audioId) {
        return audioStats.get(audioId);
    }
    
    public static class AudioStats {
        private int playCount = 0;
        private long totalPlayTime = 0;
        
        public void incrementPlayCount() { playCount++; }
        public void addPlayDuration(long duration) { totalPlayTime += duration; }
        
        public int getPlayCount() { return playCount; }
        public long getTotalPlayTime() { return totalPlayTime; }
        public long getAveragePlayTime() { 
            return playCount > 0 ? totalPlayTime / playCount : 0; 
        }
    }
}
```

---

## 🛠️ Utility Classes

### Audio Processing Utilities

```java
public class AudioUtils {
    
    /**
     * Analyzes audio data and returns information about it.
     */
    public static AudioAnalysis analyzeAudio(byte[] audioData) {
        // Implement audio analysis
        return new AudioAnalysis(
            detectBPM(audioData),
            detectKey(audioData),
            detectGenre(audioData),
            calculateLoudness(audioData)
        );
    }
    
    /**
     * Applies a simple low-pass filter to audio data.
     */
    public static byte[] applyLowPassFilter(byte[] audioData, float cutoffFreq) {
        // Implement low-pass filter
        // This is a simplified example
        byte[] filtered = new byte[audioData.length];
        // ... filter implementation
        return filtered;
    }
    
    /**
     * Normalizes audio volume to a target level.
     */
    public static byte[] normalizeVolume(byte[] audioData, float targetLevel) {
        // Calculate current RMS level
        float currentLevel = calculateRMS(audioData);
        float gain = targetLevel / currentLevel;
        
        return applyGain(audioData, gain);
    }
    
    /**
     * Converts audio format (basic implementation).
     */
    public static byte[] convertFormat(byte[] audioData, String fromFormat, String toFormat) {
        // Use FFmpeg or other audio processing library
        // This would be implemented using the existing FFmpegManager
        FFmpegManager ffmpeg = Audio_disc.getFFmpegManager();
        return ffmpeg.convertAudio(audioData, fromFormat, toFormat).join();
    }
}

public record AudioAnalysis(
    int bpm,
    String key,
    String genre,
    float loudness
) {}
```

### Disc Creation Utilities

```java
public class DiscUtils {
    
    /**
     * Creates a custom disc with specified audio and metadata.
     */
    public static ItemStack createCustomDisc(String audioId, String customTitle) {
        AudioStorageManager storage = Audio_disc.getStorageManager();
        
        // Create a new disc
        ItemStack disc = new ItemStack(Items.MUSIC_DISC_13);
        
        // Attach audio
        storage.attachToDisc(disc, audioId);
        
        // Set custom title if provided
        if (customTitle != null) {
            NbtCompound nbt = disc.getOrCreateNbt();
            NbtCompound audioNbt = nbt.getCompound("audio_disc");
            audioNbt.putString("custom_title", customTitle);
        }
        
        return disc;
    }
    
    /**
     * Clones a custom disc.
     */
    public static ItemStack cloneCustomDisc(ItemStack originalDisc) {
        if (!Audio_disc.getAPI().hasCustomAudio(originalDisc)) {
            return originalDisc.copy();
        }
        
        Optional<String> audioId = Audio_disc.getAPI().getDiscAudioId(originalDisc);
        if (audioId.isPresent()) {
            ItemStack newDisc = new ItemStack(originalDisc.getItem());
            Audio_disc.getStorageManager().attachToDisc(newDisc, audioId.get());
            
            // Copy custom NBT data
            if (originalDisc.hasNbt()) {
                newDisc.setNbt(originalDisc.getNbt().copy());
            }
            
            return newDisc;
        }
        
        return originalDisc.copy();
    }
    
    /**
     * Gets formatted display name for a custom disc.
     */
    public static Text getDiscDisplayName(ItemStack disc) {
        if (!Audio_disc.getAPI().hasCustomAudio(disc)) {
            return disc.getName();
        }
        
        Optional<String> audioId = Audio_disc.getAPI().getDiscAudioId(disc);
        if (audioId.isPresent()) {
            Optional<AudioMetadata> metadata = Audio_disc.getAPI().getAudioMetadata(audioId.get());
            if (metadata.isPresent()) {
                String title = getCustomTitle(disc).orElse(metadata.get().title());
                return Text.literal("§6♪ " + title);
            }
        }
        
        return disc.getName();
    }
    
    private static Optional<String> getCustomTitle(ItemStack disc) {
        if (disc.hasNbt()) {
            NbtCompound nbt = disc.getNbt();
            if (nbt.contains("audio_disc")) {
                NbtCompound audioNbt = nbt.getCompound("audio_disc");
                if (audioNbt.contains("custom_title")) {
                    return Optional.of(audioNbt.getString("custom_title"));
                }
            }
        }
        return Optional.empty();
    }
}
```

---

## 🧪 Testing

### Unit Testing Audio Events

```java
public class AudioEventTest {
    
    @Test
    public void testAudioEventFiring() {
        // Mock components
        AudioEventListener mockListener = Mockito.mock(AudioEventListener.class);
        AudioDiscAPIImpl.getInstance().registerListener(mockListener);
        
        // Create test event
        PlaybackStartEvent event = new PlaybackStartEvent(
            new BlockPos(0, 64, 0),
            mockWorld,
            "test-audio-id",
            new AudioMetadata("ogg", 180000, 128, 44100, "Test Song"),
            System.currentTimeMillis()
        );
        
        // Fire event
        AudioDiscAPIImpl.getInstance().firePlaybackStartEvent(event);
        
        // Verify listener was called
        Mockito.verify(mockListener).onPlaybackStart(event);
    }
    
    @Test
    public void testAudioModification() {
        AudioEventListener listener = new AudioEventListener() {
            @Override
            public AudioModification modifyAudio(AudioModificationContext context) {
                if (context.getMetadata().title().contains("test")) {
                    return AudioModification.cancel();
                }
                return AudioModification.noChange();
            }
        };
        
        AudioModificationContext context = new AudioModificationContext(
            "test-id",
            new byte[1024],
            new AudioMetadata("ogg", 180000, 128, 44100, "test song"),
            new BlockPos(0, 64, 0),
            mockWorld
        );
        
        AudioModification result = listener.modifyAudio(context);
        assertTrue(result.isCancelled());
    }
}
```

### Integration Testing

```java
public class AudioIntegrationTest {
    
    @Test
    public void testFullAudioFlow() {
        // Test complete flow: upload -> store -> play -> stop
        
        // 1. Create test audio data
        byte[] testAudio = createTestAudioData();
        AudioMetadata metadata = new AudioMetadata("ogg", 5000, 128, 44100, "Test Audio");
        
        // 2. Store audio
        AudioStorageManager storage = Audio_disc.getStorageManager();
        String audioId = storage.storeAudio(testAudio, metadata, "test-player");
        
        // 3. Create disc
        ItemStack disc = new ItemStack(Items.MUSIC_DISC_13);
        storage.attachToDisc(disc, audioId);
        
        // 4. Verify disc has audio
        assertTrue(Audio_disc.getAPI().hasCustomAudio(disc));
        assertEquals(audioId, Audio_disc.getAPI().getDiscAudioId(disc).orElse(""));
        
        // 5. Test playback
        PlaybackManager playback = Audio_disc.getPlaybackManager();
        BlockPos pos = new BlockPos(0, 64, 0);
        boolean started = playback.startPlayback(mockWorld, pos, disc);
        
        assertTrue(started);
        assertTrue(playback.isPlaying(pos));
        
        // 6. Stop playback
        playback.stopPlayback(pos);
        assertFalse(playback.isPlaying(pos));
    }
}
```

---

## 📚 Best Practices

### 1. Event Listener Performance
```java
// ✅ Good - Fast event handling
@Override
public void onPlaybackStart(PlaybackStartEvent event) {
    // Do quick operations synchronously
    logEvent(event);
    
    // Do heavy operations asynchronously
    CompletableFuture.runAsync(() -> {
        processAudioAnalytics(event);
        updateDatabase(event);
    });
}

// ❌ Bad - Blocking event handling
@Override
public void onPlaybackStart(PlaybackStartEvent event) {
    // This blocks the main thread!
    heavyDatabaseOperation(event);
    slowNetworkCall(event);
}
```

### 2. Audio Modification Safety
```java
// ✅ Good - Safe audio modification
@Override
public AudioModification modifyAudio(AudioModificationContext context) {
    try {
        byte[] originalData = context.getOriginalData();
        
        // Always validate input
        if (originalData == null || originalData.length == 0) {
            return AudioModification.noChange();
        }
        
        // Create defensive copy
        byte[] modifiedData = Arrays.copyOf(originalData, originalData.length);
        
        // Apply modifications safely
        applyEffects(modifiedData);
        
        return AudioModification.modify(modifiedData);
        
    } catch (Exception e) {
        // Never let exceptions escape
        LOGGER.error("Error modifying audio", e);
        return AudioModification.noChange();
    }
}
```

### 3. Resource Management
```java
// ✅ Good - Proper resource cleanup
public class MyAudioExtension implements AudioEventListener {
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<String, AudioProcessor> processors = new ConcurrentHashMap<>();
    
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        processors.values().forEach(AudioProcessor::cleanup);
        processors.clear();
    }
}
```

### 4. Error Handling
```java
// ✅ Good - Comprehensive error handling
@Override
public void onAudioUpload(AudioUploadEvent event) {
    try {
        processUpload(event);
    } catch (SecurityException e) {
        LOGGER.warn("Security violation in audio upload: {}", e.getMessage());
        notifyAdmins("Potential security issue", event.player());
    } catch (IOException e) {
        LOGGER.error("IO error processing upload", e);
        event.player().sendMessage(Text.literal("§cUpload processing failed"), false);
    } catch (Exception e) {
        LOGGER.error("Unexpected error in audio upload", e);
        // Don't expose internal errors to players
        event.player().sendMessage(Text.literal("§cAn error occurred"), false);
    }
}
```

---

## 🐛 Troubleshooting

### Common Issues

#### 1. Events Not Firing
```java
// Check if listener is registered
AudioDiscAPI api = AudioDiscAPIImpl.getInstance();
if (!api.getRegisteredListeners().contains(myListener)) {
    api.registerListener(myListener);
}

// Verify mod loading order
@Mod("my_mod")
public class MyMod implements ModInitializer {
    @Override
    public void onInitialize() {
        // Register after server starts
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            AudioDiscAPIImpl.getInstance().registerListener(new MyListener());
        });
    }
}
```

#### 2. Audio Modification Not Working
```java
// Debug audio modification
@Override
public AudioModification modifyAudio(AudioModificationContext context) {
    LOGGER.info("Modifying audio: {}", context.getAudioId());
    LOGGER.info("Original size: {} bytes", context.getOriginalData().length);
    
    AudioModification result = doModification(context);
    
    LOGGER.info("Modification result: cancelled={}, modified={}", 
                result.isCancelled(), result.isModified());
    
    return result;
}
```

#### 3. Memory Leaks
```java
// Use WeakReferences for caches
private final Map<String, WeakReference<AudioData>> audioCache = new ConcurrentHashMap<>();

public Optional<AudioData> getCachedAudio(String audioId) {
    WeakReference<AudioData> ref = audioCache.get(audioId);
    if (ref != null) {
        AudioData data = ref.get();
        if (data != null) {
            return Optional.of(data);
        } else {
            // Clean up dead reference
            audioCache.remove(audioId);
        }
    }
    return Optional.empty();
}
```

### Debug Commands

```java
// Add debug commands for development
public class AudioDebugCommands {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("audiodebug")
            .requires(source -> source.hasPermissionLevel(2))
            .then(literal("listeners")
                .executes(context -> {
                    AudioDiscAPI api = AudioDiscAPIImpl.getInstance();
                    context.getSource().sendFeedback(
                        Text.literal("Registered listeners: " + api.getRegisteredListeners().size()),
                        false
                    );
                    return 1;
                }))
            .then(literal("playbacks")
                .executes(context -> {
                    PlaybackManager manager = Audio_disc.getPlaybackManager();
                    int count = manager.getActivePlaybackCount();
                    context.getSource().sendFeedback(
                        Text.literal("Active playbacks: " + count),
                        false
                    );
                    return 1;
                }))
        );
    }
}
```

---

## 📖 Additional Resources

### Useful Links
- [Simple Voice Chat API Documentation](https://modrepo.de/minecraft/voicechat/wiki)
- [Fabric API Documentation](https://fabricmc.net/wiki/documentation:fabric_api)
- [Audio Processing in Java](https://docs.oracle.com/javase/tutorial/sound/)

### Sample Projects
- [Audio Visualizer Addon](https://github.com/example/audio-visualizer)
- [Music Bot Integration](https://github.com/example/music-bot-integration)
- [Audio Effects Pack](https://github.com/example/audio-effects)

### Community
- [Discord Server](https://discord.gg/example)
- [GitHub Discussions](https://github.com/example/custom-audio-disc/discussions)
- [Wiki](https://github.com/example/custom-audio-disc/wiki)

---

*This documentation is maintained by the Custom Audio Disc development team. For questions or contributions, please visit our GitHub repository.*

---


## 🎵 Audio Stream Listener API

### Overview

The Audio Stream Listener API allows mods to receive real-time audio packets during playback, enabling features like:
- Audio retransmission (e.g., radio stations)
- Real-time audio analysis
- Audio recording and logging
- Cross-server audio streaming
- Custom audio effects processing

This API works independently of player proximity - audio packets are sent to listeners even when no players are nearby the jukebox.

### AudioStreamListener Interface

```java
public interface AudioStreamListener {
    /**
     * Called for each audio packet during playback (~20ms intervals).
     * Audio data is PCM 16-bit signed format at 48kHz sample rate.
     * 
     * @param event The audio packet event containing audio data and metadata
     */
    void onAudioPacket(AudioPacketEvent event);
    
    /**
     * Called when audio playback starts at a jukebox.
     * 
     * @param world The server world
     * @param jukeboxPos The jukebox position
     * @param discName The name of the disc
     * @param audioId The unique audio identifier
     */
    default void onStreamStart(ServerWorld world, BlockPos jukeboxPos, 
                              String discName, String audioId) {}
    
    /**
     * Called when audio playback stops at a jukebox.
     * 
     * @param world The server world
     * @param jukeboxPos The jukebox position
     * @param audioId The unique audio identifier
     */
    default void onStreamStop(ServerWorld world, BlockPos jukeboxPos, String audioId) {}
}
```

### AudioPacketEvent

```java
public class AudioPacketEvent {
    private final ServerWorld world;
    private final BlockPos jukeboxPos;
    private final byte[] audioData;      // PCM 16-bit signed, 48kHz, mono
    private final String discName;
    private final int sampleRate;        // Usually 48000 Hz
    private final String audioId;
    
    // Getters
    public ServerWorld getWorld() { return world; }
    public BlockPos getJukeboxPosition() { return jukeboxPos; }
    public byte[] getAudioData() { return audioData; }
    public String getDiscName() { return discName; }
    public int getSampleRate() { return sampleRate; }
    public String getAudioId() { return audioId; }
}
```

### Registration

```java
// Register a stream listener
AudioDiscAPI api = AudioDiscAPIImpl.getInstance();
api.registerStreamListener(new MyStreamListener());

// Unregister when done
api.unregisterStreamListener(listener);
```

### Example: Simple Radio Mod Integration

```java
public class RadioIntegration implements AudioStreamListener {
    
    private final RadioManager radioManager;
    
    public RadioIntegration(RadioManager radioManager) {
        this.radioManager = radioManager;
    }
    
    @Override
    public void onAudioPacket(AudioPacketEvent event) {
        // Find nearby radio transmitter
        RadioStation transmitter = findNearbyTransmitter(
            event.getWorld(), 
            event.getJukeboxPosition()
        );
        
        if (transmitter != null && transmitter.isActive()) {
            // Get all receivers tuned to this transmitter's channel
            List<RadioStation> receivers = radioManager.getReceiversOnChannel(
                transmitter.getChannel()
            );
            
            // Retransmit audio to all receivers
            for (RadioStation receiver : receivers) {
                retransmitAudio(receiver, event.getAudioData(), event.getSampleRate());
            }
        }
    }
    
    @Override
    public void onStreamStart(ServerWorld world, BlockPos jukeboxPos, 
                             String discName, String audioId) {
        RadioStation transmitter = findNearbyTransmitter(world, jukeboxPos);
        if (transmitter != null && transmitter.isActive()) {
            // Update radio station display
            transmitter.setNowPlaying(discName);
            
            // Notify all receivers
            List<RadioStation> receivers = radioManager.getReceiversOnChannel(
                transmitter.getChannel()
            );
            for (RadioStation receiver : receivers) {
                receiver.displayMessage("Now playing: " + discName);
            }
        }
    }
    
    @Override
    public void onStreamStop(ServerWorld world, BlockPos jukeboxPos, String audioId) {
        RadioStation transmitter = findNearbyTransmitter(world, jukeboxPos);
        if (transmitter != null) {
            transmitter.setNowPlaying(null);
            
            List<RadioStation> receivers = radioManager.getReceiversOnChannel(
                transmitter.getChannel()
            );
            for (RadioStation receiver : receivers) {
                receiver.displayMessage("Playback stopped");
            }
        }
    }
    
    private RadioStation findNearbyTransmitter(ServerWorld world, BlockPos jukeboxPos) {
        // Search for transmitters within 5 blocks of the jukebox
        int radius = 5;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = jukeboxPos.add(x, y, z);
                    RadioStation station = radioManager.getStationAt(world, pos);
                    if (station != null && station.isTransmitter()) {
                        return station;
                    }
                }
            }
        }
        return null;
    }
    
    private void retransmitAudio(RadioStation receiver, byte[] audioData, int sampleRate) {
        // Get all players near the receiver
        List<ServerPlayerEntity> nearbyPlayers = receiver.getNearbyPlayers();
        
        // Send audio to each player using Voice Chat API
        for (ServerPlayerEntity player : nearbyPlayers) {
            voiceChatApi.playLocationalAudio(
                player,
                receiver.getPosition(),
                audioData,
                sampleRate
            );
        }
    }
}
```

### Example: Audio Logger

```java
public class AudioLogger implements AudioStreamListener {
    
    private final Map<String, AudioRecording> activeRecordings = new ConcurrentHashMap<>();
    
    @Override
    public void onStreamStart(ServerWorld world, BlockPos jukeboxPos, 
                             String discName, String audioId) {
        String recordingId = generateRecordingId(world, jukeboxPos);
        AudioRecording recording = new AudioRecording(discName, audioId);
        activeRecordings.put(recordingId, recording);
        
        LOGGER.info("Started recording audio: {} at {}", discName, jukeboxPos);
    }
    
    @Override
    public void onAudioPacket(AudioPacketEvent event) {
        String recordingId = generateRecordingId(event.getWorld(), event.getJukeboxPosition());
        AudioRecording recording = activeRecordings.get(recordingId);
        
        if (recording != null) {
            recording.addPacket(event.getAudioData());
        }
    }
    
    @Override
    public void onStreamStop(ServerWorld world, BlockPos jukeboxPos, String audioId) {
        String recordingId = generateRecordingId(world, jukeboxPos);
        AudioRecording recording = activeRecordings.remove(recordingId);
        
        if (recording != null) {
            // Save recording to file
            saveRecording(recording);
            LOGGER.info("Saved recording: {} ({} packets)", 
                recording.getDiscName(), recording.getPacketCount());
        }
    }
    
    private String generateRecordingId(ServerWorld world, BlockPos pos) {
        return world.getRegistryKey().getValue() + "_" + pos.toShortString();
    }
    
    private void saveRecording(AudioRecording recording) {
        Path outputPath = Paths.get("recordings", recording.getAudioId() + ".pcm");
        try {
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, recording.getAllData());
        } catch (IOException e) {
            LOGGER.error("Failed to save recording", e);
        }
    }
    
    private static class AudioRecording {
        private final String discName;
        private final String audioId;
        private final List<byte[]> packets = new ArrayList<>();
        
        public AudioRecording(String discName, String audioId) {
            this.discName = discName;
            this.audioId = audioId;
        }
        
        public void addPacket(byte[] data) {
            packets.add(data.clone());
        }
        
        public byte[] getAllData() {
            int totalSize = packets.stream().mapToInt(p -> p.length).sum();
            byte[] result = new byte[totalSize];
            int offset = 0;
            for (byte[] packet : packets) {
                System.arraycopy(packet, 0, result, offset, packet.length);
                offset += packet.length;
            }
            return result;
        }
        
        public String getDiscName() { return discName; }
        public String getAudioId() { return audioId; }
        public int getPacketCount() { return packets.size(); }
    }
}
```

### Example: Real-time Audio Analysis

```java
public class AudioAnalyzer implements AudioStreamListener {
    
    private final Map<String, AudioStats> statsMap = new ConcurrentHashMap<>();
    
    @Override
    public void onStreamStart(ServerWorld world, BlockPos jukeboxPos, 
                             String discName, String audioId) {
        String key = getKey(world, jukeboxPos);
        statsMap.put(key, new AudioStats(discName));
    }
    
    @Override
    public void onAudioPacket(AudioPacketEvent event) {
        String key = getKey(event.getWorld(), event.getJukeboxPosition());
        AudioStats stats = statsMap.get(key);
        
        if (stats != null) {
            // Analyze audio packet
            byte[] audioData = event.getAudioData();
            
            // Calculate RMS level (volume)
            double rms = calculateRMS(audioData);
            stats.addRMSValue(rms);
            
            // Detect silence
            if (rms < 0.01) {
                stats.incrementSilencePackets();
            }
            
            // Detect peaks
            if (rms > 0.8) {
                stats.incrementPeakPackets();
            }
            
            stats.incrementTotalPackets();
        }
    }
    
    @Override
    public void onStreamStop(ServerWorld world, BlockPos jukeboxPos, String audioId) {
        String key = getKey(world, jukeboxPos);
        AudioStats stats = statsMap.remove(key);
        
        if (stats != null) {
            // Log statistics
            LOGGER.info("Audio Statistics for '{}':", stats.getDiscName());
            LOGGER.info("  Total packets: {}", stats.getTotalPackets());
            LOGGER.info("  Average RMS: {:.3f}", stats.getAverageRMS());
            LOGGER.info("  Peak packets: {}", stats.getPeakPackets());
            LOGGER.info("  Silence packets: {}", stats.getSilencePackets());
            LOGGER.info("  Silence percentage: {:.1f}%", stats.getSilencePercentage());
        }
    }
    
    private String getKey(ServerWorld world, BlockPos pos) {
        return world.getRegistryKey().getValue() + "_" + pos.toShortString();
    }
    
    private double calculateRMS(byte[] audioData) {
        long sum = 0;
        int sampleCount = audioData.length / 2; // 16-bit samples
        
        for (int i = 0; i < audioData.length - 1; i += 2) {
            // Convert bytes to 16-bit sample (little endian)
            short sample = (short) ((audioData[i] & 0xFF) | ((audioData[i + 1] & 0xFF) << 8));
            sum += sample * sample;
        }
        
        if (sampleCount > 0) {
            double meanSquare = (double) sum / sampleCount;
            return Math.sqrt(meanSquare) / Short.MAX_VALUE;
        }
        
        return 0.0;
    }
    
    private static class AudioStats {
        private final String discName;
        private int totalPackets = 0;
        private int silencePackets = 0;
        private int peakPackets = 0;
        private double totalRMS = 0.0;
        
        public AudioStats(String discName) {
            this.discName = discName;
        }
        
        public void incrementTotalPackets() { totalPackets++; }
        public void incrementSilencePackets() { silencePackets++; }
        public void incrementPeakPackets() { peakPackets++; }
        public void addRMSValue(double rms) { totalRMS += rms; }
        
        public String getDiscName() { return discName; }
        public int getTotalPackets() { return totalPackets; }
        public int getSilencePackets() { return silencePackets; }
        public int getPeakPackets() { return peakPackets; }
        public double getAverageRMS() { 
            return totalPackets > 0 ? totalRMS / totalPackets : 0.0; 
        }
        public double getSilencePercentage() {
            return totalPackets > 0 ? (silencePackets * 100.0) / totalPackets : 0.0;
        }
    }
}
```

### Technical Details

#### Audio Format
- **Format**: PCM 16-bit signed (little endian)
- **Sample Rate**: 48000 Hz (48 kHz)
- **Channels**: Mono (1 channel)
- **Packet Size**: 960 samples (~20ms at 48kHz)
- **Bytes per Packet**: 1920 bytes (960 samples × 2 bytes)

#### Performance Considerations
- Audio packet events are fired approximately every 20 milliseconds
- Keep processing in `onAudioPacket()` minimal to avoid performance issues
- Use async processing for heavy operations
- Consider buffering packets before processing
- Events are only fired when listeners are registered (zero overhead when unused)

#### Thread Safety
- All listener methods are called on the **server thread**
- Listener registration/unregistration is **thread-safe**
- Exceptions in listeners are **caught and logged** without affecting playback
- Use `ConcurrentHashMap` or synchronized collections for shared state

### Best Practices

1. **Minimize Processing Time**
   ```java
   @Override
   public void onAudioPacket(AudioPacketEvent event) {
       // Bad: Heavy processing in event handler
       // processAudioWithFFmpeg(event.getAudioData());
       
       // Good: Queue for async processing
       audioQueue.offer(event.getAudioData());
   }
   ```

2. **Buffer Packets for Efficiency**
   ```java
   private final List<byte[]> buffer = new ArrayList<>();
   private static final int BUFFER_SIZE = 50; // ~1 second at 48kHz
   
   @Override
   public void onAudioPacket(AudioPacketEvent event) {
       buffer.add(event.getAudioData());
       
       if (buffer.size() >= BUFFER_SIZE) {
           processBufferedAudio(buffer);
           buffer.clear();
       }
   }
   ```

3. **Handle Errors Gracefully**
   ```java
   @Override
   public void onAudioPacket(AudioPacketEvent event) {
       try {
           processAudio(event.getAudioData());
       } catch (Exception e) {
           LOGGER.error("Error processing audio packet", e);
           // Don't let exceptions propagate
       }
   }
   ```

4. **Clean Up Resources**
   ```java
   @Override
   public void onStreamStop(ServerWorld world, BlockPos jukeboxPos, String audioId) {
       // Clean up any resources
       closeConnections();
       clearBuffers();
       saveState();
   }
   ```

### Example: ExampleStreamListener

The mod includes a complete example implementation at:
`src/main/java/org/stepan/audio_disc/api/example/ExampleStreamListener.java`

This example demonstrates:
- Packet counting and statistics
- Audio level calculation
- Logging and debugging
- Proper resource management

---

## 📚 Additional Resources

### API Documentation Files

- **API_USAGE_EXAMPLE.md** - Comprehensive API usage examples with Simple Radio Mod integration
- **API_DOCUMENTATION_RU.md** - Russian language API documentation
- **API_IMPLEMENTATION_SUMMARY.md** - Technical implementation details

### Example Code

Check the `src/main/java/org/stepan/audio_disc/api/example/` directory for complete working examples.

---

