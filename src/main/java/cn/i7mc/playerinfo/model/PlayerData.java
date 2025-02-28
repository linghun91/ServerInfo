package cn.i7mc.playerinfo.model;

import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import java.util.UUID;

public class PlayerData {
    private String name;
    private UUID uuid;
    private String skinURL;
    private ItemStack[] armor;
    private ItemStack[] inventory;
    private ItemStack mainHand;
    private ItemStack offHand;
    private int level;
    private double health;
    private double maxHealth;
    private Location location;

    public PlayerData(String name, UUID uuid, String skinURL, ItemStack[] armor, ItemStack[] inventory, 
                     ItemStack mainHand, ItemStack offHand, int level, double health, double maxHealth, Location location) {
        this.name = name;
        this.uuid = uuid;
        this.skinURL = skinURL;
        this.armor = armor;
        this.inventory = inventory;
        this.mainHand = mainHand;
        this.offHand = offHand;
        this.level = level;
        this.health = health;
        this.maxHealth = maxHealth;
        this.location = location;
    }

    // Getters
    public String getName() {
        return name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getSkinURL() {
        return skinURL;
    }

    public ItemStack[] getArmor() {
        return armor;
    }

    public ItemStack[] getInventory() {
        return inventory;
    }

    public ItemStack getMainHand() {
        return mainHand;
    }

    public ItemStack getOffHand() {
        return offHand;
    }
    
    public int getLevel() {
        return level;
    }
    
    public double getHealth() {
        return health;
    }
    
    public double getMaxHealth() {
        return maxHealth;
    }
    
    public Location getLocation() {
        return location;
    }
} 