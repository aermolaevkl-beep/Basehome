package com.example.basehome;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

public class GoogleAddress {
    @SerializedName("street")
    private String street;

    public GoogleAddress() {}

    public GoogleAddress(String street) {
        this.street = street;
    }

    public String getStreet() {
        return street != null ? street : "";
    }

    public void setStreet(String street) {
        this.street = street;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GoogleAddress that = (GoogleAddress) o;
        return Objects.equals(street, that.street);
    }

    @Override
    public int hashCode() {
        return Objects.hash(street);
    }
}
