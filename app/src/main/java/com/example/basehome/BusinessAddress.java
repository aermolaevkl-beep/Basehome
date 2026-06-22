package com.example.basehome;

public class BusinessAddress {
    private String id;
    private String street;
    private String house;
    private String city;
    private String centerName; // Название ТЦ, БЦ
    private String userId;
    private String userName;
    private String socketLocation; // Где установлена розетка
    private String reserveInfo; // Запас

    public BusinessAddress() {}

    public BusinessAddress(String street, String house, String centerName, String userId, String userName) {
        this.street = street;
        this.house = house;
        this.centerName = centerName;
        this.userId = userId;
        this.userName = userName;
        this.city = "Могилев"; // По умолчанию
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
}
