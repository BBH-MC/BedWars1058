/*
 * BedWars1058 - A bed wars mini-game.
 * Copyright (C) 2021 Andrei Dascălu
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Contact e-mail: andrew.dascalu@gmail.com
 */

package com.andrei1058.bedwars.lobbymqtt;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.arena.Arena;
import com.andrei1058.bedwars.lobbysocket.LoadedUser;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.logging.Logger;

public class LobbyMQTT {
    private static final MqttProperties msgPropertyDefault = new MqttProperties();

    private static String mqttHost;
    private static String mqttUsername;
    private static String mqttPassword;
    private static String mqttTruststore;
    private static String mqttTruststorePassword;
    private static String mqttTruststoreType;

    private static String topicPrefix;
    private static String clientID;

    private static MqttClient mqtt;

    private static Logger logger;

    public LobbyMQTT(String host, String tPrefix, String username, String password, String serverID) {
        this(host, tPrefix, username, password, serverID, null, null, null);
    }

    /**
     * @param host               "host:port" for plain MQTT (tcp:// is added), or a full URI such as "ssl://host:8883".
     * @param truststore         optional truststore file for ssl://, relative to the server's working directory.
     * @param truststorePassword optional truststore password.
     * @param truststoreType     optional truststore type, PKCS12 if empty.
     */
    public LobbyMQTT(String host, String tPrefix, String username, String password, String serverID,
                     String truststore, String truststorePassword, String truststoreType) {
        logger = BedWars.plugin.getLogger();

        // Check nulls
        if (host == null || tPrefix == null || serverID == null) {
            logger.severe("MQTT Setup Failed: Mandatory MQTT parameters missing.");
            return;
        }

        topicPrefix = tPrefix;
        clientID = serverID;

        // Set up the MQTT client
        mqttHost = host;
        mqttUsername = username;
        mqttPassword = password;
        mqttTruststore = truststore;
        mqttTruststorePassword = truststorePassword;
        mqttTruststoreType = truststoreType;
        AttemptMQTTConnection();
    }

    private void AttemptMQTTConnection() {
        MqttClient mqtt1;
        String serverURI = MQTTTransport.serverURI(mqttHost);

        try {
            // Connection options
            MqttConnectionOptions connOpts = new MqttConnectionOptions();

            // TLS (ssl://) settings
            if (!MQTTTransport.applyTLS(connOpts, serverURI, mqttTruststore, mqttTruststorePassword, mqttTruststoreType, logger)) {
                mqtt = null;
                return;
            }

            mqtt1 = new MqttClient(serverURI, clientID, new MemoryPersistence());

            // Setup user/pass if appropriate
            if (mqttUsername != null && !mqttUsername.isEmpty() && mqttPassword != null && !mqttPassword.isEmpty()) {
                connOpts.setUserName(mqttUsername);
                connOpts.setPassword(mqttPassword.getBytes(StandardCharsets.UTF_8));
            }

            // Set up the callbacks
            mqtt1.setCallback(new LobbyMQTTCallback());

            // Connect
            mqtt1.connect(connOpts);
        } catch (MqttException e) {
            logger.severe("MQTT Setup Failed: Failed to connect to MQTT broker " + serverURI
                    + " (reason code " + e.getReasonCode() + "). " + e.getMessage());
            logger.severe("Cause: " + e.getCause());
            mqtt1 = null;
        } catch (IllegalArgumentException e) {
            // Paho rejects malformed or unsupported broker URIs this way
            logger.severe("MQTT Setup Failed: invalid broker URI " + serverURI + ". " + e.getMessage());
            mqtt1 = null;
        }

        mqtt = mqtt1;

        if (mqtt == null) {
            logger.severe("MQTT Setup Failed: Unknown error.");
        }
    }

    private static String getArenaTopic(IArena arena, String property) {
        return topicPrefix + "/arenas/" + arena.getGroup() + "/" + arena.getWorldName() + "/" + property;
    }
    private static String getArenaTopic(String arenaGroup, String arenaID, String property) {
        return topicPrefix + "/arenas/" + arenaGroup + "/" + arenaID + "/" + property;
    }

    public static void publishArenaStatus(IArena arena) {
        if (mqtt == null || !mqtt.isConnected()) {
            return;
        }

        try {
            // Name
            MqttMessage nameMsg = new MqttMessage(arena.getDisplayName().getBytes(StandardCharsets.UTF_8), 1, false, msgPropertyDefault);
            mqtt.publish(getArenaTopic(arena, "name"), nameMsg);

            // Server
            MqttMessage serverMsg = new MqttMessage(clientID.toUpperCase().getBytes(StandardCharsets.UTF_8), 1, false, msgPropertyDefault);
            mqtt.publish(getArenaTopic(arena, "server"), serverMsg);

            // Status
            MqttMessage statusMsg = new MqttMessage(arena.getStatus().toString().toUpperCase().getBytes(StandardCharsets.UTF_8), 1, false, msgPropertyDefault);
            mqtt.publish(getArenaTopic(arena, "status"), statusMsg);

            // Current Players
            MqttMessage cpMessage = new MqttMessage(Integer.toString(arena.getPlayers().size()).getBytes(StandardCharsets.UTF_8), 1, false, msgPropertyDefault);
            mqtt.publish(getArenaTopic(arena, "currentPlayers"), cpMessage);

            // Max Players
            MqttMessage mpMessage = new MqttMessage(Integer.toString(arena.getMaxPlayers()).getBytes(StandardCharsets.UTF_8), 1, false, msgPropertyDefault);
            mqtt.publish(getArenaTopic(arena, "maxPlayers"), mpMessage);

            // Max In Team
            MqttMessage mitMessage = new MqttMessage(Integer.toString(arena.getMaxInTeam()).getBytes(StandardCharsets.UTF_8), 1, false, msgPropertyDefault);
            mqtt.publish(getArenaTopic(arena, "maxInTeam"), mitMessage);

            // Spectate Status
            MqttMessage specMessage = new MqttMessage(Boolean.toString(arena.isAllowSpectate()).getBytes(StandardCharsets.UTF_8), 1, false, msgPropertyDefault);
            mqtt.publish(getArenaTopic(arena, "spectate"), specMessage);

        } catch (MqttException e) {
            logger.severe("Failed to publish arena status to MQTT. " + e.getMessage());
        }
    }

    private static String getPlayerTopic(Player player, String property) {
        return topicPrefix + "/players/" + player.getUniqueId().toString() + "/" + property;
    }

    public static void publishPlayerStatus(Player player) {
        if (mqtt == null || !mqtt.isConnected()) {
            return;
        }

        if (player != null && player.isOnline()) {
            IArena arena = Arena.getArenaByPlayer(player);
            try {
                // Player Name
                MqttMessage nameMsg = new MqttMessage(player.getName().getBytes(StandardCharsets.UTF_8), 1, false, msgPropertyDefault);
                mqtt.publish(getPlayerTopic(player, "name"), nameMsg);

                // Server Name
                MqttMessage serverMsg = new MqttMessage(clientID.getBytes(StandardCharsets.UTF_8), 1, false, msgPropertyDefault);
                mqtt.publish(getPlayerTopic(player, "server"), serverMsg);

                // Arena ID
                MqttMessage arenaMsg = new MqttMessage(arena.getWorldName().getBytes(StandardCharsets.UTF_8), 1, false, msgPropertyDefault);
                mqtt.publish(getPlayerTopic(player, "status"), arenaMsg);

            } catch (MqttException e) {
                logger.severe("Failed to publish player status to MQTT. " + e.getMessage());
            }
        }
    }

    private static String getArenaJoinTopic(IArena arena) {
        return topicPrefix + "/arenas/" + arena.getGroup() + "/" + arena.getWorldName() + "/join/+";
    }
    private static String getArenaJoinTopic(String arenaGroup, String arenaID) {
        return topicPrefix + "/arenas/" + arenaGroup + "/" + arenaID + "/join/+";
    }
    private static String getArenaJoinTopicWithPlayer(String playerUUID, String arenaGroup, String arenaID) {
        return topicPrefix + "/arenas/" + arenaGroup + "/" + arenaID + "/join/" + playerUUID;
    }

    public static void subscribeArenaJoins(IArena arena) {
        if (mqtt == null || !mqtt.isConnected()) {
            return;
        }
        try {
            mqtt.subscribe(getArenaJoinTopic(arena), 2);
        } catch (MqttException e) {
            logger.severe("Failed to subscribe to arena status. " + e.getMessage());
        }
    }

    public static void unsubscribeArenaJoins(String arenaGroup, String arenaID) {
        if (mqtt == null || !mqtt.isConnected()) {
            return;
        }
        try {
            mqtt.unsubscribe(getArenaJoinTopic(arenaGroup, arenaID));
        } catch (MqttException e) {
            logger.severe("Failed to subscribe to arena status. " + e.getMessage());
        }
    }

    public static void clearArenaJoin(String playerUUID, String arenaID, String arenaGroup) {
        if (mqtt == null || !mqtt.isConnected()) {
            return;
        }
        try {
            MqttMessage zeroMsg = new MqttMessage();

            // Join Request
            mqtt.publish(getArenaJoinTopicWithPlayer(playerUUID, arenaGroup, arenaID), zeroMsg);

        } catch (MqttException e) {
            logger.severe("Failed to publish player status to MQTT. " + e.getMessage());
        }
    }

    public static void unpublishPlayerStatus(Player player) {
        if (mqtt == null || !mqtt.isConnected()) {
            return;
        }
        try {
            MqttMessage zeroMsg = new MqttMessage();

            // Player Name
            mqtt.publish(getPlayerTopic(player, "name"), zeroMsg);

            // Server Name
            mqtt.publish(getPlayerTopic(player, "server"), zeroMsg);

            // Arena ID
            mqtt.publish(getPlayerTopic(player, "status"), zeroMsg);

        } catch (MqttException e) {
            logger.severe("Failed to publish player status to MQTT. " + e.getMessage());
        }
    }

    public static void unpublishArenaStatus(String arenaGroup, String arenaID) {
        if (mqtt == null || !mqtt.isConnected()) {
            return;
        }
        try {
            MqttMessage zeroMsg = new MqttMessage();

            // Name
            mqtt.publish(getArenaTopic(arenaGroup, arenaID, "name"), zeroMsg);

            // Server
            mqtt.publish(getArenaTopic(arenaGroup, arenaID, "server"), zeroMsg);

            // Status
            mqtt.publish(getArenaTopic(arenaGroup, arenaID, "status"), zeroMsg);

            // Current Players
            mqtt.publish(getArenaTopic(arenaGroup, arenaID, "currentPlayers"), zeroMsg);

            // Max Players
            mqtt.publish(getArenaTopic(arenaGroup, arenaID, "maxPlayers"), zeroMsg);

            // Max In Team
            mqtt.publish(getArenaTopic(arenaGroup, arenaID, "maxInTeam"), zeroMsg);

            // Spectate Status
            mqtt.publish(getArenaTopic(arenaGroup, arenaID, "spectate"), zeroMsg);

        } catch (MqttException e) {
            logger.severe("Failed to publish arena status to MQTT. " + e.getMessage());
        }
    }

    class LobbyMQTTCallback implements MqttCallback {
        private boolean reconnectionRequired = false;
        private final BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        private final BukkitTask reconnectSchedule = scheduler.runTaskTimerAsynchronously(BedWars.plugin, this::reconnectTask, 1200L, 1200L);

        private void reconnectTask() {
            if (!reconnectionRequired) {
                return;
            }

            if (mqtt.isConnected()) {
                return;
            }

            AttemptMQTTConnection();
        }

        @Override
        public void disconnected(MqttDisconnectResponse mqttDisconnectResponse) {
            // Reconnect task
            reconnectionRequired = true;
            logger.warning("MQTT Server has disconnected! Attempting reconnection in 60 seconds!");
        }

        @Override
        public void mqttErrorOccurred(MqttException e) {
            logger.severe("MQTT Error: " + e.getMessage());
        }

        @Override
        public void messageArrived(String s, MqttMessage mqttMessage) {
            String topic = s.substring(0, topicPrefix.length());
            String[] segments = s.substring(topicPrefix.length()).split("/");

            if (!topic.equals(topicPrefix) || !Objects.equals(segments[1], "arenas") || !Objects.equals(segments[4], "join")) {
                logger.warning("Received MQTT Message with invalid topic: " + s);
                return;
            }

            byte[] payload = mqttMessage.getPayload();

            if (payload == null || payload.length == 0) {
                return;
            }

            ByteArrayDataInput in = ByteStreams.newDataInput(mqttMessage.getPayload());

            String language = in.readUTF();
            String target = in.readUTF();

            clearArenaJoin(segments[5], segments[3], segments[2]);

            new LoadedUser(segments[5], segments[3], language, target);
        }

        @Override
        public void deliveryComplete(IMqttToken iMqttToken) {}

        @Override
        public void connectComplete(boolean b, String s) {
            logger.info("MQTT Connection successful: " + s);
            reconnectionRequired = false;
        }

        @Override
        public void authPacketArrived(int i, MqttProperties mqttProperties) {}
    }
}
