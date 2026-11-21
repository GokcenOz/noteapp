package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.SyncStatus
import com.example.data.VaultConfigDoc

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityCenterSheet(
  userEmail: String?,
  userName: String?,
  keyFingerprint: String,
  encryptedNoteCount: Int,
  voiceMemoCount: Int,
  syncStatus: SyncStatus,
  vaultConfig: VaultConfigDoc?,
  isDarkTheme: Boolean,
  onToggleTheme: (Boolean) -> Unit,
  onSetPin: (pin: String, lockOnLaunch: Boolean) -> Unit,
  onDisablePin: () -> Unit,
  onToggleLockOnLaunch: (Boolean) -> Unit,
  onLockVaultNow: () -> Unit,
  onSignOut: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var showPinSetupDialog by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier =
            Modifier.size(44.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Kasa Güvenlik Merkezi",
            style = MaterialTheme.typography.headlineSmall,
          )
          Text(
            text = userEmail ?: userName ?: "Google Hesabı Bağlı",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Encryption & Sync Telemetry Card
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.EnhancedEncryption,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = "İstemci Taraflı AES-256-GCM",
                style = MaterialTheme.typography.titleSmall,
              )
            }
            Text(
              text = "AKTİF",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = "Anahtar Parmak İzi",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text = keyFingerprint,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.CloudDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = "Bulut Senkronu",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text =
                when (syncStatus) {
                  SyncStatus.SYNCED -> "Güncel ($encryptedNoteCount not, $voiceMemoCount ses)"
                  SyncStatus.SYNCING -> "Senkronize ediliyor…"
                  SyncStatus.OFFLINE_CACHE -> "Çevrimdışı önbellek aktif"
                },
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      // Vault PIN Controls
      val pinEnabled = vaultConfig?.pinEnabled == true
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Kasa PIN Kilidi (4 Haneli)",
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text =
                  if (pinEnabled) "Kasa PIN'i aktif. Anahtarınız PBKDF2 ile bulutta da korunuyor."
                  else "Kasa PIN'i belirleyerek gizli notları kilitleyin ve anahtarınızı yedekleyin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Button(
              onClick = { showPinSetupDialog = true },
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.weight(1f).testTag("setup_pin_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(if (pinEnabled) "PIN Değiştir" else "PIN Belirle")
            }

            if (pinEnabled) {
              OutlinedButton(
                onClick = onDisablePin,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.testTag("disable_pin_button"),
              ) {
                Text("Kaldır")
              }
            }
          }

          if (pinEnabled) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Açılışta Kasayı Kilitle", style = MaterialTheme.typography.bodyMedium)
                Text(
                  "Uygulama açıldığında PIN ister",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = vaultConfig?.lockOnLaunch == true,
                onCheckedChange = onToggleLockOnLaunch,
                modifier = Modifier.testTag("lock_on_launch_switch"),
              )
            }

            OutlinedButton(
              onClick = {
                onDismiss()
                onLockVaultNow()
              },
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.fillMaxWidth().testTag("lock_now_button"),
            ) {
              Icon(imageVector = Icons.Rounded.Lock, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Kasayı Şimdi Kilitle")
            }
          }
        }
      }

      // Theme Toggle
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.DarkMode,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
            )
            Column {
              Text("Koyu Kasa Teması", style = MaterialTheme.typography.titleSmall)
              Text(
                "Gece dostu yüksek kontrastlı görünüm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Switch(
            checked = isDarkTheme,
            onCheckedChange = onToggleTheme,
            modifier = Modifier.testTag("theme_switch"),
          )
        }
      }

      OutlinedButton(
        onClick = {
          onDismiss()
          onSignOut()
        },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sign_out_button"),
      ) {
        Icon(imageVector = Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Güvenli Çıkış Yap")
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }

  if (showPinSetupDialog) {
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var pinErr by remember { mutableStateOf<String?>(null) }

    AlertDialog(
      onDismissRequest = { showPinSetupDialog = false },
      title = { Text("4 Haneli Kasa PIN'i") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            "Bu PIN gizli notlarınızı kilitler ve anahtarınızı cihazlar arası senkronizasyon için şifreler.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedTextField(
            value = pin1,
            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin1 = it },
            label = { Text("Yeni PIN (4 rakam)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().testTag("pin_setup_first_input"),
          )
          OutlinedTextField(
            value = pin2,
            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin2 = it },
            label = { Text("PIN Tekrar") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().testTag("pin_setup_confirm_input"),
          )
          if (pinErr != null) {
            Text(
              text = pinErr!!,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            when {
              pin1.length != 4 -> pinErr = "Lütfen 4 rakam girin."
              pin1 != pin2 -> pinErr = "PIN değerleri eşleşmiyor."
              else -> {
                showPinSetupDialog = false
                onSetPin(pin1, true)
              }
            }
          },
          modifier = Modifier.testTag("pin_setup_save_button"),
        ) {
          Text("Kaydet")
        }
      },
      dismissButton = {
        TextButton(onClick = { showPinSetupDialog = false }) { Text("İptal") }
      },
    )
  }
}

@Composable
fun CreateCategoryDialog(
  onConfirm: (name: String, colorHex: String, iconKey: String) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf("") }
  val colorOptions =
    listOf("#10B981", "#38BDF8", "#F59E0B", "#A78BFA", "#F43F5E", "#14B8A6")
  var selectedColor by remember { mutableStateOf(colorOptions.first()) }
  val iconOptions = listOf("folder", "work", "person", "finance", "idea", "mic", "lock")
  var selectedIcon by remember { mutableStateOf("folder") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Yeni Kategori") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { if (it.length <= 40) name = it },
          label = { Text("Kategori Adı") },
          placeholder = { Text("Örn. Seyahat Planları") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("new_category_name_input"),
        )
        Text("Renk", style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          colorOptions.forEach { hex ->
            val color = parseHexColor(hex)
            val isSelected = selectedColor == hex
            Box(
              modifier =
                Modifier.size(34.dp)
                  .clip(CircleShape)
                  .background(color)
                  .clickable { selectedColor = hex },
              contentAlignment = Alignment.Center,
            ) {
              if (isSelected) {
                Icon(
                  imageVector = Icons.Rounded.Check,
                  contentDescription = null,
                  tint = Color.Black,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }
        Text("Simge", style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          iconOptions.forEach { key ->
            val isSelected = selectedIcon == key
            Surface(
              onClick = { selectedIcon = key },
              shape = CircleShape,
              color =
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.size(40.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = categoryIconForKey(key),
                  contentDescription = key,
                  tint =
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val clean = name.trim()
          if (clean.isNotEmpty()) {
            onConfirm(clean, selectedColor, selectedIcon)
          }
        },
        enabled = name.trim().isNotEmpty(),
        modifier = Modifier.testTag("create_category_confirm_button"),
      ) {
        Text("Oluştur")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("İptal") }
    },
  )
}
