package com.example.mirad.model;

public class FileInfo {
    private final String name;
    private final long size;
    private final long date;
    private final String dateStr;

    public FileInfo(String name, long size, long date, String dateStr) {
        this.name = name;
        this.size = size;
        this.date = date;
        this.dateStr = dateStr;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public long getDate() {
        return date;
    }

    public String getDateStr() {
        return dateStr;
    }
}
