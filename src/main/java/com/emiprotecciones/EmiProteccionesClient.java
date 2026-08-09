package com.emiprotecciones;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public final class EmiProteccionesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRendererFactories.register(
                EmiProtecciones.PROTECTION_CORE_BLOCK_ENTITY,
                context -> new ProtectionCoreRenderer()
        );

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            BlockPos pos = hitResult.getBlockPos();
            if (world.getBlockState(pos).getBlock() instanceof EmiProtecciones.ProtectionCoreBlock) {
                MinecraftClient.getInstance().setScreen(
                        new ProtectionScreen(pos, EmiProtecciones.tierOf(world.getBlockState(pos).getBlock()))
                );
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }

    public static final class ProtectionCoreModel
            extends DefaultedBlockGeoModel<EmiProtecciones.ProtectionCoreBlockEntity> {
        public ProtectionCoreModel() {
            super(Identifier.of(EmiProtecciones.MOD_ID, "protection_core"));
        }

        @Override
        public Identifier getTextureResource(EmiProtecciones.ProtectionCoreBlockEntity core) {
            return Identifier.of(
                    EmiProtecciones.MOD_ID,
                    "textures/block/" + core.getTier().textureName() + ".png"
            );
        }
    }

    public static final class ProtectionCoreRenderer
            extends GeoBlockRenderer<EmiProtecciones.ProtectionCoreBlockEntity> {
        public ProtectionCoreRenderer() {
            super(new ProtectionCoreModel());
        }
    }
}
