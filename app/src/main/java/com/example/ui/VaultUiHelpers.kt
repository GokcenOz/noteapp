package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

fun categoryIconForKey(key: String): ImageVector =
  when (key) {
    "person" -> Icons.Rounded.Person
    "work" -> Icons.Rounded.Work
    "finance" -> Icons.Rounded.AccountBalanceWallet
    "idea" -> Icons.Rounded.Lightbulb
    "mic" -> Icons.Rounded.Mic
    "lock" -> Icons.Rounded.Lock
    else -> Icons.Rounded.Folder
  }

fun parseHexColor(hex: String, fallback: Color = Color(0xFF10B981)): Color {
  return try {
    val clean = hex.trim().removePrefix("#")
    val value = clean.toLong(16)
    when (clean.length) {
      6 -> Color((0xFF000000L or value).toInt())
      8 -> Color(value.toInt())
      else -> fallback
    }
  } catch (_: Exception) {
    fallback
  }
}

fun formatDurationMs(durationMs: Long): String {
  val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
  val minutes = totalSeconds / 60L
  val seconds = totalSeconds % 60L
  return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

fun formatTimestampShort(timestamp: Timestamp?): String {
  if (timestamp == null) return "Şimdi"
  val date = timestamp.toDate()
  val formatter = SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("tr-TR"))
  return formatter.format(date)
}
