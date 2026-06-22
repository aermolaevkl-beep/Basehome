package com.example.basehome;

public class Address {
    private String id;
    private String street;
    private String house;
    private String city;
    private String userId;
    private String userName;

    public Address() {}

    public Address(String street, String house, String userId, String userName) {
        this.street = street;
        this.house = house;
        this.userId = userId;
        this.userName = userName;
        this.city = "Могилев"; // По умолчанию
    }

    public Address(String street, String house, String city, String userId, String userName) {
        this.street = street;
        this.house = house;
        this.city = city;
        this.userId = userId;
        this.userName = userName;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getHouse() { return house; }
    public void setHouse(String house) { this.house = house; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
}