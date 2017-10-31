/*
 * Copyright (c) 2017 Kevin Herron
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *   http://www.eclipse.org/org/documents/edl-v10.html.
 */

package org.eclipse.milo.opcua.sdk.client.session;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.OpcUaSession;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.session.events.CloseSessionEvent;
import org.eclipse.milo.opcua.sdk.client.session.events.CreateSessionEvent;
import org.eclipse.milo.opcua.sdk.client.session.events.Event;
import org.eclipse.milo.opcua.sdk.client.session.states.Active;
import org.eclipse.milo.opcua.sdk.client.session.states.Inactive;
import org.eclipse.milo.opcua.sdk.client.session.states.State;
import org.eclipse.milo.opcua.stack.core.util.ExecutionQueue;
import org.eclipse.milo.opcua.stack.core.util.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.collect.Lists.newCopyOnWriteArrayList;

public class SessionFsm {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final List<SessionActivityListener> listeners = newCopyOnWriteArrayList();
    private final AtomicReference<State> state = new AtomicReference<>(new Inactive());
    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);

    private final ExecutionQueue notificationQueue;

    private final OpcUaClient client;

    public SessionFsm(OpcUaClient client) {
        this.client = client;

        notificationQueue = new ExecutionQueue(client.getConfig().getExecutor());
    }

    public CompletableFuture<OpcUaSession> openSession() {
        CompletableFuture<OpcUaSession> sessionFuture = new CompletableFuture<>();

        fireEvent(new CreateSessionEvent(sessionFuture));

        return sessionFuture;
    }

    public CompletableFuture<Unit> closeSession() {
        CompletableFuture<Unit> closeFuture = new CompletableFuture<>();

        fireEvent(new CloseSessionEvent(closeFuture));

        return closeFuture;
    }

    public CompletableFuture<OpcUaSession> getSession() {
        readWriteLock.readLock().lock();
        try {
            return state.get().getSessionFuture();
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public void addListener(SessionActivityListener listener) {
        listeners.add(listener);
    }

    public void removeListener(SessionActivityListener listener) {
        listeners.remove(listener);
    }

    public OpcUaClient getClient() {
        return client;
    }

    public State getState() {
        return state.get();
    }

    public State fireEvent(Event event) {
        if (readWriteLock.writeLock().isHeldByCurrentThread()) {
            try {
                return client.getConfig().getExecutor()
                    .submit(() -> fireEvent(event)).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        } else {
            readWriteLock.writeLock().lock();

            try {
                State currentState = state.get();
                State nextState = currentState.execute(this, event);
                state.set(nextState);

                logger.info(
                    "S({}) x E({}) = S'({})",
                    currentState.getClass().getSimpleName(),
                    event.getClass().getSimpleName(),
                    nextState.getClass().getSimpleName()
                );

                if (currentState.getClass() == nextState.getClass()) {
                    nextState.onInternalTransition(this, event);
                } else {
                    nextState.onExternalTransition(this, currentState, event);

                    if (nextState instanceof Active) {
                        // transition from non-Active to Active
                        notifySessionActive(((Active) nextState).getSession());
                    } else if (currentState instanceof Active) {
                        // transition from Active to non-Active
                        notifySessionInactive(((Active) currentState).getSession());
                    }
                }


                return nextState;
            } finally {
                readWriteLock.writeLock().unlock();
            }
        }
    }

    private void notifySessionActive(OpcUaSession session) {
        notificationQueue.submit(() ->
            listeners.forEach(listener -> {
                try {
                    listener.onSessionActive(session);
                } catch (Throwable t) {
                    logger.warn("Uncaught Throwable notifying listener: {}", listener, t);
                }
            })
        );
    }

    private void notifySessionInactive(OpcUaSession session) {
        notificationQueue.submit(() ->
            listeners.forEach(listener -> {
                try {
                    listener.onSessionInactive(session);
                } catch (Throwable t) {
                    logger.warn("Uncaught Throwable notifying listener: {}", listener, t);
                }
            })
        );
    }

}
