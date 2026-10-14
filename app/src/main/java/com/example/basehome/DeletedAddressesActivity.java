package com.example.basehome;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class DeletedAddressesActivity extends AppCompatActivity {
    private DatabaseReference deletedReference;
    private DeletedAdapter adapter;
    private TextView emptyView;
    private ValueEventListener recordsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deleted_addresses);
        SwipeBackHelper.attach(this);
        findViewById(R.id.btnBackDeletedAddresses).setOnClickListener(v -> finish());
        emptyView = findViewById(R.id.tvDeletedAddressesEmpty);
        RecyclerView recycler = findViewById(R.id.rvDeletedAddresses);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeletedAdapter(this::restoreRecord, this::confirmPermanentDelete);
        recycler.setAdapter(adapter);
        deletedReference = FirebaseDatabase.getInstance().getReference("deleted_addresses");
        verifyAdminAndLoad();
    }

    private void verifyAdminAndLoad() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            denyAccess();
            return;
        }
        FirebaseDatabase.getInstance().getReference("users").child(uid).child("isAdmin").get()
                .addOnSuccessListener(snapshot -> {
                    if (!Boolean.TRUE.equals(snapshot.getValue(Boolean.class))) {
                        denyAccess();
                        return;
                    }
                    recordsListener = new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                            List<DeletedRecord> records = new ArrayList<>();
                            for (DataSnapshot child : snapshot.getChildren()) {
                                DeletedRecord record = DeletedRecord.from(child);
                                if (record != null) records.add(record);
                            }
                            records.sort((a, b) -> Long.compare(b.deletedAt, a.deletedAt));
                            adapter.submit(records);
                            emptyView.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(DeletedAddressesActivity.this, R.string.deleted_addresses_load_error, Toast.LENGTH_SHORT).show();
                        }
                    };
                    deletedReference.addValueEventListener(recordsListener);
                })
                .addOnFailureListener(error -> denyAccess());
    }

    private void denyAccess() {
        Toast.makeText(this, R.string.deleted_addresses_admin_only, Toast.LENGTH_LONG).show();
        finish();
    }

    private void restoreRecord(DeletedRecord record) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            denyAccess();
            return;
        }
        FirebaseDatabase.getInstance().getReference("users").child(uid).child("isAdmin").get()
                .addOnSuccessListener(admin -> {
                    if (!Boolean.TRUE.equals(admin.getValue(Boolean.class))) {
                        denyAccess();
                        return;
                    }
                    AddressDeletionService.getInstance().restore(record.key, uid, (success, error) -> {
                        if (success) {
                            Toast.makeText(this, R.string.deleted_address_restored, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, getString(R.string.deleted_address_restore_failed,
                                    error == null ? "" : error.getLocalizedMessage()), Toast.LENGTH_LONG).show();
                        }
                    });
                });
    }

    private void confirmPermanentDelete(DeletedRecord record) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.deleted_address_permanent_title)
                .setMessage(R.string.deleted_address_permanent_confirm)
                .setPositiveButton(R.string.deleted_address_permanent_action, (dialog, which) -> {
                    String uid = FirebaseAuth.getInstance().getUid();
                    if (uid == null) {
                        denyAccess();
                        return;
                    }
                    AddressDeletionService.getInstance().permanentlyDelete(record.key, uid, (success, error) ->
                            Toast.makeText(this, success ? R.string.deleted_address_permanently_deleted : R.string.deleted_address_action_failed,
                                    Toast.LENGTH_LONG).show());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override protected void onDestroy() {
        if (deletedReference != null && recordsListener != null) deletedReference.removeEventListener(recordsListener);
        super.onDestroy();
    }

    private static final class DeletedRecord {
        String key, sector, originalKey, deletedBy, title;
        long deletedAt;

        static DeletedRecord from(DataSnapshot snapshot) {
            String sector = snapshot.child("sector").getValue(String.class);
            String originalKey = snapshot.child("originalKey").getValue(String.class);
            if (sector == null || originalKey == null) return null;
            DeletedRecord record = new DeletedRecord();
            record.key = snapshot.getKey();
            record.sector = sector;
            record.originalKey = originalKey;
            record.deletedBy = snapshot.child("deletedBy").getValue(String.class);
            Long timestamp = snapshot.child("deletedAt").getValue(Long.class);
            record.deletedAt = timestamp == null ? 0L : timestamp;
            DataSnapshot address = snapshot.child("address");
            String street = address.child("street").getValue(String.class);
            String house = address.child("house").getValue(String.class);
            String center = address.child("centerName").getValue(String.class);
            record.title = (AddressDeletionService.BUSINESS.equals(sector) ?
                    (center == null || center.trim().isEmpty() ? "" : center + " · ") : "") +
                    (street == null ? "" : street) + (house == null || house.isEmpty() ? "" : " " + house);
            if (record.title.trim().isEmpty()) record.title = originalKey;
            return record;
        }
    }

    private static final class DeletedAdapter extends RecyclerView.Adapter<DeletedAdapter.Holder> {
        interface Action { void run(DeletedRecord record); }
        private final List<DeletedRecord> records = new ArrayList<>();
        private final Action restoreAction, deleteAction;
        DeletedAdapter(Action restoreAction, Action deleteAction) {
            this.restoreAction = restoreAction;
            this.deleteAction = deleteAction;
        }
        void submit(List<DeletedRecord> items) {
            records.clear();
            records.addAll(items);
            notifyDataSetChanged();
        }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_deleted_address, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            DeletedRecord record = records.get(position);
            holder.title.setText(record.title);
            holder.sector.setText(AddressDeletionService.PHYSICAL.equals(record.sector) ? R.string.deleted_sector_physical : R.string.deleted_sector_business);
            holder.meta.setText(holder.itemView.getContext().getString(R.string.deleted_address_meta,
                    DateFormat.format("yyyy-MM-dd HH:mm", record.deletedAt), record.deletedBy == null ? "—" : record.deletedBy));
            holder.restore.setOnClickListener(v -> restoreAction.run(record));
            holder.delete.setOnClickListener(v -> deleteAction.run(record));
        }
        @Override public int getItemCount() { return records.size(); }
        static final class Holder extends RecyclerView.ViewHolder {
            final TextView title, sector, meta;
            final Button restore, delete;
            Holder(View view) {
                super(view);
                title = view.findViewById(R.id.tvDeletedAddressTitle);
                sector = view.findViewById(R.id.tvDeletedAddressSector);
                meta = view.findViewById(R.id.tvDeletedAddressMeta);
                restore = view.findViewById(R.id.btnRestoreDeletedAddress);
                delete = view.findViewById(R.id.btnPermanentDeleteAddress);
            }
        }
    }
}
