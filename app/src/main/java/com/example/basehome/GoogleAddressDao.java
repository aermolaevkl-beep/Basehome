package com.example.basehome;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface GoogleAddressDao {
    @Query("SELECT * FROM google_addresses WHERE city = :city")
    List<GoogleAddressEntity> getEntitiesForCity(String city);

    @Query("SELECT * FROM google_addresses")
    List<GoogleAddressEntity> getAllEntities();

    @Query("SELECT fullText FROM google_addresses")
    List<String> getAllAddresses();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<GoogleAddressEntity> addresses);

    @Query("DELETE FROM google_addresses WHERE city = :city")
    void deleteForCity(String city);

    @Query("DELETE FROM google_addresses")
    void deleteAll();
}
