# Audio Disc Mod

A server-side Minecraft Fabric mod that allows players to upload custom audio files to music discs and play them through Simple Voice Chat integration.

## Features

- **Custom Audio Upload**: Upload audio files from URLs directly to music discs using commands
- **Simple Voice Chat Integration**: Play custom audio through spatial audio in jukeboxes
- **Multiple Format Support**: Supports MP3, OGG, and WAV audio formats
- **Persistent Storage**: Audio data persists across server restarts
- **Rate Limiting**: Built-in rate limiting to prevent abuse
- **Admin Commands**: Comprehensive admin tools for managing audio files
- **Developer API**: Public API for addon developers to extend functionality

## Requirements

- Minecraft 1.21.8
- Fabric Loader 0.17.3+
- Fabric API 0.136.0+
- Java 21+
- Simple Voice Chat 2.5.0+ (optional, for audio playback)

## Installation

1. Download the mod JAR file
2. Place it in your server's `mods` folder
3. Install Simple Voice Chat mod (optional but recommended)
4. Start the server
5. Configuration file will be created at `config/audiodisc.json`

## Usage

### For Players

#### Upload Audio to a Disc

1. Hold a music disc in your hand
2. Run the command: `/audiodisc upload <url>`
3. Wait for the download and processing to complete
4. Place the disc in a jukebox to play your custom audio!

**Example:**
```
/audiodisc upload https://example.com/myaudio.mp3
```

### For Administrators

#### List All Audio Files
```
/audiodisc list
```
Shows all stored audio files with their IDs and basic information.

#### View Audio Information
```
/audiodisc info <audioId>
```
Displays detailed metadata about a specific audio file.

#### Remove Audio File
```
/audiodisc clear <audioId>
```
Removes a stored audio file from the server.

#### Reload Configuration
```
/audiodisc reload
```
Reloads the configuration file without restarting the server.

## Configuration

The configuration file is located at `config/audiodisc.json`:

```json
{
  "maxFileSize": 52428800,
  "downloadTimeout": 30,
  "supportedFormats": ["mp3", "ogg", "wav"],
  "enableProgressUpdates": true,
  "progressUpdateInterval": 25,
  "storageDirectory": "audio_disc/audio",
  "enableApiEvents": true
}
```

### Configuration Options

- **maxFileSize**: Maximum file size in bytes (default: 50MB)
- **downloadTimeout**: Download timeout in seconds (default: 30)
- **supportedFormats**: List of supported audio formats
- **enableProgressUpdates**: Show download progress to players
- **progressUpdateInterval**: Progress update interval in percentage
- **storageDirectory**: Directory for storing audio files
- **enableApiEvents**: Enable API events for addons

## Permissions

- `/audiodisc upload` - Requires permission level 2 (default for operators)
- `/audiodisc list` - Requires permission level 2
- `/audiodisc info` - Requires permission level 2
- `/audiodisc clear` - Requires permission level 3
- `/audiodisc reload` - Requires permission level 3

## Rate Limiting

To prevent abuse, the mod includes built-in rate limiting:
- Maximum 3 uploads per player per minute
- 10-second cooldown between uploads
- Server-wide limit of 5 concurrent downloads

## Security Features

- Only HTTP and HTTPS protocols are allowed
- Local and private IP ranges are blocked
- File size limits enforced during download
- Content-type validation
- URL whitelist/blacklist support (configurable)

## Developer API

The mod provides a public API for addon developers:

```java
import org.stepan.audio_disc.Audio_disc;
import org.stepan.audio_disc.api.*;

// Get the API instance
AudioDiscAPI api = Audio_disc.getAPI();

// Register an event listener
api.registerListener(new AudioEventListener() {
    @Override
    public void onPlaybackStart(PlaybackStartEvent event) {
        // Handle playback start
    }
    
    @Override
    public void onPlaybackStop(PlaybackStopEvent event) {
        // Handle playback stop
    }
    
    @Override
    public void onAudioUpload(AudioUploadEvent event) {
        // Handle audio upload
    }
    
    @Override
    public AudioModification modifyAudio(AudioModificationContext context) {
        // Modify audio before playback
        return AudioModification.noChange();
    }
});

// Check if a disc has custom audio
boolean hasAudio = api.hasCustomAudio(itemStack);

// Get audio metadata
Optional<AudioMetadata> metadata = api.getAudioMetadata(audioId);
```

### API Events

- **PlaybackStartEvent**: Fired when custom audio starts playing
- **PlaybackStopEvent**: Fired when custom audio stops playing
- **AudioUploadEvent**: Fired when a player uploads audio
- **AudioModification**: Allows modifying audio before playback

## Storage

Audio files are stored in the server directory:
```
server/
└── audio_disc/
    ├── audio/
    │   ├── <uuid>.ogg
    │   ├── <uuid>.mp3
    │   └── ...
    └── metadata.json
```

Audio data is also stored in the music disc's NBT data, allowing discs to be transferred between players while maintaining their custom audio.

## Troubleshooting

### Audio doesn't play
- Ensure Simple Voice Chat is installed on both server and client
- Check that the jukebox is in a loaded chunk
- Verify the audio file was uploaded successfully

### Download fails
- Check that the URL is accessible
- Verify the file size is under the limit (50MB by default)
- Ensure the file format is supported (MP3, OGG, WAV)

### Rate limit errors
- Wait for the cooldown period to expire
- Contact an administrator if you need the limit adjusted

## Building from Source

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`.

## License

All Rights Reserved

## Credits

- Built with Fabric
- Uses Simple Voice Chat API by Max Henkel
- Developed by Stepan

## Support

For issues, questions, or suggestions, please open an issue on the project repository.
