package com.example.dhansutra.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.dhansutra.ui.theme.DhanSutraTheme

/** Spent-vs-budget bar. [fraction] may exceed 1; the bar is clamped and turns red when over budget. */
@Composable
fun BudgetProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val over = fraction > 1f
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth(),
        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Preview(showBackground = true)
@Composable
private fun BudgetProgressBarPreview() {
    DhanSutraTheme {
        Column(Modifier.padding(16.dp)) {
            BudgetProgressBar(0.4f)
            BudgetProgressBar(1.3f, Modifier.padding(top = 8.dp))
        }
    }
}
