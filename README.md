# Michis Reader

Lector de libros EPUB para Android, desarrollado en Kotlin y basado en Readium Kotlin Toolkit.

## Funciones principales

- Apertura e importación exclusiva de libros EPUB.
- Biblioteca local organizada en carpetas, con distintas vistas, búsqueda y edición de metadatos.
- Lectura EPUB mediante Readium, con tabla de contenido, navegación, selección de texto y progreso automático.
- Temas, tipografías, tamaño, grosor, interlineado, alineación, márgenes y modos rápidos de lectura.
- Citas con colores, marcadores y diccionarios asociados a cada libro.
- Sincronización incremental opcional con Google Drive de libros y estado de lectura por libro.
- Compatibilidad opcional con acciones aéreas del S Pen.
- Uso local sin cuenta obligatoria y sin telemetría.

## Abrir en Android Studio

1. Abra Android Studio y seleccione **Open**.
2. Seleccione la carpeta raíz `Reader`, que contiene `settings.gradle.kts`.
3. Confirme **Trust Project** y espere la sincronización de Gradle.
4. Si Android Studio lo solicita, instale Android SDK 36.
5. Ejecute la aplicación en un dispositivo con Android 8.0 o posterior.

El archivo `local.properties` contiene la ruta local del SDK y Android Studio puede regenerarlo. No debe compartirse ni almacenarse en el control de versiones.

## Paquetes principales

- `app`: entrada de la aplicación y restauración de la última pantalla.
- `reader`: lector, navegación, decoración y configuración EPUB.
- `library`: importación, organización, portadas y presentación de la biblioteca.
- `annotations` y `dictionary`: citas, colores y diccionarios de los libros.
- `data`: base de datos y modelos persistentes.
- `sync` y `sync.drive`: sincronización, autorización y acceso opcional a Google Drive.
- `settings`, `theme`, `spen` y `ui`: preferencias y componentes compartidos de interfaz.

La primera sincronización establece un cursor de cambios de Drive y migra automáticamente el respaldo histórico `library-state.json`. Las siguientes consultas descargan únicamente EPUB modificados y estados JSON por libro. Al cerrar el lector se encola solo el último libro, respetando la preferencia de Wi‑Fi o datos móviles.
