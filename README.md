# Michis Reader

Un lector de EPUB para Android pensado para organizar una biblioteca personal y ofrecer una experiencia de lectura configurable, privada y sin cuentas obligatorias.

Michis Reader funciona de forma local y utiliza [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit) para abrir, navegar y conservar la posición dentro de los libros. La conexión con Google Drive es completamente opcional.

> **Estado del proyecto:** en desarrollo. Actualmente admite exclusivamente archivos EPUB y requiere Android 8.0 (API 26) o una versión posterior.

## Qué ofrece

### Biblioteca organizada

- Importación de archivos EPUB desde el dispositivo.
- Carpetas, búsqueda, filtros y cuatro modos de visualización.
- Orden por título, autor o una disposición personalizada por carpeta.
- Portadas, edición de metadatos y acceso rápido al último libro leído.
- Restauración de la carpeta o de la sesión de lectura al volver a abrir la app.

### Lectura a tu manera

- Tabla de contenido jerárquica y búsqueda de texto dentro del libro.
- Navegación por toque, controles superpuestos y modo inmersivo.
- Historial para regresar o avanzar entre saltos realizados en el libro.
- Temas de lectura y cambio rápido entre dos temas configurados.
- Ajustes globales de fuente, tamaño, grosor, interlineado, alineación y márgenes.
- Lectura paginada o con desplazamiento, tanto en vertical como en horizontal.
- Estimación de páginas adaptada a la configuración visual actual.

### Herramientas para recordar y aprender

- Citas resaltadas con color y notas propias.
- Marcadores vinculados a la posición exacta de lectura.
- Diccionarios por libro, con categorías y posibilidad de compartirlos entre varios libros.
- Términos del diccionario resaltados directamente en el EPUB.
- Acceso agrupado a citas, marcadores y diccionarios desde la biblioteca.

### Sincronización opcional

- Inicio de sesión con Google y autorización de Drive separados.
- Selección de carpetas o EPUB concretos de Google Drive.
- Sincronización incremental de libros, progreso, citas, marcadores y diccionarios.
- Trabajo en segundo plano con opción de limitar la sincronización a Wi-Fi.
- Resolución de cambios por libro y protección de elementos eliminados para que no reaparezcan desde otro dispositivo.

La app sigue siendo plenamente utilizable sin iniciar sesión y sin conexión. Los documentos locales no se envían a servicios del desarrollador.

### Controles de hardware

Dentro del lector, RemoteActions permite usar el botón y los ocho gestos aéreos del S Pen sin integrar el SDK propietario. Por defecto se puede avanzar, retroceder, cambiar el tamaño del texto, alternar el tema y agregar o quitar un marcador. Cada control puede reasignarse desde Configuración y las teclas `PAGE_DOWN` y `PAGE_UP` siguen siendo compatibles con controles de hardware genéricos.

## Alcance

Michis Reader está dedicado exclusivamente a la lectura de EPUB. No incorpora lectores de PDF, DOCX, MOBI u otros formatos, ni funciones de texto a voz.

## Ejecutar el proyecto

### Requisitos

- Android Studio con soporte para Android SDK 36.
- Un dispositivo o emulador con Android 8.0 o posterior.
- JDK compatible con Android Gradle Plugin 9.2.1.

### Desde Android Studio

1. Clona o descarga el repositorio.
2. En Android Studio, selecciona **Open** y abre la carpeta raíz del proyecto.
3. Confirma **Trust Project** y espera a que termine la sincronización de Gradle.
4. Instala Android SDK 36 si Android Studio lo solicita.
5. Selecciona un dispositivo y ejecuta el módulo `app`.

`local.properties` contiene la ruta local del SDK. Android Studio puede crearlo o regenerarlo y no debe añadirse al control de versiones.

### Validación desde la terminal

En Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:compileDebugKotlin
```

En macOS o Linux:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugKotlin
```

## Tecnología

- Kotlin y Android Views con XML/View Binding.
- Readium Kotlin Toolkit 3.2.0 para el lector EPUB.
- SQLite para la biblioteca, el progreso y las anotaciones.
- WorkManager para la sincronización en segundo plano.
- Credential Manager y Google Drive para la cuenta y sincronización opcionales.
- KvColorPicker Android para colores personalizados.
- JUnit y Robolectric para pruebas automatizadas.

## Organización del código

El proyecto contiene un único módulo Android, `app`. Sus áreas principales son:

- `app`: entrada de la aplicación y restauración de la última pantalla.
- `reader`: apertura, navegación, búsqueda y apariencia del EPUB.
- `library`: importación, carpetas, portadas y representación de la biblioteca.
- `annotations` y `dictionary`: citas, marcadores y diccionarios.
- `data`: base de datos y persistencia.
- `sync` y `sync.drive`: sincronización incremental y conexión opcional con Google Drive.
- `input`: traducción genérica de controles de hardware a acciones del lector.
- `settings`, `theme` y `ui`: preferencias y componentes compartidos.

Antes de contribuir, consulta [`AGENTS.md`](AGENTS.md), que documenta la arquitectura, los comportamientos que deben conservarse y la validación esperada para los cambios.
