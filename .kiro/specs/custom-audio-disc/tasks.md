# Implementation Plan

- [x] 1. Set up project dependencies and configuration





  - Add Simple Voice Chat API dependency to build.gradle
  - Create configuration file structure for audiodisc.json
  - Set up logging infrastructure with SLF4J
  - _Requirements: 1.1, 4.4_
- [ ] 2. Implement core data models and storage foundation




- [ ] 2. Implement core data models and storage foundation

  - _Requirements: 6.1, 6.3, 6.4_

- [x] 2.1 Create audio metadata and data models



  - Implement AudioMetadata record with format, duration, bitrate, sampleRate, title fields
  - Implement AudioData class with id, data, metadata, uploadedBy, uploadTime fields
  - Create DownloadStatus enum with all status states
  - Create ValidationResult record for format validation results
  - _Requirements: 6.1, 6.3_

- [x] 2.2 Implement NBT serialization utilities



  - Create NbtUtils class for serializing/deserializing AudioMetadata to NBT
  - Implement methods to attach audio ID to ItemStack NBT data
  - Implement methods to retrieve audio ID from ItemStack NBT data
  - Add validation for NBT data integrity
  - _Requirements: 6.3, 6.4_

- [x] 2.3 Implement AudioStorageManager for persistent storage



  - Create storage directory structure (audio_disc/audio/)
  - Implement storeAudio method to save audio files with UUID naming
  - Implement getAudio method to retrieve audio data by ID
  - Implement attachToDisc and getDiscAudioId methods using NBT utilities
  - Create metadata.json management for audio file index
  - Implement in-memory LRU cache for frequently accessed audio
  - Add cleanup method for removing unused audio files
  - _Requirements: 6.1, 6.2, 6.5_

- [ ] 2.4 Write unit tests for storage components


  - Test NBT serialization and deserialization
  - Test file storage and retrieval operations
  - Test cache behavior and cleanup
  - _Requirements: 6.1, 6.2, 6.3, 6.4_


- [ ] 3. Implement audio download and processing system
  - _Requirements: 1.1, 1.2, 4.1, 4.2, 4.5, 5.1, 5.2, 5.3, 5.4_

- [x] 3.1 Create AudioDownloadManager with async download support



  - Set up ExecutorService for async downloads
  - Implement downloadAudio method returning CompletableFuture
  - Add URL validation (HTTP/HTTPS only, block local/internal IPs)
  - Implement 30-second timeout enforcement
  - Add file size validation during download (max 50MB)
  - Create DownloadTask class for tracking individual downloads
  - Implement progress tracking with percentage updates
  - Add cancelDownload functionality
  - _Requirements: 1.1, 4.1, 4.2, 4.5, 5.1, 5.2, 5.3, 5.4_


- [x] 3.1.1 Fix content type validation to accept application/binary



  - Update DownloadTask content type validation to accept application/binary
  - Ensure validation accepts: audio/*, application/octet-stream, application/ogg, application/binary
  - Test with Dropbox and other file hosting services
  - _Requirements: 4.6_

- [x] 3.2 Implement AudioProcessor for format validation and processing





  - Create format detection logic for MP3, OGG, WAV
  - Implement validateFormat method with magic number checking
  - Add extractMetadata method to read audio file properties
  - Implement file size validation (max 50MB)
  - Add error handling for corrupted files
  - _Requirements: 1.2, 4.3_

- [ ] 3.4 Implement automatic FFmpeg installation and management
  - Create FFmpegManager class for automatic FFmpeg installation
  - Implement platform detection (Windows, Linux, macOS)
  - Add automatic download and extraction of FFmpeg binaries
  - Implement FFmpeg executable verification and version checking
  - Add automatic format conversion for unsupported formats (M4A, WebM to OGG)
  - Create fallback mechanism when FFmpeg is unavailable
  - Add configuration option to disable automatic installation
  - Implement cleanup of temporary FFmpeg files
  - _Requirements: 1.2, 4.3_

- [ ] 3.3 Write unit tests for download and processing



  - Test URL validation logic
  - Test timeout handling
  - Test format detection
  - Test file size limits
  - _Requirements: 1.1, 1.2, 4.1, 4.2, 4.3, 4.5_

- [ ] 4. Implement command system

  - _Requirements: 1.1, 1.3, 1.4, 1.5, 5.5_




- [x] 4.1 Create AudioDiscCommand class


  - Register /audiodisc upload <url> command using Fabric Command API
  - Implement command permission checking
  - Add validateHeldItem method to check for music disc in hand
  - Implement executeUpload method to initiate download process
  - Add user feedback messages for validation failures
  - _Requirements: 1.3, 1.4_

- [x] 4.2 Integrate command with download and storage systems



  - Connect command execution to AudioDownloadManager
  - Display download progress messages to player (every 25%)
  - Handle download completion and attach audio to disc
  - Display success message with file size and duration
  - Implement comprehensive error handling with user-friendly messages
  - _Requirements: 1.1, 1.4, 1.5, 5.1, 5.2, 5.3, 5.5_


- [ ] 4.3 Write unit tests for command system

  - Test command registration
  - Test permission validation
  - Test held item validation
  - Test error message formatting
  - _Requirements: 1.3, 1.4_

- [ ] 5. Implement Simple Voice Chat integration

  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_






- [x] 5.1 Create SimpleVoiceChatIntegration class


  - Initialize VoicechatServerApi during mod initialization
  - Implement createStream method for locational audio at jukebox position



  - Add updateStreamPosition for spatial audio management
  - Implement stopStream method with cleanup
  - Handle case when Simple Voice Chat is not installed
  - _Requirements: 2.1, 2.2_




- [ ] 5.2 Implement PlaybackManager for jukebox audio
  - Create ActivePlayback class to track playing audio

  - Implement startPlayback method triggered by disc insertion
  - Add stopPlayback method for disc removal or completion
  - Maintain map of active playbacks by BlockPos
  - Implement playback synchronization across players in range
  - Handle natural playback completion (eject disc)


  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [ ] 5.3 Create JukeboxBlockEntityMixin

  - Mixin to intercept disc insertion in jukebox
  - Mixin to intercept disc removal from jukebox
  - Trigger PlaybackManager.startPlayback on custom disc insertion
  - Trigger PlaybackManager.stopPlayback on disc removal
  - Check for custom audio using AudioStorageManager.getDiscAudioId
  - _Requirements: 2.1, 2.4_

- [ ] 5.4 Write integration tests for playback system


  - Test playback start and stop
  - Test multiple simultaneous playbacks
  - Test playback without Simple Voice Chat installed
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_





- [ ] 6. Implement public API for addon developers
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_


- [ ] 6.1 Create API interfaces and event classes
  - Define AudioDiscAPI interface with registerListener, unregisterListener, getAudioMetadata, hasCustomAudio methods
  - Create AudioEventListener interface with onPlaybackStart, onPlaybackStop, onAudioUpload, modifyAudio methods
  - Implement PlaybackStartEvent record with jukeboxPos, world, audioId, metadata, timestamp


  - Implement PlaybackStopEvent record with jukeboxPos, world, audioId, playbackDuration, reason
  - Implement AudioUploadEvent record with player, disc, audioId, metadata, timestamp
  - Create StopReason enum with DISC_REMOVED, PLAYBACK_COMPLETE, JUKEBOX_BROKEN, MANUAL_STOP
  - Create AudioModification class for audio stream modifications
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [ ] 6.2 Implement AudioDiscAPI implementation class



  - Create AudioDiscAPIImpl singleton
  - Implement listener registration with thread-safe collection
  - Implement listener unregistration
  - Add getAudioMetadata method delegating to AudioStorageManager
  - Implement hasCustomAudio method checking NBT data
  - _Requirements: 3.1, 3.4_


- [ ] 6.3 Integrate API events into playback and upload flows
  - Fire PlaybackStartEvent when PlaybackManager starts playback
  - Fire PlaybackStopEvent when PlaybackManager stops playback
  - Fire AudioUploadEvent when command completes upload
  - Call modifyAudio on listeners before starting playback
  - Handle exceptions in listener callbacks gracefully
  - _Requirements: 3.2, 3.3, 3.5_





- [ ] 6.4 Create API documentation and example addon






  - Write JavaDoc for all public API interfaces
  - Create example addon demonstrating event listening
  - Document audio modification capabilities
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [ ] 7. Implement error handling and user feedback

  - _Requirements: 4.1, 4.2, 4.3, 4.4_


- [ ] 7.1 Create AudioDiscException hierarchy
  - Define AudioDiscException with ErrorType enum
  - Add error types: INVALID_URL, DOWNLOAD_FAILED, UNSUPPORTED_FORMAT, FILE_TOO_LARGE, NO_DISC_IN_HAND, STORAGE_ERROR, PLAYBACK_ERROR
  - Implement user-friendly error message generation
  - Add technical error logging to server console
  - _Requirements: 4.1, 4.2, 4.3, 4.4_


- [x] 7.2 Add comprehensive error handling throughout system


  - Wrap download operations with try-catch and proper error messages
  - Handle network timeouts with descriptive messages
  - Display supported formats list on format validation failure
  - Add error recovery where possible (retry logic)
  - Ensure all errors are logged with appropriate severity levels
  - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [ ] 8. Implement configuration system

  - _Requirements: 4.2, 4.5_


- [ ] 8.1 Create configuration file and loader
  - Define AudioDiscConfig class with all configurable options
  - Implement config file loading from config/audiodisc.json
  - Add default values: maxFileSize=50MB, downloadTimeout=30s, supportedFormats=[mp3,ogg,wav]
  - Create config validation on load
  - Implement config reload command for administrators
  - _Requirements: 4.2, 4.5_




- [ ] 8.2 Integrate configuration throughout system
  - Use config values in AudioDownloadManager for timeout and size limits
  - Use config values in AudioProcessor for supported formats
  - Use config values for progress update intervals


  - Make storage directory configurable
  - _Requirements: 4.2, 4.5_


- [ ] 9. Add security and rate limiting
  - _Requirements: 1.1, 4.1, 4.2_


- [ ] 9.1 Implement URL security validation
  - Block file:// protocol URLs
  - Block local IP ranges (127.0.0.0/8, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)
  - Block link-local addresses (169.254.0.0/16)
  - Validate content-type headers match audio formats
  - Add URL whitelist/blacklist configuration option
  - _Requirements: 1.1, 4.1_

- [ ] 9.2 Implement rate limiting for uploads

  - Create RateLimiter class tracking uploads per player
  - Enforce max 3 uploads per player per minute
  - Add 10-second cooldown between uploads
  - Limit server-wide concurrent downloads to 5
  - Display cooldown time remaining in error messages
  - _Requirements: 1.1, 4.2_


- [ ] 10. Add administrative commands and utilities
  - _Requirements: 6.5_

- [x] 10.1 Implement admin commands



  - Create /audiodisc clear <audioId> command to remove stored audio
  - Create /audiodisc list command to show all stored audio files
  - Create /audiodisc info <audioId> command to display audio metadata
  - Create /audiodisc reload command to reload configuration
  - Add permission checks for admin commands
  - _Requirements: 6.5_

- [ ] 10.2 Implement cleanup and maintenance utilities

  - Create scheduled task to clean up unused audio files (weekly)
  - Implement orphaned file detection (audio files not referenced by any disc)
  - Add storage usage reporting in /audiodisc list
  - Create backup/export functionality for audio data
  - _Requirements: 6.5_

- [ ] 11. Add optional enhancements

  - _Requirements: 1.5, 2.3_

- [ ] 11.1 Implement custom music disc tooltips


  - Create ItemStackMixin to add custom tooltip rendering
  - Display audio title, duration, and uploader in tooltip
  - Add visual indicator for custom audio discs
  - Format duration as MM:SS
  - _Requirements: 1.5_


- [ ] 11.2 Add playback synchronization improvements



  - Implement tick-based synchronization for late-joining players
  - Add seek functionality for resuming playback
  - Handle chunk loading/unloading for jukebox positions
  - _Requirements: 2.3_


- [ ] 12. Final integration and mod initialization
  - _Requirements: All_

- [x] 12.1 Wire all components in main mod class



  - Initialize AudioStorageManager in onInitialize
  - Initialize AudioDownloadManager with thread pool
  - Initialize PlaybackManager
  - Initialize SimpleVoiceChatIntegration
  - Register AudioDiscCommand
  - Initialize AudioDiscAPIImpl and expose to addons
  - Load configuration file
  - Set up shutdown hooks for cleanup
  - _Requirements: All_

- [ ] 12.2 Update fabric.mod.json with dependencies




  - Add Simple Voice Chat as optional dependency
  - Update mod description and metadata
  - Add entrypoint for API initialization
  - Configure mixin files
  - _Requirements: All_

- [ ] 12.3 Create comprehensive integration tests




  - Test complete upload flow from command to storage
  - Test complete playback flow from jukebox to SVC
  - Test API event firing in real scenarios
  - Test server restart persistence
  - Test error scenarios end-to-end
  - _Requirements: All_
