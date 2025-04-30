package de.cubelegends.chestshoplogger.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.Acrobot.ChestShop.Events.ShopCreatedEvent;
import com.Acrobot.ChestShop.Events.ShopDestroyedEvent;
import com.Acrobot.ChestShop.Events.TransactionEvent;

import de.cubelegends.chestshoplogger.ChestShopLogger;
import de.cubelegends.chestshoplogger.models.ShopModel;
import de.cubelegends.chestshoplogger.models.TransactionModel;
import de.cubelegends.chestshoplogger.utils.ShopSummaryManager;

public class ChestShopListener implements Listener {
	
	private ChestShopLogger plugin;
	private ShopSummaryManager summaryManager;
	
	public ChestShopListener(ChestShopLogger plugin) {
		this.plugin = plugin;
		this.summaryManager = new ShopSummaryManager(plugin);
	}
	
	@EventHandler(priority = EventPriority.NORMAL)
	public void onShopCreate(ShopCreatedEvent e) {
		ShopModel.create(plugin, e);
		summaryManager.updateSummaries();
	}
	
	@EventHandler(priority = EventPriority.NORMAL)
	public void onShopDestroy(ShopDestroyedEvent e) {
		ShopModel.delete(plugin, e);
		summaryManager.updateSummaries();
	}
	
	@EventHandler(priority = EventPriority.NORMAL)
	public void onTransaction(TransactionEvent e) {
		TransactionModel.create(plugin, e);
		summaryManager.updateSummaries();
	}

}
