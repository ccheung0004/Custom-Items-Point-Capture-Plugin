package me.crunchycars.speedItem;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public final class myPlugins extends JavaPlugin implements Listener {
    private final NamespacedKey piggyLauncherKey = new NamespacedKey(this, "piggy_launcher");
    private final NamespacedKey usageKey = new NamespacedKey(this, "usage_count");
    private final NamespacedKey tearGasKey = new NamespacedKey(this, "tear_gas");
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final long cooldownTime = 5000;
    private final int maxUses = 3;
    private final NamespacedKey molotovKey = new NamespacedKey(this, "molotov");
    private final List<Region> regions = new ArrayList<>();
    private final Map<UUID, Region> playerRegionMap = new HashMap<>();
    private final Map<UUID, Boolean> playerNotifiedMap = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ooga booga has been enabled!");

        // Sahara
        UUID worldUUID1 = UUID.fromString("ab30c279-7fdd-4ba9-83b8-8cf4644502b6");
        Location location1 = new Location(Bukkit.getWorld(worldUUID1), 115, 124, 442);
        Location location2 = new Location(Bukkit.getWorld(worldUUID1), 486, 120, 419);
        Location location3 = new Location(Bukkit.getWorld(worldUUID1), 292, 115, 315);

        regions.add(new Region(worldUUID1, location1, 10, 90, Material.RED_STAINED_GLASS, this));
        regions.add(new Region(worldUUID1, location2, 10, 90, Material.RED_STAINED_GLASS, this));
        regions.add(new Region(worldUUID1, location3, 10, 90, Material.RED_STAINED_GLASS, this));

        // Caverns
        UUID worldUUID2 = UUID.fromString("a84b8efb-bb68-49f1-9074-6d3d3384f561");
        Location location4 = new Location(Bukkit.getWorld(worldUUID2), 12, 40, 4);
        Location location5 = new Location(Bukkit.getWorld(worldUUID2), 173, 64, 163);
        Location location6 = new Location(Bukkit.getWorld(worldUUID2), 89, 61, -142);

        regions.add(new Region(worldUUID2, location4, 10, 90, Material.RED_STAINED_GLASS, this));
        regions.add(new Region(worldUUID2, location5, 10, 90, Material.RED_STAINED_GLASS, this));
        regions.add(new Region(worldUUID2, location6, 10, 90, Material.RED_STAINED_GLASS, this));

        // Kuroko
        UUID worldUUID3 = UUID.fromString("f6294f42-24f7-4f6c-984f-f37b75c0254e");
        Location location7 = new Location(Bukkit.getWorld(worldUUID3), 122, 4, 37);
        Location location8 = new Location(Bukkit.getWorld(worldUUID3), -123, 4, 118);
        Location location9 = new Location(Bukkit.getWorld(worldUUID3), 76, 4, -104);

        regions.add(new Region(worldUUID3, location7, 10, 90, Material.RED_STAINED_GLASS, this));
        regions.add(new Region(worldUUID3, location8, 10, 90, Material.RED_STAINED_GLASS, this));
        regions.add(new Region(worldUUID3, location9, 10, 90, Material.RED_STAINED_GLASS, this));

        // Test
        UUID worldUUID4 = UUID.fromString("6d8fffa1-5479-4a51-83a8-6d1cc5c58f83");
        Location location10 = new Location(Bukkit.getWorld(worldUUID4), 183, 63, 91);
        regions.add(new Region(worldUUID4, location10, 10, 10, Material.GLASS, this));
    }

    @Override
    public void onDisable() {
        getLogger().info("ooga booga has been disabled!");
    }

    public static class Region {
        private final UUID worldUUID;
        private final Location center;
        private final int radius;
        private final int countdownTime;
        private final Material blockMaterial;
        private final myPlugins plugin;
        private boolean blockBroken = false;
        private final BossBar bossBar;
        private BukkitRunnable countdownTask;
        private int timeLeft;
        private boolean isOpen = false;
        private final Map<UUID, Integer> playerTimers = new HashMap<>();

        public Region(UUID worldUUID, Location center, int radius, int countdownTime, Material blockMaterial, myPlugins plugin) {
            this.center = center;
            this.worldUUID = worldUUID;
            this.radius = radius;
            this.countdownTime = countdownTime;
            this.blockMaterial = blockMaterial;
            this.plugin = plugin;
            this.bossBar = Bukkit.createBossBar("Extraction Countdown", BarColor.RED, BarStyle.SOLID);
            this.timeLeft = countdownTime;
        }

        public boolean isOpen() {
            return isOpen;
        }

        public void setOpen(boolean open) {
            isOpen = open;
        }

        public boolean isPlayerInRegion(Player player) {
            Location loc = player.getLocation();
            return loc.getWorld().getUID().equals(worldUUID) &&
                    Math.abs(loc.getX() - center.getX()) <= radius &&
                    Math.abs(loc.getZ() - center.getZ()) <= radius;
        }

        public int getCountdownTime() {
            return countdownTime;
        }

        public boolean isBlockBroken() {
            return blockBroken;
        }

        public Location getCenter() {
            return center;
        }

        public BossBar getBossBar() {
            return bossBar;
        }

        public void breakBlock() {
            Block centerBlock = center.getBlock();

            if (!blockBroken && centerBlock.getType() != Material.AIR) {
                centerBlock.setType(Material.AIR);
                blockBroken = true;
                launchFireworks(center, Color.GREEN, 5);
                bossBar.setTitle("Extraction Site Open");
                bossBar.setColor(BarColor.GREEN);
                bossBar.setProgress(1.0);
                bossBar.setVisible(true);

                plugin.getLogger().info("Block at " + center.getX() + ", " + center.getY() + ", " + center.getZ() + " set to AIR.");
            }
        }

        public void startCountdown(Player player) {
            if (blockBroken || isOpen) {
                player.sendMessage("§c§l(!) §cThe extraction zone is already open or captured.");
                return;
            }

            if (countdownTask != null) {
                countdownTask.cancel();
            }

            playerTimers.put(player.getUniqueId(), countdownTime);

            bossBar.addPlayer(player);
            bossBar.setVisible(true);
            plugin.getLogger().info("Starting countdown for region at " + center);

            launchFireworks(center, Color.RED, 5);

            countdownTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (timeLeft <= 0) {
                        captureSite();
                        cancel();
                    } else {
                        timeLeft--;
                        UUID playerId = player.getUniqueId();
                        playerTimers.put(playerId, timeLeft);
                        bossBar.setTitle("Extraction Countdown: " + timeLeft + " seconds");
                        bossBar.setProgress(timeLeft / (double) countdownTime);
                    }
                }
            };

            countdownTask.runTaskTimer(plugin, 0, 20);
        }

        public void stopCountdown(UUID playerId) {
            if (playerTimers.containsKey(playerId)) {
                playerTimers.remove(playerId);
                bossBar.removePlayer(Bukkit.getPlayer(playerId));

                if (playerTimers.isEmpty()) {
                    bossBar.setVisible(false);
                    resetCountdown();
                } else {
                    // Find the next player with the lowest time left
                    UUID nextLowestTimerPlayer = getPlayerWithLowestTimer();
                    if (nextLowestTimerPlayer != null) {
                        Player nextPlayer = Bukkit.getPlayer(nextLowestTimerPlayer);
                        if (nextPlayer != null) {
                            int timeLeft = playerTimers.get(nextLowestTimerPlayer);
                            bossBar.setTitle("Extraction Countdown: " + timeLeft + " seconds");
                            bossBar.setProgress(timeLeft / (double) countdownTime);
                            bossBar.addPlayer(nextPlayer);
                        }
                    }
                }
            }
        }

        public UUID getPlayerWithLowestTimer() {
            return playerTimers.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }

        public void captureSite() {
            breakBlock();
            isOpen = true;
            bossBar.setTitle("Extraction Site Open");
            bossBar.setColor(BarColor.GREEN);
            bossBar.setProgress(1.0);
            plugin.getLogger().info("Extraction site at " + center + " captured!");

            new BukkitRunnable() {
                @Override
                public void run() {
                    resetSite();
                }
            }.runTaskLater(plugin, 600L);
        }

        public void resetSite() {
            Block blockToPlace = center.getBlock();
            blockToPlace.setType(blockMaterial);
            blockBroken = false;
            isOpen = false;
            timeLeft = countdownTime;
            bossBar.setTitle("Extraction Countdown: " + countdownTime + " seconds");
            bossBar.setColor(BarColor.RED);
            bossBar.setProgress(1.0);
            bossBar.setVisible(false);
            plugin.getLogger().info("Extraction site at " + center + " has been reset.");

            for (UUID playerId : plugin.playerRegionMap.keySet()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && isPlayerInRegion(player)) {
                    startCountdown(player);
                    player.sendMessage("§c§l(!) §cExtraction zone closed. Timer has been reset.");
                }
            }
        }

        public void resetCountdown() {
            if (countdownTask != null) {
                countdownTask.cancel();
            }
            timeLeft = countdownTime;
            plugin.getLogger().info("Countdown reset for region at " + center);
        }

        public void launchFireworks(Location location, Color color, int count) {
            for (int i = 0; i < count; i++) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Location fireworkLocation = location.clone().add(0.5, 1, 0.5);
                        Firework firework = fireworkLocation.getWorld().spawn(fireworkLocation, Firework.class);
                        FireworkMeta meta = firework.getFireworkMeta();

                        for (int j = 0; j < 5; j++) {
                            meta.addEffect(FireworkEffect.builder()
                                    .withColor(color)
                                    .with(FireworkEffect.Type.BALL)
                                    .withFlicker()
                                    .withTrail()
                                    .build());
                        }

                        meta.setPower(2);
                        firework.setFireworkMeta(meta);
                    }
                }.runTaskLater(plugin, i * 10L);
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        boolean playerInAnyRegion = false;
        Region currentRegion = playerRegionMap.get(playerId);

        for (Region region : regions) {
            if (region.isPlayerInRegion(player)) {
                playerInAnyRegion = true;

                if (currentRegion == null || currentRegion != region) {
                    playerRegionMap.put(playerId, region);

                    if (region.isOpen()) {
                        // If the region is already captured/open, just show the boss bar
                        BossBar bossBar = region.getBossBar();
                        bossBar.addPlayer(player);
                        bossBar.setTitle("Extraction Site Open");
                        bossBar.setColor(BarColor.GREEN);
                        bossBar.setProgress(1.0);
                        bossBar.setVisible(true);

                        if (!playerNotifiedMap.getOrDefault(playerId, false)) {
                            player.sendMessage("§a§l(!) §aExtraction zone is already open.");
                            playerNotifiedMap.put(playerId, true);
                        }
                    } else {
                        // If the region is not yet captured, start the countdown
                        playerNotifiedMap.remove(playerId);
                        region.startCountdown(player);
                        player.sendMessage("§c§l(!) §cCapturing extraction zone. Stay in the zone to open it.");
                    }
                }
                break;
            }
        }

        if (!playerInAnyRegion && currentRegion != null) {
            currentRegion.stopCountdown(playerId);
            playerRegionMap.remove(playerId);
            playerNotifiedMap.remove(playerId);

            BossBar bossBar = currentRegion.getBossBar();
            bossBar.removePlayer(player);

            if (bossBar.getPlayers().isEmpty()) {
                bossBar.setVisible(false);
            }

            player.sendMessage("§c§l(!) §cYou left the extraction zone. Countdown stopped.");
        }
    }

















////////////////////////////////////////////////////////////////////////////////////////////////////
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        ItemStack item = player.getInventory().getItemInMainHand();

        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) && item != null && item.getType() == Material.SUGAR_CANE) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName() && meta.getDisplayName().equals("§7§lTear §7Gas")) {
                event.setCancelled(true);

                Item thrownItem = player.getWorld().dropItem(player.getEyeLocation(), item);
                thrownItem.setVelocity(player.getLocation().getDirection().multiply(1.5));
                thrownItem.getPersistentDataContainer().set(tearGasKey, PersistentDataType.BYTE, (byte) 1);

                item.setAmount(1);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!thrownItem.isDead()) {
                            triggerTearGasEffect(thrownItem);
                            thrownItem.remove();
                        }
                    }
                }.runTaskLater(this, 20L);
            }
        }

        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) && item != null && item.getType() == Material.SUGAR) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName() && meta.getDisplayName().equals("§a§lBooger §aSugar")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 50, 1));

                item.setAmount(item.getAmount() - 1);

                if (item.getAmount() <= 0) {
                    player.getInventory().setItemInMainHand(null);
                }
                player.sendMessage("§a§l(!) §aYou feel a surge of adrenaline");

                event.setCancelled(true);
            }
        }

        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) && item != null && item.getType() == Material.REDSTONE) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName() && meta.getDisplayName().equals("§c§lDamage §cAmplifier")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 300, 0));

                item.setAmount(item.getAmount() - 1);

                if (item.getAmount() <= 0) {
                    player.getInventory().setItemInMainHand(null);
                }

                player.sendMessage("§a§l(!) §aYou feel a surge of strength!");

                event.setCancelled(true);
            }
        }

        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) && item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.getPersistentDataContainer().has(piggyLauncherKey, PersistentDataType.BYTE)) {
                UUID playerId = player.getUniqueId();

                if (cooldowns.containsKey(playerId)) {
                    long timeSinceLastUse = System.currentTimeMillis() - cooldowns.get(playerId);
                    if (timeSinceLastUse < cooldownTime) {
                        long timeLeft = (cooldownTime - timeSinceLastUse) / 1000;
                        player.sendMessage("§c§l(!) §cYou must wait " + timeLeft + " more seconds before using the Piggy Launcher again!");
                        event.setCancelled(true);
                        return;
                    }
                }

                int usageCount = meta.getPersistentDataContainer().getOrDefault(usageKey, PersistentDataType.INTEGER, 0);
                usageCount++;

                if (usageCount >= maxUses) {
                    player.getInventory().setItemInMainHand(null);
                    player.sendMessage("§a§l(!) §aYour Piggy Launcher broke!");
                } else {
                    meta.getPersistentDataContainer().set(usageKey, PersistentDataType.INTEGER, usageCount);
                    item.setItemMeta(meta);
                }

                event.setCancelled(true);
                Pig pig = (Pig) player.getWorld().spawnEntity(player.getLocation().add(0, 1.5, 0), EntityType.PIG);
                pig.setVelocity(player.getLocation().getDirection().multiply(2));
                pig.getPersistentDataContainer().set(piggyLauncherKey, PersistentDataType.BYTE, (byte) 1);

                cooldowns.put(playerId, System.currentTimeMillis());

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (pig.isOnGround() || pig.getVelocity().length() < 0.1) {
                            pig.getWorld().createExplosion(pig.getLocation(), 4.0F, false, false);
                            pig.remove();
                            this.cancel();
                        }
                    }
                }.runTaskTimer(this, 0L, 1L);
            }
        }

        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) && item != null && item.getType() == Material.REDSTONE_TORCH) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName() && meta.getDisplayName().equals("§c§lMolotov")) {
                event.setCancelled(true);

                Item thrownItem = player.getWorld().dropItem(player.getEyeLocation(), item);
                thrownItem.setVelocity(player.getLocation().getDirection().multiply(1.5));
                thrownItem.getPersistentDataContainer().set(molotovKey, PersistentDataType.BYTE, (byte) 1);

                thrownItem.setPickupDelay(Integer.MAX_VALUE);

                item.setAmount(1);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!thrownItem.isDead()) {
                            igniteArea(thrownItem.getLocation(), 2);
                            thrownItem.remove();
                        }
                    }
                }.runTaskLater(this, 20L);
            }
        }
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("givepiggylauncher")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                ItemStack piggyLauncher = new ItemStack(Material.BOW, 1);
                ItemMeta meta = piggyLauncher.getItemMeta();
                meta.setDisplayName("§d§lPiggy §dLauncher");
                meta.getPersistentDataContainer().set(piggyLauncherKey, PersistentDataType.BYTE, (byte) 1);
                piggyLauncher.setItemMeta(meta);

                player.getInventory().addItem(piggyLauncher);
                player.sendMessage("You have been given a Piggy Launcher!");
                return true;
            } else {
                sender.sendMessage("This command can only be run by a player.");
                return false;
            }
        }

        if (command.getName().equalsIgnoreCase("giveboogersugar")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                if (!player.isOp()) {
                    player.sendMessage("You do not have permission to use this command.");
                    return true;
                }

                ItemStack boogerSugar = new ItemStack(Material.SUGAR, 1);
                ItemMeta meta = boogerSugar.getItemMeta();
                meta.setDisplayName("§a§lBooger §aSugar");
                boogerSugar.setItemMeta(meta);

                player.getInventory().addItem(boogerSugar);
                player.sendMessage("You have been given Booger Sugar!");
                return true;
            } else {
                sender.sendMessage("This command can only be run by a player.");
                return false;
            }
        }

        if (command.getName().equalsIgnoreCase("givedamageamplifier")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                ItemStack damageAmplifier = new ItemStack(Material.REDSTONE, 1);
                ItemMeta meta = damageAmplifier.getItemMeta();
                meta.setDisplayName("§c§lDamage §cAmplifier");
                damageAmplifier.setItemMeta(meta);

                player.getInventory().addItem(damageAmplifier);
                player.sendMessage("You have been given a Damage Amplifier!");
                return true;
            } else {
                sender.sendMessage("This command can only be run by a player.");
                return false;
            }
        }

        if (command.getName().equalsIgnoreCase("giveteargas")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                ItemStack tearGas = new ItemStack(Material.SUGAR_CANE, 1);
                ItemMeta meta = tearGas.getItemMeta();
                meta.setDisplayName("§7§lTear §7Gas");
                tearGas.setItemMeta(meta);

                player.getInventory().addItem(tearGas);
                player.sendMessage("You have been given Tear Gas!");
                return true;
            } else {
                sender.sendMessage("This command can only be run by a player.");
                return false;
            }
        }

        if (command.getName().equalsIgnoreCase("givemolotov")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                ItemStack molotov = new ItemStack(Material.REDSTONE_TORCH, 1);
                ItemMeta meta = molotov.getItemMeta();
                meta.setDisplayName("§c§lMolotov");
                molotov.setItemMeta(meta);

                player.getInventory().addItem(molotov);
                player.sendMessage("You have been given a Molotov!");
                return true;
            } else {
                sender.sendMessage("This command can only be run by a player.");
                return false;
            }
        }

        return false;
    }

    private void triggerTearGasEffect(Item item) {
        item.getWorld().createExplosion(item.getLocation(), 0F, false, false);

        List<Player> players = item.getWorld().getPlayers();
        for (Player player : players) {
            if (player.getLocation().distance(item.getLocation()) <= 5) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 200, 5));
                player.sendMessage("§c§l(!) §cYou've been hit by Tear Gas!");
            }
        }
    }

    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Item item = event.getItem();
        ItemMeta meta = item.getItemStack().getItemMeta();

        if (meta != null && meta.hasDisplayName() && meta.getDisplayName().equals("§7§lTear §7Gas")) {
            event.setCancelled(true);
            item.remove();
        }
    }

    private void igniteArea(Location location, int radius) {
        World world = location.getWorld();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Location fireLocation = location.clone().add(x, 0, z);
                if (world.getBlockAt(fireLocation).getType() == Material.AIR) {
                    world.getBlockAt(fireLocation).setType(Material.FIRE);
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        Location fireLocation = location.clone().add(x, 0, z);
                        if (world.getBlockAt(fireLocation).getType() == Material.FIRE) {
                            world.getBlockAt(fireLocation).setType(Material.AIR);
                        }
                    }
                }
            }
        }.runTaskLater(this, 200L);
    }
}
