package com.duck.app.domain.model

/**
 * Глобальная таблица кодов ошибок (ТЗ §2.8). Локализация: strings.xml, key = `error_<name>`.
 */
enum class DuckErrorCode(val intValue: Int, val httpAnalog: Int) {
    E_AUDIO_001(1001, 500),
    E_AUDIO_002(1002, 500),
    E_AUDIO_003(1003, 500),
    E_AUDIO_004(1004, 404),
    E_ANIM_001(2001, 500),
    E_CATALOG_001(3001, 404),
    E_CATALOG_002(3002, 204),
    E_CATALOG_003(3003, 422),
    E_REC_001(4001, 500),
    E_REC_002(4002, 409),
    E_REC_003(4003, 422),
    E_REC_004(4004, 413),
    E_STORAGE_001(5001, 507),
    E_PERM_001(6001, 403),
    E_COLOR_001(7001, 404),
    E_COLOR_002(7002, 422),
    E_COLOR_003(7003, 501),
    E_WIDGET_001(8001, 500),
    E_WIDGET_002(8002, 410),
    E_WIDGET_003(8003, 500),
    E_DATASTORE_001(9001, 500),
    E_RECORD_PLAYBACK_001(9002, 500);

    companion object {
        fun fromInt(value: Int?): DuckErrorCode? = entries.find { it.intValue == value }
    }
}