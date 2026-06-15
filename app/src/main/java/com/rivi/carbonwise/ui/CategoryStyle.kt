package com.rivi.carbonwise.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.rivi.carbonwise.domain.Category
import com.rivi.carbonwise.ui.theme.Electricity
import com.rivi.carbonwise.ui.theme.Food
import com.rivi.carbonwise.ui.theme.Home as HomeColor
import com.rivi.carbonwise.ui.theme.Transport

data class CategoryStyle(val color: Color, val icon: ImageVector, val label: String)

fun Category.style(): CategoryStyle = when (this) {
    Category.TRANSPORT -> CategoryStyle(Transport, Icons.Filled.DirectionsCar, label)
    Category.FOOD -> CategoryStyle(Food, Icons.Filled.Restaurant, label)
    Category.ELECTRICITY -> CategoryStyle(Electricity, Icons.Filled.Bolt, label)
    Category.HOME -> CategoryStyle(HomeColor, Icons.Filled.Home, label)
}

/** Compact number formatting used across the UI (no trailing ".0"). */
fun formatKg(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format("%.1f", value)

fun formatQty(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format("%.1f", value)
