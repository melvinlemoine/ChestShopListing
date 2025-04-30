package de.cubelegends.chestshoplogger.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.cubelegends.chestshoplogger.ChestShopLogger;
import de.cubelegends.chestshoplogger.models.ShopModel;
import de.cubelegends.chestshoplogger.models.TransactionModel;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ShopSummaryManager {
    private final ChestShopLogger plugin;
    private final Gson gson;
    private final File summaryDir;

    public ShopSummaryManager(ChestShopLogger plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.summaryDir = new File(plugin.getDataFolder(), "summaries");
        if (!summaryDir.exists()) {
            summaryDir.mkdirs();
        }
    }

    public void updateSummaries() {
        CompletableFuture.runAsync(() -> {
            try {
                updateShopSummary();
            } catch (SQLException | IOException e) {
                plugin.getLogger().severe("Error updating shop summaries: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void updateShopSummary() throws SQLException, IOException {
        Map<String, Object> summary = new HashMap<>();
        List<Map<String, Object>> shops = new ArrayList<>();

        try (Connection con = plugin.getDBHandler().open()) {
            // Get all shops
            PreparedStatement st = con.prepareStatement("SELECT * FROM chestshop_shop");
            ResultSet rs = st.executeQuery();

            while (rs.next()) {
                Map<String, Object> shopData = new HashMap<>();
                int shopId = rs.getInt("id");
                
                // Basic shop info
                shopData.put("id", shopId);
                shopData.put("world", rs.getString("world"));
                shopData.put("x", rs.getInt("x"));
                shopData.put("y", rs.getInt("y"));
                shopData.put("z", rs.getInt("z"));
                shopData.put("owner_uuid", rs.getString("owneruuid"));
                shopData.put("max_amount", rs.getInt("maxamount"));
                shopData.put("buy_price", rs.getDouble("buyprice"));
                shopData.put("sell_price", rs.getDouble("sellprice"));
                shopData.put("item_name", rs.getString("itemname"));
                shopData.put("created", rs.getLong("created"));
                shopData.put("remaining_stock", rs.getInt("remaining_stock"));

                // Get total transactions
                Map<String, Object> totalTransactions = getTransactionStats(con, shopId, null);
                shopData.put("total_transactions", totalTransactions);

                // Get 30-day transactions
                long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
                Map<String, Object> recentTransactions = getTransactionStats(con, shopId, thirtyDaysAgo);
                shopData.put("recent_transactions", recentTransactions);

                // Get per-player transactions
                Map<String, Object> playerTransactions = getPlayerTransactionStats(con, shopId);
                shopData.put("player_transactions", playerTransactions);

                shops.add(shopData);
            }

            rs.close();
            st.close();
        }

        summary.put("shops", shops);
        summary.put("last_updated", System.currentTimeMillis());

        // Write to file
        File summaryFile = new File(summaryDir, "shops_summary.json");
        try (FileWriter writer = new FileWriter(summaryFile)) {
            gson.toJson(summary, writer);
        }
    }

    private Map<String, Object> getTransactionStats(Connection con, int shopId, Long since) throws SQLException {
        Map<String, Object> stats = new HashMap<>();
        String query = "SELECT type, SUM(amount) as total_amount, SUM(price) as total_price, COUNT(*) as count " +
                      "FROM chestshop_transaction WHERE shopid = ?";
        
        if (since != null) {
            query += " AND date >= ?";
        }
        query += " GROUP BY type";

        PreparedStatement st = con.prepareStatement(query);
        st.setInt(1, shopId);
        if (since != null) {
            st.setLong(2, since);
        }

        ResultSet rs = st.executeQuery();
        Map<String, Object> buyStats = new HashMap<>();
        Map<String, Object> sellStats = new HashMap<>();

        while (rs.next()) {
            String type = rs.getString("type");
            Map<String, Object> typeStats = new HashMap<>();
            typeStats.put("total_amount", rs.getInt("total_amount"));
            typeStats.put("total_price", rs.getDouble("total_price"));
            typeStats.put("count", rs.getInt("count"));

            if (type.equals("buy")) {
                buyStats = typeStats;
            } else if (type.equals("sell")) {
                sellStats = typeStats;
            }
        }

        stats.put("buy", buyStats);
        stats.put("sell", sellStats);

        rs.close();
        st.close();
        return stats;
    }

    private Map<String, Object> getPlayerTransactionStats(Connection con, int shopId) throws SQLException {
        Map<String, Object> playerStats = new HashMap<>();
        String query = "SELECT clientuuid, type, SUM(amount) as total_amount, SUM(price) as total_price, COUNT(*) as count " +
                      "FROM chestshop_transaction WHERE shopid = ? GROUP BY clientuuid, type";

        PreparedStatement st = con.prepareStatement(query);
        st.setInt(1, shopId);
        ResultSet rs = st.executeQuery();

        while (rs.next()) {
            String playerUuid = rs.getString("clientuuid");
            String type = rs.getString("type");
            
            if (!playerStats.containsKey(playerUuid)) {
                playerStats.put(playerUuid, new HashMap<>());
            }

            Map<String, Object> playerData = (Map<String, Object>) playerStats.get(playerUuid);
            Map<String, Object> typeStats = new HashMap<>();
            typeStats.put("total_amount", rs.getInt("total_amount"));
            typeStats.put("total_price", rs.getDouble("total_price"));
            typeStats.put("count", rs.getInt("count"));

            playerData.put(type, typeStats);
        }

        rs.close();
        st.close();
        return playerStats;
    }
} 