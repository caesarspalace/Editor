# Editor de Texto Interactivo en JavaFX con Búfer Circular

Un editor de texto GUI desarrollado en **JavaFX** con soporte para edición en tiempo real, formato dinámico (tipo de fuente, tamaño, color), representación visual mediante un `Canvas`, gestión del búfer mediante una estructura de datos de **Búfer Circular personalizado**, y exportación/importación en formato enriquecido comprimido (`.cna` / `.zip`).

---

## 📋 Tabla de Contenidos

- [Características Principales](#-características-principales)
- [Arquitectura del Proyecto](#-arquitectura-del-proyecto)
  - [Estructura de Clases](#estructura-de-clases)
  - [Estructura de Datos Principal (CircularBuffer)](#estructura-de-datos-principal-circularbuffer)
  - [Renderizado Visual en Canvas](#renderizado-visual-en-canvas)
  - [Persistencia y Formato Propietario (.cna)](#persistencia-y-formato-propietario-cna)
- [Requisitos del Sistema](#-requisitos-del-sistema)
- [Cómo Ejecutar el Proyecto](#-cómo-ejecutar-el-proyecto)
  - [Opción 1: Con Maven / Gradle](#opción-1-con-maven--gradle)
  - [Opción 2: Compilación y Ejecución Manual](#opción-2-compilación-y-ejecución-manual)
- [Flujo de Trabajo y Guía de Uso](#-flujo-de-trabajo-y-guía-de-uso)

---

## 🚀 Características Principales

- **Edición Multisistema mediante Pestañas (`TabPane`):** Permite abrir y trabajar con múltiples archivos simultáneamente.
- **Renderizado Customizado en `Canvas`:** Manejo fino de coordenadas visuales, ajuste de salto de línea automático (*word wrap*), tabulación y representación gráfica del cursor interactivo.
- **Búfer Circular Optimizado:** Implementación eficiente en memoria para inserción, eliminación y consulta de caracteres enriquecidos (`StyledChar`).
- **Posicionamiento Interactivo del Cursor:** 
  - Navegación precisa mediante teclado (flechas de dirección, `ENTER`, `BACKSPACE`).
  - Navegación mediante clic de mouse (`setOnMouseClicked`) calculando el carácter más cercano según las coordenadas $X, Y$.
- **Formatos Personalizados:** Aplicación de tipografía (`Font`), tamaño de letra y colores (`Color`) dinámicos.
- **Scrollbar Personalizado:** Sincronización bi-direccional del desplazamiento vertical basada en líneas lógicas y estado de arrastre del usuario.
- **Búsqueda y Reemplazo de Texto:** Herramientas integradas para búsqueda de cadenas y reemplazo interactivo con actualización en tiempo real.
- **Guardado en Formato Enriquecido (`.cna`):** Compresión ZIP que encapsula la estructura JSON con cada carácter y sus propiedades de formato.
- **Exportación a Texto Plano (`.txt`) e Impresión Multi-página:** Integración con la API `PrinterJob` de JavaFX para impresión física/virtual de páginas completas.

---

## 🏗️ Arquitectura del Proyecto

### Estructura de Clases

```text
src/
└── com/
    └── editor/
        ├── model/
        │   ├── CircularBuffer.java  # Estructura de datos principal (Búfer Circular)
        │   └── StyledChar.java      # Representación de un carácter con estilos (Fuente, Tamaño, Color)
        └── controller/
            └── EditorController.java # Control de GUI, Canvas, Scrollbar, E/S de archivos e Impresión
```

1. **`StyledChar` (Modelo):** Encapsula el carácter base (`char`), la fuente (`Font`) y el color (`Color`).
2. **`CircularBuffer` (Modelo):** Administra el almacenamiento de los objetos `StyledChar`. Proporciona métodos para reasignación dinámica de capacidad y reordenamiento lógico de elementos.
3. **`EditorController` (Controlador):** Modulo principal de la interfaz JavaFX. Gestiona:
   - Eventos de teclado y mouse en el canvas.
   - Cálculo de la lista de coordenadas visuales (`CharPos`).
   - Sincronización del desplazamiento de vista (`viewStartIndex`) con la barra de scroll (`ScrollBar`).
   - Operaciones de E/S de archivos (JSON, TXT, CNA).
   - Diálogos de diálogo e impresión en papel.

---

### Estructura de Datos Principal (`CircularBuffer`)

A diferencia de un arreglo tradicional o lista enlazada estándar, la clase `CircularBuffer` permite almacenar y desplazar los caracteres con alta eficiencia:

- **Estructura Interna:** Un arreglo circular de `StyledChar[]` con un puntero o índice de inicio/fin.
- **Inserción y Eliminación:** Al realizar operaciones de edición en una posición específica (`cursorIndex`), los caracteres se desplazan adecuadamente dentro del rango válido sin necesidad de duplicar el búfer completo constantemente.
- **Capacidad Dinámica:** En caso de sobrepasar el tamaño límite del búfer, este escala automáticamente preservando el orden lógico de los caracteres insertados.

---

### Renderizado Visual en Canvas

El método `renderBufferToCanvas(TabState state)` se encarga del redibujado:

1. **Mapeo de Coordenadas:** Recorre los caracteres desde el índice visible (`viewStartIndex`) hasta llenar el alto visible del canvas.
2. **Mapeo `CharPos`:** Por cada carácter renderizado, se calcula su coordenada $(X, Y)$ y se guarda en la lista interna `charPositions`.
3. **Posicionamiento por Clic:** Cuando el usuario hace clic en el canvas, `handleCanvasMouseClick` busca en `charPositions` el registro con la menor distancia euclidiana respecto al punto de clic y actualiza `cursorIndex`.
4. **Dibujo del Cursor:** Un cursor tipo barra vertical es dibujado sobre la posición calculada del `cursorIndex`.

---

### Persistencia y Formato Propietario (`.cna`)

El formato `.cna` es un contenedor comprimido (estándar ZIP) que contiene un archivo `document.json`:

```json
[
  {"c": "H", "font": "Consolas", "size": 14, "color": "0x000000ff"},
  {"c": "o", "font": "Consolas", "size": 14, "color": "0x000000ff"},
  {"c": "l", "font": "Consolas", "size": 14, "color": "0x000000ff"},
  {"c": "a", "font": "Consolas", "size": 14, "color": "0x000000ff"},
  {"c": "\\n", "font": "Consolas", "size": 14, "color": "0x000000ff"}
]
```

Al abrir un archivo `.cna`, el controlador descomprime dinámicamente el `document.json`, lee la secuencia de estilos y reconstruye la representación interna dentro del `CircularBuffer`.

---

## 💻 Requisitos del Sistema

- **Java Development Kit (JDK):** Versión 17 o superior.
- **JavaFX SDK:** Versión 17 o superior (si no se gestiona mediante Maven/Gradle).
- **IDE:** IntelliJ IDEA, Eclipse, NetBeans o VS Code (opcional).

---

## 🛠️ Cómo Ejecutar el Proyecto

### Opción 1: Con Maven / Gradle (Recomendado)

Si tu proyecto utiliza Maven, asegúrate de tener las dependencias de JavaFX en tu archivo `pom.xml`:

```xml
<dependencies>
    <dependency>
        <groupId>org.openjfx</groupId>
        <artifactId>javafx-controls</artifactId>
        <version>17.0.2</version>
    </dependency>
    <dependency>
        <groupId>org.openjfx</groupId>
        <artifactId>javafx-fxml</artifactId>
        <version>17.0.2</version>
    </dependency>
</dependencies>
```

Ejecuta el proyecto con el comando:

```bash
mvn clean javafx:run
```

---

### Opción 2: Compilación y Ejecución Manual

1. Descarga e instala **JavaFX SDK**.
2. Compila las clases de la estructura definiendo el *module-path*:

```bash
javac --module-path /ruta/a/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml -d bin src/com/editor/model/*.java src/com/editor/controller/*.java
```

3. Ejecuta la aplicación:

```bash
java --module-path /ruta/a/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml -cp bin com.editor.Main
```

---

## 📖 Flujo de Trabajo y Guía de Uso

1. **Crear / Abrir Documentos:**
   - Usa los botones o accesos directos para crear pestañas nuevas.
   - En el menú/opciones de abrir, selecciona un archivo de texto `.txt` o un archivo enriquecido `.cna`.
2. **Formato en Tiempo Real:**
   - Selecciona la tipografía, tamaño de fuente y color desde la barra superior antes de escribir para cambiar el estilo de los caracteres que vayas agregando.
3. **Navegación:**
   - Desplázate con el teclado (Flechas, Enter, Backspace).
   - Haz clic directo sobre cualquier palabra o línea del texto para situar el cursor de edición allí inmediatamente.
   - Utiliza la barra de desplazamiento lateral (`ScrollBar`) para navegar en documentos extensos.
4. **Búsqueda y Reemplazo:**
   - Accede a las herramientas de buscar o reemplazar para modificar coincidencias dentro del búfer de texto.
5. **Impresión:**
   - La opción de impresión fragmenta automáticamente el búfer en páginas adaptadas a los márgenes y resolución del controlador de impresión predeterminado.