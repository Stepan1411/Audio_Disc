package org.stepan.audio_disc.mixin;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.stepan.audio_disc.Audio_disc;
import org.stepan.audio_disc.model.AudioData;
import org.stepan.audio_disc.model.AudioMetadata;
import org.stepan.audio_disc.storage.AudioStorageManager;

import java.util.List;
import java.util.Optional;

/**
 * Mixin to add custom tooltips for audio discs.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    /**
     * Adds custom tooltip information for audio discs.
     */
    @Inject(method = "getTooltip", at = @At("RETURN"))
    private void addCustomAudioTooltip(Item.TooltipContext context, net.minecraft.entity.player.PlayerEntity player, 
                                      TooltipType type, CallbackInfoReturnable<List<Text>> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        
        // Check if this is a music disc
        if (!stack.getItem().toString().contains("music_disc")) {
            return;
        }

        // Get storage manager
        AudioStorageManager storageManager = Audio_disc.getStorageManager();
        if (storageManager == null) {
            return;
        }

        // Check if this disc has custom audio
        Optional<String> audioIdOpt = storageManager.getDiscAudioId(stack);
        if (audioIdOpt.isEmpty()) {
            return;
        }

        String audioId = audioIdOpt.get();
        Optional<AudioData> audioDataOpt = storageManager.getAudio(audioId);
        
        if (audioDataOpt.isEmpty()) {
            return;
        }

        AudioData audioData = audioDataOpt.get();
        AudioMetadata metadata = audioData.metadata();
        
        List<Text> tooltip = cir.getReturnValue();
        
        // Add separator
        tooltip.add(Text.literal(""));
        
        // Add custom audio indicator
        tooltip.add(Text.literal("♪ Custom Audio Disc").formatted(Formatting.GOLD, Formatting.BOLD));
        
        // Add title (check for custom title first)
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        String title = metadata.title();
        
        if (customData != null) {
            NbtCompound nbt = customData.copyNbt();
            if (nbt.contains("audio_disc")) {
                Optional<NbtCompound> audioDiscNbtOpt = nbt.getCompound("audio_disc");
                if (audioDiscNbtOpt.isPresent()) {
                    NbtCompound audioDiscNbt = audioDiscNbtOpt.get();
                    if (audioDiscNbt.contains("custom_title")) {
                        Optional<String> customTitleOpt = audioDiscNbt.getString("custom_title");
                        if (customTitleOpt.isPresent()) {
                            title = customTitleOpt.get();
                        }
                    }
                }
            }
        }
        
        tooltip.add(Text.literal("Title: ").formatted(Formatting.GRAY)
            .append(Text.literal(title).formatted(Formatting.WHITE)));
        
        // Add duration
        if (metadata.duration() > 0) {
            long durationSeconds = metadata.duration() / 1000;
            long minutes = durationSeconds / 60;
            long seconds = durationSeconds % 60;
            String durationStr = String.format("%d:%02d", minutes, seconds);
            
            tooltip.add(Text.literal("Duration: ").formatted(Formatting.GRAY)
                .append(Text.literal(durationStr).formatted(Formatting.WHITE)));
        }
        
        // Add format
        tooltip.add(Text.literal("Format: ").formatted(Formatting.GRAY)
            .append(Text.literal(metadata.format().toUpperCase()).formatted(Formatting.WHITE)));
        
        // Add uploader
        tooltip.add(Text.literal("Uploaded by: ").formatted(Formatting.GRAY)
            .append(Text.literal(audioData.uploadedBy()).formatted(Formatting.AQUA)));
    }
}
