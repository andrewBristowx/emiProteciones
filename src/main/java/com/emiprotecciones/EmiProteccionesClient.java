package com.emiprotecciones;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public final class EmiProteccionesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRendererFactories.register(
                EmiProtecciones.PROTECTION_CORE_BLOCK_ENTITY,
                context -> new ProtectionCoreRenderer()
        );
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
