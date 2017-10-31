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

package org.eclipse.milo.opcua.sdk.client.session.states;

import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.sdk.client.OpcUaSession;
import org.eclipse.milo.opcua.sdk.client.session.SessionFsm;
import org.eclipse.milo.opcua.sdk.client.session.events.CloseSessionEvent;
import org.eclipse.milo.opcua.sdk.client.session.events.CreateSessionEvent;
import org.eclipse.milo.opcua.sdk.client.session.events.Event;
import org.eclipse.milo.opcua.sdk.client.session.events.TransferFailureEvent;
import org.eclipse.milo.opcua.sdk.client.session.events.TransferSuccessEvent;

import static org.eclipse.milo.opcua.stack.core.util.FutureUtils.complete;

public class Retransferring extends AbstractState implements State {

    private CompletableFuture<OpcUaSession> sessionFuture;

    @Override
    public CompletableFuture<OpcUaSession> getSessionFuture() {
        return sessionFuture;
    }

    @Override
    public void onExternalTransition(SessionFsm fsm, State from, Event event) {
        sessionFuture = from.getSessionFuture();
    }

    @Override
    public void onInternalTransition(SessionFsm fsm, Event event) {
        if (event instanceof CreateSessionEvent) {
            CreateSessionEvent e = (CreateSessionEvent) event;

            complete(e.getSessionFuture()).with(sessionFuture);
        }
    }

    @Override
    public State execute(SessionFsm fsm, Event event) {
        if (event instanceof TransferSuccessEvent) {
            OpcUaSession session = ((TransferSuccessEvent) event).getSession();

            fsm.getClient().getConfig().getExecutor()
                .submit(() -> sessionFuture.complete(session));

            return new Active();
        } else if (event instanceof TransferFailureEvent) {
            Throwable failure = ((TransferFailureEvent) event).getFailure();

            sessionFuture.completeExceptionally(failure);

            return new Recreating();
        } else if (event instanceof CreateSessionEvent) {
            return this;
        } else if (event instanceof CloseSessionEvent) {
            // CloseSessionEvent preempted our receipt of a TransferFailureEvent or TransferSuccessEvent.
            // Closing state will receive one of those events and execute the appropriate action.
            return new Closing();
        } else {
            return this;
        }
    }

}
