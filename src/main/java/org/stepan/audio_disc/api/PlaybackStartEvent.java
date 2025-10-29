package org.stepan.audio_disc.api;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.stepan.audio_disc.model.AudioMetadata;

/**
 * Event fired when audio playback starts.
 */
public record PlaybackStartEvent(
    BlockPos jukeboxPos,
    ServerWorld world,
    String audioId,
    AudioMetadata metadata,
    long timestamp
) {
    /**
     * Creates a new playback start event.
     * 
     * @param jukeboxPos The position of the jukebox
     * @param world The server world
     * @param audioId The audio identifier
     * @param metadata The audio metadata
     */
    public PlaybackStartEvent(BlockPos jukeboxPos, ServerWorld world, String audioId, AudioMetadata metadata) {
        this(jukeboxPos, world, audioId, metadata, System.currentTimeMillis());
    }
}
