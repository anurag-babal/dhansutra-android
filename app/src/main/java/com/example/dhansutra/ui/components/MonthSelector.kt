package com.example.dhansutra.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.dhansutra.ui.theme.DhanSutraTheme
import com.example.dhansutra.util.MonthYear
import java.time.YearMonth

@Composable
fun MonthSelector(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    /** Latest month the user may navigate to; defaults to the current month. */
    maxMonth: YearMonth = MonthYear.current(),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(MonthYear.label(month), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onMonthChange(month.plusMonths(1)) }, enabled = month.isBefore(maxMonth)) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MonthSelectorPreview() {
    DhanSutraTheme { MonthSelector(month = YearMonth.of(2026, 10), onMonthChange = {}) }
}
