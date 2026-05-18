package com.ava.electricity.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PriceCurve(prices: List<Double>, title: String, subtitle: String) {
    var selectedBar by remember(prices) { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF7FFFFFF), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF064E3B))
        Text(subtitle, color = Color(0xFF64748B))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("â†‘ Price", color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(top = 8.dp)
                .pointerInput(prices) {
                    detectTapGestures { offset ->
                        if (prices.isEmpty()) return@detectTapGestures
                        val gap = 4.dp.toPx()
                        val barWidth = (size.width - gap * (prices.size - 1)) / prices.size
                        val index = (offset.x / (barWidth + gap)).toInt()
                        selectedBar = index.takeIf { it in prices.indices }
                    }
                }
        ) {
            val maxPrice = prices.maxOrNull() ?: 1.0
            val minPrice = prices.minOrNull() ?: 0.0
            val range = (maxPrice - minPrice).takeIf { it > 0.0 } ?: 1.0
            val gap = 4.dp.toPx()
            val barWidth = (size.width - gap * (prices.size - 1)) / prices.size
            val baseline = size.height

            drawLine(Color(0xFFE2E8F0), Offset(0f, size.height * .33f), Offset(size.width, size.height * .33f), strokeWidth = 2f)
            drawLine(Color(0xFFE2E8F0), Offset(0f, size.height * .66f), Offset(size.width, size.height * .66f), strokeWidth = 2f)

            var selectedLabel: Triple<String, Float, Float>? = null
            prices.forEachIndexed { index, price ->
                val ratio = ((price - minPrice) / range).toFloat()
                val barHeight = (size.height * (0.18f + ratio * 0.82f)).coerceAtLeast(8.dp.toPx())
                val x = index * (barWidth + gap)
                val y = baseline - barHeight
                val color = when {
                    price <= 0.10 -> Color(0xFF10B981)
                    price < 0.19 -> Color(0xFFFBBF24)
                    else -> Color(0xFFEF4444)
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                )
                if (selectedBar == index) {
                    drawRoundRect(
                        color = Color(0xFF0F172A),
                        topLeft = Offset(x - 1.dp.toPx(), y - 1.dp.toPx()),
                        size = Size(barWidth + 2.dp.toPx(), barHeight + 2.dp.toPx()),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        alpha = 0.18f
                    )
                    selectedLabel = Triple(
                        "${index.toString().padStart(2, '0')}:00  EUR ${"%.3f".format(price)}",
                        (x + barWidth / 2).coerceIn(54.dp.toPx(), size.width - 54.dp.toPx()),
                        (y - 10.dp.toPx()).coerceAtLeast(18.dp.toPx())
                    )
                }
            }

            selectedLabel?.let { (label, centerX, centerY) ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(15, 23, 42)
                    textSize = 12.sp.toPx()
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }
                val width = paint.measureText(label) + 18.dp.toPx()
                val height = 26.dp.toPx()
                drawRoundRect(
                    color = Color(0xFFFFFFFF),
                    topLeft = Offset(centerX - width / 2, centerY - height),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                )
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(label, centerX, centerY - 8.dp.toPx(), paint)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("00:00", color = Color(0xFF64748B))
            Text("06:00", color = Color(0xFF64748B))
            Text("12:00", color = Color(0xFF64748B))
            Text("18:00", color = Color(0xFF64748B))
            Text("23:00", color = Color(0xFF64748B))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("Time â†’", color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
        }
    }
}

