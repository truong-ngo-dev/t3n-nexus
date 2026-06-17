package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.lib.common.domain.exception.DomainException;

public enum CategoryLevel {
    L1(1), L2(2), L3(3);

    private final int value;

    CategoryLevel(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public CategoryLevel nextLevel() {
        return switch (this) {
            case L1 -> L2;
            case L2 -> L3;
            case L3 -> throw new DomainException(CategoryErrorCode.MAX_DEPTH_EXCEEDED);
        };
    }
}
