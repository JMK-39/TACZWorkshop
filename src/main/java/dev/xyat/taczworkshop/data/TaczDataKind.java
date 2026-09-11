package dev.xyat.taczworkshop.data;

import java.util.Locale;

public enum TaczDataKind {
    GUN("guns", false),
    ATTACHMENT("attachments", false),
    AMMO("ammo", false),
    MELEE("melee", true),
    THROWABLE("throwables", true),
    CONSUMABLE("consumables", true);

    private final String jsonKey;
    private final boolean inlineData;

    TaczDataKind(String jsonKey, boolean inlineData) {
        this.jsonKey = jsonKey;
        this.inlineData = inlineData;
    }

    public String jsonKey() {
        return jsonKey;
    }

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean usesInlineData() {
        return inlineData;
    }

    public boolean isTaczNative() {
        return !inlineData;
    }

    public boolean isLrTactical() {
        return inlineData;
    }

    public static TaczDataKind fromWire(String value) {
        if (value == null) throw new IllegalArgumentException("Missing TACZ data kind");
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "gun" -> GUN;
            case "attachment" -> ATTACHMENT;
            case "ammo" -> AMMO;
            case "melee" -> MELEE;
            case "throwable" -> THROWABLE;
            case "consumable" -> CONSUMABLE;
            default -> throw new IllegalArgumentException("Unknown TACZ data kind: " + value);
        };
    }
}
