package me.zombieman.dev.suffixesplus.databases;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.DataSourceConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.zombieman.dev.suffixesplus.SuffixesPlus;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class CollectionDatabase {
    private final Dao<CollectionData, String> collectionDataDao;
    private final HikariDataSource hikari;
    private final ConnectionSource connectionSource;

    public CollectionDatabase(String jdbcUrl, String username, String password) throws SQLException {
        // HikariCP
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setIdleTimeout(600_000); // 10 minutes
        config.setMaxLifetime(1_800_000); // 30 minutes
        config.setConnectionTimeout(30_000); // 30 seconds
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("SuffixesPlusHikariPool-CollectionDatabase");

        hikari = new HikariDataSource(config);

        connectionSource = new DataSourceConnectionSource(hikari, jdbcUrl);

        // Create table if not exists
        try {
            TableUtils.createTableIfNotExists(connectionSource, CollectionData.class);
            System.out.println("CollectionData table checked/created successfully.");
        } catch (SQLException e) {
            System.out.println("FAILED TO CREATE CollectionData TABLE!");
            e.printStackTrace();
            throw e;
        }

        collectionDataDao = DaoManager.createDao(connectionSource, CollectionData.class);
        System.out.println("Database connection established with HikariCP and tables checked.");
    }

    public void close() {
        try {
            if (connectionSource != null) {
                connectionSource.close();
            }
            if (hikari != null) {
                hikari.close();
            }
            System.out.println("Database connection closed.");
        } catch (Exception e) {
            System.out.println("Failed to close database connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean collectionExists(String id) {

        if (id == null) return false;

        try {
            CollectionData collectionData = collectionDataDao.queryForId(id);
            return collectionData != null;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void createCollection(String id, String collection) throws SQLException {
        CollectionData collectionData = new CollectionData();

        collectionData.setId(id);
        collectionData.setCollection(collection);
        collectionData.setSuffix(null);
        collectionData.setIcon("CHEST");
        collectionData.setLore(null);

        collectionDataDao.create(collectionData);
    }

    public void removeCollection(String id) throws SQLException {
        CollectionData collectionData = collectionDataDao.queryForId(id);

        if (collectionData != null) {
            collectionDataDao.delete(collectionData);
        }
    }

    public boolean addSuffix(String id, String suffix) throws SQLException {
        CollectionData collectionData = collectionDataDao.queryForId(id);
        if (collectionData != null) {
            List<String> suffixesList = fromStringToList(collectionData.getSuffix());
            if (suffixesList.contains(suffix)) return false;
            suffixesList.add(suffix);
            collectionData.setSuffix(fromListToString(suffixesList));
            collectionDataDao.update(collectionData);
            return true;
        }
        return false;
    }

    public void addSuffixes(String id, List<String> suffixes) throws SQLException {
        CollectionData collectionData = collectionDataDao.queryForId(id);
        if (collectionData != null) {
            List<String> suffixesList = fromStringToList(collectionData.getSuffix());
            for (String suffix : suffixesList) {
                suffixes.remove(suffix);
            }
            suffixesList.addAll(suffixes);
            collectionData.setSuffix(fromListToString(suffixesList));
            collectionDataDao.update(collectionData);
        }
    }

    public boolean removeSuffix(String id, String suffix) throws SQLException {
        CollectionData collectionData = collectionDataDao.queryForId(id);
        if (collectionData != null) {
            List<String> suffixesList = fromStringToList(collectionData.getSuffix());
            if (!suffixesList.contains(suffix)) {
                return false;
            }
            suffixesList.remove(suffix);
            collectionData.setSuffix(fromListToString(suffixesList));
            collectionDataDao.update(collectionData);
            return true;
        }
        return false;
    }

    public void setLore(String id, String lore) throws SQLException {
        CollectionData collectionData = collectionDataDao.queryForId(id);
        if (collectionData != null) {
            collectionData.setLore(lore);
            collectionDataDao.update(collectionData);
        }
    }

    public void updateCollection(String id, String collection) throws SQLException {
        CollectionData collectionData = collectionDataDao.queryForId(id);
        if (collectionData != null) {
            collectionData.setCollection(collection);
            collectionDataDao.update(collectionData);
        }
    }

    public void removeCollection(String id, String collection) throws SQLException {
        CollectionData collectionData = collectionDataDao.queryForId(id);
        if (collectionData != null) {
            collectionData.setCollection(null);
            collectionDataDao.update(collectionData);
        }
    }

    public boolean setIcon(String id, String icon) throws SQLException {
        CollectionData collectionData = collectionDataDao.queryForId(id);

        if (collectionData == null) return false;

        Material material = Material.matchMaterial(icon);

        if (material != null) {
            collectionData.setIcon(material.name());
            collectionDataDao.update(collectionData);
            return true;
        }
        return false;
    }

    public CollectionData getCollection(String collection) throws SQLException {
        return collectionDataDao.queryForId(collection);
    }

    public List<String> getAllCollections() throws SQLException {
        List<String> collections = new ArrayList<>();

        List<CollectionData> collectionData = collectionDataDao.queryForAll();

        for (CollectionData collectionData1 : collectionData) {
            collections.add(collectionData1.getId());
        }

        return collections;
    }

    public List<CollectionData> getAllCollectionData() throws SQLException {
        return collectionDataDao.queryForAll();
    }

    public List<String> fromStringToList(String string) {
        if (string == null || string.isBlank()) {
            return new ArrayList<>();
        }

        return new ArrayList<>(Arrays.asList(string.split(",")));
    }

    public String fromListToString(List<String> list) {
        return  String.join(",", list);
    }
}