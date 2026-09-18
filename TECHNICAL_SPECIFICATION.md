# Техническая спецификация «Утка Разраба»

> **Версия:** 1.1
> **Дата:** 18.09.2026
> **Роль автора:** Lead System Analyst
> **Платформа:** Android (публикация в RuStore), minSdk 26 (Android 8.0), targetSdk 35
> **Источники:** [BUSINESS_SPECIFICATION.md](BUSINESS_SPECIFICATION.md) v1.1, [UI_SPECIFICATION.md](UI_SPECIFICATION.md) v1.1
> **Статус:** готова к реализации (все ассеты и каталог звуков сформированы, см. §8.1)
> **Режим работы:** полностью offline (A1, Q10). Серверная часть отсутствует.
>
> **Журнал версий:**
> - **1.0** — первичная спецификация (архитектура, логика, модель данных, контракты, виджет, дефициты, V2-бэклог, открытые вопросы).
> - **1.1** (18.09.2026) — закрыты UQ-1…UQ-5 решениями бизнеса; зафиксированы: механизм перекраски hue-rotation, эталон анимации «Мой Говорящий Том», 5 пресетов звука, 7 радужных цветов + произвольный выбор, отсутствие экспорта записи, BA-01 → V2. Добавлены: §8.1 пакет готовых ассетов, §5.2 версии библиотек, §5.5 логгер, §11 QA-матрица приёмки V1, актуализирован §3.3 под реальный `sound_catalog.json`.

---

## 0. Итоговый объём продукта

| # | Фича | Источник | Статус |
|---|---|---|---|
| 1 | Главный экран с уткой, тап → кряк + анимация | US-01, US-07 | V1 |
| 2 | Выбор типа кряка (SINGLE / RANDOM), 5 пресетов, предпрослушивание | US-02 | V1 (UQ-4) |
| 3 | Кряк по умолчанию, переживает перезапуск | US-03 | V1 |
| 4 | Цвет утки: 7 цветов радуги + произвольный выбор (по кругу/таблице), персистентность | US-04 | V1 (механизм: hue-rotation, см. §2.4/§8) |
| 5 | Виджет 3 размеров (2×2, 4×2, 4×3), кряк из виджета, синхронизация настроек | US-05 | V1 |
| 6 | Персистентное хранение настроек | US-06 | V1 |
| 7 | Анимация при кряке | US-07 | V1 |
| 8 | Онбординг (только первый запуск) | US-08 | V1 |
| 9 | Запись своего кряка (≤5 c), удаление, возврат к пресетам | US-09 | V1 |
| 10 | TalkBack | US-10 | V1 (обязательна для RuStore) |
| 11 | Счётчик «проблем сегодня» | F-01 | V1 |
| 12 | Тёмная/светлая тема (следование системе) | A8 | V1 |
| 13 | Экран «О приложении» | UI §3.3 | V1 |
| 14 | Текстовый дневник «Объясни утке» | BA-01 | **V2** (решение 18.09.2026: не включаем в V1) |
| 15 | Pomodoro, напоминания, оверлей, дневник багов, реакции на long-press | BA-02…04, 06, 07; F-02…05 | **V2** (см. §9) |

---

## 1. Архитектурные решения (контрактные, не рекомендательные)

| Слой | Решение | Обязательно |
|---|---|---|
| UI | Jetpack Compose + Material 3 (compileSdk 35, minSdk 26) | да |
| Паттерн | MVVM: UI → ViewModel → UseCase → Repository → DataSource | да |
| DI | Hilt | да |
| Асинхронность | Kotlin Coroutines + Flow | да |
| Хранение настроек | Jetpack DataStore (Preferences), ключи см. §2.1 | да |
| Хранение записи пользователя | Файл .wav в `filesDir/quack/` | да |
| Звук пресетов | SoundPool (WAV PCM 16-bit mono, ≤1 с) | да |
| Звук записи/предпросмотр | MediaPlayer | да |
| Запись микрофона | MediaRecorder (WAV/MP4 fallback) | да |
| Анимация утки | Растровый PNG + Compose `graphicsLayer` (idle-«дыхание», покачивание, прижатие при тапе); эталон поведения — «Мой Говорящий Том» (UQ-2). Lottie — **опционально**, НЕ требуется для V1 | да (PNG-вариант) |
| Виджет | Jetpack Glance + `AppWidgetProvider`, resizable | да |
| Разрешения | `RECORD_AUDIO` (runtime); `VIBRATE` | да (VIBRATE — normal, декларируется в манифесте) |
| Вибрация | `Vibrator` / `VibratorManager` (API 31+) | да |

Правила архитектуры:
- **Ни один слой выше Repository не читает файлы напрямую.** Доступ к `filesDir` только через `CustomQuackRepository`.
- **Звук и UI развязаны.** `DuckSoundPlayer` (domain) управляет SoundPool/MediaPlayer; ViewModel вызывает только UseCase-сигнатуры (§3.1).
- **Виджет не зависит от Activity.** Читает настройки через `DataStore` синхронно (первый publish) и через `Glance`/корутины; все действия — через `PendingIntent` (§3.2).

---

## 2. Логика системы и Edge Cases

### 2.1 Конечный автомат воспроизведения кряка (UC-01)

```
[Idle] --Tap (valid quack)--> [Debouncing 300ms] --passed--> [Preparing slot] --maxStreams=1-->
   [Playing (SoundPool.play)] --completed/failure--> [Idle]
[Playing] --Re-tap within 300ms--> деbounce-окно сбрасывается, звук НЕ перезапускается (игнор)
[Playing] --Re-tap after 300ms--> [Stop current] -> [Preparing slot] -> [Playing new]
[Silent mode] -> воспроизведение анимации; звук пропускается; если vibrate=true -> Vibrator 80ms
[Sound file missing/corrupt] -> [Fallback preset "Кряк-классика"] + логирование; 
   если 2+ анимации подряд без звука -> Snackbar «Проверь настройки звука»
```

Алгоритм тапа (псевдокод):

```
onDuckTap():
  now = uptimeMillis()
  if (now - lastTapAt) < 300: return                 # debounce (US-01)
  lastTapAt = now
  cancelPendingAnimation()                            # US-07: сброс и рестарт анимации
  quack = resolveQuack()                              # SINGLE|RANDOM|custom (см. resolveQuack)
  try:
    ret = soundPool.play(quack.soundId, volume=1.0)   # maxStreams=1 → текущий автоматически прерывается
    if ret == 0: throw SoundPlaybackException(quack)
    startDuckAnimation(duration = quack.durationMs)   # Compose squeeze base→pressed (дыхание/прижатие), sync
  catch e:
    onPlaybackError(e)                                # fallback-пресет + счётчик «молчаливых анимаций»
  if isRingerSilent():
    soundPool.autoPause()                             # 5a: без звука
    if settings.vibrateOnQuack: vibrator.vibrate(80)
    guardAnimationOnly = true
  incrementDailyCounter()                             # F-01: +1 к «проблемам сегодня»
```

`resolveQuack()` — приоритет выбора звука:

```
if quackMode == RANDOM:
    return randomFrom(availableQuacks)                # пул = пресеты + custom (если есть и валиден)
else: # SINGLE
    if activeQuackId == CUSTOM and customQuackExists: return customQuack
    if activeQuackId in presets and presetLoaded:     return preset
    if activeQuackId == CUSTOM and customQuackMissing: # запись удалена программно
        logWarning("custom quack dangling, fallback to classic")
        return preset(CLASSIC)
    return preset(CLASSIC)                            # total-fallback
```

Краевые случаи воспроизведения:

| # | Сценарий | Поведение | Код/текст |
|---|---|---|---|
| E-01 | Тап быстрее 300 мс | Игнор повторного тапа | — |
| E-02 | Тап после 300 мс при играющем звуке | SoundPool (maxStreams=1) прерывает текущий, стартует новый | — |
| E-03 | Режим «Без звука» | Анимация без звука; вибрация 80 мс, если `vibrateOnQuack` | — |
| E-04 | Режим «Не беспокоить» (ДНД) | Зовётся как silent | — |
| E-05 | MediaVolume == 0, но не silent | SoundPool тихо играет (0 громкость), анимация идёт | — |
| E-06 | Звук повреждён/не загружен | Fallback на «Кряк-классика»; лог катастрофа; если 2+ анимаций без звука подряд — Snackbar «Проверь настройки звука» | E_AUDIO_001 |
| E-07 | Активен custom-кряк, файл удалён (извне/чистка кэша) | Невиданный файл → fallback CLASSIC + сброс `activeQuackId` на CLASSIC, `isCustomActive=false` | E_AUDIO_004 |
| E-08 | SoundPool не смог загрузить ни один пресет | Экран кряков → State ERROR, выбор деактивирован, диалог «Нет доступных звуков. Переустановите приложение.» | E_AUDIO_002 |
| E-09 | Кадры анимации отсутствуют/повреждены | Static `Image` (кадр base), hue-условие то же | E_ANIM_001 |
| E-10 | Preloaded SoundPool возвращает fail (soundId 0) | retry 1 раз через `load()`, иначе fallback | E_AUDIO_003 |
| E-11 | Анимация длиннее звука | Animation обрезается по `quack.durationMs` (звук — приоритет «короткий») | — |
| E-12 | Громкость/каналы: до 14 потоков пресетов | `SoundPool.Builder().setMaxStreams(1)` | — |

### 2.2 Логика каталога кряков и выбора (UC-02, US-02/03)

Данные каталога поставляются как **ассеты приложения** (не сеть). Каждый пресет — пара `wav` + запись в `sound_catalog.json` (см. §3.3).

Логика выбора:

```
selectQuack(id):
  if id == CUSTOM:
     require(customQuackExists) else throw E_AUDIO_004
  if id not in presets: throw E_CATALOG_001
  activeQuackId = id
  isCustomQuackActive = (id == CUSTOM)
  setDefaultFlag(id, true)                 # US-03: «по умолчанию» = активный текущий, единственный
  persist()
```

Краевые случаи:

| # | Сценарий | Поведение | Код/текст |
|---|---|---|---|
| E-C1 | Ни один пресет не загружен | Список → State EMPTY: «Кряков нет» + «Переустановите приложение»; выбор выключен | E_CATALOG_002 |
| E-C2 | Выбран RANDOM, custom-записи нет | Пул = только пресеты; без ошибок | — |
| E-C3 | Выбран RANDOM, custom-файл битый при следующем тапе | Custom исключается из пула на сессию, лог; пресеты работают | E_AUDIO_001 |
| E-C4 | Предпрослушивание (PlayArrow) | Останавливает текущий preview; другие Preview-кнопки в списке disabled на время воспроизведения | — |
| E-C5 | Дублирование «default» | Флаг default применим только к одному кряку: установка нового сбрасывает предыдущий | — |
| E-C6 | Каталог в APK повреждён (json не распарсился) | Статический резервный список по умолчанию (5 пресетов из ресурсов), лог | E_CATALOG_003 |

### 2.3 Логика записи своего кряка (UC-06, US-09)

```
запись:
  1. Проверить RECORD_AUDIO (если нет -> экран-объяснение, §UI 6.2 Loading)
  2. Проверить MediaRecorder.isAvailable()         # E-R1
  3. RecordSession: state = RECORDING, таймер 5с
      - остаток таймера <1с -> auto-stop, Toast «Достигнут лимит 5 секунд»
      - уровень dB -> amplitude bars (50 Гц)
  4. stop(): финализируем файл -> filesDir/quack/custom_<ts>.wav
  5. Валидация длительности: dur < 1s -> Alert «Запись слишком короткая (менее 1 сек)» + удалить файл + reset (E-R4)
  6. Предпросмотр (MediaPlayer)
  7. Сохранить -> переименовать в custom_quack.wav, записать метаданные в DataStore, добавить в каталог как CUSTOM
  8. Отмена/Перезаписать -> удалить временный файл, начать заново
  delete(): удалить файл + метаданные, активный кряк -> CLASSIC (если был CUSTOM)
```

| # | Сценарий | Поведение | Код/текст |
|---|---|---|---|
| E-R1 | MediaRecorder недоступен | Snackbar «Не удалось записать звук» → AlertDialog «Не удалось записать звук» + «Повторить»/«Отмена» | E_REC_001 |
| E-R2 | Микрофон занят другим приложением | Snackbar «Микрофон занят. Попробуйте позже», возврат в настройки | E_REC_002 |
| E-R3 | Отказ от разрешения RECORD_AUDIO | Экран-объяснение; запись недоступна, пресеты работают; повторный запрос — по кнопке | E_PERM_001 |
| E-R4 | Запись <1 c | Alert «Запись слишком короткая (менее 1 сек)», файл удаляется, форма в начальное состояние | E_REC_003 |
| E-R5 | Авто-стоп на 5 c | Остановка, уведомление «Достигнут лимит 5 секунд» | E_REC_004 |
| E-R6 | Нехватка места в storage | Snackbar «Недостаточно свободного места», файл-временный удаляется | E_STORAGE_001 |
| E-R7 | Вызов/звонок: прерванная запись | Auto-stop корректный, файл переходит в предпросмотр | — |
| E-R8 | Во время записи получено ACTION_AUDIO_BECOMING_NOISY (наушники отключили) | Предпросмотр останавливается (MediaPlayer pause) | — |
| E-R9 | Повторная запись поверх существующей | Старый файл удаляется ТОЛЬКО после успешной финализации нового (атомарность: temp → rename) | — |
| E-R10 | Нулевой (тихий) файл ≥1 c | Сохраняется как есть; предупреждения нет (by design) | — |
| E-R11 | Файл записи активен как custom, пользователь удаляет приложение-виджет | Настройки не затрагиваются | — |

### 2.4 Логика цвета утки (UC-03, US-04)

```
setColor(quackColor):
  type: PRESET | CUSTOM_HEX
  if PRESET: id в [7 rainbow] иначе throw E_COLOR_001
  if CUSTOM_HEX: валидация: /^#([0-9A-Fa-f]{6})$/; иначе 422-аналог E_COLOR_002
  duckColor = value
  persist(); notifyWidget(REFRESH_COLOR)     # AppWidgetManager.updateAppWidget
  Snackbar «Цвет сохранён»
```

Палитра (обрезанный спектр): 7 предустановленных цветов радуги + произвольный выбор
через color wheel / таблицу (решение бизнеса 18.09.2026):

| Пресет | Hex | Компоненты RGB | Примечание |
|---|---|---|---|
| `rainbow_red` | `#E5323B` | (229, 50, 59) | красный |
| `rainbow_orange` | `#F2781E` | (242, 120, 30) | оранжевый |
| `rainbow_yellow` | `#F6C120` | (246, 193, 32) | жёлтый (≈ duckBase hue) |
| `rainbow_green` | `#5DBB45` | (93, 187, 69) | зелёный |
| `rainbow_cyan` | `#34B4C9` | (52, 180, 201) | голубой |
| `rainbow_blue` | `#3F6CBA` | (63, 108, 186) | синий |
| `rainbow_violet` | `#8B57B8` | (139, 87, 184) | фиолетовый |

Механика перекраски (hue-rotation, решение принято): к растровому ассету применяется
`ColorMatrix` hue-rotate, где целевой anchor — жёлтая доминанта `#FED950` (duckBase);
`ColorMatrix` считается как `hueDelta = hue(target) - hue(#FED950)`. Для радужных пресетов
оффсет точный; для произвольного цвета → приблизительный (UI §9). `duckShade` (клюв/тень)
не перекрашивается (UI §1.1).

| # | Сценарий | Поведение | Код/текст |
|---|---|---|---|
| E-L1 | Растровый PNG без слоя Body | МЕХАНИЗМ ПО УМОЛЧАНИЮ: hue-rotation жёлтой доминанты (решение 18.09.2026). Пресеты (радужные) — точные, custom — приблизительные; код не требует Lottie-перекраски | E_COLOR_003 |
| E-L2 | Пользователь выбрал слишком тёмный цвет | ColorPicker ограничивает Lightness `0.3f..1f`; hex вне диапазона не применим | E_COLOR_002 |
| E-L3 | Дубликат выбранного (UI: кнопка Apply disabled) | Не вызывается | — |
| E-L4 | Виджет не перерисован (провайдер не ответил) | Перерисовка при следующем `updateAppWidget` (QC-04) | E_WIDGET_002 |
| E-L5 | Цветовой контраст или цвет утки = цвету фона подложки | Подложка виджета НЕ красится в duckColor (surfaceVariant, UI §3.7) | — |

### 2.5 Онбординг (UC-05, US-08)

```
startDestination: if !onboardingCompleted -> "onboarding" else "main"
  onboarding: тап «Понятно» | swipe -> dataStore.onboardingCompleted = true -> popBackStack
  E-O1: DataStore недоступен/ошибка чтения -> трактуется как первый запуск (показать онбординг)
  E-O2: повторный запуск после ошибки записи флага -> флаг перезапишется при следующем завершении онбординга
```

### 2.6 Виджет (UC-04, US-05)

Контракт action'ов — §3.2. Логика:

```
onUpdate(widgetIds): read settings from DataStore -> render Glance (duck PNG с текущим цветом)
  -> updateAppWidget(appWidgetIds)
Первый рендер: placeholder (серый с лого-уткой), затем PNG-кард текущего цвета.
Long-press -> системное контекстное меню -> «Открыть приложение» (PendingIntent -> MainActivity).
Resize: размер читается из AppWidgetOptions (optionsBundle), раскладка 2×2 | 4×2 | 4×3 по минимальным dp (UI §3.7).
E-W1: приложение удалено, виджет остался -> GlanceText «Приложение удалено», clickable off.
E-W2: DataStore читается с опозданием -> показ последнего известного цвета, перерисовать по onUpdate.
E-W3: двойной быстрый тап по виджету -> debounce из §2.1 применяется в PendingIntentReceiver.
E-W4: виджет на экране блокировки/кеепалive -> только кряк, без навигации.
```

### 2.7 Разрешения (matrix)

| Разрешение | Тип | Когда запрашивается | Отказ |
|---|---|---|---|
| `RECORD_AUDIO` | Runtime (dangerous) | При открытии «Записать свой кряк» (US-09) | Функция недоступна, пресеты работают (E-R3), кнопка «Разрешить микрофон» в объяснительном экране |
| `VIBRATE` | Normal | Нет (декларация в манифесте) | — |

Правило: **ни один runtime-запрос не блокирует главный экран**.

### 2.8 Глобальная таблица кодов ошибок

Единый enum `DuckErrorCode` (domain). Локализация — strings.xml, key = `error_<code>`.

| Код | Int | Сценарий | Текст (RU) | HTTP-аналог* |
|---|---|---|---|---|
| `E_AUDIO_001` | 1001 | Пресет повреждён/не загружен | «Проверь настройки звука» (Snackbar) | 500 |
| `E_AUDIO_002` | 1002 | Ни один пресет не загружен | «Нет доступных звуков. Переустановите приложение.» | 500 |
| `E_AUDIO_003` | 1003 | SoundPool load/preload fail | (лог; fallback) | 500 |
| `E_AUDIO_004` | 1004 | Custom-кряк не найден (dangling) | (лог; fallback CLASSIC) | 404 |
| `E_ANIM_001` | 2001 | Кадры анимации отсутствуют | (статичный PNG base) | 500 |
| `E_CATALOG_001` | 3001 | id кряка не найден в каталоге | (игнор; сохранить прежний) | 404 |
| `E_CATALOG_002` | 3002 | Каталог пуст | «Кряков нет», состояние Empty | 204/404 |
| `E_CATALOG_003` | 3003 | Невалидный JSON каталога | (резервный список; лог) | 422 |
| `E_REC_001` | 4001 | MediaRecorder недоступен | «Не удалось записать звук» | 500 |
| `E_REC_002` | 4002 | Микрофон занят | «Микрофон занят. Попробуйте позже» | 409 |
| `E_REC_003` | 4003 | Запись <1 c | «Запись слишком короткая (менее 1 сек)» | 422 |
| `E_REC_004` | 4004 | Лимит 5 c | «Достигнут лимит 5 секунд» | 413 |
| `E_STORAGE_001` | 5001 | Недостаточно места | «Недостаточно свободного места» | 507 |
| `E_PERM_001` | 6001 | Нет RECORD_AUDIO | «Нужен доступ к микрофону» (экран) | 403 |
| `E_COLOR_001` | 7001 | Неизвестный пресет цвета | (игнор) | 404 |
| `E_COLOR_002` | 7002 | Невалидный hex | «Недопустимый формат цвета» | 422 |
| `E_COLOR_003` | 7003 | Перекраска не поддерживается (PNG fallback) | (лог) | 501 |
| `E_WIDGET_001` | 8001 | Провайдер виджета не ответил | (перерисовка по onUpdate) | 500 |
| `E_WIDGET_002` | 8002 | Виджет показывает «Приложение удалено» | «Приложение удалено» | 410 |
| `E_WIDGET_003` | 8003 | PendingIntent не создан (манифест) | (лог) | 500 |
| `E_DATASTORE_001` | 9001 | DataStore read/write error | (defaults; лог) | 500 |
| `E_RECORD_PLAYBACK_001` | 9002 | MediaPlayer custom file error | (fallback; лог) | 500 |

\* HTTP-аналог приведён условно для соответствия формату ТЗ; сетевых запросов в продукте нет.

---

## 3. Модель данных (Data Model)

### 3.1 DataStore Preferences (настройки)

Ключи (единый `DuckPreferences`):

| Ключ | Тип | Required | Default | Ограничения | UseCase |
|---|---|---|---|---|---|
| `active_quack_id` | String | Required | `"classic"` | Один из `presetIds` или `"custom"` | US-02/03 |
| `quack_mode` | String | Required | `"single"` | `single` \| `random` | US-02 |
| `duck_color` | String | Required | `"#F8C840"` (duckBase) | Hex `#RRGGBB` (6 hex-символов) | US-04 |
| `theme` | String | Required | `"system"` | `system` \| `light` \| `dark` | A8 |
| `onboarding_completed` | Boolean | Required | `false` | — | US-08 |
| `custom_quack_meta` | String? | Optional | `null` | JSON {date, durationMs} | US-09 |
| `is_custom_quack_active` | Boolean | Required | `false` | true ⇒ `active_quack_id=="custom"` | US-09 |
| `vibrate_on_quack` | Boolean | Required | `false` | — | US-01 5a |
| `daily_counter` | Int | Required | `0` | 0..Int.MAX, сбрасывается при смене даты | F-01 |
| `daily_counter_date` | String | Required | ISO `yyyy-MM-dd` | формат ISO-дата | F-01 |

Инварианты:
- Если `quack_mode == "single"` и `active_quack_id == "custom"`, то `custom_quack_meta != null` (иначе коррекция по E-07).
- `daily_counter` валиден только для `daily_counter_date == today`; иначе сброс в 0.

### 3.2 Файловая система (запись кряка)

| Абсолютный путь (в `filesDir`) | Назначение | Ограничения |
|---|---|---|
| `quack/custom_quack.wav` | Финальный файл записи пользователя | WAV PCM 16-bit mono, ≤5 c; единственный upfront; не выставлять в MediaStore |
| `quack/tmp_<uuid>.wav` | Временный файл активной записи | Удаляется при Отмена/Перезаписать/ошибке (E-R9, E-R6) |
| `quack/.nomedia` | Маркер игнора в галерее | Обязателен |

Атомарность сохранения: `tmp -> rename to custom_quack.wav`; старый файл удаляется после успешного rename.

### 3.3 Каталог пресетов (поставляется в APK)

Ассет: `assets/sound_catalog.json` (фактический, сформирован 18.09.2026 — см. §8.1)

```json
{
  "version": 1,
  "presets": [
    {
      "id": "classic",
      "name": "Кряква, пруд",
      "file": "quacks/classic.wav",
      "durationMs": 900,
      "isDefault": true,
      "license": "CC BY-SA 4.0",
      "credit": "Пекинская утка и кряква (запись WaderClub)",
      "sourceUrl": "https://commons.wikimedia.org/wiki/File:Pekin_duck_%26_mallard.ogg"
    },
    {
      "id": "duck_home",
      "name": "Домашняя утка",
      "file": "quacks/duck_home.wav",
      "durationMs": 900,
      "isDefault": false,
      "license": "CC BY-SA 4.0",
      "credit": "Домашняя утка (запись Ganesh Mohan T)",
      "sourceUrl": "https://commons.wikimedia.org/wiki/File:Domestic_duck_sound_01.wav"
    },
    {
      "id": "pond_1",
      "name": "Утка, Mudchute 1",
      "file": "quacks/pond_1.wav",
      "durationMs": 859,
      "isDefault": false,
      "license": "CC BY-SA 3.0",
      "credit": "Утки, Mudchute City Farm (запись Secretlondon)",
      "sourceUrl": "https://commons.wikimedia.org/wiki/File:Mudchute_duck_1.ogg"
    },
    {
      "id": "pond_2",
      "name": "Утка, Mudchute 2",
      "file": "quacks/pond_2.wav",
      "durationMs": 900,
      "isDefault": false,
      "license": "CC BY-SA 3.0",
      "credit": "Утки, Mudchute City Farm (запись Secretlondon)",
      "sourceUrl": "https://commons.wikimedia.org/wiki/File:Mudchute_duck_2.ogg"
    },
    {
      "id": "squeak",
      "name": "Резиновая уточка",
      "file": "quacks/squeak.wav",
      "durationMs": 900,
      "isDefault": false,
      "license": "CC BY-SA 4.0",
      "credit": "Писк резиновой утки (запись Dragonhawk12)",
      "sourceUrl": "https://commons.wikimedia.org/wiki/File:Rubber_Duck_Squeaker.ogg"
    }
  ]
}
```

| Поле | Тип | Required | Ограничения |
|---|---|---|---|
| `id` | String | Required | unique, lowercase kebab-case, regex `^[a-z0-9-]+$`, max 32 |
| `name` | String | Required | max 40 символов |
| `file` | String | Required | путь относительно `assets/` |
| `durationMs` | Integer | Required | 100 — 1000 (пресет ≤1 с по A6) |
| `isDefault` | Boolean | Optional | default `false`; флаг единственного экземпляра — `classic` |
| `license` | String | Optional | SPDX-ид лицензии (для экрана «О приложении») |
| `credit` | String | Optional | атрибуция автора записи (обязательна для CC BY(-SA)) |
| `sourceUrl` | String | Optional | ссылка на источник (Wikimedia Commons) |

Минимум результирующих пресетов после парсинга — 5; меньше → E_CATALOG_002/003.
Атрибуция CC BY-SA 3.0/4.0 обязательна: раздел «Звуки» на экране «О приложении»
формируется из полей `license` + `credit` + `sourceUrl` (обязательное требование, а не опция).

### 3.4 Сущность каталога в рантайме (domain)

```
QuackPreset(id: String, name: String, file: String, durationMs: Int, isDefault: Boolean,
            license: String?, credit: String?, sourceUrl: String?) {
  val label get() = "%.1f с · пресет".format(durationMs / 1000.0)   // UI: QuackCard (UI_SPEC §5)
}
QuackCustom(date: Long, durationMs: Int)          // мета из DataStore
DuckSettings(activeQuackId, quackMode, duckColor, theme, onboardingCompleted,
             customQuackMeta: QuackCustom?, isCustomQuackActive, vibrateOnQuack,
             dailyCounter, dailyCounterDate)
DuckError(code: DuckErrorCode, message: String, cause: Throwable?)
```

---

## 4. Проектирование API (контракты взаимодействия)

**Оговорка:** бэкенда нет (Q10/A1) — классических REST/gRPC запросов в системе не существует. Контракты ниже фиксируют (а) domain-уровень доступа к данным и (б) межпроцессные контракты Android (Intent/Broadcast) и формат ответов как типизированных `Result`. Формат ответов и коды даны в стиле HTTP для совместимости с шаблоном ТЗ.

### 4.1 Domain-контракты (диаграмма зависимостей)

```
Collectors: D -> UI        : MainActivity, WidgetReceiver
          /  -> DuckViewModel (StateFlow<DuckUiState>)
          |  -> SettingsViewModel
          V
Repository                       DataSource
DuckSettingsRepository  <----   PreferencesDataSource (DataStore)
DuckCatalogRepository   <----   AssetsDataSource (sound_catalog.json)
CustomQuackRepository   <----   FileDataSource (filesDir) + MediaRecorder
DuckSoundPlayer         <----   SoundPool/MediaPlayer (no DataSource)
WidgetSyncManager       <----   AppWidgetManager + PreferencesDataSource
```

### 4.2 Интерфейсы (сигнатуры Kotlin = контракт)

```kotlin
interface DuckSettingsRepository {
  suspend fun observeSettings(): Flow<DuckSettings>                    // GET settings (SSE-аналог)
  suspend fun setActiveQuack(id: String, setDefault: Boolean = true)   // PUT
  suspend fun setQuackMode(mode: QuackMode)                            // PUT
  suspend fun setDuckColor(colorHex: String)                           // PUT, валидация #RRGGBB
  suspend fun setTheme(theme: Theme)                                   // PUT
  suspend fun completeOnboarding()                                     // PUT
  suspend fun setVibrateOnQuack(v: Boolean)                            // PUT
  suspend fun touchDailyCounter()                                      // PATCH (инкремент)
}

interface DuckCatalogRepository {
  suspend fun getPresets(): Result<List<QuackPreset>, DuckError>       // GET catalog
  suspend fun getActiveQuack(): Result<Quack, DuckError>               // GET active (resolve §2.1)
}

interface CustomQuackRepository {
  suspend fun hasCustom(): Boolean
  suspend fun saveRecording(tmpPath: Path, durationMs: Long): Result<Unit, DuckError>  // PUT, атомарный rename
  suspend fun deleteCustom(): Result<Unit, DuckError>                                  // DELETE
  suspend fun observeCustomMeta(): Flow<QuackCustom?>
}

interface DuckSoundPlayer {
  fun onDuckTap()                                  // UC-01; максимальная частота = 1/300ms (debounce)
  fun playPreview(preset: QuackPreset)             // предпрослушивание (не влияет на active)
  fun stopAll()
}

interface WidgetSyncManager {
  fun notifyColorChanged()                         // trigger AppWidgetManager.updateAppWidget
  fun notifyQuackChanged()
  fun onWidgetUpdate(ids: IntArray)                // entry point from AppWidgetProvider
}
```

### 4.3 Межпроцессные контракты — виджет (Intent/Broadcast)

Объявление в манифесте:

```xml
<receiver android:name=".widget.DuckWidgetProvider"
          android:exported="false">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE"/>
    </intent-filter>
    <meta-data android:name="android.appwidget.provider"
               android:resource="@xml/duck_widget_info"/>
</receiver>
```

Actions (внутренние, экспорт `false`):

| Action | Компонент | Extras | Эффект |
|---|---|---|---|
| `ACTION_QUACK` (`com.duckwidget.QUACK`) | `QuackWidgetActionReceiver` | `EXTRA_WIDGET_ID: Int` | Воспроизвести кряк (clearTop, FLAG_IMMUTABLE) |
| `ACTION_OPEN_APP` (`com.duckwidget.OPEN`) | Activity `MainActivity` | — | Открыть приложение (контекстное меню long-press) |
| `ACTION_APPWIDGET_UPDATE` | `DuckWidgetProvider` | `EXTRA_APPWIDGET_IDS` | Перерисовка (after settings change) |

Пример: успешный контракт тапа по виджету

```text
User tap -> PendingIntent.getBroadcast(context, requestCode=widgetId,
  Intent(action=ACTION_QUACK).setPackage(pkg), FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
  -> onReceive -> WidgetActionReceiver.quack()  // debounce 300ms по EXTRA_WIDGET_ID
  -> success: SoundPool.play + (если виден) обновление кадра анимации
  -> error:    log, no-op (провал виджета не прерывает приложение)
```

Ответы типизированы `Result<T, DuckError>`:

```kotlin
sealed class Result<out T> {
  data class Success<T>(val data: T) : Result<T>()
  data class Failure<E>(val error: E) : Result<Nothing>()
}
```

Примеры «ответов» на данные (формат контракта):

```json
// GET activeQuack (Success 200)
{ "status": 200, "activeQuack": { "id": "classic", "name": "Кряк-классика", "durationMs": 800 } }

// PUT duckColor invalid (422 E_COLOR_002)
{ "status": 422, "code": "E_COLOR_002", "message": "Недопустимый формат цвета",
  "field": "duck_color", "expected": "#RRGGBB", "actual": "yellow" }

// DELETE custom quack, файл не найден (404 E_AUDIO_004) — идемпотентен
{ "status": 200, "code": null, "message": "deleted or absent" }
```

### 4.4 Экран-контракты ViewModel (UI state)

```kotlin
data class DuckUiState(
  val quackMode: QuackMode,
  val activeQuack: QuackView,          // id, name, durationMs
  val duckColor: Color,
  val dailyCounter: Int,
  val isQuacking: Boolean,
  val lastError: DuckError?            // consumed via Snackbar once
)
```

---

## 5. Интеграции и зависимости

### 5.1 Системные сервисы Android (синхронно, локально)

| Сервис | Как | Когда вызов | Fallback |
|---|---|---|---|
| `AudioManager` | Проверка RINGER_MODE_SILENT | Перед каждым тапом (E-03) | — |
| `Vibrator`/`VibratorManager` | 80 мс при silent | При silent + `vibrateOnQuack` | Нет вибро → только анимация |
| `MediaRecorder` (AudioSource.MIC) | Запись | US-09 | E_REC_001 |
| `AppWidgetManager` | updateAppWidget | onUpdate, цвет/кряк, E-W4 | Рассинхрон → перерисовка в след. цикл |
| `DataStore` (ReadWrite) | Порядок записи строго последовательный | Все настройки | E_DATASTORE_001 → defaults |
| `AudioFocus` request | `MediaPlayer` preview/record playback | Предпросмотр записи | Игнор-отказ (короткие звуки) |

**Синхронность между приложением и виджетом:** не через сеть, а через shared `DataStore` + `AppWidgetManager.updateAppWidget`. Порядок: `setDuckColor()` → persist → `notifyColorChanged()` → провайдер перечитывает DataStore → рисует PNG кадра текущего цвета. Тонкость: виджет — отдельный процесс; читать DataStore нужно в `onUpdate` (первый publish) и в `Glance`-корутине с `readPermission` в манифесте провайдера (обязательный `android:readPermission` на `DataStore` файл → использовать `applicationId + ".datastore"` защиту не требуется, файл внутри process). Защита — только внутренний процесс: оба в одном APK.

### 5.2 Сторонние библиотеки (диаграмма)

```
app (compose, material3, lifecycle, navigation)
  ├─ com.airbnb.lottie:lottie-compose        // анимации утки (в V1 не обязателен — hue-rotation PNG)
  ├─ androidx.glance:glance-appwidget        // виджет
  ├─ androidx.datastore:datastore-preferences
  ├─ com.google.dagger:hilt-android          // DI
  ├─ kotlinx.coroutines / kotlinx.serialization (каталог звуков)
  ├─ com.jakewharton.timber:timber           // логгер (см. §5.5)
  └─ (ассеты) filesDir: assets/sound_catalog.json, assets/quacks/*.wav, assets/design/*.png
```

**Зафиксированные версии (reference; точные значения — в `gradle/libs.versions.toml`, перепроверить
актуальность на момент первичной сборки; правило: `compileSdk 35 + AGP` совместимая пара):**

| Библиотека | Версия (reference) | Примечание |
|---|---|---|
| Kotlin | 2.0.x | `kotlinOptions.jvmTarget = 17` |
| AGP | 8.6+ | требуется для compileSdk 35; выше — по готовности |
| androidx.compose:compose-bom | 2025.06.01 (или новее стабильная) | BOM контролирует compose-модули |
| androidx.activity:activity-compose | 1.9.x | — |
| androidx.lifecycle (viewmodel-compose, runtime-compose) | 2.8.x | — |
| androidx.navigation:navigation-compose | 2.8.5 | — |
| androidx.datastore:datastore-preferences | 1.1.1 | — |
| androidx.glance:glance-appwidget | 1.1.1 | — |
| com.airbnb.lottie:lottie-compose | 6.5.x | не обязателен в V1 (см. выше) |
| com.google.dagger:hilt-android (+ hilt-compiler) | 2.52 | — |
| kotlinx-coroutines-android | 1.9.0 | — |
| kotlinx-serialization (json) | 1.7.3 | + serialization Gradle plugin |
| com.jakewharton.timber:timber | 5.0.1 | — |
| test: junit 4.13.2, robolectric 4.13, kotlinx-coroutines-test | 1.9.0 | тесты |
| targetSdk / minSdk | 35 / 26 | — |

Правило обновления: НЕ менять версии без прогона всего Smoke-набора QA-матрицы (§11).

### 5.3 Потоки и конкурентность

| Область | Примитив | Правило |
|---|---|---|
| SoundPool | собственный поток (SoundPool) | Только через `DuckSoundPlayer` singleton |
| MediaRecorder | Coroutine (Dispatchers.Default) | Таймер 5 c tick 100 мс, обновление dB 50 Гц — только на UI (StateFlow) |
| DataStore | sequential (автоматически) | Никаких параллельных write |
| Гланец-виджет | CoroutineScope(SupervisorJob) | Область привязано к провайдеру; отмена при onDeleted |
| Debounce 300 мс | SystemClock | Общий для Activity и WidgetReceiver (независимые инстансы — приемлемо) |

### 5.4 Отсутствующие интеграции (явно не делаем в V1)

- Нет сети/бэкенда (Q10), нет кафки/очередей (см. §9 — только локальные напоминания, NotificationManager в V2).
- Нет аналитики/телеметрии (Q8) → нет 152-ФЗ-compliance.
- Нет экспорта/шаринга записи (Q13, решение 18.09.2026: запись остаётся внутри приложения, экспорт не предусмотрен).
- Нет фоновых служб (foreground service запрещён; запись — только foreground activity).

### 5.5 Логгер

| Параметр | Решение |
|---|---|
| Инструмент | `com.jakewharton.timber:timber` (единая обёртка `DuckLogger`) |
| Tag | `"DuckApp"` на уровне приложения; компонент через `Timber.tag("DuckApp:Widget")` и т.п. |
| Уровни | Debug(dev only), Info, Warn, Error; катастрофические — Error с кодовым маркером (`E_AUDIO_001` и т.п., §2.8) |
| Конфигурация | `BuildConfig.DEBUG` → `Timber.plant(DebugTree)`; Release → `ReleaseTree` (Info+; без чувствительных данных) |
| Запрещено | Логировать содержимое записи, пути вне `filesDir`, персональные данные; `Log` напрямую вне обёртки не используется |
| Ротация | Не требуется (логи — только logcat, `updatePeriodMillis=0`, локальных файлов логов нет) |
| Ошибки данных | E_DATASTORE_001 и E_CATALOG_00x всегда логируются с причиной (throwable); в UI показывается только пользовательский текст из `strings.xml` |

---

## 6. Спецификация виджета (размеры и провайдер)

| Параметр | Значение |
|---|---|
| `minWidth/minHeight` | 2×2: 110×110 dp; 4×2: 250×110 dp; 4×3: 250×180 dp |
| `resizeMode` | `horizontal|vertical` |
| `widgetCategory` | `home_screen` |
| `android:description` | «Утка Разраба: крякни утке» (RuStore widget picker) |
| `initialLayout` | placeholder (серый + лого) |
| `updatePeriodMillis` | `0` (только по событию, без периодических обновлений — экономия батареи) |
| `initialKeyguardLayout` | как 2×2 |

Кнопка «Открыть приложение» в контекстном меню — стандартная, формируется из `MainActivity` + `ActivityInfo` провайдера (long-press меню добавляет действие). Отдельного layout не требуется (UI §3.7).

---

## 7. Обязательные требования к коду (Checklist для разработчика)

- [ ] minSdk 26, targetSdk 35, publish-требования RuStore (targetSdk ≥35).
- [ ] Никаких колбэков на сеть; все `Result`-ошибки проходят через единый `DuckErrorCode`.
- [ ] `RECORD_AUDIO` запрашивается только на экране записи; отказ не блокирует главный экран.
- [ ] Пресеты — WAV PCM 16-bit mono ≤1 c; каталог — `sound_catalog.json`.
- [ ] Виджет: все PendingIntent `FLAG_IMMUTABLE`; receiver `android:exported="false"`.
- [ ] TalkBack: `contentDescription` на все интерактивные элементы (UI §7, US-10).
- [ ] Атрибуция звуков: раздел «Звуки» в «О приложении» из `sound_catalog.json` (`license`, `credit`, `sourceUrl`) — обязательна по CC BY-SA (ATR-01).
- [ ] Запись: атомарный rename; `.nomedia`; удаление временных файлов при отмене.
- [ ] Единый `DuckSoundPlayer` (singleton), maxStreams=1, debounce 300 мс.
- [ ] Термины UI из UI_SPEC §4 (QuackCard, ColorSwatch, etc.) — переиспользовать как есть.

---

## 8. Ограничения по известным дефицитам (UQ5/UQ6)

| Известный дефицит | Решение в коде |
|---|---|---|
| Ассет утки — растровый PNG без альфа: `duck_disugn.PNG`, 1254×1254 px, RGB 8-bit, non-interlaced, фон почти белый #FEFEFE/#FDFDFD, жёлтая доминанта тела ≈ **#FED950**, присутствует чёрный контур/глаз (#000000) | Hue-rotation — ОСНОВНОЙ механизм (решение 18.09.2026), Lottie/vector-перекраска НЕ требуется для V1. Двухкадровый PNG (base/прижатая), вырез утки с альфой для онбординга/виджета/карточек. Документировать в коде как `TINTING_HUE_ROTATION` |
| Виджет без Lottie-анимации | Готовые PNG-кадры (обычная / прижатая); press-эффект через GlanceModifier |
| `duckShade` (#E8A828) не перекрашивается | By design (UI §1.1) |

### 8.1 Пакет готовых ассетов (поставка разработчику)

Сформирован 18.09.2026. Все файлы ниже лежат в репозитории и являются частью релиза V1:

| Файл | Характеристики | Назначение |
|---|---|---|
| `assets/quacks/classic.wav` | WAV PCM 16-bit mono 44100 Гц, 0,900 c | пресет «Кряква, пруд» (default) |
| `assets/quacks/duck_home.wav` | WAV PCM 16-bit mono 44100 Гц, 0,900 c | пресет «Домашняя утка» |
| `assets/quacks/pond_1.wav` | WAV PCM 16-bit mono 44100 Гц, 0,859 c | пресет «Утка, Mudchute 1» |
| `assets/quacks/pond_2.wav` | WAV PCM 16-bit mono 44100 Гц, 0,900 c | пресет «Утка, Mudchute 2» |
| `assets/quacks/squeak.wav` | WAV PCM 16-bit mono 44100 Гц, 0,900 c | пресет «Резиновая уточка» |
| `assets/sound_catalog.json` | см. §3.3 (полная копия) | каталог пресетов + атрибуция |
| `assets/design/duck_cutout.png` | 1254×1254 RGBA, вырез утки (фон удалён), ≤711 КБ | онбординг, карточки, виджет-кадры |
| `assets/design/widget_base.png` | 512×512 RGBA | кадр виджета «обычное состояние» |
| `assets/design/widget_pressed.png` | 512×512 RGBA | кадр виджета «прижатое» (scale 0.92, сдвиг вниз) |
| `assets/design/ic_launcher_foreground.png` | 1080×1080 RGBA, утка в safe zone | adaptive-иконка (foreground) |
| `assets/design/ic_launcher_background.png` | 1080×1080 RGB (`#F6C120`) | adaptive-иконка (background) |
| `assets/quacks_src/*` | исходные записи (ogg/wav, Wikimedia Commons) | SRC только для проверки лицензий; в APK НЕ включаются |

Правила поставки:
- В APK включаются: `assets/sound_catalog.json`, `assets/quacks/*.wav`, `assets/design/duck_cutout.png`,
  кадры виджета, оба `ic_launcher_*`.
- `assets/quacks_src/` — не включать (в `noCompress`-исключения не попадает, в `git` остаётся как трейс лицензий).
- Точка выреза утки: `duck_cutout.png` — единый источник; виджет-кадры/иконка генерируются из него
  скриптами (см. раздел Сборка).

---

## 9. Дополнительные варианты использования утки (response на запрос бизнеса)

Кандидаты на включение (V2, кроме F-01):

| ID | Фича | Логика | Ценность |
|---|---|---|---|
| F-01 | Счётчик «Сегодня ты объяснил утке: N» | Инкремент при каждом кряке (per-day), сброс по дате | мотивация, retention | 
| BA-01 | Текстовый лог/дневник сессии | Поле ввода → утка «слушает» (Lottie thinking) → лог сохраняется локально в `filesDir/journal/*.json`; просмотр «вчерашний дневник» | экстернализация мыслей = ядро RDD |
| BA-02 | Pomodoro 25/5 с уткой | Таймер + утка-состояние (работа/отдых), запуск из настроек | фокус |
| BA-03 / F-05 | Напоминание «Ты объяснил утке?» раз в час | NotificationChannel + quick action «Покрякать» (PendingIntent→main) | DAU |
| BA-04 | Дневник багов с тегами + геймификация | Список + теги + счётчик → уровни «Утёнок→Старшая утка→Дракон» | геймификация |
| BA-06 | Кряк-событий: при уведомлении/тишине (Ambient) | Подписка на системные нотификации/фокус → авто-кряк | «жизненность» |
| BA-07 | Плавающий оверлей поверх IDE | SYSTEM_ALERT_WINDOW overlay (FAB с уткой); риск-фича (Google Play/RuStore политики оверлеев) | «утка рядом всегда» |
| F-02 | Настроение утки по времени/количеству кряков | Ночная «спящая» Lottie; злость после 30 кряков за сессию | живость |
| F-03 | Long-press 600 мс → спец-кряк | Отдельный пресет «злой/радостный» | вариативность |
| F-04 | Бейдж-статистика в виджете 4×3: «12 багов объяснено» | Подпись на виджете (читает counter) | геймификация |
| (новое) | Комбо-кряк: быстрый серийный тап → ускоренный кряк (pitch up) | Pitch через SoundPool `setPitch`, определяется частотой тапов | игровость |
| (новое) | Виджет-кряк с анимацией «утка плывёт» при тапе (wave PNG-кадры) | Серия кадров на пресс | живость виджета |

Приоритет V1: только **F-01** (счётчик). BA-01 (дневник) и остальные фичи — бэклог V2 (BA-01 отклонён для V1 решением 18.09.2026).

---

## 10. Открытые вопросы

| # | Вопрос | Решение | Статус |
|---|---|---|---|
| UQ-1 | Дизайн-ассет: Lottie/vector со слоем `Body` | **Закрыт** — работаем на растровом PNG, перекраска через hue-rotation (18.09.2026) | ✅ |
| UQ-2 | Эталон анимации «приложение с котом» | **«Мой Говорящий Том»** (com.outfit7.mytalkingtomfree, RuStore). Стиль: живой питомец — idle «дыхание», покачивание при тапе, прижатие, короткая мимика | ✅ |
| UQ-3 | Экспорт/шаринг записи (Q13) | **Нет** — запись остаётся внутри приложения (18.09.2026) | ✅ |
| UQ-4 | Количество пресетов + источник звука | **5 пресетов**, WAV PCM 16-bit mono ≤1 c; CC BY-SA 3.0/4.0 (Wikimedia Commons), атрибуция обязательна и выводится в «О приложении» | ✅ (сформированы 18.09.2026, ТЗ §8.1, ATR-01) |
| UQ-5 (новый) | Анимация в виджете (Glance без Lottie) | Двухкадровый PNG (base/прижатая), press-эффект  | ✅ (решение V1) |

---

## 11. QA-матрица приёмки V1

Полный «зелёный» прогон набора — критерий готовности к публикации в RuStore.
Smoke-подмножество (SA-01, CA-01, WI-01, TH-01, RE-01, RE-02) обязателен после каждого
обновления любой версии библиотеки (§5.2).

| ID | Требование | Шаги проверки | Ожидаемый результат |
|---|---|---|---|
| ON-01 | US-08 | Первый запуск: онбординг → «Понятно» → главный экран; повторный запуск | Онбординг только 1 раз; после закрытия флаг записан (E-O1/E-O2 — повторный запуск без онбординга) |
| SA-01 | US-01 | Тап по утке | Кряк (звук default) + анимация «прижат/дыхание»; debounce 300 мс: повторный тап <300 мс не перезапускает звук |
| SA-02 | US-01 + E-02 | Тап при играющем звуке (тап >300 мс) | Звук прерывается, стартует новый |
| SA-03 | US-01 (silent) | Выключить звук (silent/ДНД) + тап | Анимация без звука; вибрация 80 мс если `vibrateOnQuack` |
| SA-04 | 2.1 E-06…E-10 | Удалить/повредить `assets/quacks/classic.wav` в отладочной сборке + тап | Fallback «Кряк-классика»-пресет (другой), лог `E_AUDIO_001`; 2+ «молчаливые» анимации → Snackbar «Проверь настройки звука» |
| CA-01 | US-02/03 | Экран кряков: выбрать каждый пресет → предпросмотр → «Сделать кряком по умолчанию» | Превью играет; флаг default единственный; выбор сохраняется |
| CA-02 | US-02 RANDOM | Включить RANDOM; тап 10× | Кряки из пула, присутствует случайность; каталог не пустой |
| CO-01 | US-04 | Применить 7 радужных цветов по очереди | Утка перекрашивается точно (hex совпадает с таблицей §2.4), Snackbar «Цвет сохранён» |
| CO-02 | US-04 (wheel) | Выбрать произвольный цвет из wheel/таблицы; hex `#RRGGBB` | Применён (hue-rotation приблизительный); невалидный hex → «Недопустимый формат цвета» `E_COLOR_002` |
| CO-03 | E-L2 | Выбрать слишком тёмный цвет (Lightness <0.3) | Не применяется (`E_COLOR_002`); ползунок ограничен 0.3..1.0 |
| CO-04 | E-L1 | Перекраска на растровом PNG | Работает (hue-rotation, `TINTING_HUE_ROTATION`); `duckShade` и чёрный контур не меняются |
| WI-01 | US-05 | Добавить виджет 2×2, 4×2, 4×3; тап по утке в виджете | Кряк из виджета (sound через `ACTION_QUACK`); раскладка соответствует размеру |
| WI-02 | US-05 (sync) | Сменить цвет/кряк в приложении → проверить виджет | Кадр и цвет виджета обновились без перезапуска |
| WI-03 | E-W1 | Удалить приложение, оставить виджет | «Приложение удалено», tap неактивен |
| WI-04 | E-W3 | Двойной быстрый тап по виджету | Один кряк (debounce) |
| TH-01 | A8 | Переключить тему системы light/dark | UI перестраивается; виджет не зависит от темы (surfaceVariant) |
| RE-01 | US-09 | Записать кряк (микрофон доступен, ~3 с) → предпросмотр → сохранить → тап по утке | Воспроизводится custom-запись; флаг `is_custom_quack_active=true` |
| RE-02 | US-09 (короткая) | Записать <1 с → сохранить | Alert «Запись слишком короткая (менее 1 сек)», файл удалён, форма сброшена `E_REC_003` |
| RE-03 | US-09 (лимит) | Записать ≥5 с | Авто-стоп на 5 с, Toast «Достигнут лимит 5 секунд» `E_REC_004` |
| RE-04 | US-09 (отказ доступа) | Отклонить RECORD_AUDIO | Экран-объяснение; пресеты работают; кнопка «Разрешить микрофон» |
| RE-05 | US-09 (удаление) | Удалить запись | Файл+мета удалены; активный кряк сброшен на classic `E_AUDIO_004` (fallback в лог) |
| CN-01 | F-01 | Тап по утке 5× — открыть счётчик | «Объяснено утке: 5»; после смены даты — сброс в 0 |
| ACC-01 | US-06 | Изменить настройки → свернуть → перезапустить | Все настройки переживают перезапуск (DataStore) |
| A11-01 | US-10 (TalkBack) | Включить TalkBack; пройти главный / каталог / запись / виджет | Все элементы имеют `contentDescription`; виджет = текст+кнопка; никаких «необъявленных» тапов |
| ATR-01 | «О приложении» | Открыть «О приложении» → раздел «Звуки» | Для всех 5 пресетов отображаются `credit`, `license`, `sourceUrl` (атрибуция CC BY-SA обязательна) |
| ICON-01 | Лаунчер | Установка; просмотр иконки в лаунчере и в RuStore | Adaptive-иконка не обрезана (утка в safe zone), фон `#F6C120` |