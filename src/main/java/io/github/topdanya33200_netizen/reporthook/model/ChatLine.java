package io.github.topdanya33200_netizen.reporthook.model;

import java.time.Instant;

/**
 * One chat message of the reported player, kept as evidence.
 */
public record ChatLine(Instant time, String message) {
}
