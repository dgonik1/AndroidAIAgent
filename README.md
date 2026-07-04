# Android AI Agent

Автономный Android-агент с встроенной локальной LLM (Gemma 2B).

## Возможности

- AI-агент с ReAct-циклом (10 итераций)
- Локальный запуск LLM через MediaPipe (без интернета)
- Инструменты: SMS, звонки, камера, файлы, веб-поиск, WiFi/Bluetooth/яркость

## Сборка через GitHub Actions

1. Создай репозиторий на GitHub
2. Загрузи туда весь проект (кроме `.gitkeep`)
3. Положи файл модели в `app/src/main/assets/ml/gemma_2b_q4.tflite`

   **Где взять модель?** Обычно конвертируют Gemma 2B через MediaPipe.
   - Репозиторий: https://www.kaggle.com/models/google/gemma
   - После конвертации в TFLite через `mediapipe-model-maker`
   - Или готовая квантизованная модель из сообщества

4. Закоммить и запушь — GitHub Actions сам соберёт APK
5. Скачай APK из вкладки Actions → `app-debug` → artifacts

## Сборка локально

```bash
gradle wrapper
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Минимальные требования

- Android 8.0+ (API 26)
- 3+ GB свободной RAM (для работы LLM)
- Файл модели ~1.5 GB
