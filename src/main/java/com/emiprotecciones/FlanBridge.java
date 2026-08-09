package com.emiprotecciones;

import io.github.flemmli97.flan.api.permission.BuiltinPermission;
import io.github.flemmli97.flan.claim.Claim;
import io.github.flemmli97.flan.claim.ClaimStorage;
import io.github.flemmli97.flan.player.ClaimMode;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Server-only bridge to Flan.
 *
 * Kept out of the client initializer so clients do not need Flan installed
 * just to render the GeckoLib protection cores.
 */
public final class FlanBridge {
    public static final String GUEST_GROUP = "invitados";

    private FlanBridge() {
    }

    public static UUID createClaim(
            ServerWorld world,
            BlockPos center,
            EmiProtecciones.ProtectionTier tier,
            ServerPlayerEntity player
    ) {
        int radius = tier.radius();
        BlockPos from = new BlockPos(center.getX() - radius, center.getY(), center.getZ() - radius);
        BlockPos to = new BlockPos(center.getX() + radius, center.getY(), center.getZ() + radius);

        ClaimStorage storage = ClaimStorage.get(world);

        // The protection core itself is the purchase/cost, so do not charge
        // a second time through Flan's claim-block economy.
        Claim claim = storage.createAdminClaim(from, to, world, false);
        if (claim == null) {
            return null;
        }

        storage.transferOwner(claim, player.getUuid());
        claim.setClaimName("Protección " + tier.displayName());
        ensureGuestGroup(claim);
        return claim.getClaimID();
    }

    public static void removeClaim(ServerWorld world, UUID claimId) {
        ClaimStorage storage = ClaimStorage.get(world);
        Claim claim = storage.getFromUUID(claimId);
        if (claim != null) {
            storage.deleteClaim(claim, true, ClaimMode.DEFAULT, world);
        }
    }

    public static boolean addGuest(ServerWorld world, UUID claimId, ServerPlayerEntity owner, String playerName) {
        Claim claim = claim(world, claimId);
        if (claim == null || !isOwnerOrAdmin(claim, owner)) {
            return false;
        }

        ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(playerName);
        if (target == null) {
            return false;
        }

        ensureGuestGroup(claim);
        return claim.setPlayerGroup(target.getUuid(), GUEST_GROUP, true);
    }

    public static boolean removeGuest(ServerWorld world, UUID claimId, ServerPlayerEntity owner, String playerName) {
        Claim claim = claim(world, claimId);
        if (claim == null || !isOwnerOrAdmin(claim, owner)) {
            return false;
        }

        ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(playerName);
        if (target == null) {
            return false;
        }

        return claim.setPlayerGroup(target.getUuid(), null, true);
    }

    public static boolean setGuestPermission(
            ServerWorld world,
            UUID claimId,
            ServerPlayerEntity owner,
            String permission,
            boolean allow
    ) {
        Claim claim = claim(world, claimId);
        if (claim == null || !isOwnerOrAdmin(claim, owner)) {
            return false;
        }

        Identifier id = switch (permission) {
            case "break" -> BuiltinPermission.BREAK;
            case "place" -> BuiltinPermission.PLACE;
            case "open_container" -> BuiltinPermission.OPENCONTAINER;
            case "door" -> BuiltinPermission.DOOR;
            case "button_lever" -> BuiltinPermission.BUTTONLEVER;
            case "animal_interact" -> BuiltinPermission.ANIMALINTERACT;
            default -> null;
        };

        if (id == null) {
            return false;
        }

        ensureGuestGroup(claim);
        return claim.editPerms(owner, GUEST_GROUP, id, allow ? 1 : 0);
    }

    public static boolean setGlobalPermission(
            ServerWorld world,
            UUID claimId,
            ServerPlayerEntity owner,
            String permission,
            boolean allow
    ) {
        Claim claim = claim(world, claimId);
        if (claim == null || !isOwnerOrAdmin(claim, owner)) {
            return false;
        }

        Identifier id = switch (permission) {
            case "hurt_player" -> BuiltinPermission.HURTPLAYER;
            case "explosions" -> BuiltinPermission.EXPLOSIONS;
            default -> null;
        };

        return id != null && claim.editGlobalPerms(owner, id, allow ? 1 : 0);
    }

    private static Claim claim(ServerWorld world, UUID claimId) {
        return claimId == null ? null : ClaimStorage.get(world).getFromUUID(claimId);
    }

    private static void ensureGuestGroup(Claim claim) {
        if (!claim.groups().contains(GUEST_GROUP)) {
            // -1 leaves the permission at its global default but creates the group map.
            claim.editPerms(null, GUEST_GROUP, BuiltinPermission.EDITPERMS, -1, true);
        }
    }

    private static boolean isOwnerOrAdmin(Claim claim, ServerPlayerEntity player) {
        return player.isCreative()
                || player.hasPermissionLevel(4)
                || (claim.getOwner() != null && claim.getOwner().equals(player.getUuid()));
    }
}
