package com.voidpanel.neoforge;

import com.voidpanel.VoidPanelCommon;
import com.voidpanel.command.Cmds;
import com.voidpanel.feature.AntiDupe;
import com.voidpanel.feature.Chat;
import com.voidpanel.feature.Pets;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** NeoForge entrypoint. The shared logic lives in {@link VoidPanelCommon}. */
@Mod("voidpanel")
public class VoidPanelNeoForge {
	public VoidPanelNeoForge(IEventBus modBus) {
		VoidPanelCommon.init(FMLPaths.CONFIGDIR.get());

		IEventBus bus = NeoForge.EVENT_BUS;
		bus.addListener((RegisterCommandsEvent e) -> Cmds.register(e.getDispatcher()));

		bus.addListener((ServerStartedEvent e) -> VoidPanelCommon.serverStarted(e.getServer()));
		bus.addListener((ServerStoppingEvent e) -> VoidPanelCommon.serverStopping(e.getServer()));
		bus.addListener((ServerTickEvent.Post e) -> VoidPanelCommon.serverTick(e.getServer()));

		// Player.getDisplayName() is cached on NeoForge, so nicknames go through NameFormat.
		bus.addListener((PlayerEvent.NameFormat e) -> {
			if (e.getEntity() instanceof ServerPlayer player) {
				Component nick = Chat.nick(player);
				if (nick != null) e.setDisplayname(nick);
			}
		});
		Chat.nickChanged = Player::refreshDisplayName;

		bus.addListener((ServerChatEvent e) -> {
			if (!Chat.onChat(e.getRawText(), e.getPlayer())) e.setCanceled(true);
		});

		bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
			if (e.getEntity() instanceof ServerPlayer player) VoidPanelCommon.playerJoin(player, player.level().getServer());
		});
		bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
			if (e.getEntity() instanceof ServerPlayer player) VoidPanelCommon.playerLeave(player, player.level().getServer());
		});

		// The join event fires before the entity is added, so stray menu items are cancelled
		// rather than discarded. Pets are recorded last, once nobody else has cancelled the join.
		bus.addListener((EntityJoinLevelEvent e) -> {
			if (!e.getLevel().isClientSide() && AntiDupe.isGuiDrop(e.getEntity())) e.setCanceled(true);
		});
		bus.addListener(EventPriority.LOWEST, (EntityJoinLevelEvent e) -> {
			if (!e.getLevel().isClientSide()) Pets.record(e.getEntity());
		});
		bus.addListener((EntityLeaveLevelEvent e) -> {
			if (!e.getLevel().isClientSide()) VoidPanelCommon.entityUnload(e.getEntity());
		});

		bus.addListener((LivingDamageEvent.Post e) -> {
			if (!e.getEntity().level().isClientSide()) VoidPanelCommon.afterDamage(e.getEntity(), e.getSource(), e.getInflictedDamage());
		});
		// LOWEST and not receiving cancelled events: only counts deaths that really happen.
		bus.addListener(EventPriority.LOWEST, (LivingDeathEvent e) -> {
			if (e.getEntity().level().isClientSide()) return;
			VoidPanelCommon.afterDeath(e.getEntity(), e.getSource());
			if (e.getSource().getEntity() != null) VoidPanelCommon.afterKill(e.getSource().getEntity(), e.getEntity());
		});

		bus.addListener((PlayerInteractEvent.RightClickBlock e) -> {
			if (VoidPanelCommon.graveInteract(e.getEntity(), e.getLevel(), e.getPos())) {
				e.setCancellationResult(InteractionResult.SUCCESS);
				e.setCanceled(true);
			}
		});
		bus.addListener((BreakBlockEvent e) -> {
			if (e.getLevel() instanceof Level level && VoidPanelCommon.graveInteract(e.getPlayer(), level, e.getPos())) {
				// Vanilla clients predict the break, so send the grave back to them.
				e.setNotifyClient(true);
				e.setCanceled(true);
			}
		});

		VoidPanelCommon.LOGGER.info("VoidPanel loaded");
	}
}
