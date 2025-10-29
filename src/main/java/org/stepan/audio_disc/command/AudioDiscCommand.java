package org.stepan.audio_disc.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.loader.api.FabricLoader;
import org.stepan.audio_disc.Audio_disc;
import org.stepan.audio_disc.api.AudioDiscAPIImpl;
import org.stepan.audio_disc.api.AudioUploadEvent;
import org.stepan.audio_disc.config.AudioDiscConfig;
import org.stepan.audio_disc.download.AudioDownloadManager;
import org.stepan.audio_disc.model.AudioMetadata;
import org.stepan.audio_disc.model.ValidationResult;
import org.stepan.audio_disc.processing.AudioProcessor;
import org.stepan.audio_disc.storage.AudioStorageManager;
import org.stepan.audio_disc.util.RateLimiter;
import org.stepan.audio_disc.util.Localization;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class AudioDiscCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("AudioDisc");

    /**
     * Registers the /audiodisc command and its subcommands.
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, 
                               CommandRegistryAccess registryAccess,
                               CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("audiodisc")
            .then(CommandManager.literal("upload")
                .then(CommandManager.argument("url", StringArgumentType.greedyString())
                    .executes(context -> executeUpload(context, StringArgumentType.getString(context, "url")))
                )
            )
            .then(CommandManager.literal("youtube")
                .then(CommandManager.argument("url", StringArgumentType.greedyString())
                    .executes(context -> executeYouTube(context, StringArgumentType.getString(context, "url")))
                )
            )
            .then(CommandManager.literal("info")
                .executes(AudioDiscCommand::executeInfo)
            )
            .then(CommandManager.literal("clear")
                .executes(AudioDiscCommand::executeClear)
            )
            .then(CommandManager.literal("reload")
                .requires(source -> source.hasPermissionLevel(3))
                .executes(AudioDiscCommand::executeReload)
            )
            .then(CommandManager.literal("lang")
                .requires(source -> source.hasPermissionLevel(3))
                .then(CommandManager.literal("create")
                    .then(CommandManager.argument("language", StringArgumentType.string())
                        .executes(context -> executeCreateLang(context, StringArgumentType.getString(context, "language")))
                    )
                )
                .then(CommandManager.literal("list")
                    .executes(AudioDiscCommand::executeListLang)
                )
            )
        );
        
        LOGGER.info("Registered /audiodisc command");
    }

    /**
     * Executes the upload command.
     */
    private static int executeUpload(CommandContext<ServerCommandSource> context, String url) {
        ServerCommandSource source = context.getSource();
        
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        if (!validateHeldItem(player)) {
            player.sendMessage(Text.literal(Localization.getForPlayer("command.upload.no_disc", player)), false);
            return 0;
        }

        // Validate URL
        if (url == null || url.trim().isEmpty()) {
            player.sendMessage(Text.literal(Localization.getForPlayer("command.upload.invalid_url", player)), false);
            return 0;
        }

        // Check rate limiting
        org.stepan.audio_disc.util.RateLimiter rateLimiter = Audio_disc.getRateLimiter();
        if (rateLimiter != null) {
            org.stepan.audio_disc.util.RateLimiter.RateLimitResult limitResult = rateLimiter.checkLimit(player.getUuid());
            if (!limitResult.isAllowed()) {
                player.sendMessage(Text.literal(Localization.formatForPlayer("command.upload.rate_limit", player, limitResult.getMessage())), false);
                return 0;
            }
        }

        player.sendMessage(Text.literal(Localization.getForPlayer("command.upload.starting", player)), false);
        
        // Process asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                processDirectUpload(player, url, player.getMainHandStack());
            } catch (Exception e) {
                LOGGER.error("Error processing direct upload", e);
                player.sendMessage(Text.literal("§cОшибка загрузки: " + e.getMessage()), false);
            }
        });

        return 1;
    }

    /**
     * Executes the YouTube command.
     */
    private static int executeYouTube(CommandContext<ServerCommandSource> context, String url) {
        ServerCommandSource source = context.getSource();
        
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        if (!validateHeldItem(player)) {
            player.sendMessage(Text.literal(Localization.getForPlayer("command.upload.no_disc", player)), false);
            return 0;
        }

        if (!isValidYouTubeUrl(url)) {
            player.sendMessage(Text.literal(Localization.getForPlayer("command.youtube.invalid_url", player)), false);
            player.sendMessage(Text.literal(Localization.getForPlayer("command.youtube.supported_formats", player)), false);
            return 0;
        }

        // Check if yt-dlp is available
        if (!org.stepan.audio_disc.download.YtDlpManager.isAvailable()) {
            player.sendMessage(Text.literal(Localization.getForPlayer("command.youtube.yt_dlp_required", player)), false);
            player.sendMessage(Text.literal(Localization.getForPlayer("command.youtube.use_direct", player)), false);
            return 0;
        }

        player.sendMessage(Text.literal(Localization.getForPlayer("command.youtube.starting", player)), false);
        player.sendMessage(Text.literal("§7URL: " + url), false);
        
        // Process asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                processYouTubeDownload(player, url, player.getMainHandStack());
            } catch (Exception e) {
                LOGGER.error("Error processing YouTube download", e);
                player.sendMessage(Text.literal("§cОшибка загрузки: " + e.getMessage()), false);
            }
        });

        return 1;
    }

    /**
     * Shows information about the audio on the held disc.
     */
    private static int executeInfo(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        if (!validateHeldItem(player)) {
            player.sendMessage(Text.literal(Localization.getForPlayer("command.info.no_disc", player)), false);
            return 0;
        }

        try {
            AudioStorageManager storageManager = Audio_disc.getStorageManager();
            if (storageManager == null) {
                player.sendMessage(Text.literal("§cError: Storage manager not initialized"), false);
                return 0;
            }

            ItemStack disc = player.getMainHandStack();
            String audioId = storageManager.getDiscAudioId(disc).orElse(null);
            
            if (audioId == null) {
                player.sendMessage(Text.literal(Localization.getForPlayer("command.info.no_audio", player)), false);
                return 0;
            }

            var audioData = storageManager.getAudio(audioId);
            if (audioData.isEmpty()) {
                player.sendMessage(Text.literal(Localization.getForPlayer("command.info.no_audio", player)), false);
                return 0;
            }

            var audio = audioData.get();
            var metadata = audio.getMetadata();
            
            // Display info
            player.sendMessage(Text.literal(Localization.getForPlayer("command.info.header", player)), false);
            player.sendMessage(Text.literal(Localization.formatForPlayer("command.info.id", player, audioId)), false);
            player.sendMessage(Text.literal(Localization.formatForPlayer("command.info.title", player, metadata.title())), false);
            player.sendMessage(Text.literal(Localization.formatForPlayer("command.info.format", player, metadata.format().toUpperCase())), false);
            
            if (metadata.duration() > 0) {
                String duration = formatDuration(metadata.duration());
                player.sendMessage(Text.literal(Localization.formatForPlayer("command.info.duration", player, duration)), false);
            }
            
            if (metadata.bitrate() > 0) {
                player.sendMessage(Text.literal(Localization.formatForPlayer("command.info.bitrate", player, metadata.bitrate())), false);
            }
            
            if (metadata.sampleRate() > 0) {
                player.sendMessage(Text.literal(Localization.formatForPlayer("command.info.sample_rate", player, metadata.sampleRate())), false);
            }
            
            player.sendMessage(Text.literal(Localization.formatForPlayer("command.info.uploaded_by", player, audio.getUploadedBy())), false);
            player.sendMessage(Text.literal(Localization.formatForPlayer("command.info.file_size", player, formatFileSize(audio.getData().length))), false);
            
        } catch (Exception e) {
            LOGGER.error("Error getting disc info", e);
            player.sendMessage(Text.literal("§cError getting disc information: " + e.getMessage()), false);
        }

        return 1;
    }

    /**
     * Clears audio from the held disc.
     */
    private static int executeClear(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        if (!validateHeldItem(player)) {
            player.sendMessage(Text.literal(Localization.getForPlayer("command.clear.no_disc", player)), false);
            return 0;
        }

        try {
            AudioStorageManager storageManager = Audio_disc.getStorageManager();
            if (storageManager == null) {
                player.sendMessage(Text.literal("§cError: Storage manager not initialized"), false);
                return 0;
            }

            ItemStack disc = player.getMainHandStack();
            String audioId = storageManager.getDiscAudioId(disc).orElse(null);
            
            if (audioId == null) {
                player.sendMessage(Text.literal(Localization.getForPlayer("command.clear.no_audio", player)), false);
                return 0;
            }

            // Clear audio from disc (remove NBT data)
            storageManager.clearDiscAudio(disc);
            
            player.sendMessage(Text.literal(Localization.getForPlayer("command.clear.success", player)), false);
            
        } catch (Exception e) {
            LOGGER.error("Error clearing disc audio", e);
            player.sendMessage(Text.literal(Localization.formatForPlayer("command.clear.error", player, e.getMessage())), false);
        }

        return 1;
    }

    /**
     * Reloads the mod configuration.
     */
    private static int executeReload(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            // Reload configuration
            Path configPath = FabricLoader.getInstance().getConfigDir().resolve("audiodisc").resolve("config.json");
            AudioDiscConfig newConfig = AudioDiscConfig.load(configPath);
            
            if (newConfig.validate()) {
                // Update the config in the main class
                Audio_disc.updateConfig(newConfig);
                
                // Reload localization
                Localization.loadLanguage(newConfig.getLanguage());
                
                source.sendFeedback(() -> Text.literal(Localization.get("command.reload.success")), true);
            } else {
                source.sendFeedback(() -> Text.literal(Localization.get("command.reload.failed")), true);
            }
            
        } catch (Exception e) {
            LOGGER.error("Error reloading configuration", e);
            source.sendFeedback(() -> Text.literal(Localization.format("command.reload.failed", e.getMessage())), true);
        }

        return 1;
    }

    /**
     * Creates a full language file for customization.
     */
    private static int executeCreateLang(CommandContext<ServerCommandSource> context, String language) {
        ServerCommandSource source = context.getSource();
        
        try {
            boolean success = Localization.createFullLanguageFile(language);
            
            if (success) {
                source.sendFeedback(() -> Text.literal("§aСоздан файл локализации для языка: " + language), true);
                source.sendFeedback(() -> Text.literal("§7Файл: config/audiodisc/lang/" + language.toLowerCase() + ".json"), false);
                source.sendFeedback(() -> Text.literal("§7Отредактируйте файл и используйте /audiodisc reload для применения"), false);
            } else {
                source.sendFeedback(() -> Text.literal("§cНе удалось создать файл локализации для языка: " + language), true);
            }
            
        } catch (Exception e) {
            LOGGER.error("Error creating language file", e);
            source.sendFeedback(() -> Text.literal("§cОшибка создания файла локализации: " + e.getMessage()), true);
        }

        return 1;
    }

    /**
     * Lists available language files.
     */
    private static int executeListLang(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            Path langDir = FabricLoader.getInstance().getConfigDir().resolve("audiodisc").resolve("lang");
            
            source.sendFeedback(() -> Text.literal("§a=== Файлы локализации ==="), false);
            source.sendFeedback(() -> Text.literal("§7Встроенные языки: ru_ru, en_us, fr_fr, de_de, es_es, zh_cn"), false);
            
            if (Files.exists(langDir)) {
                source.sendFeedback(() -> Text.literal("§7Кастомные файлы в config/audiodisc/lang/:"), false);
                
                Files.list(langDir)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        String langCode = fileName.replace(".json", "");
                        source.sendFeedback(() -> Text.literal("§f- " + langCode + " §7(" + fileName + ")"), false);
                    });
            } else {
                source.sendFeedback(() -> Text.literal("§7Кастомных файлов не найдено"), false);
            }
            
            source.sendFeedback(() -> Text.literal("§7Используйте §f/audiodisc lang create <код_языка> §7для создания"), false);
            
        } catch (Exception e) {
            LOGGER.error("Error listing language files", e);
            source.sendFeedback(() -> Text.literal("§cОшибка получения списка файлов: " + e.getMessage()), true);
        }

        return 1;
    }

    /**
     * Checks if yt-dlp is available.
     */
    private static boolean isYtDlpAvailable() {
        return org.stepan.audio_disc.download.YtDlpManager.isAvailable();
    }

    /**
     * Processes direct audio file upload from URL.
     */
    private static void processDirectUpload(ServerPlayerEntity player, String url, ItemStack disc) {
        try {
            LOGGER.info("Starting direct upload from URL: {}", url);
            
            // Validate URL
            if (!isValidUrl(url)) {
                player.sendMessage(Text.literal(Localization.getForPlayer("command.upload.url_protocol", player)), false);
                return;
            }

            // Get managers
            AudioDownloadManager downloadManager = Audio_disc.getDownloadManager();
            AudioProcessor audioProcessor = Audio_disc.getAudioProcessor();
            AudioStorageManager storageManager = Audio_disc.getStorageManager();

            if (downloadManager == null || audioProcessor == null || storageManager == null) {
                player.sendMessage(Text.literal(Localization.getForPlayer("command.upload.system_error", player)), false);
                return;
            }

            // Create progress tracker for simplified progress reporting
            ProgressTracker progressTracker = new ProgressTracker(player);
            progressTracker.setStage("downloading");

            // Download audio file
            CompletableFuture<byte[]> downloadFuture = downloadManager.downloadAudio(url, player.getUuid(), progress -> {
                // Update progress every 10%
                if (progress % 10 == 0) {
                    progressTracker.updateProgress(progress / 4); // Download is 25% of total process
                }
            });
            
            downloadFuture.thenAccept(audioData -> {
                try {
                    if (audioData == null || audioData.length == 0) {
                        player.sendMessage(Text.literal(Localization.formatForPlayer("command.upload.download_failed", player, "File not found")), false);
                        return;
                    }

                    progressTracker.updateProgress(30); // Download complete

                    // Validate audio format
                    ValidationResult validation = audioProcessor.validateFormat(audioData);
                    if (!validation.valid()) {
                        player.sendMessage(Text.literal(Localization.formatForPlayer("command.upload.error", player, validation.errorMessage())), false);
                        return;
                    }

                    progressTracker.setStage("processing");
                    progressTracker.updateProgress(50);

                    // Extract metadata
                    org.stepan.audio_disc.model.AudioMetadata metadata = audioProcessor.extractMetadata(audioData);
                    
                    // Process audio
                    byte[] processedData = audioProcessor.processAudio(audioData);

                    progressTracker.setStage("saving");
                    progressTracker.updateProgress(80);

                    // Store audio
                    String audioId = storageManager.storeAudio(processedData, metadata, player.getName().getString());
                    
                    // Attach to disc
                    storageManager.attachToDisc(disc, audioId);

                    progressTracker.updateProgress(100);

                    // Success message
                    player.sendMessage(Text.literal(Localization.getForPlayer("command.upload.success", player)), false);

                    // Record successful upload for rate limiting
                    org.stepan.audio_disc.util.RateLimiter rateLimiter = Audio_disc.getRateLimiter();
                    if (rateLimiter != null) {
                        rateLimiter.recordUpload(player.getUuid());
                    }

                    // Fire API event
                    AudioDiscAPIImpl.getInstance().fireAudioUploadEvent(new AudioUploadEvent(
                        player, disc, audioId, metadata, System.currentTimeMillis()
                    ));

                } catch (Exception e) {
                    LOGGER.error("Error processing uploaded audio", e);
                    player.sendMessage(Text.literal(Localization.formatForPlayer("command.upload.error", player, e.getMessage())), false);
                }
            }).exceptionally(throwable -> {
                LOGGER.error("Error downloading audio", throwable);
                player.sendMessage(Text.literal(Localization.formatForPlayer("command.upload.download_failed", player, throwable.getMessage())), false);
                return null;
            });

        } catch (Exception e) {
            LOGGER.error("Direct upload failed", e);
            player.sendMessage(Text.literal(Localization.formatForPlayer("command.upload.download_failed", player, e.getMessage())), false);
        }
    }

    /**
     * Processes YouTube download using yt-dlp and then processes through upload logic.
     */
    private static void processYouTubeDownload(ServerPlayerEntity player, String youtubeUrl, ItemStack disc) {
        try {
            LOGGER.info("Starting YouTube download and conversion to MP3: {}", youtubeUrl);
            
            // Create temp directory
            Path tempDir = FabricLoader.getInstance().getGameDir().resolve("temp_audio");
            if (!tempDir.toFile().exists()) {
                tempDir.toFile().mkdirs();
            }

            String videoId = extractVideoId(youtubeUrl);
            String outputFileName = videoId + ".mp3";
            Path outputFile = tempDir.resolve(outputFileName);

            // Get yt-dlp executable path
            String ytDlpPath = org.stepan.audio_disc.download.YtDlpManager.getExecutablePath();
            if (ytDlpPath == null) {
                player.sendMessage(Text.literal("§cОшибка: yt-dlp недоступен"), false);
                return;
            }

            player.sendMessage(Text.literal("§7Скачивание видео с YouTube..."), false);

            // Check if FFmpeg is available for conversion
            boolean hasFFmpeg = isFFmpegAvailable();
            
            ProcessBuilder pb;
            if (hasFFmpeg) {
                player.sendMessage(Text.literal("§7FFmpeg найден, конвертируем в MP3..."), false);
                // Use yt-dlp to download and convert to MP3
                pb = new ProcessBuilder(
                    ytDlpPath,
                    "--extract-audio",           // Extract audio only
                    "--audio-format", "mp3",     // Convert to MP3
                    "--audio-quality", "0",      // Best quality
                    "--max-filesize", "50M",     // Max file size
                    "--no-playlist",             // Single video only
                    "--output", tempDir.resolve(videoId + ".%(ext)s").toString(),
                    youtubeUrl
                );
            } else {
                player.sendMessage(Text.literal("§7FFmpeg не найден, скачиваем в исходном формате..."), false);
                // Download best audio without conversion
                pb = new ProcessBuilder(
                    ytDlpPath,
                    "--format", "bestaudio[ext=mp3]/bestaudio[ext=ogg]/bestaudio",
                    "--max-filesize", "50M",     // Max file size
                    "--no-playlist",             // Single video only
                    "--output", tempDir.resolve(videoId + ".%(ext)s").toString(),
                    youtubeUrl
                );
            }

            pb.directory(tempDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            
            // Capture output for debugging and progress
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    LOGGER.debug("yt-dlp: {}", line);
                    
                    // Show progress to user
                    if (line.contains("[download]") && line.contains("%")) {
                        // Extract percentage from yt-dlp output
                        try {
                            String percent = line.substring(line.indexOf("]") + 1).trim();
                            if (percent.contains("%")) {
                                percent = percent.substring(0, percent.indexOf("%")).trim();
                                if (percent.matches("\\d+\\.\\d+")) {
                                    int progress = (int) Double.parseDouble(percent);
                                    if (progress % 25 == 0) {
                                        player.sendMessage(Text.literal("§7Прогресс: " + progress + "%"), false);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Ignore parsing errors
                        }
                    }
                }
            }

            int exitCode = process.waitFor();
            
            LOGGER.info("yt-dlp finished with exit code: {}", exitCode);
            if (output.length() > 0) {
                LOGGER.info("yt-dlp output:\n{}", output.toString());
            }

            boolean ffmpegMissing = false;
            if (exitCode != 0) {
                LOGGER.error("yt-dlp failed with exit code: {}", exitCode);
                
                String errorMsg = output.toString();
                if (errorMsg.contains("Sign in to confirm")) {
                    player.sendMessage(Text.literal("§cОшибка: видео требует авторизации"), false);
                    return;
                } else if (errorMsg.contains("Video unavailable")) {
                    player.sendMessage(Text.literal("§cОшибка: видео недоступно"), false);
                    return;
                } else if (errorMsg.contains("Private video")) {
                    player.sendMessage(Text.literal("§cОшибка: приватное видео"), false);
                    return;
                } else if (errorMsg.contains("ffmpeg not found") || errorMsg.contains("ffprobe and ffmpeg not found")) {
                    player.sendMessage(Text.literal("§eВнимание: FFmpeg не найден для конвертации в MP3"), false);
                    player.sendMessage(Text.literal("§7Попробуем использовать скачанный файл напрямую..."), false);
                    ffmpegMissing = true;
                    // Don't return, continue with downloaded file
                } else {
                    player.sendMessage(Text.literal("§cОшибка загрузки с YouTube"), false);
                    player.sendMessage(Text.literal("§7Проверьте ссылку и попробуйте еще раз"), false);
                    return;
                }
            }

            // Look for the converted MP3 file
            java.io.File mp3File = outputFile.toFile();
            if (!mp3File.exists()) {
                // Try to find any audio file with the video ID
                java.io.File[] files = tempDir.toFile().listFiles((dir, name) -> 
                    name.startsWith(videoId) && (name.endsWith(".mp3") || name.endsWith(".m4a") || 
                    name.endsWith(".webm") || name.endsWith(".ogg")));
                
                if (files != null && files.length > 0) {
                    mp3File = files[0];
                    LOGGER.info("Found alternative audio file: {}", mp3File.getName());
                } else {
                    LOGGER.error("No audio file found after yt-dlp conversion");
                    player.sendMessage(Text.literal("§cОшибка: конвертированный файл не найден"), false);
                    return;
                }
            }

            LOGGER.info("Found audio file: {}", mp3File.getName());
            
            if (ffmpegMissing) {
                player.sendMessage(Text.literal("§aВидео скачано! Используем исходный формат."), false);
                player.sendMessage(Text.literal("§7Файл: " + mp3File.getName()), false);
            } else {
                player.sendMessage(Text.literal("§aВидео скачано и конвертировано в MP3!"), false);
            }
            player.sendMessage(Text.literal("§7Размер: " + formatFileSize(mp3File.length())), false);

            // Read the MP3 file
            byte[] audioData = java.nio.file.Files.readAllBytes(mp3File.toPath());
            
            // Now use the upload logic to process the audio
            player.sendMessage(Text.literal("§7Обработка аудио через систему мода..."), false);
            
            // Get managers
            AudioProcessor audioProcessor = Audio_disc.getAudioProcessor();
            AudioStorageManager storageManager = Audio_disc.getStorageManager();

            if (audioProcessor == null || storageManager == null) {
                player.sendMessage(Text.literal("§cОшибка: системы мода не инициализированы"), false);
                return;
            }

            // Handle WebM format
            String fileExtension = getFileExtension(mp3File.getName());
            if ("webm".equals(fileExtension)) {
                player.sendMessage(Text.literal("§eВнимание: WebM формат обнаружен"), false);
                
                // Check if FFmpeg is available or wait for it
                if (!isFFmpegAvailable()) {
                    player.sendMessage(Text.literal("§7FFmpeg не найден, ожидаем автоматической установки..."), false);
                    
                    // Wait up to 30 seconds for FFmpeg to be installed
                    boolean ffmpegReady = waitForFFmpeg(30);
                    if (!ffmpegReady) {
                        player.sendMessage(Text.literal("§cFFmpeg не установился в течение 30 секунд"), false);
                        player.sendMessage(Text.literal("§7WebM не поддерживается для воспроизведения"), false);
                        player.sendMessage(Text.literal("§7Попробуйте позже или установите FFmpeg вручную"), false);
                        return;
                    } else {
                        player.sendMessage(Text.literal("§aFFmpeg установлен! Конвертируем WebM..."), false);
                    }
                }
                
                // Try to convert WebM to MP3 using FFmpeg
                java.io.File convertedFile = tryConvertWebMToOgg(mp3File, tempDir);
                if (convertedFile != null) {
                    player.sendMessage(Text.literal("§aWebM успешно конвертирован в MP3"), false);
                    mp3File = convertedFile;
                    audioData = java.nio.file.Files.readAllBytes(mp3File.toPath());
                } else {
                    player.sendMessage(Text.literal("§cНе удалось конвертировать WebM"), false);
                    player.sendMessage(Text.literal("§7Проверьте логи сервера для подробностей"), false);
                    return;
                }
            }

            // Validate audio format
            ValidationResult validation = audioProcessor.validateFormat(audioData);
            if (!validation.valid()) {
                player.sendMessage(Text.literal("§cОшибка: " + validation.errorMessage()), false);
                return;
            }

            // Extract metadata
            org.stepan.audio_disc.model.AudioMetadata metadata = audioProcessor.extractMetadata(audioData);
            
            // Process audio
            byte[] processedData = audioProcessor.processAudio(audioData);

            // Store audio
            String audioId = storageManager.storeAudio(processedData, metadata, player.getName().getString());
            
            // Attach to disc
            storageManager.attachToDisc(disc, audioId);

            // Success message
            player.sendMessage(Text.literal("§a✅ YouTube аудио успешно добавлено на диск!"), false);
            player.sendMessage(Text.literal("§7ID: " + audioId), false);
            
            String title = extractVideoTitle(youtubeUrl, videoId);
            player.sendMessage(Text.literal("§7Название: " + title), false);
            
            if (metadata.duration() > 0) {
                long durationSeconds = metadata.duration() / 1000;
                long minutes = durationSeconds / 60;
                long seconds = durationSeconds % 60;
                player.sendMessage(Text.literal(String.format("§7Длительность: %d:%02d", minutes, seconds)), false);
            }

            // Record successful upload for rate limiting
            org.stepan.audio_disc.util.RateLimiter rateLimiter = Audio_disc.getRateLimiter();
            if (rateLimiter != null) {
                rateLimiter.recordUpload(player.getUuid());
            }

            // Fire API event
            AudioDiscAPIImpl.getInstance().fireAudioUploadEvent(new AudioUploadEvent(
                player, disc, audioId, metadata, System.currentTimeMillis()
            ));

            // Clean up
            mp3File.delete();
            LOGGER.info("Cleaned up temporary file: {}", mp3File.getName());

        } catch (Exception e) {
            LOGGER.error("YouTube download and conversion failed", e);
            
            if (e.getMessage() != null) {
                if (e.getMessage().contains("Cannot run program") || 
                    e.getMessage().contains("CreateProcess error=2") ||
                    e.getMessage().contains("No such file or directory")) {
                    
                    player.sendMessage(Text.literal("§c❌ yt-dlp не может быть запущен!"), false);
                    player.sendMessage(Text.literal("§7Попробуйте: §f/audiodisc ytdlp install"), false);
                    
                } else {
                    player.sendMessage(Text.literal("§cОшибка загрузки с YouTube: " + e.getMessage()), false);
                }
            } else {
                player.sendMessage(Text.literal("§cНеизвестная ошибка при загрузке с YouTube"), false);
            }
        }
    }

    /**
     * Extracts video ID from YouTube URL.
     */
    private static String extractVideoId(String url) {
        try {
            if (url.contains("youtu.be/")) {
                String id = url.substring(url.lastIndexOf("/") + 1);
                // Remove query parameters and fragments
                id = id.split("\\?")[0].split("#")[0];
                return id.isEmpty() ? "unknown" : id;
            } else if (url.contains("watch?v=") || url.contains("watch&v=")) {
                String id = url.substring(url.indexOf("v=") + 2);
                // Remove other parameters
                id = id.split("&")[0].split("#")[0];
                return id.isEmpty() ? "unknown" : id;
            } else if (url.contains("/embed/")) {
                String id = url.substring(url.indexOf("/embed/") + 7);
                id = id.split("\\?")[0].split("#")[0];
                return id.isEmpty() ? "unknown" : id;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to extract video ID from URL: {}", url, e);
        }
        
        // Fallback: use timestamp
        return "video_" + System.currentTimeMillis();
    }

    /**
     * Validates held item.
     */
    private static boolean validateHeldItem(ServerPlayerEntity player) {
        return isMusicDisc(player.getMainHandStack());
    }

    /**
     * Checks if item is a music disc.
     */
    private static boolean isMusicDisc(ItemStack stack) {
        if (stack.isEmpty()) return false;
        
        return stack.getItem() == Items.MUSIC_DISC_13 ||
               stack.getItem() == Items.MUSIC_DISC_CAT ||
               stack.getItem() == Items.MUSIC_DISC_BLOCKS ||
               stack.getItem() == Items.MUSIC_DISC_CHIRP ||
               stack.getItem() == Items.MUSIC_DISC_FAR ||
               stack.getItem() == Items.MUSIC_DISC_MALL ||
               stack.getItem() == Items.MUSIC_DISC_MELLOHI ||
               stack.getItem() == Items.MUSIC_DISC_STAL ||

               stack.getItem() == Items.MUSIC_DISC_WARD ||
               stack.getItem() == Items.MUSIC_DISC_11 ||
               stack.getItem() == Items.MUSIC_DISC_WAIT ||
               stack.getItem() == Items.MUSIC_DISC_PIGSTEP ||
               stack.getItem() == Items.MUSIC_DISC_OTHERSIDE ||
               stack.getItem() == Items.MUSIC_DISC_5 ||
               stack.getItem() == Items.MUSIC_DISC_RELIC ||
               stack.getItem() == Items.MUSIC_DISC_CREATOR ||
               stack.getItem() == Items.MUSIC_DISC_CREATOR_MUSIC_BOX ||
               stack.getItem() == Items.MUSIC_DISC_PRECIPICE;
    }

    /**
     * Executes yt-dlp install command.
     */
    private static int executeYtDlpInstall(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (org.stepan.audio_disc.download.YtDlpManager.isAvailable()) {
            source.sendFeedback(() -> Text.literal("§ayt-dlp уже установлен и доступен"), false);
            return 1;
        }
        
        source.sendFeedback(() -> Text.literal("§eНачинается загрузка yt-dlp..."), false);
        
        org.stepan.audio_disc.download.YtDlpManager.downloadAndInstall().thenAccept(success -> {
            if (success) {
                source.sendFeedback(() -> Text.literal("§a✅ yt-dlp успешно установлен!"), true);
            } else {
                source.sendFeedback(() -> Text.literal("§c❌ Не удалось установить yt-dlp"), true);
            }
        });
        
        return 1;
    }
    
    /**
     * Executes yt-dlp uninstall command.
     */
    private static int executeYtDlpUninstall(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        boolean removed = org.stepan.audio_disc.download.YtDlpManager.uninstall();
        if (removed) {
            source.sendFeedback(() -> Text.literal("§ayt-dlp удален из мода"), true);
        } else {
            source.sendFeedback(() -> Text.literal("§7Нет установленного yt-dlp для удаления"), false);
        }
        
        return 1;
    }
    
    /**
     * Executes yt-dlp status command.
     */
    private static int executeYtDlpStatus(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        source.sendFeedback(() -> Text.literal("§6=== yt-dlp Status ==="), false);
        
        if (org.stepan.audio_disc.download.YtDlpManager.isAvailable()) {
            String executablePath = org.stepan.audio_disc.download.YtDlpManager.getExecutablePath();
            source.sendFeedback(() -> Text.literal("§a✅ yt-dlp доступен"), false);
            source.sendFeedback(() -> Text.literal("§7Путь: " + executablePath), false);
            
            long size = org.stepan.audio_disc.download.YtDlpManager.getInstallationSize();
            if (size > 0) {
                source.sendFeedback(() -> Text.literal("§7Размер: " + formatFileSize(size)), false);
            }
        } else {
            source.sendFeedback(() -> Text.literal("§c❌ yt-dlp недоступен"), false);
            source.sendFeedback(() -> Text.literal("§7Используйте §f/audiodisc ytdlp install §7для автоматической установки"), false);
        }
        
        return 1;
    }

    /**
     * Extracts video title from YouTube URL or uses video ID as fallback.
     */
    private static String extractVideoTitle(String youtubeUrl, String videoId) {
        // For now, use video ID as title. In future, could parse yt-dlp output for title
        return "YouTube Audio " + videoId;
    }
    
    /**
     * Gets file extension from filename.
     */
    private static String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1).toLowerCase();
        }
        return "unknown";
    }

    /**
     * Formats file size.
     */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * Formats duration in milliseconds to MM:SS format.
     */
    private static String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * Simple progress tracker for upload operations.
     */
    private static class ProgressTracker {
        private final ServerPlayerEntity player;
        private String currentStage = "downloading";
        private int lastReportedProgress = -1;

        public ProgressTracker(ServerPlayerEntity player) {
            this.player = player;
        }

        public void setStage(String stage) {
            this.currentStage = stage;
        }

        public void updateProgress(int progress) {
            // Only report progress every 10%
            int roundedProgress = (progress / 10) * 10;
            if (roundedProgress != lastReportedProgress && roundedProgress >= 0 && roundedProgress <= 100) {
                lastReportedProgress = roundedProgress;
                
                String key = "progress." + currentStage;
                player.sendMessage(Text.literal(Localization.formatForPlayer(key, player, roundedProgress)), false);
            }
        }
    }

    /**
     * Tries to convert M4A file to OGG using FFmpeg.
     * 
     * @param m4aFile The M4A file to convert
     * @param tempDir The temporary directory for output
     * @return The converted OGG file, or null if conversion failed
     */
    private static java.io.File tryConvertM4AToOgg(java.io.File m4aFile, Path tempDir) {
        try {
            // Check if FFmpeg is available
            if (!isFFmpegAvailable()) {
                LOGGER.info("FFmpeg not available for M4A conversion");
                return null;
            }

            String baseName = m4aFile.getName().replaceAll("\\.[^.]+$", "");
            java.io.File oggFile = tempDir.resolve(baseName + ".mp3").toFile();

            LOGGER.info("Converting M4A to MP3 using FFmpeg: {} -> {}", m4aFile.getName(), oggFile.getName());

            // Get FFmpeg executable path
            String ffmpegPath = getFFmpegExecutablePath();
            if (ffmpegPath == null) {
                LOGGER.warn("FFmpeg executable not found");
                return null;
            }

            ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-i", m4aFile.getAbsolutePath(),
                "-c:a", "libmp3lame", // Use MP3 codec instead of Vorbis
                "-b:a", "128k",       // 128kbps bitrate
                "-y",                 // Overwrite output file
                oggFile.getAbsolutePath()
            );

            pb.directory(tempDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Read output for debugging
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.debug("FFmpeg: {}", line);
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0 && oggFile.exists() && oggFile.length() > 0) {
                LOGGER.info("Successfully converted M4A to MP3: {} bytes", oggFile.length());
                return oggFile;
            } else {
                LOGGER.warn("FFmpeg conversion failed with exit code: {}", exitCode);
                return null;
            }

        } catch (Exception e) {
            LOGGER.warn("Failed to convert M4A to MP3: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Validates if URL is valid for direct upload.
     */
    private static boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        try {
            java.net.URL urlObj = new java.net.URL(url);
            String protocol = urlObj.getProtocol().toLowerCase();
            
            // Only allow HTTP and HTTPS
            if (!protocol.equals("http") && !protocol.equals("https")) {
                return false;
            }
            
            // Block local addresses for security
            String host = urlObj.getHost().toLowerCase();
            if (host.equals("localhost") || host.equals("127.0.0.1") || 
                host.startsWith("192.168.") || host.startsWith("10.") || 
                host.startsWith("172.16.") || host.startsWith("172.17.") ||
                host.startsWith("172.18.") || host.startsWith("172.19.") ||
                host.startsWith("172.20.") || host.startsWith("172.21.") ||
                host.startsWith("172.22.") || host.startsWith("172.23.") ||
                host.startsWith("172.24.") || host.startsWith("172.25.") ||
                host.startsWith("172.26.") || host.startsWith("172.27.") ||
                host.startsWith("172.28.") || host.startsWith("172.29.") ||
                host.startsWith("172.30.") || host.startsWith("172.31.")) {
                return false;
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validates if URL is a valid YouTube URL.
     */
    private static boolean isValidYouTubeUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        return url.contains("youtube.com/watch") || 
               url.contains("youtu.be/") || 
               url.contains("youtube.com/embed/") ||
               url.contains("m.youtube.com/watch");
    }

    /**
     * Tries to convert WebM file to OGG using FFmpeg.
     * 
     * @param webmFile The WebM file to convert
     * @param tempDir The temporary directory for output
     * @return The converted OGG file, or null if conversion failed
     */
    private static java.io.File tryConvertWebMToOgg(java.io.File webmFile, Path tempDir) {
        try {
            // Check if FFmpeg is available
            if (!isFFmpegAvailable()) {
                LOGGER.info("FFmpeg not available for WebM conversion");
                return null;
            }

            String baseName = webmFile.getName().replaceAll("\\.[^.]+$", "");
            java.io.File oggFile = tempDir.resolve(baseName + ".mp3").toFile();

            LOGGER.info("Converting WebM to MP3 using FFmpeg: {} -> {}", webmFile.getName(), oggFile.getName());

            // Get FFmpeg executable path
            String ffmpegPath = getFFmpegExecutablePath();
            if (ffmpegPath == null) {
                LOGGER.warn("FFmpeg executable not found");
                return null;
            }

            ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-i", webmFile.getAbsolutePath(),
                "-c:a", "libmp3lame", // Use MP3 codec instead of Vorbis
                "-b:a", "128k",       // 128kbps bitrate
                "-y",                 // Overwrite output file
                oggFile.getAbsolutePath()
            );

            pb.directory(tempDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Read output for debugging
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.debug("FFmpeg: {}", line);
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0 && oggFile.exists() && oggFile.length() > 0) {
                LOGGER.info("Successfully converted WebM to MP3: {} bytes", oggFile.length());
                return oggFile;
            } else {
                LOGGER.warn("FFmpeg WebM conversion failed with exit code: {}", exitCode);
                return null;
            }

        } catch (Exception e) {
            LOGGER.warn("Failed to convert WebM to MP3: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Checks if FFmpeg is available on the system.
     */
    private static boolean isFFmpegAvailable() {
        // First check if FFmpegManager has FFmpeg available
        if (org.stepan.audio_disc.download.FFmpegManager.isAvailable()) {
            return true;
        }
        
        // Fallback to system FFmpeg check
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the FFmpeg executable path (either from FFmpegManager or system).
     */
    private static String getFFmpegExecutablePath() {
        // First try FFmpegManager
        if (org.stepan.audio_disc.download.FFmpegManager.isAvailable()) {
            String path = org.stepan.audio_disc.download.FFmpegManager.getExecutablePath();
            if (path != null) {
                return path;
            }
        }
        
        // Fallback to system FFmpeg
        return "ffmpeg";
    }

    /**
     * Waits for FFmpeg to become available (during automatic installation).
     * 
     * @param timeoutSeconds Maximum time to wait in seconds
     * @return true if FFmpeg became available, false if timeout
     */
    private static boolean waitForFFmpeg(int timeoutSeconds) {
        int attempts = 0;
        int maxAttempts = timeoutSeconds * 2; // Check every 500ms
        
        while (attempts < maxAttempts) {
            if (isFFmpegAvailable()) {
                return true;
            }
            
            try {
                Thread.sleep(500); // Wait 500ms between checks
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            
            attempts++;
        }
        
        return false;
    }
}