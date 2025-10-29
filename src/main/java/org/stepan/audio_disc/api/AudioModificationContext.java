package org.stepan.audio_disc.api;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.stepan.audio_disc.model.AudioMetadata;

/**
 * Context provided to listeners for audio modification.
 */
public record AudioModificationContext(
    String audioId,
    byte[] audioData,
    AudioMetadata metadata,
    BlockPos jukeboxPos,
    ServerWorld world
) {
}
