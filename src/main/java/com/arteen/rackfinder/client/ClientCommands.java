package com.arteen.rackfinder.client;

import com.arteen.rackfinder.client.gui.RackSearchScreen;
import com.arteen.rackfinder.data.ItemAggregate;
import com.arteen.rackfinder.data.RackStore;
import com.arteen.rackfinder.scan.RackScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

import java.util.List;
import java.util.Locale;

/**
 * Client-only commands (no server permissions needed).
 */
public final class ClientCommands {
    private ClientCommands() {
    }

    public static void register(final RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rackfinder")
                .then(Commands.literal("open").executes(context -> {
                    Minecraft.getInstance().setScreen(new RackSearchScreen());
                    return 1;
                }))
                .then(Commands.literal("rescan").executes(context -> {
                    RackScanner.requestRescan();
                    reply(context.getSource(), "rescanning loaded chunks...");
                    return 1;
                }))
                .then(Commands.literal("save").executes(context -> {
                    RackStore.INSTANCE.save();
                    reply(context.getSource(), "index saved");
                    return 1;
                }))
                .then(Commands.literal("clear").executes(context -> {
                    RackStore.INSTANCE.clear();
                    TargetItem.clear();
                    HighlightState.clear();
                    reply(context.getSource(), "index cleared");
                    return 1;
                }))
                .then(Commands.literal("target").executes(context -> {
                    reply(context.getSource(), TargetItem.isSet()
                            ? "target: " + TargetItem.get().displayName() + " (" + TargetItem.get().itemId() + ")"
                            : "target: none");
                    return 1;
                }))
                .then(Commands.literal("dump").executes(context -> {
                    dump(context.getSource());
                    return 1;
                })));
    }

    private static void dump(final CommandSourceStack source) {
        final RackStore store = RackStore.INSTANCE;
        reply(source, String.format(Locale.ROOT,
                "world=%s racks=%d withData=%d empty=%d items=%d",
                store.worldKey(), store.rackCount(), store.documentedRackCount(),
                store.emptyRackCount(), store.itemCount()));
        final List<ItemAggregate> items = store.aggregates();
        final int limit = Math.min(items.size(), 12);
        for (int index = 0; index < limit; index++) {
            final ItemAggregate aggregate = items.get(index);
            reply(source, String.format(Locale.ROOT, " - %s x%d in %d rack(s)",
                    aggregate.key().displayName(), aggregate.total(), aggregate.rackCount()));
        }
        if (items.isEmpty()) {
            reply(source, "no rack contents known yet: walk around the colony or open a rack");
        }
    }

    private static void reply(final CommandSourceStack source, final String message) {
        source.sendSystemMessage(Component.literal("[RackFinder] " + message));
    }
}
