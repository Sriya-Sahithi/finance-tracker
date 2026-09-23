package com.financetracker.statementimport;

import com.financetracker.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "statement_import_sessions")
public class StatementImportSession {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "detected_account_name", length = 255)
    private String detectedAccountName;

    @Column(name = "detected_account_number", length = 34)
    private String detectedAccountNumber;

    @Column(name = "preview_rows_json", nullable = false, columnDefinition = "TEXT")
    private String previewRowsJson;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getDetectedAccountName() {
        return detectedAccountName;
    }

    public void setDetectedAccountName(String detectedAccountName) {
        this.detectedAccountName = detectedAccountName;
    }

    public String getDetectedAccountNumber() {
        return detectedAccountNumber;
    }

    public void setDetectedAccountNumber(String detectedAccountNumber) {
        this.detectedAccountNumber = detectedAccountNumber;
    }

    public String getPreviewRowsJson() {
        return previewRowsJson;
    }

    public void setPreviewRowsJson(String previewRowsJson) {
        this.previewRowsJson = previewRowsJson;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
