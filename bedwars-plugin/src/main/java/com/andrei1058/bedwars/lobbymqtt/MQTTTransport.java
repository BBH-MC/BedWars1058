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

import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Broker URI and TLS settings for the lobby MQTT bridge.
 * Kept free of Bukkit classes so it can be tested on its own.
 */
public final class MQTTTransport {

    // Paho SSL property keys (see org.eclipse.paho.mqttv5.client.security.SSLSocketFactoryFactory)
    static final String SSL_PROTOCOL = "com.ibm.ssl.protocol";
    static final String SSL_TRUSTSTORE = "com.ibm.ssl.trustStore";
    static final String SSL_TRUSTSTORE_PASSWORD = "com.ibm.ssl.trustStorePassword";
    static final String SSL_TRUSTSTORE_TYPE = "com.ibm.ssl.trustStoreType";

    /**
     * "TLSv1.3" context: the JVM offers TLS 1.3 and 1.2 and negotiates the highest
     * the broker supports. (A "TLSv1.2" context would never offer 1.3.)
     * TLS 1.0/1.1 are disabled by the JVM's default jdk.tls.disabledAlgorithms.
     */
    static final String TLS_PROTOCOL = "TLSv1.3";
    static final String DEFAULT_TRUSTSTORE_TYPE = "PKCS12";
    static final int CONNECTION_TIMEOUT_SECONDS = 10;

    private MQTTTransport() {
    }

    /**
     * @param host the configured host: "host:port" (plain MQTT, as before) or a full URI
     *             such as "ssl://host:8883" or "tcp://host:1883".
     * @return the broker URI given to Paho.
     */
    public static String serverURI(String host) {
        String trimmed = host.trim();
        if (trimmed.contains("://")) {
            return trimmed;
        }
        return "tcp://" + trimmed;
    }

    public static boolean isTLS(String serverURI) {
        String lower = serverURI.toLowerCase();
        return lower.startsWith("ssl://") || lower.startsWith("wss://");
    }

    /**
     * Applies TLS settings to the connection options when the URI is ssl:// (or wss://).
     * Without a truststore, the JVM's default trust store is used.
     * Hostname verification is always kept on.
     *
     * @param truststore path to a truststore file, relative to the server's working directory; null or empty = none.
     * @return false if the TLS settings are unusable (missing truststore file); the caller should not connect.
     */
    public static boolean applyTLS(MqttConnectionOptions options, String serverURI, String truststore,
                                   String truststorePassword, String truststoreType, Logger logger) {
        boolean hasTruststore = truststore != null && !truststore.trim().isEmpty();

        if (!isTLS(serverURI)) {
            if (hasTruststore) {
                logger.warning("MQTT: a truststore is set but the broker URI " + serverURI
                        + " is not ssl://, so the truststore is ignored and the connection is not encrypted.");
            }
            return true;
        }

        Properties ssl = new Properties();
        ssl.setProperty(SSL_PROTOCOL, TLS_PROTOCOL);

        if (hasTruststore) {
            File file = new File(truststore.trim());
            if (!file.isFile()) {
                logger.severe("MQTT Setup Failed: truststore file not found: " + file.getAbsolutePath());
                return false;
            }
            String type = truststoreType == null || truststoreType.trim().isEmpty()
                    ? DEFAULT_TRUSTSTORE_TYPE : truststoreType.trim();
            ssl.setProperty(SSL_TRUSTSTORE, file.getAbsolutePath());
            ssl.setProperty(SSL_TRUSTSTORE_TYPE, type);
            if (truststorePassword != null && !truststorePassword.isEmpty()) {
                ssl.setProperty(SSL_TRUSTSTORE_PASSWORD, truststorePassword);
            }
            logger.info("MQTT: using TLS with truststore " + file.getAbsolutePath() + " (" + type + ").");
        } else {
            logger.info("MQTT: using TLS with the JVM default trust store.");
        }

        options.setSSLProperties(ssl);
        options.setHttpsHostnameVerificationEnabled(true);
        return true;
    }

    /**
     * Creates a client and connects it. On failure, logs why (never the passwords),
     * closes the client and returns null.
     */
    public static MqttClient connect(String host, String clientID, String username, String password,
                                     String truststore, String truststorePassword, String truststoreType,
                                     MqttCallback callback, Logger logger) {
        String serverURI = serverURI(host);
        MqttClient client = null;
        try {
            MqttConnectionOptions connOpts = new MqttConnectionOptions();
            connOpts.setConnectionTimeout(CONNECTION_TIMEOUT_SECONDS);

            // TLS (ssl://) settings
            if (!applyTLS(connOpts, serverURI, truststore, truststorePassword, truststoreType, logger)) {
                return null;
            }

            // Setup user/pass if appropriate
            if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                connOpts.setUserName(username);
                connOpts.setPassword(password.getBytes(StandardCharsets.UTF_8));
            }

            client = new MqttClient(serverURI, clientID, new MemoryPersistence());
            client.setCallback(callback);
            client.connect(connOpts);
            return client;
        } catch (MqttException e) {
            logger.severe("MQTT: failed to connect to MQTT broker " + serverURI
                    + " (reason code " + e.getReasonCode() + "). " + e.getMessage());
            logger.severe("Cause: " + e.getCause());
        } catch (IllegalArgumentException e) {
            // Paho rejects malformed or unsupported broker URIs this way
            logger.severe("MQTT: invalid broker URI " + serverURI + ". " + e.getMessage());
        }
        close(client);
        return null;
    }

    /** Disconnects (if connected) and closes a client, ignoring errors. Null-safe. */
    public static void close(MqttClient client) {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect(2000);
            }
        } catch (MqttException ignored) {
        }
        try {
            client.close(true);
        } catch (MqttException ignored) {
        }
    }
}
