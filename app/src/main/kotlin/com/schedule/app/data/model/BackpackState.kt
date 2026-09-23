package com.schedule.app.data.model

import kotlinx.serialization.Serializable

/**
 * Состояние чек-листа собранных в рюкзак предметов.
 * Синхронизируется между устройством ребёнка и родителя.
 *
 * @property checkedItems Множество ключей собранных вещей вида "YYYY-MM-DD_номерУрока_название"
 * @property lastUpdated Временная метка последнего изменения (System.currentTimeMillis())
 */
@Serializable
data class BackpackState(
    val checkedItems: Set<String> = emptySet(),
    val lastUpdated: Long = 0L
)
