package bladesmp.deathmoneylossplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class ReloadCommand implements CommandExecutor {

    private final DeathMoneyLossPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ReloadCommand(DeathMoneyLossPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("deathmoneylossplugin.reload")) {
            Component message = miniMessage.deserialize("<red>Du hast keine Berechtigung für diesen Befehl!");
            sender.sendMessage(message);
            return true;
        }

        plugin.reloadPluginConfig();

        Component successMessage = miniMessage.deserialize("<green>DeathMoneyLoss Konfiguration wurde neu geladen!");
        Component infoMessage = miniMessage.deserialize("<yellow>Aktueller Geldverlust: " + plugin.getLossPercentage() + "%");

        sender.sendMessage(successMessage);
        sender.sendMessage(infoMessage);

        return true;
    }
}