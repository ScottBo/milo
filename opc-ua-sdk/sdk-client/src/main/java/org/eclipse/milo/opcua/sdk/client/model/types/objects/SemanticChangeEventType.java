/*
 * Copyright (c) 2019 the Eclipse Milo Authors
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.milo.opcua.sdk.client.model.types.objects;

import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.sdk.client.model.types.variables.PropertyType;
import org.eclipse.milo.opcua.sdk.core.QualifiedProperty;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.structured.SemanticChangeStructureDataType;

public interface SemanticChangeEventType extends BaseModelChangeEventType {
    QualifiedProperty<SemanticChangeStructureDataType[]> CHANGES = new QualifiedProperty<>(
        "http://opcfoundation.org/UA/",
        "Changes",
        ExpandedNodeId.parse("nsu=http://opcfoundation.org/UA/;i=897"),
        ValueRanks.OneDimension,
        SemanticChangeStructureDataType[].class
    );

    CompletableFuture<? extends PropertyType> getChangesNode();

    CompletableFuture<SemanticChangeStructureDataType[]> getChanges();

    CompletableFuture<StatusCode> setChanges(SemanticChangeStructureDataType[] value);
}
