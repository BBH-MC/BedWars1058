package com.andrei1058.bedwars.support.version.v1_21_R7;

import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.shop.ShopHolo;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.arena.team.TeamColor;
import com.andrei1058.bedwars.api.entity.Despawnable;
import com.andrei1058.bedwars.api.events.player.PlayerKillEvent;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.api.language.Messages;
import com.andrei1058.bedwars.api.server.VersionSupport;
import com.andrei1058.bedwars.support.version.common.VersionCommon;
import com.andrei1058.bedwars.support.version.v1_21_R7.despawnable.DespawnableAttributes;
import com.andrei1058.bedwars.support.version.v1_21_R7.despawnable.DespawnableFactory;
import com.andrei1058.bedwars.support.version.v1_21_R7.despawnable.DespawnableType;
import com.mojang.datafixers.util.Pair;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketPlayOutEntityDestroy;
import net.minecraft.network.protocol.game.PacketPlayOutEntityEquipment;
import net.minecraft.network.protocol.game.PacketPlayOutEntityHeadRotation;
import net.minecraft.network.protocol.game.PacketPlayOutEntityVelocity;
import net.minecraft.network.protocol.game.PacketPlayOutSpawnEntity;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.network.PlayerConnection;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBase;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Bed;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.command.Command;
import org.bukkit.craftbukkit.v1_21_R7.CraftServer;
import org.bukkit.craftbukkit.v1_21_R7.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_21_R7.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_21_R7.util.CraftMagicNumbers;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

@SuppressWarnings("unused")
public class v1_21_R7 extends VersionSupport {

    /**
     * Vanilla item NBT keys that used to be written straight onto the item tag.
     * Since 1.20.5 item data lives in data components, so these are routed
     * through the Bukkit meta API instead of the plugin's own data container.
     */
    private static final String VANILLA_POTION_KEY = "Potion";
    private static final String VANILLA_POTION_COLOR_KEY = "CustomPotionColor";

    private static final String ATTACK_DAMAGE_KEY = "generic.attackDamage";

    private final DespawnableFactory despawnableFactory;

    public v1_21_R7(Plugin plugin, String name) {
        super(plugin, name);
        loadDefaultEffects();
        this.despawnableFactory = new DespawnableFactory(this);
    }

    @Override
    public void registerVersionListeners() {
        new VersionCommon(this);
    }

    @Override
    public void registerCommand(String name, Command cmd) {
        ((CraftServer) getPlugin().getServer()).getCommandMap().register(name, cmd);
    }

    @Override
    public void sendTitle(@NotNull Player p, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        p.sendTitle(title == null ? " " : title, subtitle == null ? " " : subtitle, fadeIn, stay, fadeOut);
    }

    public void spawnSilverfish(Location loc, ITeam bedWarsTeam, double speed, double health, int despawn, double damage) {
        var attr = new DespawnableAttributes(DespawnableType.SILVERFISH, speed, health, damage, despawn);
        var entity = despawnableFactory.spawn(attr, loc, bedWarsTeam);

        new Despawnable(
                entity,
                bedWarsTeam, despawn,
                Messages.SHOP_UTILITY_NPC_SILVERFISH_NAME,
                PlayerKillEvent.PlayerKillCause.SILVERFISH_FINAL_KILL,
                PlayerKillEvent.PlayerKillCause.SILVERFISH
        );
    }

    @Override
    public void spawnIronGolem(Location loc, ITeam bedWarsTeam, double speed, double health, int despawn) {
        var attr = new DespawnableAttributes(DespawnableType.IRON_GOLEM, speed, health, 4, despawn);
        var entity = despawnableFactory.spawn(attr, loc, bedWarsTeam);
        new Despawnable(
                entity,
                bedWarsTeam, despawn,
                Messages.SHOP_UTILITY_NPC_IRON_GOLEM_NAME,
                PlayerKillEvent.PlayerKillCause.IRON_GOLEM_FINAL_KILL,
                PlayerKillEvent.PlayerKillCause.IRON_GOLEM
        );
    }

    @Override
    public void playAction(@NotNull Player p, String text) {
        p.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.translateAlternateColorCodes('&', text)
                )
        );
    }

    @Override
    public boolean isBukkitCommandRegistered(String name) {
        return ((CraftServer) getPlugin().getServer()).getCommandMap().getCommand(name) != null;
    }

    @Override
    public org.bukkit.inventory.ItemStack getItemInHand(@NotNull Player p) {
        return p.getInventory().getItemInMainHand();
    }

    @Override
    public void hideEntity(@NotNull Entity e, Player p) {
        PacketPlayOutEntityDestroy packet = new PacketPlayOutEntityDestroy(e.getEntityId());
        this.sendPacket(p, packet);
    }

    @Override
    public void minusAmount(Player p, org.bukkit.inventory.@NotNull ItemStack i, int amount) {
        if (i.getAmount() - amount <= 0) {
            if (p.getInventory().getItemInOffHand().equals(i)) {
                p.getInventory().setItemInOffHand(null);
            } else {
                p.getInventory().removeItem(i);
            }
            return;
        }
        i.setAmount(i.getAmount() - amount);

        p.updateInventory();
    }

    @Override
    public void setSource(@NotNull TNTPrimed tnt, Player owner) {
        // exposed by the Bukkit API since 1.13, no need to poke at the NMS field
        tnt.setSource(owner);
    }

    @Override
    public boolean isArmor(org.bukkit.inventory.@Nullable ItemStack itemStack) {
        if (null == itemStack) return false;
        Material type = itemStack.getType();
        return Tag.ITEMS_HEAD_ARMOR.isTagged(type)
                || Tag.ITEMS_CHEST_ARMOR.isTagged(type)
                || Tag.ITEMS_LEG_ARMOR.isTagged(type)
                || Tag.ITEMS_FOOT_ARMOR.isTagged(type)
                || Material.ELYTRA == type;
    }

    @Override
    public boolean isTool(org.bukkit.inventory.@Nullable ItemStack itemStack) {
        if (null == itemStack) return false;
        return Tag.ITEMS_TOOLS.isTagged(itemStack.getType());
    }

    @Override
    public boolean isSword(org.bukkit.inventory.@Nullable ItemStack itemStack) {
        if (null == itemStack) return false;
        return Tag.ITEMS_SWORDS.isTagged(itemStack.getType());
    }

    @Override
    public boolean isAxe(org.bukkit.inventory.@Nullable ItemStack itemStack) {
        if (null == itemStack) return false;
        return Tag.ITEMS_AXES.isTagged(itemStack.getType());
    }

    @Override
    public boolean isBow(org.bukkit.inventory.@Nullable ItemStack itemStack) {
        if (null == itemStack) return false;
        Material type = itemStack.getType();
        return Material.BOW == type || Material.CROSSBOW == type;
    }

    @Override
    public boolean isProjectile(org.bukkit.inventory.@Nullable ItemStack itemStack) {
        if (null == itemStack) return false;
        Material type = itemStack.getType();
        return Material.EGG == type || materialFireball() == type
                || materialSnowball() == type || Material.ARROW == type;
    }

    @Override
    public boolean isInvisibilityPotion(org.bukkit.inventory.@NotNull ItemStack itemStack) {
        if (!itemStack.getType().equals(org.bukkit.Material.POTION)) return false;

        org.bukkit.inventory.meta.PotionMeta pm = (org.bukkit.inventory.meta.PotionMeta) itemStack.getItemMeta();

        return pm != null && pm.hasCustomEffects() && pm.hasCustomEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
    }

    @Override
    public void registerEntities() {
    }

    @Override
    public void spawnShop(@NotNull Location loc, String name1, List<Player> players, IArena arena) {
        Location l = loc.clone();

        if (l.getWorld() == null) return;
        Villager vlg = (Villager) l.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        vlg.setAI(false);
        vlg.setRemoveWhenFarAway(false);
        vlg.setCollidable(false);
        vlg.setInvulnerable(true);
        vlg.setSilent(true);

        for (Player p : players) {
            String[] name = Language.getMsg(p, name1).split(",");
            if (name.length == 1) {
                ArmorStand a = createArmorStand(name[0], l.clone().add(0, 1.85, 0));
                new ShopHolo(Language.getPlayerLanguage(p).getIso(), a, null, l, arena);
            } else {
                ArmorStand a = createArmorStand(name[0], l.clone().add(0, 2.1, 0));
                ArmorStand b = createArmorStand(name[1], l.clone().add(0, 1.85, 0));
                new ShopHolo(Language.getPlayerLanguage(p).getIso(), a, b, l, arena);
            }
        }
        for (ShopHolo sh : ShopHolo.getShopHolo()) {
            if (sh.getA() == arena) {
                sh.update();
            }
        }
    }

    @Override
    public double getDamage(org.bukkit.inventory.ItemStack i) {
        Double damage = readData(i, ATTACK_DAMAGE_KEY, PersistentDataType.DOUBLE);
        return null == damage ? 0D : damage;
    }

    private static ArmorStand createArmorStand(String name, Location loc) {
        if (loc == null) return null;
        if (loc.getWorld() == null) return null;
        ArmorStand a = loc.getWorld().spawn(loc, ArmorStand.class);
        a.setGravity(false);
        a.setVisible(false);
        a.setCustomNameVisible(true);
        a.setCustomName(name);
        return a;
    }

    @Override
    public void voidKill(Player p) {
        EntityPlayer player = getPlayer(p);
        // hurt(WorldServer, DamageSource, float) since 1.21; o() is the out_of_world source
        player.a(player.A(), player.en().o(), 1000);
    }

    @Override
    public void hideArmor(@NotNull Player victim, Player receiver) {
        List<Pair<EnumItemSlot, ItemStack>> items = new ArrayList<>();
        items.add(new Pair<>(EnumItemSlot.f, ItemStack.l));
        items.add(new Pair<>(EnumItemSlot.e, ItemStack.l));
        items.add(new Pair<>(EnumItemSlot.d, ItemStack.l));
        items.add(new Pair<>(EnumItemSlot.c, ItemStack.l));
        PacketPlayOutEntityEquipment packet1 = new PacketPlayOutEntityEquipment(victim.getEntityId(), items);
        sendPacket(receiver, packet1);
    }

    @Override
    public void showArmor(@NotNull Player victim, Player receiver) {
        List<Pair<EnumItemSlot, ItemStack>> items = new ArrayList<>();
        items.add(new Pair<>(EnumItemSlot.f, CraftItemStack.asNMSCopy(victim.getInventory().getHelmet())));
        items.add(new Pair<>(EnumItemSlot.e, CraftItemStack.asNMSCopy(victim.getInventory().getChestplate())));
        items.add(new Pair<>(EnumItemSlot.d, CraftItemStack.asNMSCopy(victim.getInventory().getLeggings())));
        items.add(new Pair<>(EnumItemSlot.c, CraftItemStack.asNMSCopy(victim.getInventory().getBoots())));
        PacketPlayOutEntityEquipment packet1 = new PacketPlayOutEntityEquipment(victim.getEntityId(), items);
        sendPacket(receiver, packet1);
    }

    @Override
    public void spawnDragon(Location l, ITeam bwt) {
        if (l == null || l.getWorld() == null) {
            getPlugin().getLogger().log(Level.WARNING, "Could not spawn Dragon. Location is null");
            return;
        }
        EnderDragon ed = (EnderDragon) l.getWorld().spawnEntity(l, EntityType.ENDER_DRAGON);
        ed.setPhase(EnderDragon.Phase.CIRCLING);
    }

    @Override
    public void colorBed(ITeam bwt) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockState bed = bwt.getBed().clone().add(x, 0, z).getBlock().getState();
                if (bed instanceof Bed) {
                    bed.setType(bwt.getColor().bedMaterial());
                    bed.update();
                }
            }
        }
    }

    @Override
    public void registerTntWhitelist(float endStoneBlast, float glassBlast) {
        try {
            // BlockBase#explosionResistance. The field letter shifts every release,
            // on 1.21.11 it is the float copied from BlockBase$Info#f.
            Field field = BlockBase.class.getDeclaredField("G");
            field.setAccessible(true);

            field.set(CraftMagicNumbers.getBlock(Material.END_STONE), endStoneBlast);
            field.set(CraftMagicNumbers.getBlock(Material.GLASS), glassBlast);

            Material[] coloredGlass = new Material[]{
                    Material.WHITE_STAINED_GLASS, Material.ORANGE_STAINED_GLASS,
                    Material.MAGENTA_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS,
                    Material.YELLOW_STAINED_GLASS, Material.LIME_STAINED_GLASS,
                    Material.PINK_STAINED_GLASS, Material.GRAY_STAINED_GLASS,
                    Material.LIGHT_GRAY_STAINED_GLASS, Material.CYAN_STAINED_GLASS,
                    Material.PURPLE_STAINED_GLASS, Material.BLUE_STAINED_GLASS,
                    Material.BROWN_STAINED_GLASS, Material.GREEN_STAINED_GLASS,
                    Material.RED_STAINED_GLASS, Material.BLACK_STAINED_GLASS,
                    Material.TINTED_GLASS,
            };

            Arrays.stream(coloredGlass).forEach(
                    glass -> {
                        try {
                            field.set(CraftMagicNumbers.getBlock(glass), glassBlast);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
        } catch (NoSuchFieldException | IllegalAccessException e) {
            getPlugin().getLogger().log(Level.WARNING, "Could not register the TNT whitelist", e);
        }
    }

    @Override
    public void setBlockTeamColor(@NotNull Block block, TeamColor teamColor) {
        if (block.getType().toString().contains("STAINED_GLASS") || block.getType().toString().equals("GLASS")) {
            block.setType(teamColor.glassMaterial());
        } else if (block.getType().toString().contains("_TERRACOTTA")) {
            block.setType(teamColor.glazedTerracottaMaterial());
        } else if (block.getType().toString().contains("_WOOL")) {
            block.setType(teamColor.woolMaterial());
        }
    }

    @Override
    public void setCollide(@NotNull Player p, IArena a, boolean value) {
        p.setCollidable(value);
        if (a == null) return;
        a.updateSpectatorCollideRule(p, value);
    }

    @Override
    public org.bukkit.inventory.ItemStack addCustomData(org.bukkit.inventory.ItemStack i, String data) {
        return setTag(i, VersionSupport.PLUGIN_TAG_GENERIC_KEY, data);
    }

    @Override
    public org.bukkit.inventory.ItemStack setTag(org.bukkit.inventory.ItemStack itemStack, String key, String value) {
        if (null == itemStack) return null;

        ItemMeta meta = itemStack.getItemMeta();
        if (null == meta) return itemStack;

        // "Potion" and "CustomPotionColor" used to be written straight onto the vanilla
        // item tag. They are data components now, so go through the meta API.
        if (VANILLA_POTION_KEY.equals(key) && meta instanceof PotionMeta potionMeta) {
            PotionType potionType = parsePotionType(value);
            if (null != potionType) {
                potionMeta.setBasePotionType(potionType);
                itemStack.setItemMeta(potionMeta);
            }
            return itemStack;
        }
        if (VANILLA_POTION_COLOR_KEY.equals(key) && meta instanceof PotionMeta potionMeta) {
            Color color = parseColor(value);
            if (null != color) {
                potionMeta.setColor(color);
                itemStack.setItemMeta(potionMeta);
            }
            return itemStack;
        }

        meta.getPersistentDataContainer().set(dataKey(key), PersistentDataType.STRING, value);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    @Override
    public String getTag(org.bukkit.inventory.ItemStack itemStack, String key) {
        return readData(itemStack, key, PersistentDataType.STRING);
    }

    @Override
    public boolean isCustomBedWarsItem(org.bukkit.inventory.ItemStack i) {
        return null != getTag(i, VersionSupport.PLUGIN_TAG_GENERIC_KEY);
    }

    @Override
    public String getCustomData(org.bukkit.inventory.ItemStack i) {
        return getTag(i, VersionSupport.PLUGIN_TAG_GENERIC_KEY);
    }

    @Override
    public org.bukkit.inventory.ItemStack colourItem(org.bukkit.inventory.ItemStack itemStack, ITeam bedWarsTeam) {
        if (itemStack == null) return null;
        String type = itemStack.getType().toString();
        if (isBed(itemStack.getType())) {
            return new org.bukkit.inventory.ItemStack(bedWarsTeam.getColor().bedMaterial(), itemStack.getAmount());
        } else if (type.contains("_STAINED_GLASS_PANE")) {
            return new org.bukkit.inventory.ItemStack(bedWarsTeam.getColor().glassPaneMaterial(), itemStack.getAmount());
        } else if (type.contains("STAINED_GLASS") || type.equals("GLASS")) {
            return new org.bukkit.inventory.ItemStack(bedWarsTeam.getColor().glassMaterial(), itemStack.getAmount());
        } else if (type.contains("_TERRACOTTA")) {
            return new org.bukkit.inventory.ItemStack(bedWarsTeam.getColor().glazedTerracottaMaterial(), itemStack.getAmount());
        } else if (type.contains("_WOOL")) {
            return new org.bukkit.inventory.ItemStack(bedWarsTeam.getColor().woolMaterial(), itemStack.getAmount());
        }
        return itemStack;
    }

    @Override
    public org.bukkit.inventory.ItemStack createItemStack(String material, int amount, short data) {
        org.bukkit.inventory.ItemStack i;
        try {
            i = new org.bukkit.inventory.ItemStack(org.bukkit.Material.valueOf(material), amount);
        } catch (Exception ex) {
            getPlugin().getLogger().log(Level.WARNING, material + " is not a valid " + getName() + " material!");
            i = new org.bukkit.inventory.ItemStack(org.bukkit.Material.BEDROCK);
        }
        return i;
    }

    @Override
    public org.bukkit.Material materialFireball() {
        return org.bukkit.Material.FIRE_CHARGE;
    }

    @Override
    public org.bukkit.Material materialPlayerHead() {
        return org.bukkit.Material.PLAYER_HEAD;
    }

    @Override
    public org.bukkit.Material materialSnowball() {
        return org.bukkit.Material.SNOWBALL;
    }

    @Override
    public org.bukkit.Material materialGoldenHelmet() {
        return org.bukkit.Material.GOLDEN_HELMET;
    }

    @Override
    public org.bukkit.Material materialGoldenChestPlate() {
        return org.bukkit.Material.GOLDEN_CHESTPLATE;
    }

    @Override
    public org.bukkit.Material materialGoldenLeggings() {
        return org.bukkit.Material.GOLDEN_LEGGINGS;
    }

    @Override
    public org.bukkit.Material materialNetheriteHelmet() {
        return Material.NETHERITE_HELMET;
    }

    @Override
    public org.bukkit.Material materialNetheriteChestPlate() {
        return Material.NETHERITE_CHESTPLATE;
    }

    @Override
    public org.bukkit.Material materialNetheriteLeggings() {
        return Material.NETHERITE_LEGGINGS;
    }

    @Override
    public org.bukkit.Material materialElytra() {
        return Material.ELYTRA;
    }

    @Override
    public org.bukkit.Material materialCake() {
        return org.bukkit.Material.CAKE;
    }

    @Override
    public org.bukkit.Material materialCraftingTable() {
        return org.bukkit.Material.CRAFTING_TABLE;
    }

    @Override
    public org.bukkit.Material materialEnchantingTable() {
        return org.bukkit.Material.ENCHANTING_TABLE;
    }

    @Override
    public org.bukkit.Material woolMaterial() {
        return org.bukkit.Material.WHITE_WOOL;
    }

    @Override
    public String getShopUpgradeIdentifier(org.bukkit.inventory.ItemStack itemStack) {
        String identifier = getTag(itemStack, VersionSupport.PLUGIN_TAG_TIER_KEY);
        return null == identifier ? "null" : identifier;
    }

    @Override
    public org.bukkit.inventory.ItemStack setShopUpgradeIdentifier(org.bukkit.inventory.ItemStack itemStack, String identifier) {
        return setTag(itemStack, VersionSupport.PLUGIN_TAG_TIER_KEY, identifier);
    }

    @Override
    public org.bukkit.inventory.ItemStack getPlayerHead(Player player, org.bukkit.inventory.ItemStack copyTagFrom) {
        org.bukkit.inventory.ItemStack head = new org.bukkit.inventory.ItemStack(materialPlayerHead());

        var meta = head.getItemMeta();
        if (copyTagFrom != null && null != meta) {
            ItemMeta source = copyTagFrom.getItemMeta();
            if (null != source) {
                PersistentDataContainer from = source.getPersistentDataContainer();
                PersistentDataContainer to = meta.getPersistentDataContainer();
                for (NamespacedKey key : from.getKeys()) {
                    // another plugin may own non string entries on the same item
                    if (!from.has(key, PersistentDataType.STRING)) continue;
                    String value = from.get(key, PersistentDataType.STRING);
                    if (null != value) {
                        to.set(key, PersistentDataType.STRING, value);
                    }
                }
            }
        }

        if (meta instanceof SkullMeta) {
            ((SkullMeta) meta).setOwnerProfile(player.getPlayerProfile());
        }
        head.setItemMeta(meta);
        return head;
    }

    @Override
    public void sendPlayerSpawnPackets(Player respawned, IArena arena) {
        if (respawned == null) return;
        if (arena == null) return;
        if (!arena.isPlayer(respawned)) return;

        // if method was used when the player was still in re-spawning screen
        if (arena.getRespawnSessions().containsKey(respawned)) return;

        EntityPlayer entityPlayer = getPlayer(respawned);
        PacketPlayOutSpawnEntity show = spawnPacket(respawned, entityPlayer);
        PacketPlayOutEntityVelocity playerVelocity = new PacketPlayOutEntityVelocity(entityPlayer);
        // we send head rotation packet because sometimes on respawn others see him with bad rotation
        PacketPlayOutEntityHeadRotation head = new PacketPlayOutEntityHeadRotation(entityPlayer, getCompressedAngle(entityPlayer.getBukkitYaw()));

        // retrieve current armor and in-hand items
        // we send a packet later for timing issues where other players do not see them
        List<Pair<EnumItemSlot, ItemStack>> list = getPlayerEquipment(entityPlayer);


        for (Player p : arena.getPlayers()) {
            if (p == null) continue;
            if (p.equals(respawned)) continue;
            // if p is in re-spawning screen continue
            if (arena.getRespawnSessions().containsKey(p)) continue;

            EntityPlayer boundTo = getPlayer(p);
            if (p.getWorld().equals(respawned.getWorld())) {
                if (respawned.getLocation().distance(p.getLocation()) <= arena.getRenderDistance()) {

                    // send respawned player to regular players
                    this.sendPackets(
                            p, show, head, playerVelocity,
                            new PacketPlayOutEntityEquipment(respawned.getEntityId(), list)
                    );

                    // send nearby players to respawned player
                    // if the player has invisibility hide armor
                    if (p.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY)) {
                        hideArmor(p, respawned);
                    } else {

                        PacketPlayOutSpawnEntity show2 = spawnPacket(p, boundTo);
                        PacketPlayOutEntityVelocity playerVelocity2 = new PacketPlayOutEntityVelocity(boundTo);
                        PacketPlayOutEntityHeadRotation head2 = new PacketPlayOutEntityHeadRotation(boundTo, getCompressedAngle(boundTo.getBukkitYaw()));
                        this.sendPackets(respawned, show2, playerVelocity2, head2);

                        showArmor(p, respawned);
                    }
                }
            }
        }

        for (Player spectator : arena.getSpectators()) {
            if (spectator == null) continue;
            if (spectator.equals(respawned)) continue;
            EntityPlayer boundTo = ((CraftPlayer) spectator).getHandle();
            respawned.hidePlayer(getPlugin(), spectator);
            if (spectator.getWorld().equals(respawned.getWorld())) {
                if (respawned.getLocation().distance(spectator.getLocation()) <= arena.getRenderDistance()) {

                    // send respawned player to spectator
                    this.sendPackets(
                            spectator, show, playerVelocity,
                            new PacketPlayOutEntityEquipment(respawned.getEntityId(), list),
                            new PacketPlayOutEntityHeadRotation(entityPlayer, getCompressedAngle(entityPlayer.getBukkitYaw()))
                    );
                }
            }
        }
    }

    /**
     * Since 1.21 there is no add-entity packet constructor that takes the entity alone,
     * the remaining ones either need a tracker entry or snap the position to the block grid.
     */
    private @NotNull PacketPlayOutSpawnEntity spawnPacket(@NotNull Player bukkitPlayer, @NotNull EntityPlayer entityPlayer) {
        Location location = bukkitPlayer.getLocation();
        return new PacketPlayOutSpawnEntity(
                bukkitPlayer.getEntityId(),
                bukkitPlayer.getUniqueId(),
                location.getX(), location.getY(), location.getZ(),
                location.getPitch(), location.getYaw(),
                entityPlayer.ay(),
                0,
                entityPlayer.dN(),
                location.getYaw()
        );
    }

    @Override
    public String getInventoryName(@NotNull InventoryEvent e) {
        return e.getView().getTitle();
    }

    @Override
    public void setUnbreakable(@NotNull ItemMeta itemMeta) {
        itemMeta.setUnbreakable(true);
    }

    @Override
    public String getMainLevel() {
        // the dedicated server properties accessor is obfuscated and shifts every
        // release, the first loaded world is the level named by level-name
        List<World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? "world" : worlds.get(0).getName();
    }

    @Override
    public int getVersion() {
        // impacts on sidebar
        // experimental score placeholders
        return 10;
    }

    @Override
    public void setJoinSignBackground(@NotNull BlockState b, org.bukkit.Material material) {
        if (b.getBlockData() instanceof WallSign) {
            b.getBlock().getRelative(((WallSign) b.getBlockData()).getFacing().getOppositeFace()).setType(material);
        }
    }

    @Override
    public void spigotShowPlayer(Player victim, @NotNull Player receiver) {
        receiver.showPlayer(getPlugin(), victim);
    }

    @Override
    public void spigotHidePlayer(Player victim, @NotNull Player receiver) {
        receiver.hidePlayer(getPlugin(), victim);
    }

    @Override
    public Fireball setFireballDirection(@NotNull Fireball fireball, @NotNull Vector vector) {
        // exposed by the Bukkit API, matches the old acceleration maths
        fireball.setDirection(vector.clone().multiply(0.1D));
        return fireball;
    }

    @Override
    public void playRedStoneDot(@NotNull Player player) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.RED, 1);
        Location location = player.getLocation().clone().add(0, 2.6, 0);
        for (Player inWorld : player.getWorld().getPlayers()) {
            if (inWorld.equals(player)) continue;
            inWorld.spawnParticle(Particle.DUST, location, 0, 0, 0, 0, 0, dust);
        }
    }

    @Override
    public void clearArrowsFromPlayerBody(Player player) {
        // minecraft clears them on death on newer version
    }

    private @Nullable PotionType parsePotionType(@Nullable String value) {
        if (null == value || value.isEmpty()) return null;
        String name = value.contains(":") ? value.substring(value.indexOf(':') + 1) : value;
        try {
            return PotionType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            getPlugin().getLogger().log(Level.WARNING, "Unknown potion type: " + value);
            return null;
        }
    }

    private @Nullable Color parseColor(@Nullable String value) {
        if (null == value || value.isEmpty()) return null;
        try {
            return Color.fromRGB(Integer.decode(value.trim()) & 0xFFFFFF);
        } catch (IllegalArgumentException ex) {
            getPlugin().getLogger().log(Level.WARNING, "Unknown potion color: " + value);
            return null;
        }
    }

    /**
     * Item data used to live on the raw NBT tag. Data components replaced it on 1.20.5,
     * so plugin owned values go in the persistent data container instead.
     */
    private @NotNull NamespacedKey dataKey(@NotNull String key) {
        return new NamespacedKey(getPlugin(), key.toLowerCase(Locale.ROOT).replace('.', '_'));
    }

    private <T> @Nullable T readData(org.bukkit.inventory.@Nullable ItemStack itemStack, String key,
                                     PersistentDataType<?, T> type) {
        if (null == itemStack) return null;
        ItemMeta meta = itemStack.getItemMeta();
        if (null == meta) return null;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey namespacedKey = dataKey(key);
        return container.has(namespacedKey, type) ? container.get(namespacedKey, type) : null;
    }

    public EntityPlayer getPlayer(Player player) {
        return ((CraftPlayer) player).getHandle();
    }

    public List<Pair<EnumItemSlot, ItemStack>> getPlayerEquipment(@NotNull Player player) {
        return getPlayerEquipment(getPlayer(player));
    }

    public List<Pair<EnumItemSlot, ItemStack>> getPlayerEquipment(@NotNull EntityPlayer entityPlayer) {
        List<Pair<EnumItemSlot, ItemStack>> list = new ArrayList<>();
        // getItemBySlot moved from c(..) to a(..) on 1.21
        list.add(new Pair<>(EnumItemSlot.a, entityPlayer.a(EnumItemSlot.a)));
        list.add(new Pair<>(EnumItemSlot.b, entityPlayer.a(EnumItemSlot.b)));
        list.add(new Pair<>(EnumItemSlot.f, entityPlayer.a(EnumItemSlot.f)));
        list.add(new Pair<>(EnumItemSlot.e, entityPlayer.a(EnumItemSlot.e)));
        list.add(new Pair<>(EnumItemSlot.d, entityPlayer.a(EnumItemSlot.d)));
        list.add(new Pair<>(EnumItemSlot.c, entityPlayer.a(EnumItemSlot.c)));

        return list;
    }

    @Override
    public void placeTowerBlocks(@NotNull Block b, @NotNull IArena a, @NotNull TeamColor color, int x, int y, int z) {
        b.getRelative(x, y, z).setType(color.woolMaterial());
        a.addPlacedBlock(b.getRelative(x, y, z));
    }

    @Override
    public void placeLadder(@NotNull Block b, int x, int y, int z, @NotNull IArena a, int ladderData) {
        Block block = b.getRelative(x, y, z);  //ladder block
        block.setType(Material.LADDER);
        Ladder ladder = (Ladder) block.getBlockData();
        a.addPlacedBlock(block);
        switch (ladderData) {
            case 2 -> {
                ladder.setFacing(BlockFace.NORTH);
                block.setBlockData(ladder);
            }
            case 3 -> {
                ladder.setFacing(BlockFace.SOUTH);
                block.setBlockData(ladder);
            }
            case 4 -> {
                ladder.setFacing(BlockFace.WEST);
                block.setBlockData(ladder);
            }
            case 5 -> {
                ladder.setFacing(BlockFace.EAST);
                block.setBlockData(ladder);
            }
        }
    }

    @Override
    public void playVillagerEffect(@NotNull Player player, Location location) {
        // VILLAGER_HAPPY was renamed to HAPPY_VILLAGER on 1.20.5
        player.spawnParticle(Particle.HAPPY_VILLAGER, location, 1);
    }

    private void sendPacket(Player player, Packet<?> packet) {
        // the connection field moved from c to g on 1.21
        ((CraftPlayer) player).getHandle().g.sendPacket(packet);
    }

    private void sendPackets(Player player, Packet<?> @NotNull ... packets) {
        PlayerConnection connection = ((CraftPlayer) player).getHandle().g;
        for (Packet<?> p : packets) {
            connection.sendPacket(p);
        }
    }
}
