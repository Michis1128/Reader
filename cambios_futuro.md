# Cambios para el futuro

Este documento reúne las mejoras y propuestas que se decida aplazar. Cuando una solicitud se mande “para el futuro”, deberá registrarse aquí con su objetivo, alcance y consideraciones de implementación.

## Migrar los textos visibles a `strings.xml`

### Objetivo

Centralizar en `app/src/main/res/values/strings.xml` todos los textos visibles para el usuario que actualmente están escritos directamente en layouts XML o archivos Kotlin.

### Beneficios

- Facilitar futuras traducciones de la aplicación.
- Evitar textos duplicados o redactados de manera inconsistente.
- Permitir que Android Studio detecte y administre recursos de texto.
- Simplificar cambios generales de terminología.
- Mejorar las pruebas y la accesibilidad de la interfaz.

### Alcance propuesto

1. Localizar textos visibles codificados directamente en `res/layout`.
2. Localizar títulos, mensajes, botones, avisos y `Toast` escritos directamente en Kotlin.
3. Crear nombres descriptivos y estables en `strings.xml`.
4. Sustituir los textos estáticos de XML por referencias `@string/...`.
5. Sustituir en Kotlin los textos simples por `getString(R.string...)`.
6. Utilizar recursos con parámetros para cantidades, nombres de libros y mensajes dinámicos.
7. Crear recursos plurales para citas, libros, marcadores, elementos y resultados cuando corresponda.
8. Conservar fuera de `strings.xml` únicamente datos técnicos que no sean visibles para el usuario.

### Reglas para la migración

- Usar nombres completos y legibles; evitar abreviaturas ambiguas.
- No modificar simultáneamente el comportamiento de las pantallas.
- Migrar por módulo para facilitar la revisión: biblioteca, lector, diccionarios, citas, configuración y Drive.
- Verificar acentos, signos, interpolaciones y plurales después de cada módulo.
- Mantener el español como idioma predeterminado.
- Añadir otros idiomas en directorios como `values-en` solamente cuando exista una traducción revisada.

### Validación necesaria

- Buscar nuevamente textos visibles escritos directamente en Kotlin y XML.
- Comprobar que no aparezcan identificadores de recursos en la interfaz.
- Revisar textos dinámicos en singular y plural.
- Verificar todas las pantallas en Android Studio y en un dispositivo.
- Ejecutar las pruebas del proyecto sin generar un APK salvo que se solicite expresamente.
