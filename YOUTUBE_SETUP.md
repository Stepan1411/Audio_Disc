# YouTube Support Setup

## 🚀 Автоматическая установка (Рекомендуется)

Мод теперь **автоматически скачивает и устанавливает yt-dlp**! Просто используйте команду YouTube, и мод сам все настроит.

### Команды для YouTube

- `/audiodisc youtube <url>` - загрузить аудио с YouTube (автоматически установит yt-dlp если нужно)
- `/audiodisc ytdlp status` - проверить статус yt-dlp
- `/audiodisc ytdlp install` - принудительно установить yt-dlp (админ)
- `/audiodisc ytdlp uninstall` - удалить yt-dlp из мода (админ)

### Другие команды

- `/audiodisc upload <url>` - загрузить аудио с прямой ссылки
- `/audiodisc info` - информация о диске
- `/audiodisc clear` - очистить диск (админ)
- `/audiodisc reload` - перезагрузить конфиг (админ)

## Поддерживаемые форматы

✅ **MP3** - полная поддержка  
✅ **WAV** - встроенная поддержка Java  
❌ **OGG** - временно отключена

## 🔧 Ручная установка yt-dlp (Опционально)

Если автоматическая установка не работает, можно установить yt-dlp вручную:

### Windows
```bash
pip install yt-dlp
```

### Linux
```bash
# Ubuntu/Debian
sudo apt update && sudo apt install python3-pip
pip3 install yt-dlp

# Arch Linux
sudo pacman -S yt-dlp
```

### macOS
```bash
# Через Homebrew
brew install yt-dlp

# Или через pip
pip3 install yt-dlp
```

### Проверка установки
```bash
yt-dlp --version
```

## 🤖 Как работает автоматическая установка

1. При первом использовании `/audiodisc youtube <url>` мод проверяет наличие yt-dlp
2. Если yt-dlp не найден, мод автоматически скачивает его с GitHub
3. Файл сохраняется в папку `minecraft/audiodisc/yt-dlp/`
4. Мод автоматически делает файл исполняемым (Linux/macOS)
5. YouTube команды становятся доступными!

## Поддерживаемые YouTube ссылки

- `https://www.youtube.com/watch?v=VIDEO_ID`
- `https://youtu.be/VIDEO_ID`
- `https://m.youtube.com/watch?v=VIDEO_ID`

## Ограничения

- Максимальный размер файла: 50MB
- Качество аудио: 128kbps MP3
- Максимальная длительность: зависит от размера файла

## Примеры использования

```
/audiodisc youtube https://www.youtube.com/watch?v=dQw4w9WgXcQ
/audiodisc youtube https://youtu.be/dQw4w9WgXcQ
```

## 📱 Что происходит при первом использовании

При первом использовании `/audiodisc youtube <url>` вы увидите:

```
⚠ yt-dlp не найден. Начинаю автоматическую загрузку...
Это может занять несколько секунд...
✅ yt-dlp успешно установлен!
Теперь можно использовать YouTube команды
Начинается загрузка аудио с YouTube...
```

## 🛠 Управление yt-dlp

- `/audiodisc ytdlp status` - проверить статус установки
- `/audiodisc ytdlp install` - принудительно переустановить (админ)
- `/audiodisc ytdlp uninstall` - удалить из мода (админ)

## Возможные ошибки

### "Video unavailable"
- Видео удалено или приватное
- Неверная ссылка
- **Решение**: Проверьте ссылку и доступность видео

### "HTTP Error 403" или "Sign in to confirm"
- Видео требует авторизации
- Ограничения по региону
- **Решение**: Используйте другое видео или прямую ссылку

### "Cannot run program yt-dlp"
- yt-dlp не установлен
- **Решение**: Установите yt-dlp по инструкции выше

## Альтернативы YouTube

Если yt-dlp недоступен, используйте прямые ссылки на аудио файлы:
- SoundCloud (прямые ссылки)
- Dropbox/Google Drive (прямые ссылки)
- Собственный веб-сервер
- Любой HTTP/HTTPS сервер с MP3/WAV файлами