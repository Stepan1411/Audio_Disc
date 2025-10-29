package org.stepan.audio_disc.playback;

import net.minecraft.util.math.BlockPos;
import org.stepan.audio_disc.model.AudioMetadata;
import org.stepan.audio_disc.playback.SimpleVoiceChatIntegration.AudioStreamInfo;

import java.util.UUID;

/**
 * Represents an active audio playback session.
 */
public class ActivePlayback {
    private final String audioId;
    private final BlockPos position;
    private final UUID streamId;
    private final AudioStreamInfo stream;
    private final long startTime;
    private final AudioMetadata metadata;
    private volatile boolean playing;

    public ActivePlayback(String audioId, BlockPos position, UUID streamId, 
                         AudioStreamInfo stream, AudioMetadata metadata) {
        this.audioId = audioId;
        this.position = position;
        this.streamId = streamId;
        this.stream = stream;
        this.startTime = System.currentTimeMillis();
        this.metadata = metadata;
        this.playing = true;
    }

    /**
     * Gets the audio ID being played.
     * 
     * @return The audio ID
     */
    public String getAudioId() {
        return audioId;
    }

    /**
     * Gets the position of the jukebox.
     * 
     * @return The block position
     */
    public BlockPos getPosition() {
        return position;
    }

    /**
     * Gets the stream ID.
     * 
     * @return The stream UUID
     */
    public UUID getStreamId() {
        return streamId;
    }

    /**
     * Gets the audio stream information.
     * 
     * @return The stream info
     */
    public AudioStreamInfo getStream() {
        return stream;
    }

    /**
     * Gets the start time of playback.
     * 
     * @return The start time in milliseconds
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Gets the audio metadata.
     * 
     * @return The metadata
     */
    public AudioMetadata getMetadata() {
        return metadata;
    }

    /**
     * Checks if the playback is currently active.
     * 
     * @return true if playing, false otherwise
     */
    public boolean isPlaying() {
        if (!playing || stream == null) {
            return false;
        }
        
        // Check if it's a PersonalAudioPlayerInfo (new AudioPlayer approach)
        if (stream instanceof SimpleVoiceChatIntegration.PersonalAudioPlayerInfo) {
            SimpleVoiceChatIntegration.PersonalAudioPlayerInfo playerInfo = 
                (SimpleVoiceChatIntegration.PersonalAudioPlayerInfo) stream;
            return stream.isPlaying() && !playerInfo.getAudioSupplier().isFinished();
        }
        
        // Fallback to old method
        return stream.isPlaying();
    }

    /**
     * Gets the elapsed playback time.
     * 
     * @return The elapsed time in milliseconds
     */
    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Checks if the playback has completed based on duration.
     * 
     * @return true if completed, false otherwise
     */
    public boolean isComplete() {
        if (metadata.duration() <= 0) {
            return false;
        }
        return getElapsedTime() >= metadata.duration();
    }

    /**
     * Stops the playback.
     */
    public void stop() {
        this.playing = false;
        if (stream != null) {
            stream.setPlaying(false);
        }
    }
}
