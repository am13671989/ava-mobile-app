package com.ava.electricity.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TopHomeButton(text: String, onHome: () -> Unit) {
    Button(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Home, contentDescription = text)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}
