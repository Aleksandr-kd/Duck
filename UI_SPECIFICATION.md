# UI Layout Specification — «Утка Разраба»

> **Целевой стек:** Android, Jetpack Compose + Material 3, minSdk 26 (A2), offline (A1), язык RU (A5), Lottie (A7), SoundPool, Glance.
> **Версия:** 1.1 (адаптировано под фактический дизайн утки).
> **Дизайн-ассет утки (факт):** `утка_дазійн.PNG` — растровый RGB PNG 1536×1024, фон почти белый (#FDFDFD), тело утки жёлтое (доминанта ≈ #F8C840), тень/клюв ≈ #E8A828, прозрачного слоя нет.
> **Назначение:** по этому документу фронтенд-разработчик пишет код без графических макетов.

---

## 0. Технологический базис

| Слой | Решение |
|---|---|
| UI-фреймворк | Jetpack Compose, Material 3 (`compileSdk 35`, `minSdk 26`) |
| Тема | `darkColorScheme()` / `lightColorScheme()`, следование системной (`isSystemInDarkTheme`) |
| Типографика | Roboto (системный) для body + Nunito SemiBold/Bold для заголовков (кириллица, округлость для «тёплого» тона) |
| Анимация утки | Исходник — растровый макет (PNG без альфы) → запрос у дизайнера векторной/Lottie-версии с отдельным слоем тела (`Body`). В рантайме `LottieComposition`, плей при тапе, `setSpeed` синхронно со звуком |
| Звук | `SoundPool` для пресетов (WAV PCM 16-bit mono, ≤1s, A6), `MediaPlayer` для записи пользователя |
| Хранение | DataStore Preferences (настройки), файлы в `filesDir` (запись кряка) |
| Виджет | Jetpack Glance + `AppWidgetProvider`, resizable |
| Адаптивность | Зависит от ориентации/размера: главный экран — центровка; настройки — `LazyColumn` с maxWidth `600.dp` |

---

## 1. Глобальные дизайн-токены

### 1.1 Цветовая система (M3 + кастом)

| Токен | Light | Dark | Применение |
|---|---|---|---|
| `primary` | #6B5B95 (фиолет-индиго) | #D0BCFF | Активные элементы, ключевые кнопки |
| `onPrimary` | #FFFFFF | #381E72 | Контент на primary |
| `primaryContainer` | #E6DDFF | #4F378B | Бейджи, подложки выбранного |
| `secondary` | #5E8C61 (утино-зелёный) | #7FD080 | Второстепенные элементы |
| `background` | #FDF8F2 (тёплый крем) | #141218 | Фон экранов |
| `surface` | #FFFFFF | #1E1A22 | Карточки, диалоги |
| `onSurface` | #1A1C1E | #E6E1E5 | Основной текст |
| `surfaceVariant` | #F4EFE6 | #49454F | Подложки полей ввода |
| `error` | #BA1A1A | #FFB4AB | Ошибки |
| `success` (кастом) | #2E7D32 | #81C995 | Успешное сохранение записи |
| `outline` | #79747E | #938F99 | Границы невыбранных элементов |
| `duckBase` | #F8C840 (тело утки по макету) | #F8C840 | Базовый жёлтый утки, default для US-04 |
| `duckShade` | #E8A828 (тень/клюв из макета) | #E8A828 | Не участвует в перекраске |
| `quackColor` (active) | выбирается юзером (US-04); default = `duckBase` | — | Цвет тела утки на всех экранах и виджете |

### 1.2 Сетка и отступы

```
spacing.xs = 4.dp   // зазор между иконкой и текстом в строке
spacing.s  = 8.dp   // внутренние отступы компактных чипов
spacing.m  = 16.dp  // стандартный padding страницы
spacing.l  = 24.dp  // отступы крупных карточек
spacing.xl = 32.dp  // верхние отступы секций-заголовков
```

Мобильная сетка: одно-колоночная (Column/LazyColumn), max content width → `600.dp`, центровка `Box(contentAlignment = Alignment.Center)`.
Десктоп/планшет (DPI ≥ 840dp): двухколоночная сетка настроек — `LazyVerticalGrid(GridCells.Adaptive(minSize = 280.dp))` для кряков и цветов.

---

## 2. Экранная карта и навигация

```
NavHost(graph):
  startDestination = "splash/main"  // условно: если !onboardingCompleted → "onboarding"
  ├─ "onboarding"      // US-08
  ├─ "main"            // US-01, US-07 → DuckScreen
  ├─ "settings"        // контейнер → "quacks", "color", "record", "about"
  │   ├─ "settings/quacks"    // US-02, US-03
  │   ├─ "settings/color"     // US-04
  │   └─ "settings/record"    // US-09
  └─ AppWidget (вне NavHost, отдельный remote URI)
```

Переходы: `slide_in/slide_out` на уровне настроек, главный ← → настройки через `fade`.

---

## 3. Анатомия экранов

### 3.1 Онбординг (`onboarding`) — US-08

```
Scaffold(containerColor = background) {
  Column(centered, verticalArrangement = Center, padding = m) {
    // [1] Иллюстрация
    Box(size 180.dp, clip = CircleShape, background = primaryContainer) {
      DuckAsset(loop = true, tilt = 10f, contentScale = fit)   // утка кивает (используем тот же ассет)
    }
    // [2] Заголовок
    Text("Утка Разраба", style = headlineMedium, font = Nunito Bold, color = onBackground, textAlign = Center)
    // [3] Подзаголовок/объяснение
    Text("Объясни утке свою проблему.\nНажми на неё!",
         style = bodyLarge, color = onSurfaceVariant, textAlign = Center, lineHeight = 24.sp)
    // [4] Действие
    Button("Понятно", filled, containerColor = primary, shape = RoundedCornerShape(24.dp),
           height = 56.dp, width = fillMaxWidth(0.7f)) {
      Icon(Icons.Rounded.CheckCircle)
      Text("Понятно", labelLarge)
    }
  }
}
```

Поведение:
- Тап по «Понятно» → `onboardingCompleted = true` в DataStore → `popBackStack` → главный. Повторно не показывается (A9/US-08).
- Свайп в любую сторону → закрытие онбординга (эквивалентно «Понятно», если gesture навигация включена).
- TalkBack: фокус на кнопку сразу после отрисовки.

### 3.2 Главный экран (`main`) — US-01, US-07

```
Scaffold(
  containerColor = background,
  topBar = CenterAlignedTopAppBar(
    title = Text("Утка Разраба", titleLarge, Nunito Bold),
    actions = [ IconButton(Settings, → "settings") ]
  )
) {
  Box(fillMaxSize, contentAlignment = Center) {
    Column(horizontalAlignment = CenterHorizontally) {
      // [1] Зона утки (кликабельна)
      // Зона утки. Пропорции из макета: холст 1536×1024, утка ~1.35:1 (W:H).
      Box(fillMaxWidth(0.8f), aspectRatio(1.35f)) {
        // Перекраска (US-04): Lottie keypath "Body.Color" (если вектор/Lottie)
        // ИЛИ hue-rotation жёлтой доминанты (fallback для растрового PNG) = quackColor
        DuckAsset(modifier = Modifier
          .pointerInput(Unit) { detectTapGestures { duckViewModel.onTap() } })   // рипл
        // анимация кряка: тот же ассет, scale 1.06→1.0 + shake по X, синхронно со звуком
        DuckQuackOverlay(visibleIf = isQuacking, duration = quackDurationMs)
      }
      // [2] Подпись-подсказка
      Text(if (isQuacking) "Кря! Кря!" else "Нажми — я покрякаю",
           bodyLarge, color = onSurfaceVariant)
      // [3] Счётчик сессии (мотивация, предлагаемая фича)
      Text("Сегодня ты объяснил утке: ${counter} проблем",
           labelMedium, color = onSurfaceVariant.copy(alpha = 0.6f))
    }
  }
}
```

Поведение при изменении размера:
- Тел.: утка ≈ 75% ширины экрана (aspect 1.35:1 из макета), все элементы в одной колонке.
- Планшет (width ≥ 840dp): утка до 480dp ширины, подсказка ниже.

### 3.3 Экран настроек (`settings`) — контейнер

```
Scaffold(
  topBar = TopAppBar(title = "Настройки", navigationIcon = BackArrow)
) {
  LazyColumn(fillMaxSize, contentPadding = m, verticalArrangement = spacing s 8.dp, maxWidth 600.dp) {
    item { SettingListItem("Выбор кряка", text1 = "Активный: ${activeQuackName}", leading = MusicNote,
                           → "settings/quacks") }
    item { SettingListItem("Цвет утки",      text1 = "Текущий: ${duckColorName}", leading = Palette,
                           → "settings/color") }
    item { SettingListItem("Записать свой кряк", text1 = customQuack?.let{"Есть запись"} ?: "Запись до 5 сек",
                           leading = Mic, → "settings/record") }
    item { HorizontalDivider() }
    item { SettingListItem("О приложении", leading = Info, → "about") }
  }
}
```

`SettingListItem` описан в §4.7.

### 3.4 Выбор кряка (`settings/quacks`) — US-02, US-03

```
LazyColumn(padding m) {
  // Блок «Режим»
  item { SectionHeader("Режим кряка") }
  item { SegmentedButton(
           selected = { SINGLE, RANDOM },
           options = ["Один и тот же", "Случайный"] ) }           // Сегмент 2 шт
  if (selectedMode == SINGLE) {
    item { SectionHeader("Активный кряк") }
    items(presets) { QuackCard(it, isActive = it.id == activeQuackId) }
  }
  item { SectionHeader("Свой кряк") }
  item { CustomQuackRow(customQuack, → "settings/record") }       // "Свой кряк" + play + delete
  item { TextButton("Записать свой кряк", icon = Mic, → "settings/record") }
}
```

### 3.5 Цвет утки (`settings/color`) — US-04

```
LazyVerticalGrid(columns = Adaptive(minSize = 72.dp), padding m, spacing 8.dp) {
  items(presetColors) { ColorSwatch(it, isSelected = it == quackColor) }   // 8 шт
  item { AddCustomColorTile }   // «Свой цвет» → открывает ColorPickerDialog
}
// внизу — галочка/кнопка подтверждения
item { Button "Применить" (enabled = selectedColor != quackColor) }.
Text preview: маленькая утка-Lottie с текущим цветом под сеткой (feedback US-04).
```

### 3.6 Запись кряка (`settings/record`) — US-09

```
Scaffold(topBar = "Запись кряка" + Back) {
  Column(centered, padding m) {
    // [1] Индикатор уровня (waveform)
    RecordLevelIndicator(dB, isRecording)      // §4.5
    // [2] Таймер
    Text(if (isRecording) "${remaining}s" else "Запись до 5 секунд",
         headlineMedium, Nunito Bold, color = if (remaining==0) error else onSurface)
    // [3] Кнопки
    Row(spacing 16.dp, centered) {
      if (!isRecording) {
        Button("Начать запись", icon = Mic, containerColor = primary) { RecordingSession.start() }
      } else {
        Button("Стоп", containerColor = error, icon = Stop) { RecordingSession.stop() }
      }
    }
    // [4] Пост-запись — карточка предпрослушивания
    if (recordedPreview != null) {
      Card {
        Row {
          IconButton(PlayArrow → playback preview / Stop → stop preview)
          Column { Text("Предпросмотр", titleMedium); Text("${duration}s · Свой кряк", bodyMedium) }
        }
        Row(End) {
          TextButton("Отмена", color = onSurfaceVariant)  { discard() }
          TextButton("Перезаписать", color = onSurfaceVariant) { startNew() }
          Button("Сохранить", containerColor = success) { saveToFiles() }
        }
      }
    }
  }
}
```

### 3.7 Виджет (`AppWidget`) — US-05

Три размерных вида в одном `Glance`-композиции, размер читается из `AppWidgetOptions`:

| Size | Минимум (dp) | Внутренний layout |
|---|---|---|
| 2×2 | 110×110 | `Box(center) { DuckImage(circle) }` — только утка |
| 4×2 | 250×110 | `Row { DuckImage(80.dp); Text("Жми — покрякаю") }` — утка + подпись |
| 4×3 | 250×180 | `Column { Row(утка + подпись) ; Text(activeQuackName, labelMedium) }` — + статус |

```
GlanceTheme(colors = dark/light) {
  GlanceBox(fillMaxSize, background = CardBackground /* surface */, roundedCornerRadius = 16.dp) {
    // Clickable → PendingIntent(action = "quack")
    GlanceBox(modifier = GlanceModifier
        .fillMaxSize()
        .clickable(action = quackAction)
        .background(surfaceVariant)  // НЕ duckColor: жёлтая утка на жёлтом нечитаема
        .cornerRadius(24.dp)) {
      // Подложка для читаемости жёлтой утки в светлой теме: тень (elevation) — нижний GlanceBox,
      // поверх — сам ассет. Перекраска: предгенерация PNG-кадра текущего цвета при onUpdate.
      GlanceImage(provider = duckPainter(quackColor), contentScale = fit)  // цвет из DataStore
    }
    // маленький значок шестеренки: long-press → системное контекстное меню (встроено ОС)
  }
}
```

- Long-press на виджет → системное контекстное меню → пункт «Открыть приложение» (PendingIntent → MainActivity) — US-05.
- Анимация в виджете: ограниченная (эффект press / scale), т.к. Glance без Lottie — используем `GlanceModifier.clip` + готовые PNG-кадры (2 кадра: обычная / прижатая).

---

## 4. Компонентная структура

Формат: иерархия (вложенность) → контент → стили.

### 4.1 `QuackCard` (карточка кряка)

```
Card(
  border = if (isActive) { primary, 2.dp } else { outlineVariant, 1.dp },
  colors = CardDefaults.cardColors(containerColor =
      if (isActive) primaryContainer else surface),
  shape = RoundedCornerShape(16.dp),
  onClick = { onSelect(quack) },
  modifier = Modifier.fillMaxWidth()   // в списке: padding bottom 8.dp
) {
  Row(verticalAlignment = CenterVertically, padding 16.dp) {
    // 1. Радио-индикатор выбора
    RadioButton(selected = isActive, onClick = { onSelect(quack) },
                colors = RadioButtonDefaults.colors(selectedColor = primary))
    // 2. Название + метаданные
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Start) {
      Text(quack.name, titleMedium, color = onSurface)                       // «Кряк-классика»
      Text(quack.label, bodySmall, color = onSurfaceVariant)                 // «0.8 с · пресет»
    }
    // 3. Кнопка предпрослушивания
    IconButton(onClick = { playPreview(quack) },
               border = CircleShape, size = 40.dp) {
      Icon(Icons.Rounded.PlayArrow, contentDescription = "Прослушать")      // при игре — Icons.Rounded.Stop
    }
    // 4. Маркер «по умолчанию»
    if (quack.isDefault) { Badge { Text("default", labelSmall, color = primaryContainer) } }
  }
}
```

Клик по карточке = выбор кряка. Клик по иконке play = предпрослушать (без смены активного).

### 4.2 `CustomQuackRow` (свой кряк в списке)

```
Card(outlineVariant, 1.dp) {
  Row(16.dp) {
    Icon(Mic, tint = secondary, size 24.dp)
    Column(weight 1f) {
      Text("Свой кряк", titleMedium)
      Text(customQuack?.let { "${it.duration}s · записано ${it.date}" } ?: "Не записан", bodySmall, onSurfaceVariant)
    }
    if (customQuack != null) {
      IconButton(PlayArrow)   // предпрослушать запись
      IconButton(Delete, tint = error)  // удалить → ConfirmDialog «Удалить свой кряк?»
    }
  }
}
```

### 4.3 `ColorSwatch` (пресетный цвет)

```
Box(
  size = 56.dp,
  clip = CircleShape,
  border = if (isSelected) { primary, 3.dp } else { outlineVariant, 1.dp },
  background = presetColor,
  onClick = { onPickName(preset) },
  contentAlignment = Center
) {
  if (isSelected) Icon(Icons.Rounded.Check, color = contrastOn(presetColor), size 28.dp)
}
// внизу — Text(preset.name, labelSmall, onSurfaceVariant)
```

### 4.4 `ColorPickerDialog` (произвольный цвет)

```
AlertDialog(
  shape = RoundedCornerShape(28.dp),
  containerColor = surface,
  title = Text("Свой цвет утки", titleLarge, Nunito Bold),
  text = {
    Column(24.dp) {
      ColorWheel(180.dp)                    // H 0–360
      Slider(value = saturation, 0f..1f)    // S
      Slider(value = lightness, 0.3f..1f)   // L ← затемнение полностью не даём (утка читаема)
      Box(48.dp, rounded 12.dp, background = previewColor)  // превью выбранного
      Text("#${hex}", bodySmall, monospace, onSurfaceVariant)
    }
  },
  confirmButton = Button("Применить", containerColor = primary)   // enabled = colorChanged
  ,
  dismissButton = TextButton("Отмена", onSurfaceVariant)
)
```

### 4.5 `RecordLevelIndicator` (уровень звука)

```
Row(horizontalArrangement = SpaceEvenly, width 280.dp, height 48.dp) {
  repeat(14) { index ->
    Box(
      width = 6.dp,
      height = barHeightByDb(level, index),   // 8..48.dp, амплитуда по громкости
      clip = RoundedCornerShape(3.dp),
      background = if (isRecording) {
          if (index < 5) secondary else primary     // пониже/повыше
        } else surfaceVariant
    )
  }
}
```

При `!isRecording` бары серые и минимальные, анимация не идёт. При записи бары «прыгают» с таймингом синхронно с dB (обновление ~50 Гц).

### 4.6 `SegmentedButton` (режим SINGLE/RANDOM)

```
SingleChoiceSegmentedButtonRow(fillMaxWidth) {
  SegmentedButton(
    selected = (mode == SINGLE),
    onClick = { mode = SINGLE },
    shape = SegmentedButtonDefaults.itemShape(index, count = 2),
    colors = SegmentedButtonDefaults.colors(activeContainerColor = primary,
                                            activeContentColor = onPrimary)
  ) { Text("Один кряк") }
  SegmentedButton(/* то же */ RANDOM) { Text("Случайный") }
}
```

### 4.7 `SettingListItem` (строка настроек)

```
Row(Modifier.fillMaxWidth().clickable(onClick).padding(vertical 12.dp, horizontal m),
    verticalAlignment = CenterVertically) {
  Icon(leadingIcon, tint = primary, modifier = 28.dp)
  Column(weight 1f, start 16.dp) {
    Text(title, titleMedium, onSurface)
    if (text1 != null) Text(text1, bodySmall, onSurfaceVariant)
  }
  Icon(Icons.Rounded.ChevronRight, tint = outline)
}
```

### 4.8 Кнопки (общие)

| Тип | Compose | Стиль |
|---|---|---|
| Primary | `Button` | `containerColor = primary`, `shape = RoundedCornerShape(24.dp)`, `height 48.dp` |
| Secondary | `OutlinedButton` | `border = outline`, `textColor = primary` |
| Danger | `Button` | `containerColor = error`, `contentColor = onError` |
| Success | `Button` | `containerColor = success`, `contentColor = onSuccess` |
| Text | `TextButton` | `color = primary` |
| Icon | `IconButton` | размер 48.dp, `Icon` 24.dp |

Минимальная площадь касания — 48×48dp (a11y).

---

## 5. Интерактивные состояния (UI States)

Android-континуум: `Idle → Pressed(120ms ripple) → Released → ...`.

### 5.1 Утка на главном экране и виджете

| Состояние | Визуал | Логика |
|---|---|---|
| Idle | базовая Lottie, «дышит» (Loop 20/100, stagger) | — |
| Pressed | scale 0.92 (`graphicsLayer`, spring `DampingRatioNoBouncy`) | тап зарегистрирован |
| Quacking | Lottie `duck_quack` + звук, 300–800ms | sync со SoundPool |
| Re-tap during quack | анимация сбрасывается и рестартует, звук прерывается | debounce = 300ms (US-01) |
| Silent mode | только анимация; если `vibrate=true` → `Vibrator` 80ms | US-01 |
| Sound file broken | fallback-пресет + логирование; если утка «молчит» >2 анимаций подряд → Snackbar «Проверь настройки звука» | UC-01 error |

### 5.2 Кнопка / карточка

| State | Эффект |
|---|---|
| Idle | как в §4 |
| Pressed | ripple (`Indication`), для карточек — `scale 0.98` |
| Focused (Tab-навигация) | `border 2.dp primary` вокруг фокуса (не пресетный) |
| Disabled | `alpha = 0.4f`, если раньше был текстовый цвет — onSurfaceVariant; onClick не срабатывает |

### 5.3 Клик-логики (отображение/скрытие контента)

- **Предпрослушивание кряка:** нажатие PlayArrow → иконка меняется на Stop → другим PlayArrow в списке disabled; повторный тап — остановка, сброс иконок.
- **«Применить» цвет:** смена цвета мгновенно на превью-утке (US-04); подтверждение через Snackbar «Цвет сохранён», возврат в «Настройки».
- **Свой кряк — сохранение:** `saved` → Snackbar success, навигация назад в список кряков, «Свой кряк» появляется в списке активным.
- **Удаление своего кряка:** `ConfirmDialog`: заголовок «Удалить запись?» / text «Запись будет удалена безвозвратно.» / confirm `error` «Удалить» / dismiss «Отмена». После удаления активный кряк → «Кряк-классика».
- **Запись:** кнопка «Стоп» → предпрослушивание; если длина <1s → Alert error «Запись слишком короткая (менее 1 сек)» → автоматический сброс, форма к началу (UC-06 9a–12b).

---

## 6. Состояния данных (Data States)

### 6.1 Список кряков (`settings/quacks`)

| State | UI |
|---|---|
| **Loading** | 5 `QuackCard`-скелетонов: `Card` с внутренним `Box(alpha 0.1f secondary)`, shimmer (анимация translateX -100%→100%), высота 72.dp |
| **Empty** | `Column(center)`: утка-Lottie (грустная, loop) + `Text("Кряков нет", titleMedium)` + `Text("Переустановите приложение", bodyMedium)` + кнопка «Звуки не загружены» disabled (UC-02) |
| **Error** | `AlertDialog(error)`: title «Нет доступных звуков. Переустановите приложение.» + кнопка «ОК» → выбор деактивирован (UC-02) |

### 6.2 Запись кряка

| State | UI |
|---|---|
| **Loading** (без разрешения) | Центрированный экран-объяснение: Icon(Mic off), `Text("Нужен доступ к микрофону", titleMedium)`, `Text("Запись своего кряка работает с микрофоном. Предустановленные кряки доступны и без него.", bodyMedium)`, `Button("Разрешить микрофон")` → permission request (UC-06) |
| **Empty** | start-состояние записи: бары серые, «Запись до 5 секунд» |
| **Microphone busy** | Snackbar error «Микрофон занят. Попробуйте позже» → возврат в настройки (UC-06) |
| **No storage** | Snackbar error «Недостаточно свободного места» |
| **Error recording** | `AlertDialog(error)`: «Не удалось записать звук» + «Повторить» / «Отмена» |

### 6.3 Виджет

| State | UI |
|---|---|
| **Loading** | 2 кадра (base PNG), при первом рендере — серый placeholder с лого-уткой |
| **Empty** (приложение удалено) | `GlanceText("Приложение удалено", labelMedium)`, clickable выключен (UC-04) |
| **Error** | виджет перерисовывается по `AppWidgetManager.updateAppWidget` при следующем цикле (UC-04) |

### 6.4 Утка на главном экране (общий Fallback)

| State | UI |
|---|---|
| Ассет утки не найден | Static `Image` (PNG-дубль) с тем же hue-условием |
| Звук недоступен | анимация без звука + иконка volume-off у подписи |

---

## 7. Доступность (TalkBack) — US-10

| Элемент | contentDescription / семантика |
|---|---|
| Утка (главный) | «Утка. Нажмите, чтобы услышать кряк» |
| Каждая карточка кряка | `${quack.name}. Выбрать этот кряк` |
| PlayArrow (предпрослушать) | «Прослушать ${quack.name}» |
| Утка на виджете | «Утка. Нажмите, чтобы услышать кряк» |
| Кнопка записи | «Начать запись» / «Остановить запись» — динамически |
| Таймер | «Осталось 3 секунды» (announce при каждом целочисленном значении) |
| Все IconButton | осмысленные описания; декоративные иконки — `clearAndSetSemantics {}` |
| Segmented Button | роль radio group |
| Цвета | выбор цвета дополнительно озвучивается названием пресета, не только hex |

Для контраста: минимально AA (4.5:1) для текста, у пресетов утки проверяем контраст иконки Check (`contrastOn`).

---

## 8. Доп. варианты использования утки (кандидаты, поверх БС §3)

Рекомендация BA уже в БС; добавляю конкретику для UI-слоя:

| ID | Фича | Первый UI-набросок | Статус |
|---|---|---|---|
| **F-01** | Счётчик «проблем рассказано сегодня» (уже в §3.2) | маленькая текстовая строка под уткой + рост при каждом кряке | V1 (дёшево) |
| **F-02** | Настроение утки по time-of-day / по количеству кряков | ночная анимация (спящая утка), злой-индикатор после 30 кряков | V2 |
| **F-03** | «Кряк-реакция» на длинный тап | long-press 600ms → спец-кряк (злость/радость) в дополнение к простому тапу | V2 |
| **F-04** | Бейдж-статистика в виджете 4×3: «12 багов объяснено» | подпись внизу виджета | V2 |
| **F-05** | «Вы ничего не объясняли уже N часов» — push/напоминание (расширяет BA-03) | `Settings → Напоминания`: NotificationChannel + quick action «Покрякать» | V2 |

Приоритет для V1 — **F-01** (0 строк бэкенда, один Text под уткой).

---

## 9. Допущения и открытые точки

- **Q5 (дизайн утки)** — дан: жёлтая утка, растровый PNG 1536×1024 без прозрачности. Два следствия:
  1. **Требуется вырез с альфой** (для онбординга, виджета, карточек). В макете фон белый — подложка карточек даст белый прямоугольник, если не вырезать.
  2. **Перекраска (US-04) ограничена:** keypath `Body.Color` возможен только после конвертации в Lottie/vector. Fallback для V1 — hue-rotation жёлтой доминанты (пресеты дадут корректные оттенки, «Свой цвет» из color picker — приблизительные). Утка-клюв/тень (`duckShade`) не перекрашивается.
  → Запрос дизайнеру: Lottie/vector с отдельным слоем `Body` + прозрачный фон.
- **Q6 (эталон анимации)** — не назван. Зафиксировано: покачивание + прижатие при тапе (Lottie).
- Система координат M3-токенов Light/Dark приведена к M3 2024 (Dynamic Color на устройствах Android 12+ — `dynamicLightColorScheme`), но primary фикс. (не подстраивается под обои — т.к. утка цветная, сохраняем бренд).
- Виджет Glance анимирует ограниченно (только press-эффект и перерисовка) — полноценная Lottie в виджете невозможна без ExoPlayer-альтернативы; в V1 не делаем.
- Названия токенов (primary/surface и т.д.) — это M3 semantic colors в Compose `Color.kt`, не CSS-классы; точные hex заданы в §1.1.