package cn.i7mc.playerinfo.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Location;
import cn.i7mc.playerinfo.model.PlayerData;
import java.util.*;

public class ItemStackSerializer {
    
    public static Map<String, Object> serialize(PlayerData playerData) {
        Map<String, Object> result = new HashMap<>();
        result.put("name", playerData.getName());
        
        // 添加UUID和皮肤URL到JSON结果
        if (playerData.getUuid() != null) {
            result.put("uuid", playerData.getUuid().toString());
        }
        
        if (playerData.getSkinURL() != null) {
            result.put("skinURL", playerData.getSkinURL());
        }
        
        result.put("armor", serializeItems(playerData.getArmor()));
        result.put("inventory", serializeItems(playerData.getInventory()));

        // Serialize main hand and off hand items
        ItemStack mainHand = playerData.getMainHand();
        ItemStack offHand = playerData.getOffHand();
        
        Map<String, Object> mainHandData = serializeItem(mainHand);
        Map<String, Object> offHandData = serializeItem(offHand);
        
        result.put("mainHand", mainHandData);
        result.put("offHand", offHandData);
        
        // 序列化新增的玩家状态数据
        result.put("level", playerData.getLevel());
        result.put("health", playerData.getHealth());
        result.put("maxHealth", playerData.getMaxHealth());
        
        // 序列化位置信息 - 避免直接序列化Location对象，仅提取需要的数据
        Location location = playerData.getLocation();
        if (location != null) {
            try {
                Map<String, Object> locationData = new HashMap<>();
                locationData.put("x", location.getX());
                locationData.put("y", location.getY());
                locationData.put("z", location.getZ());
                // 只存储世界名称，避免序列化整个World对象
                locationData.put("world", location.getWorld() != null ? location.getWorld().getName() : "unknown");
                locationData.put("yaw", location.getYaw());
                locationData.put("pitch", location.getPitch());
                
                result.put("location", locationData);
            } catch (Exception e) {
                // 如果序列化Location对象出错，记录错误并继续而不是失败整个序列化过程
                Map<String, Object> simpleLocation = new HashMap<>();
                simpleLocation.put("error", "无法序列化位置信息: " + e.getMessage());
                result.put("location", simpleLocation);
            }
        }
        
        return result;
    }

    private static List<Map<String, Object>> serializeItems(ItemStack[] items) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (items != null) {
            for (ItemStack item : items) {
                try {
                    Map<String, Object> serializedItem = serializeItem(item);
                    if (serializedItem != null) {
                        result.add(serializedItem);
                    }
                } catch (Exception e) {
                    // 如果序列化单个物品失败，添加一个错误标记项而不是中断整个数组的序列化
                    Map<String, Object> errorItem = new HashMap<>();
                    errorItem.put("error", "序列化物品失败");
                    errorItem.put("type", item != null ? item.getType().name() : "UNKNOWN");
                    result.add(errorItem);
                }
            }
        }
        return result;
    }

    private static Map<String, Object> serializeItem(ItemStack item) {
        if (item == null) {
            return null;
        }
        
        if (item.getType().name().equals("AIR")) {
            return null;
        }

        Map<String, Object> itemMap = new HashMap<>();
        itemMap.put("type", item.getType().name());
        itemMap.put("amount", item.getAmount());
        
        // 为所有物品添加数据值，不仅仅是有耐久度的物品
        // 这对于像染料(INK_SACK)这样使用数据值来区分的物品很重要
        short dataValue = item.getDurability();
        itemMap.put("durability", dataValue);
        
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (meta.hasDisplayName()) {
                    itemMap.put("name", meta.getDisplayName());
                }
                if (meta.hasLore()) {
                    itemMap.put("lore", meta.getLore());
                }
                // 我们不序列化其他可能导致循环引用的元数据
            }
        } catch (Exception e) {
            // 如果处理物品元数据时出错，记录错误但继续序列化其他数据
            itemMap.put("meta_error", "无法序列化物品元数据: " + e.getMessage());
        }
        
        return itemMap;
    }
} 