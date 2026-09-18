package com.schedule.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val ScheduleShapes = Shapes(
    // Кнопки, чипы, поля ввода → 10dp
    small  = RoundedCornerShape(10.dp),
    // Карточки уроков → 16dp
    medium = RoundedCornerShape(16.dp),
    // ModalBottomSheet → 20dp
    large  = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
)
