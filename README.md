# USBX

Explorador y visor multimedia para memorias USB conectadas por USB‑C / OTG en Android.
Dispositivo de referencia: **Samsung Galaxy M52 5G** (Android 14 / One UI 6), pero solo usa APIs
públicas de Android: no hay código específico de Samsung.

> **Estado: Fase 2 (visor avanzado).** Lo que no está en la tabla de abajo como «Hecho» no está
> implementado, y la app no lo aparenta.

| Área | Estado |
|---|---|
| Detección de memorias USB, permiso, desconexión y reconexión | Hecho (Fase 1) |
| Explorador: carpetas, lista, cuadrícula, miniaturas, orden, ocultos | Hecho (Fase 1) |
| Abrir, abrir con…, información y compartir | Hecho (Fase 1) |
| Visor básico: pantalla completa, deslizar, precarga | Hecho (Fase 1) |
| Zoom nítido, doble toque, rotación, orientación, transiciones, tira de miniaturas, GIF | Hecho (Fase 2) |
| Reproductor de vídeo integrado (hoy los vídeos se abren con otra app) | Fase 3 |
| Multiview (2 fotos o 2 vídeos) | Fase 4 |
| Copiar, mover, borrar, renombrar, selección múltiple | Fase 5 |
| Ajustes, caché avanzada, accesibilidad, diseño final | Fase 6 |

## Instalar en el móvil

### Opción A: APK compilado por GitHub (sin instalar nada en el PC)

1. En GitHub, abre la pestaña **Actions** → workflow **Android CI** → la ejecución más reciente
   con ✔ de la rama que quieras probar.
2. Abajo, en **Artifacts**, descarga `usbx-debug-apk` (es un .zip que contiene el .apk).
3. Pasa el `.apk` al móvil y ábrelo con «Mis archivos». La primera vez Android pedirá permitir
   «Instalar apps desconocidas» para esa app.

El APK de depuración se instala como **USBX** con id `com.mcifu.usbx.debug`, y se puede
actualizar encima de una versión anterior sin desinstalar (el repositorio incluye una clave de
depuración fija; no sirve para publicar en Google Play).

### Opción B: compilar en el PC

Requisitos: JDK 17 o superior (probado con 21) y Android SDK con la plataforma 37 (Android Studio
la descarga sola).

```bash
./gradlew assembleDebug                  # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest lintDebug    # tests unitarios y Android Lint
```

## Tecnología

Versiones estables comprobadas en los repositorios oficiales (septiembre de 2026):

| Componente | Versión | Motivo |
|---|---|---|
| Gradle | 9.8.0 | Última estable |
| Android Gradle Plugin | 9.4.1 | Última estable; Kotlin integrado (sin plugin `kotlin-android`) |
| Kotlin | 2.4.20 | Última estable; compilador de Compose incluido |
| compileSdk / targetSdk | 37 / 37 | Android 17. En el M52 (Android 14) no activa cambios de comportamiento nuevos |
| minSdk | 26 | Android 8.0: iconos adaptativos, `java.time`, cubre prácticamente todos los móviles en uso |
| Compose BOM | 2026.09.00 | Material 3 |
| Navigation Compose | 2.10.2 | Rutas tipadas con kotlinx.serialization |
| Coil (+ coil-gif) | 3.6.3 | Carga de imágenes con cancelación, caché en memoria y disco; GIF animados |
| Telephoto (zoomable-image-coil3) | 0.19.0 | Zoom con subsampling: al ampliar carga teselas de la foto original |
| DataStore | 1.2.1 | Ajustes persistentes |
| ExifInterface | 1.4.2 | Miniaturas EXIF, orientación y metadatos |
| Media3 (ExoPlayer) | — | Se añadirá en la Fase 3 (reproductor). Hoy no hace falta |

Sin Hilt/Dagger (el grafo de dependencias cabe en `AppContainer`), sin Firebase, sin
analítica y sin acceso a red. La app no declara permisos de almacenamiento, de red ni ningún otro
que el usuario tenga que conceder.

## Cómo accede USBX a la memoria USB

Se evaluaron tres opciones:

1. **`MANAGE_EXTERNAL_STORAGE` («acceso a todos los archivos») + rutas `/storage/XXXX-XXXX`.**
   Descartada: es un permiso especial que Google Play solo acepta para gestores de archivos
   completos, da acceso a todo el móvil y va contra el requisito de no usar permisos innecesarios.
2. **Acceso USB directo (`UsbManager` + libaums).** Descartada: salta el montaje del sistema,
   solo entiende FAT32, es más lento, entra en conflicto con Android si este ya montó la
   memoria y no es una API oficial de almacenamiento.
3. **Storage Access Framework (elegida).** Android monta el pendrive (FAT32/exFAT) y USBX pide
   permiso sobre la raíz con `StorageVolume.createOpenDocumentTreeIntent()`: el selector del
   sistema se abre directamente en la memoria y basta con pulsar «Usar esta carpeta». El
   permiso se guarda (`takePersistableUriPermission`) y se reconoce cuando vuelves a conectar
   la misma memoria, porque el UUID del volumen forma parte del URI.

Detección: `StorageManager.getStorageVolumes()` + broadcasts `ACTION_MEDIA_*` +
`StorageVolumeCallback` (Android 11+). Con todo ello la desconexión se detecta en cuanto Android
desmonta el volumen.

Las carpetas se leen con **una sola consulta** a `DocumentsContract` por carpeta. `DocumentFile`
(la alternativa habitual) hace una consulta extra por archivo, lo que con miles de fotos supone
segundos de espera.

## Miniaturas y rendimiento

- `LazyVerticalGrid` / `LazyColumn`: solo existen en memoria las celdas visibles (más unas pocas
  precargadas).
- Fotos: primero la **miniatura EXIF incrustada** (unos KB al principio del archivo; las cámaras
  Samsung guardan 512×384). Si no existe o es pequeña, decodificación **submuestreada**
  (`ImageDecoder` / `inSampleSize`), que nunca carga la foto completa en memoria.
- Vídeos: fotograma clave cercano al segundo 1 (`MediaMetadataRetriever.getScaledFrameAtTime`).
- Audio: carátula incrustada.
- Caché en memoria (25 % de la memoria de la app) y **en disco** (256 MB en la caché de la app):
  al volver a conectar el mismo pendrive, las miniaturas ya generadas no se vuelven a leer del USB.
- Generación limitada a 3 en paralelo: el bus USB atiende las lecturas de una en una y más
  paralelismo solo retrasa las miniaturas que estás viendo.
- Coil cancela la carga cuando una celda sale de pantalla (scroll rápido).
- Ordenación y filtrado en `Dispatchers.Default`, E/S en `Dispatchers.IO`; nada bloquea la interfaz.
- Barra de desplazamiento rápido para carpetas con cientos o miles de elementos.
- Visor: la página anterior y la siguiente se componen por adelantado y se decodifican en caché
  ±2 imágenes al tamaño de pantalla, así que pasar de foto no muestra pantalla de carga.
- Zoom sin cargar la foto entera: a tamaño de pantalla se usa una versión reducida y, al ampliar,
  Telephoto decodifica solo las teselas visibles de la foto original (`BitmapRegionDecoder`).
  Una foto de 50 MP ocuparía ~200 MB en memoria; así nunca se carga completa.
- Mientras llega la foto, el visor muestra la miniatura que ya está en memoria: la transición
  desde la cuadrícula nunca pasa por una pantalla negra.

## Arquitectura

```
UI (Compose)  →  ViewModel (StateFlow)  →  Dominio (modelos y reglas puras)  →  Datos  →  Android (SAF, StorageManager)
```

```
app/src/main/java/com/mcifu/usbx/
├── di/AppContainer.kt            Inyección de dependencias manual e ImageLoader de Coil
├── domain/
│   ├── model/                    UsbStorage, FileItem, FileType, FileTypeRules, BrowserSettings,
│   │                             GridLayout, SortOrder, FileDetails, StorageException
│   ├── repository/               Interfaces: StorageRepository, FileRepository,
│   │                             FileDetailsRepository, SettingsRepository
│   ├── FileSorter.kt             Orden natural (IMG_2 antes que IMG_10)
│   ├── MediaNavigation.kt        Secuencia del visor según ámbito (imágenes, vídeos, multimedia)
│   └── StorageConnection.kt      Transiciones conectado/desconectado
├── data/
│   ├── storage/                  SAF, StorageManager, lectura de carpetas y metadatos
│   ├── thumbnail/                Generador de miniaturas + Fetcher y caché para Coil
│   └── settings/                 DataStore
└── ui/
    ├── home/                     Memorias detectadas, permiso, carpeta manual
    ├── browser/                  Lista, cuadrícula, ajustes de visualización, acciones
    ├── viewer/                   Visor: páginas (imagen/vídeo/audio/archivo), controles, efectos
    ├── common/                   Diálogos, abrir/compartir, formatos, scroll rápido
    ├── navigation/               Rutas tipadas y NavHost
    └── theme/                    Material 3 con paleta propia, claro y oscuro
```

Preparado para las siguientes fases:

- El visor ya recorre vídeos (según «qué recorrer al deslizar»): hoy muestran su portada y se
  abren con el reproductor instalado; en la Fase 3 esa página pasará a ser el reproductor integrado.
- `OrientationMode` (auto / vertical / horizontal) ya se guarda y lo reutilizará el reproductor.
- `ExternalActions.share` ya acepta varios archivos (para la selección múltiple de la Fase 5).
- El permiso del árbol se toma con lectura **y escritura**, así que copiar, mover y renombrar
  (Fase 5) no exigirán volver a autorizar la memoria.

## Guía de pruebas de la Fase 1 en el Galaxy M52

Necesitas: el M52, un pendrive USB‑C (o USB‑A con adaptador OTG) formateado en FAT32 o exFAT, con
alguna carpeta con muchas fotos (idealmente cientos), algún vídeo, un MP3 y un PDF.

| # | Prueba | Cómo hacerla | Resultado esperado |
|---|---|---|---|
| 1 | Compilar | Opción A o B de arriba | APK generado sin errores |
| 2 | Instalar | Abrir el APK en el móvil | Aparece el icono USBX (pendrive turquesa) |
| 3 | Conectar USB | Con USBX abierto, enchufa el pendrive | En 1–3 s aparece su tarjeta: «Conectada · falta conceder acceso», con espacio libre |
| 4 | Abrir USBX | Si lo conectaste antes de abrir la app, abre USBX | La memoria aparece igual |
| 5 | Conceder permiso | «Conceder acceso» → en el selector pulsa «Usar esta carpeta» → «Permitir» | Se abre el explorador en la raíz del pendrive |
| 5b | Permiso rechazado | Repite pulsando Atrás en el selector | Aviso «Permiso rechazado…» con «Reintentar»; la app sigue funcionando |
| 6 | Navegar | Entra en carpetas; usa Atrás del sistema y la ruta superior (toca un nivel intermedio) | Cada nivel conserva su posición de scroll; la ruta salta al nivel pulsado |
| 7 | Lista / cuadrícula | Icono junto a los ajustes, arriba a la derecha | Cambia al instante y se recuerda al reiniciar la app |
| 8 | Tamaño de miniaturas y columnas | Icono de ajustes (deslizadores) → Columnas «Auto» + Pequeñas/Medianas/Grandes/Muy grandes; luego columnas 2–6 | La cuadrícula se recalcula; con columnas fijas el tamaño queda desactivado (explicado en el panel) |
| 8b | Carpeta enorme | Abre la carpeta con cientos de fotos y haz scroll rápido; arrastra la barra del borde derecho | Scroll fluido; las miniaturas aparecen al pararse; la segunda visita es casi instantánea |
| 8c | Girar el móvil | Con la cuadrícula abierta, gira a horizontal | Más columnas, sin perder la posición |
| 9 | Abrir fotografía | Toca una foto | Visor a pantalla completa con nombre y «25 / 340» |
| 10 | Pasar a la siguiente | Desliza izquierda/derecha; toca para ocultar/mostrar controles | Cambio sin pantalla de carga; al volver, el explorador muestra la última foto vista |
| 11 | Miniaturas de vídeo | Carpeta con vídeos | Fotograma del vídeo con icono ▶ |
| 12 | Abrir vídeo / audio / PDF | Tócalos | Se abren con la app que tengas (Samsung Video, VLC, lector PDF…) |
| 13 | Abrir con… | ⋮ (lista) o pulsación larga (cuadrícula) → «Abrir con…» | Selector de apps de Android |
| 14 | Archivo sin app | Toca un archivo raro (p. ej. `.xyz`) | «No se puede abrir este archivo directamente» con «Abrir con otra aplicación» si hay alguna |
| 15 | Información | ⋮ → «Información» en una foto, un vídeo y un MP3 | Nombre, tipo, tamaño, fecha, ruta, dimensiones / duración / cámara / artista |
| 16 | Compartir | ⋮ → «Compartir» → p. ej. Quick Share o WhatsApp | La app destino recibe el archivo |
| 17 | Modo oscuro | Ajustes de Android → Pantalla → Modo oscuro | La app cambia de tema; el visor siempre es negro |
| 18 | Desconectar USB | Con el explorador o el visor abiertos, desenchufa el pendrive | Al instante: «La memoria USB se ha desconectado» con «Ir al inicio». Sin cierres |
| 19 | Reconectar | Vuelve a enchufarlo sin salir de esa pantalla | Se recarga solo («Memoria USB reconectada») en la misma carpeta o foto, sin pedir permiso otra vez |
| 20 | Pendrive distinto | Conecta otra memoria | Aparece con «falta conceder acceso» (cada memoria se autoriza una vez) |

## Guía de pruebas de la Fase 2 (visor)

Usa una carpeta con fotos grandes (idealmente de 12 MP o más), algún vídeo, un GIF animado y,
si puedes, una foto HEIC.

| # | Prueba | Cómo hacerla | Resultado esperado |
|---|---|---|---|
| 1 | Transición | En la cuadrícula, toca una foto; luego pulsa Atrás | La foto crece desde su celda hasta pantalla completa y vuelve a su celda al cerrar |
| 2 | Pellizcar | Pellizca para ampliar hasta ver detalle fino | Se amplía de forma continua; tras un instante la imagen se vuelve nítida (teselas de la foto original) |
| 3 | Doble toque | Doble toque sobre un punto; otro doble toque | Amplía 2,5× alrededor del dedo; el segundo vuelve a encajar |
| 4 | Arrastrar ampliada | Con la foto ampliada, arrastra en todas direcciones | Se mueve la foto, no cambia de página hasta llegar al borde |
| 5 | Pasar de foto | Sin zoom, desliza rápido varias fotos seguidas | Sin pantallas de carga; al volver a una foto ampliada aparece encajada |
| 6 | Controles | Toca la foto; amplía con los controles visibles | El toque los muestra/oculta; al ampliar se ocultan solos |
| 7 | Girar | Botones de girar abajo | La foto gira 90° con animación y sigue ocupando la pantalla; no se modifica el archivo |
| 8 | Orientación | Botón «Auto / Vertical / Horizontal» abajo; con «Horizontal» sal y vuelve a entrar | Con Horizontal el móvil pasa a apaisado aunque la rotación automática esté desactivada; se recuerda; al volver al explorador vuelve a lo normal |
| 9 | Tira de miniaturas | Toca una miniatura lejana de la tira | Salta a ese elemento; la miniatura actual queda centrada y resaltada. Se puede ocultar en ⋮ |
| 10 | Qué recorrer | ⋮ → «Qué recorrer al deslizar…» → prueba «Solo imágenes» y «Fotos y vídeos» | La lista cambia sin perder la foto actual; con vídeos aparece su portada con ▶ |
| 11 | Vídeo desde el visor | En la portada de un vídeo pulsa ▶ | Se abre con el reproductor instalado (el integrado llega en la Fase 3) |
| 12 | GIF | Abre un GIF animado | Se anima en el visor (en la cuadrícula se ve fijo) |
| 13 | HEIC | Abre una foto HEIC | Se muestra y amplía igual que un JPG |
| 14 | Información y compartir | Botones ⓘ y compartir arriba | Igual que en la Fase 1, sobre el elemento visible |
| 15 | Desconectar en el visor | Con una foto ampliada, desenchufa el pendrive; vuelve a enchufarlo | Aviso de desconexión; al reconectar vuelve a la misma foto |

Las pruebas de tu lista original sobre velocidad, barra de progreso, avance/retroceso rápido,
bloqueo de controles y repetición de vídeo corresponden a la Fase 3 y todavía **no** se pueden hacer.

Si algo falla, lo más útil es: modelo de pendrive y formato (FAT32/exFAT), qué pantalla estaba
abierta y, si es posible, el log (`adb logcat | grep -i usbx`).

## Limitaciones conocidas

- **NTFS**: Android solo monta FAT32 y exFAT en la mayoría de móviles (incluido el M52). Un
  pendrive NTFS no aparecerá; Android mostrará su propio aviso de formato no compatible.
- **Desenchufar sin expulsar**: para leer no hay riesgo de corromper datos. Aun así, si
  desenchufas justo mientras Android lee un archivo, el sistema puede cerrar los procesos que
  lo tienen abierto; USBX abre cada archivo el mínimo tiempo posible. Conviene comprobarlo
  en la prueba 18.
- **GIF animados** se animan en el visor; en la cuadrícula se ve el primer fotograma.
- **Zoom nítido** requiere que Android sepa decodificar por regiones el formato (JPEG, PNG, WebP y
  HEIF sí). En otros formatos el zoom funciona pero con la resolución de pantalla.
- **Girar** es solo visual y no se guarda: modificar archivos llegará con la Fase 5.
- **HEIC/HEIF** necesita Android 9+ y **AVIF** Android 12+ (el M52 cumple ambos).
- TIFF, RAW (DNG) y SVG se abren con otras apps.

## Licencia

Pendiente de decidir por el propietario del repositorio.
