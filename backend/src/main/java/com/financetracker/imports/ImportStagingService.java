package com.financetracker.imports;

import com.financetracker.common.exception.BadRequestException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Holds parsed statement previews in memory for a short time so the user can confirm the
 * column mapping before anything is committed to the database. Nothing here touches disk.
 */
@Service
public class ImportStagingService {

    private static final Duration TTL = Duration.ofMinutes(15);

    private final Map<String, StagedImport> staged = new ConcurrentHashMap<>();

    public String stage(Long userId, ParsedTable table) {
        String token = UUID.randomUUID().toString();
        staged.put(token, new StagedImport(userId, table, Instant.now().plus(TTL)));
        return token;
    }

    public ParsedTable consume(String token, Long userId) {
        StagedImport entry = staged.get(token);
        if (entry == null || entry.expiresAt().isBefore(Instant.now()) || !entry.userId().equals(userId)) {
            staged.remove(token);
            throw new BadRequestException("Upload preview has expired. Please upload the file again");
        }
        return entry.table();
    }

    public void discard(String token) {
        staged.remove(token);
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    void evictExpired() {
        Instant now = Instant.now();
        staged.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }
}
