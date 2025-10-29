package org.stepan.audio_disc.api;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Event fired when audio playback stops.
 */
public record PlaybackStopEvent(
    BlockPos jukeboxPos,
    ServerWorld world,
    String audioId,
    long playbackDuration,
    StopReason reason,
    long timestamp
) {
    /**
     * Creates a new playback stop event.
     * 
     * @param jukeboxPos The position of the jukebox
     * @param world The server world
     * @param audioId The audio identifier
     * @param playbackDuration The duration of playback in milliseconds
     * @param reason The reason playback stopped
     */
    public PlaybackStopEvent(BlockPos jukeboxPos, ServerWorld world, String audioId, 
                            long playbackDuration, StopReason reason) {
        this(jukeboxPos, world, audioId, playbackDuration, reason, System.currentTimeMillis());
    }
}
