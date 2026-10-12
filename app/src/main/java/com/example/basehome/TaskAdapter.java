package com.example.basehome;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private final List<TaskItem> tasks = new ArrayList<>();

    public void updateTasks(List<TaskItem> newTasks) {
        tasks.clear();
        if (newTasks != null) {
            tasks.addAll(newTasks);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        TaskItem item = tasks.get(position);
        holder.tvType.setText(item.getType());
        holder.tvDateTime.setText(item.getDateTime());
        holder.tvAddress.setText(item.getAddress());
        holder.tvClientName.setText(item.getClientName());
        holder.tvAccount.setText("Л/с: " + item.getAccountNumber());
        holder.tvTariffTech.setText(item.getTariff() + " | " + item.getTechnology());

        String phone = item.getClientPhone();
        if (phone != null && !phone.trim().isEmpty()) {
            holder.btnCall.setVisibility(View.VISIBLE);
            holder.btnCall.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.trim()));
                v.getContext().startActivity(intent);
            });
        } else {
            holder.btnCall.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView tvType, tvDateTime, tvAddress, tvClientName, tvAccount, tvTariffTech;
        MaterialButton btnCall;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            tvType = itemView.findViewById(R.id.tvTaskType);
            tvDateTime = itemView.findViewById(R.id.tvTaskDateTime);
            tvAddress = itemView.findViewById(R.id.tvTaskAddress);
            tvClientName = itemView.findViewById(R.id.tvTaskClientName);
            tvAccount = itemView.findViewById(R.id.tvTaskAccount);
            tvTariffTech = itemView.findViewById(R.id.tvTaskTariffTech);
            btnCall = itemView.findViewById(R.id.btnTaskCall);
        }
    }
}
