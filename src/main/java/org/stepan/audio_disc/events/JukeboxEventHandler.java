package org.stepan.audio_disc.events;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.JukeboxBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stepan.audio_disc.Audio_disc;
import org.stepan.audio_disc.playback.PlaybackManager;
import org.stepan.audio_disc.storage.AudioStorageManager;

import java.util.Optional;

/**
 * Handles jukebox interactions without using mixins.
 */
public class JukeboxEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("AudioDisc");

    /**
     * Registers jukebox event handlers.
     */
    public static void register() {
        UseBlockCallback.EVENT.register(JukeboxEventHandler::onUseBlock);
        LOGGER.info("Registered jukebox event handlers");
    }

    /**
     * Handles block use events for jukeboxes.
     */
    private static ActionResult onUseBlock(net.minecraft.entity.player.PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        if (world.isClient) {
            return ActionResult.PASS;
        }

        BlockPos pos = hitResult.getBlockPos();
        
        // Check if it's a jukebox
        if (!world.getBlockState(pos).isOf(Blocks.JUKEBOX)) {
            return ActionResult.PASS;
        }

        // Get jukebox block entity
        if (!(world.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox)) {
            return ActionResult.PASS;
        }

        ItemStack heldItem = player.getStackInHand(hand);
        ItemStack currentDisc = jukebox.getStack();

        // If jukebox is empty and player is holding a disc
        if (currentDisc.isEmpty() && !heldItem.isEmpty() && isMusicDisc(heldItem)) {
            return handleDiscInsertion(player, (ServerWorld) world, pos, jukebox, heldItem, hand);
        }
        
        // If jukebox has a disc and player clicked (regardless of hand contents)
        if (!currentDisc.isEmpty()) {
            return handleDiscRemoval(player, (ServerWorld) world, pos, jukebox, currentDisc, heldItem.isEmpty());
        }

        return ActionResult.PASS;
    }

    /**
     * Handles disc insertion into jukebox.
     */
    private static ActionResult handleDiscInsertion(net.minecraft.entity.player.PlayerEntity player, ServerWorld world, 
                                                   BlockPos pos, JukeboxBlockEntity jukebox, ItemStack disc, Hand hand) {
        
        AudioStorageManager storageManager = Audio_disc.getStorageManager();
        if (storageManager == null) {
            return ActionResult.PASS;
        }

        // Check if this disc has custom audio
        Optional<String> audioId = storageManager.getDiscAudioId(disc);
        if (audioId.isEmpty()) {
            // Not a custom disc, let vanilla handle it
            return ActionResult.PASS;
        }

        LOGGER.info("Custom audio disc detected, starting custom playback at {}", pos);

        // Insert the disc into jukebox
        ItemStack discCopy = disc.copy();
        discCopy.setCount(1);
        jukebox.setStack(discCopy);

        // Remove disc from player's hand
        if (!player.getAbilities().creativeMode) {
            disc.decrement(1);
        }

        // Start custom playback
        PlaybackManager playbackManager = Audio_disc.getPlaybackManager();
        if (playbackManager != null) {
            boolean started = playbackManager.startPlayback(world, pos, discCopy);
            if (started) {
                LOGGER.info("Started custom audio playback at {}", pos);
            } else {
                LOGGER.warn("Failed to start custom audio playback at {}", pos);
            }
        }

        return ActionResult.SUCCESS;
    }

    /**
     * Handles disc removal from jukebox.
     */
    private static ActionResult handleDiscRemoval(net.minecraft.entity.player.PlayerEntity player, ServerWorld world,
                                                 BlockPos pos, JukeboxBlockEntity jukebox, ItemStack currentDisc, boolean handWasEmpty) {
        
        AudioStorageManager storageManager = Audio_disc.getStorageManager();
        if (storageManager == null) {
            return ActionResult.PASS;
        }

        // Check if this is a custom disc
        Optional<String> audioId = storageManager.getDiscAudioId(currentDisc);
        if (audioId.isEmpty()) {
            // Not a custom disc, let vanilla handle it
            return ActionResult.PASS;
        }

        LOGGER.info("Removing custom audio disc from jukebox at {}", pos);

        // Stop custom playback
        PlaybackManager playbackManager = Audio_disc.getPlaybackManager();
        if (playbackManager != null) {
            playbackManager.stopPlayback(pos);
        }

        // If hand was empty, we handle the removal ourselves
        if (handWasEmpty) {
            // Remove disc from jukebox and give to player
            jukebox.setStack(ItemStack.EMPTY);
            
            // Give disc to player
            if (!player.getInventory().insertStack(currentDisc)) {
                // If inventory is full, drop the disc
                player.dropItem(currentDisc, false);
            }
            
            return ActionResult.SUCCESS;
        } else {
            // If hand was not empty, we only stop the playback and let vanilla handle the disc removal
            // This ensures the music stops even when the disc is ejected due to full hand
            return ActionResult.PASS;
        }
    }

    /**
     * Checks if an item is a music disc.
     */
    private static boolean isMusicDisc(ItemStack stack) {
        if (stack.isEmpty()) return false;
        
        String itemName = stack.getItem().toString();
        return itemName.contains("music_disc");
    }
}