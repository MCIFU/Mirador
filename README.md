# Mirador

Explorador y visor multimedia para memorias USB conectadas por USB‑C / OTG en Android.
Dispositivo de referencia: **Samsung Galaxy M52 5G** (Android 14 / One UI 6), pero solo usa APIs
públicas de Android: no hay código específico de Samsung.

> **Estado: Fase 6 (pulido) — versión 0.9.1.** Antes se llamaba USBX; desde la 0.9.0 es Mirador
> (id nuevo: la versión USBX hay que desinstalarla aparte). Lo que no está en la tabla de abajo como «Hecho» no está
> implementado, y la app no lo aparenta.

| Área | Estado |
|---|---|
| Detección de memorias USB, permiso, desconexión y reconexión | Hecho (Fase 1) |
| Explorador: carpetas, lista, cuadrícula, miniaturas, orden, ocultos | Hecho (Fase 1) |
| Abrir, abrir con…, información y compartir | Hecho (Fase 1) |
| Visor básico: pantalla completa, deslizar, precarga | Hecho (Fase 1) |
| Zoom nítido, doble toque, rotación, orientación, transiciones, tira de miniaturas, GIF | Hecho (Fase 2) |
| Reproductor de vídeo y audio integrado: barra de progreso, velocidad, repetición, bloqueo, gestos | Hecho (Fase 3) |
| Multiview: 2 fotos o 2 vídeos, zoom y reproducción sincronizables, intercambiar paneles | Hecho (Fase 4) |
| Abrir fotos, vídeos y audio desde otras apps («Abrir con Mirador») | Hecho (0.5.0) |
| Selección múltiple, copiar, mover, eliminar, renombrar, nueva carpeta, progreso | Hecho (Fase 5) |
| Ajustes (tema, Material You, visor, caché), eliminar desde el visor, informe de diagnóstico | Hecho (Fase 6) |
| Fluidez: precarga de miniaturas, zoom sin recomposición, vídeo sin saltos al abrir/cerrar, perfil de referencia | Hecho (0.9.0) |
| Zoom en vídeos: pellizcar (hasta 6×), arrastrar ampliado, vuelta a 1× animada (botón o Atrás) | Hecho (0.9.1) |

## Instalar en el móvil

### Opción A: APK compilado por GitHub (sin instalar nada en el PC)

1. En GitHub, abre la pestaña **Actions** → workflow **Android CI** → la ejecución más reciente
   con ✔ de la rama que quieras probar.
2. Abajo, en **Artifacts**, descarga `mirador-release-apk` (es un .zip que contiene el .apk).
   Es la versión recomendada para el uso diario: va optimizada con R8 y el perfil de referencia,
   y se desplaza y amplía más fluida que la de depuración (`mirador-debug-apk`).
3. Pasa el `.apk` al móvil y ábrelo con «Mis archivos». La primera vez Android pedirá permitir
   «Instalar apps desconocidas» para esa app.

El APK release se instala como **Mirador** con id `com.mcifu.mirador` (el de depuración, con
`com.mcifu.mirador.debug`, como app aparte). Ambos se pueden
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

## Abrir con Mirador desde otras apps

Mirador se registra para abrir fotos, vídeos y audio. En **Mis archivos** (o la galería, WhatsApp…)
toca un archivo, elige **Mirador** y pulsa **Siempre**. Si la Galería o el reproductor de Samsung se
abren directamente, quítales el «abrir por defecto» en Ajustes → Aplicaciones → (app) →
Establecer como predeterminada → Borrar valores predeterminados.

- Si Mirador tiene acceso a esa memoria, el archivo se abre con su carpeta y puedes deslizar.
- Si no, se abre solo ese archivo, con todas las funciones del visor y del reproductor.

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
| Media3 (ExoPlayer + ui-compose) | 1.11.1 | Reproductor mantenido por Google; `ContentFrame` gestiona formato y portada |

Sin Hilt/Dagger (el grafo de dependencias cabe en `AppContainer`), sin Firebase, sin
analítica y sin acceso a red. La app no declara permisos de almacenamiento, de red ni ningún otro
que el usuario tenga que conceder.

## Cómo accede Mirador a la memoria USB

Se evaluaron tres opciones:

1. **`MANAGE_EXTERNAL_STORAGE` («acceso a todos los archivos») + rutas `/storage/XXXX-XXXX`.**
   Descartada: es un permiso especial que Google Play solo acepta para gestores de archivos
   completos, da acceso a todo el móvil y va contra el requisito de no usar permisos innecesarios.
2. **Acceso USB directo (`UsbManager` + libaums).** Descartada: salta el montaje del sistema,
   solo entiende FAT32, es más lento, entra en conflicto con Android si este ya montó la
   memoria y no es una API oficial de almacenamiento.
3. **Storage Access Framework (elegida).** Android monta el pendrive (FAT32/exFAT) y Mirador pide
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
app/src/main/java/com/mcifu/mirador/
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
    ├── player/                   Reproductor: controlador ExoPlayer, gestos, barra, controles, bloqueo, sincronización
    ├── multiview/                Dos paneles, zoom compartible, selector de archivo
    ├── common/                   Diálogos, abrir/compartir, formatos, scroll rápido
    ├── navigation/               Rutas tipadas y NavHost
    └── theme/                    Material 3 con paleta propia, claro y oscuro
```

Preparado para las siguientes fases:

- `VideoPlayerController` + `PlayerState` encapsulan un reproductor completo; el multiview usa dos
  y `PlaybackSync` los enlaza con las mismas llamadas (`play`, `seekTo`, `setSpeed`…).
- Las reglas del reproductor (`PlaybackRules`) son puras y están probadas con tests.
- `ExternalActions.share` ya acepta varios archivos (para la selección múltiple de la Fase 5).
- El permiso del árbol se toma con lectura **y escritura**, así que copiar, mover y renombrar
  (Fase 5) no exigirán volver a autorizar la memoria.

## Guía de pruebas de la Fase 1 en el Galaxy M52

Necesitas: el M52, un pendrive USB‑C (o USB‑A con adaptador OTG) formateado en FAT32 o exFAT, con
alguna carpeta con muchas fotos (idealmente cientos), algún vídeo, un MP3 y un PDF.

| # | Prueba | Cómo hacerla | Resultado esperado |
|---|---|---|---|
| 1 | Compilar | Opción A o B de arriba | APK generado sin errores |
| 2 | Instalar | Abrir el APK en el móvil | Aparece el icono Mirador (pendrive turquesa) |
| 3 | Conectar USB | Con Mirador abierto, enchufa el pendrive | En 1–3 s aparece su tarjeta: «Conectada · falta conceder acceso», con espacio libre |
| 4 | Abrir Mirador | Si lo conectaste antes de abrir la app, abre Mirador | La memoria aparece igual |
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
| 10 | Qué recorrer | ⋮ → «Qué recorrer al deslizar…» → prueba «Solo imágenes» y «Fotos y vídeos» | La lista cambia sin perder la foto actual; con «Fotos y vídeos», al llegar a un vídeo empieza a reproducirse |
| 11 | Vídeo desde el visor | Desliza hasta un vídeo | Se reproduce en la misma página (ver guía de la Fase 3) |
| 12 | GIF | Abre un GIF animado | Se anima en el visor (en la cuadrícula se ve fijo) |
| 13 | HEIC | Abre una foto HEIC | Se muestra y amplía igual que un JPG |
| 14 | Información y compartir | Botones ⓘ y compartir arriba | Igual que en la Fase 1, sobre el elemento visible |
| 15 | Desconectar en el visor | Con una foto ampliada, desenchufa el pendrive; vuelve a enchufarlo | Aviso de desconexión; al reconectar vuelve a la misma foto |

## Guía de pruebas de la Fase 3 (reproductor)

Usa una carpeta con 2–3 vídeos (uno de varios minutos), alguna foto entre ellos y un MP3. Si tienes
un MKV o AVI, pruébalo también.

| # | Prueba | Cómo hacerla | Resultado esperado |
|---|---|---|---|
| 1 | Abrir vídeo | Toca un vídeo en el explorador | Se abre el reproductor y empieza solo; mientras carga se ve su portada |
| 2 | Play/pausa | Botón central; doble toque en el centro de la imagen | Pausa y reanuda |
| 3 | Controles | Toca la imagen; espera sin tocar | Aparecen tiempo actual, duración y botones; se ocultan solos a los ~3,5 s reproduciendo |
| 4 | Arrastrar barra | Arrastra el punto de la barra adelante y atrás, despacio y rápido | La burbuja muestra el tiempo; el vídeo enseña el fotograma aproximado; al soltar sigue desde ahí sin saltar atrás |
| 5 | Tocar la barra | Toca un punto de la barra | Salta a esa posición |
| 6 | ±10 s | Botones ⟲10 / 10⟳; doble toque a izquierda y derecha | Retrocede/avanza 10 s con un indicador en ese lado |
| 7 | Avance rápido | Mantén el dedo en la parte **derecha** unos segundos, luego suelta | Aparece «⏩ 2×», sube a 4×, 8× y 16× cada 1,5 s; al soltar vuelve a la velocidad normal |
| 8 | Retroceso rápido | Igual en la parte **izquierda** | «⏪ 2×…16×» hacia atrás; al soltar sigue reproduciendo normal |
| 9 | Sin interferencias | Mientras mantienes pulsado, mueve el dedo; desliza rápido de un lado a otro sin mantener | Mantenido: no cambia de vídeo. Deslizar: pasa al vídeo/foto siguiente, sin avance rápido |
| 10 | Velocidad | Botón «1×» → 0,25× … 2× | Cambia al instante (el audio mantiene el tono) |
| 11 | Repetición | Botón «Repetir / Una vez / Carpeta»; deja terminar un vídeo corto en cada modo | Repetir: vuelve al principio. Una vez: se para y aparecen los controles con ⟲. Carpeta: pasa al siguiente vídeo o audio y, tras el último, al primero |
| 12 | Bloquear | Botón «Bloquear»; toca, desliza, pulsa Atrás; luego mantén pulsado 1 s | Bloqueado: nada cambia, solo aparece «🔒 Controles bloqueados». Manteniendo pulsado se llena un anillo y se desbloquea |
| 13 | Orientación | Botón «Auto / Vertical / Horizontal» con un vídeo apaisado | «Horizontal» fuerza apaisado aunque el giro automático esté desactivado |
| 14 | Volumen | Botón de volumen → deslizador | Baja el volumen del vídeo (los botones del móvil siguen controlando el general) |
| 15 | Reanudar | Ve a mitad de un vídeo, desliza a otro y vuelve | Continúa donde lo dejaste |
| 16 | Audio | Abre un MP3 | Carátula (si tiene) con los mismos controles |
| 17 | Salir de la app | Con un vídeo reproduciéndose, pulsa Inicio | Se pausa; al volver sigue en pausa en el mismo punto |
| 18 | Formato no compatible | Un vídeo con códec raro (p. ej. AVI antiguo o MKV con DTS) | Mensaje «El reproductor integrado no admite este formato» con «Abrir con otra aplicación» |
| 19 | Desconectar | Desenchufa el pendrive reproduciendo; vuelve a enchufarlo | Aviso de desconexión sin cierres; al reconectar vuelve al mismo vídeo cerca del mismo punto |

## Guía de pruebas de la Fase 4 (multiview)

Usa una carpeta con varias fotos parecidas (p. ej. ráfagas) y dos vídeos.

| # | Prueba | Cómo hacerla | Resultado esperado |
|---|---|---|---|
| 1 | Abrir | Pulsación larga en una foto → «Comparar en multiview» (o ⋮ en el visor) | Se abre con esa foto arriba y un panel vacío con «Elegir foto o vídeo» |
| 2 | Elegir el segundo | Toca «Elegir foto o vídeo» y escoge otra foto | Aparece en el segundo panel |
| 3 | Zoom independiente | Pellizca y arrastra en un panel; doble toque | Solo cambia ese panel; doble toque amplía 2,5× o vuelve a encajar |
| 4 | Zoom sincronizado | Botón de enlace arriba (con dos fotos) y amplía en un panel | El otro panel se amplía y se mueve exactamente igual; arriba pone «Zoom sincronizado» |
| 5 | Cambiar y cerrar | En la cabecera de un panel: icono de galería (cambiar) y ✕ (cerrar) | Cambia la foto de ese panel o lo vacía |
| 6 | Intercambiar | Botón ⇅ (vertical) o ⇄ (horizontal) | Los paneles cambian de sitio al instante, sin recargar |
| 7 | Girar | Pon el móvil en horizontal (o usa el botón de orientación) | Los paneles pasan a estar lado a lado |
| 8 | Dos vídeos | Pon un vídeo en cada panel | Se reproducen a la vez; el segundo empieza silenciado (icono de altavoz tachado) |
| 9 | Controles independientes | En cada panel: pausa, barra, velocidad, volumen, repetición | Cada acción afecta solo a su panel |
| 10 | Sincronizar | Pausa, deja el vídeo B 2 s por delante, abre el menú de enlace → «Sincronizar reproducción» | Arriba: «Sincronizado · desfase +2,0 s». Play, pausa, barra y velocidad en cualquiera de los dos mueven ambos manteniendo el desfase |
| 11 | Alinear | Menú de enlace → «Alinear (desfase 0)» | Ambos saltan al mismo instante |
| 12 | Volver al visor | Atrás desde el multiview abierto desde el visor | El vídeo del visor continúa donde estaba |
| 13 | Desconectar | Desenchufa el USB con dos vídeos reproduciéndose; reconecta | Aviso sin cierres; al reconectar vuelven ambos vídeos |

Si algo falla, lo más útil es: modelo de pendrive y formato (FAT32/exFAT), qué pantalla estaba
abierta y, si es posible, el log (`adb logcat | grep -i mirador`).

## Guía de pruebas de la Fase 5 (gestión de archivos)

Hazla con una carpeta de copias, no con fotos únicas.

| # | Prueba | Cómo hacerla | Resultado esperado |
|---|---|---|---|
| 1 | Nueva carpeta | Icono de carpeta con «+» arriba → «Prueba» | Aparece la carpeta; nombres con `/ : * ?` se rechazan |
| 2 | Seleccionar | Pulsación larga en una foto, toca otras dos | Marca ✓ y barra «3 seleccionados»; Atrás quita la selección |
| 3 | Copiar | Copiar → elige «Prueba» → «Copiar aquí» | Tarjeta de progreso; las 3 fotos aparecen en «Prueba» |
| 4 | Cancelar | Copia un vídeo grande y pulsa Cancelar | No queda ningún archivo a medias en el destino |
| 5 | Renombrar | Selecciona una → ⋮ → Renombrar | Viene seleccionado el nombre sin la extensión |
| 6 | Mover | Mueve una foto a otra carpeta | Desaparece del origen al instante y está en el destino |
| 7 | Entre memorias | Con pendrive y tarjeta autorizados, mueve un archivo de una a otra | Se copia y solo después se borra el original |
| 8 | Eliminar | Elimina la carpeta «Prueba» | Pide confirmación (sin papelera) y la borra con su contenido |
| 9 | Compartir varios | Selecciona 3 fotos → compartir | La app destino recibe las 3 |

## Guía de pruebas de la Fase 6

| # | Prueba | Cómo hacerla | Resultado esperado |
|---|---|---|---|
| 1 | Ajustes | Pantalla principal → ⋮ → Ajustes | Tema, colores, visor, explorador, caché y versión |
| 2 | Tema | Elige Claro / Oscuro / Sistema y Material You | Cambia al instante en toda la app (el visor sigue siendo negro) |
| 3 | Caché | «Borrar caché de miniaturas» | El tamaño baja a 0; las miniaturas se regeneran al volver |
| 4 | Eliminar desde el visor | En una foto: ⋮ → Eliminar → confirmar | Se borra y el visor pasa a la siguiente |

## Limitaciones conocidas

- **NTFS**: Android solo monta FAT32 y exFAT en la mayoría de móviles (incluido el M52). Un
  pendrive NTFS no aparecerá; Android mostrará su propio aviso de formato no compatible.
- **Desenchufar sin expulsar**: para leer no hay riesgo de corromper datos. Aun así, si
  desenchufas justo mientras Android lee un archivo, el sistema puede cerrar los procesos que
  lo tienen abierto; Mirador abre cada archivo el mínimo tiempo posible. Conviene comprobarlo
  en la prueba 18.
- **GIF animados** se animan en el visor; en la cuadrícula se ve el primer fotograma.
- **Zoom nítido** requiere que Android sepa decodificar el formato por regiones. JPEG, PNG y WebP
  siempre; para el resto (HEIC, BMP…) Mirador lo comprueba al abrir cada archivo y, si no se puede,
  hace zoom sobre la imagen a resolución de pantalla (menos detalle al ampliar, pero sin bloqueos).
- **Girar** es solo visual y no se guarda: modificar archivos llegará con la Fase 5.
- **HEIC/HEIF** necesita Android 9+ y **AVIF** Android 12+ (el M52 cumple ambos).
- **Códecs de vídeo**: ExoPlayer lee MP4, MKV, WebM, MOV, AVI, TS y 3GP, pero la decodificación la
  hace el móvil. El M52 decodifica H.264, H.265/HEVC y VP9 por hardware; **AV1** y audio
  **DTS/AC‑3** pueden no reproducirse (Mirador lo avisa y ofrece otra app). Añadir decodificadores
  software (FFmpeg) es posible pero aumenta el APK en unos 10 MB; se valorará en la Fase 6.
- **Avance rápido 4×–16× y retroceso** funcionan por saltos entre fotogramas clave: la imagen avanza
  a trompicones (como en la mayoría de reproductores), sin sonido. El 2× hacia delante es fluido.
- **Vista previa al arrastrar la barra**: es el propio vídeo mostrando el fotograma clave más
  cercano; no hay miniatura flotante aparte.
- **Precarga de vídeo**: las páginas vecinas tienen su portada lista, pero el vídeo siguiente no se
  empieza a decodificar hasta que llegas a él (en local suele tardar menos de medio segundo). La
  precarga real de vídeo (`PreloadManager` de Media3) se valorará en la Fase 6.
- **Reproducción en segundo plano / pantalla apagada** no está incluida: al salir de la app se pausa.
- **Operaciones de archivos** siguen al cambiar de pantalla, pero si cierras Mirador del todo a mitad de una
  copia, Android puede cortarla: lo ya copiado se conserva, pero el archivo que se estaba copiando puede quedar incompleto en el destino (bórralo y vuelve a copiarlo).
  Si una carpeta falla a medias al copiarla, lo copiado se queda en el destino; el original nunca se toca.
- **Sin papelera**: eliminar en una memoria USB o tarjeta SD es definitivo.
- **Zoom en el multiview** no usa teselas (no es compatible con compartir el zoom entre dos fotos): cada
  panel carga la foto al doble de su tamaño, suficiente hasta ~3×. Para zoom máximo, usa el visor normal.
- **Zoom sincronizado** aplica la misma ampliación y desplazamiento a los dos paneles: coincide al
  píxel cuando las dos fotos tienen la misma proporción (lo normal en ráfagas o fotos de la misma cámara).
- **Dos vídeos 4K a la vez** pueden superar la capacidad de decodificación del M52; en ese caso uno
  de los paneles muestra el error con «Reintentar». Con 1080p no debería haber problema.
- **Sincronización**: la corrección de deriva provoca un pequeño salto en el vídeo B si se desvía más de
  250 ms (se comprueba cada 2 s). El selector del multiview solo muestra archivos de la misma carpeta.
- TIFF, RAW (DNG) y SVG se abren con otras apps.

## Licencia

Pendiente de decidir por el propietario del repositorio.
