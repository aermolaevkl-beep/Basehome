package com.example.basehome;

import java.util.List;

public class TaskDateGroup {
    private final String date;
    private final List<TaskItem> tasks;

    public TaskDateGroup(String date, List<TaskItem> tasks) {
        this.date = date != null ? date : "";
        this.tasks = tasks != null ? tasks : new java.util.ArrayList<>();
    }

    public String getDate() {
        return date;
    }

    public List<TaskItem> getTasks() {
        return tasks;
    }

    public int getTaskCount() {
        return tasks.size();
    }
}
