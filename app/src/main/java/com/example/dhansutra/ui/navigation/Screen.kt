package com.example.dhansutra.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(val route: String, val label: String, val icon: ImageVector) {
    Dashboard("dashboard", "Dashboard", Icons.Rounded.Dashboard),
    Planning("planning", "Planning", Icons.Rounded.CalendarMonth),
    Transactions("transactions", "Transactions", Icons.Rounded.ReceiptLong),
    Analytics("analytics", "Analytics", Icons.Rounded.PieChart),
}
