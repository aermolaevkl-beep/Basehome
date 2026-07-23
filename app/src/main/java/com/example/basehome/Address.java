package com.example.basehome;

import java.util.Objects;

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
        this.city = "Могилев";
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Address address = (Address) o;
        return Objects.equals(id, address.id) &&
                Objects.equals(street, address.street) &&
                Objects.equals(house, address.house) &&
                Objects.equals(city, address.city);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, street, house, city);
    }
}