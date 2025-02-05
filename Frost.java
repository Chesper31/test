package me.elb1to.practice;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import lombok.Getter;
import me.elb1to.practice.commands.admin.CancelMatchCommand;
import me.elb1to.practice.commands.admin.GotoEventCommand;
import me.elb1to.practice.commands.admin.SetLootCommand;
import me.elb1to.practice.commands.admin.SetSpawnCommand;
import me.elb1to.practice.commands.admin.SpawnCommand;
import me.elb1to.practice.commands.user.TournamentCommand;
import me.elb1to.practice.database.MongoHandler;
import me.elb1to.practice.game.match.Match;
import me.elb1to.practice.handler.CommandHandler;
import me.elb1to.practice.handler.ListenerHandler;
import me.elb1to.practice.handler.ManagerHandler;
import me.elb1to.practice.handler.MiscHandler;
import me.elb1to.practice.managers.chunk.ChunkRestorationManager;
import me.elb1to.practice.providers.papi.PlaceholderAPIProvider;
import me.elb1to.practice.user.player.PracticePlayerData;
import me.elb1to.practice.util.CC;
import me.elb1to.practice.util.config.FileConfig;
import me.elb1to.practice.util.file.Config;
import me.elb1to.practice.util.threads.Threads;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Getter
public final class Frost extends JavaPlugin {

    @Getter private static Frost instance;

    private final Gson gson = new Gson();
    private final Random random = new Random();
    private final JsonParser jsonParser = new JsonParser();

    private Config mainConfig, arenasConfig, kitsConfig;
    private FileConfig settingsConfig, messagesConfig, scoreboardConfig, eventScoreboardConfig, tablistConfig, hotbarConfig, menusConfig, chestConfig;

    private MongoHandler mongoHandler;
    private ManagerHandler managerHandler;

    private boolean usingCustomKB = false;
    private long leaderboardUpdateTime;

    @Override
    public void onEnable() {
        instance = this;
        Threads.init();

        if (!getDescription().getAuthors().contains("Elb1to") || !getDescription().getName().equals("Frost")) {
            Bukkit.getPluginManager().disablePlugin(this);
            Bukkit.shutdown();
        }

        this.mainConfig = new Config("config", this);
        this.arenasConfig = new Config("arenas", this);
        this.kitsConfig = new Config("kits", this);
        this.settingsConfig = new FileConfig(this, "settings.yml");
        this.messagesConfig = new FileConfig(this, "messages.yml");
        this.scoreboardConfig = new FileConfig(this, "scoreboard.yml");
        this.eventScoreboardConfig = new FileConfig(this, "event-scoreboard.yml");
        this.tablistConfig = new FileConfig(this, "tablist.yml");
        this.hotbarConfig = new FileConfig(this, "hotbar.yml");
        this.menusConfig = new FileConfig(this, "menus.yml");
        this.chestConfig = new FileConfig(this, "chest.yml");

        if (getServer().getPluginManager().getPlugin("KnockbackController") == null) {
            getServer().getConsoleSender().sendMessage(CC.translate("&8[&bFrost&8] &cYou don't have any KnockbackController for Frost in your plugins folder."));
            getServer().getConsoleSender().sendMessage(CC.translate("&8[&bFrost&8] &cBecause of that, KB-Per-Kits won't work."));
        } else {
            usingCustomKB = true;
            getServer().getConsoleSender().sendMessage(CC.translate("&8[&bFrost&8] &aThis server is running " + me.elb1to.practice.controller.PracticeKnockbackController.name + "!"));
            getServer().getConsoleSender().sendMessage(CC.translate("&8[&bFrost&8] &a" + me.elb1to.practice.controller.PracticeKnockbackController.name + "'s KnockbackAPI methods have been implemented"));
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIProvider(this).register();
            getServer().getConsoleSender().sendMessage(CC.translate("&8[&bFrost&8] &aPlaceholder API expansion successfully registered."));
        }

        leaderboardUpdateTime = (settingsConfig.getConfig().getInt("SETTINGS.LEADERBOARDS.UPDATE-TIME") * 60L) * 20L;

        mongoHandler = new MongoHandler(this);
        managerHandler = new ManagerHandler(this);
        managerHandler.register();

        this.registerCommands();
        new CommandHandler(this);
        new ListenerHandler(this);
        new MiscHandler(this);
    }

    @Override
    public void onDisable() {
        for (PracticePlayerData practicePlayerData : managerHandler.getPlayerManager().getAllData()) {
            managerHandler.getPlayerManager().saveData(practicePlayerData);
            Threads.executeData(() -> managerHandler.getPlayerManager().saveData(practicePlayerData));
        }

        managerHandler.getArenaManager().saveArenas();
        managerHandler.getKitManager().saveKits();

        for (Map.Entry<UUID, Match> entry : managerHandler.getMatchManager().getMatches().entrySet()) {
            Match match = entry.getValue();
            if (match.getKit().isBuild() || match.getKit().isSpleef()) {
                ChunkRestorationManager.getIChunkRestoration().reset(match.getStandaloneArena());
            }
        }

        for (Entity entity : this.getServer().getWorld("world").getEntities()) {
            if (entity.getType() == EntityType.DROPPED_ITEM) {
                entity.remove();
            }
        }

        for (Chunk chunk : this.getServer().getWorld("world").getLoadedChunks()) {
            chunk.unload(true);
        }

        getServer().getConsoleSender().sendMessage(CC.translate("&8[&bFrost&8] &cUnloading chunks..."));

        this.mongoHandler.disconnect();
    }

    private void registerCommands() {
        Arrays.asList(new GotoEventCommand(), new SetLootCommand(), new SpawnCommand(), new SetSpawnCommand(), new CancelMatchCommand(), new TournamentCommand()).forEach(command -> this.registerCommand(command, getName()));
    }

    public void registerCommand(Command cmd, String fallbackPrefix) {
        MinecraftServer.getServer().server.getCommandMap().register(cmd.getName(), fallbackPrefix, cmd);
    }
}
