package com.sogeco.fleet.modules.alert.evaluator;

/** Alerte proposee par un evaluateur, avant deduplication. */
public record AlertCandidate(String title, String description) {
}
