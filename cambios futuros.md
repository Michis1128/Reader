# Cambios futuros: transición gradual de la interfaz a XML

Este documento es una guía local para reorganizar gradualmente la interfaz de Michis Reader. No forma parte del producto y está excluido de Git mediante `.gitignore`.

## Objetivo

Separar progresivamente la presentación visual de la lógica Kotlin para facilitar:

- La edición desde Android Studio.
- El uso de vistas previas y herramientas visuales.
- El mantenimiento de márgenes, tamaños, colores y estilos.
- La reutilización de tarjetas, filas, encabezados y controles.
- La adaptación a teléfonos, tablets y distintas orientaciones.
- La lectura del código sin mezclar lógica de negocio con construcción de Views.

La migración debe ser gradual. No se debe reescribir toda la interfaz en una sola etapa ni modificar simultáneamente el comportamiento funcional de cada pantalla.

## Qué tipo de XML debe usarse

### `res/layout`

Debe contener la estructura de las pantallas y componentes:

- Contenedores.
- Encabezados.
- Barras de herramientas.
- Textos y campos de entrada.
- Botones y selectores.
- Plantillas para filas y tarjetas.
- Paneles superpuestos del lector.

### `res/drawable`

Debe reservarse para recursos visuales:

- Fondos redondeados.
- Bordes y estados presionado/seleccionado.
- Gradientes.
- Iconos vectoriales.
- Selectores visuales.

No debe utilizarse `drawable` para definir la estructura completa de una pantalla.

### `res/values`

Debe centralizar valores reutilizables:

- `colors.xml`: colores base que no se calculen dinámicamente.
- `dimens.xml`: márgenes, paddings, radios y tamaños frecuentes.
- `strings.xml`: todos los textos visibles para el usuario.
- `styles.xml`: estilos de componentes.
- `themes.xml`: configuración general de los temas.

Cuando sea conveniente deben existir variantes como `values-night`, `layout-land` o recursos específicos para tablets.

## Arquitectura visual objetivo

```text
app/src/main/res/
├── layout/
│   ├── activity_main.xml
│   ├── activity_settings.xml
│   ├── activity_dictionary.xml
│   ├── activity_book_quotes.xml
│   ├── activity_reset_books.xml
│   ├── activity_drive_library_picker.xml
│   ├── panel_epub_settings.xml
│   ├── panel_epub_contents.xml
│   ├── item_library_book.xml
│   ├── item_library_folder.xml
│   ├── item_dictionary_category.xml
│   ├── item_dictionary_entry.xml
│   └── item_quote.xml
├── drawable/
│   ├── background_card.xml
│   ├── background_button.xml
│   ├── background_input.xml
│   ├── background_spinner.xml
│   └── icon_*.xml
├── values/
│   ├── colors.xml
│   ├── dimens.xml
│   ├── strings.xml
│   ├── styles.xml
│   └── themes.xml
└── values-night/
    └── themes.xml
```

Los nombres definitivos pueden variar, pero deben describir claramente la pantalla o componente representado.

## Responsabilidades después de la transición

### XML

- Jerarquía y posición de Views.
- Márgenes y paddings iniciales.
- Tamaños y restricciones.
- Estilos y fondos predeterminados.
- Identificadores estables para acceder a cada View.

### Kotlin

- Carga y transformación de datos.
- Navegación.
- Eventos de clic, selección y gestos.
- Adaptadores y listas dinámicas.
- Aplicación de paletas calculadas en tiempo de ejecución.
- Integración con Readium, Google Drive, base de datos y S Pen.
- Mostrar, ocultar o actualizar elementos según el estado.

## Elementos que deben seguir siendo dinámicos

No todo debe convertirse en una estructura estática. Deben conservar una implementación dinámica cuando resulte más clara:

- El árbol jerárquico del índice EPUB.
- Las decoraciones de citas y diccionarios dentro de Readium.
- Los libros y carpetas cuyo número depende de la biblioteca.
- Los resultados de búsqueda.
- Las categorías y entradas de diccionario.
- Los temas o colores calculados a partir de una selección personalizada.

En estos casos, la plantilla de cada elemento sí puede definirse en XML y repetirse mediante un adaptador o inflater.

El selector de color puede continuar usando Compose mientras la biblioteca KvColorPicker lo requiera. No es necesario convertir toda la aplicación a Compose por un único componente.

## Orden recomendado de migración

### Etapa 1: recursos compartidos

1. Extraer textos visibles a `strings.xml`.
2. Extraer dimensiones repetidas a `dimens.xml`.
3. Crear estilos para botones, spinners, campos y tarjetas.
4. Crear fondos e iconos reutilizables en `drawable`.
5. Mantener la paleta dinámica actual como fuente de colores en tiempo de ejecución.

### Etapa 2: componentes pequeños

1. Encabezado fijo con botón de regreso.
2. Filas de configuración.
3. Tarjetas de secciones.
4. Filas de citas y diccionarios.
5. Plantillas de libros y carpetas.

Esta etapa permite comprobar el enfoque sin reemplazar pantallas completas.

### Etapa 3: pantallas estáticas

Migrar una pantalla por vez, comenzando por:

1. Configuración general.
2. Reiniciar libros.
3. Citas.
4. Diccionarios.
5. Selector de biblioteca de Drive.

Cada pantalla debe conservar exactamente su comportamiento antes de comenzar con la siguiente.

### Etapa 4: biblioteca

1. Crear layouts independientes para carpeta, portada grande, portada pequeña, lista detallada y lista compacta.
2. Mantener el cálculo del modo de vista en Kotlin.
3. Usar adaptadores o inflado de plantillas para evitar construir cada tarjeta manualmente.
4. Verificar orientación vertical y horizontal.
5. Preservar navegación por carpetas, pulsación prolongada y restauración de ubicación.

### Etapa 5: lector EPUB

Debe realizarse al final por ser la pantalla más delicada.

1. Migrar primero las barras superior e inferior superpuestas.
2. Migrar el panel de índice.
3. Migrar el menú Aa.
4. Mantener el fragmento y navegador de Readium bajo control de Kotlin.
5. No permitir que los paneles cambien el tamaño disponible para el documento.
6. Preservar pantalla completa, insets, gestos laterales y controles del S Pen.

## Procedimiento para migrar una pantalla

Para cada pantalla o componente:

1. Identificar todas las Views creadas actualmente desde Kotlin.
2. Separar cuáles son estáticas y cuáles dependen de datos.
3. Crear el XML correspondiente sin eliminar todavía el código anterior.
4. Inflar el layout desde la Activity, panel o adaptador.
5. Conectar las Views mediante View Binding o identificadores claros.
6. Trasladar únicamente la construcción visual; conservar la lógica existente.
7. Aplicar la paleta dinámica después de inflar el layout.
8. Comparar comportamiento, márgenes, orientación e insets.
9. Eliminar el constructor programático antiguo solo cuando la sustitución esté validada.
10. Ejecutar pruebas y revisar manualmente todos los temas.

## Recomendación sobre View Binding

Al comenzar la migración debe evaluarse activar View Binding:

```kotlin
buildFeatures {
    viewBinding = true
}
```

Esto permite acceder a las Views con referencias seguras y evita depender de múltiples llamadas a `findViewById`. Debe adoptarse de forma consistente para los layouts nuevos.

## Reglas para evitar regresiones

- No cambiar lógica funcional y diseño en la misma subetapa salvo que sea indispensable.
- No reemplazar nombres explícitos por abreviaturas.
- No codificar colores directamente dentro de Activities.
- No duplicar dimensiones o estilos que ya existan.
- No romper la aplicación dinámica de temas personalizados.
- Mantener siempre visible el encabezado de regreso, excepto cuando el lector esté en pantalla completa.
- Respetar la barra de estado en todos los ScrollView fuera del modo de lectura inmersivo.
- Verificar contraste y legibilidad en todos los temas predeterminados.
- Verificar teléfono vertical, teléfono horizontal y, cuando sea posible, tablet.
- No generar APK durante las tareas normales salvo petición explícita.

## Validación mínima por subetapa

- Compilación Kotlin correcta.
- Pruebas unitarias correctas.
- Pantalla sin cierres inesperados.
- Tema aplicado a fondo, tarjetas, controles y barras.
- Scroll e insets correctos.
- Botón de regreso visible y funcional.
- Estado conservado después de rotar o reabrir la pantalla cuando corresponda.
- Sin archivos o implementaciones visuales antiguas abandonadas.

## Criterio de finalización

La transición estará completa cuando las pantallas principales utilicen layouts XML o plantillas XML reutilizables, Kotlin se concentre en comportamiento y datos, y los componentes dinámicos permanezcan separados y claramente justificados.

La prioridad es mejorar el mantenimiento sin sacrificar estabilidad. Si una migración vuelve más difícil la integración con Readium o una vista verdaderamente dinámica, debe conservarse la solución Kotlin y documentarse la razón.
