package bladesmp.deathmoneylossplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DeathMoneyLossPlugin extends JavaPlugin implements Listener {

    private Economy economy = null;
    private double lossPercentage = 10.0;
    private DecimalFormat moneyFormat = new DecimalFormat("#,##0.00");
    private MiniMessage miniMessage = MiniMessage.miniMessage();

    // Message Settings
    private boolean enableVictimMessage = true;
    private boolean enableKillerMessage = true;
    private boolean enableVictimActionBar = true;
    private boolean enableKillerActionBar = true;

    // Messages
    private String victimMessage = "<red>Du hast <yellow>{amount}€</yellow> verloren! (<yellow>{percentage}%</yellow> deines Geldes)";
    private String killerMessage = "<green>Du hast <yellow>{amount}€</yellow> von <yellow>{victim}</yellow> erhalten!";
    private String victimActionBar = "<red>-{amount}€";
    private String killerActionBar = "<green>+{amount}€";

    // Sound Settings
    private boolean enableVictimSound = true;
    private boolean enableKillerSound = true;
    private String victimSoundName = "ENTITY_VILLAGER_NO";
    private String killerSoundName = "ENTITY_EXPERIENCE_ORB_PICKUP";
    private float victimSoundVolume = 1.0f;
    private float killerSoundVolume = 1.0f;
    private float victimSoundPitch = 1.0f;
    private float killerSoundPitch = 1.0f;

    // Temp storage for respawn messages
    private Map<UUID, PendingMessage> pendingMessages = new HashMap<>();

    @Override
    public void onEnable() {
        // Prüfen ob Folia läuft
        if (isFolia()) {
            getLogger().info("Folia erkannt - Plugin läuft im Folia-Modus");
        }

        // Config erstellen/laden
        saveDefaultConfig();
        loadConfigValues();

        // Vault Economy setup
        if (!setupEconomy()) {
            getLogger().warning("Vault Economy Plugin nicht gefunden!");
            getLogger().warning("Das Plugin wird geladen, aber Economy-Features sind deaktiviert.");
            getLogger().warning("Bitte installiere Vault und ein Economy Plugin für volle Funktionalität.");
        } else {
            getLogger().info("Vault Economy erfolgreich verbunden!");
        }

        // Event Listener registrieren
        getServer().getPluginManager().registerEvents(this, this);

        // Commands registrieren
        this.getCommand("deathmoneyreload").setExecutor(new ReloadCommand(this));

        getLogger().info("DeathMoneyLoss Plugin erfolgreich aktiviert!");
        if (economy != null) {
            getLogger().info("Geldverlust beim Tod: " + lossPercentage + "%");
        }
    }

    @Override
    public void onDisable() {
        pendingMessages.clear();
        getLogger().info("DeathMoneyLoss Plugin deaktiviert!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    private void loadConfigValues() {
        reloadConfig();
        lossPercentage = getConfig().getDouble("loss-percentage", 10.0);

        // Message Settings
        enableVictimMessage = getConfig().getBoolean("messages.victim.enable", true);
        enableKillerMessage = getConfig().getBoolean("messages.killer.enable", true);
        enableVictimActionBar = getConfig().getBoolean("actionbars.victim.enable", true);
        enableKillerActionBar = getConfig().getBoolean("actionbars.killer.enable", true);

        // Message Content
        victimMessage = getConfig().getString("messages.victim.text",
                "<red>Du hast <yellow>{amount}€</yellow> verloren! (<yellow>{percentage}%</yellow> deines Geldes)");
        killerMessage = getConfig().getString("messages.killer.text",
                "<green>Du hast <yellow>{amount}€</yellow> von <yellow>{victim}</yellow> erhalten!");
        victimActionBar = getConfig().getString("actionbars.victim.text", "<red>-{amount}€");
        killerActionBar = getConfig().getString("actionbars.killer.text", "<green>+{amount}€");

        // Sound Settings
        enableVictimSound = getConfig().getBoolean("sounds.victim.enable", true);
        enableKillerSound = getConfig().getBoolean("sounds.killer.enable", true);
        victimSoundName = getConfig().getString("sounds.victim.sound", "ENTITY_VILLAGER_NO");
        killerSoundName = getConfig().getString("sounds.killer.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        victimSoundVolume = (float) getConfig().getDouble("sounds.victim.volume", 1.0);
        killerSoundVolume = (float) getConfig().getDouble("sounds.killer.volume", 1.0);
        victimSoundPitch = (float) getConfig().getDouble("sounds.victim.pitch", 1.0);
        killerSoundPitch = (float) getConfig().getDouble("sounds.killer.pitch", 1.0);

        // Validierung der Prozentzahl
        if (lossPercentage < 0) {
            lossPercentage = 0;
        } else if (lossPercentage > 100) {
            lossPercentage = 100;
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Nur wenn ein Spieler den anderen getötet hat
        if (killer == null || !(killer instanceof Player)) {
            return;
        }

        // Prüfen ob Economy verfügbar ist
        if (economy == null) {
            return;
        }

        // Prüfen ob Spieler von Geldverlust befreit ist
        if (victim.hasPermission("deathmoneylossplugin.exempt")) {
            return;
        }

        // Aktuelles Geld des Opfers abrufen
        double victimMoney = economy.getBalance(victim);

        // Wenn das Opfer kein Geld hat, nichts tun
        if (victimMoney <= 0) {
            return;
        }

        // Geldverlust berechnen
        double lossAmount = (victimMoney * lossPercentage) / 100.0;

        // Mindestbetrag prüfen
        if (lossAmount < 0.01) {
            return;
        }

        // Geld transferieren (Folia-kompatibel)
        if (isFolia()) {
            // Auf dem Async-Scheduler ausführen (Folia)
            Bukkit.getAsyncScheduler().runNow(this, (task) -> {
                performMoneyTransfer(victim, killer, lossAmount);
            });
        } else {
            // Synchron ausführen (Bukkit/Spigot/Paper)
            performMoneyTransfer(victim, killer, lossAmount);
        }
    }

    private void performMoneyTransfer(Player victim, Player killer, double amount) {
        // Geld vom Opfer abziehen
        economy.withdrawPlayer(victim, amount);
        // Geld dem Killer geben
        economy.depositPlayer(killer, amount);

        // Nachricht für das Opfer speichern (wird beim Respawn gesendet)
        PendingMessage pendingMessage = new PendingMessage(
                amount, lossPercentage, killer.getName()
        );
        pendingMessages.put(victim.getUniqueId(), pendingMessage);

        // Sofort Nachricht und ActionBar für den Killer senden
        sendKillerNotification(killer, amount, victim.getName());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Prüfen ob eine ausstehende Nachricht vorliegt
        if (pendingMessages.containsKey(playerId)) {
            PendingMessage pending = pendingMessages.get(playerId);

            // Kurz warten und dann Nachrichten senden
            if (isFolia()) {
                player.getScheduler().runDelayed(this, (task) -> {
                    sendVictimNotification(player, pending.amount, pending.percentage);
                }, null, 10L); // 0.5 Sekunden Verzögerung
            } else {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    sendVictimNotification(player, pending.amount, pending.percentage);
                }, 10L);
            }

            // Ausstehende Nachricht entfernen
            pendingMessages.remove(playerId);
        }
    }

    private void sendVictimNotification(Player victim, double amount, double percentage) {
        String formattedAmount = moneyFormat.format(amount);

        // Chat Nachricht
        if (enableVictimMessage) {
            String message = victimMessage
                    .replace("{amount}", formattedAmount)
                    .replace("{percentage}", String.valueOf(percentage));
            Component component = miniMessage.deserialize(message);
            victim.sendMessage(component);
        }

        // Action Bar
        if (enableVictimActionBar) {
            String actionBarText = victimActionBar.replace("{amount}", formattedAmount);
            Component actionBarComponent = miniMessage.deserialize(actionBarText);
            victim.sendActionBar(actionBarComponent);
        }

        // Sound
        if (enableVictimSound) {
            playSound(victim, victimSoundName, victimSoundVolume, victimSoundPitch);
        }
    }

    private void sendKillerNotification(Player killer, double amount, String victimName) {
        String formattedAmount = moneyFormat.format(amount);

        // Chat Nachricht
        if (enableKillerMessage) {
            String message = killerMessage
                    .replace("{amount}", formattedAmount)
                    .replace("{victim}", victimName);
            Component component = miniMessage.deserialize(message);
            killer.sendMessage(component);
        }

        // Action Bar
        if (enableKillerActionBar) {
            String actionBarText = killerActionBar.replace("{amount}", formattedAmount);
            Component actionBarComponent = miniMessage.deserialize(actionBarText);
            killer.sendActionBar(actionBarComponent);
        }

        // Sound
        if (enableKillerSound) {
            playSound(killer, killerSoundName, killerSoundVolume, killerSoundPitch);
        }
    }

    private void playSound(Player player, String soundName, float volume, float pitch) {
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Ungültiger Sound: " + soundName);
            // Fallback zu einem Standard-Sound
            try {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, volume, pitch);
            } catch (Exception ignored) {
                // Wenn auch das fehlschlägt, ignorieren
            }
        }
    }

    private boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // Getter für andere Klassen
    public Economy getEconomy() {
        return economy;
    }

    public void reloadPluginConfig() {
        loadConfigValues();
        getLogger().info("Konfiguration neu geladen! Geldverlust: " + lossPercentage + "%");
    }

    public double getLossPercentage() {
        return lossPercentage;
    }

    // Hilfsklasse für ausstehende Nachrichten
    private static class PendingMessage {
        final double amount;
        final double percentage;
        final String killerName;

        PendingMessage(double amount, double percentage, String killerName) {
            this.amount = amount;
            this.percentage = percentage;
            this.killerName = killerName;
        }
    }
}