package org.stepan.audio_disc.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.stepan.audio_disc.model.AudioMetadata;
import java.util.Optional;

public class NbtUtils {
    private static final String AUDIO_DISC_KEY = "audio_disc";
    private static final String AUDIO_ID_KEY = "audio_id";
    private static final String UPLOADED_BY_KEY = "uploaded_by";
    private static final String UPLOAD_TIME_KEY = "upload_time";
    private static final String METADATA_KEY = "metadata";
    private static final String FORMAT_KEY = "format";
    private static final String DURATION_KEY = "duration";
    private static final String BITRATE_KEY = "bitrate";
    private static final String SAMPLE_RATE_KEY = "sample_rate";
    private static final String TITLE_KEY = "title";

    public static NbtCompound serializeMetadata(AudioMetadata metadata) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString(FORMAT_KEY, metadata.format());
        nbt.putLong(DURATION_KEY, metadata.duration());
        nbt.putInt(BITRATE_KEY, metadata.bitrate());
        nbt.putInt(SAMPLE_RATE_KEY, metadata.sampleRate());
        nbt.putString(TITLE_KEY, metadata.title());
        return nbt;
    }

    public static Optional<AudioMetadata> deserializeMetadata(NbtCompound nbt) {
        if (nbt == null || nbt.isEmpty()) {
            return Optional.empty();
        }
        try {
            Optional<String> formatOpt = nbt.getString(FORMAT_KEY);
            Optional<Long> durationOpt = nbt.getLong(DURATION_KEY);
            Optional<Integer> bitrateOpt = nbt.getInt(BITRATE_KEY);
            Optional<Integer> sampleRateOpt = nbt.getInt(SAMPLE_RATE_KEY);
            Optional<String> titleOpt = nbt.getString(TITLE_KEY);
            
            if (formatOpt.isEmpty() || titleOpt.isEmpty()) {
                return Optional.empty();
            }
            
            String format = formatOpt.get();
            String title = titleOpt.get();
            
            if (format.isBlank() || title.isBlank()) {
                return Optional.empty();
            }
            
            return Optional.of(new AudioMetadata(
                format,
                durationOpt.orElse(0L),
                bitrateOpt.orElse(0),
                sampleRateOpt.orElse(0),
                title
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static void attachAudioToItem(ItemStack stack, String audioId, String uploadedBy, long uploadTime, AudioMetadata metadata) {
        if (stack.isEmpty()) return;
        
        NbtComponent currentData = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound rootNbt = currentData.copyNbt();
        
        NbtCompound audioDiscNbt = new NbtCompound();
        audioDiscNbt.putString(AUDIO_ID_KEY, audioId);
        audioDiscNbt.putString(UPLOADED_BY_KEY, uploadedBy);
        audioDiscNbt.putLong(UPLOAD_TIME_KEY, uploadTime);
        audioDiscNbt.put(METADATA_KEY, serializeMetadata(metadata));
        
        rootNbt.put(AUDIO_DISC_KEY, audioDiscNbt);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(rootNbt));
    }

    private static Optional<NbtCompound> getAudioDiscNbt(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return Optional.empty();
        }
        
        NbtCompound rootNbt = customData.copyNbt();
        if (!rootNbt.contains(AUDIO_DISC_KEY)) {
            return Optional.empty();
        }
        
        return rootNbt.getCompound(AUDIO_DISC_KEY);
    }

    public static Optional<String> getAudioId(ItemStack stack) {
        return getAudioDiscNbt(stack)
            .flatMap(nbt -> nbt.getString(AUDIO_ID_KEY))
            .filter(s -> !s.isBlank());
    }

    public static Optional<String> getUploadedBy(ItemStack stack) {
        return getAudioDiscNbt(stack)
            .flatMap(nbt -> nbt.getString(UPLOADED_BY_KEY))
            .filter(s -> !s.isBlank());
    }

    public static Optional<AudioMetadata> getMetadata(ItemStack stack) {
        return getAudioDiscNbt(stack)
            .filter(nbt -> nbt.contains(METADATA_KEY))
            .flatMap(nbt -> nbt.getCompound(METADATA_KEY))
            .flatMap(NbtUtils::deserializeMetadata);
    }

    public static boolean hasCustomAudio(ItemStack stack) {
        return getAudioId(stack).isPresent();
    }

    public static boolean validateNbtIntegrity(ItemStack stack) {
        return getAudioDiscNbt(stack)
            .map(nbt -> nbt.contains(AUDIO_ID_KEY) &&
                       nbt.contains(UPLOADED_BY_KEY) &&
                       nbt.contains(UPLOAD_TIME_KEY) &&
                       nbt.contains(METADATA_KEY))
            .orElse(false);
    }

    /**
     * Sets a custom title for a music disc.
     * 
     * @param stack The music disc ItemStack
     * @param customTitle The custom title to set
     */
    public static void setCustomTitle(ItemStack stack, String customTitle) {
        if (stack.isEmpty()) return;
        
        NbtComponent currentData = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound rootNbt = currentData.copyNbt();
        
        // Get or create audio_disc compound
        NbtCompound audioDiscNbt;
        if (rootNbt.contains(AUDIO_DISC_KEY)) {
            Optional<NbtCompound> audioDiscNbtOpt = rootNbt.getCompound(AUDIO_DISC_KEY);
            audioDiscNbt = audioDiscNbtOpt.orElse(new NbtCompound());
        } else {
            audioDiscNbt = new NbtCompound();
        }
        
        // Set custom title
        audioDiscNbt.putString("custom_title", customTitle);
        
        rootNbt.put(AUDIO_DISC_KEY, audioDiscNbt);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(rootNbt));
    }

    /**
     * Clears all audio data from a music disc.
     * 
     * @param stack The music disc ItemStack
     */
    public static void clearAudioData(ItemStack stack) {
        if (stack.isEmpty()) return;
        
        NbtComponent currentData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (currentData == null) return;
        
        NbtCompound rootNbt = currentData.copyNbt();
        
        // Remove audio_disc data
        if (rootNbt.contains(AUDIO_DISC_KEY)) {
            rootNbt.remove(AUDIO_DISC_KEY);
            
            if (rootNbt.isEmpty()) {
                // If NBT is now empty, remove the component entirely
                stack.remove(DataComponentTypes.CUSTOM_DATA);
            } else {
                stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(rootNbt));
            }
        }
    }
}
