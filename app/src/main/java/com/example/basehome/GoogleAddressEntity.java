package com.example.basehome;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "google_addresses")
public class GoogleAddressEntity {
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private String fullText;

    public GoogleAddressEntity(String fullText) {
        this.fullText = fullText;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getFullText() { return fullText; }
    public void setFullText(String fullText) { this.fullText = fullText; }
}
