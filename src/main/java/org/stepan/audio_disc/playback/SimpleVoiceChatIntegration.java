package org.stepan.audio_disc.playback;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.spi.AudioFileReader;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integrates with Simple Voice Chat for spatial audio playback.
 */
public class SimpleVoiceChatIntegration implements VoicechatPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("AudioDisc");
    private static SimpleVoiceChatIntegration instance;
    private static VoicechatServerApi staticVoicechatApi;
    private static volatile boolean staticInitialized = false;
    
    private org.stepan.audio_disc.config.AudioDiscConfig config;
    
    // Static block to initialize audio providers
    static {
        try {
            // Force loading of MP3 provider
            Class.forName("javazoom.spi.mpeg.sampled.file.MpegAudioFileReader");
            LOGGER.info("MP3 audio provider loaded successfully");
        } catch (ClassNotFoundException e) {
            LOGGER.warn("MP3 audio provider not available: {}", e.getMessage());
        }
        
        // List available audio file readers (using reflection since getAudioFileReaders is private)
        try {
            java.lang.reflect.Method method = AudioSystem.class.getDeclaredMethod("getAudioFileReaders");
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<AudioFileReader> readers = (java.util.List<AudioFileReader>) method.invoke(null);
            LOGGER.info("Available audio file readers: {}", readers.size());
            for (AudioFileReader reader : readers) {
                LOGGER.info("  - {}", reader.getClass().getName());
            }
        } catch (Exception e) {
            LOGGER.warn("Could not list audio file readers: {}", e.getMessage());
        }
    }
    
    private VoicechatServerApi voicechatApi;
    private volatile boolean initialized = false;
    private final Map<UUID, AudioStreamInfo> activeStreams;

    public SimpleVoiceChatIntegration() {
        this.config = null; // Will be set later
        this.activeStreams = new ConcurrentHashMap<>();
        instance = this; // Set singleton instance
        LOGGER.info("SimpleVoiceChatIntegration instance created (config will be set later)");
    }
    
    public SimpleVoiceChatIntegration(org.stepan.audio_disc.config.AudioDiscConfig config) {
        this.config = config;
        this.activeStreams = new ConcurrentHashMap<>();
        instance = this; // Set singleton instance
        LOGGER.info("SimpleVoiceChatIntegration instance created with config");
    }
    
    /**
     * Sets the configuration after creation.
     */
    public void setConfig(org.stepan.audio_disc.config.AudioDiscConfig config) {
        this.config = config;
        LOGGER.info("Configuration set for SimpleVoiceChatIntegration");
    }
    
    public static SimpleVoiceChatIntegration getInstance() {
        return instance;
    }

    @Override
    public String getPluginId() {
        return "audio_disc";
    }

    @Override
    public void initialize(VoicechatApi api) {
        LOGGER.info("Simple Voice Chat plugin initialize() called");
        LOGGER.info("API type: {}", api.getClass().getName());
        
        // Check if this is a server API
        if (api instanceof VoicechatServerApi) {
            LOGGER.info("Detected VoicechatServerApi, initializing...");
            initializeServerApi((VoicechatServerApi) api);
        } else {
            LOGGER.warn("API is not VoicechatServerApi, waiting for server started event");
        }
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent.class, this::onServerStarted);
    }

    /**
     * Called when the voice chat server starts.
     */
    private void onServerStarted(de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent event) {
        LOGGER.info("Voice chat server started event received");
        LOGGER.info("Event voicechat API: {}", event.getVoicechat());
        LOGGER.info("Current static state: staticInitialized={}, staticVoicechatApi={}", 
            staticInitialized, staticVoicechatApi != null);
        
        if (event.getVoicechat() != null) {
            LOGGER.info("Calling initializeServerApi from onServerStarted");
            initializeServerApi(event.getVoicechat());
        } else {
            LOGGER.warn("Event voicechat API is null!");
        }
    }

    /**
     * Initializes the server API.
     * 
     * @param api The VoicechatServerApi instance
     */
    public void initializeServerApi(VoicechatServerApi api) {
        LOGGER.info("initializeServerApi called with API: {}", api.getClass().getName());
        
        this.voicechatApi = api;
        this.initialized = true;
        
        // Also set static references so all instances can access it
        staticVoicechatApi = api;
        staticInitialized = true;
        
        LOGGER.info("Set static flags: staticInitialized={}, staticVoicechatApi={}", 
            staticInitialized, staticVoicechatApi != null);
        
        // Register audio category with icon
        try {
            String categoryName = org.stepan.audio_disc.util.Localization.getVoiceChatCategory();
            
            // Create a simple music disc icon (16x16 pixels)
            int[][] discIcon = createMusicDiscIcon();
            
            api.registerVolumeCategory(api.volumeCategoryBuilder()
                .setId("audio_disc")
                .setName(categoryName)
                .setDescription("Volume for custom audio discs")
                .setIcon(discIcon) // Add custom icon for the category
                .build());
            LOGGER.info("Registered Voice Chat category: {}", categoryName);
        } catch (Exception e) {
            LOGGER.warn("Failed to register Voice Chat category: {}", e.getMessage());
        }
        
        LOGGER.info("Simple Voice Chat server API initialized successfully");
    }

    /**
     * Gets the VoicechatServerApi instance.
     * 
     * @return The VoicechatServerApi instance, or null if not initialized
     */
    public VoicechatServerApi getVoicechatApi() {
        if (staticInitialized && staticVoicechatApi != null) {
            return staticVoicechatApi;
        }
        return voicechatApi;
    }

    /**
     * Checks if Simple Voice Chat is available and initialized.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        // Check both instance and static initialization
        boolean instanceInit = initialized && voicechatApi != null;
        boolean staticInit = staticInitialized && staticVoicechatApi != null;
        
        LOGGER.info("isInitialized check: instanceInit={}, staticInit={}, staticInitialized={}, staticVoicechatApi={}", 
            instanceInit, staticInit, staticInitialized, staticVoicechatApi != null);
        
        // If static is initialized but instance is not, copy the reference
        if (staticInit && !instanceInit) {
            LOGGER.info("Copying static Voice Chat API to instance");
            this.voicechatApi = staticVoicechatApi;
            this.initialized = true;
            instanceInit = true;
        }
        
        boolean result = staticInit || instanceInit;
        LOGGER.info("isInitialized returning: {}", result);
        return result;
    }



    /**
     * Creates a personal audio stream for a specific player using AudioPlayer approach.
     * 
     * @param streamId Unique identifier for this stream
     * @param player The player to send audio to
     * @param audioData The audio data to stream
     * @param categoryId The audio category ID
     * @return true if stream was created successfully, false otherwise
     */
    public boolean createPersonalStream(UUID streamId, net.minecraft.server.network.ServerPlayerEntity player, byte[] audioData, String categoryId) {
        if (!isInitialized()) {
            LOGGER.warn("Cannot create personal audio stream: Simple Voice Chat not initialized");
            LOGGER.warn("Make sure Simple Voice Chat mod is installed on the server");
            return false;
        }

        try {
            // Use LocationalAudioChannel with AudioPlayer approach like in Radio mod
            de.maxhenkel.voicechat.api.Position pos = voicechatApi.createPosition(
                player.getX(), 
                player.getY() + 1.0, // Slightly above player head
                player.getZ()
            );
            
            LocationalAudioChannel channel = voicechatApi.createLocationalAudioChannel(
                streamId,
                voicechatApi.fromServerLevel((net.minecraft.server.world.ServerWorld) player.getWorld()),
                pos
            );
            
            if (channel == null) {
                LOGGER.error("Failed to create locational audio channel for player {}", player.getName().getString());
                return false;
            }
            
            // Set audio properties for personal playback
            if (categoryId != null) {
                channel.setCategory(categoryId);
            }
            
            // Set distance from config (for personal playback, use smaller range)
            double audioRange = (config != null) ? config.getAudioRange() : 64.0;
            channel.setDistance((float) Math.min(audioRange, 10.0));
            
            LOGGER.info("Personal locational audio channel created for player {} with ID: {}, category: {}, distance: 5.0", 
                player.getName().getString(), streamId, categoryId);
            
            // Create AudioPlayer with custom audio supplier
            PersonalAudioSupplier audioSupplier = new PersonalAudioSupplier(audioData);
            de.maxhenkel.voicechat.api.audiochannel.AudioPlayer audioPlayer = voicechatApi.createAudioPlayer(
                channel, 
                voicechatApi.createEncoder(de.maxhenkel.voicechat.api.opus.OpusEncoderMode.AUDIO), 
                audioSupplier
            );
            
            if (audioPlayer == null) {
                LOGGER.error("Failed to create audio player for player {}", player.getName().getString());
                return false;
            }
            
            // Create stream info for personal stream
            PersonalAudioPlayerInfo streamInfo = new PersonalAudioPlayerInfo(streamId, channel, audioPlayer, audioSupplier, player.getBlockPos(), audioData);
            activeStreams.put(streamId, streamInfo);
            
            // Start playing
            audioPlayer.startPlaying();
            streamInfo.setPlaying(true);
            
            LOGGER.info("Created and started personal audio player {} for player {}", streamId, player.getName().getString());
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to create personal audio stream for player {}", player.getName().getString(), e);
            return false;
        }
    }

    /**
     * Creates a locational audio stream at the specified position.
     * 
     * @param streamId Unique identifier for this stream
     * @param world The server world
     * @param position The block position for the audio source
     * @param audioData The audio data to stream
     * @param categoryId The audio category ID
     * @return The created audio stream info, or null if failed
     */
    public AudioStreamInfo createStream(UUID streamId, net.minecraft.server.world.ServerWorld world, BlockPos position, byte[] audioData, String categoryId) {
        if (!isInitialized()) {
            LOGGER.warn("Cannot create audio stream: Simple Voice Chat not initialized");
            LOGGER.warn("Make sure Simple Voice Chat mod is installed on the server");
            return null;
        }

        try {
            // Create locational audio channel
            de.maxhenkel.voicechat.api.Position pos = voicechatApi.createPosition(
                position.getX() + 0.5, 
                position.getY() + 0.5, 
                position.getZ() + 0.5
            );
            
            // Create a static locational audio channel
            LocationalAudioChannel channel = voicechatApi.createLocationalAudioChannel(
                streamId,
                voicechatApi.fromServerLevel(world),
                pos
            );
            
            if (channel == null) {
                LOGGER.error("Failed to create locational audio channel");
                return null;
            }
            
            // Set audio properties
            channel.setCategory(categoryId);
            double audioRange = (config != null) ? config.getAudioRange() : 64.0;
            channel.setDistance((float) audioRange); // Use config audio range
            
            LOGGER.info("Channel distance set to: {} blocks", audioRange);
            LOGGER.info("Channel category set to: {}", categoryId);
            
            // Enable the channel
            try {
                // Make sure the channel is properly configured
                LOGGER.info("Audio channel created with ID: {}, category: {}, distance: {}", 
                    streamId, categoryId, audioRange);
            } catch (Exception e) {
                LOGGER.warn("Error configuring audio channel: {}", e.getMessage());
            }
            
            AudioStreamInfo streamInfo = new AudioStreamInfo(streamId, channel, position, audioData);
            activeStreams.put(streamId, streamInfo);
            
            LOGGER.info("Created audio stream {} at position {}", streamId, position);
            return streamInfo;
            
        } catch (Exception e) {
            LOGGER.error("Failed to create audio stream", e);
            return null;
        }
    }

    /**
     * Starts streaming audio data.
     * 
     * @param streamId The stream identifier
     * @return true if started successfully, false otherwise
     */
    public boolean startStream(UUID streamId) {
        AudioStreamInfo streamInfo = activeStreams.get(streamId);
        if (streamInfo == null) {
            LOGGER.warn("Cannot start stream: stream not found {}", streamId);
            return false;
        }

        try {
            // Start streaming audio data in chunks
            streamInfo.setPlaying(true);
            
            // Create a thread to stream audio data
            Thread streamThread = new Thread(() -> streamAudioData(streamInfo));
            streamThread.setName("AudioDisc-Stream-" + streamId);
            streamThread.setDaemon(true);
            streamThread.start();
            
            LOGGER.info("Started audio stream {}", streamId);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to start audio stream", e);
            return false;
        }
    }



    /**
     * Streams personal audio data in chunks.
     * 
     * @param streamInfo The personal stream information
     */
    private void streamPersonalAudioData(PersonalAudioStreamInfo streamInfo) {
        try {
            byte[] audioData = streamInfo.getAudioData();
            de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel channel = streamInfo.getStaticChannel();
            
            LOGGER.info("Starting personal audio stream for player {}: {} bytes", 
                streamInfo.getPlayer().getName().getString(), audioData.length);
            
            // Convert audio data to proper format for streaming
            ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
            
            // Try to read as audio stream
            try {
                // First, let's check what we have
                LOGGER.info("Attempting to decode personal audio data: {} bytes", audioData.length);
                
                AudioInputStream audioInputStream;
                try {
                    audioInputStream = AudioSystem.getAudioInputStream(bais);
                } catch (NoClassDefFoundError ncdfe) {
                    LOGGER.error("Audio library missing: {}", ncdfe.getMessage());
                    LOGGER.error("Cannot decode audio - falling back to raw data");
                    throw new Exception("Audio library not available", ncdfe);
                }
                AudioFormat format = audioInputStream.getFormat();
                
                LOGGER.info("Original personal audio format: {} Hz, {} channels, {} bits, encoding: {}", 
                    format.getSampleRate(), format.getChannels(), format.getSampleSizeInBits(), format.getEncoding());
                
                // Convert to Simple Voice Chat compatible format (16-bit PCM, mono, 24kHz)
                AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    24000.0f, // 24kHz sample rate
                    16,       // 16-bit
                    1,        // mono
                    2,        // frame size (16-bit mono = 2 bytes per frame)
                    24000.0f, // frame rate
                    false     // little endian
                );
                
                // Convert audio format
                AudioInputStream convertedStream = audioInputStream;
                if (!format.matches(targetFormat)) {
                    try {
                        // First convert to PCM if needed
                        if (!format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)) {
                            AudioFormat pcmFormat = new AudioFormat(
                                AudioFormat.Encoding.PCM_SIGNED,
                                format.getSampleRate(),
                                16,
                                format.getChannels(),
                                format.getChannels() * 2,
                                format.getSampleRate(),
                                false
                            );
                            
                            if (AudioSystem.isConversionSupported(pcmFormat, format)) {
                                convertedStream = AudioSystem.getAudioInputStream(pcmFormat, audioInputStream);
                                LOGGER.info("Converted personal audio to PCM format");
                            }
                        }
                        
                        // Then convert to target format
                        if (AudioSystem.isConversionSupported(targetFormat, convertedStream.getFormat())) {
                            convertedStream = AudioSystem.getAudioInputStream(targetFormat, convertedStream);
                            LOGGER.info("Converted personal audio to target format: 24kHz 16-bit mono");
                        } else {
                            LOGGER.warn("Direct conversion not supported for personal audio, trying alternative approach");
                            // Use the PCM format we have
                            targetFormat = convertedStream.getFormat();
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Personal audio format conversion failed: {}", e.getMessage());
                        // Use original format
                        convertedStream = audioInputStream;
                        targetFormat = format;
                    }
                }
                
                LOGGER.info("Final personal audio format: {} Hz, {} channels, {} bits", 
                    targetFormat.getSampleRate(), targetFormat.getChannels(), targetFormat.getSampleSizeInBits());
                
                // Calculate proper chunk size based on format
                int frameSize = targetFormat.getFrameSize();
                int sampleRate = (int) targetFormat.getSampleRate();
                int chunkDurationMs = 40; // 40ms chunks
                int framesPerChunk = (sampleRate * chunkDurationMs) / 1000;
                int chunkSize = framesPerChunk * frameSize;
                
                LOGGER.info("Using personal audio chunk size: {} bytes ({} frames, {}ms)", chunkSize, framesPerChunk, chunkDurationMs);
                
                // Stream in chunks with proper timing
                byte[] buffer = new byte[chunkSize];
                int bytesRead;
                
                while (streamInfo.isPlaying() && (bytesRead = convertedStream.read(buffer)) != -1) {
                    // Send audio chunk to voice chat
                    if (bytesRead == buffer.length) {
                        channel.send(buffer);
                    } else {
                        // Send partial buffer
                        byte[] chunk = new byte[bytesRead];
                        System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                        channel.send(chunk);
                    }
                    
                    // Wait for proper timing
                    try {
                        Thread.sleep(chunkDurationMs);
                    } catch (InterruptedException e) {
                        LOGGER.info("Personal audio stream {} interrupted during sleep", streamInfo.getStreamId());
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                convertedStream.close();
                audioInputStream.close();
                
            } catch (Exception e) {
                LOGGER.error("Could not parse personal audio format: {}", e.getMessage());
                
                // Try alternative approach - assume it's a valid audio file but we can't decode it
                LOGGER.warn("Attempting to send raw personal audio data as fallback");
                
                try {
                    // Send raw data in small chunks, assuming it's compressed audio
                    int chunkSize = 4096; // Larger chunks for compressed data
                    for (int i = 0; i < audioData.length && streamInfo.isPlaying(); i += chunkSize) {
                        int length = Math.min(chunkSize, audioData.length - i);
                        byte[] chunk = new byte[length];
                        System.arraycopy(audioData, i, chunk, 0, length);
                        
                        channel.send(chunk);
                        
                        // Shorter delay for compressed data
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ie) {
                            LOGGER.info("Personal audio stream {} interrupted during fallback", streamInfo.getStreamId());
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } catch (Exception fallbackException) {
                    LOGGER.error("Fallback personal audio streaming also failed: {}", fallbackException.getMessage());
                }
                
                streamInfo.setPlaying(false);
                return;
            }
            
            // Mark as finished
            streamInfo.setPlaying(false);
            LOGGER.info("Personal audio stream {} finished for player {}", streamInfo.getStreamId(), streamInfo.getPlayer().getName().getString());
            
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                LOGGER.info("Personal audio stream {} interrupted", streamInfo.getStreamId());
                Thread.currentThread().interrupt();
            } else {
                LOGGER.error("Error streaming personal audio data: {}", e.getMessage());
                LOGGER.debug("Full stack trace:", e);
            }
        } catch (NoClassDefFoundError e) {
            LOGGER.error("Audio library error (missing class) in personal stream: {}", e.getMessage());
            LOGGER.error("This usually means an audio library is not properly loaded");
            LOGGER.error("Try restarting the server or check audio library dependencies");
        } catch (Error e) {
            LOGGER.error("Critical error in personal audio streaming: {}", e.getMessage());
            LOGGER.debug("Full stack trace:", e);
        }
    }

    /**
     * Streams audio data in chunks.
     * 
     * @param streamInfo The stream information
     */
    private void streamAudioData(AudioStreamInfo streamInfo) {
        try {
            byte[] audioData = streamInfo.getAudioData();
            LocationalAudioChannel channel = streamInfo.getChannel();
            
            // Convert audio data to proper format for streaming
            ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
            
            // Try to read as audio stream
            try {
                // First, let's check what we have
                LOGGER.info("Attempting to decode audio data: {} bytes", audioData.length);
                
                // Check first few bytes for format identification
                if (audioData.length >= 4) {
                    String header = String.format("%02X %02X %02X %02X", 
                        audioData[0] & 0xFF, audioData[1] & 0xFF, audioData[2] & 0xFF, audioData[3] & 0xFF);
                    LOGGER.info("Audio header bytes: {}", header);
                }
                
                AudioInputStream audioInputStream;
                try {
                    audioInputStream = AudioSystem.getAudioInputStream(bais);
                } catch (NoClassDefFoundError ncdfe) {
                    LOGGER.error("Audio library missing: {}", ncdfe.getMessage());
                    LOGGER.error("Cannot decode audio - falling back to raw data");
                    throw new Exception("Audio library not available", ncdfe);
                }
                AudioFormat format = audioInputStream.getFormat();
                
                LOGGER.info("Original audio format: {} Hz, {} channels, {} bits, encoding: {}", 
                    format.getSampleRate(), format.getChannels(), format.getSampleSizeInBits(), format.getEncoding());
                
                // Convert to Simple Voice Chat compatible format (16-bit PCM, mono, 24kHz)
                AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    24000.0f, // 24kHz sample rate
                    16,       // 16-bit
                    1,        // mono
                    2,        // frame size (16-bit mono = 2 bytes per frame)
                    24000.0f, // frame rate
                    false     // little endian
                );
                
                // Convert audio format
                AudioInputStream convertedStream = audioInputStream;
                if (!format.matches(targetFormat)) {
                    try {
                        // First convert to PCM if needed
                        if (!format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)) {
                            AudioFormat pcmFormat = new AudioFormat(
                                AudioFormat.Encoding.PCM_SIGNED,
                                format.getSampleRate(),
                                16,
                                format.getChannels(),
                                format.getChannels() * 2,
                                format.getSampleRate(),
                                false
                            );
                            
                            if (AudioSystem.isConversionSupported(pcmFormat, format)) {
                                convertedStream = AudioSystem.getAudioInputStream(pcmFormat, audioInputStream);
                                LOGGER.info("Converted to PCM format");
                            }
                        }
                        
                        // Then convert to target format
                        if (AudioSystem.isConversionSupported(targetFormat, convertedStream.getFormat())) {
                            convertedStream = AudioSystem.getAudioInputStream(targetFormat, convertedStream);
                            LOGGER.info("Converted to target format: 24kHz 16-bit mono");
                        } else {
                            LOGGER.warn("Direct conversion not supported, trying alternative approach");
                            // Use the PCM format we have
                            targetFormat = convertedStream.getFormat();
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Audio format conversion failed: {}", e.getMessage());
                        // Use original format
                        convertedStream = audioInputStream;
                        targetFormat = format;
                    }
                }
                
                LOGGER.info("Final audio format: {} Hz, {} channels, {} bits", 
                    targetFormat.getSampleRate(), targetFormat.getChannels(), targetFormat.getSampleSizeInBits());
                
                // Calculate proper chunk size based on format
                int frameSize = targetFormat.getFrameSize();
                int sampleRate = (int) targetFormat.getSampleRate();
                int chunkDurationMs = 40; // 40ms chunks
                int framesPerChunk = (sampleRate * chunkDurationMs) / 1000;
                int chunkSize = framesPerChunk * frameSize;
                
                LOGGER.info("Using chunk size: {} bytes ({} frames, {}ms)", chunkSize, framesPerChunk, chunkDurationMs);
                
                // Stream in chunks with proper timing
                byte[] buffer = new byte[chunkSize];
                int bytesRead;
                
                while (streamInfo.isPlaying() && (bytesRead = convertedStream.read(buffer)) != -1) {
                    // Send audio chunk to voice chat
                    if (bytesRead == buffer.length) {
                        channel.send(buffer);
                    } else {
                        // Send partial buffer
                        byte[] chunk = new byte[bytesRead];
                        System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                        channel.send(chunk);
                    }
                    
                    // Wait for proper timing
                    try {
                        Thread.sleep(chunkDurationMs);
                    } catch (InterruptedException e) {
                        LOGGER.info("Audio stream {} interrupted during sleep", streamInfo.getStreamId());
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                convertedStream.close();
                audioInputStream.close();
                
            } catch (Exception e) {
                LOGGER.error("Could not parse audio format: {}", e.getMessage());
                LOGGER.error("Available audio file readers:");
                try {
                    java.lang.reflect.Method method = AudioSystem.class.getDeclaredMethod("getAudioFileReaders");
                    method.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    java.util.List<AudioFileReader> readers = (java.util.List<AudioFileReader>) method.invoke(null);
                    for (AudioFileReader reader : readers) {
                        LOGGER.error("  - {}", reader.getClass().getName());
                    }
                } catch (Exception reflectionException) {
                    LOGGER.error("Could not list audio file readers: {}", reflectionException.getMessage());
                }
                
                // Try alternative approach - assume it's a valid audio file but we can't decode it
                // This might happen if the audio libraries aren't properly loaded
                LOGGER.warn("Attempting to send raw audio data as fallback");
                
                try {
                    // Send raw data in small chunks, assuming it's compressed audio
                    int chunkSize = 4096; // Larger chunks for compressed data
                    for (int i = 0; i < audioData.length && streamInfo.isPlaying(); i += chunkSize) {
                        int length = Math.min(chunkSize, audioData.length - i);
                        byte[] chunk = new byte[length];
                        System.arraycopy(audioData, i, chunk, 0, length);
                        
                        channel.send(chunk);
                        
                        // Shorter delay for compressed data
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ie) {
                            LOGGER.info("Audio stream {} interrupted during fallback", streamInfo.getStreamId());
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } catch (Exception fallbackException) {
                    LOGGER.error("Fallback audio streaming also failed: {}", fallbackException.getMessage());
                }
                
                streamInfo.setPlaying(false);
                return;
            }
            
            // Mark as finished
            streamInfo.setPlaying(false);
            LOGGER.info("Audio stream {} finished", streamInfo.getStreamId());
            
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                LOGGER.info("Audio stream {} interrupted", streamInfo.getStreamId());
                Thread.currentThread().interrupt();
            } else {
                LOGGER.error("Error streaming audio data: {}", e.getMessage());
                LOGGER.debug("Full stack trace:", e);
            }
        } catch (NoClassDefFoundError e) {
            LOGGER.error("Audio library error (missing class): {}", e.getMessage());
            LOGGER.error("This usually means an audio library is not properly loaded");
            LOGGER.error("Try restarting the server or check audio library dependencies");
        } catch (Error e) {
            LOGGER.error("Critical error in audio streaming: {}", e.getMessage());
            LOGGER.debug("Full stack trace:", e);
        }
    }

    /**
     * Updates the position of an audio stream.
     * 
     * @param streamId The stream identifier
     * @param position The new position
     */
    public void updateStreamPosition(UUID streamId, BlockPos position) {
        AudioStreamInfo streamInfo = activeStreams.get(streamId);
        if (streamInfo != null) {
            de.maxhenkel.voicechat.api.Position pos = voicechatApi.createPosition(
                position.getX() + 0.5, 
                position.getY() + 0.5, 
                position.getZ() + 0.5
            );
            // Note: LocationalAudioChannel doesn't have setLocation in newer versions
            // Position is set during channel creation
            streamInfo.setPosition(position);
            LOGGER.debug("Updated stream {} position to {}", streamId, position);
        }
    }

    /**
     * Stops an audio stream.
     * 
     * @param streamId The stream identifier
     */
    public void stopStream(UUID streamId) {
        AudioStreamInfo streamInfo = activeStreams.remove(streamId);
        if (streamInfo != null) {
            streamInfo.setPlaying(false);
            
            try {
                // If it's a PersonalAudioPlayerInfo, stop the audio player
                if (streamInfo instanceof PersonalAudioPlayerInfo) {
                    PersonalAudioPlayerInfo playerInfo = (PersonalAudioPlayerInfo) streamInfo;
                    playerInfo.getAudioPlayer().stopPlaying();
                    LOGGER.info("Stopped audio player for stream {}", streamId);
                }
            } catch (Exception e) {
                LOGGER.warn("Error stopping audio channel: {}", e.getMessage());
            }
            
            LOGGER.info("Stopped audio stream {}", streamId);
        }
    }

    /**
     * Stops all active streams.
     */
    public void stopAllStreams() {
        LOGGER.info("Stopping all audio streams");
        activeStreams.keySet().forEach(this::stopStream);
    }

    /**
     * Gets the number of active streams.
     * 
     * @return The count of active streams
     */
    public int getActiveStreamCount() {
        return activeStreams.size();
    }

    /**
     * Creates a simple music disc icon for the Voice Chat category.
     * 
     * @return 16x16 pixel icon as int array (ARGB format)
     */
    private int[][] createMusicDiscIcon() {
        int[][] icon = new int[16][16];
        
        // Colors (ARGB format)
        int transparent = 0x00000000;
        int black = 0xFF000000;
        int darkGray = 0xFF404040;
        int gray = 0xFF808080;
        int lightGray = 0xFFC0C0C0;
        int white = 0xFFFFFFFF;
        int gold = 0xFFFFD700;
        
        // Fill with transparent background
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                icon[y][x] = transparent;
            }
        }
        
        // Draw a simple music disc (circle with hole in center)
        // Outer circle (disc edge)
        for (int y = 2; y < 14; y++) {
            for (int x = 2; x < 14; x++) {
                double dx = x - 7.5;
                double dy = y - 7.5;
                double distance = Math.sqrt(dx * dx + dy * dy);
                
                if (distance <= 5.5 && distance >= 4.5) {
                    icon[y][x] = black; // Outer edge
                } else if (distance <= 4.5 && distance >= 1.5) {
                    // Disc surface with gradient
                    if (distance <= 2.5) {
                        icon[y][x] = lightGray;
                    } else if (distance <= 3.5) {
                        icon[y][x] = gray;
                    } else {
                        icon[y][x] = darkGray;
                    }
                } else if (distance <= 1.5) {
                    icon[y][x] = black; // Center hole
                }
            }
        }
        
        // Add a small highlight to make it look more like a disc
        icon[4][6] = white;
        icon[4][7] = white;
        icon[5][6] = lightGray;
        
        return icon;
    }

    /**
     * Information about an active audio stream.
     */
    public static class AudioStreamInfo {
        private final UUID streamId;
        private final LocationalAudioChannel channel;
        private BlockPos position;
        private final byte[] audioData;
        private volatile boolean playing;

        public AudioStreamInfo(UUID streamId, LocationalAudioChannel channel, BlockPos position, byte[] audioData) {
            this.streamId = streamId;
            this.channel = channel;
            this.position = position;
            this.audioData = audioData;
            this.playing = false;
        }

        public UUID getStreamId() {
            return streamId;
        }

        public LocationalAudioChannel getChannel() {
            return channel;
        }

        public BlockPos getPosition() {
            return position;
        }

        public void setPosition(BlockPos position) {
            this.position = position;
        }

        public byte[] getAudioData() {
            return audioData;
        }

        public boolean isPlaying() {
            return playing;
        }

        public void setPlaying(boolean playing) {
            this.playing = playing;
        }
    }

    /**
     * Information about an active personal audio stream.
     */
    public static class PersonalAudioStreamInfo extends AudioStreamInfo {
        private final de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel staticChannel;
        private final net.minecraft.server.network.ServerPlayerEntity player;

        public PersonalAudioStreamInfo(UUID streamId, de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel staticChannel, 
                                     net.minecraft.server.network.ServerPlayerEntity player, byte[] audioData) {
            super(streamId, null, player.getBlockPos(), audioData); // No locational channel for personal streams
            this.staticChannel = staticChannel;
            this.player = player;
        }

        public de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel getStaticChannel() {
            return staticChannel;
        }

        public net.minecraft.server.network.ServerPlayerEntity getPlayer() {
            return player;
        }
    }

    /**
     * Information about an active personal audio player.
     */
    public static class PersonalAudioPlayerInfo extends AudioStreamInfo {
        private final de.maxhenkel.voicechat.api.audiochannel.AudioPlayer audioPlayer;
        private final PersonalAudioSupplier audioSupplier;

        public PersonalAudioPlayerInfo(UUID streamId, LocationalAudioChannel channel, 
                                     de.maxhenkel.voicechat.api.audiochannel.AudioPlayer audioPlayer,
                                     PersonalAudioSupplier audioSupplier,
                                     BlockPos position, byte[] audioData) {
            super(streamId, channel, position, audioData);
            this.audioPlayer = audioPlayer;
            this.audioSupplier = audioSupplier;
        }

        public de.maxhenkel.voicechat.api.audiochannel.AudioPlayer getAudioPlayer() {
            return audioPlayer;
        }

        public PersonalAudioSupplier getAudioSupplier() {
            return audioSupplier;
        }
    }

    /**
     * Audio supplier for personal debug audio playback.
     */
    public static class PersonalAudioSupplier implements java.util.function.Supplier<short[]> {
        private final byte[] audioData;
        private AudioInputStream audioInputStream;
        private boolean initialized = false;
        private boolean finished = false;
        private AudioFormat targetFormat;
        private int frameSize;

        public PersonalAudioSupplier(byte[] audioData) {
            this.audioData = audioData;
        }

        @Override
        public short[] get() {
            if (finished) {
                return null;
            }

            if (!initialized) {
                if (!initialize()) {
                    finished = true;
                    return null;
                }
                initialized = true;
            }

            try {
                // Simple Voice Chat ожидает ровно 960 сэмплов на фрейм
                short[] frame = new short[960]; // FRAME_SIZE_SAMPLES = 960
                
                // Read audio chunk and convert to short array
                byte[] buffer = new byte[960 * frameSize]; // 960 frames * 2 bytes per frame
                int bytesRead = audioInputStream.read(buffer);
                
                if (bytesRead <= 0) {
                    finished = true;
                    return null;
                }

                // Convert bytes to shorts (little endian)
                int samplesRead = bytesRead / 2; // 16-bit = 2 bytes per sample
                for (int i = 0; i < Math.min(samplesRead, 960); i++) {
                    int byteIndex = i * 2;
                    if (byteIndex + 1 < bytesRead) {
                        // Little endian conversion
                        frame[i] = (short) ((buffer[byteIndex] & 0xFF) | ((buffer[byteIndex + 1] & 0xFF) << 8));
                    }
                }

                // Если прочитали меньше 960 сэмплов, остальные будут нулями (тишина)
                return frame;

            } catch (Exception e) {
                LOGGER.error("Error reading audio data in supplier: {}", e.getMessage());
                finished = true;
                return null;
            }
        }

        private boolean initialize() {
            try {
                // Check if this is an M4A file first
                if (isM4AFormat(audioData)) {
                    LOGGER.warn("M4A format detected - Java AudioSystem cannot decode M4A natively");
                    LOGGER.warn("Attempting to process M4A file anyway - may fail");
                    // Don't return false, let it try to process
                }
                
                // Check if this is a WebM file
                if (isWebMFormat(audioData)) {
                    LOGGER.warn("WebM format detected - Java AudioSystem cannot decode WebM natively");
                    LOGGER.warn("WebM playback requires external decoder or conversion to PCM format");
                    LOGGER.error("Failed to initialize personal audio supplier: Stream of unsupported format");
                    return false;
                }
                
                ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
                try {
                    audioInputStream = AudioSystem.getAudioInputStream(bais);
                } catch (javax.sound.sampled.UnsupportedAudioFileException e) {
                    if (isM4AFormat(audioData)) {
                        LOGGER.error("M4A format is not supported by Java AudioSystem");
                        LOGGER.error("To fix this issue, you need to:");
                        LOGGER.error("1. Install FFmpeg on your server");
                        LOGGER.error("2. Configure yt-dlp to convert M4A to MP3/OGG");
                        LOGGER.error("3. Or use a different audio format");
                        return false;
                    }
                    if (isWebMFormat(audioData)) {
                        LOGGER.error("WebM format is not supported by Java AudioSystem");
                        LOGGER.error("To fix this issue, you need to:");
                        LOGGER.error("1. Install FFmpeg on your server");
                        LOGGER.error("2. Configure yt-dlp to convert WebM to MP3/OGG");
                        LOGGER.error("3. Or use a different audio format");
                        return false;
                    }
                    throw e; // Re-throw if it's not M4A or WebM
                }
                AudioFormat format = audioInputStream.getFormat();
                
                LOGGER.info("Personal audio supplier format: {} Hz, {} channels, {} bits, encoding: {}", 
                    format.getSampleRate(), format.getChannels(), format.getSampleSizeInBits(), format.getEncoding());

                // Convert to Simple Voice Chat compatible format (16-bit PCM, mono, 48kHz)
                // Simple Voice Chat обычно работает с 48kHz, не 24kHz
                targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    48000.0f, // 48kHz sample rate (стандарт для Simple Voice Chat)
                    16,       // 16-bit
                    1,        // mono
                    2,        // frame size (16-bit mono = 2 bytes per frame)
                    48000.0f, // frame rate
                    false     // little endian
                );

                // Convert audio format if needed
                if (!format.matches(targetFormat)) {
                    try {
                        // First convert to PCM if needed
                        if (!format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)) {
                            AudioFormat pcmFormat = new AudioFormat(
                                AudioFormat.Encoding.PCM_SIGNED,
                                format.getSampleRate(),
                                16,
                                format.getChannels(),
                                format.getChannels() * 2,
                                format.getSampleRate(),
                                false
                            );
                            
                            if (AudioSystem.isConversionSupported(pcmFormat, format)) {
                                audioInputStream = AudioSystem.getAudioInputStream(pcmFormat, audioInputStream);
                                LOGGER.info("Converted personal audio supplier to PCM format");
                            }
                        }
                        
                        // Then convert to target format
                        if (AudioSystem.isConversionSupported(targetFormat, audioInputStream.getFormat())) {
                            audioInputStream = AudioSystem.getAudioInputStream(targetFormat, audioInputStream);
                            LOGGER.info("Converted personal audio supplier to target format: 24kHz 16-bit mono");
                        } else {
                            LOGGER.warn("Direct conversion not supported for personal audio supplier, using available format");
                            targetFormat = audioInputStream.getFormat();
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Personal audio supplier format conversion failed: {}", e.getMessage());
                        targetFormat = format;
                    }
                }

                frameSize = targetFormat.getFrameSize();
                
                LOGGER.info("Personal audio supplier initialized: {} Hz, {} channels, {} bits, frame size: {} bytes", 
                    targetFormat.getSampleRate(), targetFormat.getChannels(), targetFormat.getSampleSizeInBits(), frameSize);
                LOGGER.info("Simple Voice Chat expects 960 samples per frame (20ms at 48kHz)");

                return true;

            } catch (Exception e) {
                LOGGER.error("Failed to initialize personal audio supplier: {}", e.getMessage());
                return false;
            }
        }

        public boolean isFinished() {
            return finished;
        }

        /**
         * Checks if the audio data is in M4A format.
         */
        private boolean isM4AFormat(byte[] data) {
            if (data.length < 12) {
                return false;
            }
            
            // M4A files start with a size field (4 bytes) followed by "ftyp"
            // Check for "ftyp" at offset 4
            byte[] M4A_FTYP = {0x66, 0x74, 0x79, 0x70}; // "ftyp"
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
         * Checks if the audio data is in WebM format.
         */
        private boolean isWebMFormat(byte[] data) {
            if (data.length < 4) {
                return false;
            }
            
            // WebM files start with EBML header: 0x1A 0x45 0xDF 0xA3
            byte[] WEBM_MAGIC = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};
            for (int i = 0; i < WEBM_MAGIC.length; i++) {
                if (data[i] != WEBM_MAGIC[i]) {
                    return false;
                }
            }
            
            return true;
        }
    }


}
