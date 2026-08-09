package com.emiprotecciones;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-only LuckPerms bridge. */
public final class PermissionBridge {
    private PermissionBridge() {
    }

    public static boolean hasPermission(ServerPlayerEntity player, String permission) {
        User user = LuckPermsProvider.get().getUserManager().getUser(player.getUuid());
        return user != null
                && user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
    }
}
