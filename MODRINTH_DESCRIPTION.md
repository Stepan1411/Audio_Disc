# 🎵 Custom Audio Disc

Transform your Minecraft music experience with custom audio discs! Upload any audio file from the internet directly to music discs and enjoy spatial audio playback through Simple Voice Chat.

## ✨ Features

### 🎧 **Easy Audio Upload**
- Upload audio from any URL with simple commands
- Support for **MP3**, **OGG**, **WAV**, **M4A**, **WebM**, and more
- Automatic format conversion using FFmpeg when needed
- Real-time progress tracking during downloads

### 🔊 **Immersive Playback**
- **Spatial audio** through Simple Voice Chat integration
- Perfect synchronization across all players
- Standard jukebox range (64 blocks)
- Works alongside vanilla music discs

### 🛠️ **Smart Processing**
- Automatic format detection and conversion
- File size validation (up to 50MB)
- Comprehensive error handling
- Persistent storage across server restarts

### 🎮 **Player-Friendly**
- **Multilingual support** (English, Russian, German, French, Spanish, Chinese)
- Rich tooltips showing audio metadata
- Custom disc renaming
- Progress feedback during uploads

### 🔧 **Developer API**
- Event system for addon developers
- Audio modification hooks
- Playback state monitoring
- Comprehensive documentation

## 📋 Commands

| Command | Description |
|---------|-------------|
| `/audiodisc upload <url>` | Upload audio from URL to held disc |
| `/audiodisc youtube <url>` | Download audio from YouTube (requires yt-dlp) |
| `/audiodisc rename <title>` | Rename your custom disc |
| `/audiodisc info` | Show audio information |
| `/audiodisc clear` | Remove audio from disc |

## 🚀 Quick Start

1. **Hold a music disc** in your hand
2. **Run the command**: `/audiodisc upload <audio_url>`
3. **Wait for processing** (progress shown in chat)
4. **Place in jukebox** and enjoy!

## 📦 Requirements

- **Minecraft 1.21+** (Fabric)
- **Fabric API**
- **Simple Voice Chat** (for audio playback)
- **yt-dlp** (optional, for YouTube support)
- **FFmpeg** (optional, for format conversion)

## 🎯 Perfect For

- **Music servers** wanting custom soundtracks
- **Creative builders** adding ambiance to builds  
- **Event organizers** playing custom audio
- **Roleplay servers** with immersive sound design

## 🔧 Technical Features

- **Asynchronous processing** - no server lag
- **Memory efficient** - smart caching system
- **Network optimized** - connection pooling and timeouts
- **Security focused** - URL validation and rate limiting
- **Mixin-free design** - maximum compatibility

## 🌍 Supported Formats

### ✅ Native Support
- **MP3** - Most common format
- **OGG** - Minecraft's preferred format  
- **WAV** - Uncompressed audio

### 🔄 Auto-Conversion (with FFmpeg)
- **M4A** - Apple audio format
- **WebM** - Web audio format
- **AAC** - Advanced audio codec
- **FLAC** - Lossless compression

## 🛡️ Safety & Security

- **URL validation** - only HTTP/HTTPS allowed
- **File size limits** - configurable maximum size
- **Rate limiting** - prevents spam uploads
- **Content validation** - ensures audio files only
- **Permission system** - admin control over features

## 📖 Documentation

Comprehensive documentation available including:
- Installation guide
- Configuration options
- API documentation for developers
- Troubleshooting guide
- Performance optimization tips

---

**Transform your server's audio experience today!** 🎵

*Compatible with most Fabric mods and designed for maximum server performance.*

---

# 🎵 Custom Audio Disc (Русский)

Преобразите музыкальный опыт в Minecraft с помощью кастомных аудио дисков! Загружайте любые аудиофайлы из интернета прямо на музыкальные диски и наслаждайтесь пространственным звуком через Simple Voice Chat.

## ✨ Возможности

### 🎧 **Простая загрузка аудио**
- Загрузка аудио с любого URL простыми командами
- Поддержка **MP3**, **OGG**, **WAV**, **M4A**, **WebM** и других форматов
- Автоматическая конвертация форматов с помощью FFmpeg
- Отслеживание прогресса загрузки в реальном времени

### 🔊 **Погружающее воспроизведение**
- **Пространственный звук** через интеграцию с Simple Voice Chat
- Идеальная синхронизация между всеми игроками
- Стандартная дальность проигрывателя (64 блока)
- Работает вместе с ванильными музыкальными дисками

### 🛠️ **Умная обработка**
- Автоматическое определение и конвертация форматов
- Проверка размера файлов (до 50МБ)
- Комплексная обработка ошибок
- Постоянное хранение после перезапуска сервера

### 🎮 **Удобство для игроков**
- **Многоязычная поддержка** (английский, русский, немецкий, французский, испанский, китайский)
- Богатые подсказки с метаданными аудио
- Переименование кастомных дисков
- Обратная связь о прогрессе загрузки

### 🔧 **API для разработчиков**
- Система событий для разработчиков дополнений
- Хуки для модификации аудио
- Мониторинг состояния воспроизведения
- Подробная документация

## 📋 Команды

| Команда | Описание |
|---------|----------|
| `/audiodisc upload <url>` | Загрузить аудио с URL на диск в руке |
| `/audiodisc youtube <url>` | Скачать аудио с YouTube (требует yt-dlp) |
| `/audiodisc rename <название>` | Переименовать кастомный диск |
| `/audiodisc info` | Показать информацию об аудио |
| `/audiodisc clear` | Удалить аудио с диска |

## 🚀 Быстрый старт

1. **Возьмите музыкальный диск** в руку
2. **Выполните команду**: `/audiodisc upload <ссылка_на_аудио>`
3. **Дождитесь обработки** (прогресс показывается в чате)
4. **Поставьте в проигрыватель** и наслаждайтесь!

## 📦 Требования

- **Minecraft 1.21+** (Fabric)
- **Fabric API**
- **Simple Voice Chat** (для воспроизведения аудио)
- **yt-dlp** (опционально, для поддержки YouTube)
- **FFmpeg** (опционально, для конвертации форматов)

## 🎯 Идеально подходит для

- **Музыкальных серверов**, желающих кастомные саундтреки
- **Творческих строителей**, добавляющих атмосферу в постройки
- **Организаторов событий**, воспроизводящих кастомное аудио
- **Ролевых серверов** с погружающим звуковым дизайном

## 🔧 Технические особенности

- **Асинхронная обработка** - никаких лагов сервера
- **Эффективная память** - умная система кэширования
- **Оптимизация сети** - пулинг соединений и таймауты
- **Фокус на безопасности** - валидация URL и ограничение скорости
- **Дизайн без миксинов** - максимальная совместимость

## 🌍 Поддерживаемые форматы

### ✅ Нативная поддержка
- **MP3** - Самый распространенный формат
- **OGG** - Предпочитаемый формат Minecraft
- **WAV** - Несжатое аудио

### 🔄 Авто-конвертация (с FFmpeg)
- **M4A** - Аудио формат Apple
- **WebM** - Веб аудио формат
- **AAC** - Продвинутый аудио кодек
- **FLAC** - Сжатие без потерь

## 🛡️ Безопасность и защита

- **Валидация URL** - разрешены только HTTP/HTTPS
- **Ограничения размера файлов** - настраиваемый максимальный размер
- **Ограничение скорости** - предотвращает спам загрузки
- **Валидация контента** - только аудиофайлы
- **Система разрешений** - административный контроль функций

## 📖 Документация

Доступна подробная документация, включающая:
- Руководство по установке
- Опции конфигурации
- API документация для разработчиков
- Руководство по устранению неполадок
- Советы по оптимизации производительности

---

**Преобразите аудио опыт вашего сервера уже сегодня!** 🎵

*Совместим с большинством Fabric модов и разработан для максимальной производительности сервера.*