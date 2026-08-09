package com.emiprotecciones;

import io.github.flemmli97.flan.claim.Claim;
import io.github.flemmli97.flan.claim.ClaimStorage;
import io.github.flemmli97.flan.player.ClaimMode;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Server-only bridge to Flan.
 *
 * Kept out of the common initializer so clients do not need Flan installed
 * just to render the GeckoLib protection cores.
 */
public final class FlanBridge {
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
        return claim.getClaimID();
    }

    public static void removeClaim(ServerWorld world, UUID claimId) {
        ClaimStorage storage = ClaimStorage.get(world);
        Claim claim = storage.getFromUUID(claimId);
        if (claim != null) {
            storage.deleteClaim(claim, true, ClaimMode.DEFAULT, world);
        }
    }
}
