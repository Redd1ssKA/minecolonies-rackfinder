package com.arteen.rackfinder.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client config: stored in config/rackfinder-client.toml.
 */
public final class RackFinderConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue SCAN_RADIUS_CHUNKS;
    public static final ForgeConfigSpec.IntValue SCAN_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue FULL_REFRESH_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_HIGHLIGHTS;
    public static final ForgeConfigSpec.IntValue MAX_LABELS;
    public static final ForgeConfigSpec.BooleanValue SHOW_HUD_LIST;
    public static final ForgeConfigSpec.BooleanValue HIGHLIGHT_ON_SELECT;
    public static final ForgeConfigSpec.BooleanValue SLOT_HIGHLIGHT;
    public static final ForgeConfigSpec.IntValue AUTO_SAVE_SECONDS;
    public static final ForgeConfigSpec.BooleanValue CHAT_FEEDBACK;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Scanning").push("scan");
        SCAN_RADIUS_CHUNKS = b
                .comment("How many chunks around the player are inspected for MineColonies racks.")
                .defineInRange("radiusChunks", 8, 1, 32);
        SCAN_INTERVAL_TICKS = b
                .comment("How often (in ticks) the mod looks for newly loaded chunks to scan.")
                .defineInRange("intervalTicks", 20, 5, 200);
        FULL_REFRESH_SECONDS = b
                .comment("Re-read every loaded rack at least this often (0 = never). Helps with stale data.")
                .defineInRange("fullRefreshSeconds", 120, 0, 3600);
        b.pop();

        b.comment("Highlighting").push("highlight");
        MAX_HIGHLIGHTS = b
                .comment("Maximum number of rack outlines drawn at the same time.")
                .defineInRange("maxHighlights", 128, 1, 512);
        MAX_LABELS = b
                .comment("How many world-space labels (item name + distance) are drawn. 0 = no labels.")
                .defineInRange("maxLabels", 10, 0, 64);
        SHOW_HUD_LIST = b
                .comment("Draw a small list of the nearest highlighted racks in the corner of the screen.")
                .define("hudList", true);
        HIGHLIGHT_ON_SELECT = b
                .comment("Automatically highlight every rack that contains the selected item.")
                .define("autoHighlight", true);
        b.pop();

        b.comment("Container slots").push("inventory");
        SLOT_HIGHLIGHT = b
                .comment("Draw a green frame around slots that contain the picked search item",
                        "inside any container GUI (including the rack window).")
                .define("slotHighlight", true);
        b.pop();

        b.comment("Storage").push("storage");
        AUTO_SAVE_SECONDS = b
                .comment("How often the collected rack index is written to disk.")
                .defineInRange("autoSaveSeconds", 30, 5, 600);
        b.pop();

        b.comment("Misc").push("misc");
        CHAT_FEEDBACK = b
                .comment("Print search/rescan summaries into chat.")
                .define("chatFeedback", true);
        b.pop();

        SPEC = b.build();
    }

    private RackFinderConfig() {
    }
}
