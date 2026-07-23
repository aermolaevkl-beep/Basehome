package com.example.basehome;

import java.util.Objects;

public class BusinessAddress {
    private String id;
    private String street;
    private String house;
    private String city;
    private String centerName;
    private String userId;
    private String userName;
    private String socketLocation;
    private String reserveInfo;

    public BusinessAddress() {}

    public BusinessAddress(String street, String house, String centerName, String userId, String userName) {
        this.street = street;
        this.house = house;
        this.centerName = centerName;
        this.userId = userId;
        this.userName = userName;
        this.city = "Могилев";
    }

    public BusinessAddress(String street, String house, String city, String centerName, String userId, String userName) {
        this.street = street;
        this.house = house;
        this.city = city;
        this.centerName = centerName;
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
    public String getCenterName() { return centerName; }
    public void setCenterName(String centerName) { this.centerName = centerName; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getSocketLocation() { return socketLocation; }
    public void setSocketLocation(String socketLocation) { this.socketLocation = socketLocation; }
    public String getReserveInfo() { return reserveInfo; }
    public void setReserveInfo(String reserveInfo) { this.reserveInfo = reserveInfo; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BusinessAddress that = (BusinessAddress) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(street, that.street) &&
                Objects.equals(house, that.house) &&
                Objects.equals(city, that.city) &&
                Objects.equals(centerName, that.centerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, street, house, city, centerName);
    }
}