package com.example.basehome;

public class Entrance {
    private String id;
    private String number;
    private String intercomCode;
    private String crossFloor;
    private String basementKey;
    private String lastEditorId;
    private String lastEditorName;
    private long lastEditTimestamp;

    public Entrance() {}

    public Entrance(String id, String number, String intercomCode, String crossFloor, String basementKey, String lastEditorId, String lastEditorName, long lastEditTimestamp) {
        this.id = id;
        this.number = number;
        this.intercomCode = intercomCode;
        this.crossFloor = crossFloor;
        this.basementKey = basementKey;
        this.lastEditorId = lastEditorId;
        this.lastEditorName = lastEditorName;
        this.lastEditTimestamp = lastEditTimestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getIntercomCode() { return intercomCode; }
    public void setIntercomCode(String intercomCode) { this.intercomCode = intercomCode; }
    public String getCrossFloor() { return crossFloor; }
    public void setCrossFloor(String crossFloor) { this.crossFloor = crossFloor; }
    public String getBasementKey() { return basementKey; }
    public void setBasementKey(String basementKey) { this.basementKey = basementKey; }
    public String getLastEditorId() { return lastEditorId; }
    public void setLastEditorId(String lastEditorId) { this.lastEditorId = lastEditorId; }
    public String getLastEditorName() { return lastEditorName; }
    public void setLastEditorName(String lastEditorName) { this.lastEditorName = lastEditorName; }
    public long getLastEditTimestamp() { return lastEditTimestamp; }
    public void setLastEditTimestamp(long lastEditTimestamp) { this.lastEditTimestamp = lastEditTimestamp; }
}