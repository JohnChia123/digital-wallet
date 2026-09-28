package com.example.wallet.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * Test-only control knob for MockGatewayService: lets a test arrange how the *next* gateway call
 * for a given wallet behaves, instead of the gateway always succeeding. Mirrors how real payment
 * sandboxes let you flag a test account to trigger specific responses (e.g. magic card numbers).
 * One-shot -- arranging a mode only affects the next call for that wallet, then reverts to SUCCESS.
 */
@Service
public class GatewaySimulator {
    public enum Mode { SUCCESS, PERMANENT_FAILURE, TIMEOUT }

    private final Map<UUID, Mode> arranged = new ConcurrentHashMap<>();

    public void arrange(UUID walletId, Mode mode) {
        arranged.put(walletId, mode);
    }

    Mode consume(UUID walletId) {
        Mode mode = arranged.remove(walletId);
        return mode == null ? Mode.SUCCESS : mode;
    }
}
