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
    private final Map<UUID, Integer> playerTimers = new HashMap<>();
    private final List<Region> regions = new ArrayList<>();
    private final Map<UUID, BukkitRunnable> activeCountdowns = new HashMap<>();
    private final Map<UUID, Region> playerRegionMap = new HashMap<>();
    private final Map<UUID, Boolean> playerNotifiedMap = new HashMap<>();
    private boolean isSiteOpen = false;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ooga booga has been enabled!");

        UUID worldUUID1 = UUID.fromString("ab30c279-7fdd-4ba9-83b8-8cf4644502b6");
        Location location1 = new Location(Bukkit.getWorld(worldUUID1), 115, 124, 442);
        regions.add(new Region(worldUUID1, location1, 10, 10, Material.GLASS, this));

        UUID worldUUID2 = UUID.fromString("a84b8efb-bb68-49f1-9074-6d3d3384f561");
        Location location2 = new Location(Bukkit.getWorld(worldUUID2), 12, 40, 4);
        regions.add(new Region(worldUUID2, location2, 10, 10, Material.GLASS, this));

        UUID worldUUID3 = UUID.fromString("f6294f42-24f7-4f6c-984f-f37b75c0254e");
        Location location3 = new Location(Bukkit.getWorld(worldUUID3), -123, 4, 118);
        regions.add(new Region(worldUUID3, location3, 10, 10, Material.GLASS, this));

        //test
        UUID worldUUID4 = UUID.fromString("6d8fffa1-5479-4a51-83a8-6d1cc5c58f83");
        Location location4 = new Location(Bukkit.getWorld(worldUUID4), 183, 63, 91);
        regions.add(new Region(worldUUID4, location4, 10, 10, Material.GLASS, this));

    }

    @Override
    public void onDisable() {
        getLogger().info("ooga booga has been disabled!");
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
                    if (!region.isBlockBroken()) {
                        playerRegionMap.put(playerId, region);
                        playerNotifiedMap.remove(playerId);
                        startCountdown(player, region);
                        player.sendMessage("§c§l(!) §cCapturing extraction zone. Stay in the zone for 1.5 minutes to open it.");
                    } else {
                        if (!playerNotifiedMap.getOrDefault(playerId, false)) {
                            player.sendMessage("§c§l(!) §cExtraction zone is already open.");
                            playerNotifiedMap.put(playerId, true);
                        }
                    }
                }
                break;
            }
        }

        if (!playerInAnyRegion && currentRegion != null) {
            stopCountdown(playerId);
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

    private void startCountdown(Player player, Region region) {
        UUID playerId = player.getUniqueId();

        if (isSiteOpen) {
            player.sendMessage("§c§l(!) §cThe extraction zone is already open.");
            return;
        }

        if (region.isBlockBroken()) {
            player.sendMessage("§c§l(!) §cThe extraction zone is already open.");
            return;
        }

        playerTimers.put(playerId, region.getCountdownTime());
        BossBar bossBar = region.getBossBar();
        bossBar.addPlayer(player);
        bossBar.setVisible(true);
        region.launchFireworks(region.getCenter(), Color.RED, 5);

        BukkitRunnable countdown = new BukkitRunnable() {
            @Override
            public void run() {
                if (isSiteOpen) {
                    bossBar.setTitle("Extraction Site Open");
                    bossBar.setColor(BarColor.GREEN);
                    bossBar.setProgress(1.0);
                    return;
                }

                int timeLeft = playerTimers.get(playerId) - 1;
                playerTimers.put(playerId, timeLeft);

                UUID lowestTimerPlayer = getPlayerWithLowestTimer();
                if (lowestTimerPlayer != null && lowestTimerPlayer.equals(playerId)) {
                    bossBar.setTitle("Extraction Countdown: " + timeLeft + " seconds");
                    bossBar.setProgress(timeLeft / (double) region.getCountdownTime());
                }

                if (timeLeft <= 0) {
                    isSiteOpen = true;
                    region.breakBlock();

                    for (UUID uuid : playerRegionMap.keySet()) {
                        if (playerRegionMap.get(uuid).equals(region)) {
                            Player regionPlayer = Bukkit.getPlayer(uuid);
                            if (regionPlayer != null) {
                                regionPlayer.sendMessage("§a§l(!) §aThe extraction site is now open!");
                            }
                        }
                    }

                    bossBar.setTitle("Extraction Site Open");
                    bossBar.setColor(BarColor.GREEN);
                    bossBar.setProgress(1.0);

                    // No need to call another delay here as it’s handled in placeBlockBack
                    region.placeBlockBack();

                    this.cancel();
                }
            }
        };

        countdown.runTaskTimer(this, 0, 20);
        activeCountdowns.put(playerId, countdown);
    }


    private void updateBossBarsForAllPlayers(Region region) {
        UUID lowestTimerPlayer = getPlayerWithLowestTimer();
        BossBar bossBar = region.getBossBar();

        if (lowestTimerPlayer != null) {
            Player lowestTimerPlayerEntity = Bukkit.getPlayer(lowestTimerPlayer);
            if (lowestTimerPlayerEntity != null) {
                bossBar.setTitle("Extraction Countdown: " + playerTimers.get(lowestTimerPlayer) + " seconds");
                bossBar.setProgress(playerTimers.get(lowestTimerPlayer) / (double) region.getCountdownTime());

                for (UUID uuid : playerRegionMap.keySet()) {
                    if (playerRegionMap.get(uuid).equals(region)) {
                        Player regionPlayer = Bukkit.getPlayer(uuid);
                        if (regionPlayer != null) {
                            bossBar.addPlayer(regionPlayer);
                        }
                    }
                }
                bossBar.setVisible(true);
            }
        } else {
            bossBar.setVisible(false);
        }
    }

    private UUID getPlayerWithLowestTimer() {
        return playerTimers.entrySet()
                .stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private void stopCountdown(UUID playerId) {
        if (activeCountdowns.containsKey(playerId)) {
            activeCountdowns.get(playerId).cancel();
            activeCountdowns.remove(playerId);
        }

        Region region = playerRegionMap.get(playerId);
        if (region != null) {
            BossBar bossBar = region.getBossBar();
            bossBar.removePlayer(Bukkit.getPlayer(playerId));

            if (isSiteOpen) {
                bossBar.setTitle("Extraction Site Open");
                bossBar.setColor(BarColor.GREEN);
                bossBar.setProgress(1.0);
                bossBar.setVisible(true);
            } else {
                UUID lowestTimerPlayer = getPlayerWithLowestTimer();
                if (lowestTimerPlayer != null) {
                    Player lowestTimerPlayerEntity = Bukkit.getPlayer(lowestTimerPlayer);
                    if (lowestTimerPlayerEntity != null) {
                        bossBar.addPlayer(lowestTimerPlayerEntity);
                        bossBar.setTitle("Extraction Countdown: " + playerTimers.get(lowestTimerPlayer) + " seconds");
                        bossBar.setProgress(playerTimers.get(lowestTimerPlayer) / (double) region.getCountdownTime());
                    }
                } else {
                    bossBar.setVisible(false);
                }
            }
        }
    }

    public void resetExtractionSite() {
        isSiteOpen = false;
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

        public Region(UUID worldUUID, Location center, int radius, int countdownTime, Material blockMaterial, myPlugins plugin) {
            this.center = center;
            this.worldUUID = worldUUID;
            this.radius = radius;
            this.countdownTime = countdownTime;
            this.blockMaterial = blockMaterial;
            this.plugin = plugin;
            this.bossBar = Bukkit.createBossBar("Extraction Countdown", BarColor.RED, BarStyle.SOLID);
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

                plugin.getLogger().info("Block at " + center.getX() + ", " + center.getY() + ", " + center.getZ() + " set to AIR.");
            }
        }

        public void placeBlockBack() {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Block blockToPlace = center.getBlock();
                    blockToPlace.setType(blockMaterial);
                    blockBroken = false;

                    plugin.resetExtractionSite();
                    updateBossBarsForAllPlayers();

                    // Check if any players are in the region when the block is placed back
                    for (UUID playerId : plugin.playerRegionMap.keySet()) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null && isPlayerInRegion(player)) {
                            // Restart the countdown for the player in the region
                            plugin.startCountdown(player, Region.this);
                            player.sendMessage("§c§l(!) §cExtraction zone closed. Timer has been reset.");

                        }
                    }

                    plugin.getLogger().info("Block at " + center.getX() + ", " + center.getY() + ", " + center.getZ() + " set to " + blockMaterial.name() + ".");
                }
            }.runTaskLater(plugin, 600L);
        }




        private void updateBossBarsForAllPlayers() {
            UUID lowestTimerPlayer = plugin.getPlayerWithLowestTimer();
            if (lowestTimerPlayer != null) {
                Player lowestTimerPlayerEntity = Bukkit.getPlayer(lowestTimerPlayer);
                if (lowestTimerPlayerEntity != null) {
                    bossBar.setTitle("Extraction Countdown: " + plugin.playerTimers.get(lowestTimerPlayer) + " seconds");
                    bossBar.setProgress(plugin.playerTimers.get(lowestTimerPlayer) / (double) countdownTime);
                    bossBar.setVisible(true);
                }
            } else {
                bossBar.setVisible(false);
            }
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
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        ItemStack item = player.getInventory().getItemInMainHand();

        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) && item != null && item.getType() == Material.SUGAR_CANE) {
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

        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) && item != null && item.getType() == Material.REDSTONE) {
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

        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) && item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.getPersistentDataContainer().has(piggyLauncherKey, PersistentDataType.BYTE)) {
                UUID playerId = player.getUniqueId();

                if (cooldowns.containsKey(playerId)) {
                    long timeSinceLastUse = System.currentTimeMillis() - cooldowns.get(playerId);
                    if (timeSinceLastUse < cooldownTime) {
                        long timeLeft = (cooldownTime - timeSinceLastUse) / 1000;
                        player.sendMessage("§c§l(!)§cYou must wait " + timeLeft + " §cmore seconds before using the Piggy Launcher again!");
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
