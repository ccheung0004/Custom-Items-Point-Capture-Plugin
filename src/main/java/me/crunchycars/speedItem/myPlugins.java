package me.crunchycars.speedItem;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
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
    private final NamespacedKey usageKey = new NamespacedKey(this, "usage_count"); // Correct declaration of usageKey
    private final NamespacedKey tearGasKey = new NamespacedKey(this, "tear_gas");
    private final Map<UUID, Long> cooldowns = new HashMap<>(); // Map to store player cooldowns
    private final long cooldownTime = 5000; // Cooldown time in milliseconds (e.g., 5000ms = 5 seconds)
    private final int maxUses = 3; // Maximum number of uses before the bow breaks
    private final NamespacedKey molotovKey = new NamespacedKey(this, "molotov");


    private final List<Region> regions = new ArrayList<>(); // List to store multiple regions
    private final Map<UUID, BukkitRunnable> activeCountdowns = new HashMap<>(); // To track active countdowns for players
    private final Map<UUID, Region> playerRegionMap = new HashMap<>(); // To track the region each player is in
    private final Map<UUID, Boolean> playerNotifiedMap = new HashMap<>(); // To track if the player has been notified about a broken block


    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ooga booga has been enabled!");
        regions.add(new Region(new Location(Bukkit.getWorld("icycaverns"), 12, 40, 4), 10, 10, Material.GLASS, this));
        regions.add(new Region(new Location(Bukkit.getWorld("icycaverns"), 89, 61, -142), 10, 10, Material.GLASS, this));
        regions.add(new Region(new Location(Bukkit.getWorld("icycaverns"), 173, 64, 163), 10, 10, Material.GLASS, this));
        regions.add(new Region(new Location(Bukkit.getWorld("sahara"), 115, 124, 442), 10, 10, Material.GLASS, this));
        regions.add(new Region(new Location(Bukkit.getWorld("sahara"), 292, 114, 315), 10, 10, Material.GLASS, this));
        regions.add(new Region(new Location(Bukkit.getWorld("sahara"), 486, 120, 419), 10, 10, Material.GLASS, this));
        regions.add(new Region(new Location(Bukkit.getWorld("kuroko"), -123, 4, 118), 10, 10, Material.GLASS, this));
        regions.add(new Region(new Location(Bukkit.getWorld("kuroko"), 76, 4, -104), 10, 10, Material.GLASS, this));
        regions.add(new Region(new Location(Bukkit.getWorld("kuroko"), 122, 4, 37), 10, 10, Material.GLASS, this));

        // Add more regions as needed
    }

    @Override
    public void onDisable() {
        getLogger().info("ooga booga has been disabled!");
    }

    //////////////CAP POINT PLUGIN//////////////

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        boolean playerInAnyRegion = false;

        for (Region region : regions) {
            if (region.isPlayerInRegion(player)) {
                playerInAnyRegion = true;

                // If the player is entering the region for the first time, start the countdown and send a message
                if (!playerRegionMap.containsKey(playerId) || playerRegionMap.get(playerId) != region) {
                    if (!region.isBlockBroken()) { // Only start countdown if the block is not broken
                        playerRegionMap.put(playerId, region);
                        playerNotifiedMap.remove(playerId); // Reset notification status
                        startCountdown(player, region);
                        player.sendMessage("You have entered a region. Stay here for 2 minutes!");
                    } else {
                        if (!playerNotifiedMap.getOrDefault(playerId, false)) { // Check if the player has already been notified
                            player.sendMessage("The block is already broken. Wait for it to restore.");
                            playerNotifiedMap.put(playerId, true); // Mark the player as notified
                        }
                    }
                }
                break; // No need to check further once the player is in a region
            }
        }

        // If the player is no longer in any region, remove them from the maps and stop the countdown
        if (!playerInAnyRegion && playerRegionMap.containsKey(playerId)) {
            stopCountdown(playerId);
            playerRegionMap.remove(playerId);
            playerNotifiedMap.remove(playerId);
            player.sendMessage("You left the region. The countdown has been stopped.");
        }
    }


    private void startCountdown(Player player, Region region) {
        UUID playerId = player.getUniqueId();

        // Launch a red firework to indicate the countdown start
        region.launchFireworks(region.getCenter(), Color.RED, 3); // Launch 5 fireworks

        BukkitRunnable countdown = new BukkitRunnable() {
            int timeLeft = region.getCountdownTime();

            @Override
            public void run() {
                if (timeLeft <= 0) {
                    region.breakAndPlaceBlock();
                    player.sendMessage("You have successfully stayed within the region for 2 minutes. The block has been broken and will be placed back in 30 seconds!");
                    stopCountdown(playerId);

                    // Launch a green firework to indicate the site is open
                    region.launchFireworks(region.getCenter(), Color.GREEN, 5); // Launch 5 fireworks
                } else if (!region.isPlayerInRegion(player)) {
                    player.sendMessage("You left the region. The countdown has been stopped.");
                    stopCountdown(playerId);
                } else {
                    timeLeft--;
                }
            }
        };

        countdown.runTaskTimer(this, 0, 20); // Schedule task to run every second (20 ticks)
        activeCountdowns.put(playerId, countdown);
    }



    private void stopCountdown(UUID playerId) {
        if (activeCountdowns.containsKey(playerId)) {
            activeCountdowns.get(playerId).cancel();
            activeCountdowns.remove(playerId);
        }
    }

    private static class Region {
        private final Location center;
        private final int radius;
        private final int countdownTime;
        private final Material blockMaterial;
        private final JavaPlugin plugin;
        private boolean blockBroken = false;

        public Region(Location center, int radius, int countdownTime, Material blockMaterial, JavaPlugin plugin) {
            this.center = center;
            this.radius = radius;
            this.countdownTime = countdownTime;
            this.blockMaterial = blockMaterial;
            this.plugin = plugin;
        }

        public boolean isPlayerInRegion(Player player) {
            Location loc = player.getLocation();
            return Math.abs(loc.getX() - center.getX()) <= radius && Math.abs(loc.getZ() - center.getZ()) <= radius;
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

        public void breakAndPlaceBlock() {
            Block centerBlock = center.getBlock();

            // Break the block
            centerBlock.setType(Material.AIR);
            blockBroken = true; // Mark the block as broken

            // Debugging output to ensure this code is running
            plugin.getLogger().info("Block at " + center.getX() + ", " + center.getY() + ", " + center.getZ() + " set to AIR.");

            // Schedule to place the block back after 30 seconds
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Re-fetch the block at the location to ensure we are working with the correct reference
                    Block blockToPlace = center.getBlock();
                    blockToPlace.setType(blockMaterial); // Place the block back with the specified material
                    blockBroken = false; // Mark the block as restored

                    // Debugging output to confirm block placement
                    plugin.getLogger().info("Block at " + center.getX() + ", " + center.getY() + ", " + center.getZ() + " set to " + blockMaterial.name() + ".");
                }
            }.runTaskLater(plugin, 600L); // 600 ticks = 30 seconds
        }
        public void launchFireworks(Location location, Color color, int count) {
            for (int i = 0; i < count; i++) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Location fireworkLocation = location.clone().add(0.5, 1, 0.5);
                        Firework firework = fireworkLocation.getWorld().spawn(fireworkLocation, Firework.class);
                        FireworkMeta meta = firework.getFireworkMeta();

                        // Add multiple effects to the firework
                        for (int j = 0; j < 5; j++) {
                            meta.addEffect(FireworkEffect.builder()
                                    .withColor(color)
                                    .with(FireworkEffect.Type.BALL)
                                    .withFlicker()
                                    .withTrail()
                                    .build());
                        }

                        meta.setPower(2); // Increase the power to make the firework shoot higher
                        firework.setFireworkMeta(meta);
                    }
                }.runTaskLater(plugin, i * 10L); // Delay each firework by 10 ticks
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
            if (meta != null && meta.hasDisplayName() && meta.getDisplayName().equals("§7Tear Gas")) {
                event.setCancelled(true); // Cancel any default behavior

                // Launch the Tear Gas (throw it)
                Item thrownItem = player.getWorld().dropItem(player.getEyeLocation(), item);
                thrownItem.setVelocity(player.getLocation().getDirection().multiply(1.5));
                thrownItem.getPersistentDataContainer().set(tearGasKey, PersistentDataType.BYTE, (byte) 1);

                // Remove one Tear Gas from the player's hand
                item.setAmount(item.getAmount() - 1);
                if (item.getAmount() <= 0) {
                    player.getInventory().setItemInMainHand(null);
                }

                // Prevent the item from being picked up by any player
                thrownItem.setPickupDelay(Integer.MAX_VALUE);

                // Schedule to trigger the effect after a short delay
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!thrownItem.isDead()) {
                            triggerTearGasEffect(thrownItem);
                            thrownItem.remove(); // Remove the item entity after the effect
                        }
                    }
                }.runTaskLater(this, 20L); // 20 ticks = 1 second
            }
        }

        // Check if the player right-clicked (either on block or air)
        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) && item != null && item.getType() == Material.SUGAR) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName() && meta.getDisplayName().equals("§a§lBooger Sugar")) {
                // Apply Speed II effect for 5 seconds (100 ticks)
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 50, 1));

                // Remove one Booger Sugar from the player's hand
                item.setAmount(item.getAmount() - 1);

                // If the item stack is now empty, remove it from the inventory
                if (item.getAmount() <= 0) {
                    player.getInventory().setItemInMainHand(null);
                }
                player.sendMessage("§aYou feel a surge of adrenaline");


                // Cancel the event to prevent any default action (like placing a block)
                event.setCancelled(true);
            }
        }

        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) && item != null && item.getType() == Material.REDSTONE) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName() && meta.getDisplayName().equals("§c§lDamage Amplifier")) {

                // Apply Strength I effect for 15 seconds (300 ticks)
                player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 300, 0));

                // Remove one Damage Amplifier from the player's hand
                item.setAmount(item.getAmount() - 1);

                // If the item stack is now empty, remove it from the inventory
                if (item.getAmount() <= 0) {
                    player.getInventory().setItemInMainHand(null);
                }

                player.sendMessage("You feel a surge of strength!");

                // Cancel the event to prevent any default action
                event.setCancelled(true);
            }
        }

        // Check if the player right-clicked and is holding the Piggy Launcher
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) && item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.getPersistentDataContainer().has(piggyLauncherKey, PersistentDataType.BYTE)) {
                UUID playerId = player.getUniqueId();

                // Check if the player is on cooldown
                if (cooldowns.containsKey(playerId)) {
                    long timeSinceLastUse = System.currentTimeMillis() - cooldowns.get(playerId);
                    if (timeSinceLastUse < cooldownTime) {
                        long timeLeft = (cooldownTime - timeSinceLastUse) / 1000;
                        player.sendMessage("You must wait " + timeLeft + " more seconds before using the Piggy Launcher again!");
                        event.setCancelled(true);
                        return;
                    }
                }

                // Handle usage count
                int usageCount = meta.getPersistentDataContainer().getOrDefault(usageKey, PersistentDataType.INTEGER, 0);
                usageCount++;

                if (usageCount >= maxUses) {
                    // Break the bow after max uses
                    player.getInventory().setItemInMainHand(null);
                    player.sendMessage("Your Piggy Launcher broke!");
                } else {
                    // Update the usage count
                    meta.getPersistentDataContainer().set(usageKey, PersistentDataType.INTEGER, usageCount);
                    item.setItemMeta(meta);
                }

                // Launch the pig immediately
                event.setCancelled(true); // Cancel default bow usage
                Pig pig = (Pig) player.getWorld().spawnEntity(player.getLocation().add(0, 1.5, 0), EntityType.PIG);
                pig.setVelocity(player.getLocation().getDirection().multiply(2));
                pig.getPersistentDataContainer().set(piggyLauncherKey, PersistentDataType.BYTE, (byte) 1);
                player.sendMessage("Pig launched!");

                // Apply the cooldown
                cooldowns.put(playerId, System.currentTimeMillis());

                // Schedule a task to check for collision with the environment
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (pig.isOnGround() || pig.getVelocity().length() < 0.1) {
                            pig.getWorld().createExplosion(pig.getLocation(), 4.0F, false, false);
                            pig.remove();
                            getLogger().info("Pig exploded!");
                            this.cancel();  // Stop the task
                        }
                    }
                }.runTaskTimer(this, 0L, 1L);  // Run every tick
            }
        }
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) && item != null && item.getType() == Material.REDSTONE_TORCH) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName() && meta.getDisplayName().equals("§cMolotov")) {
                event.setCancelled(true); // Cancel any default behavior

                // Launch the Molotov (throw it)
                Item thrownItem = player.getWorld().dropItem(player.getEyeLocation(), item);
                thrownItem.setVelocity(player.getLocation().getDirection().multiply(1.5));
                thrownItem.getPersistentDataContainer().set(molotovKey, PersistentDataType.BYTE, (byte) 1);

                thrownItem.setPickupDelay(Integer.MAX_VALUE);

                // Remove one Molotov from the player's hand
                item.setAmount(item.getAmount() - 1);
                if (item.getAmount() <= 0) {
                    player.getInventory().setItemInMainHand(null);
                }

                // Schedule to trigger the fire effect after a short delay
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!thrownItem.isDead()) {
                            igniteArea(thrownItem.getLocation(), 2); // 4-block radius
                            thrownItem.remove(); // Remove the item entity after the effect
                        }
                    }
                }.runTaskLater(this, 20L); // 20 ticks = 1 second
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
                meta.setDisplayName("§d§lPiggy Launcher");
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
                meta.setDisplayName("§a§lBooger Sugar");
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
                meta.setDisplayName("§c§lDamage Amplifier");
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
                meta.setDisplayName("§7Tear Gas");
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
                meta.setDisplayName("§cMolotov");
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
        // Create a harmless explosion at the item's location
        item.getWorld().createExplosion(item.getLocation(), 0F, false, false);

        // Apply nausea effect to nearby players
        List<Player> players = item.getWorld().getPlayers();
        for (Player player : players) {
            if (player.getLocation().distance(item.getLocation()) <= 5) { // Radius of 5 blocks
                player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 200, 5)); // Nausea for 3 seconds (60 ticks)
                player.sendMessage("You've been hit by Tear Gas!");
            }
        }
    }
    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Item item = event.getItem();
        ItemMeta meta = item.getItemStack().getItemMeta();

        // Prevent pickup of Tear Gas item
        if (meta != null && meta.hasDisplayName() && meta.getDisplayName().equals("§7Tear Gas")) {
            event.setCancelled(true);
            item.remove(); // Remove the item immediately if somehow picked up
        }
    }

    private void igniteArea(Location location, int radius) {
        World world = location.getWorld();

        // Ignite the blocks in a square around the location
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Location fireLocation = location.clone().add(x, 0, z);
                if (world.getBlockAt(fireLocation).getType() == Material.AIR) {
                    world.getBlockAt(fireLocation).setType(Material.FIRE);
                }
            }
        }
        // Schedule to extinguish the fire after 10 seconds
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
        }.runTaskLater(this, 200L); // 200 ticks = 10 seconds




    }
}





