package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.lib.common.domain.exception.DomainException;

/** Cây cố định ba cấp (INV-CAT-01); cấp ba là lá. */
public enum CategoryLevel {
    L1(1), L2(2), L3(3);

    private final int value;

    CategoryLevel(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public boolean isLeaf() {
        return this == L3;
    }

    /** Cấp của con; cha đã là cấp ba thì từ chối. */
    public CategoryLevel nextLevel() {
        return switch (this) {
            case L1 -> L2;
            case L2 -> L3;
            case L3 -> throw new DomainException(CategoryErrorCode.CATEGORY_MAX_DEPTH_EXCEEDED);
        };
    }

    public static CategoryLevel of(int value) {
        return switch (value) {
            case 1 -> L1;
            case 2 -> L2;
            case 3 -> L3;
            default -> throw new IllegalArgumentException("Invalid category level: " + value);
        };
    }
}
