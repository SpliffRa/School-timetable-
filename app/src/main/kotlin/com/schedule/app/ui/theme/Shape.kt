package com.schedule.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val ScheduleShapes = Shapes(
    // Кнопки, бейджи, чипы → 12dp
    small  = RoundedCornerShape(12.dp),
    // Карточки уроков, баннеры → 20dp
    medium = RoundedCornerShape(20.dp),
    // Оверлеи, диалоги, ModalBottomSheet → 24dp
    large  = RoundedCornerShape(24.dp)
)
