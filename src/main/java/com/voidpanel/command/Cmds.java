package com.voidpanel.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import com.voidpanel.VoidPanel;
import com.voidpanel.config.Config;
import com.voidpanel.data.DataStore;
import com.voidpanel.data.PlayerData;
import com.voidpanel.data.PlayerData.Home;
import com.voidpanel.feature.Afk;
import com.voidpanel.feature.Chat;
import com.voidpanel.feature.Ignore;
import com.voidpanel.feature.Invsee;
import com.voidpanel.feature.Ranks;
import com.voidpanel.feature.StaffChat;
import com.voidpanel.feature.Vanish;
import com.voidpanel.feature.Warps;
import com.voidpanel.feature.ChatPrompts;
import com.voidpanel.feature.Emotes;
import com.voidpanel.feature.Messages;
import com.voidpanel.feature.Hat;
import com.voidpanel.feature.Homes;
import com.voidpanel.feature.Moderation;
import com.voidpanel.feature.Sit;
import com.voidpanel.feature.Spawn;
import com.voidpanel.feature.Tpa;
import com.voidpanel.gui.BansGui;
import com.voidpanel.gui.DailyGui;
import com.voidpanel.gui.GravesGui;
import com.voidpanel.gui.HistoryGui;
import com.voidpanel.gui.IgnoreGui;
import com.voidpanel.gui.PlayersGui;
import com.voidpanel.gui.ProfileGui;
import com.voidpanel.gui.PunishGui;
import com.voidpanel.gui.RanksGui;
import com.voidpanel.gui.TagsGui;
import com.voidpanel.gui.TopGui;
import com.voidpanel.gui.WarpsGui;
import com.voidpanel.gui.ChatFormatGui;
import com.voidpanel.gui.GradientGui;
import com.voidpanel.gui.PetsGui;
import com.voidpanel.gui.HomesGui;
import com.voidpanel.gui.MainGui;
import com.voidpanel.gui.PermissionEditorGui;
import com.voidpanel.gui.PermissionsGui;
import com.voidpanel.gui.RtpGui;
import com.voidpanel.gui.WhitelistGui;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class Cmds {
	private Cmds() {}

	private interface PlayerAction {
		void run(ServerPlayer player, CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
	}

	private static final SuggestionProvider<CommandSourceStack> ONLINE = (ctx, b) -> {
		ServerPlayer viewer = ctx.getSource().getPlayer();
		return SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerList().getPlayers().stream()
			.filter(p -> viewer == null || Vanish.canSee(viewer, p)).map(p -> p.getGameProfile().name()), b);
	};

	private static final SuggestionProvider<CommandSourceStack> WARPS = (ctx, b) ->
		SharedSuggestionProvider.suggest(DataStore.root().warps.values().stream().map(w -> w.name), b);

	private static final SuggestionProvider<CommandSourceStack> RANKS = (ctx, b) ->
		SharedSuggestionProvider.suggest(DataStore.root().ranks.keySet(), b);

	private static final SuggestionProvider<CommandSourceStack> TAGS = (ctx, b) ->
		SharedSuggestionProvider.suggest(DataStore.root().tags.keySet(), b);

	private static final SuggestionProvider<CommandSourceStack> DURATIONS = (ctx, b) ->
		SharedSuggestionProvider.suggest(new String[] {"30m", "1h", "6h", "1d", "3d", "7d", "30d", "perm"}, b);

	private static final SuggestionProvider<CommandSourceStack> HOMES = (ctx, b) -> {
		ServerPlayer p = ctx.getSource().getPlayer();
		if (p == null) return b.buildFuture();
		return SharedSuggestionProvider.suggest(DataStore.get(p.getUUID()).homes.values().stream().map(h -> h.name), b);
	};

	private static final SuggestionProvider<CommandSourceStack> TPA_FROM = (ctx, b) -> {
		ServerPlayer p = ctx.getSource().getPlayer();
		if (p == null) return b.buildFuture();
		return SharedSuggestionProvider.suggest(Tpa.pendingNames(p), b);
	};

	private static final SuggestionProvider<CommandSourceStack> BANNED = (ctx, b) ->
		SharedSuggestionProvider.suggest(Moderation.bans(ctx.getSource().getServer()).stream()
			.filter(e -> e.getUser() != null).map(e -> e.getUser().name()), b);

	private static final SuggestionProvider<CommandSourceStack> BLACKLISTED = (ctx, b) ->
		SharedSuggestionProvider.suggest(Moderation.ipBans(ctx.getSource().getServer()).stream().map(e -> e.getUser()), b);

	/** Wraps a player-only action so console gets a clear error. */
	private static int player(CommandContext<CommandSourceStack> ctx, PlayerAction action) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		action.run(p, ctx);
		return 1;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> cmd(String name, Perm perm) {
		return literal(name).requires(s -> Perms.check(s, perm));
	}

	private static Consumer<String> reply(CommandSourceStack source) {
		return legacy -> source.sendSuccess(() -> Msg.prefixed(Text.of(legacy)), false);
	}

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		// ---------------------------------------------------------------- panel
		var panel = d.register(cmd("voidpanel", Perm.PANEL)
			.executes(c -> player(c, (p, x) -> new MainGui(p).open()))
			.then(literal("reload").requires(s -> Perms.check(s, Perm.CHAT_ADMIN) || Perms.check(s, Perm.PERMISSIONS)).executes(c -> {
				Config.load();
				c.getSource().sendSuccess(() -> Msg.prefixed(Text.of("&aConfig reloaded.")), true);
				return 1;
			}))
			.then(literal("chat").requires(s -> Perms.check(s, Perm.CHAT_ADMIN)).executes(c -> player(c, (p, x) -> new ChatFormatGui(p).open())))
			.then(literal("cancel").executes(c -> player(c, (p, x) -> {
				if (ChatPrompts.cancel(p)) Msg.info(p, "Cancelled.");
			}))));
		d.register(cmd("vp", Perm.PANEL).executes(c -> player(c, (p, x) -> new MainGui(p).open())).redirect(panel));
		// Used by the [Cancel] button on chat prompts; works without the panel permission.
		d.register(literal("vpcancel").executes(c -> player(c, (p, x) -> {
			if (ChatPrompts.cancel(p)) Msg.info(p, "Cancelled.");
		})));

		// ---------------------------------------------------------------- homes
		d.register(cmd("sethome", Perm.SETHOME)
			.executes(c -> player(c, (p, x) -> Homes.set(p, "home")))
			.then(argument("name", StringArgumentType.word()).executes(c -> player(c, (p, x) ->
				Homes.set(p, StringArgumentType.getString(x, "name"))))));

		d.register(cmd("homes", Perm.HOMES).executes(c -> player(c, (p, x) -> new HomesGui(p).open())));

		d.register(cmd("home", Perm.HOME)
			.executes(c -> player(c, (p, x) -> homeDefault(p)))
			.then(argument("name", StringArgumentType.word()).suggests(HOMES).executes(c -> player(c, (p, x) -> {
				String name = StringArgumentType.getString(x, "name");
				Home home = Homes.find(DataStore.get(p.getUUID()), name);
				if (home == null) Msg.err(p, "You don't have a home called &f" + name + "&c.");
				else Homes.go(p, home);
			}))));

		d.register(cmd("delhome", Perm.HOMES)
			.then(argument("name", StringArgumentType.word()).suggests(HOMES).executes(c -> player(c, (p, x) -> {
				String name = StringArgumentType.getString(x, "name");
				Home home = Homes.find(DataStore.get(p.getUUID()), name);
				if (home == null) Msg.err(p, "You don't have a home called &f" + name + "&c.");
				else Homes.delete(p, home);
			}))));

		// ---------------------------------------------------------------- teleport requests
		d.register(cmd("tpa", Perm.TPA)
			.then(argument("player", StringArgumentType.word()).suggests(ONLINE).executes(c -> player(c, (p, x) -> {
				ServerPlayer target = online(x, "player");
				if (target != null) Tpa.request(p, target);
			}))));
		d.register(cmd("tpaccept", Perm.TPACCEPT)
			.executes(c -> player(c, (p, x) -> Tpa.accept(p, null)))
			.then(argument("player", StringArgumentType.word()).suggests(TPA_FROM).executes(c -> player(c, (p, x) ->
				Tpa.accept(p, StringArgumentType.getString(x, "player"))))));
		d.register(cmd("tpdeny", Perm.TPDENY)
			.executes(c -> player(c, (p, x) -> Tpa.deny(p, null)))
			.then(argument("player", StringArgumentType.word()).suggests(TPA_FROM).executes(c -> player(c, (p, x) ->
				Tpa.deny(p, StringArgumentType.getString(x, "player"))))));
		d.register(cmd("tpacancel", Perm.TPA)
			.then(argument("player", StringArgumentType.word()).suggests(ONLINE).executes(c -> player(c, (p, x) -> {
				ServerPlayer target = online(x, "player");
				if (target != null) Tpa.cancel(p, target);
			}))));

		// ---------------------------------------------------------------- travel
		d.register(cmd("rtp", Perm.RTP).executes(c -> player(c, (p, x) -> RtpGui.openFor(p))));
		d.register(cmd("back", Perm.BACK).executes(c -> player(c, (p, x) -> Spawn.back(p))));
		d.register(cmd("spawn", Perm.SPAWN).executes(c -> player(c, (p, x) -> Spawn.go(p))));
		d.register(cmd("setspawn", Perm.SETSPAWN).executes(c -> player(c, (p, x) -> Spawn.set(p))));

		// ---------------------------------------------------------------- fun
		d.register(cmd("hat", Perm.HAT).executes(c -> player(c, (p, x) -> Hat.wear(p))));
		d.register(cmd("sit", Perm.SIT).executes(c -> player(c, (p, x) -> Sit.toggle(p))));

		// greedyString, because word() rejects the & and # used by colour codes.
		d.register(cmd("nick", Perm.NICK)
			.then(argument("nickname", StringArgumentType.greedyString())
				.suggests((c, b) -> SharedSuggestionProvider.suggest(new String[] {"off"}, b))
				.executes(c -> player(c, (p, x) -> Chat.setNick(p, p, StringArgumentType.getString(x, "nickname").trim())))));
		d.register(cmd("setnick", Perm.NICK_OTHERS)
			.then(argument("player", StringArgumentType.word()).suggests(ONLINE)
				.then(argument("nickname", StringArgumentType.greedyString()).executes(c -> player(c, (p, x) -> {
					ServerPlayer target = online(x, "player");
					if (target != null) Chat.setNick(p, target, StringArgumentType.getString(x, "nickname").trim());
				})))));

		// ---------------------------------------------------------------- private messages
		for (String vanilla : new String[] {"msg", "tell", "w"}) removeRoot(d, vanilla);
		var msg = d.register(cmd("msg", Perm.MSG)
			.then(argument("player", StringArgumentType.word()).suggests(ONLINE)
				.then(argument("message", StringArgumentType.greedyString()).executes(c -> {
					ServerPlayer target = online(c, "player");
					if (target != null) Messages.send(c.getSource(), target, StringArgumentType.getString(c, "message"));
					return 1;
				}))));
		for (String alias : new String[] {"tell", "w", "whisper", "m", "pm"}) {
			d.register(cmd(alias, Perm.MSG).redirect(msg));
		}
		var reply = d.register(cmd("r", Perm.MSG)
			.then(argument("message", StringArgumentType.greedyString()).executes(c -> player(c, (p, x) ->
				Messages.reply(p, StringArgumentType.getString(x, "message"), x.getSource())))));
		d.register(cmd("reply", Perm.MSG).redirect(reply));
		d.register(cmd("socialspy", Perm.SOCIALSPY).executes(c -> player(c, (p, x) -> Messages.toggleSpy(p))));

		// ---------------------------------------------------------------- emotes, pets, gradients
		d.register(cmd("emotes", Perm.EMOTES)
			.executes(c -> player(c, (p, x) -> Emotes.list(p)))
			.then(literal("toggle").executes(c -> player(c, (p, x) -> Emotes.toggle(p)))));
		d.register(cmd("pets", Perm.PETS)
			.executes(c -> player(c, (p, x) -> new PetsGui(p, p.getUUID(), p.getGameProfile().name()).open()))
			.then(argument("player", StringArgumentType.word()).suggests(ONLINE).requires(s -> Perms.check(s, Perm.PETS_OTHERS))
				.executes(c -> player(c, (p, x) -> {
					String name = StringArgumentType.getString(x, "player");
					Moderation.resolve(p.level().getServer(), name, result -> {
						if (result.isEmpty()) Msg.err(p, "No player called &f" + name + "&c.");
						else new PetsGui(p, result.get().id(), result.get().name()).open();
					});
				}))));
		d.register(cmd("gradient", Perm.GRADIENT).executes(c -> player(c, (p, x) -> new GradientGui(p).open())));

		// ---------------------------------------------------------------- whitelist
		// Adds "panel" to vanilla's /whitelist (which already needs op level 3).
		d.register(literal("whitelist").then(literal("panel").requires(s -> Perms.check(s, Perm.WHITELIST))
			.executes(c -> player(c, (p, x) -> new WhitelistGui(p).open()))));
		// Same panel, for non-ops who were given the permission.
		d.register(cmd("wlpanel", Perm.WHITELIST).executes(c -> player(c, (p, x) -> new WhitelistGui(p).open())));

		// ---------------------------------------------------------------- bans
		removeRoot(d, "ban");
		d.register(cmd("ban", Perm.BAN)
			.executes(c -> player(c, (p, x) -> new BansGui(p, false).open()))
			.then(argument("player", StringArgumentType.word()).suggests(ONLINE)
				.executes(c -> ban(c, null))
				.then(argument("reason", StringArgumentType.greedyString()).executes(c -> ban(c, StringArgumentType.getString(c, "reason"))))));
		d.register(cmd("unban", Perm.BAN)
			.then(argument("player", StringArgumentType.word()).suggests(BANNED).executes(c -> {
				CommandSourceStack src = c.getSource();
				String name = StringArgumentType.getString(c, "player");
				var entry = Moderation.bans(src.getServer()).stream()
					.filter(e -> e.getUser() != null && e.getUser().name().equalsIgnoreCase(name)).findFirst();
				if (entry.isEmpty()) {
					src.sendFailure(Msg.prefixed(Text.of("&c" + name + " isn't banned.")));
					return 0;
				}
				Moderation.unban(src.getServer(), entry.get().getUser());
				reply(src).accept("&aUnbanned &f" + entry.get().getUser().name() + "&a.");
				return 1;
			})));

		d.register(cmd("blacklist", Perm.BLACKLIST)
			.executes(c -> player(c, (p, x) -> new BansGui(p, true).open()))
			.then(argument("target", StringArgumentType.word()).suggests(ONLINE)
				.executes(c -> blacklist(c, null))
				.then(argument("reason", StringArgumentType.greedyString()).executes(c -> blacklist(c, StringArgumentType.getString(c, "reason"))))));
		d.register(cmd("unblacklist", Perm.BLACKLIST)
			.then(argument("target", StringArgumentType.word()).suggests(BLACKLISTED).executes(c -> {
				CommandSourceStack src = c.getSource();
				String target = StringArgumentType.getString(c, "target");
				var ip = Moderation.ipOf(src.getServer(), target);
				if (ip.isEmpty() || !Moderation.unblacklist(src.getServer(), ip.get())) {
					src.sendFailure(Msg.prefixed(Text.of("&cThat isn't blacklisted.")));
					return 0;
				}
				reply(src).accept("&aRemoved &f" + ip.get() + " &afrom the blacklist.");
				return 1;
			})));

		registerMore(d);

		// ---------------------------------------------------------------- permissions
		d.register(cmd("permissions", Perm.PERMISSIONS)
			.executes(c -> player(c, (p, x) -> new PermissionsGui(p).open()))
			.then(argument("player", StringArgumentType.word()).suggests(ONLINE).executes(c -> player(c, (p, x) -> {
				String name = StringArgumentType.getString(x, "player");
				Moderation.resolve(p.level().getServer(), name, result -> {
					if (result.isEmpty()) Msg.err(p, "No Minecraft account called &f" + name + "&c.");
					else new PermissionEditorGui(p, result.get()).open();
				});
			}))));
	}

	/** Resolve a (maybe offline) player name, then run the action on the server thread. */
	private static int withProfile(CommandContext<CommandSourceStack> c, String arg, Consumer<net.minecraft.server.players.NameAndId> action) {
		String name = StringArgumentType.getString(c, arg);
		CommandSourceStack src = c.getSource();
		Moderation.resolve(src.getServer(), name, r -> {
			if (r.isEmpty()) src.sendFailure(Msg.prefixed(Text.of("&cNo player called " + name + ".")));
			else action.accept(r.get());
		});
		return 1;
	}

	private static String by(CommandSourceStack src) {
		return src.getTextName();
	}

	private static void registerMore(CommandDispatcher<CommandSourceStack> d) {
		// ---------------------------------------------------------------- warps
		d.register(cmd("warps", Perm.WARP).executes(c -> player(c, (p, x) -> new WarpsGui(p).open())));
		d.register(cmd("warp", Perm.WARP)
			.executes(c -> player(c, (p, x) -> new WarpsGui(p).open()))
			.then(argument("name", StringArgumentType.word()).suggests(WARPS).executes(c -> player(c, (p, x) -> {
				var w = Warps.get(StringArgumentType.getString(x, "name"));
				if (w == null) Msg.err(p, "There's no warp called that.");
				else Warps.go(p, w);
			}))));
		d.register(cmd("setwarp", Perm.SETWARP).then(argument("name", StringArgumentType.word()).executes(c -> player(c, (p, x) ->
			Warps.set(p, StringArgumentType.getString(x, "name"))))));
		d.register(cmd("delwarp", Perm.SETWARP).then(argument("name", StringArgumentType.word()).suggests(WARPS).executes(c -> player(c, (p, x) -> {
			var w = Warps.get(StringArgumentType.getString(x, "name"));
			if (w == null) Msg.err(p, "There's no warp called that.");
			else Warps.delete(p, w);
		}))));

		// ---------------------------------------------------------------- tpahere, afk, graves, ignore
		d.register(cmd("tpahere", Perm.TPAHERE).then(argument("player", StringArgumentType.word()).suggests(ONLINE).executes(c -> player(c, (p, x) -> {
			ServerPlayer target = online(x, "player");
			if (target != null) Tpa.request(p, target, true);
		}))));
		d.register(cmd("afk", Perm.AFK).executes(c -> player(c, (p, x) -> Afk.toggle(p))));
		d.register(cmd("graves", Perm.GRAVES).executes(c -> player(c, (p, x) -> new GravesGui(p).open()))
			.then(literal("toggle").executes(c -> player(c, (p, x) -> com.voidpanel.feature.Graves.togglePersonal(p))))
			.then(literal("server").requires(s -> Perms.check(s, Perm.GRAVES_TOGGLE)).executes(c -> {
				com.voidpanel.config.Config.get().gravesEnabled = !com.voidpanel.config.Config.get().gravesEnabled;
				com.voidpanel.config.Config.save();
				reply(c.getSource()).accept("&aGraves are now " + (com.voidpanel.config.Config.get().gravesEnabled ? "ON" : "&cOFF") + " &afor the server.");
				return 1;
			})));
		d.register(cmd("ignore", Perm.IGNORE)
			.executes(c -> player(c, (p, x) -> new IgnoreGui(p).open()))
			.then(argument("player", StringArgumentType.word()).suggests(ONLINE).executes(c -> player(c, (p, x) ->
				withProfile(x, "player", who -> Ignore.toggle(p, who))))));

		// ---------------------------------------------------------------- tags
		// Not "/tag": vanilla already has that for entity tags.
		d.register(cmd("tags", Perm.TAGS).executes(c -> player(c, (p, x) -> new TagsGui(p).open()))
			.then(literal("give").requires(src -> Perms.check(src, Perm.TAGS_ADMIN)).then(argument("player", StringArgumentType.word()).suggests(ONLINE).then(argument("tag", StringArgumentType.word()).suggests(TAGS)
				.executes(c -> withProfile(c, "player", who -> {
					String tag = StringArgumentType.getString(c, "tag").toLowerCase();
					if (!DataStore.root().tags.containsKey(tag)) {
						c.getSource().sendFailure(Msg.prefixed(Text.of("&cNo tag called " + tag + ".")));
						return;
					}
					var data = DataStore.get(who.id());
					if (data.name == null) data.name = who.name();
					if (!data.unlockedTags.contains(tag)) data.unlockedTags.add(tag);
					DataStore.markDirty();
					reply(c.getSource()).accept("&aGave the &f" + tag + " &atag to " + who.name() + ".");
					ServerPlayer online = c.getSource().getServer().getPlayerList().getPlayer(who.id());
					if (online != null) online.sendSystemMessage(Msg.prefixed(Text.of("&aYou unlocked the ").append(Text.of(DataStore.root().tags.get(tag).display))
						.append(Text.of(" &atag! ")).append(Msg.button("&e[Equip]", "/tags", "&7Open /tags"))));
				})))))
			.then(literal("take").requires(src -> Perms.check(src, Perm.TAGS_ADMIN)).then(argument("player", StringArgumentType.word()).suggests(ONLINE).then(argument("tag", StringArgumentType.word()).suggests(TAGS)
				.executes(c -> withProfile(c, "player", who -> {
					String tag = StringArgumentType.getString(c, "tag").toLowerCase();
					var data = DataStore.get(who.id());
					data.unlockedTags.remove(tag);
					if (tag.equals(data.tag)) data.tag = null;
					DataStore.markDirty();
					reply(c.getSource()).accept("&aTook the &f" + tag + " &atag from " + who.name() + ".");
				}))))));

		// ---------------------------------------------------------------- daily, profiles, stats
		d.register(cmd("daily", Perm.DAILY).executes(c -> player(c, (p, x) -> new DailyGui(p).open())));
		d.register(cmd("players", Perm.PROFILE).executes(c -> player(c, (p, x) -> new PlayersGui(p, false).open())));
		d.register(cmd("profile", Perm.PROFILE)
			.executes(c -> player(c, (p, x) -> new ProfileGui(p, new net.minecraft.server.players.NameAndId(p.getGameProfile())).open()))
			.then(argument("player", StringArgumentType.word()).suggests(ONLINE).executes(c -> player(c, (p, x) ->
				withProfile(x, "player", who -> new ProfileGui(p, who).open())))));
		d.register(cmd("top", Perm.TOP).executes(c -> player(c, (p, x) -> new TopGui(p).open())));

		// ---------------------------------------------------------------- punishments
		d.register(cmd("punish", Perm.PUNISH)
			.executes(c -> player(c, (p, x) -> new PlayersGui(p, true).open()))
			.then(argument("player", StringArgumentType.word()).suggests(ONLINE).executes(c -> player(c, (p, x) ->
				withProfile(x, "player", who -> new PunishGui(p, who).open())))));
		d.register(cmd("history", Perm.PUNISH).then(argument("player", StringArgumentType.word()).suggests(ONLINE).executes(c -> player(c, (p, x) ->
			withProfile(x, "player", who -> new HistoryGui(p, who).open())))));
		d.register(cmd("mute", Perm.PUNISH).then(argument("player", StringArgumentType.word()).suggests(ONLINE)
			.executes(c -> mute(c, "perm", "Muted by staff"))
			.then(argument("duration", StringArgumentType.word()).suggests(DURATIONS)
				.executes(c -> mute(c, StringArgumentType.getString(c, "duration"), "Muted by staff"))
				.then(argument("reason", StringArgumentType.greedyString())
					.executes(c -> mute(c, StringArgumentType.getString(c, "duration"), StringArgumentType.getString(c, "reason")))))));
		d.register(cmd("unmute", Perm.PUNISH).then(argument("player", StringArgumentType.word()).suggests(ONLINE).executes(c -> withProfile(c, "player", who -> {
			if (Moderation.unmute(c.getSource().getServer(), who)) reply(c.getSource()).accept("&aUnmuted " + who.name() + ".");
			else c.getSource().sendFailure(Msg.prefixed(Text.of("&c" + who.name() + " isn't muted.")));
		}))));
		d.register(cmd("tempban", Perm.PUNISH).then(argument("player", StringArgumentType.word()).suggests(ONLINE)
			.then(argument("duration", StringArgumentType.word()).suggests(DURATIONS)
				.executes(c -> tempban(c, "Banned by staff"))
				.then(argument("reason", StringArgumentType.greedyString()).executes(c -> tempban(c, StringArgumentType.getString(c, "reason")))))));
		d.register(cmd("warn", Perm.PUNISH).then(argument("player", StringArgumentType.word()).suggests(ONLINE)
			.then(argument("reason", StringArgumentType.greedyString()).executes(c -> withProfile(c, "player", who ->
				Moderation.warn(c.getSource().getServer(), by(c.getSource()), who, StringArgumentType.getString(c, "reason"), reply(c.getSource())))))));
		removeRoot(d, "kick");
		d.register(cmd("kick", Perm.PUNISH).then(argument("player", StringArgumentType.word()).suggests(ONLINE)
			.executes(c -> kick(c, "Kicked by staff"))
			.then(argument("reason", StringArgumentType.greedyString()).executes(c -> kick(c, StringArgumentType.getString(c, "reason"))))));

		// ---------------------------------------------------------------- staff tools
		d.register(cmd("invsee", Perm.INVSEE).then(argument("player", StringArgumentType.word()).suggests(ONLINE).executes(c -> player(c, (p, x) ->
			withProfile(x, "player", who -> Invsee.openInventory(p, who))))));
		d.register(cmd("endersee", Perm.ENDERSEE).then(argument("player", StringArgumentType.word()).suggests(ONLINE).executes(c -> player(c, (p, x) ->
			withProfile(x, "player", who -> Invsee.openEnder(p, who))))));
		d.register(cmd("vanish", Perm.VANISH).executes(c -> player(c, (p, x) -> Vanish.toggle(p))));
		d.register(cmd("v", Perm.VANISH).executes(c -> player(c, (p, x) -> Vanish.toggle(p))));
		var sc = d.register(cmd("staffchat", Perm.STAFFCHAT)
			.executes(c -> player(c, (p, x) -> StaffChat.toggle(p)))
			.then(argument("message", StringArgumentType.greedyString()).executes(c -> player(c, (p, x) ->
				StaffChat.send(p, StringArgumentType.getString(x, "message"))))));
		d.register(cmd("sc", Perm.STAFFCHAT).executes(c -> player(c, (p, x) -> StaffChat.toggle(p))).redirect(sc));

		// ---------------------------------------------------------------- ranks
		d.register(cmd("ranks", Perm.RANKS).executes(c -> player(c, (p, x) -> new RanksGui(p).open())));
		d.register(cmd("rank", Perm.RANKS)
			.executes(c -> player(c, (p, x) -> new RanksGui(p).open()))
			.then(literal("set").then(argument("player", StringArgumentType.word()).suggests(ONLINE).then(argument("rank", StringArgumentType.word()).suggests(RANKS)
				.executes(c -> setRank(c, "perm"))
				.then(argument("duration", StringArgumentType.word()).suggests(DURATIONS)
					.executes(c -> setRank(c, StringArgumentType.getString(c, "duration")))))))
			.then(literal("list").executes(c -> {
				for (var r : Ranks.sorted()) {
					c.getSource().sendSuccess(() -> Msg.prefixed(Text.of(r.display + " &8(" + r.id + ", weight " + r.weight + ", " + Ranks.members(r) + " members)")), false);
				}
				return 1;
			})));
	}

	private static int setRank(CommandContext<CommandSourceStack> c, String duration) {
		var rank = Ranks.get(StringArgumentType.getString(c, "rank"));
		long millis = Moderation.parseDuration(duration);
		if (rank == null || millis < 0) {
			c.getSource().sendFailure(Msg.prefixed(Text.of(rank == null ? "&cNo rank called that." : "&cUse a duration like 1d, 7d, 30d or perm.")));
			return 0;
		}
		return withProfile(c, "player", who -> {
			Ranks.set(c.getSource().getServer(), who.id(), who.name(), rank, by(c.getSource()), millis);
			reply(c.getSource()).accept("&a" + who.name() + " is now " + Text.strip(rank.display)
				+ (millis > 0 ? " for " + Moderation.formatDuration(millis) : " permanently") + ".");
		});
	}

	private static int mute(CommandContext<CommandSourceStack> c, String duration, String reason) {
		long millis = Moderation.parseDuration(duration);
		if (millis < 0) {
			c.getSource().sendFailure(Msg.prefixed(Text.of("&cUse a duration like 30m, 2h, 1d, 7d or perm.")));
			return 0;
		}
		return withProfile(c, "player", who -> Moderation.mute(c.getSource().getServer(), by(c.getSource()), who, millis, reason, reply(c.getSource())));
	}

	private static int tempban(CommandContext<CommandSourceStack> c, String reason) {
		long millis = Moderation.parseDuration(StringArgumentType.getString(c, "duration"));
		if (millis < 0) {
			c.getSource().sendFailure(Msg.prefixed(Text.of("&cUse a duration like 30m, 2h, 1d, 7d or perm.")));
			return 0;
		}
		return withProfile(c, "player", who -> Moderation.tempban(c.getSource().getServer(), by(c.getSource()), who, millis, reason, reply(c.getSource())));
	}

	private static int kick(CommandContext<CommandSourceStack> c, String reason) {
		ServerPlayer target = online(c, "player");
		if (target == null) return 0;
		Moderation.kick(c.getSource().getServer(), by(c.getSource()), target, reason, reply(c.getSource()));
		return 1;
	}

	private static void homeDefault(ServerPlayer p) {
		PlayerData data = DataStore.get(p.getUUID());
		if (data.homes.isEmpty()) {
			p.sendSystemMessage(Msg.prefixed(Text.of("&cYou don't have any homes yet. ")
				.append(Msg.suggest("&e[Set one here]", "/sethome home", "&7Click to fill in /sethome"))));
			return;
		}
		Home named = Homes.find(data, "home");
		if (data.homes.size() == 1 || named != null) {
			Homes.go(p, named != null ? named : data.homes.values().iterator().next());
			return;
		}
		if (Perms.has(p, Perm.HOMES)) {
			new HomesGui(p).open();
			return;
		}
		var line = Msg.prefixed(Text.of("&7Pick a home: "));
		for (Home h : data.homes.values()) {
			line.append(Msg.button("&a[" + h.name + "] ", "/home " + h.name, "&7" + h.loc.dimName() + " " + h.loc.coords()));
		}
		p.sendSystemMessage(line);
	}

	private static ServerPlayer online(CommandContext<CommandSourceStack> ctx, String arg) {
		String name = StringArgumentType.getString(ctx, arg);
		ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
		ServerPlayer viewer = ctx.getSource().getPlayer();
		if (target != null && viewer != null && !Vanish.canSee(viewer, target)) target = null;
		if (target == null) ctx.getSource().sendFailure(Msg.prefixed(Text.of("&c" + name + " isn't online.")));
		return target;
	}

	private static int ban(CommandContext<CommandSourceStack> c, String reason) {
		CommandSourceStack src = c.getSource();
		MinecraftServer server = src.getServer();
		String name = StringArgumentType.getString(c, "player");
		String by = src.getTextName();
		Moderation.resolve(server, name, result -> {
			if (result.isEmpty()) {
				src.sendFailure(Msg.prefixed(Text.of("&cNo Minecraft account called " + name + ".")));
				return;
			}
			if (src.getPlayer() != null && src.getPlayer().getUUID().equals(result.get().id())) {
				src.sendFailure(Msg.prefixed(Text.of("&cYou can't ban yourself.")));
				return;
			}
			Moderation.ban(server, by, result.get(), reason, reply(src));
		});
		return 1;
	}

	private static int blacklist(CommandContext<CommandSourceStack> c, String reason) {
		CommandSourceStack src = c.getSource();
		MinecraftServer server = src.getServer();
		String target = StringArgumentType.getString(c, "target");
		var ip = Moderation.ipOf(server, target);
		if (ip.isEmpty()) {
			src.sendFailure(Msg.prefixed(Text.of("&cNo IP known for " + target + ". They need to have joined at least once.")));
			return 0;
		}
		if (src.getPlayer() != null && ip.get().equals(src.getPlayer().getIpAddress())) {
			src.sendFailure(Msg.prefixed(Text.of("&cThat's your own IP address.")));
			return 0;
		}
		String label = Moderation.looksLikeIp(target) ? target : target + " &7(" + ip.get() + ")";
		Moderation.blacklist(server, src.getTextName(), ip.get(), label, reason, reply(src));
		return 1;
	}

	/** Brigadier has no remove; reach into the root node's maps to drop a vanilla command. */
	@SuppressWarnings("unchecked")
	private static void removeRoot(CommandDispatcher<CommandSourceStack> d, String name) {
		try {
			for (String fieldName : new String[] {"children", "literals", "arguments"}) {
				Field f = CommandNode.class.getDeclaredField(fieldName);
				f.setAccessible(true);
				((Map<String, ?>) f.get(d.getRoot())).remove(name);
			}
		} catch (ReflectiveOperationException e) {
			VoidPanel.LOGGER.warn("Couldn't replace vanilla /{}; VoidPanel's version will merge with it", name, e);
		}
	}
}
