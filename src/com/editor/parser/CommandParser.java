package com.editor.parser;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandParser {

    // Mapa de traducción de palabras clave en español / alias a tipos estándar SQL
    private static final Map<String, String> DATA_TYPES = new HashMap<>();

    static {
        // Fechas y Horas
        DATA_TYPES.put("fecha", "DATE");
        DATA_TYPES.put("date", "DATE");
        DATA_TYPES.put("hora", "TIME");
        DATA_TYPES.put("time", "TIME");
        DATA_TYPES.put("fechahora", "TIMESTAMP");
        DATA_TYPES.put("timestamp", "TIMESTAMP");

        // Números Enteros
        DATA_TYPES.put("entero", "INTEGER");
        DATA_TYPES.put("int", "INTEGER");
        DATA_TYPES.put("integer", "INTEGER");
        DATA_TYPES.put("enterocorto", "SMALLINT");
        DATA_TYPES.put("smallint", "SMALLINT");
        DATA_TYPES.put("enterogrande", "BIGINT");
        DATA_TYPES.put("bigint", "BIGINT");

        // Números Decimales / Flotantes
        DATA_TYPES.put("decimal", "DECIMAL(10,2)");
        DATA_TYPES.put("numeric", "NUMERIC(10,2)");
        DATA_TYPES.put("numero", "NUMERIC(10,2)");
        DATA_TYPES.put("flotante", "FLOAT");
        DATA_TYPES.put("float", "FLOAT");
        DATA_TYPES.put("doble", "DOUBLE PRECISION");
        DATA_TYPES.put("double", "DOUBLE PRECISION");

        // Cadenas / Texto
        DATA_TYPES.put("texto", "VARCHAR(255)");
        DATA_TYPES.put("string", "VARCHAR(255)");
        DATA_TYPES.put("varchar", "VARCHAR(255)");
        DATA_TYPES.put("caracter", "CHAR(1)");
        DATA_TYPES.put("char", "CHAR(1)");
        DATA_TYPES.put("textolargo", "TEXT");
        DATA_TYPES.put("text", "TEXT");

        // Booleanos / Lógicos
        DATA_TYPES.put("booleano", "BOOLEAN");
        DATA_TYPES.put("bool", "BOOLEAN");
        DATA_TYPES.put("boolean", "BOOLEAN");

        // Binarios / BLOB
        DATA_TYPES.put("binario", "BLOB");
        DATA_TYPES.put("blob", "BLOB");
    }

    /**
     * Traduce una orden de creación de tabla en lenguaje natural a SQL estándar.
     * Ejemplo: "create tabla1 columna fecha formato fecha columna nombre formato texto"
     * Salida:  "CREATE TABLE tabla1 (\n    fecha DATE,\n    nombre VARCHAR(255)\n);"
     */
    public static String parseCommand(String input) {
        if (input == null || input.trim().isEmpty()) {
            return input;
        }

        String trimmed = input.trim();

        // Verificar si es una instrucción de tipo CREATE
        if (trimmed.toLowerCase().startsWith("create ")) {
            String[] tokens = trimmed.split("\\s+");

            if (tokens.length >= 2) {
                String tableName = tokens[1];
                StringBuilder columnsSql = new StringBuilder();

                // Patrón para capturar pares de "columna <nombre> formato <tipo>"
                Pattern pattern = Pattern.compile("columna\\s+(\\w+)\\s+formato\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(trimmed);

                int columnCount = 0;
                while (matcher.find()) {
                    String colName = matcher.group(1);
                    String rawType = matcher.group(2).toLowerCase();

                    // Mapear el tipo ingresado a su equivalente SQL (o usar el mismo en mayúsculas si no está mapeado)
                    String sqlType = DATA_TYPES.getOrDefault(rawType, rawType.toUpperCase());

                    if (columnCount > 0) {
                        columnsSql.append(",\n");
                    }
                    columnsSql.append("    ").append(colName).append(" ").append(sqlType);
                    columnCount++;
                }

                if (columnCount > 0) {
                    return String.format("CREATE TABLE %s (\n%s\n);", tableName, columnsSql.toString());
                } else {
                    // Si no usó las palabras "columna/formato", hace la traducción directa en línea simple
                    // Ejemplo: "create tabla1 fecha date" -> "CREATE TABLE tabla1 (fecha DATE);"
                    if (tokens.length == 4) {
                        String colName = tokens[2];
                        String rawType = tokens[3].toLowerCase();
                        String sqlType = DATA_TYPES.getOrDefault(rawType, rawType.toUpperCase());
                        return String.format("CREATE TABLE %s (%s %s);", tableName, colName, sqlType);
                    }
                }
            }
        }

        return input;
    }
}