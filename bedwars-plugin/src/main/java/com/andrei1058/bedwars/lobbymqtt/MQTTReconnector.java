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

import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

/**
 * Schedules MQTT reconnection attempts: at most one pending attempt at a time,
 * with exponential backoff, reset on a successful connect, and stopped on plugin disable.
 * Kept free of Bukkit classes so it can be tested on its own.
 */
public final class MQTTReconnector {

    /** Runs a task once after a delay; returns an action that cancels it. */
    public interface Scheduler {
        Runnable schedule(Runnable task, long delayMillis);
    }

    private final Scheduler scheduler;
    private final BooleanSupplier attempt;
    private final Logger logger;
    private final long initialDelayMillis;
    private final long maxDelayMillis;

    private long nextDelayMillis;
    private Runnable cancelPending;
    private boolean stopped;

    /**
     * @param attempt tries to connect; returns true when connected.
     */
    public MQTTReconnector(Scheduler scheduler, BooleanSupplier attempt, Logger logger,
                           long initialDelayMillis, long maxDelayMillis) {
        this.scheduler = scheduler;
        this.attempt = attempt;
        this.logger = logger;
        this.initialDelayMillis = initialDelayMillis;
        this.maxDelayMillis = maxDelayMillis;
        this.nextDelayMillis = initialDelayMillis;
    }

    /** Schedules a reconnection attempt, unless one is already pending or the reconnector is stopped. */
    public synchronized void schedule() {
        if (stopped || cancelPending != null) {
            return;
        }
        long delay = nextDelayMillis;
        nextDelayMillis = Math.min(nextDelayMillis * 2, maxDelayMillis);
        logger.warning("MQTT: reconnecting in " + (delay / 1000.0) + " seconds.");
        cancelPending = scheduler.schedule(this::run, delay);
    }

    private void run() {
        synchronized (this) {
            cancelPending = null;
            if (stopped) {
                return;
            }
        }
        boolean connected;
        try {
            connected = attempt.getAsBoolean();
        } catch (RuntimeException e) {
            logger.severe("MQTT: reconnection attempt failed: " + e);
            connected = false;
        }
        if (connected) {
            connected();
        } else {
            schedule();
        }
    }

    /** Call on a successful connect: cancels any pending attempt and resets the backoff. */
    public synchronized void connected() {
        nextDelayMillis = initialDelayMillis;
        cancelPendingLocked();
    }

    /** Call on plugin disable: cancels any pending attempt and refuses new ones. */
    public synchronized void stop() {
        stopped = true;
        cancelPendingLocked();
    }

    public synchronized boolean isPending() {
        return cancelPending != null;
    }

    private void cancelPendingLocked() {
        if (cancelPending != null) {
            cancelPending.run();
            cancelPending = null;
        }
    }
}
