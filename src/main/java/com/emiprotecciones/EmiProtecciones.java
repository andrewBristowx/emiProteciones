package com.emiprotecciones;

import com.mojang.serialization.MapCodec;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public final class EmiProtecciones implements ModInitializer {
    public static final String MOD_ID = "emiprotecciones";

    public static final ProtectionCoreBlock PROTECTION_CORE = new ProtectionCoreBlock(
            BlockBehaviour.Properties.of().strength(4.0F, 1200.0F).noOcclusion()
    );

    public static final Item PROTECTION_CORE_ITEM =
            new BlockItem(PROTECTION_CORE, new Item.Properties());

    public static final BlockEntityType<ProtectionCoreBlockEntity> PROTECTION_CORE_BLOCK_ENTITY =
            BlockEntityType.Builder.of(ProtectionCoreBlockEntity::new, PROTECTION_CORE).build(null);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.BLOCK, id("protection_core"), PROTECTION_CORE);
        Registry.register(BuiltInRegistries.ITEM, id("protection_core"), PROTECTION_CORE_ITEM);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("protection_core"), PROTECTION_CORE_BLOCK_ENTITY);

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (state.is(PROTECTION_CORE)
                    && blockEntity instanceof ProtectionCoreBlockEntity core
                    && !core.canBreak(player)) {
                if (!world.isClientSide) {
                    player.displayClientMessage(
                            Component.literal("§cEsta Pokébola de Protección pertenece a otro jugador."),
                            true
                    );
                }
                return false;
            }
            return true;
        });
    }

    public static final class Client implements ClientModInitializer {
        @Override
        public void onInitializeClient() {
            BlockEntityRenderers.register(
                    PROTECTION_CORE_BLOCK_ENTITY,
                    context -> new ProtectionCoreRenderer()
            );
        }
    }

    public static final class ProtectionCoreBlock extends BaseEntityBlock {
        public ProtectionCoreBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return null;
        }

        @Override
        public RenderShape getRenderShape(BlockState state) {
            return RenderShape.ENTITYBLOCK_ANIMATED;
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new ProtectionCoreBlockEntity(pos, state);
        }

        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
                Level level,
                BlockState state,
                BlockEntityType<T> type
        ) {
            if (level.isClientSide) {
                return null;
            }

            return createTickerHelper(
                    type,
                    PROTECTION_CORE_BLOCK_ENTITY,
                    ProtectionCoreBlockEntity::serverTick
            );
        }

        @Override
        public void setPlacedBy(
                Level level,
                BlockPos pos,
                BlockState state,
                @Nullable LivingEntity placer,
                ItemStack stack
        ) {
            super.setPlacedBy(level, pos, state, placer, stack);

            if (!level.isClientSide
                    && placer instanceof Player player
                    && level.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
                core.setOwner(player.getUUID());
                core.startPreview();
                player.displayClientMessage(
                        Component.literal("§d✦ Protección preparada §7• §fÁrea visual: §d21×21"),
                        false
                );
            }
        }
    }

    public static final class ProtectionCoreBlockEntity extends BlockEntity implements GeoBlockEntity {
        private static final RawAnimation IDLE =
                RawAnimation.begin().thenLoop("animation.protection_core.idle");

        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        private UUID owner;
        private int previewTicks;

        public ProtectionCoreBlockEntity(BlockPos pos, BlockState state) {
            super(PROTECTION_CORE_BLOCK_ENTITY, pos, state);
        }

        public void setOwner(UUID owner) {
            this.owner = owner;
            setChanged();
        }

        public boolean canBreak(Player player) {
            return owner == null || owner.equals(player.getUUID()) || player.isCreative();
        }

        public void startPreview() {
            previewTicks = 160;
            setChanged();
        }

        public static void serverTick(
                Level level,
                BlockPos pos,
                BlockState state,
                ProtectionCoreBlockEntity core
        ) {
            if (!(level instanceof ServerLevel serverLevel) || core.previewTicks <= 0) {
                return;
            }

            if (core.previewTicks % 10 == 0) {
                spawnBoundary(serverLevel, pos);
            }
            core.previewTicks--;
        }

        private static void spawnBoundary(ServerLevel level, BlockPos center) {
            final int radius = 10;
            final double y = center.getY() + 1.05;

            for (int offset = -radius; offset <= radius; offset += 2) {
                spawn(level, center.getX() + offset + 0.5, y, center.getZ() - radius + 0.5);
                spawn(level, center.getX() + offset + 0.5, y, center.getZ() + radius + 0.5);
                spawn(level, center.getX() - radius + 0.5, y, center.getZ() + offset + 0.5);
                spawn(level, center.getX() + radius + 0.5, y, center.getZ() + offset + 0.5);
            }
        }

        private static void spawn(ServerLevel level, double x, double y, double z) {
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.02, 0.08, 0.02, 0.0);
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            if (owner != null) {
                tag.putUUID("Owner", owner);
            }
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, state -> state.setAndContinue(IDLE)));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }
    }

    public static final class ProtectionCoreModel extends DefaultedBlockGeoModel<ProtectionCoreBlockEntity> {
        public ProtectionCoreModel() {
            super(ResourceLocation.fromNamespaceAndPath(MOD_ID, "protection_core"));
        }

        @Override
        public RenderType getRenderType(
                ProtectionCoreBlockEntity animatable,
                ResourceLocation texture
        ) {
            return RenderType.entityCutoutNoCull(getTextureResource(animatable));
        }
    }

    public static final class ProtectionCoreRenderer extends GeoBlockRenderer<ProtectionCoreBlockEntity> {
        public ProtectionCoreRenderer() {
            super(new ProtectionCoreModel());
        }
    }
}
