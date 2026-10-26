package com.editor.model;

public class CircularBuffer {
    private StyledChar[] buffer;
    private int capacity;
    private int head = 0;
    private int tail = 0;
    private int size = 0;
    private int pageSize;

    public CircularBuffer(int capacity, int pageSize) {
        this.capacity = capacity;
        this.pageSize = pageSize;
        this.buffer = new StyledChar[capacity];
    }

    public int getSize() { return size; }
    public int getPageSize() { return pageSize; }

    public StyledChar get(int logicalIndex) {
        if (logicalIndex < 0 || logicalIndex >= size) return null;
        int actualIndex = (head + logicalIndex) % capacity;
        return buffer[actualIndex];
    }

    public boolean insert(int logicalIndex, StyledChar item) {
        if (size >= capacity || logicalIndex < 0 || logicalIndex > size) return false;

        // Desplazar elementos hacia la derecha
        for (int i = size; i > logicalIndex; i--) {
            int dest = (head + i) % capacity;
            int src = (head + i - 1) % capacity;
            buffer[dest] = buffer[src];
        }

        int target = (head + logicalIndex) % capacity;
        buffer[target] = item;
        size++;
        tail = (head + size) % capacity; // Recalculo correcto del tail circular
        return true;
    }

    public boolean delete(int logicalIndex) {
        if (logicalIndex < 0 || logicalIndex >= size) return false;

        // Desplazar elementos hacia la izquierda
        for (int i = logicalIndex; i < size - 1; i++) {
            int dest = (head + i) % capacity;
            int src = (head + i + 1) % capacity;
            buffer[dest] = buffer[src];
        }

        int lastActual = (head + size - 1) % capacity;
        buffer[lastActual] = null;
        size--;
        tail = (head + size) % capacity; // Recalculo correcto del tail circular
        return true;
    }

    public void write(StyledChar item) {
        insert(size, item);
    }

    public StyledChar[] getPage(int pageNumber) {
        int totalPages = getTotalPages();
        if (pageNumber < 0 || pageNumber >= totalPages) return new StyledChar[0];

        int start = (head + (pageNumber * pageSize)) % capacity;
        int currentBatchSize = Math.min(pageSize, size - (pageNumber * pageSize));
        
        StyledChar[] pageData = new StyledChar[currentBatchSize];
        for (int i = 0; i < currentBatchSize; i++) {
            pageData[i] = buffer[(start + i) % capacity];
        }
        return pageData;
    }

    public int getTotalPages() {
        return Math.max(1, (int) Math.ceil((double) size / pageSize));
    }
}