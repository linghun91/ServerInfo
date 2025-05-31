package cn.i7mc.playerinfo.model;

import com.google.gson.JsonObject;
import java.util.Objects;

/**
 * 占位符数据模型类，用于存储自定义占位符信息
 * 包含图标、名称、值、优先级等属性
 */
public class PlaceholderData implements Comparable<PlaceholderData> {
    
    private String id;          // 唯一标识符
    private boolean enabled;    // 是否启用
    private String icon;        // 图标名称
    private String placeholder; // 占位符表达式
    private String name;        // 显示名称
    private int priority;       // 显示优先级
    private String value;       // 解析后的值

    /**
     * 默认构造函数
     */
    public PlaceholderData() {
    }

    /**
     * 完整参数的构造函数
     */
    public PlaceholderData(String id, boolean enabled, String icon, String placeholder, String name, int priority) {
        this.id = id;
        this.enabled = enabled;
        this.icon = icon;
        this.placeholder = placeholder;
        this.name = name;
        this.priority = priority;
        this.value = "0"; // 默认值
    }

    /**
     * 将对象转换为JsonObject
     */
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("enabled", enabled);
        json.addProperty("icon", icon);
        json.addProperty("placeholder", placeholder);
        json.addProperty("priority", priority);
        json.addProperty("value", value);
        return json;
    }

    /**
     * 实现Comparable接口，按优先级排序
     */
    @Override
    public int compareTo(PlaceholderData other) {
        return Integer.compare(this.priority, other.priority);
    }

    /**
     * 重写equals方法
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaceholderData that = (PlaceholderData) o;
        return Objects.equals(id, that.id);
    }

    /**
     * 重写hashCode方法
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // Getters 和 Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
} 