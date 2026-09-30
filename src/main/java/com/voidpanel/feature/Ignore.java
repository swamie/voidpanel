package com.voidpanel.feature;

import com.voidpanel.data.DataStore;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.Msg;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

/** /ignore: hide someone's chat, private messages and teleport requests. Staff can't be ignored. */
public final class Ignore {
	private Ignore() {}

	public static boolean ignores(ServerPlayer viewer, ServerPlayer sender) {
		if (viewer == sender || Perms.isAdmin(sender)) return false;
		return DataStore.find(viewer.getUUID()).map(d -> d.ignored.contains(sender.getStringUUID())).orElse(false);
	}

	public static boolean isIgnoring(UUID viewer, UUID other) {
		return DataStore.find(viewer).map(d -> d.ignored.contains(other.toString())).orElse(false);
	}

	public static void toggle(ServerPlayer player, NameAndId target) {
		if (target.id().equals(player.getUUID())) {
			Msg.err(player, "You can't ignore yourself.");
			return;
		}
		var d = DataStore.get(player.getUUID());
		String id = target.id().toString();
		if (d.ignored.remove(id)) {
			Msg.ok(player, "You're no longer ignoring &f" + target.name() + "&a.");
		} else {
			d.ignored.add(id);
			DataStore.get(target.id()).name = DataStore.get(target.id()).name == null ? target.name() : DataStore.get(target.id()).name;
			Msg.ok(player, "Ignoring &f" + target.name() + "&a. You won't see their chat, messages or requests.");
		}
		DataStore.markDirty();
	}
}
