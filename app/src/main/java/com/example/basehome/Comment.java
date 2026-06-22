package com.example.basehome;

import java.util.HashMap;
import java.util.Map;

public class Comment {
    private String id;
    private String text;
    private String authorId; // UID пользователя
    private String authorEmail;
    private long timestamp;
    private int plusCount;
    private int minusCount;
    private String parentId;
    private Map<String, Integer> votedUsers = new HashMap<>();

    public Comment() {
    }

    public Comment(String id, String text, String authorId, String authorEmail, long timestamp, String parentId) {
        this.id = id;
        this.text = text;
        this.authorId = authorId;
        this.authorEmail = authorEmail;
        this.timestamp = timestamp;
        this.parentId = parentId;
        this.plusCount = 0;
        this.minusCount = 0;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getAuthorEmail() { return authorEmail; }
    public void setAuthorEmail(String authorEmail) { this.authorEmail = authorEmail; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getPlusCount() { return plusCount; }
    public void setPlusCount(int plusCount) { this.plusCount = plusCount; }

    public int getMinusCount() { return minusCount; }
    public void setMinusCount(int minusCount) { this.minusCount = minusCount; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public Map<String, Integer> getVotedUsers() { return votedUsers; }
    public void setVotedUsers(Map<String, Integer> votedUsers) { this.votedUsers = votedUsers; }
}