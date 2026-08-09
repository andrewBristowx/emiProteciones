package com.emiprotecciones;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.MapCodec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
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
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
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
    public static final String EMI_PERMISSION = "emiprotecciones.tier.emi";

    public enum ProtectionTier {
        BASIC("basic", "Básica", 10, "protection_core"),
        ADVANCED("advanced", "Avanzada", 15, "protection_core_advanced"),
        EPIC("epic", "Épica", 20, "protection_core_epic"),
        LEGENDARY("legendary", "Legendaria", 30, "protection_core_legendary"),
        EMI("emi", "Emi", 60, "protection_core_emi");

        private final String id;
        private final String displayName;
        private final int radius;
        private final String textureName;

        ProtectionTier(String id, String displayName, int radius, String textureName) {
            this.id = id;
            this.displayName = displayName;
            this.radius = radius;
            this.textureName = textureName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public int radius() {
            return radius;
        }

        public int size() {
            return radius * 2 + 1;
        }

        public String textureName() {
            return textureName;
        }
    }

    private static AbstractBlock.Settings coreSettings() {
        return AbstractBlock.Settings.create().strength(4.0F, 1200.0F).nonOpaque();
    }

    public static final ProtectionCoreBlock PROTECTION_CORE = new ProtectionCoreBlock(coreSettings());
    public static final ProtectionCoreBlock PROTECTION_CORE_ADVANCED = new ProtectionCoreBlock(coreSettings());
    public static final ProtectionCoreBlock PROTECTION_CORE_EPIC = new ProtectionCoreBlock(coreSettings());
    public static final ProtectionCoreBlock PROTECTION_CORE_LEGENDARY = new ProtectionCoreBlock(coreSettings());
    public static final ProtectionCoreBlock PROTECTION_CORE_EMI = new ProtectionCoreBlock(coreSettings());

    public static final Item PROTECTION_CORE_ITEM = new BlockItem(PROTECTION_CORE, new Item.Settings());
    public static final Item PROTECTION_CORE_ADVANCED_ITEM = new BlockItem(PROTECTION_CORE_ADVANCED, new Item.Settings());
    public static final Item PROTECTION_CORE_EPIC_ITEM = new BlockItem(PROTECTION_CORE_EPIC, new Item.Settings());
    public static final Item PROTECTION_CORE_LEGENDARY_ITEM = new BlockItem(PROTECTION_CORE_LEGENDARY, new Item.Settings());
    public static final Item PROTECTION_CORE_EMI_ITEM = new BlockItem(PROTECTION_CORE_EMI, new Item.Settings());

    public static final BlockEntityType<ProtectionCoreBlockEntity> PROTECTION_CORE_BLOCK_ENTITY =
            BlockEntityType.Builder.create(
                    ProtectionCoreBlockEntity::new,
                    PROTECTION_CORE,
                    PROTECTION_CORE_ADVANCED,
                    PROTECTION_CORE_EPIC,
                    PROTECTION_CORE_LEGENDARY,
                    PROTECTION_CORE_EMI
            ).build(null);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        register("protection_core", PROTECTION_CORE, PROTECTION_CORE_ITEM);
        register("protection_core_advanced", PROTECTION_CORE_ADVANCED, PROTECTION_CORE_ADVANCED_ITEM);
        register("protection_core_epic", PROTECTION_CORE_EPIC, PROTECTION_CORE_EPIC_ITEM);
        register("protection_core_legendary", PROTECTION_CORE_LEGENDARY, PROTECTION_CORE_LEGENDARY_ITEM);
        register("protection_core_emi", PROTECTION_CORE_EMI, PROTECTION_CORE_EMI_ITEM);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, id("protection_core"), PROTECTION_CORE_BLOCK_ENTITY);

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (state.getBlock() instanceof ProtectionCoreBlock
                    && blockEntity instanceof ProtectionCoreBlockEntity core
                    && !core.canBreak(player)) {
                if (!world.isClient) {
                    player.sendMessage(Text.literal("§cEsta protección pertenece a otro jugador."), true);
                }
                return false;
            }
            return true;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClient
                    && world instanceof ServerWorld serverWorld
                    && state.getBlock() instanceof ProtectionCoreBlock
                    && blockEntity instanceof ProtectionCoreBlockEntity core) {
                core.removeFlanClaim(serverWorld);
            }
        });

        registerManagementCommands();
    }

    private static void register(String path, Block block, Item item) {
        Registry.register(Registries.BLOCK, id(path), block);
        Registry.register(Registries.ITEM, id(path), item);
    }

    private static void registerManagementCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("emiprotecciones")
                        .then(CommandManager.literal("manage")
                                .then(CommandManager.argument("pos", StringArgumentType.word())
                                        .then(CommandManager.literal("add")
                                                .then(CommandManager.argument("player", StringArgumentType.word())
                                                        .executes(EmiProtecciones::addGuest)))
                                        .then(CommandManager.literal("remove")
                                                .then(CommandManager.argument("player", StringArgumentType.word())
                                                        .executes(EmiProtecciones::removeGuest)))
                                        .then(CommandManager.literal("guestperm")
                                                .then(CommandManager.argument("permission", StringArgumentType.word())
                                                        .then(CommandManager.argument("allow", BoolArgumentType.bool())
                                                                .executes(EmiProtecciones::setGuestPermission))))
                                        .then(CommandManager.literal("globalperm")
                                                .then(CommandManager.argument("permission", StringArgumentType.word())
                                                        .then(CommandManager.argument("allow", BoolArgumentType.bool())
                                                                .executes(EmiProtecciones::setGlobalPermission))))
                                        .then(CommandManager.literal("preview")
                                                .executes(EmiProtecciones::previewClaim))))
        ));
    }

    private static int addGuest(CommandContext<ServerCommandSource> context) {
        ManagedCore managed = managedCore(context);
        if (managed == null) return 0;

        String name = StringArgumentType.getString(context, "player");
        boolean ok = FlanBridge.addGuest(managed.world(), managed.core().getFlanClaimId(), managed.player(), name);
        managed.player().sendMessage(
                Text.literal(ok
                        ? "§d✦ " + name + " §fahora está permitido en tu protección."
                        : "§c✦ No se pudo añadir a " + name + ". Debe estar conectado."),
                false
        );
        return ok ? 1 : 0;
    }

    private static int removeGuest(CommandContext<ServerCommandSource> context) {
        ManagedCore managed = managedCore(context);
        if (managed == null) return 0;

        String name = StringArgumentType.getString(context, "player");
        boolean ok = FlanBridge.removeGuest(managed.world(), managed.core().getFlanClaimId(), managed.player(), name);
        managed.player().sendMessage(
                Text.literal(ok
                        ? "§d✦ " + name + " §fya no está permitido en tu protección."
                        : "§c✦ No se pudo quitar a " + name + ". Debe estar conectado."),
                false
        );
        return ok ? 1 : 0;
    }

    private static int setGuestPermission(CommandContext<ServerCommandSource> context) {
        ManagedCore managed = managedCore(context);
        if (managed == null) return 0;

        String permission = StringArgumentType.getString(context, "permission");
        boolean allow = BoolArgumentType.getBool(context, "allow");
        boolean ok = FlanBridge.setGuestPermission(
                managed.world(), managed.core().getFlanClaimId(), managed.player(), permission, allow
        );
        managed.player().sendMessage(
                Text.literal(ok
                        ? "§d✦ Permiso actualizado: §f" + permission + " §7→ " + (allow ? "§aPERMITIR" : "§cBLOQUEAR")
                        : "§c✦ No se pudo modificar ese permiso."),
                true
        );
        return ok ? 1 : 0;
    }

    private static int setGlobalPermission(CommandContext<ServerCommandSource> context) {
        ManagedCore managed = managedCore(context);
        if (managed == null) return 0;

        String permission = StringArgumentType.getString(context, "permission");
        boolean allow = BoolArgumentType.getBool(context, "allow");
        boolean ok = FlanBridge.setGlobalPermission(
                managed.world(), managed.core().getFlanClaimId(), managed.player(), permission, allow
        );
        managed.player().sendMessage(
                Text.literal(ok
                        ? "§d✦ Regla de parcela actualizada: §f" + permission + " §7→ " + (allow ? "§aACTIVA" : "§cBLOQUEADA")
                        : "§c✦ No se pudo modificar esa regla."),
                true
        );
        return ok ? 1 : 0;
    }

    private static int previewClaim(CommandContext<ServerCommandSource> context) {
        ManagedCore managed = managedCore(context);
        if (managed == null) return 0;
        managed.core().startPreview();
        managed.player().sendMessage(Text.literal("§d✦ Mostrando límites de la protección."), true);
        return 1;
    }

    @Nullable
    private static ManagedCore managedCore(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            return null;
        }

        String raw = StringArgumentType.getString(context, "pos");
        String[] parts = raw.split(",");
        if (parts.length != 3) {
            player.sendMessage(Text.literal("§cPosición de protección inválida."), true);
            return null;
        }

        try {
            BlockPos pos = new BlockPos(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            );
            ServerWorld world = context.getSource().getWorld();
            if (!(world.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core)) {
                player.sendMessage(Text.literal("§cNo hay una protección válida en esa posición."), true);
                return null;
            }
            if (!core.canManage(player)) {
                player.sendMessage(Text.literal("§cSolo el propietario puede administrar esta protección."), true);
                return null;
            }
            if (core.getFlanClaimId() == null) {
                player.sendMessage(Text.literal("§cEsta protección no tiene un claim de Flan asociado."), true);
                return null;
            }
            return new ManagedCore(world, player, core);
        } catch (NumberFormatException ignored) {
            player.sendMessage(Text.literal("§cPosición de protección inválida."), true);
            return null;
        }
    }

    private record ManagedCore(ServerWorld world, ServerPlayerEntity player, ProtectionCoreBlockEntity core) {
    }

    public static ProtectionTier tierOf(Block block) {
        if (block == PROTECTION_CORE_ADVANCED) return ProtectionTier.ADVANCED;
        if (block == PROTECTION_CORE_EPIC) return ProtectionTier.EPIC;
        if (block == PROTECTION_CORE_LEGENDARY) return ProtectionTier.LEGENDARY;
        if (block == PROTECTION_CORE_EMI) return ProtectionTier.EMI;
        return ProtectionTier.BASIC;
    }

    public static boolean canUseEmiTier(ServerPlayerEntity player) {
        if (FabricLoader.getInstance().isModLoaded("luckperms")) {
            try {
                return PermissionBridge.hasPermission(player, EMI_PERMISSION) || player.hasPermissionLevel(4);
            } catch (RuntimeException | LinkageError ignored) {
                // If the LuckPerms bridge cannot initialize, OP remains a safe fallback.
            }
        }
        return player.hasPermissionLevel(4);
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

            if (world.isClient
                    || !(world instanceof ServerWorld serverWorld)
                    || !(placer instanceof ServerPlayerEntity player)
                    || !(world.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core)) {
                return;
            }

            ProtectionTier tier = core.getTier();

            if (tier == ProtectionTier.EMI && !canUseEmiTier(player)) {
                player.sendMessage(Text.literal("§c✦ La Protección Emi es exclusiva de Emi / administración."), false);
                serverWorld.removeBlock(pos, false);
                if (!player.isCreative()) {
                    player.giveItemStack(new ItemStack(state.getBlock().asItem()));
                }
                return;
            }

            if (!FabricLoader.getInstance().isModLoaded("flan")) {
                player.sendMessage(Text.literal("§c✦ EmiProtecciones necesita Flan instalado en el servidor."), false);
                serverWorld.removeBlock(pos, false);
                if (!player.isCreative()) {
                    player.giveItemStack(new ItemStack(state.getBlock().asItem()));
                }
                return;
            }

            core.setOwner(player.getUuid());

            if (!core.createFlanClaim(serverWorld, player)) {
                player.sendMessage(
                        Text.literal("§c✦ No se pudo crear la protección: el área se cruza con otra protección."),
                        false
                );
                serverWorld.removeBlock(pos, false);
                if (!player.isCreative()) {
                    player.giveItemStack(new ItemStack(state.getBlock().asItem()));
                }
                return;
            }

            core.startPreview();
            player.sendMessage(
                    Text.literal(
                            "§d✦ Protección " + tier.displayName()
                                    + " creada §7• §fÁrea: §d" + tier.size() + "×" + tier.size()
                    ),
                    false
            );
        }
    }

    public static final class ProtectionCoreBlockEntity extends BlockEntity implements GeoBlockEntity {
        private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.protection_core.idle");

        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        private UUID owner;
        private UUID flanClaimId;
        private int previewTicks;

        public ProtectionCoreBlockEntity(BlockPos pos, BlockState state) {
            super(PROTECTION_CORE_BLOCK_ENTITY, pos, state);
        }

        public ProtectionTier getTier() {
            return tierOf(getCachedState().getBlock());
        }

        public UUID getOwner() {
            return owner;
        }

        public void setOwner(UUID owner) {
            this.owner = owner;
            markDirty();
        }

        public UUID getFlanClaimId() {
            return flanClaimId;
        }

        public void setFlanClaimId(UUID claimId) {
            this.flanClaimId = claimId;
            markDirty();
        }

        public boolean canBreak(PlayerEntity player) {
            return owner == null || owner.equals(player.getUuid()) || player.isCreative();
        }

        public boolean canManage(ServerPlayerEntity player) {
            return owner == null
                    || owner.equals(player.getUuid())
                    || player.isCreative()
                    || player.hasPermissionLevel(4);
        }

        public boolean createFlanClaim(ServerWorld world, ServerPlayerEntity player) {
            UUID created = FlanBridge.createClaim(world, pos, getTier(), player);
            if (created == null) {
                return false;
            }
            setFlanClaimId(created);
            return true;
        }

        public void removeFlanClaim(ServerWorld world) {
            if (flanClaimId == null || !FabricLoader.getInstance().isModLoaded("flan")) {
                return;
            }

            FlanBridge.removeClaim(world, flanClaimId);
            flanClaimId = null;
            markDirty();
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
                spawnBoundary(serverWorld, pos, core.getTier().radius());
            }
            core.previewTicks--;
        }

        private static void spawnBoundary(ServerWorld world, BlockPos center, int radius) {
            final double y = center.getY() + 1.05;
            final int step = Math.max(2, radius / 10);

            for (int offset = -radius; offset <= radius; offset += step) {
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
            if (flanClaimId != null) {
                nbt.putUuid("FlanClaimId", flanClaimId);
            }
        }

        @Override
        protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
            super.readNbt(nbt, registries);
            owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
            flanClaimId = nbt.containsUuid("FlanClaimId") ? nbt.getUuid("FlanClaimId") : null;
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
