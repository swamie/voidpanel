package com.voidpanel.gui;

import com.voidpanel.config.Config;
import com.voidpanel.data.Loc;
import com.voidpanel.feature.Combat;
import com.voidpanel.feature.Teleports;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * /rtp as a case opening: a reel of biome blocks spins across the middle row and
 * slows down onto the biome you're about to land in.
 */
public final class RtpGui extends Gui {
	private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
	private static final Item[] PANES = {
		Items.STAINED_GLASS_PANE.magenta(), Items.STAINED_GLASS_PANE.purple(), Items.STAINED_GLASS_PANE.pink(),
		Items.STAINED_GLASS_PANE.lightBlue(), Items.STAINED_GLASS_PANE.cyan(), Items.STAINED_GLASS_PANE.blue()};
	private static final Item[] REEL_ITEMS = {
		Items.GRASS_BLOCK, Items.SAND, Items.SNOW_BLOCK, Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.SPRUCE_LOG,
		Items.BIRCH_LOG, Items.DARK_OAK_LOG, Items.CHERRY_LOG, Items.MANGROVE_ROOTS, Items.RED_SAND, Items.STONE,
		Items.MYCELIUM, Items.PODZOL, Items.PALE_OAK_LOG, Items.OAK_LOG, Items.TERRACOTTA, Items.MOSS_BLOCK};

	/** Gaps (in ticks) between reel steps: fast, then slowing down. */
	private static final int[] STEPS;

	static {
		List<Integer> s = new ArrayList<>();
		for (int i = 0; i < 22; i++) s.add(1);
		for (int i = 0; i < 8; i++) s.add(2);
		for (int i = 0; i < 5; i++) s.add(3);
		s.add(4); s.add(5); s.add(6); s.add(8); s.add(10); s.add(13);
		STEPS = s.stream().mapToInt(Integer::intValue).toArray();
	}

	private enum State { IDLE, SPINNING, REVEALED }

	private record Candidate(int x, int z, Holder<Biome> biome) {}

	private final RandomSource random = RandomSource.create();
	private final List<ItemStack> reel = new ArrayList<>();
	private State state = State.IDLE;
	private List<Candidate> candidates = List.of();
	private int step, wait, revealTicks, paneShift;

	public RtpGui(ServerPlayer player) {
		super(player, 3, "&5&l✦ &8Random Teleport");
	}

	public static void openFor(ServerPlayer player) {
		long left = cooldownLeft(player);
		if (left > 0 && !player.isCreative()) {
			Msg.err(player, "You can use /rtp again in &f" + (left / 1000 + 1) + "s&c.");
			return;
		}
		new RtpGui(player).open();
	}

	private static long cooldownLeft(ServerPlayer player) {
		return COOLDOWNS.getOrDefault(player.getUUID(), 0L) - System.currentTimeMillis();
	}

	@Override
	protected void build() {
		if (state == State.IDLE) {
			fill(Items.STAINED_GLASS_PANE.black());
			for (int i : new int[] {0, 2, 4, 6, 8, 18, 20, 22, 24, 26}) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.purple()));
			int r = Config.get().rtpRange;
			set(13, ItemBuilder.of(Items.ENDER_CHEST).name("&d&lOpen the Void Crate").glint(true).lore(
				"&7Spin to be sent somewhere random",
				"&7in the &aOverworld&7.",
				"",
				"&8▪ &7Range: &f±" + r + " &7blocks from &f" + Config.get().rtpCenterX + ", " + Config.get().rtpCenterZ,
				"&8▪ &7Cooldown: &f" + Config.get().rtpCooldownSeconds + "s",
				"",
				"&e▶ Click to spin!").build(), c -> spin());
			closeButton(22);
		}
	}

	private void spin() {
		if (state != State.IDLE) return;
		if (Combat.inCombat(player)) {
			Msg.err(player, "You can't teleport while in combat!");
			close();
			return;
		}
		ServerLevel overworld = player.level().getServer().overworld();
		candidates = findCandidates(overworld);
		if (candidates.isEmpty()) {
			Msg.err(player, "Couldn't find dry land, try again.");
			close();
			return;
		}
		// Start loading the chunks now; the spin gives them time to generate.
		for (Candidate c : candidates) {
			overworld.getChunkSource().addTicketWithRadius(TicketType.PORTAL, new ChunkPos(c.x >> 4, c.z >> 4), 1);
		}
		state = State.SPINNING;
		step = 0;
		wait = 0;
		reel.clear();
		for (int i = 0; i < 9; i++) reel.add(randomReelItem());
		refreshSpin();
	}

	@Override
	protected void tick() {
		if (state == State.SPINNING) {
			if (--wait > 0) return;
			if (step >= STEPS.length) {
				reveal();
				return;
			}
			wait = STEPS[step];
			// The item that ends on the pointer (slot 13 = reel index 4) is the real destination.
			int remaining = STEPS.length - step;
			reel.remove(0);
			reel.add(remaining == 5 ? destinationItem(false) : randomReelItem());
			step++;
			paneShift++;
			refreshSpin();
			Msg.sound(player, SoundEvents.NOTE_BLOCK_HAT, 0.5f, 0.8f + step / (float) STEPS.length);
		} else if (state == State.REVEALED) {
			revealTicks++;
			if (revealTicks % 4 == 0) {
				paneShift++;
				refreshSpin();
			}
			if (revealTicks == 30) finish();
		}
	}

	private void refreshSpin() {
		// Top/bottom rows cycle colours, the middle row is the reel.
		for (int i = 0; i < 9; i++) {
			Item top = state == State.REVEALED ? (paneShift % 2 == 0 ? Items.STAINED_GLASS_PANE.lime() : Items.STAINED_GLASS_PANE.yellow())
				: PANES[(i + paneShift) % PANES.length];
			set(i, ItemBuilder.filler(top));
			set(18 + i, ItemBuilder.filler(top));
			set(9 + i, reel.get(i));
		}
		set(4, ItemBuilder.of(Items.HOPPER).name("&d▼").build());
		set(22, ItemBuilder.of(Items.END_ROD).name("&d▲").build());
	}

	private void reveal() {
		state = State.REVEALED;
		revealTicks = 0;
		reel.set(4, destinationItem(true));
		refreshSpin();
		Candidate c = candidates.get(0);
		Msg.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
		player.sendSystemMessage(Msg.prefixed(Component.literal("You rolled ").withStyle(s -> s.withColor(0xAAAAAA))
			.append(biomeName(c.biome).copy().withStyle(s -> s.withColor(0x55FF55).withBold(true)))
			.append(Component.literal(" at " + c.x + ", " + c.z).withStyle(s -> s.withColor(0xAAAAAA)))));
	}

	private void finish() {
		close();
		if (Combat.inCombat(player)) {
			Msg.err(player, "Teleport cancelled, you're in combat!");
			return;
		}
		ServerLevel overworld = player.level().getServer().overworld();
		for (Candidate c : candidates) {
			BlockPos spot = safeSpot(overworld, c.x, c.z);
			if (spot != null) {
				COOLDOWNS.put(player.getUUID(), System.currentTimeMillis() + Config.get().rtpCooldownSeconds * 1000L);
				Teleports.teleport(player, new Loc(overworld.dimension().identifier().toString(),
					spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot(), 0));
				Msg.ok(player, "Welcome to &f" + spot.getX() + ", " + spot.getY() + ", " + spot.getZ() + "&a!");
				return;
			}
		}
		Msg.err(player, "That spot wasn't safe (or still loading). No cooldown, try again!");
	}

	@Override
	protected void onClose() {
		if (state == State.SPINNING) {
			Msg.info(player, "Spin cancelled.");
			state = State.IDLE;
		}
	}

	// ---------------------------------------------------------------- world logic

	private List<Candidate> findCandidates(ServerLevel level) {
		Config cfg = Config.get();
		int range = Math.max(16, cfg.rtpRange);
		List<Candidate> found = new ArrayList<>();
		for (int tries = 0; tries < 80 && found.size() < 3; tries++) {
			int x = cfg.rtpCenterX + random.nextInt(range * 2 + 1) - range;
			int z = cfg.rtpCenterZ + random.nextInt(range * 2 + 1) - range;
			Holder<Biome> biome = level.getUncachedNoiseBiome(x >> 2, 64 >> 2, z >> 2);
			if (cfg.rtpAvoidWater && (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER))) continue;
			found.add(new Candidate(x, z, biome));
		}
		return found;
	}

	private static BlockPos safeSpot(ServerLevel level, int x, int z) {
		if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return null;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		if (y <= level.getMinY() + 1) return null;
		BlockPos ground = new BlockPos(x, y - 1, z);
		BlockState state = level.getBlockState(ground);
		if (!state.getFluidState().isEmpty()) return null;
		if (state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.FIRE) || state.is(Blocks.CAMPFIRE)
			|| state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)) return null;
		return new BlockPos(x, y, z);
	}

	// ---------------------------------------------------------------- items

	private ItemStack randomReelItem() {
		Item item = REEL_ITEMS[random.nextInt(REEL_ITEMS.length)];
		return ItemBuilder.of(item).name("&7???").build();
	}

	private ItemStack destinationItem(boolean revealed) {
		Candidate c = candidates.get(0);
		ItemBuilder b = ItemBuilder.of(iconFor(c.biome)).name(Component.empty().append(biomeName(c.biome))
			.withStyle(s -> s.withColor(revealed ? 0x55FF55 : 0xAAAAAA).withBold(revealed)));
		if (revealed) b.glint(true).lore("&7X: &f" + c.x, "&7Z: &f" + c.z, "", "&aTeleporting...");
		return b.build();
	}

	private static Component biomeName(Holder<Biome> biome) {
		Identifier id = biome.unwrapKey().map(k -> k.identifier()).orElse(Identifier.withDefaultNamespace("plains"));
		return Component.translatable(Util.makeDescriptionId("biome", id));
	}

	private static Item iconFor(Holder<Biome> biome) {
		String id = biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
		if (id.contains("badlands")) return Items.RED_SAND;
		if (id.contains("desert") || id.contains("beach")) return Items.SAND;
		if (id.contains("snowy") || id.contains("frozen") || id.contains("ice") || id.contains("grove")) return Items.SNOW_BLOCK;
		if (id.contains("jungle") || id.contains("bamboo")) return Items.JUNGLE_LOG;
		if (id.contains("savanna")) return Items.ACACIA_LOG;
		if (id.contains("taiga")) return Items.SPRUCE_LOG;
		if (id.contains("birch")) return Items.BIRCH_LOG;
		if (id.contains("pale")) return Items.PALE_OAK_LOG;
		if (id.contains("dark_forest")) return Items.DARK_OAK_LOG;
		if (id.contains("cherry")) return Items.CHERRY_LOG;
		if (id.contains("mangrove") || id.contains("swamp")) return Items.MANGROVE_ROOTS;
		if (id.contains("mushroom")) return Items.MYCELIUM;
		if (id.contains("peaks") || id.contains("slopes") || id.contains("windswept") || id.contains("stony")) return Items.STONE;
		if (id.contains("meadow")) return Items.CORNFLOWER;
		if (id.contains("flower")) return Items.POPPY;
		if (id.contains("forest")) return Items.OAK_LOG;
		if (id.contains("lush") || id.contains("dripstone") || id.contains("deep_dark")) return Items.MOSS_BLOCK;
		return Items.GRASS_BLOCK;
	}
}
