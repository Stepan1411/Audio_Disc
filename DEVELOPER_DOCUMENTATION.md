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