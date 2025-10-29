# 🔧 Исправление YouTube команды

## Проблема

YouTube команда выдавала ошибку:
```
§cОшибка: не удалось загрузить аудио с YouTube
```

## Исправления

### 1. Улучшенное логирование и отладка

- Добавлено детальное логирование процесса yt-dlp
- Захват вывода yt-dlp для диагностики ошибок
- Логирование путей и параметров команды

### 2. Улучшенная обработка ошибок

- Более детальные сообщения об ошибках
- Специфичные сообщения для разных типов ошибок:
  - Требуется авторизация
  - Видео недоступно
  - Приватное видео
  - Проблемы с запуском yt-dlp

### 3. Улучшенный парсинг YouTube URL

- Поддержка различных форматов YouTube URL:
  - `https://www.youtube.com/watch?v=VIDEO_ID`
  - `https://youtu.be/VIDEO_ID`
  - `https://www.youtube.com/embed/VIDEO_ID`
- Правильная обработка параметров и фрагментов URL
- Fallback на timestamp если не удается извлечь ID

### 4. Улучшенная конфигурация yt-dlp

- Добавлен параметр `--no-playlist` для скачивания только одного видео
- Установка рабочей директории для процесса
- Перенаправление потока ошибок для лучшей диагностики

### 5. Улучшенный поиск загруженных файлов

- Поиск файлов с различными расширениями (mp3, m4a, webm)
- Детальное логирование содержимого временной папки
- Лучшая диагностика при отсутствии файлов

## Что добавлено в код

### Детальное логирование:
```java
LOGGER.info("Starting YouTube download with yt-dlp: {}", ytDlpPath);
LOGGER.info("YouTube URL: {}", youtubeUrl);
LOGGER.info("Output file: {}", outputFile);
```

### Захват вывода yt-dlp:
```java
StringBuilder output = new StringBuilder();
try (java.io.BufferedReader reader = new java.io.BufferedReader(
        new java.io.InputStreamReader(process.getInputStream()))) {
    String line;
    while ((line = reader.readLine()) != null) {
        output.append(line).append("\n");
        LOGGER.debug("yt-dlp output: {}", line);
    }
}
```

### Улучшенная обработка ошибок:
```java
if (errorMsg.contains("Sign in to confirm")) {
    player.sendMessage(Text.literal("§cОшибка: видео требует авторизации"), false);
} else if (errorMsg.contains("Video unavailable")) {
    player.sendMessage(Text.literal("§cОшибка: видео недоступно"), false);
} else if (errorMsg.contains("Private video")) {
    player.sendMessage(Text.literal("§cОшибка: приватное видео"), false);
}
```

## Тестирование

Теперь при использовании YouTube команды:

1. **Детальные логи** покажут точную причину ошибки
2. **Понятные сообщения** для игроков о типе проблемы
3. **Лучшая диагностика** для администраторов

### Команды для тестирования:
```
/audiodisc ytdlp status
/audiodisc youtube https://youtu.be/VIDEO_ID
```

## Ожидаемый результат

- Более информативные сообщения об ошибках
- Лучшая диагностика проблем с yt-dlp
- Поддержка различных форматов YouTube URL
- Детальные логи для отладки

Теперь YouTube функциональность должна работать стабильнее и предоставлять лучшую обратную связь! 🎵