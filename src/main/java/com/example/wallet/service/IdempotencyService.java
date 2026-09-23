package com.example.wallet.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.wallet.entity.IdempotencyKey;
import com.example.wallet.entity.IdempotencyStatus;
import com.example.wallet.exception.IdempotencyKeyInProgressException;
import com.example.wallet.exception.IdempotencyKeyReusedException;
import com.example.wallet.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Makes a write endpoint safe to retry. Usage in a controller:
 *
 *   Optional<String> cached = idempotency.begin(userId, key, request);
 *   if (cached.isPresent()) return deserialize(cached.get());   // genuine retry: replay, do nothing
 *   try {
 *       WalletResponse result = ...do the real work...;
 *       idempotency.complete(userId, key, serialize(result));
 *       return result;
 *   } catch (RuntimeException e) {
 *       idempotency.abandon(userId, key);   // nothing happened -- free the key for a clean retry
 *       throw e;
 *   }
 *
 * Only successful completions are cached and replayed. A request that fails outright (validation,
 * wallet not found, etc.) hasn't moved any money, so there's nothing unsafe about letting the
 * client just try again -- abandon() deletes the row rather than recording the failure.
 */
@Service
public class IdempotencyService {
    private final IdempotencyKeyRepository keys;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyKeyRepository keys, ObjectMapper objectMapper) {
        this.keys = keys;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Optional<String> begin(String userId, String idempotencyKey, Object requestBody) {
        String hash = hash(requestBody);

        Optional<IdempotencyKey> existing = keys.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyKey record = existing.get();
            if (!record.getRequestHash().equals(hash)) {
                throw new IdempotencyKeyReusedException();
            }
            if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                throw new IdempotencyKeyInProgressException();
            }
            return Optional.of(record.getResponse());
        }

        try {
            keys.saveAndFlush(new IdempotencyKey(userId, idempotencyKey, hash));
        } catch (DataIntegrityViolationException e) {
            // Someone else's insert won the race on UNIQUE(user_id, idempotency_key) between our
            // findBy and our insert. Don't try to recover in this transaction -- a failed insert
            // can leave the persistence context unusable. Tell the caller to retry the whole
            // request; by then the winner's row will be visible to a fresh findBy above.
            throw new IdempotencyKeyInProgressException();
        }
        return Optional.empty();
    }

    @Transactional
    public void complete(String userId, String idempotencyKey, String responseJson) {
        IdempotencyKey record = keys.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("begin() was not called for this key"));
        record.complete(responseJson);
        keys.save(record);
    }

    @Transactional
    public void abandon(String userId, String idempotencyKey) {
        keys.findByUserIdAndIdempotencyKey(userId, idempotencyKey).ifPresent(keys::delete);
    }

    private String hash(Object requestBody) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(requestBody);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not hash request body", e);
        }
    }
}
