package com.voidpanel.feature;

import com.voidpanel.util.Msg;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;

/** /sit: ride an invisible, zero-size display entity that deletes itself when you stand up. */
public final class Sit {
	private Sit() {}

	public static void toggle(ServerPlayer player) {
		if (player.getVehicle() instanceof Seat) {
			stand(player);
			return;
		}
		if (player.isPassenger()) {
			Msg.err(player, "You're already riding something.");
			return;
		}
		if (!player.onGround() || player.isSwimming() || player.isSleeping() || player.isFallFlying() || player.getAbilities().flying) {
			Msg.err(player, "You need to be standing on the ground to sit.");
			return;
		}
		ServerLevel level = player.level();
		Seat seat = new Seat(level);
		seat.setPos(player.getX(), player.getY(), player.getZ());
		seat.setYRot(player.getYRot());
		level.addFreshEntity(seat);
		if (!player.startRiding(seat, true, false)) {
			seat.discard();
			Msg.err(player, "You can't sit here.");
			return;
		}
		Msg.actionBar(player, "&7You sat down &8| &fsneak &7or &f/sit &7to stand");
	}

	public static void stand(ServerPlayer player) {
		if (player.getVehicle() instanceof Seat seat) {
			player.stopRiding();
			seat.discard();
		}
	}

	/** Client sees a plain item_display holding nothing. Never saved to disk. */
	public static final class Seat extends Display.ItemDisplay {
		public Seat(Level level) {
			super(net.minecraft.world.entity.EntityTypes.ITEM_DISPLAY, level);
			this.setInvisible(true);
			this.setNoGravity(true);
		}

		@Override
		public void tick() {
			super.tick();
			if (!this.level().isClientSide() && this.tickCount > 5 && !this.isVehicle()) this.discard();
		}

		@Override
		protected void removePassenger(Entity passenger) {
			super.removePassenger(passenger);
			if (!this.level().isClientSide()) this.discard();
		}

		@Override
		public boolean shouldBeSaved() {
			return false;
		}
	}
}
