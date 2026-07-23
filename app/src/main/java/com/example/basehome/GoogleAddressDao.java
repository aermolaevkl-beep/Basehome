package com.example.basehome;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface GoogleAddressDao {
    @Query("SELECT fullText FROM google_addresses")
    List<String> getAllAddresses();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<GoogleAddressEntity> addresses);

    @Query("DELETE FROM google_addresses")
    void deleteAll();
}
