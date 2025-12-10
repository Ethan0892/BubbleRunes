package com.bubblecraft.bubblerune;

public enum RuneTier {
    COMMON("common"),
    UNCOMMON("uncommon"),
    RARE("rare"),
    EPIC("epic"),
    LEGENDARY("legendary"),
    SPECIAL("special"),
    VERYSPECIAL("veryspecial");

    private final String configKey;

    RuneTier(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }
}
