package com.kbo.stats.domain;

public enum PlayerType {
    BATTER("타자"),
    PITCHER("투수");

    private final String displayName;

    PlayerType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
