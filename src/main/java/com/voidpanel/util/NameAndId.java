package com.voidpanel.util;

import com.mojang.authlib.GameProfile;
import java.util.UUID;

/** Id plus name of a player, online or offline. */
public record NameAndId(UUID id, String name) {
	public NameAndId(GameProfile profile) {
		this(profile.getId(), profile.getName());
	}

	public GameProfile toProfile() {
		return new GameProfile(id, name);
	}
}
