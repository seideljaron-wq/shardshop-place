package dev.shardshopblock;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.*;

public class ShardShopBlock extends JavaPlugin {

    private static ShardShopBlock instance;

    // Registered shop block locations
    private final Set<Location> shopLocations = new HashSet<>();

    // ArmorStand UUIDs used as holograms (location key → stand UUID)
    private final Map<String, UUID> hologramStands = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadLocations();
        spawnAllHolograms();

        getServer().getPluginManager().registerEvents(new ShopBlockListener(this), this);
        getCommand("shopblock").setExecutor(new ShopBlockCommand(this));

        // Particle task – runs every 10 ticks (0.5s)
        if (getConfig().getBoolean("show-particles", true)) {
            Bukkit.getScheduler().runTaskTimer(this, new ParticleTask(this), 10L, 10L);
        }

        getLogger().info("ShardShopBlock enabled! " + shopLocations.size() + " shop block(s) loaded.");
    }

    @Override
    public void onDisable() {
        removeAllHolograms();
        getLogger().info("ShardShopBlock disabled.");
    }

    // ── Location management ──────────────────────────────────────────────────

    public Set<Location> getShopLocations() {
        return shopLocations;
    }

    public boolean isShopBlock(Location loc) {
        // Compare block coords only (ignore eye-height etc.)
        Location block = loc.getBlock().getLocation();
        for (Location l : shopLocations) {
            if (l.getWorld().equals(block.getWorld())
                    && l.getBlockX() == block.getBlockX()
                    && l.getBlockY() == block.getBlockY()
                    && l.getBlockZ() == block.getBlockZ()) {
                return true;
            }
        }
        return false;
    }

    public boolean addShopBlock(Location loc) {
        Location block = loc.getBlock().getLocation();
        if (isShopBlock(block)) return false;
        shopLocations.add(block);
        saveLocations();
        spawnHologram(block);
        return true;
    }

    public boolean removeShopBlock(Location loc) {
        Location block = loc.getBlock().getLocation();
        Location found = null;
        for (Location l : shopLocations) {
            if (l.getWorld().equals(block.getWorld())
                    && l.getBlockX() == block.getBlockX()
                    && l.getBlockY() == block.getBlockY()
                    && l.getBlockZ() == block.getBlockZ()) {
                found = l;
                break;
            }
        }
        if (found == null) return false;
        shopLocations.remove(found);
        removeHologram(locationKey(found));
        saveLocations();
        return true;
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private void loadLocations() {
        shopLocations.clear();
        List<String> raw = getConfig().getStringList("shop-blocks");
        for (String s : raw) {
            Location loc = parseLocation(s);
            if (loc != null) shopLocations.add(loc);
        }
    }

    private void saveLocations() {
        List<String> raw = new ArrayList<>();
        for (Location loc : shopLocations) {
            raw.add(serializeLocation(loc));
        }
        getConfig().set("shop-blocks", raw);
        saveConfig();
    }

    private String serializeLocation(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private Location parseLocation(String s) {
        try {
            String[] parts = s.split(",");
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (Exception e) {
            getLogger().warning("Could not parse shop-block location: " + s);
            return null;
        }
    }

    private String locationKey(Location loc) {
        return serializeLocation(loc);
    }

    // ── Holograms (invisible ArmorStand with custom name) ───────────────────

    private void spawnAllHolograms() {
        for (Location loc : shopLocations) {
            spawnHologram(loc);
        }
    }

    public void spawnHologram(Location blockLoc) {
        String text = getConfig().getString("hologram-text", "");
        if (text.isEmpty()) return;

        String key = locationKey(blockLoc);
        removeHologram(key); // remove old stand first if exists

        // Position: 1.5 blocks above the block center
        Location standLoc = blockLoc.clone().add(0.5, 1.75, 0.5);

        ArmorStand stand = (ArmorStand) standLoc.getWorld().spawnEntity(standLoc, EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        stand.setSmall(true);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setInvulnerable(true);
        stand.setPersistent(true);
        stand.setCustomNameVisible(true);

        // Parse legacy color codes (&b, &l etc.)
        Component name = LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        stand.customName(name);

        hologramStands.put(key, stand.getUniqueId());
    }

    private void removeHologram(String key) {
        UUID uid = hologramStands.remove(key);
        if (uid == null) return;
        Entity e = Bukkit.getEntity(uid);
        if (e != null) e.remove();
    }

    private void removeAllHolograms() {
        for (UUID uid : hologramStands.values()) {
            Entity e = Bukkit.getEntity(uid);
            if (e != null) e.remove();
        }
        hologramStands.clear();
    }

    public static ShardShopBlock getInstance() {
        return instance;
    }
}
