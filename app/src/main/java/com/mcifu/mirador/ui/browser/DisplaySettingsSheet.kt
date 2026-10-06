package com.mcifu.mirador.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mcifu.mirador.domain.model.BrowserSettings
import com.mcifu.mirador.domain.model.GridLayout
import com.mcifu.mirador.domain.model.SortField
import com.mcifu.mirador.domain.model.ThumbnailSize
import com.mcifu.mirador.domain.model.ViewMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DisplaySettingsSheet(
    settings: BrowserSettings,
    onChange: ((BrowserSettings) -> BrowserSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Visualización", style = MaterialTheme.typography.titleLarge)

            SectionTitle("Vista")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val modes = listOf(ViewMode.LIST to "Lista", ViewMode.GRID to "Cuadrícula")
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = settings.viewMode == mode,
                        onClick = { onChange { it.copy(viewMode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        icon = {
                            Icon(
                                if (mode == ViewMode.LIST) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                                contentDescription = null,
                            )
                        },
                    ) { Text(label) }
                }
            }

            val gridEnabled = settings.viewMode == ViewMode.GRID
            SectionTitle("Columnas")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf(GridLayout.AUTO) + GridLayout.COLUMN_OPTIONS).forEach { value ->
                    FilterChip(
                        selected = settings.columns == value,
                        enabled = gridEnabled,
                        onClick = { onChange { it.copy(columns = value) } },
                        label = { Text(if (value == GridLayout.AUTO) "Auto" else value.toString()) },
                    )
                }
            }

            val sizeEnabled = gridEnabled && settings.columns == GridLayout.AUTO
            SectionTitle(
                if (sizeEnabled || !gridEnabled) "Tamaño de miniaturas"
                else "Tamaño de miniaturas · lo fijan las columnas",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThumbnailSize.entries.forEach { size ->
                    FilterChip(
                        selected = settings.thumbnailSize == size,
                        enabled = sizeEnabled,
                        onClick = { onChange { it.copy(thumbnailSize = size) } },
                        label = { Text(size.label()) },
                    )
                }
            }

            SectionTitle("Ordenar por")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortField.entries.forEach { field ->
                    val selected = settings.sortOrder.field == field
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onChange {
                                // Pulsar el criterio activo invierte el sentido.
                                val order = it.sortOrder
                                it.copy(
                                    sortOrder = if (order.field == field) order.copy(ascending = !order.ascending)
                                    else order.copy(field = field, ascending = field == SortField.NAME || field == SortField.TYPE),
                                )
                            }
                        },
                        label = { Text(field.label()) },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    if (settings.sortOrder.ascending) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                                    contentDescription = if (settings.sortOrder.ascending) "Ascendente" else "Descendente",
                                )
                            }
                        } else null,
                    )
                }
            }

            SwitchRow(
                label = "Carpetas primero",
                checked = settings.sortOrder.foldersFirst,
                onCheckedChange = { value -> onChange { it.copy(sortOrder = it.sortOrder.copy(foldersFirst = value)) } },
            )
            SwitchRow(
                label = "Mostrar archivos ocultos y de sistema",
                checked = settings.showHidden,
                onCheckedChange = { value -> onChange { it.copy(showHidden = value) } },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun ThumbnailSize.label() = when (this) {
    ThumbnailSize.SMALL -> "Pequeñas"
    ThumbnailSize.MEDIUM -> "Medianas"
    ThumbnailSize.LARGE -> "Grandes"
    ThumbnailSize.EXTRA_LARGE -> "Muy grandes"
}

private fun SortField.label() = when (this) {
    SortField.NAME -> "Nombre"
    SortField.DATE -> "Fecha"
    SortField.SIZE -> "Tamaño"
    SortField.TYPE -> "Tipo"
}
