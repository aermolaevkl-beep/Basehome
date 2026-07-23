package com.example.basehome;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Spinner;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kotlin.Unit;

public class BusinessAddressDetailActivity extends AppCompatActivity {

    private TextView tvStreet, tvHouse, tvCenter, tvCity, tvReplyTo, tvBusAddedBy;
    private EditText etNewComment;
    private DatabaseReference databaseReference, commentsReference, usersReference;
    
    private RecyclerView rvSockets, rvReserves, rvComments;
    private BusinessInfoAdapter socketAdapter, reserveAdapter;
    private CommentAdapter commentAdapter;
    
    private final List<BusinessInfoEntry> socketEntries = new ArrayList<>();
    private final List<BusinessInfoEntry> reserveEntries = new ArrayList<>();
    private final List<Comment> commentsList = new ArrayList<>();
    
    private BusinessAddress currentBusinessAddress;
    private ImageButton btnDelete, btnEdit, btnAddComment;
    private boolean isAdmin = false;
    private String addressId;
    private String replyingToCommentId = null;
    private String currentUserName = "Аноним";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_business_address_detail);

        addressId = getIntent().getStringExtra("addressId");
        if (addressId == null) {
            finish();
            return;
        }

        databaseReference = FirebaseDatabase.getInstance().getReference("business_addresses").child(addressId);
        commentsReference = FirebaseDatabase.getInstance().getReference("comments").child(addressId);
        usersReference = FirebaseDatabase.getInstance().getReference("users");

        tvCity = findViewById(R.id.tvBusCity);
        tvStreet = findViewById(R.id.tvBusStreet);
        tvHouse = findViewById(R.id.tvBusHouse);
        tvCenter = findViewById(R.id.tvBusCenterName);
        tvBusAddedBy = findViewById(R.id.tvBusAddedBy);
        tvReplyTo = findViewById(R.id.tvReplyTo);
        etNewComment = findViewById(R.id.etNewComment);
        
        btnDelete = findViewById(R.id.btnDeleteBusinessAddress);
        btnEdit = findViewById(R.id.btnEditBusinessAddress);
        btnAddComment = findViewById(R.id.btnAddComment);
        
        rvSockets = findViewById(R.id.rvSockets);
        rvReserves = findViewById(R.id.rvReserves);
        rvComments = findViewById(R.id.rvComments);

        findViewById(R.id.btnBackBusinessDetail).setOnClickListener(v -> finish());
        tvReplyTo.setOnClickListener(v -> cancelReply());

        btnDelete.setVisibility(View.GONE);
        btnEdit.setVisibility(View.GONE);

        setupRecyclerViews();
        checkAdminStatus();
        loadData();
        loadComments();

        findViewById(R.id.btnSocket).setOnClickListener(v -> showAddDialog("Добавить розетку", "sockets"));
        findViewById(R.id.btnReserve).setOnClickListener(v -> showAddDialog("Добавить запас", "reserves"));
        btnEdit.setOnClickListener(v -> showEditBusinessAddressDialog());
        btnDelete.setOnClickListener(v -> confirmDeleteAddress());
        btnAddComment.setOnClickListener(v -> addComment());
    }

    private void checkAdminStatus() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        usersReference.child(uid).get().addOnSuccessListener(snapshot -> {
            isAdmin = Boolean.TRUE.equals(snapshot.child("isAdmin").getValue(Boolean.class));
            currentUserName = snapshot.child("name").getValue(String.class);
            if (currentUserName == null) {
                currentUserName = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            }
            
            if (socketAdapter != null) socketAdapter.setAdmin(isAdmin);
            if (reserveAdapter != null) reserveAdapter.setAdmin(isAdmin);
            
            setupCommentAdapter();
            updateActionButtonsVisibility();
        });
    }

    private void setupCommentAdapter() {
        commentAdapter = new CommentAdapter(commentsList, isAdmin,
            (comment, isPlus) -> { handleReaction(comment, isPlus); return Unit.INSTANCE; },
            comment -> { startReply(comment); return Unit.INSTANCE; },
            comment -> { confirmDeleteComment(comment); return Unit.INSTANCE; },
            comment -> { showEditCommentDialog(comment); return Unit.INSTANCE; }
        );
        rvComments.setAdapter(commentAdapter);
    }

    private void updateActionButtonsVisibility() {
        String currentUid = FirebaseAuth.getInstance().getUid();
        boolean isAuthor = currentBusinessAddress != null && currentUid != null && currentUid.equals(currentBusinessAddress.getUserId());
        
        int visibility = (isAdmin || isAuthor) ? View.VISIBLE : View.GONE;
        btnDelete.setVisibility(visibility);
        btnEdit.setVisibility(visibility);
    }

    private void setupRecyclerViews() {
        rvSockets.setLayoutManager(new LinearLayoutManager(this));
        socketAdapter = new BusinessInfoAdapter(socketEntries, isAdmin, 
                entry -> deleteEntry("sockets", entry.getId()),
                entry -> showEditEntryDialog("Редактировать розетку", "sockets", entry));
        rvSockets.setAdapter(socketAdapter);

        rvReserves.setLayoutManager(new LinearLayoutManager(this));
        reserveAdapter = new BusinessInfoAdapter(reserveEntries, isAdmin, 
                entry -> deleteEntry("reserves", entry.getId()),
                entry -> showEditEntryDialog("Редактировать запас", "reserves", entry));
        rvReserves.setAdapter(reserveAdapter);

        rvComments.setLayoutManager(new LinearLayoutManager(this));
        setupCommentAdapter();
    }

    private void loadData() {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentBusinessAddress = snapshot.getValue(BusinessAddress.class);
                if (currentBusinessAddress != null) {
                    tvCity.setText(currentBusinessAddress.getCity() != null ? currentBusinessAddress.getCity() : "Могилев");
                    tvStreet.setText(currentBusinessAddress.getStreet());
                    tvHouse.setText(getString(R.string.house_label, currentBusinessAddress.getHouse()));
                    tvCenter.setText(currentBusinessAddress.getCenterName());
                    
                    CharSequence nameWithRank = RankHelper.INSTANCE.formatNameWithRank(
                            currentBusinessAddress.getUserId(), 
                            currentBusinessAddress.getUserName());
                    tvBusAddedBy.setText(TextUtils.concat("Создал: ", nameWithRank));
                    
                    updateActionButtonsVisibility();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        databaseReference.child("sockets").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                socketEntries.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    BusinessInfoEntry entry = data.getValue(BusinessInfoEntry.class);
                    if (entry != null) {
                        entry.setId(data.getKey());
                        socketEntries.add(entry);
                    }
                }
                socketAdapter.notifyDataSetChanged();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        databaseReference.child("reserves").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                reserveEntries.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    BusinessInfoEntry entry = data.getValue(BusinessInfoEntry.class);
                    if (entry != null) {
                        entry.setId(data.getKey());
                        reserveEntries.add(entry);
                    }
                }
                reserveAdapter.notifyDataSetChanged();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadComments() {
        commentsReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Comment> allComments = new ArrayList<>();
                for (DataSnapshot data : snapshot.getChildren()) {
                    Comment comment = data.getValue(Comment.class);
                    if (comment != null) {
                        comment.setId(data.getKey());
                        allComments.add(comment);
                    }
                }
                rebuildCommentsTree(allComments);
                commentAdapter.notifyDataSetChanged();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void rebuildCommentsTree(List<Comment> all) {
        commentsList.clear();
        List<Comment> roots = new ArrayList<>();
        for (Comment c : all) {
            if (c.getParentId() == null || c.getParentId().isEmpty()) {
                roots.add(c);
            }
        }
        roots.sort((c1, c2) -> Long.compare(c1.getTimestamp(), c2.getTimestamp()));

        Set<String> addedIds = new HashSet<>();
        for (Comment root : roots) {
            addRecursive(root, all, addedIds);
        }
        
        for (Comment c : all) {
            if (!addedIds.contains(c.getId())) {
                commentsList.add(c);
            }
        }
    }

    private void addRecursive(Comment parent, List<Comment> all, Set<String> addedIds) {
        if (addedIds.contains(parent.getId())) return;
        commentsList.add(parent);
        addedIds.add(parent.getId());

        List<Comment> children = new ArrayList<>();
        for (Comment c : all) {
            if (parent.getId().equals(c.getParentId())) {
                children.add(c);
            }
        }
        children.sort((c1, c2) -> Long.compare(c1.getTimestamp(), c2.getTimestamp()));
        for (Comment child : children) {
            addRecursive(child, all, addedIds);
        }
    }

    private void addComment() {
        String text = etNewComment.getText().toString().trim();
        if (text.isEmpty()) return;
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        String commentId = commentsReference.push().getKey();
        if (commentId == null) return;

        Comment comment = new Comment(commentId, text, uid, 
            FirebaseAuth.getInstance().getCurrentUser().getEmail(), 
            System.currentTimeMillis(), replyingToCommentId);
        
        commentsReference.child(commentId).setValue(comment).addOnSuccessListener(aVoid -> {
            etNewComment.setText("");
            cancelReply();
        });
    }

    private void startReply(Comment comment) {
        replyingToCommentId = comment.getId();
        tvReplyTo.setText("Ответ пользователю (отменить)");
        tvReplyTo.setVisibility(View.VISIBLE);
        etNewComment.requestFocus();
    }

    private void cancelReply() {
        replyingToCommentId = null;
        tvReplyTo.setVisibility(View.GONE);
    }

    private void handleReaction(Comment comment, boolean isPlus) {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        Map<String, Integer> votedUsers = comment.getVotedUsers();
        if (votedUsers == null) votedUsers = new HashMap<>();

        int currentVote = votedUsers.getOrDefault(userId, 0);
        int newVote = isPlus ? 1 : -1;

        if (currentVote == newVote) {
            votedUsers.remove(userId);
            if (isPlus) comment.setPlusCount(comment.getPlusCount() - 1);
            else comment.setMinusCount(comment.getMinusCount() - 1);
        } else {
            if (currentVote != 0) {
                if (currentVote == 1) comment.setPlusCount(comment.getPlusCount() - 1);
                else comment.setMinusCount(comment.getMinusCount() - 1);
            }
            votedUsers.put(userId, newVote);
            if (isPlus) comment.setPlusCount(comment.getPlusCount() + 1);
            else comment.setMinusCount(comment.getMinusCount() + 1);
        }
        comment.setVotedUsers(votedUsers);
        commentsReference.child(comment.getId()).setValue(comment);
    }

    private void confirmDeleteComment(Comment comment) {
        new AlertDialog.Builder(this)
            .setTitle("Удаление")
            .setMessage("Удалить комментарий?")
            .setPositiveButton("Да", (dialog, which) -> commentsReference.child(comment.getId()).removeValue())
            .setNegativeButton("Нет", null)
            .show();
    }

    private void showEditCommentDialog(Comment comment) {
        EditText editText = new EditText(this);
        editText.setText(comment.getText());
        
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(padding, padding / 2, padding, 0);
        editText.setLayoutParams(lp);
        container.addView(editText);

        new AlertDialog.Builder(this)
            .setTitle("Редактировать комментарий")
            .setView(container)
            .setPositiveButton("Сохранить", (dialog, which) -> {
                String newText = editText.getText().toString().trim();
                if (!newText.isEmpty()) {
                    commentsReference.child(comment.getId()).child("text").setValue(newText);
                }
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void showEditBusinessAddressDialog() {
        if (currentBusinessAddress == null) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.activity_add_business_address, null);
        EditText etStreet = dialogView.findViewById(R.id.etBusinessStreet);
        EditText etHouse = dialogView.findViewById(R.id.etBusinessHouse);
        EditText etCenter = dialogView.findViewById(R.id.etCenterName);
        Spinner spinnerCity = dialogView.findViewById(R.id.spinnerBusinessCity);
        Button btnSave = dialogView.findViewById(R.id.btnSaveBusiness);

        String[] cities = {"Могилев", "Бобруйск"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cities);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCity.setAdapter(adapter);

        etStreet.setText(currentBusinessAddress.getStreet());
        etHouse.setText(currentBusinessAddress.getHouse());
        etCenter.setText(currentBusinessAddress.getCenterName());
        int cityIndex = "Бобруйск".equals(currentBusinessAddress.getCity()) ? 1 : 0;
        spinnerCity.setSelection(cityIndex);
        
        btnSave.setVisibility(View.GONE);

        new AlertDialog.Builder(this)
                .setTitle("Редактировать Юр. адрес")
                .setView(dialogView)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String streetInput = etStreet.getText().toString().trim();
                    String house = etHouse.getText().toString().trim();
                    String centerInput = etCenter.getText().toString().trim();
                    String city = spinnerCity.getSelectedItem().toString();

                    if (!streetInput.isEmpty() && !house.isEmpty() && !centerInput.isEmpty()) {
                        String street = capitalize(streetInput);
                        String center = capitalize(centerInput);

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("street", street);
                        updates.put("house", house);
                        updates.put("centerName", center);
                        updates.put("city", city);

                        databaseReference.updateChildren(updates)
                                .addOnSuccessListener(aVoid -> Toast.makeText(BusinessAddressDetailActivity.this, "Обновлено", Toast.LENGTH_SHORT).show());
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void confirmDeleteAddress() {
        new AlertDialog.Builder(this)
                .setTitle("Удаление адреса")
                .setMessage("Вы уверены, что хотите полностью удалить этот Юр. адрес?")
                .setPositiveButton("Удалить", (dialog, which) -> 
                    databaseReference.removeValue()
                            .addOnSuccessListener(aVoid -> {
                                FirebaseDatabase.getInstance().getReference("comments").child(addressId).removeValue();
                                Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                )
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showAddDialog(String title, String nodeKey) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_simple_input, null);
        EditText etInput = view.findViewById(R.id.etSimpleInput);
        builder.setView(view);

        builder.setPositiveButton("Добавить", (dialog, which) -> {
            String text = etInput.getText().toString().trim();
            String uid = FirebaseAuth.getInstance().getUid();
            if (!text.isEmpty() && uid != null) {
                String id = databaseReference.child(nodeKey).push().getKey();
                BusinessInfoEntry entry = new BusinessInfoEntry(id, text, System.currentTimeMillis(), uid);
                if (id != null) {
                    databaseReference.child(nodeKey).child(id).setValue(entry);
                }
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showEditEntryDialog(String title, String nodeKey, BusinessInfoEntry entry) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_simple_input, null);
        EditText etInput = view.findViewById(R.id.etSimpleInput);
        etInput.setText(entry.getText());
        builder.setView(view);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String text = etInput.getText().toString().trim();
            if (!text.isEmpty()) {
                databaseReference.child(nodeKey).child(entry.getId()).child("text").setValue(text);
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void deleteEntry(String nodeKey, String entryId) {
        new AlertDialog.Builder(this)
            .setTitle("Удаление")
            .setMessage("Удалить эту запись?")
            .setPositiveButton("Да", (dialog, which) -> 
                databaseReference.child(nodeKey).child(entryId).removeValue()
                    .addOnSuccessListener(aVoid -> Toast.makeText(BusinessAddressDetailActivity.this, "Удалено", Toast.LENGTH_SHORT).show())
            )
            .setNegativeButton("Нет", null)
            .show();
    }
}
