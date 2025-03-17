package cn.i7mc.playerinfo.model;

import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import java.util.UUID;
import java.util.Map;
import com.google.gson.JsonObject;

public class PlayerData {
    private String name;
    private UUID uuid;
    private String skinURL;
    private ItemStack[] armor;
    private ItemStack[] inventory;
    private Map<Integer, Map<String, Object>> inventoryMap;
    private Map<String, Map<String, Object>> equipmentMap;
    private ItemStack mainHand;
    private ItemStack offHand;
    private int level;
    private float exp;
    private int foodLevel;
    private String gamemode;
    private String world;
    private double health;
    private double maxHealth;
    private Location location;
    
    // 占位符数据
    private JsonObject placeholders;

    // DragonCore容器数据
    private Map<String, Map<String, Object>> dragonCore;

    // 位置坐标相关字段
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    // 默认构造函数
    public PlayerData() {
    }

    public PlayerData(String name, UUID uuid, String skinURL, ItemStack[] armor, ItemStack[] inventory, 
                     ItemStack mainHand, ItemStack offHand, int level, double health, double maxHealth, 
                     Location location) {
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
    
    public Map<Integer, Map<String, Object>> getInventoryMap() {
        return inventoryMap;
    }
    
    public Map<String, Map<String, Object>> getEquipmentMap() {
        return equipmentMap;
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

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    // Setters
    public void setName(String name) {
        this.name = name;
    }

    public void setUuid(String uuid) {
        this.uuid = UUID.fromString(uuid);
    }

    public void setSkinURL(String skinURL) {
        this.skinURL = skinURL;
    }

    public void setArmor(ItemStack[] armor) {
        this.armor = armor;
    }

    public void setInventory(ItemStack[] inventory) {
        this.inventory = inventory;
    }
    
    public void setInventory(Map<Integer, Map<String, Object>> inventoryMap) {
        this.inventoryMap = inventoryMap;
    }
    
    public void setEquipment(Map<String, Map<String, Object>> equipmentMap) {
        this.equipmentMap = equipmentMap;
    }

    public void setMainHand(ItemStack mainHand) {
        this.mainHand = mainHand;
    }

    public void setOffHand(ItemStack offHand) {
        this.offHand = offHand;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setExp(float exp) {
        this.exp = exp;
    }

    public float getExp() {
        return exp;
    }

    public void setHealth(double health) {
        this.health = health;
    }

    public void setMaxHealth(double maxHealth) {
        this.maxHealth = maxHealth;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public void setFoodLevel(int foodLevel) {
        this.foodLevel = foodLevel;
    }

    public int getFoodLevel() {
        return foodLevel;
    }

    public void setGamemode(String gamemode) {
        this.gamemode = gamemode;
    }

    public String getGamemode() {
        return gamemode;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public JsonObject getPlaceholders() {
        return placeholders;
    }

    public void setPlaceholders(JsonObject placeholders) {
        this.placeholders = placeholders;
    }
    
    public Map<String, Map<String, Object>> getDragonCore() {
        return dragonCore;
    }
    
    public void setDragonCore(Map<String, Map<String, Object>> dragonCore) {
        this.dragonCore = dragonCore;
    }
} 