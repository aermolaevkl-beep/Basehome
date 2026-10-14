package com.example.basehome;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;

import java.util.HashMap;
import java.util.Map;

/** Moves complete address trees to a journal using atomic Realtime Database writes. */
public final class AddressDeletionService {
    public static final String PHYSICAL = "addresses";
    public static final String BUSINESS = "business_addresses";
    private static final String DELETED = "deleted_addresses";
    private final DatabaseReference root = FirebaseDatabase.getInstance().getReference();

    public interface Completion {
        void onComplete(boolean success, Exception error);
    }

    private AddressDeletionService() {}

    public static AddressDeletionService getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final AddressDeletionService INSTANCE = new AddressDeletionService();
    }

    public void softDelete(String sector, String originalKey, String uid, Completion completion) {
        if ((!PHYSICAL.equals(sector) && !BUSINESS.equals(sector)) || originalKey == null || uid == null) {
            completion.onComplete(false, new IllegalArgumentException("Invalid address deletion request"));
            return;
        }

        DatabaseReference source = root.child(sector).child(originalKey);
        DatabaseReference commentsReference = root.child("comments").child(originalKey);
        DatabaseReference entrancesReference = root.child("entrances").child(originalKey);
        source.runTransaction(new Transaction.Handler() {
            @Override
            @NonNull
            public Transaction.Result doTransaction(@NonNull MutableData addressData) {
                if (addressData.getValue() == null) return Transaction.abort();
                return Transaction.success(addressData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot addressSnapshot) {
                if (error != null) {
                    completion.onComplete(false, error.toException());
                    return;
                }
                if (!committed || addressSnapshot == null || !addressSnapshot.exists()) {
                    completion.onComplete(false, new IllegalStateException("Address no longer exists"));
                    return;
                }
                Task<DataSnapshot> commentsTask = commentsReference.get();
                Task<DataSnapshot> entrancesTask = PHYSICAL.equals(sector)
                        ? entrancesReference.get() : Tasks.forResult(null);
                Tasks.whenAllSuccess(commentsTask, entrancesTask)
                        .addOnSuccessListener(values -> {
                    DataSnapshot commentsSnapshot = (DataSnapshot) values.get(0);
                    DataSnapshot entrancesSnapshot = (DataSnapshot) values.get(1);
                    if (!addressSnapshot.exists()) {
                        completion.onComplete(false, new IllegalStateException("Address no longer exists"));
                        return;
                    }

                    String journalKey = root.child(DELETED).push().getKey();
                    if (journalKey == null) {
                        completion.onComplete(false, new IllegalStateException("Could not create journal key"));
                        return;
                    }
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("sector", sector);
                    entry.put("originalKey", originalKey);
                    entry.put("deletedAt", ServerValue.TIMESTAMP);
                    entry.put("deletedBy", uid);
                    entry.put("address", addressSnapshot.getValue());
                    if (commentsSnapshot.exists()) entry.put("comments", commentsSnapshot.getValue());
                    if (entrancesSnapshot != null && entrancesSnapshot.exists()) entry.put("entrances", entrancesSnapshot.getValue());
                    if (BUSINESS.equals(sector)) {
                        entry.put("sockets", addressSnapshot.child("sockets").getValue());
                        entry.put("reserves", addressSnapshot.child("reserves").getValue());
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put(DELETED + "/" + journalKey, entry);
                    updates.put(sector + "/" + originalKey, null);
                    updates.put("comments/" + originalKey, null);
                    if (PHYSICAL.equals(sector)) updates.put("entrances/" + originalKey, null);
                    root.updateChildren(updates)
                            .addOnSuccessListener(ignored -> completion.onComplete(true, null))
                            .addOnFailureListener(updateError -> completion.onComplete(false, updateError));
                        })
                        .addOnFailureListener(readError -> completion.onComplete(false, readError));
            }
        }, false);
    }

    /** Restores atomically, refusing to replace any existing address or associated children. */
    public void restore(String journalKey, String uid, Completion completion) {
        if (journalKey == null || uid == null) {
            completion.onComplete(false, new IllegalArgumentException("Invalid restore request"));
            return;
        }
        FirebaseDatabase.getInstance().getReference("users").child(uid).child("isAdmin").get()
                .addOnSuccessListener(adminSnapshot -> {
                    if (!Boolean.TRUE.equals(adminSnapshot.getValue(Boolean.class))) {
                        completion.onComplete(false, new SecurityException("Administrator access required"));
                        return;
                    }
                    restoreRecord(journalKey, completion);
                })
                .addOnFailureListener(error -> completion.onComplete(false, error));
    }

    private void restoreRecord(String journalKey, Completion completion) {
        root.runTransaction(new Transaction.Handler() {
            @Override
            @NonNull
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                MutableData journal = data.child(DELETED).child(journalKey);
                Object sectorValue = journal.child("sector").getValue();
                Object originalValue = journal.child("originalKey").getValue();
                Object addressValue = journal.child("address").getValue();
                if (!(sectorValue instanceof String) || !(originalValue instanceof String) || addressValue == null) {
                    return Transaction.abort();
                }
                String sector = (String) sectorValue;
                String originalKey = (String) originalValue;
                if (!PHYSICAL.equals(sector) && !BUSINESS.equals(sector)) return Transaction.abort();

                MutableData destination = data.child(sector).child(originalKey);
                MutableData comments = data.child("comments").child(originalKey);
                MutableData entrances = data.child("entrances").child(originalKey);
                if (destination.getValue() != null) return Transaction.abort();
                Object commentsValue = journal.child("comments").getValue();
                Object entrancesValue = journal.child("entrances").getValue();
                if (commentsValue != null && comments.getValue() != null) return Transaction.abort();
                if (PHYSICAL.equals(sector) && entrancesValue != null && entrances.getValue() != null) return Transaction.abort();

                destination.setValue(addressValue);
                if (commentsValue != null) comments.setValue(commentsValue);
                if (PHYSICAL.equals(sector) && entrancesValue != null) entrances.setValue(entrancesValue);
                journal.setValue(null);
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (error != null) completion.onComplete(false, error.toException());
                else if (committed) completion.onComplete(true, null);
                else completion.onComplete(false, new IllegalStateException("Restore blocked: destination exists or journal data is invalid"));
            }
        });
    }

    public void permanentlyDelete(String journalKey, String uid, Completion completion) {
        if (journalKey == null || uid == null) {
            completion.onComplete(false, new IllegalArgumentException("Invalid permanent-delete request"));
            return;
        }
        FirebaseDatabase.getInstance().getReference("users").child(uid).child("isAdmin").get()
                .addOnSuccessListener(adminSnapshot -> {
                    if (!Boolean.TRUE.equals(adminSnapshot.getValue(Boolean.class))) {
                        completion.onComplete(false, new SecurityException("Administrator access required"));
                        return;
                    }
                    permanentlyDeleteRecord(journalKey, completion);
                })
                .addOnFailureListener(error -> completion.onComplete(false, error));
    }

    private void permanentlyDeleteRecord(String journalKey, Completion completion) {
        DatabaseReference record = root.child(DELETED).child(journalKey);
        record.runTransaction(new Transaction.Handler() {
            @Override
            @NonNull
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                if (data.getValue() == null) return Transaction.abort();
                data.setValue(null);
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (error != null) completion.onComplete(false, error.toException());
                else completion.onComplete(committed, committed ? null : new IllegalStateException("Record no longer exists"));
            }
        }, false);
    }
}
