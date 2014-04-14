/*
 * Copyright (C) 2014 Lord_Ralex
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.hoenn.pokebot;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import jline.console.ConsoleReader;
import org.hoenn.pokebot.api.channels.Channel;
import org.hoenn.pokebot.api.events.ConnectionEvent;
import org.hoenn.pokebot.api.users.Bot;
import org.hoenn.pokebot.api.users.User;
import org.hoenn.pokebot.eventhandler.EventHandler;
import org.hoenn.pokebot.extension.ExtensionManager;
import org.hoenn.pokebot.implementation.PokeBotBot;
import org.hoenn.pokebot.implementation.PokeBotChannel;
import org.hoenn.pokebot.implementation.PokeBotUser;
import org.hoenn.pokebot.input.KeyboardListener;
import org.hoenn.pokebot.permissions.PermissionManager;
import org.hoenn.pokebot.scheduler.Scheduler;
import org.hoenn.pokebot.settings.Settings;
import org.pircbotx.PircBotX;
import org.pircbotx.exception.IrcException;
import org.pircbotx.exception.NickAlreadyInUseException;

/**
 *
 * @author Lord_Ralex
 */
public class PokeBotCore {

    private final EventHandler eventHandler;
    private final KeyboardListener kblistener;
    private final Settings globalSettings;
    private final PermissionManager permManager;
    private final ExtensionManager extensionManager;
    private final Scheduler scheduler;
    private final PircBotX driver;
    private final ConcurrentHashMap<String, Channel> channelCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<org.pircbotx.User, User> userCache = new ConcurrentHashMap<>();
    private Bot botUser;

    protected PokeBotCore() {
        if (!(new File("config.yml").exists())) {
            try (InputStream input = PokeBot.class.getResourceAsStream("/config.yml")) {
                try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(new File("config.yml")))) {
                    try {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = input.read(buffer)) >= 0) {
                            output.write(buffer, 0, len);
                        }
                        input.close();
                        output.close();
                    } catch (IOException ex) {
                        PokeBot.log(Level.SEVERE, "An error occurred on copying the streams", ex);
                    }
                }
            } catch (IOException ex) {
                PokeBot.log(Level.SEVERE, "Error on saving config", ex);
            }
        }
        globalSettings = new Settings();
        try {
            globalSettings.load(new File("config.yml"));
        } catch (IOException e) {
            PokeBot.log(Level.SEVERE, "Could not load config.yml", e);
        }
        driver = new PircBotX();
        KeyboardListener temp;
        try {
            temp = new KeyboardListener(this, driver);
        } catch (IOException ex) {
            temp = null;
            PokeBot.log(Level.SEVERE, "An error occured", ex);
        }
        kblistener = temp;
        eventHandler = new EventHandler(driver);
        extensionManager = new ExtensionManager();
        permManager = new PermissionManager();
        scheduler = new Scheduler();
    }

    protected void createInstance(String user, String pass) throws IOException, IrcException {
        driver.setEncoding(Charset.forName("UTF-8"));
        String bind = globalSettings.getString("bind-ip");
        if (bind != null && !bind.isEmpty()) {
            InetAddress addr = InetAddress.getByName(bind);
            driver.setInetAddress(addr);
        }
        driver.setVerbose(true);
        driver.setVersion("PokeBot   - v" + PokeBot.VERSION);
        driver.setAutoReconnect(false);
        driver.setAutoReconnectChannels(true);
        String nick = user;
        if (nick == null || nick.isEmpty()) {
            nick = globalSettings.getString("nick");
        }
        if (nick == null || nick.isEmpty()) {
            nick = "DebugBot";
        }
        driver.setName(nick);
        driver.setLogin(nick);

        PokeBot.log(Level.INFO, "Nick of bot: " + nick);

        eventHandler.load();
        extensionManager.load();
        try {
            permManager.load();
        } catch (IOException e) {
            PokeBot.log(Level.SEVERE, "Error loading permissions file", e);
        }
        boolean eventSuccess = driver.getListenerManager().addListener(eventHandler);
        if (eventSuccess) {
            PokeBot.log(Level.INFO, "Listener hook attached to bot");
        } else {
            PokeBot.log(Level.INFO, "Listener hook was unable to attach to the bot");
        }
        String network = globalSettings.getString("network");
        int port = globalSettings.getInt("port");
        if (network == null || network.isEmpty()) {
            network = "irc.esper.net";
        }
        if (port == 0 || port < 0) {
            port = 6667;
        }
        if (pass == null || pass.isEmpty()) {
            pass = globalSettings.getString("nick-pw");
        }
        PokeBot.log(Level.INFO, "Connecting to: " + network + ":" + port);
        try {
            driver.connect(network, port);
        } catch (NickAlreadyInUseException ex) {
            PokeBot.log(Level.SEVERE, "The nick is already taken");
            driver.changeNick(nick + "_");
            driver.connect(network, port);
            driver.sendMessage("chanserv", "ghost " + nick + " " + pass);
            driver.changeNick(nick);
            if (!globalSettings.getString("nick").equalsIgnoreCase(driver.getNick())) {
                PokeBot.log(Level.SEVERE, "Could not claim the nick " + nick);
            }
        }
        if (pass != null && !pass.isEmpty()) {
            driver.sendMessage("nickserv", "identify " + pass);
            PokeBot.log(Level.INFO, "Logging in to nickserv");
        }
        eventHandler.fireEvent(new ConnectionEvent());
        List<String> channels = globalSettings.getStringList("channels");
        if (channels != null && !channels.isEmpty()) {
            for (String chan : channels) {
                PokeBot.log(Level.INFO, "Joining " + chan);
                driver.joinChannel(chan);
            }
        }
        PokeBot.log(Level.INFO, "Initial loading complete, engaging listeners");
        eventHandler.startQueue();
        PokeBot.log(Level.INFO, "Starting keyboard listener");
        kblistener.start();
        PokeBot.log(Level.INFO, "All systems operational");
    }

    public EventHandler getEventHandler() {
        return eventHandler;
    }

    public ExtensionManager getExtensionManager() {
        return extensionManager;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public ConsoleReader getConsole() {
        return kblistener.getJLine();
    }

    public PermissionManager getPermManager() {
        return permManager;
    }

    public Settings getSettings() {
        return globalSettings;
    }

    public void shutdown() {
        eventHandler.stopRunner();
        driver.shutdown(true);
    }

    public Channel getChannel(String name) {
        if (channelCache.containsKey(name.toLowerCase())) {
            return channelCache.get(name.toLowerCase());
        }
        Channel newChan = new PokeBotChannel(driver, name);
        channelCache.put(name.toLowerCase(), newChan);
        return newChan;
    }

    public User getUser(String name) {
        org.pircbotx.User pircbotxUser = driver.getUser(name);
        if (userCache.contains(pircbotxUser)) {
            return userCache.get(pircbotxUser);
        }
        User newUser = new PokeBotUser(driver, name);
        userCache.put(pircbotxUser, newUser);
        return newUser;
    }

    public Bot getBot() {
        if (botUser == null) {
            botUser = new PokeBotBot(driver);
        }
        return botUser;
    }

}