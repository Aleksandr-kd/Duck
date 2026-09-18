-- =====================================================================================
-- Миграция 001: справочные таблицы «Утка Разраба»
-- Тип: REFERENCE-скрипт для backend/аналитики.
-- ВАЖНО: V1 приложения полностью offline: каталог звуков хранится в
-- assets/sound_catalog.json, настройки пользователя — в DataStore Preferences,
-- запись пользователя — файл .wav в filesDir. Таблицы ниже описывают модель данных
-- для серверной синхронизации на будущее (V2+).
-- =====================================================================================

-- Каталог звуковых пресетов (соответствует assets/sound_catalog.json).
CREATE TABLE IF NOT EXISTS sound_catalog (
    id         TEXT PRIMARY KEY,          -- 'classic', 'kva', ...
    name       TEXT NOT NULL,             -- человекочитаемое название
    license    TEXT NOT NULL,             -- 'CC BY-SA 4.0' и пр.
    file_path  TEXT NOT NULL,             -- имя файла в assets
    is_default INTEGER NOT NULL DEFAULT 0,  -- 1 = классический кряк
    enabled    INTEGER NOT NULL DEFAULT 1,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Настройки пользователя (параметр/значение — расширяемая структура).
CREATE TABLE IF NOT EXISTS app_settings (
    param  TEXT PRIMARY KEY,              -- 'theme', 'active_quack_id', 'quack_mode',
                                          -- 'duck_color', 'vibrate_on_quack',
                                          -- 'onboarding_completed'
    value  TEXT NOT NULL
);

-- Записи-кряки пользователя (V1: единственный активный файл custom_quack.wav).
CREATE TABLE IF NOT EXISTS custom_quacks (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    file_path   TEXT NOT NULL,
    duration_ms INTEGER NOT NULL,
    is_active   INTEGER NOT NULL DEFAULT 1,
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    deleted_at  TEXT
);

-- Суточная статистика кряков (touchDailyCounter).
CREATE TABLE IF NOT EXISTS daily_stats (
    date        TEXT PRIMARY KEY,         -- 'YYYY-MM-DD'
    quack_count INTEGER NOT NULL DEFAULT 1,
    first_at    TEXT,
    last_at     TEXT,
    updated_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Индексы
CREATE INDEX IF NOT EXISTS idx_custom_deleted ON custom_quacks (is_active, deleted_at);
CREATE INDEX IF NOT EXISTS idx_stats_date      ON daily_stats (date DESC);