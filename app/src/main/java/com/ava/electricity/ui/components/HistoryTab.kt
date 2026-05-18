package com.ava.electricity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ava.electricity.data.HistoryItem

@Composable
fun HistoryTab(history: List<HistoryItem>, emptyText: String, priceLabel: String, onClearHistory: (() -> Unit)? = null) {
    Column {
        if (history.isEmpty()) {
            Text(emptyText, modifier = Modifier.background(Color(0xFFE0F2FE), RoundedCornerShape(14.dp)).padding(14.dp))
        } else {
            if (onClearHistory != null) {
                Button(onClick = onClearHistory, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Text("Clear history")
                }
            }
            history.forEach { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(item.deviceName, fontWeight = FontWeight.Bold)
                        Text(item.timeSchedule, fontSize = 13.sp)
                        Text("$priceLabel: EUR ${"%.2f".format(item.price)}")
                    }
                }
            }
        }
    }
}

