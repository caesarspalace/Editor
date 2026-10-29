package com.editor.controller;

import com.editor.model.CircularBuffer;
import com.editor.model.StyledChar;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextFlow;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.HBox;
import javafx.geometry.Orientation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javafx.print.PrinterJob;
import javafx.scene.text.Text;

public class EditorController {

    @FXML
    private TabPane tabPane;
    @FXML
    private ComboBox<String> fontFamilyCombo;
    @FXML
    private TextField fontSizeField;
    @FXML
    private ComboBox<String> fontColorCombo;
  
    private String currentFontName = "Consolas";
    private double currentFontSize = 14.0;
    private Color currentColor = Color.BLACK;

    private static class TabState {
        CircularBuffer buffer;
        int cursorIndex = 0;
        int viewStartIndex = 0;
        Canvas canvas;
        Tab tab;
        File currentFile = null;
        List<CharPos> charPositions = new ArrayList<>();
        private ScrollBar scrollBar;
        public boolean isProgrammaticChange = false;
        public boolean isUserDragging = false;

        // Variables para selección de texto
        int selectionStart = -1;
        int selectionEnd = -1;
        boolean isSelectingWithMouse = false;
    }

    private static class CharPos {
        int logicalIndex;
        double x, y;
        int line;

        public CharPos(int logicalIndex, double x, double y, int line) {
            this.logicalIndex = logicalIndex;
            this.x = x;
            this.y = y;
            this.line = line;
        }
    }

    @FXML
    public void initialize() {
        fontFamilyCombo.getItems().addAll(Font.getFamilies());
        fontFamilyCombo.setValue("Consolas");

        fontColorCombo.getItems().addAll("BLACK", "BLUE", "GREEN", "RED");
        fontColorCombo.setValue("BLACK");

        fontFamilyCombo.setOnAction(e -> {
            String selected = fontFamilyCombo.getValue();
            if (selected != null) currentFontName = selected;
            requestFocusOnActiveCanvas();
        });

        fontColorCombo.setOnAction(e -> {
            String selected = fontColorCombo.getValue();
            if (selected != null) {
                switch (selected) {
                    case "BLUE": currentColor = Color.BLUE; break;
                    case "GREEN": currentColor = Color.GREEN; break;
                    case "RED": currentColor = Color.RED; break;
                    default: currentColor = Color.BLACK; break;
                }
            }
            requestFocusOnActiveCanvas();
        });

        fontSizeField.textProperty().addListener((observable, oldValue, newValue) -> {
            try {
                if (!newValue.isEmpty()) {
                    double size = Double.parseDouble(newValue);
                    if (size > 0 && size <= 100) {
                        currentFontSize = size;
                    }
                }
            } catch (NumberFormatException ignored) {}
        });

        createNewEmptyTab("Sin título");

        // Listener global para asegurar que al cambiar de pestaña el Canvas recupere el foco
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                Platform.runLater(() -> {
                    TabState state = (TabState) newTab.getProperties().get("tabState");
                    if (state != null && state.canvas != null) {
                        state.canvas.requestFocus();
                    }
                });
            }
        });
    }

    private void requestFocusOnActiveCanvas() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            TabState state = (TabState) selectedTab.getProperties().get("tabState");
            if (state != null && state.canvas != null) {
                state.canvas.requestFocus();
            }
        }
    }

    @FXML
    private void handleNew() {
        createNewEmptyTab("Sin título");
    }

    @FXML
    private void handleOpen() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Abrir archivo");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Archivo CNA (*.cna)", "*.cna"),
            new FileChooser.ExtensionFilter("Archivos ZIP (*.zip)", "*.zip"),
            new FileChooser.ExtensionFilter("Archivos JSON (*.json)", "*.json"),
            new FileChooser.ExtensionFilter("Texto Plano (*.txt)", "*.txt"),
            new FileChooser.ExtensionFilter("Todos los archivos", "*.*")
        );
        File file = fileChooser.showOpenDialog(tabPane.getScene().getWindow());
        if (file != null) {
            openFileInTab(file);
        }
    }

    @FXML
    private void handleSave() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            TabState state = (TabState) selectedTab.getProperties().get("tabState");
            if (state != null) {
                if (state.currentFile != null) {
                    saveBufferToCna(state, state.currentFile);
                } else {
                    handleSaveAs();
                }
            }
        }
    }

    @FXML
    private void handleSaveAs() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            TabState state = (TabState) selectedTab.getProperties().get("tabState");
            if (state != null) {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Guardar archivo CNA como");
                fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Archivo CNA (*.cna)", "*.cna")
                );
                File file = fileChooser.showSaveDialog(tabPane.getScene().getWindow());
                
                if (file != null) {
                    if (!file.getName().toLowerCase().endsWith(".cna")) {
                        file = new File(file.getAbsolutePath() + ".cna");
                    }
                    saveBufferToCna(state, file);
                    state.currentFile = file;
                    state.tab.setText(file.getName());
                }
            }
        }
    }

    @FXML
    private void handleSavePlainText() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            TabState state = (TabState) selectedTab.getProperties().get("tabState");
            if (state != null) {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Guardar como texto plano");
                fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Archivo de texto plano (*.txt)", "*.txt")
                );
                File file = fileChooser.showSaveDialog(tabPane.getScene().getWindow());
                
                if (file != null) {
                    try {
                        StringBuilder sb = new StringBuilder();
                        int size = state.buffer.getSize();
                        for (int i = 0; i < size; i++) {
                            StyledChar sc = state.buffer.get(i);
                            if (sc != null) {
                                sb.append(sc.getCharacter());
                            }
                        }
                        Files.writeString(file.toPath(), sb.toString());
                        System.out.println("Texto plano guardado en: " + file.getAbsolutePath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    @FXML
    private void handleExecuteCommand() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            TabState state = (TabState) selectedTab.getProperties().get("tabState");
            if (state != null) {
                javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
                dialog.setTitle("Traducir Comando SQL");
                dialog.setHeaderText("Ingrese las instrucciones para traducir a SQL:");

                javafx.scene.control.ButtonType executeBtnType = new javafx.scene.control.ButtonType("Traducir e Insertar", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
                dialog.getDialogPane().getButtonTypes().addAll(executeBtnType, javafx.scene.control.ButtonType.CANCEL);

                javafx.scene.control.TextArea textArea = new javafx.scene.control.TextArea();
                textArea.setPromptText("Ejemplo:\ncreate tabla1 columna fecha formato fecha columna nombre formato texto");
                textArea.setPrefRowCount(6);
                textArea.setPrefColumnCount(40);
                textArea.setWrapText(true);

                javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(10);
                vbox.setPadding(new javafx.geometry.Insets(15));
                vbox.getChildren().addAll(new javafx.scene.control.Label("Comando:"), textArea);

                dialog.getDialogPane().setContent(vbox);

                Platform.runLater(textArea::requestFocus);

                dialog.setResultConverter(dialogButton -> {
                    if (dialogButton == executeBtnType) {
                        return textArea.getText();
                    }
                    return null;
                });

                java.util.Optional<String> result = dialog.showAndWait();
                result.ifPresent(cmd -> {
                    if (cmd.trim().isEmpty()) return;

                    String translated = com.editor.parser.CommandParser.parseCommand(cmd);

                    Font dynamicFont = Font.font(currentFontName, currentFontSize);
                    for (int i = 0; i < translated.length(); i++) {
                        char c = translated.charAt(i);
                        state.buffer.insert(state.cursorIndex, new StyledChar(c, dynamicFont, currentColor));
                        state.cursorIndex++;
                    }

                    ensureCursorVisible(state);
                    renderBufferToCanvas(state);
                });

                if (state.canvas != null) {
                    Platform.runLater(state.canvas::requestFocus);
                }
            }
        }
    }

    private void saveBufferToCna(TabState state, File file) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            int size = state.buffer.getSize();
            for (int i = 0; i < size; i++) {
                StyledChar sc = state.buffer.get(i);
                if (sc == null) continue;

                String charRepresentation = String.valueOf(sc.getCharacter());
                if (sc.getCharacter() == '\n') charRepresentation = "\\n";
                else if (sc.getCharacter() == '\t') charRepresentation = "\\t";
                else if (sc.getCharacter() == '\"') charRepresentation = "\\\"";
                else if (sc.getCharacter() == '\\') charRepresentation = "\\\\";

                sb.append(String.format("  {\"c\": \"%s\", \"font\": \"%s\", \"size\": %.0f, \"color\": \"%s\"}",
                        charRepresentation,
                        sc.getFontName(),
                        sc.getFontSize(),
                        sc.getColor().toString()));

                if (i < size - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("]");

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
                ZipEntry entry = new ZipEntry("document.json");
                zos.putNextEntry(entry);
                byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
                zos.write(data, 0, data.length);
                zos.closeEntry();
            }

            System.out.println("Archivo CNA guardado exitosamente en: " + file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createNewEmptyTab(String title) {
        Tab tab = new Tab(title);
        HBox contentBox = new HBox(5);
        Canvas canvas = new Canvas(850, 500);

        TabState state = new TabState();
        ScrollBar scrollBar = new ScrollBar();
        scrollBar.setOrientation(javafx.geometry.Orientation.VERTICAL);
        scrollBar.setPrefHeight(500);
        scrollBar.prefHeightProperty().bind(canvas.heightProperty());
        javafx.scene.layout.HBox.setHgrow(canvas, javafx.scene.layout.Priority.ALWAYS);
        scrollBar.setMin(0);
        scrollBar.setUnitIncrement(1);
        scrollBar.setBlockIncrement(10);
        
        state.buffer = new CircularBuffer(50000, 1000);
        state.canvas = canvas;
        state.scrollBar = scrollBar;
        state.tab = tab;
        tab.getProperties().put("tabState", state);

        scrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (state.isProgrammaticChange || state.buffer == null) {
                return;
            }

            int targetLine = newVal.intValue();
            int charIndex = 0;
            int currentLine = 1;
            int size = state.buffer.getSize();

            for (int i = 0; i < size; i++) {
                if (currentLine >= targetLine) {
                    charIndex = i;
                    break;
                }
                StyledChar sc = state.buffer.get(i);
                if (sc != null && sc.getCharacter() == '\n') {
                    currentLine++;
                    charIndex = i + 1;
                }
            }

            if (state.viewStartIndex != charIndex) {
                state.viewStartIndex = charIndex;
                renderBufferToCanvas(state);
                canvas.requestFocus();
            }
        });

        scrollBar.setOnMousePressed(e -> state.isUserDragging = true);
        scrollBar.setOnMouseReleased(e -> {
            state.isUserDragging = false;
            updateScrollBar(state);
        });

        setupCanvasInteractivity(state);
        renderBufferToCanvas(state);

        contentBox.getChildren().addAll(canvas, scrollBar);
        tab.setContent(contentBox);
           
        tabPane.getTabs().addFirst(tab);
        tabPane.getSelectionModel().select(tab);

        Platform.runLater(canvas::requestFocus);
    }

    private void openFileInTab(File file) {
        Tab tab = new Tab(file.getName());
        HBox contentBox = new HBox(5);
        Canvas canvas = new Canvas(850, 500);

        TabState state = new TabState();
        ScrollBar scrollBar = new ScrollBar();
        scrollBar.setOrientation(javafx.geometry.Orientation.VERTICAL);
        scrollBar.setPrefHeight(500);
        scrollBar.prefHeightProperty().bind(canvas.heightProperty());
        javafx.scene.layout.HBox.setHgrow(canvas, javafx.scene.layout.Priority.ALWAYS);
        scrollBar.setMin(0);
        scrollBar.setUnitIncrement(1);
        scrollBar.setBlockIncrement(10);
        
        state.buffer = new CircularBuffer(50000, 1000);
        state.canvas = canvas;
        state.scrollBar = scrollBar;
        state.tab = tab;
        state.currentFile = file;
        tab.getProperties().put("tabState", state);

        scrollBar.setOnMousePressed(e -> state.isUserDragging = true);
        scrollBar.setOnMouseReleased(e -> {
            state.isUserDragging = false;
            updateScrollBar(state);
        });

        scrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (state.isProgrammaticChange || state.buffer == null) {
                return;
            }

            int targetLine = newVal.intValue();
            int charIndex = 0;
            int currentLine = 1;
            int size = state.buffer.getSize();

            for (int i = 0; i < size; i++) {
                if (currentLine >= targetLine) {
                    charIndex = i;
                    break;
                }
                StyledChar sc = state.buffer.get(i);
                if (sc != null && sc.getCharacter() == '\n') {
                    currentLine++;
                    charIndex = i + 1;
                }
            }

            if (state.viewStartIndex != charIndex) {
                state.viewStartIndex = charIndex;
                renderBufferToCanvas(state);
                canvas.requestFocus();
            }
        });

        try {
            String content = "";
            String fileName = file.getName().toLowerCase();
            
            if (fileName.endsWith(".cna") || fileName.endsWith(".zip")) {
                try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (entry.getName().endsWith(".json") || entry.getName().equals("document.json")) {
                            byte[] bufferBytes = zis.readAllBytes();
                            content = new String(bufferBytes, StandardCharsets.UTF_8);
                            break;
                        }
                        zis.closeEntry();
                    }
                }
            } else {
                content = Files.readString(file.toPath());
            }

            if (content.trim().startsWith("[")) {
                parseStyledJsonContent(content, state.buffer);
            } else {
                Font defaultFont = Font.font(currentFontName, currentFontSize);
                for (char c : content.toCharArray()) {
                    state.buffer.write(new StyledChar(c, defaultFont, currentColor));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        setupCanvasInteractivity(state);
        renderBufferToCanvas(state);

        contentBox.getChildren().addAll(canvas, scrollBar);
        tab.setContent(contentBox);
        tabPane.getTabs().addFirst(tab);
        tabPane.getSelectionModel().select(tab);

        Platform.runLater(canvas::requestFocus);
    }

    private void parseStyledJsonContent(String json, CircularBuffer buffer) {
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("]")) trimmed = trimmed.substring(0, trimmed.length() - 1);

        String[] entries = trimmed.split("\\},\\s*\\{");
        
        for (String entry : entries) {
            try {
                String cleanEntry = entry.trim();
                if (!cleanEntry.startsWith("{")) cleanEntry = "{" + cleanEntry;
                if (!cleanEntry.endsWith("}")) cleanEntry = cleanEntry + "}";

                String cStr = extractJsonField(cleanEntry, "c");
                String font = extractJsonField(cleanEntry, "font");
                double size = Double.parseDouble(extractJsonField(cleanEntry, "size"));
                String colorStr = extractJsonField(cleanEntry, "color");

                char c = cStr.isEmpty() ? ' ' : cStr.charAt(0);
                if (cStr.equals("\\n")) c = '\n';
                else if (cStr.equals("\\t")) c = '\t';
                else if (cStr.equals("\\\"")) c = '\"';
                else if (cStr.equals("\\\\")) c = '\\';

                Color color = Color.web(colorStr);
                Font f = Font.font(font, size);

                buffer.write(new StyledChar(c, f, color));
            } catch (Exception ignored) {}
        }
    }

    private String extractJsonField(String json, String field) {
        String search = "\"" + field + "\":";
        int idx = json.indexOf(search);
        if (idx == -1) return "";
        idx += search.length();
        
        while (idx < json.length() && (json.charAt(idx) == ' ' || json.charAt(idx) == '\n' || json.charAt(idx) == '\r' || json.charAt(idx) == '\t')) {
            idx++;
        }
        
        if (idx >= json.length()) return "";
        
        if (json.charAt(idx) == '\"') {
            idx++;
            int startIdx = idx;
            while (idx < json.length()) {
                if (json.charAt(idx) == '\"' && json.charAt(idx - 1) != '\\') {
                    break;
                }
                idx++;
            }
            return json.substring(startIdx, idx).replace("\\\"", "\"");
        } else {
            int startIdx = idx;
            while (idx < json.length() && json.charAt(idx) != ',' && json.charAt(idx) != '}') {
                idx++;
            }
            return json.substring(startIdx, idx).trim();
        }
    }

    private void setupCanvasInteractivity(TabState state) {
        Canvas canvas = state.canvas;
        canvas.setFocusTraversable(true);

        // --- MANEJO DEL MOUSE (SELECCIÓN POR ARRASTRE) ---
        canvas.setOnMousePressed(e -> {
            canvas.requestFocus(); // Forzar el foco de inmediato al hacer clic
            handleCanvasMouseClick(e, state);
            state.selectionStart = state.cursorIndex;
            state.selectionEnd = state.cursorIndex;
            state.isSelectingWithMouse = true;
            renderBufferToCanvas(state);
            canvas.requestFocus(); // Asegurar el foco también tras el render
        });

        canvas.setOnMouseDragged(e -> {
            if (state.isSelectingWithMouse) {
                handleCanvasMouseClick(e, state);
                state.selectionEnd = state.cursorIndex;
                renderBufferToCanvas(state);
            }
        });

        canvas.setOnMouseReleased(e -> {
            state.isSelectingWithMouse = false;
            canvas.requestFocus(); // Devolver el foco al soltar el mouse
        });

        // --- MANEJO DE TECLAS Y ATAJOS ---
        canvas.setOnKeyPressed(event -> {
            if (event.isShortcutDown()) {
                if (event.getCode() == KeyCode.C) {
                    copySelectedText(state);
                    canvas.requestFocus();
                    event.consume();
                    return;
                } else if (event.getCode() == KeyCode.V) {
                    pasteFromClipboard(state);
                    canvas.requestFocus();
                    event.consume();
                    return;
                } else if (event.getCode() == KeyCode.X) {
                    copySelectedText(state);
                    deleteSelection(state);
                    ensureCursorVisible(state);
                    renderBufferToCanvas(state);
                    canvas.requestFocus();
                    event.consume();
                    return;
                } else if (event.getCode() == KeyCode.A) {
                    state.selectionStart = 0;
                    state.selectionEnd = state.buffer.getSize();
                    renderBufferToCanvas(state);
                    canvas.requestFocus();
                    event.consume();
                    return;
                }
            }

            if (event.getCode() == KeyCode.BACK_SPACE) {
                if (!deleteSelection(state)) {
                    if (state.cursorIndex > 0) {
                        state.buffer.delete(state.cursorIndex - 1);
                        state.cursorIndex--;
                    }
                }
                ensureCursorVisible(state);
                renderBufferToCanvas(state);
                canvas.requestFocus();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                deleteSelection(state);
                Font dynamicFont = Font.font(currentFontName, currentFontSize);
                state.buffer.insert(state.cursorIndex, new StyledChar('\n', dynamicFont, currentColor));
                state.cursorIndex++;
                ensureCursorVisible(state);
                renderBufferToCanvas(state);
                canvas.requestFocus();
                event.consume();
            } else if (event.getCode() == KeyCode.LEFT || event.getCode() == KeyCode.RIGHT) {
                if (!event.isShiftDown()) {
                    state.selectionStart = -1;
                    state.selectionEnd = -1;
                }
                if (event.getCode() == KeyCode.LEFT && state.cursorIndex > 0) {
                    state.cursorIndex--;
                } else if (event.getCode() == KeyCode.RIGHT && state.cursorIndex < state.buffer.getSize()) {
                    state.cursorIndex++;
                }
                ensureCursorVisible(state);
                renderBufferToCanvas(state);
                canvas.requestFocus();
            } else if (event.getCode() == KeyCode.UP || event.getCode() == KeyCode.DOWN) {
                if (!event.isShiftDown()) {
                    state.selectionStart = -1;
                    state.selectionEnd = -1;
                }
                navigateVertical(state, event.getCode() == KeyCode.UP ? -1 : 1);
                ensureCursorVisible(state);
                renderBufferToCanvas(state);
                event.consume();
                canvas.requestFocus();
            }
        });

        canvas.setOnKeyTyped(event -> {
            String character = event.getCharacter();
            if (!character.isEmpty() && !event.isShortcutDown()) {
                char c = character.charAt(0);
                if (c >= 32 || c == '\t' || c == '\r' || c == '\n') {
                    deleteSelection(state);
                    Font dynamicFont = Font.font(currentFontName, currentFontSize);
                    state.buffer.insert(state.cursorIndex, new StyledChar(c, dynamicFont, currentColor));
                    state.cursorIndex++;
                    ensureCursorVisible(state);
                    renderBufferToCanvas(state);
                    canvas.requestFocus();
                }
            }
        });
    }

    private void ensureCursorVisible(TabState state) {
        for (int safety = 0; safety < 50; safety++) {
            renderBufferToCanvas(state);

            boolean found = false;
            int minIdx = state.charPositions.isEmpty() ? state.viewStartIndex : state.charPositions.get(0).logicalIndex;
            int maxIdx = state.charPositions.isEmpty() ? state.viewStartIndex : state.charPositions.get(state.charPositions.size() - 1).logicalIndex;

            for (CharPos cp : state.charPositions) {
                if (cp.logicalIndex == state.cursorIndex) {
                    found = true;
                    break;
                }
            }

            if (found) break;

            if (state.cursorIndex > maxIdx) {
                int nextNewline = findNextNewline(state, state.viewStartIndex);
                if (nextNewline > state.viewStartIndex) {
                    state.viewStartIndex = nextNewline;
                } else {
                    state.viewStartIndex = Math.min(state.buffer.getSize(), state.viewStartIndex + 20);
                }
            } else if (state.cursorIndex < minIdx) {
                state.viewStartIndex = Math.max(0, state.viewStartIndex - 40);
            } else {
                break;
            }
        }
    }

    private int findNextNewline(TabState state, int start) {
        for (int i = start; i < state.buffer.getSize(); i++) {
            StyledChar sc = state.buffer.get(i);
            if (sc != null && sc.getCharacter() == '\n') {
                return i + 1;
            }
        }
        return start + 25;
    }

    private void navigateVertical(TabState state, int direction) {
        if (state.charPositions.isEmpty()) return;

        CharPos currentPos = null;
        for (CharPos cp : state.charPositions) {
            if (cp.logicalIndex == state.cursorIndex) {
                currentPos = cp;
                break;
            }
        }

        if (currentPos == null && state.cursorIndex == state.buffer.getSize()) {
            currentPos = state.charPositions.get(state.charPositions.size() - 1);
        }
        if (currentPos == null) return;

        int targetLine = currentPos.line + direction;
        double targetX = currentPos.x;

        CharPos bestMatch = null;
        double minDistance = Double.MAX_VALUE;

        for (CharPos cp : state.charPositions) {
            if (cp.line == targetLine) {
                double dist = Math.abs(cp.x - targetX);
                if (dist < minDistance) {
                    minDistance = dist;
                    bestMatch = cp;
                }
            }
        }

        if (bestMatch != null) {
            state.cursorIndex = bestMatch.logicalIndex;
        } else if (direction > 0 && state.cursorIndex < state.buffer.getSize()) {
            state.cursorIndex = Math.min(state.buffer.getSize(), state.cursorIndex + 15);
        } else if (direction < 0 && state.cursorIndex > 0) {
            state.cursorIndex = Math.max(0, state.cursorIndex - 15);
        }
    }

    private void renderBufferToCanvas(TabState state) {
        Canvas canvas = state.canvas;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        
        state.charPositions.clear();

        double startX = 20;
        double startY = 30;
        double x = startX;
        double y = startY;
        double lineHeight = 22;
        double maxWidth = canvas.getWidth() - 30;
        double maxHeight = canvas.getHeight() - 30;
        int currentLine = 0;

        int i = state.viewStartIndex;
        state.charPositions.add(new CharPos(i, x, y, currentLine));

        while (i < state.buffer.getSize()) {
            StyledChar sc = state.buffer.get(i);
            if (sc == null) {
                i++;
                continue;
            }

            int globalLogicalIndex = i + 1;

            if (sc.getCharacter() == '\n') {
                x = startX;
                y += lineHeight;
                currentLine++;
                
                if (y > maxHeight) {
                    break;
                }
                
                state.charPositions.add(new CharPos(globalLogicalIndex, x, y, currentLine));
                i++;
                continue;
            }

            if (sc.getCharacter() == '\t') {
                x += 30;
                state.charPositions.add(new CharPos(globalLogicalIndex, x, y, currentLine));
                i++;
                continue;
            }

            int selMin = Math.min(state.selectionStart, state.selectionEnd);
            int selMax = Math.max(state.selectionStart, state.selectionEnd);

            if (state.selectionStart != -1 && state.selectionEnd != -1 && i >= selMin && i < selMax) {
                gc.setFill(Color.LIGHTBLUE);
                gc.fillRect(x, y - 16, sc.getFontSize() * 0.65, 20);
            }

            gc.setFont(Font.font(sc.getFontName(), sc.getFontSize()));
            gc.setFill(sc.getColor());
            gc.fillText(String.valueOf(sc.getCharacter()), x, y);

            x += (sc.getFontSize() * 0.6); 

            if (x > maxWidth) {
                x = startX;
                y += lineHeight;
                currentLine++;
                
                if (y > maxHeight) {
                    break;
                }
            }

            state.charPositions.add(new CharPos(globalLogicalIndex, x, y, currentLine));
            i++;
        }
        updateScrollBar(state);
        drawCursor(gc, state);
    }

    private void updateScrollBar(TabState state) {
        if (state.scrollBar != null && state.buffer != null) {
            if (state.isUserDragging) {
                return;
            }

            int size = state.buffer.getSize();
            
            int totalLines = 1;
            for (int i = 0; i < size; i++) {
                StyledChar sc = state.buffer.get(i);
                if (sc != null && sc.getCharacter() == '\n') {
                    totalLines++;
                }
            }

            state.scrollBar.setMin(1);
            state.scrollBar.setMax(Math.max(1, totalLines));
            state.scrollBar.setUnitIncrement(1);
            state.scrollBar.setBlockIncrement(10);

            int currentLine = 1;
            for (int i = 0; i < Math.min(state.viewStartIndex, size); i++) {
                StyledChar sc = state.buffer.get(i);
                if (sc != null && sc.getCharacter() == '\n') {
                    currentLine++;
                }
            }

            state.isProgrammaticChange = true;
            if ((int) state.scrollBar.getValue() != currentLine) {
                state.scrollBar.setValue(currentLine);
            }
            state.isProgrammaticChange = false;
        }
    }
    
    private void drawCursor(GraphicsContext gc, TabState state) {
        CharPos cursorCharPos = null;
        for (CharPos cp : state.charPositions) {
            if (cp.logicalIndex == state.cursorIndex) {
                cursorCharPos = cp;
                break;
            }
        }

        if (cursorCharPos != null) {
            gc.setStroke(Color.BLUE);
            gc.setLineWidth(1.5);
            gc.strokeLine(cursorCharPos.x, cursorCharPos.y - 14, cursorCharPos.x, cursorCharPos.y + 4);
        }
    }

    @FXML
    private void handlePrint() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            TabState state = (TabState) selectedTab.getProperties().get("tabState");
            if (state != null) {
                PrinterJob job = PrinterJob.createPrinterJob();
                if (job != null) {
                    boolean proceed = job.showPrintDialog(tabPane.getScene().getWindow());
                    if (proceed) {
                        javafx.print.PageLayout pageLayout = job.getJobSettings().getPageLayout();
                        double printableWidth = pageLayout.getPrintableWidth();
                        double printableHeight = pageLayout.getPrintableHeight();

                        int size = state.buffer.getSize();
                        if (size == 0) {
                            job.endJob();
                            return;
                        }

                        boolean success = true;
                        int currentCharIndex = 0;

                        while (currentCharIndex < size && success) {
                            TextFlow pageFlow = new TextFlow();
                            pageFlow.setPrefWidth(printableWidth);
                            pageFlow.setMaxWidth(printableWidth);

                            double estimatedLineHeight = 16.0;
                            double maxAllowedHeight = printableHeight - 50;
                            int linesInCurrentPage = 0;
                            
                            int maxLinesPerPage = Math.max(5, (int) (maxAllowedHeight / estimatedLineHeight));

                            while (currentCharIndex < size && linesInCurrentPage < maxLinesPerPage) {
                                StyledChar firstSc = state.buffer.get(currentCharIndex);
                                if (firstSc == null) {
                                    currentCharIndex++;
                                    continue;
                                }

                                String currentFontFamily = getSafeFontFamily(firstSc);
                                double currentFontSize = getSafeFontSize(firstSc);
                                String currentColor = getSafeColor(firstSc);

                                StringBuilder styleBatchText = new StringBuilder();
                                int tempIndex = currentCharIndex;

                                while (tempIndex < size) {
                                    StyledChar nextSc = state.buffer.get(tempIndex);
                                    if (nextSc == null) break;

                                    String nextFont = getSafeFontFamily(nextSc);
                                    double nextSize = getSafeFontSize(nextSc);
                                    String nextColor = getSafeColor(nextSc);

                                    if (!nextFont.equals(currentFontFamily) || nextSize != currentFontSize || !nextColor.equals(currentColor)) {
                                        break;
                                    }

                                    char c = nextSc.getCharacter();
                                    styleBatchText.append(c);
                                    tempIndex++;

                                    if (c == '\n') {
                                        linesInCurrentPage++;
                                        break;
                                    }
                                }

                                Text textNode = new Text(styleBatchText.toString());
                                textNode.setFont(javafx.scene.text.Font.font(currentFontFamily, currentFontSize));
                                textNode.setFill(javafx.scene.paint.Color.web(currentColor));

                                pageFlow.getChildren().add(textNode);
                                currentCharIndex = tempIndex;
                            }

                            pageFlow.applyCss();
                            pageFlow.layout();

                            success = job.printPage(pageFlow);
                        }

                        if (success) {
                            job.endJob();
                            System.out.println("¡Todas las páginas del archivo fueron enviadas a CUPS con éxito!");
                        } else {
                            System.out.println("La impresión falló durante el proceso.");
                            job.endJob();
                        }
                    } else {
                        job.endJob();
                    }
                } else {
                    System.out.println("No se encontró ninguna impresora disponible en el sistema.");
                }
                if (state.canvas != null) {
                    Platform.runLater(state.canvas::requestFocus);
                }
            }
        }
    }

    private String getSafeFontFamily(StyledChar sc) {
        try {
            String font = sc.getFontName();
            return (font != null && !font.isEmpty()) ? font : "System";
        } catch (Exception e) {
            return "System";
        }
    }

    private double getSafeFontSize(StyledChar sc) {
        try {
            double size = sc.getFontSize();
            return (size > 0) ? size : 12.0;
        } catch (Exception e) {
            return 12.0;
        }
    }

    private String getSafeColor(StyledChar sc) {
        try {
            String color = sc.getColor().toString();
            return (color != null && !color.isEmpty()) ? color : "#000000";
        } catch (Exception e) {
            return "#000000";
        }
    }
    
    @FXML
    private void handleFind() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            TabState state = (TabState) selectedTab.getProperties().get("tabState");
            if (state != null) {
                javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
                dialog.setTitle("Buscar texto");
                dialog.setHeaderText("Buscar una palabra o frase en el documento");
                dialog.setContentText("Texto a buscar:");

                java.util.Optional<String> result = dialog.showAndWait();
                result.ifPresent(query -> {
                    if (query.isEmpty()) return;

                    StringBuilder fullText = new StringBuilder();
                    int size = state.buffer.getSize();
                    for (int i = 0; i < size; i++) {
                        StyledChar sc = state.buffer.get(i);
                        if (sc != null) {
                            fullText.append(sc.getCharacter());
                        }
                    }

                    String textStr = fullText.toString();
                    int foundIndex = textStr.indexOf(query, state.cursorIndex);

                    if (foundIndex == -1) {
                        foundIndex = textStr.indexOf(query, 0);
                    }

                    if (foundIndex != -1) {
                        state.cursorIndex = foundIndex;
                        ensureCursorVisible(state);
                        renderBufferToCanvas(state);
                        System.out.println("Texto encontrado en la posición lógica: " + foundIndex);
                    } else {
                        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                        alert.setTitle("Resultado de búsqueda");
                        alert.setHeaderText(null);
                        alert.setContentText("No se encontró el texto: \"" + query + "\"");
                        alert.showAndWait();
                    }
                });

                if (state.canvas != null) {
                    Platform.runLater(state.canvas::requestFocus);
                }
            }
        }
    }

    @FXML
    private void handleReplace() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            TabState state = (TabState) selectedTab.getProperties().get("tabState");
            if (state != null) {
                javafx.scene.control.Dialog<javafx.util.Pair<String, String>> dialog = new javafx.scene.control.Dialog<>();
                dialog.setTitle("Buscar y Reemplazar");
                dialog.setHeaderText("Reemplazar texto en el documento");

                javafx.scene.control.ButtonType replaceBtnType = new javafx.scene.control.ButtonType("Reemplazar", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
                dialog.getDialogPane().getButtonTypes().addAll(replaceBtnType, javafx.scene.control.ButtonType.CANCEL);

                javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
                grid.setHgap(10);
                grid.setVgap(10);
                grid.setPadding(new javafx.geometry.Insets(20, 20, 10, 10));

                javafx.scene.control.TextField findField = new javafx.scene.control.TextField();
                findField.setPromptText("Texto a buscar");
                javafx.scene.control.TextField replaceField = new javafx.scene.control.TextField();
                replaceField.setPromptText("Texto de reemplazo");

                grid.add(new javafx.scene.control.Label("Buscar:"), 0, 0);
                grid.add(findField, 1, 0);
                grid.add(new javafx.scene.control.Label("Reemplazar con:"), 0, 1);
                grid.add(replaceField, 1, 1);

                dialog.getDialogPane().setContent(grid);

                dialog.setResultConverter(dialogButton -> {
                    if (dialogButton == replaceBtnType) {
                        return new javafx.util.Pair<>(findField.getText(), replaceField.getText());
                    }
                    return null;
                });

                java.util.Optional<javafx.util.Pair<String, String>> result = dialog.showAndWait();
                result.ifPresent(pair -> {
                    String target = pair.getKey();
                    String replacement = pair.getValue();

                    if (target == null || target.isEmpty()) return;

                    StringBuilder fullText = new StringBuilder();
                    int size = state.buffer.getSize();
                    for (int i = 0; i < size; i++) {
                        StyledChar sc = state.buffer.get(i);
                        if (sc != null) {
                            fullText.append(sc.getCharacter());
                        }
                    }

                    String textStr = fullText.toString();
                    int foundIndex = textStr.indexOf(target, state.cursorIndex);
                    
                    if (foundIndex == -1) {
                        foundIndex = textStr.indexOf(target, 0);
                    }

                    if (foundIndex != -1) {
                        for (int i = 0; i < target.length(); i++) {
                            state.buffer.delete(foundIndex);
                        }

                        Font dynamicFont = Font.font(currentFontName, currentFontSize);
                        for (int i = 0; i < replacement.length(); i++) {
                            char c = replacement.charAt(i);
                            state.buffer.insert(foundIndex + i, new StyledChar(c, dynamicFont, currentColor));
                        }

                        state.cursorIndex = foundIndex + replacement.length();
                        ensureCursorVisible(state);
                        renderBufferToCanvas(state);
                        System.out.println("Texto reemplazado en la posición lógica: " + foundIndex);
                    } else {
                        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                        alert.setTitle("Reemplazar");
                        alert.setHeaderText(null);
                        alert.setContentText("No se encontró el texto: \"" + target + "\"");
                        alert.showAndWait();
                    }
                });

                if (state.canvas != null) {
                    Platform.runLater(state.canvas::requestFocus);
                }
            }
        }
    }

    private void handleCanvasMouseClick(javafx.scene.input.MouseEvent event, TabState state) {
        if (state.charPositions.isEmpty()) return;

        double clickX = event.getX();
        double clickY = event.getY();

        CharPos closestChar = null;
        double minDistance = Double.MAX_VALUE;

        for (CharPos cp : state.charPositions) {
            if (clickY >= cp.y - 18 && clickY <= cp.y + 4) {
                double distX = Math.abs(cp.x - clickX);
                if (distX < minDistance) {
                    minDistance = distX;
                    closestChar = cp;
                }
            }
        }

        if (closestChar != null) {
            state.cursorIndex = closestChar.logicalIndex;
        } else {
            closestChar = state.charPositions.get(state.charPositions.size() - 1);
            state.cursorIndex = closestChar.logicalIndex;
        }

        ensureCursorVisible(state);
        renderBufferToCanvas(state);
        state.canvas.requestFocus();
    }

    private void copySelectedText(TabState state) {
        if (state.selectionStart != -1 && state.selectionEnd != -1 && state.selectionStart != state.selectionEnd) {
            int start = Math.min(state.selectionStart, state.selectionEnd);
            int end = Math.max(state.selectionStart, state.selectionEnd);

            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                StyledChar sc = state.buffer.get(i);
                if (sc != null) {
                    sb.append(sc.getCharacter());
                }
            }

            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(sb.toString());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        }
    }

    private void pasteFromClipboard(TabState state) {
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        if (clipboard.hasString()) {
            deleteSelection(state);
            String text = clipboard.getString();
            Font dynamicFont = Font.font(currentFontName, currentFontSize);

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                state.buffer.insert(state.cursorIndex, new StyledChar(c, dynamicFont, currentColor));
                state.cursorIndex++;
            }

            ensureCursorVisible(state);
            renderBufferToCanvas(state);
        }
    }

    private boolean deleteSelection(TabState state) {
        if (state.selectionStart != -1 && state.selectionEnd != -1 && state.selectionStart != state.selectionEnd) {
            int start = Math.min(state.selectionStart, state.selectionEnd);
            int end = Math.max(state.selectionStart, state.selectionEnd);

            int count = end - start;
            for (int i = 0; i < count; i++) {
                state.buffer.delete(start);
            }

            state.cursorIndex = start;
            state.selectionStart = -1;
            state.selectionEnd = -1;
            return true;
        }
        return false;
    }
}