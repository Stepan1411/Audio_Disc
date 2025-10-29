package org.stepan.audio_disc.api;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.stepan.audio_disc.model.AudioMetadata;

/**
 * Event fired when a player uploads audio to a disc.
 */
public record AudioUploadEvent(
    ServerPlayerEntity player,
    ItemStack disc,
    String audioId,
    AudioMetadata metadata,
    long timestamp
) {
    /**
     * Creates a new audio upload event.
     * 
     * @param player The player who uploaded the audio
     * @param disc The music disc item
     * @param audioId The audio identifier
     * @param metadata The audio metadata
     */
    public AudioUploadEvent(ServerPlayerEntity player, ItemStack disc, String audioId, AudioMetadata metadata) {
        this(player, disc, audioId, metadata, System.currentTimeMillis());
    }
}
