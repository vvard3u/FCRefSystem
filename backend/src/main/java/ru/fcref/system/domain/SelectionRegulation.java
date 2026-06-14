package ru.fcref.system.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SelectionRegulation {

    private final String id;
    private final String name;
    private final String description;
    private final Instant createdAt;
    private final String createdByUserId;
    private final List<SelectionStage> stages;
    private boolean active;

    public SelectionRegulation(
            String id,
            String name,
            String description,
            Instant createdAt,
            String createdByUserId,
            List<SelectionStage> stages,
            boolean active
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.createdByUserId = createdByUserId;
        this.stages = new ArrayList<>(stages);
        this.active = active;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedByUserId() {
        return createdByUserId;
    }

    public List<SelectionStage> getStages() {
        return stages;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
