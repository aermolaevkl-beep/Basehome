package com.example.basehome;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "google_addresses")
public class GoogleAddressEntity {
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private String city;
    private String fullText;
    private String streetHouse;
    private String streetHouseKey;
    private String apartment;

    public GoogleAddressEntity() {}

    @Ignore
    public GoogleAddressEntity(String city, String fullText, String streetHouse, String streetHouseKey, String apartment) {
        this.city = city;
        this.fullText = fullText;
        this.streetHouse = streetHouse;
        this.streetHouseKey = streetHouseKey;
        this.apartment = apartment;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCity() { return city != null ? city : "Могилев"; }
    public void setCity(String city) { this.city = city; }
    
    public String getFullText() { return fullText != null ? fullText : ""; }
    public void setFullText(String fullText) { this.fullText = fullText; }

    public String getStreetHouse() { return streetHouse != null ? streetHouse : ""; }
    public void setStreetHouse(String streetHouse) { this.streetHouse = streetHouse; }

    public String getStreetHouseKey() { return streetHouseKey != null ? streetHouseKey : ""; }
    public void setStreetHouseKey(String streetHouseKey) { this.streetHouseKey = streetHouseKey; }

    public String getApartment() { return apartment != null ? apartment : ""; }
    public void setApartment(String apartment) { this.apartment = apartment; }
}
