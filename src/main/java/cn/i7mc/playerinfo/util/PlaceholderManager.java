package cn.i7mc.playerinfo.util;

import cn.i7mc.playerinfo.PlayerInfo;
import cn.i7mc.playerinfo.model.PlaceholderData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 占位符管理器类，负责与PlaceholderAPI交互并解析自定义占位符
 */
public class PlaceholderManager {
    private final PlayerInfo plugin;
    private final Map<String, PlaceholderData> placeholders = new ConcurrentHashMap<>();
    private boolean placeholderAPIAvailable = false;
    private File configFile;
    private FileConfiguration config;

    /**
     * 构造函数
     */
    public PlaceholderManager(PlayerInfo plugin) {
        this.plugin = plugin;
        this.checkPlaceholderAPI();
        this.setupConfig();
        this.loadConfig();
    }

    /**
     * 检查PlaceholderAPI是否可用
     */
    private void checkPlaceholderAPI() {
        Plugin placeholderAPI = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        placeholderAPIAvailable = placeholderAPI != null && placeholderAPI.isEnabled();
        if (!placeholderAPIAvailable) {
        } else {
        }
    }

    /**
     * 设置配置文件
     */
    private void setupConfig() {
        // 检查并创建目录
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            return;
        }

        // 配置文件路径
        configFile = new File(dataFolder, "Placeholder.yml");

        // 如果文件不存在，从模板创建
        if (!configFile.exists()) {
            try {
                copyTemplateToConfig();
            } catch (IOException e) {
            }
        } else {
        }

        // 名称映射文件路径
        File namemapFile = new File(plugin.getDataFolder(), "web/namemap.cnf");
        
        // 确保web目录存在
        File webDir = new File(plugin.getDataFolder(), "web");
        if (!webDir.exists() && !webDir.mkdirs()) {
        }
        
        // 如果名称映射文件不存在，从模板创建
        if (!namemapFile.exists()) {
            try {
                copyNamemapTemplate(webDir);
            } catch (IOException e) {
            }
        } else {
        }

        // 加载配置
        try {
            config = YamlConfiguration.loadConfiguration(configFile);
            
            // 尝试通过Reader方式使用UTF-8编码重新加载配置
            try {
                config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                    new FileInputStream(configFile), StandardCharsets.UTF_8));
            } catch (Exception e) {
            }
            
            // 检查配置是否有占位符节点
            if (config.contains("placeholders")) {
                ConfigurationSection placeholders = config.getConfigurationSection("placeholders");
                if (placeholders != null) {
                }
            } else {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 从模板复制配置文件
     */
    private void copyTemplateToConfig() throws IOException {
        // 从插件资源获取模板 - 使用正确的文件名Placeholder.yml
        InputStream inputStream = plugin.getResource("Placeholder.yml");
        if (inputStream == null) {
            createDefaultConfig();
            return;
        }
        
        // 复制模板到目标文件
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(reader);
             FileWriter writer = new FileWriter(configFile)) {
            
            int lineCount = 0;
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                writer.write(line + System.lineSeparator());
                lineCount++;
            }
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 创建默认配置文件
     */
    private void createDefaultConfig() throws IOException {
        
        Path path = Paths.get(configFile.getAbsolutePath());
        List<String> lines = new ArrayList<>();
        
        lines.add("# 更多信息区域占位符配置");
        lines.add("# 此配置文件用于定义在web界面\"更多信息\"区域显示的自定义占位符信息");
        lines.add("# 所有占位符都依赖PlaceholderAPI插件，请确保正确安装并配置");
        lines.add("");
        lines.add("placeholders:");
        lines.add("  # 经济信息（默认）");
        lines.add("  economy:");
        lines.add("    enabled: true");
        lines.add("    icon: \"gold_ingot\"");
        lines.add("    placeholder: \"%vault_eco_balance%\"");
        lines.add("    name: \"金币\"");
        lines.add("    priority: 10");
        lines.add("");
        lines.add("  # 点券信息（默认）");
        lines.add("  points:");
        lines.add("    enabled: true");
        lines.add("    icon: \"emerald\"");
        lines.add("    placeholder: \"%playerpoints_points%\"");
        lines.add("    name: \"点券\"");
        lines.add("    priority: 20");
        
        try {
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 从模板复制名称映射文件
     */
    private void copyNamemapTemplate(File webDir) throws IOException {
        // 从插件资源获取模板
        InputStream inputStream = plugin.getResource("namemap.cnf");
        if (inputStream == null) {
            createDefaultNamemap(webDir);
            return;
        }
        
        // 复制模板到目标文件
        File namemapFile = new File(webDir, "namemap.cnf");
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(reader);
             OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(namemapFile), StandardCharsets.UTF_8)) {
            
            int lineCount = 0;
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                writer.write(line + System.lineSeparator());
                lineCount++;
            }
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 创建默认名称映射文件
     */
    private void createDefaultNamemap(File webDir) throws IOException {
        
        File namemapFile = new File(webDir, "namemap.cnf");
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(namemapFile), StandardCharsets.UTF_8)) {
            writer.write("{\n");
            writer.write("    \"economy\": \"金币\",\n");
            writer.write("    \"points\": \"点券\",\n");
            writer.write("    \"attack\": \"攻击力\",\n");
            writer.write("    \"defense\": \"物理防御\"\n");
            writer.write("}");
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 加载配置
     */
    public void loadConfig() {
        this.placeholders.clear();
        
        if (!placeholderAPIAvailable) {
            return;
        }
        
        // 检查配置文件是否存在
        if (configFile == null || !configFile.exists()) {
            return;
        }
        
        // 检查配置是否为null
        if (this.config == null) {
            try {
                this.config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                    new FileInputStream(configFile), StandardCharsets.UTF_8));
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        
        // 获取配置根节点
        ConfigurationSection placeholdersSection = this.config.getConfigurationSection("placeholders");
        if (placeholdersSection == null) {
            // 尝试打印整个配置内容以便调试
            try {
            } catch (Exception e) {
            }
            return;
        }
        
        
        int count = 0;
        int enabledCount = 0;
        for (String id : placeholdersSection.getKeys(false)) {
            count++;
            
            ConfigurationSection section = placeholdersSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            
            boolean enabled = section.getBoolean("enabled", false);
            if (!enabled) {
                continue;
            }
            
            enabledCount++;
            String icon = section.getString("icon", "barrier");
            String placeholder = section.getString("placeholder", "");
            // 不再读取name字段，直接使用ID
            int priority = section.getInt("priority", 999);
            
            // 创建并添加占位符数据对象（设置name为ID，因为前端会处理映射）
            PlaceholderData data = new PlaceholderData(id, enabled, icon, placeholder, id, priority);
            this.placeholders.put(id, data);
        }
        
        // 打印所有加载的占位符
        StringBuilder sb = new StringBuilder("[占位符调试] 已加载的占位符: ");
        for (String key : this.placeholders.keySet()) {
            sb.append(key).append(", ");
        }
        plugin.getLogger().info(sb.toString());
    }
    
    /**
     * 保存配置
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "保存Placeholder.yml时出错", e);
        }
    }
    
    /**
     * 重新加载配置
     */
    public void reloadConfig() {
        this.config = YamlConfiguration.loadConfiguration(configFile);
        
        // 使用UTF-8编码重新加载配置文件
        try {
            this.config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                new FileInputStream(configFile), StandardCharsets.UTF_8));
        } catch (Exception e) {
        }
        
        loadConfig();
    }
    
    /**
     * 更新玩家的占位符数据
     */
    public JsonObject updatePlayerPlaceholders(Player player) {
        // 如果PlaceholderAPI不可用，返回空结果
        if (!placeholderAPIAvailable || player == null) {
            return new JsonObject();
        }
        
        JsonArray placeholderArray = new JsonArray();
        
        // 按优先级排序占位符
        List<PlaceholderData> sortedPlaceholders = new ArrayList<>(placeholders.values());
        Collections.sort(sortedPlaceholders);
        
        // 处理每个占位符
        for (PlaceholderData data : sortedPlaceholders) {
            if (!data.isEnabled()) continue;
            
            // 使用PlaceholderAPI解析占位符
            String rawPlaceholder = data.getPlaceholder();
            String parsed = PlaceholderAPI.setPlaceholders(player, rawPlaceholder);
            
            // 更新值并添加到结果中
            data.setValue(parsed);
            placeholderArray.add(data.toJsonObject());
        }
        
        // 创建结果对象
        JsonObject result = new JsonObject();
        result.add("placeholders", placeholderArray);
        
        return result;
    }
    
    /**
     * 根据玩家名获取占位符数据
     */
    public JsonObject getPlaceholderDataByPlayerName(String playerName) {
        Player player = plugin.getServer().getPlayer(playerName);
        if (player != null && player.isOnline()) {
            return updatePlayerPlaceholders(player);
        }
        return new JsonObject();
    }
    
    /**
     * 获取指定玩家的占位符信息，支持离线玩家
     */
    public JsonObject getOfflinePlayerPlaceholders(OfflinePlayer player) {
        if (!placeholderAPIAvailable || player == null) {
            return new JsonObject();
        }
        
        JsonArray placeholderArray = new JsonArray();
        
        // 按优先级排序占位符
        List<PlaceholderData> sortedPlaceholders = new ArrayList<>(placeholders.values());
        Collections.sort(sortedPlaceholders);
        
        // 处理每个占位符
        for (PlaceholderData data : sortedPlaceholders) {
            if (!data.isEnabled()) continue;
            
            // 使用PlaceholderAPI解析占位符
            String rawPlaceholder = data.getPlaceholder();
            String parsed = PlaceholderAPI.setPlaceholders(player, rawPlaceholder);
            
            // 更新值并添加到结果中
            data.setValue(parsed);
            placeholderArray.add(data.toJsonObject());
        }
        
        // 创建结果对象
        JsonObject result = new JsonObject();
        result.add("placeholders", placeholderArray);
        
        return result;
    }
    
    /**
     * 检查是否启用了PlaceholderAPI
     */
    public boolean isPlaceholderAPIAvailable() {
        return placeholderAPIAvailable;
    }

    /**
     * 获取占位符数据的JSON
     * @param player 玩家对象
     * @return 占位符数据的JSON对象
     */
    public JsonObject getPlaceholdersJson(Player player) {
        JsonObject data = new JsonObject();
        JsonArray placeholders = new JsonArray();
        
        
        if (!this.isPlaceholderAPIAvailable() || !this.config.getBoolean("enabled", true)) {
            data.addProperty("placeholdersAvailable", false);
            data.add("placeholders", placeholders);
            return data;
        }
        
        // 添加一个名称映射，用于前端处理可能的乱码
        JsonObject nameMapping = new JsonObject();
        
        // 处理配置中的每个占位符
        ConfigurationSection placeholdersSection = this.config.getConfigurationSection("placeholders");
        if (placeholdersSection != null) {
            
            // 按优先级排序
            List<String> keys = new ArrayList<>(placeholdersSection.getKeys(false));
            keys.sort((a, b) -> {
                int priorityA = placeholdersSection.getInt(a + ".priority", 999);
                int priorityB = placeholdersSection.getInt(b + ".priority", 999);
                return Integer.compare(priorityA, priorityB);
            });
            
            // 处理每个占位符
            for (String id : keys) {
                ConfigurationSection section = placeholdersSection.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                
                boolean enabled = section.getBoolean("enabled", false);
                if (!enabled) {
                    continue;
                }
                
                String icon = section.getString("icon", "barrier");
                String placeholder = section.getString("placeholder", "");
                int priority = section.getInt("priority", 999);
                
                // 替换占位符
                String value = placeholder;
                if (placeholder.contains("%") && player != null && PlaceholderAPI.containsPlaceholders(placeholder)) {
                    try {
                        value = PlaceholderAPI.setPlaceholders(player, placeholder);
                    } catch (Exception e) {
                    }
                } else {
                }
                
                // 创建占位符JSON对象 - 使用ID作为name
                JsonObject placeholderObj = new JsonObject();
                placeholderObj.addProperty("id", id);
                placeholderObj.addProperty("enabled", enabled);
                placeholderObj.addProperty("icon", icon);
                placeholderObj.addProperty("placeholder", placeholder);
                placeholderObj.addProperty("priority", priority);
                placeholderObj.addProperty("value", value);
                
                placeholders.add(placeholderObj);
            }
        } else {
        }
        
        data.addProperty("placeholdersAvailable", true);
        data.add("placeholders", placeholders);
        data.add("nameMapping", nameMapping);
        return data;
    }
} 