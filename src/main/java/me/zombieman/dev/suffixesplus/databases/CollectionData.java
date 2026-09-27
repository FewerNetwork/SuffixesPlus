package me.zombieman.dev.suffixesplus.databases;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "collections")
public class CollectionData {

    @DatabaseField(id = true)
    private String id;
    @DatabaseField
    private String collection;
    @DatabaseField()
    private String suffix;
    @DatabaseField(defaultValue = "CHEST")
    private String icon;
    @DatabaseField
    private String lore;

    public CollectionData() {}

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }
    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }
    public String getLore() {
        return lore;
    }

    public void setLore(String lore) {
        this.lore = lore;
    }
}
