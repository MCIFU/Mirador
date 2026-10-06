package com.mcifu.mirador.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.SingletonImageLoader
import com.mcifu.mirador.domain.LoopMode
import com.mcifu.mirador.domain.model.ThemeMode
import com.mcifu.mirador.domain.model.ViewerScope
import com.mcifu.mirador.ui.common.Formatters

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDiagnostics: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val viewer by viewModel.viewer.collectAsStateWithLifecycle()
    val browser by viewModel.browser.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val loader = remember { SingletonImageLoader.get(context) }
    var cacheBytes by remember { mutableLongStateOf(loader.diskCache?.size ?: 0L) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section("Apariencia")
            Label("Tema")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Oscuro").forEach { (mode, label) ->
                    FilterChip(selected = appearance.themeMode == mode, onClick = { viewModel.updateAppearance { it.copy(themeMode = mode) } }, label = { Text(label) })
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow("Colores del fondo de pantalla (Material You)", appearance.dynamicColor) { v ->
                    viewModel.updateAppearance { it.copy(dynamicColor = v) }
                }
            }

            Section("Visor y reproductor")
            Label("Qué recorrer al deslizar")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ViewerScope.IMAGES to "Solo imágenes",
                    ViewerScope.VISUAL_MEDIA to "Fotos y vídeos",
                    ViewerScope.VIDEOS to "Solo vídeos",
                    ViewerScope.ALL_MEDIA to "Todo lo multimedia",
                    ViewerScope.ALL_FILES to "Todos los archivos",
                ).forEach { (scope, label) ->
                    FilterChip(selected = viewer.scope == scope, onClick = { viewModel.updateViewer { it.copy(scope = scope) } }, label = { Text(label) })
                }
            }
            Label("Al terminar un vídeo")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(LoopMode.REPEAT_ONE to "Repetir vídeo", LoopMode.PLAY_ONCE to "Parar", LoopMode.REPEAT_FOLDER to "Siguiente de la carpeta").forEach { (mode, label) ->
                    FilterChip(selected = viewer.loopMode == mode, onClick = { viewModel.updateViewer { it.copy(loopMode = mode) } }, label = { Text(label) })
                }
            }
            SwitchRow("Tira de miniaturas en el visor", viewer.showFilmstrip) { v -> viewModel.updateViewer { it.copy(showFilmstrip = v) } }

            Section("Explorador")
            SwitchRow("Mostrar archivos ocultos y de sistema", browser.showHidden) { v -> viewModel.updateBrowser { it.copy(showHidden = v) } }
            SwitchRow("Carpetas primero", browser.sortOrder.foldersFirst) { v ->
                viewModel.updateBrowser { it.copy(sortOrder = it.sortOrder.copy(foldersFirst = v)) }
            }

            Section("Almacenamiento")
            Text(
                "Caché de miniaturas: ${Formatters.size(context, cacheBytes)} (máx. 256 MB). Evita volver a leer del USB las miniaturas ya generadas.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = {
                loader.diskCache?.clear()
                loader.memoryCache?.clear()
                cacheBytes = loader.diskCache?.size ?: 0L
            }) { Text("Borrar caché de miniaturas") }

            Section("Acerca de")
            Text("Mirador $version", style = MaterialTheme.typography.bodyLarge)
            Text("Sin anuncios, sin cuentas y sin conexión a internet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Informe de diagnóstico",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onDiagnostics).padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 12.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelLarge)

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
