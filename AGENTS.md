# Guía de mantenimiento para agentes — Michis Reader

Este archivo es la referencia operativa para cualquier agente o desarrollador que modifique el repositorio. Debe leerse antes de cambiar código. Su objetivo es conservar las decisiones ya tomadas, proteger los flujos delicados y evitar que una mejora local rompa lectura, persistencia o sincronización.

## 1. Identidad y alcance de la aplicación

Michis Reader es una aplicación Android escrita en Kotlin y dedicada **exclusivamente a leer EPUB**. El lector usa Readium Kotlin Toolkit.

Decisiones que no deben revertirse sin una solicitud explícita:

- No volver a agregar lectores de PDF, TXT, DOCX, MOBI, FB2, JSON, CSV, LOG u otros formatos.
- No agregar TTS.
- No exigir cuenta: toda la lectura local debe funcionar sin iniciar sesión.
- No enviar documentos locales a servicios del desarrollador.
- Google Drive es opcional y se usa únicamente cuando el usuario lo activa.
- No generar APK, ZIP ni instalar la aplicación salvo que el usuario lo solicite expresamente. Para validar, usar pruebas o compilación de Kotlin, no tareas `assemble`.
- Usar nombres completos y código legible; evitar abreviaturas ambiguas y duplicación de lógica.

## 2. Tecnología y configuración

- Módulo único: `app`.
- Paquete: `com.michis.reader`.
- Android mínimo: API 26.
- `compileSdk` y `targetSdk`: 36.
- UI actual: layouts XML, View Binding y componentes creados dinámicamente donde aún es necesario.
- Lector: Readium Kotlin Toolkit 3.2.0.
- Persistencia: SQLite mediante `ReaderDatabase` y repositorios especializados.
- Trabajo en segundo plano: WorkManager.
- Selector de color: `KvColorPickerOverlay`, respaldado por KvColorPicker Android 3.0.1.
- Inicio de sesión: Credential Manager/Google ID; autorización de Drive separada del inicio de sesión.

Aunque Compose está habilitado en Gradle, la interfaz vigente usa XML y View Binding. No migres una pantalla a Compose como parte incidental de otro cambio.

## 3. Mapa del código

### Entrada y restauración

- `app/MainActivity.kt`: biblioteca, navegación por carpetas, filtros, vistas, importación y accesos generales.
- `app/ReaderResumeState.kt`: decide si al iniciar debe reabrirse el último libro o regresar a la carpeta de biblioteca correspondiente.

### Lector EPUB

- `reader/ReadiumEpubActivity.kt`: coordinador del ciclo de vida, apertura de la publicación Readium, controles y conexiones entre los componentes especializados del lector.
- `reader/EpubReadingSettingsPanel.kt`: contenido y acciones del menú Aa.
- `reader/EpubContentsPanel.kt`: árbol jerárquico del índice.
- `reader/EpubDecorationController.kt`: resaltados interactivos de diccionarios y citas.
- `reader/EpubAppearanceController.kt`: preferencias Readium, temas, tipografía y ajustes CSS de página; no dupliques esta lógica en la Activity.
- `reader/EpubSearchController.kt`: búsqueda interna, resultados, decoraciones y registro de saltos producidos por búsqueda.
- `reader/ReaderSessionController.kt`: restauración de sesión, persistencia final y sincronización por libro al salir o minimizar.
- `reader/ReaderWindowController.kt`: pantalla inmersiva, barras del sistema, notch y tiempo de pantalla activa.
- `reader/DictionaryLocatorCache.kt`: caché descartable e incremental de búsquedas Readium para términos de diccionario.
- `reader/EpubPageEstimator.kt`: cálculo aproximado de páginas relativo a la presentación.

### Biblioteca

- `library/LibraryImportCoordinator.kt`: importación local.
- `library/LibraryBrowserState.kt`: carpeta, filtro, modo de vista y orden personalizado.
- `library/LibraryViewRenderer.kt`: representación de carpetas y libros.
- `library/BookCoverLoader.kt`: extracción y caché de portadas EPUB.
- `library/LibraryDocumentActions.kt`: editar metadatos, reiniciar y eliminar.
- `library/LibrarySectionsController.kt`: secciones principales de citas, marcadores y diccionarios.

### Datos, citas y diccionarios

- `data/ReaderDatabase.kt`: esquema, migraciones, modelos y fachada de persistencia.
- `annotations/AnnotationRepository.kt`: citas y marcadores.
- `annotations/BookQuotesActivity.kt`: citas agrupadas por libro, edición y eliminación múltiple.
- `annotations/QuoteColorActivity.kt`: creación y edición de color/nota de una cita.
- `dictionary/DictionaryRepository.kt`: categorías, entradas, vínculos y reglas de duplicados.
- `dictionary/DictionaryActivity.kt`: creación de subcategorías, entradas, edición, eliminación y uso compartido entre libros.
- `dictionary/DictionarySyncMerger.kt`: fusión segura de diccionarios sincronizados.

### Configuración, temas y UI

- `settings/ReaderSettingsRepository.kt`: preferencias globales de lectura y apariencia.
- `settings/SettingsActivity.kt`: configuración general.
- `settings/DriveSettingsSection.kt`: cuenta, autorización y sincronización visible al usuario.
- `settings/ResetBooksActivity.kt`: reinicio seleccionable de libros.
- `theme/AppThemePalette.kt`: paleta global, contraste, fondos y estilo recursivo.
- `theme/ReadingThemePalette.kt`: temas disponibles para el contenido EPUB.
- `theme/KvColorPickerOverlay.kt`: único punto de entrada para escoger colores.
- `ui/ScreenHeader.kt`: encabezado fijo y regreso visible.
- `ui/SystemBarInsets.kt`: aplicación compartida de barras del sistema y notch en actividades normales; no dupliques listeners de insets por pantalla.
- `ui/LimitedHeightSpinner.kt`: desplegable temático con altura limitada. Debe conservar constructores compatibles con inflación XML.

### Google Drive y sincronización

- `sync/drive/OptionalGoogleAccountManager.kt`: identidad opcional.
- `sync/drive/GoogleDriveAuthorizationManager.kt`: permiso de Drive; no mezclarlo con el inicio de sesión.
- `sync/drive/DriveLibraryPickerActivity.kt`: selección jerárquica de varias carpetas o EPUB individuales.
- `sync/drive/GoogleDriveBookLibraryRepository.kt`: detección incremental y descarga de EPUB seleccionados.
- `sync/drive/GoogleDriveFolderRepository.kt`: carpeta privada de estado y archivos JSON.
- `sync/IncrementalLibrarySyncCoordinator.kt`: manifiesto y estados JSON por libro.
- `sync/LibrarySyncSnapshotBuilder.kt` y mergers: serialización y reconciliación.
- `sync/SyncStateRepository.kt`: identificadores de sincronización, marcas de tiempo y tombstones.
- `sync/AutomaticDriveSyncScheduler.kt` y `GoogleDriveSyncWorker.kt`: sincronización automática y restricciones de red.

## 4. Comportamientos que deben conservarse

### Biblioteca

- Solo se importan EPUB.
- La jerarquía de carpetas de Drive se refleja en la biblioteca; no aplanar todos los libros en una sola lista.
- En una carpeta aparece la entrada `...` para regresar. Esta entrada no participa en el orden manual.
- Los cuatro modos de vista, búsqueda, filtros y orden personalizado deben seguir funcionando juntos.
- Mantener pulsado un libro abre sus acciones; un toque normal lo abre.
- Las portadas deben conservar proporción vertical y cargar sin bloquear la UI.

### Restauración al abrir la app

- Si la app se cerró o se minimizó con un libro activo, debe reabrir ese libro y dejar que Readium restaure su posición.
- Si el usuario salió realmente del lector, debe abrir la biblioteca en la carpeta del último libro.
- Abrir Configuración general desde el menú Aa no cuenta por sí solo como abandonar el libro.
- No borres ni marques prematuramente `ReaderResumeState` durante cambios de ciclo de vida.

### Lector

- Readium es la fuente de verdad para publicación, navegación, selección y posiciones.
- Los controles superior e inferior se superponen al documento; mostrarlos no debe redimensionar ni desplazar el EPUB.
- Al ocultar controles se ocultan también las barras del sistema; al mostrarlos se restauran.
- El slider compacto permanece disponible con controles ocultos.
- Los toques laterales cambian de página y el toque central alterna controles, salvo cuando se acaba de activar una decoración o cerrar un panel.
- En vertical, Índice, Citas, Diccionario y Marcador viven bajo Herramientas. En horizontal aparecen como botones separados. Aa permanece independiente.
- El panel Aa y los demás menús deben respetar la paleta global de menús.
- Los saltos realizados con cualquiera de los sliders o con el índice alimentan un historial implementado como lista doblemente ligada. Con los controles visibles se ofrecen dinámicamente `Regresar a página #`, `Limpiar historial` y `Avanzar a página #`; un salto nuevo después de retroceder descarta la rama futura.
- Abrir una cita desde el lector y navegar entre coincidencias de la búsqueda interna también alimenta ese mismo historial. La búsqueda usa el servicio de la publicación Readium y no distingue mayúsculas de minúsculas.
- Las preferencias de lectura son globales entre libros: tema, fuente, tamaño, grosor, interlineado, alineación, márgenes, orientación/paginación y opciones relacionadas.
- El cambio rápido alterna únicamente entre los dos temas configurados; no cambia tipografía ni dimensiones.
- El contenido inicia en la parte superior. La alineación horizontal sigue siendo configurable.
- El ancho útil del contenido se ajusta a los márgenes de la hoja y no debe reducirse junto con la tipografía. Se anula el `maxLineLength` basado en `rem` de Readium tanto para una página como para el pliego de dos páginas. El perfil predeterminado es `REDUCED` (la mitad del margen normal); `LARGE`, `NORMAL` y `REDUCED` conservan el `pageGutter` de Readium, mientras `CUSTOM` permite superior, inferior, izquierdo y derecho independientes.
- La estimación de página depende de la maquetación actual; no tratarla como número fijo del archivo EPUB.
- No eliminar páginas en blanco válidas del EPUB mediante heurísticas destructivas.

### Citas

- Las citas se agrupan por documento, no son una lista global plana.
- Guardan fragmento, nota, color, posición Readium y página aproximada.
- Se muestran como resaltado, no subrayado.
- Tocar una cita resaltada dentro del EPUB abre directamente su editor.
- Tocar una tarjeta en `BookQuotesActivity` abre el editor; el botón `Abrir` navega a la posición de lectura.
- Una cita solo se elimina desde su editor y después de una confirmación. No reincorpores eliminación directa ni selección múltiple destructiva en los listados.
- La edición debe actualizar `updated_at` para que Drive detecte el cambio.
- Tras volver al lector se deben refrescar las decoraciones.

### Marcadores

- Pulsar el marcador alterna agregar/eliminar en la misma posición.
- Existe un cooldown de tres segundos y un Toast explicativo; no eliminarlo sin petición explícita.
- Reiniciar un libro elimina sus marcadores junto con el resto de su estado, pero conserva el EPUB.

### Diccionarios

- Son por libro, aunque un diccionario puede vincularse y compartirse con varios libros.
- Las entradas no distinguen mayúsculas y no se permiten duplicados entre subcategorías del mismo diccionario efectivo.
- El menú principal muestra primero la creación de subcategoría y después la opción para compartir.
- Los términos y frases se resaltan dentro del EPUB.
- Un toque breve sobre un término resaltado abre directamente su entrada en Diccionario; no usar popup contextual ni exigir pulsación prolongada.
- Si se cambia una entrada, categoría o vínculo, conservar la lógica de sincronización y el refresco de decoraciones.

### Controles de hardware y Air Actions

- El soporte de Air Actions usa exclusivamente el contrato `REMOTE_ACTION`, `res/xml/remote_actions.xml` y los `KeyEvent` generados por el sistema.
- No agregar el Samsung S Pen Remote SDK ni comprobaciones de fabricante, Bluetooth, sensores o reconocimiento manual de gestos.
- `ReaderHardwareKeyMapper` traduce únicamente eventos iniciales y sin repetición de las ocho teclas declaradas por RemoteActions.
- `HardwareInputDispatcher` debe permanecer genérico, síncrono y desacoplado de Samsung y Readium; no vuelvas a introducir un flujo que pueda perder entradas antes de que exista un colector.
- `ReaderHardwareInputPreferences` conserva el mapeo configurable y sus valores predeterminados; Ajustes debe permitir restaurarlos.
- La actividad del lector intercepta las teclas en `dispatchKeyEvent` antes que Readium, consume repeticiones y liberaciones, y reutiliza `navigateOnePage`; no dupliques la navegación.
- Los eventos no deben producir acciones fuera de `ReadiumEpubActivity`.

## 5. Invariantes de datos y sincronización

Esta es la zona de mayor riesgo.

1. Cada documento, anotación, categoría, entrada y vínculo sincronizable tiene `sync_id` y `updated_at`.
2. Toda creación o edición sincronizable debe generar/conservar `sync_id` y actualizar `updated_at`.
3. No eliminar filas sincronizables directamente con `SQLiteDatabase.delete`. Usar las funciones de `ReaderDatabase`/`SyncStateRepository` que crean tombstones.
4. Los tombstones evitan que un elemento eliminado reaparezca desde otro dispositivo. No descartarlos durante fusiones.
5. Las claves de estado por libro y los fingerprints no deben basarse únicamente en el identificador local autoincremental.
6. La sincronización debe resolver por versión/fecha y preservar el cambio más reciente; no reemplazar ciegamente toda la base local.
7. La sincronización habitual es incremental:
   - Drive Changes detecta EPUB añadidos, modificados o eliminados.
   - El manifiesto identifica estados por libro.
   - Los estados se almacenan en archivos `book-state-<key>.json`.
   - El respaldo histórico `library-state.json` solo se usa para migración/compatibilidad.
8. Al salir, cerrar o minimizar el lector se encola la sincronización del libro activo, no una subida completa de toda la biblioteca.
9. La sincronización manual puede reconciliar el conjunto completo, pero no debe volver a subir EPUB sin cambios.
10. Respetar la preferencia `solo Wi‑Fi` frente a `datos móviles`; las tareas automáticas deben mantener sus restricciones de WorkManager.
11. Un reinicio de libro es una mutación sincronizable: progreso a cero y tombstones para citas, marcadores, categorías, entradas y vínculos afectados.
12. Toda sincronización larga de Drive, incluida la iniciada manualmente, debe encolarse mediante `AutomaticDriveSyncScheduler` y ejecutarse en `GoogleDriveSyncWorker`. Las actividades solo resuelven autorizaciones que requieren UI, encolan y observan el progreso; nunca deben sostener una sincronización con `lifecycleScope`.
13. La sincronización completa y la sincronización de un libro comparten el bloqueo de `GoogleDriveSyncCoordinator`; no crear rutas paralelas que omitan esa serialización.
    - `last_opened_at = -1` es el marcador interno de reinicio intencional. No lo normalices a cero: cero identifica un libro nuevo y permite restaurar progreso remoto durante una reinstalación.
    - Las fusiones deben ignorar entidades remotas cuya versión sea anterior o igual a un tombstone local.
12. Antes de cambiar el esquema, aumenta la versión de `ReaderDatabase` y escribe una migración incremental que preserve instalaciones existentes. No dependas solo de `onCreate`.

Para cambios de sincronización, lee completos antes de editar: `IncrementalLibrarySyncCoordinator`, `SyncStateRepository`, `LibrarySyncSnapshotBuilder`, los mergers involucrados y sus pruebas.

## 6. Reglas de interfaz y temas

- Todas las actividades normales deben mantener encabezado y regreso visibles durante scroll. La excepción es el lector cuando oculta controles.
- El contenido desplazable no debe dibujarse encima de la barra de estado, salvo la experiencia inmersiva intencional del lector.
- Usa los estilos compartidos de `styles.xml`, dimensiones de `dimens.xml`, layouts reutilizables y View Binding.
- Mantén separación entre botones; no deben tocarse. Evita controles tan grandes que oculten acciones en teléfonos verticales.
- Todos los botones XML usan `Widget.MichisReader.Button`; los creados dinámicamente reciben la misma geometría mediante `AppThemePalette`. Su forma es de píldora, con radio de 24 dp y altura mínima de 48 dp.
- Inputs y spinners usan radio de 16 dp, borde temático y los márgenes compartidos de `dimens.xml`. No agregues controles visualmente aislados con formas o separaciones propias sin una razón funcional.
- Usa `ui_component_margin_horizontal`, `ui_component_margin_vertical` y las variantes `ui_content_spacing*` para separar componentes; evita nuevos márgenes arbitrarios codificados directamente.
- Los paneles editables usan tarjetas/rectángulos redondeados con padding interno; títulos de sección quedan fuera cuando así está establecido.
- Antes de aplicar `AppThemePalette.apply(activity)`, marca fondos especiales con `markBackground`, `markSurface` o `markCard`.
- Las vistas añadidas después del primer render deben volver a recibir el tema, normalmente con `content.post { AppThemePalette.apply(activity) }`.
- No fijes fondos crema, blancos o negros en Kotlin/XML si deben responder al tema. Usa la paleta y contraste dinámico.
- El texto debe obtener contraste suficiente sobre cada fondo. No derives el color del texto suponiendo que todos los temas son claros.
- Para colores personalizados usa solamente `KvColorPickerOverlay.show(...)`. No reincorpores sliders RGB/HSV ni otro selector paralelo.
- La identidad visual usa `ic_michis_reader_mark`, el icono adaptativo `ic_launcher` y su capa `monochrome`. Conserva las variantes clara/nocturna y la capa monocromática para iconos temáticos; no reemplaces el launcher por un PNG plano con bordes blancos.
- Los spinners usan `LimitedHeightSpinner`: 3–4 opciones visibles, scroll para el resto, fondo temático, selección por toque y sin apertura por pulsación prolongada.
- La transición futura de textos visibles a `strings.xml` está especificada en `cambios_futuro.md`. Si el usuario dice “mandarlo para el futuro”, documenta la propuesta allí.

## 7. Forma segura de realizar cambios

1. Lee el archivo completo que vas a modificar y sus colaboradores directos.
2. Busca todos los usos de clases, extras, claves de preferencias, columnas y métodos que cambies.
3. Conserva cambios locales ajenos; el worktree puede estar sucio.
4. Prefiere extender el repositorio/controlador responsable a ejecutar SQL o llamadas de Drive desde una Activity.
5. Reutiliza la lógica existente: no crees una segunda ruta para guardar progreso, citas, diccionarios o sincronizar.
6. Mantén callbacks de UI pequeños y delega persistencia/mezcla en repositorios.
7. Si agregas una preferencia global, centraliza clave y valor predeterminado en `ReaderSettingsRepository`.
8. Si agregas una Activity, regístrala en el manifest, aplica insets, encabezado y tema global.
9. Si agregas una vista XML, usa View Binding y verifica que cualquier vista personalizada tenga constructores XML válidos.
10. Si una decisión se pospone explícitamente, agrégala a `cambios_futuro.md`, no la implementes parcialmente.

## 8. Acciones que requieren especial cuidado

- No cambies scopes, cliente OAuth, flujo de autorización o nombres de archivos de Drive sin revisar compatibilidad con usuarios existentes.
- No registres tokens, correos, identificadores de cuenta ni contenido de libros en logs.
- No borres cachés, bases de datos, archivos importados o carpetas del usuario durante una “limpieza” sin autorización expresa.
- No uses `!!`; resuelve ausencia con retornos, valores seguros o errores explícitos.
- No bloquees el hilo principal con red, extracción de EPUB, imágenes o procesamiento grande.
- No conviertas el lector Readium en un WebView propio.
- No intentes “corregir” contenido EPUB con transformaciones irreversibles.
- No cambies simultáneamente esquema, formato JSON de sincronización y UI salvo que la tarea lo requiera y existan pruebas de migración.

## 9. Validación mínima antes de entregar

Ejecuta solo lo proporcional al cambio y reporta qué se validó.

### Comprobaciones estáticas

- `git diff --check`
- Buscar referencias antiguas después de renombrar con `rg`.
- Parsear todos los XML de `app/src/main/res`.
- Revisar `git diff` para confirmar que no se tocaron archivos ajenos.

### Pruebas

- Pruebas unitarias: `gradlew.bat :app:testDebugUnitTest`
- Compilación sin APK, si hace falta: `gradlew.bat :app:compileDebugKotlin`
- No ejecutar `assembleDebug`, `build`, instalación ADB ni empaquetado salvo petición explícita.

Pruebas existentes relevantes:

- `ReaderDatabaseTest`: persistencia, reinicio y reglas de datos.
- `LibraryFlowsTest`: integración de biblioteca.
- `LibraryBrowserStateTest`: carpetas, vistas y orden.
- `ReaderSettingsRepositoryTest`: preferencias globales.
- `DriveLibrarySelectionTest`: selección de fuentes Drive.
- `AppThemePaletteTest`: contraste y paletas.

Cuando un cambio afecte sincronización, añade o amplía pruebas para conflicto local/remoto, reinstalación/restauración, tombstones y sincronización por libro. Cuando afecte temas, comprueba al menos un tema claro, uno oscuro y un color personalizado.

## 10. Criterio de finalización

Un cambio no está terminado solo porque compila. Debe mantener:

- lectura EPUB y restauración de posición;
- persistencia después de reiniciar la app;
- sincronización opcional e incremental sin resucitar eliminaciones;
- funcionamiento local sin cuenta ni red;
- legibilidad en temas claros, oscuros y personalizados;
- navegación hacia atrás e insets correctos;
- comportamiento coherente en vertical y horizontal;
- ausencia de regresiones en citas, marcadores y diccionarios.

Si una solicitud contradice una invariante de este documento, no la ignores: explica el conflicto y confirma el alcance antes de hacer una migración incompatible.
