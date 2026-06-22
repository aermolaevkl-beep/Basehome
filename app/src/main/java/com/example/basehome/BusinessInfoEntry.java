package com.example.basehome;

public class BusinessInfoEntry {
    private String id;
    private String text;
    private long timestamp;
    private String authorId;

    public BusinessInfoEntry() {}

    public BusinessInfoEntry(String id, String text, long timestamp, String authorId) {
        this.id = id;
        this.text = text;
        this.timestamp = timestamp;
        this.authorId = authorId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
}
