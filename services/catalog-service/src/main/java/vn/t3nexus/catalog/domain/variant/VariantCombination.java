package vn.t3nexus.catalog.domain.variant;

import vn.t3nexus.lib.common.domain.model.ValueObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public final class VariantCombination implements ValueObject {

    private final List<VariantAttributePair> pairs;

    public VariantCombination(List<VariantAttributePair> pairs) {
        this.pairs = List.copyOf(pairs);
    }

    public List<VariantAttributePair> getPairs() {
        return pairs;
    }

    // Deterministic SHA-256 hash — sorted by templateId to ensure order-insensitivity
    public String toHash() {
        String input = pairs.stream()
                .sorted(Comparator.comparing(p -> p.templateId().getValue()))
                .map(p -> p.templateId().getValue() + ":" + p.optionId().getValue())
                .collect(Collectors.joining(";"));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // Order-insensitive equality — [A,B] == [B,A]
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VariantCombination other)) return false;
        return new HashSet<>(pairs).equals(new HashSet<>(other.pairs));
    }

    @Override
    public int hashCode() {
        return new HashSet<>(pairs).hashCode();
    }
}
