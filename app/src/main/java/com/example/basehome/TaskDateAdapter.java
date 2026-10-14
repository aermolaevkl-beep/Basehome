package com.example.basehome;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskDateAdapter extends RecyclerView.Adapter<TaskDateAdapter.DateViewHolder> {

    private final List<TaskDateGroup> dateGroups = new ArrayList<>();
    private final OnDateClickListener listener;

    public interface OnDateClickListener {
        void onDateClick(TaskDateGroup dateGroup);
    }

    public TaskDateAdapter(OnDateClickListener listener) {
        this.listener = listener;
    }

    public void updateDateGroups(List<TaskDateGroup> newGroups) {
        dateGroups.clear();
        if (newGroups != null) {
            dateGroups.addAll(newGroups);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task_date, parent, false);
        return new DateViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DateViewHolder holder, int position) {
        TaskDateGroup group = dateGroups.get(position);
        String groupDate = group.getDate();
        boolean isToday = isToday(groupDate);
        holder.tvDateTitle.setText(groupDate);
        holder.tvTodayBadge.setVisibility(isToday ? View.VISIBLE : View.GONE);
        holder.tvDateCount.setText("Задач: " + group.getTaskCount());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDateClick(group);
            }
        });
    }

    private boolean isToday(String groupDate) {
        if (groupDate == null) return false;
        try {
            Date date = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(groupDate);
            if (date == null) return false;
            return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(date)
                    .equals(new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date()));
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public int getItemCount() {
        return dateGroups.size();
    }

    static class DateViewHolder extends RecyclerView.ViewHolder {
        TextView tvDateTitle, tvDateCount, tvTodayBadge;

        public DateViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDateTitle = itemView.findViewById(R.id.tvTaskDateTitle);
            tvDateCount = itemView.findViewById(R.id.tvTaskDateCount);
            tvTodayBadge = itemView.findViewById(R.id.tvTaskTodayBadge);
        }
    }
}
