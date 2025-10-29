# ✅ Автоматическая установка yt-dlp при старте сервера

## Что реализовано

### 🚀 Автоматическая установка при старте сервера

Теперь yt-dlp устанавливается **автоматически при запуске сервера**, а не только при первом использовании команды.

### 📋 Ключевые изменения

1. **Обновлен Audio_disc.java**:
   - Добавлен вызов `initializeYtDlp()` в методе `onInitialize()`
   - Улучшено логирование с эмодзи для лучшей читаемости
   - Асинхронная установка не блокирует запуск сервера

2. **Конфигурация готова**:
   - `autoInstallYtDlp: true` по умолчанию в `AudioDiscConfig.java`
   - Можно отключить установку через конфиг

3. **YtDlpManager готов**:
   - Поддерживает асинхронную загрузку
   - Кроссплатформенная установка (Windows/Linux/macOS)
   - Автоматическое определение платформы

### 🎯 Пользовательский опыт

#### При запуске сервера:
```
[AudioDisc] 🔍 Checking yt-dlp availability on server startup...
[AudioDisc] 📥 yt-dlp not found. Starting automatic download...
[AudioDisc] ⏳ This may take a few seconds depending on your internet connection...
[AudioDisc] 🚀 Server startup will continue in the background...
[AudioDisc] 🎉 yt-dlp successfully installed automatically!
[AudioDisc] 🎵 YouTube functionality is now available
```

#### Если yt-dlp уже установлен:
```
[AudioDisc] 🔍 Checking yt-dlp availability on server startup...
[AudioDisc] ✅ yt-dlp is already available at: /path/to/yt-dlp
[AudioDisc] 🎵 YouTube functionality is ready!
```

#### Если автоустановка отключена:
```
[AudioDisc] 🔧 Automatic yt-dlp installation is disabled in config
[AudioDisc] 💡 Use /audiodisc ytdlp install to install manually if needed
[AudioDisc] 📺 YouTube functionality will only work if yt-dlp is manually installed
```

### ⚙️ Конфигурация

В `config/audiodisc/config.json`:
```json
{
  "autoInstallYtDlp": true  // включить/отключить автоустановку
}
```

### 🔄 Резервный механизм

Если автоустановка при старте не сработала, мод попробует еще раз при первом использовании `/audiodisc youtube <url>`.

### 📁 Файлы

- **Основная логика**: `src/main/java/org/stepan/audio_disc/Audio_disc.java`
- **Менеджер yt-dlp**: `src/main/java/org/stepan/audio_disc/download/YtDlpManager.java`
- **Конфигурация**: `src/main/java/org/stepan/audio_disc/config/AudioDiscConfig.java`
- **Документация**: `AUTO_YTDLP.md`

### 🎉 Преимущества

- ✅ **Готовность из коробки** - YouTube работает сразу после установки мода
- ✅ **Неблокирующая установка** - сервер запускается быстро
- ✅ **Автоматизация** - никаких ручных действий от администраторов
- ✅ **Гибкость** - можно отключить в конфиге
- ✅ **Кроссплатформенность** - работает на Windows/Linux/macOS
- ✅ **Безопасность** - загрузка только с официального GitHub

## Тестирование

1. Запустите сервер с модом
2. Проверьте логи на сообщения `[AudioDisc]`
3. Используйте `/audiodisc youtube <url>` для проверки функциональности
4. Проверьте папку `minecraft/audiodisc/yt-dlp/` на наличие исполняемого файла

Теперь пользователям не нужно ничего устанавливать вручную - мод все сделает сам при старте сервера! 🎵