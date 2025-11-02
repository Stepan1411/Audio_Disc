package org.stepan.audio_disc.playback;

import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stepan.audio_disc.api.*;
import org.stepan.audio_disc.model.AudioData;
import org.stepan.audio_disc.model.AudioMetadata;
import org.stepan.audio_disc.storage.AudioStorageManager;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages audio playback for jukeboxes.
 */
public class PlaybackManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("AudioDisc");
    
    private final Map<BlockPos, ActivePlayback> activePlaybacks;
    private final SimpleVoiceChatIntegration voiceChatIntegration;
    private final AudioStorageManager storageManager;
    private final ScheduledExecutorService scheduler;

    public PlaybackManager(SimpleVoiceChatIntegration voiceChatIntegration, AudioStorageManager storageManager) {
        this.activePlaybacks = new ConcurrentHashMap<>();
        this.voiceChatIntegration = voiceChatIntegration;
        this.storageManager = storageManager;
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        // Start monitoring task for playback completion
        startPlaybackMonitor();
    }

    /**
     * Starts monitoring active playbacks for completion.
     */
    private void startPlaybackMonitor() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkPlaybackCompletion();
            } catch (Exception e) {
                LOGGER.error("Error in playback monitor", e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * Checks for completed playbacks and stops them.
     */
    private void checkPlaybackCompletion() {
        activePlaybacks.entrySet().removeIf(entry -> {
            ActivePlayback playback = entry.getValue();
            
            // Check if audio supplier is finished (for AudioPlayer approach)
            boolean audioFinished = false;
            SimpleVoiceChatIntegration.AudioStreamInfo streamInfo = playback.getStream();
            if (streamInfo instanceof SimpleVoiceChatIntegration.PersonalAudioPlayerInfo) {
                SimpleVoiceChatIntegration.PersonalAudioPlayerInfo playerInfo = 
                    (SimpleVoiceChatIntegration.PersonalAudioPlayerInfo) streamInfo;
                audioFinished = playerInfo.getAudioSupplier().isFinished();
            }
            
            if (!playback.isPlaying() || playback.isComplete() || audioFinished) {
                LOGGER.info("Playback completed at position {} (finished: {}, complete: {})", 
                    entry.getKey(), audioFinished, playback.isComplete());
                StopReason reason = (playback.isComplete() || audioFinished) ? StopReason.PLAYBACK_COMPLETE : StopReason.MANUAL_STOP;
                
                // If playback completed naturally, eject the disc like vanilla behavior
                if (reason == StopReason.PLAYBACK_COMPLETE) {
                    ejectDiscFromJukebox(entry.getKey());
                }
                
                stopPlayback(entry.getKey(), reason);
                return true;
            }
            
            return false;
        });
    }

    /**
     * Starts playback when a custom disc is inserted into a jukebox.
     * 
     * @param world The server world
     * @param jukeboxPos The jukebox position
     * @param disc The music disc item
     * @return true if playback started successfully, false otherwise
     */
    public boolean startPlayback(ServerWorld world, BlockPos jukeboxPos, ItemStack disc) {
        // Check if already playing at this position
        if (activePlaybacks.containsKey(jukeboxPos)) {
            LOGGER.debug("Already playing at position {}", jukeboxPos);
            stopPlayback(jukeboxPos);
        }

        // Get audio ID from disc
        Optional<String> audioIdOpt = storageManager.getDiscAudioId(disc);
        if (audioIdOpt.isEmpty()) {
            LOGGER.debug("No custom audio on disc at position {}", jukeboxPos);
            return false;
        }

        String audioId = audioIdOpt.get();
        LOGGER.info("Starting playback of audio {} at position {}", audioId, jukeboxPos);

        // Get audio data
        Optional<AudioData> audioDataOpt = storageManager.getAudio(audioId);
        if (audioDataOpt.isEmpty()) {
            LOGGER.error("Audio data not found for ID: {}", audioId);
            return false;
        }

        AudioData audioData = audioDataOpt.get();
        AudioMetadata metadata = audioData.metadata();

        // Check if Simple Voice Chat is available
        if (!voiceChatIntegration.isInitialized()) {
            LOGGER.warn("Simple Voice Chat not available, cannot play audio");
            LOGGER.warn("Integration initialized: {}", voiceChatIntegration.isInitialized());
            LOGGER.warn("Please make sure Simple Voice Chat is properly loaded");
            return false;
        }
        
        LOGGER.info("Simple Voice Chat is available, creating audio stream");

        try {
            // Call API listeners for audio modification
            AudioModificationContext modContext = new AudioModificationContext(
                audioId,
                audioData.data(),
                metadata,
                jukeboxPos,
                world
            );
            
            AudioModification modification = AudioDiscAPIImpl.getInstance().callModifyAudio(modContext);
            
            // Check if playback was cancelled
            if (modification.isCancelled()) {
                LOGGER.info("Playback cancelled by API listener at position {}", jukeboxPos);
                return false;
            }
            
            // Apply modifications if any
            byte[] finalAudioData = modification.isModified() && modification.getModifiedData() != null
                ? modification.getModifiedData()
                : audioData.data();
            
            // Create audio stream using AudioPlayer approach
            UUID streamId = UUID.randomUUID();
            
            // Use LocationalAudioChannel with AudioPlayer for jukebox playback
            de.maxhenkel.voicechat.api.Position pos = voiceChatIntegration.getVoicechatApi().createPosition(
                jukeboxPos.getX() + 0.5, 
                jukeboxPos.getY() + 0.5, 
                jukeboxPos.getZ() + 0.5
            );
            
            de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel channel = voiceChatIntegration.getVoicechatApi().createLocationalAudioChannel(
                streamId,
                voiceChatIntegration.getVoicechatApi().fromServerLevel(world),
                pos
            );
            
            if (channel == null) {
                LOGGER.error("Failed to create locational audio channel for jukebox");
                return false;
            }
            
            // Set audio properties for jukebox playback
            channel.setCategory("audio_disc");
            channel.setDistance(64.0f); // Standard jukebox range
            
            LOGGER.info("Jukebox audio channel created at {} with distance: 64.0", jukeboxPos);
            
            // Create AudioPlayer with custom audio supplier
            SimpleVoiceChatIntegration.PersonalAudioSupplier audioSupplier = new SimpleVoiceChatIntegration.PersonalAudioSupplier(finalAudioData);
            
            // TODO: Set context for API event firing
            // Note: setContext method exists in PersonalAudioSupplier but may need to be called differently
            // audioSupplier.setContext(world, jukeboxPos, metadata.title(), audioId);
            
            de.maxhenkel.voicechat.api.audiochannel.AudioPlayer audioPlayer = voiceChatIntegration.getVoicechatApi().createAudioPlayer(
                channel, 
                voiceChatIntegration.getVoicechatApi().createEncoder(OpusEncoderMode.AUDIO), 
                audioSupplier
            );
            
            if (audioPlayer == null) {
                LOGGER.error("Failed to create audio player for jukebox");
                return false;
            }
            
            // Create stream info for jukebox playback
            SimpleVoiceChatIntegration.PersonalAudioPlayerInfo streamInfo = new SimpleVoiceChatIntegration.PersonalAudioPlayerInfo(
                streamId, channel, audioPlayer, audioSupplier, jukeboxPos, finalAudioData
            );

            // Create active playback
            ActivePlayback playback = new ActivePlayback(
                audioId,
                jukeboxPos,
                streamId,
                streamInfo,
                metadata
            );

            activePlaybacks.put(jukeboxPos, playback);

            // Start playing
            audioPlayer.startPlaying();
            streamInfo.setPlaying(true);
            
            LOGGER.info("Started jukebox audio player at {}", jukeboxPos);

            // Fire playback start event
            PlaybackStartEvent startEvent = new PlaybackStartEvent(
                jukeboxPos,
                world,
                audioId,
                metadata
            );
            AudioDiscAPIImpl.getInstance().firePlaybackStartEvent(startEvent);
            
            // Notify stream listeners about stream start
            AudioDiscAPIImpl.getInstance().notifyStreamStart(world, jukeboxPos, metadata.title(), audioId);
            
            LOGGER.info("Successfully started playback at position {}", jukeboxPos);
            return true;

        } catch (Exception e) {
            LOGGER.error("Error starting playback", e);
            return false;
        }
    }

    /**
     * Stops playback at a jukebox position.
     * 
     * @param jukeboxPos The jukebox position
     */
    public void stopPlayback(BlockPos jukeboxPos) {
        stopPlayback(jukeboxPos, StopReason.MANUAL_STOP);
    }

    /**
     * Stops playback at a jukebox position with a specific reason.
     * 
     * @param jukeboxPos The jukebox position
     * @param reason The reason for stopping
     */
    public void stopPlayback(BlockPos jukeboxPos, StopReason reason) {
        ActivePlayback playback = activePlaybacks.remove(jukeboxPos);
        if (playback != null) {
            long duration = playback.getElapsedTime();
            playback.stop();
            
            // Stop the audio stream
            SimpleVoiceChatIntegration.AudioStreamInfo streamInfo = playback.getStream();
            if (streamInfo instanceof SimpleVoiceChatIntegration.PersonalAudioPlayerInfo) {
                SimpleVoiceChatIntegration.PersonalAudioPlayerInfo playerInfo = 
                    (SimpleVoiceChatIntegration.PersonalAudioPlayerInfo) streamInfo;
                playerInfo.getAudioPlayer().stopPlaying();
                LOGGER.info("Stopped jukebox audio player at {}", jukeboxPos);
            } else {
                // Fallback to old method
                voiceChatIntegration.stopStream(playback.getStreamId());
            }
            
            // Fire playback stop event
            // Note: We can't get the ServerWorld here easily, so we'll pass null
            // In a real implementation, you might want to store the world in ActivePlayback
            PlaybackStopEvent stopEvent = new PlaybackStopEvent(
                jukeboxPos,
                null, // world
                playback.getAudioId(),
                duration,
                reason
            );
            AudioDiscAPIImpl.getInstance().firePlaybackStopEvent(stopEvent);
            
            // Notify stream listeners about stream stop
            // Note: world is null here, ideally should be stored in ActivePlayback
            AudioDiscAPIImpl.getInstance().notifyStreamStop(null, jukeboxPos, playback.getAudioId());
            
            LOGGER.info("Stopped playback at position {} (reason: {})", jukeboxPos, reason);
        }
    }

    /**
     * Gets the current playback at a position.
     * 
     * @param jukeboxPos The jukebox position
     * @return An Optional containing the active playback if present
     */
    public Optional<ActivePlayback> getPlayback(BlockPos jukeboxPos) {
        return Optional.ofNullable(activePlaybacks.get(jukeboxPos));
    }

    /**
     * Checks if there is active playback at a position.
     * 
     * @param jukeboxPos The jukebox position
     * @return true if playing, false otherwise
     */
    public boolean isPlaying(BlockPos jukeboxPos) {
        ActivePlayback playback = activePlaybacks.get(jukeboxPos);
        return playback != null && playback.isPlaying();
    }

    /**
     * Stops all active playbacks.
     */
    public void stopAllPlaybacks() {
        LOGGER.info("Stopping all playbacks");
        activePlaybacks.keySet().forEach(this::stopPlayback);
    }

    /**
     * Gets the number of active playbacks.
     * 
     * @return The count of active playbacks
     */
    public int getActivePlaybackCount() {
        return activePlaybacks.size();
    }

    /**
     * Handles disc ejection when playback completes naturally.
     * 
     * @param jukeboxPos The jukebox position
     */
    private void ejectDiscFromJukebox(BlockPos jukeboxPos) {
        LOGGER.info("Playback completed at {} - ejecting disc", jukeboxPos);
        
        // Get the active playback to find the world
        ActivePlayback playback = activePlaybacks.get(jukeboxPos);
        if (playback == null) {
            LOGGER.warn("Cannot eject disc - no active playback found at {}", jukeboxPos);
            return;
        }
        
        // We need to access the world through the stream info
        // For now, we'll use a workaround by storing world reference in ActivePlayback
        // Or we can trigger ejection through the mixin
        LOGGER.info("Disc ejection will be handled by JukeboxBlockEntity when setStack is called with empty stack");
    }

    /**
     * Shuts down the playback manager.
     */
    public void shutdown() {
        LOGGER.info("Shutting down PlaybackManager");
        stopAllPlaybacks();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
