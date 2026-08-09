package com.emiprotecciones;

import com.mojang.serialization.MapCodec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public final class EmiProtecciones implements ModInitializer {
    public static final String MOD_ID = "emiprotecciones";

    public static final ProtectionCoreBlock PROTECTION_CORE = new ProtectionCoreBlock(
            AbstractBlock.Settings.create().strength(4.0F, 1200.0F).nonOpaque()
    );

    public static final Item PROTECTION_CORE_ITEM =
            new BlockItem(PROTECTION_CORE, new Item.Settings());

    public static final BlockEntityType<ProtectionCoreBlockEntity> PROTECTION_CORE_BLOCK_ENTITY =
            BlockEntityType.Builder.create(ProtectionCoreBlockEntity::new, PROTECTION_CORE).build(null);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Registry.register(Registries.BLOCK, id("protection_core"), PROTECTION_CORE);
        Registry.register(Registries.ITEM, id("protection_core"), PROTECTION_CORE_ITEM);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, id("protection_core"), PROTECTION_CORE_BLOCK_ENTITY);

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (state.isOf(PROTECTION_CORE)
                    && blockEntity instanceof ProtectionCoreBlockEntity core
                    && !core.canBreak(player)) {
                if (!world.isClient) {
                    player.sendMessage(
                            Text.literal("§cEsta Pokébola de Protección pertenece a otro jugador."),
                            true
                    );
                }
                return false;
            }
            return true;
        });
    }

    public static final class ProtectionCoreBlock extends BlockWithEntity {
        public static final MapCodec<ProtectionCoreBlock> CODEC = createCodec(ProtectionCoreBlock::new);

        public ProtectionCoreBlock(Settings settings) {
            super(settings);
        }

        @Override
        protected MapCodec<? extends BlockWithEntity> getCodec() {
            return CODEC;
        }

        @Override
        public BlockRenderType getRenderType(BlockState state) {
            return BlockRenderType.ENTITYBLOCK_ANIMATED;
        }

        @Nullable
        @Override
        public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
            return new ProtectionCoreBlockEntity(pos, state);
        }

        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
                World world,
                BlockState state,
                BlockEntityType<T> type
        ) {
            if (world.isClient) {
                return null;
            }

            return validateTicker(
                    type,
                    PROTECTION_CORE_BLOCK_ENTITY,
                    ProtectionCoreBlockEntity::serverTick
            );
        }

        @Override
        public void onPlaced(
                World world,
                BlockPos pos,
                BlockState state,
                @Nullable LivingEntity placer,
                ItemStack stack
        ) {
            super.onPlaced(world, pos, state, placer, stack);

            if (!world.isClient
                    && placer instanceof PlayerEntity player
                    && world.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
                core.setOwner(player.getUuid());
                core.startPreview();
                player.sendMessage(
                        Text.literal("§d✦ Protección preparada §7• §fÁrea visual: §d21×21"),
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
            markDirty();
        }

        public boolean canBreak(PlayerEntity player) {
            return owner == null || owner.equals(player.getUuid()) || player.isCreative();
        }

        public void startPreview() {
            previewTicks = 160;
            markDirty();
        }

        public static void serverTick(
                World world,
                BlockPos pos,
                BlockState state,
                ProtectionCoreBlockEntity core
        ) {
            if (!(world instanceof ServerWorld serverWorld) || core.previewTicks <= 0) {
                return;
            }

            if (core.previewTicks % 10 == 0) {
                spawnBoundary(serverWorld, pos);
            }
            core.previewTicks--;
        }

        private static void spawnBoundary(ServerWorld world, BlockPos center) {
            final int radius = 10;
            final double y = center.getY() + 1.05;

            for (int offset = -radius; offset <= radius; offset += 2) {
                spawn(world, center.getX() + offset + 0.5, y, center.getZ() - radius + 0.5);
                spawn(world, center.getX() + offset + 0.5, y, center.getZ() + radius + 0.5);
                spawn(world, center.getX() - radius + 0.5, y, center.getZ() + offset + 0.5);
                spawn(world, center.getX() + radius + 0.5, y, center.getZ() + offset + 0.5);
            }
        }

        private static void spawn(ServerWorld world, double x, double y, double z) {
            world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.02, 0.08, 0.02, 0.0);
        }

        @Override
        protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
            super.writeNbt(nbt, registries);
            if (owner != null) {
                nbt.putUuid("Owner", owner);
            }
        }

        @Override
        protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
            super.readNbt(nbt, registries);
            owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
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
}
