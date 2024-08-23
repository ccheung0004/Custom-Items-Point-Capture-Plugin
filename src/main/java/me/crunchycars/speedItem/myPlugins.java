package me.crunchycars.speedItem;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.block.Action;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pig;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class myPlugins extends JavaPlugin implements Listener {
    private final NamespacedKey piggyLauncherKey = new NamespacedKey(this, "piggy_launcher");
    private final NamespacedKey usageKey = new NamespacedKey(this, "usage_count"); // Correct declaration of usageKey
    private final NamespacedKey tearGasKey = new NamespacedKey(this, "tear_gas");
    private final Map<UUID, Long> cooldowns = new HashMap<>(); // Map to store player cooldowns
    private final long cooldownTime = 5000; // Cooldown time in milliseconds (e.g., 5000ms = 5 seconds)
    private final int maxUses = 3; // Maximum number of uses before the bow breaks
    private final NamespacedKey molotovKey = new NamespacedKey(this, "molotov");


    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ooga booga has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ooga booga has been disabled!");
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





