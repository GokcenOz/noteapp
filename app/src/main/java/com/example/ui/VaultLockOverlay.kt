package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun VaultPinGate(
  title: String,
  subtitle: String,
  errorMessage: String?,
  allowDismiss: Boolean,
  onPinEntered: (String) -> Boolean,
  onDismiss: () -> Unit = {},
  onSignOutFallback: (() -> Unit)? = null,
) {
  var digits by remember { mutableStateOf("") }

  if (allowDismiss) {
    BackHandler { onDismiss() }
  }

  Surface(
    color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
    modifier = Modifier.fillMaxSize(),
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(24.dp)
          .widthIn(max = 420.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Box(
        modifier =
          Modifier.size(68.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Rounded.Lock,
          contentDescription = "Kasa kilitli",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(32.dp),
        )
      }

      Spacer(modifier = Modifier.height(18.dp))

      Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(24.dp))

      // 4 PIN Dots
      Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        repeat(4) { index ->
          val filled = index < digits.length
          Surface(
            modifier = Modifier.size(18.dp),
            shape = CircleShape,
            color =
              if (filled) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.surfaceVariant,
            border =
              BorderStroke(
                1.5.dp,
                if (filled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
              ),
          ) {}
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      if (errorMessage != null) {
        Text(
          text = errorMessage,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          textAlign = TextAlign.Center,
        )
      } else {
        Spacer(modifier = Modifier.height(16.dp))
      }

      Spacer(modifier = Modifier.height(18.dp))

      val keypadRows =
        listOf(
          listOf("1", "2", "3"),
          listOf("4", "5", "6"),
          listOf("7", "8", "9"),
          listOf("", "0", "DEL"),
        )

      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in keypadRows) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            for (key in row) {
              if (key.isEmpty()) {
                Spacer(modifier = Modifier.size(72.dp))
              } else if (key == "DEL") {
                Surface(
                  onClick = {
                    if (digits.isNotEmpty()) digits = digits.dropLast(1)
                  },
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  modifier = Modifier.size(72.dp).testTag("pin_backspace"),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Rounded.Backspace,
                      contentDescription = "Sil",
                      tint = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                }
              } else {
                Surface(
                  onClick = {
                    if (digits.length < 4) {
                      val next = digits + key
                      digits = next
                      if (next.length == 4) {
                        val success = onPinEntered(next)
                        if (!success) digits = ""
                      }
                    }
                  },
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                  modifier = Modifier.size(72.dp).testTag("pin_key_$key"),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Text(
                      text = key,
                      style = MaterialTheme.typography.headlineSmall,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (allowDismiss) {
          TextButton(onClick = onDismiss, modifier = Modifier.testTag("pin_cancel_button")) {
            Text("İptal")
          }
        }
        if (onSignOutFallback != null) {
          TextButton(onClick = onSignOutFallback, modifier = Modifier.testTag("pin_signout_button")) {
            Text("Farklı Hesapla Giriş")
          }
        }
      }
    }
  }
}
