/*******************************************************************************
 * Copyright (c) 2020 Sierra Wireless and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *     Sierra Wireless - initial API and implementation
 *******************************************************************************/
package org.eclipse.leshan.client.resource;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.leshan.client.resource.listener.ObjectListener;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link ObjectListener} which is able to store notification during transaction and raise all grouped event at the
 * end of the transaction.
 * <p>
 * This class is not threadsafe.
 */
public class TransactionalObjectListener implements ObjectListener {

    private static Logger LOG = LoggerFactory.getLogger(TransactionalObjectListener.class);

    protected int currentLevel = 0;
    protected List<Integer> instancesAdded = new ArrayList<>();
    protected List<Integer> instancesRemoved = new ArrayList<>();
    protected List<LwM2mPath> resourcesChanged = new ArrayList<>();

    protected LwM2mObjectEnabler objectEnabler;
    protected List<ObjectListener> innerListeners = new ArrayList<ObjectListener>();

    public TransactionalObjectListener(LwM2mObjectEnabler objectEnabler) {
        this.objectEnabler = objectEnabler;
    }

    public void addListener(ObjectListener listener) {
        innerListeners.add(listener);
    }

    public void removeListener(ObjectListener listener) {
        innerListeners.remove(listener);
    }

    /**
     * Open a transaction with a given level. Same level must be used to open and close a transaction.
     * <p>
     * a transaction can be started in another transaction but in that case the inner transaction should use a higher
     * level.
     *
     * @param level the transaction level, a not 0 positive integer.
     */
    public void beginTransaction(byte level) {
        if (level <= 0) {
            throw new IllegalArgumentException("level must be > 0.");
        }
        if (currentLevel == 0) {
            currentLevel = level;
        } else if (level <= currentLevel) {
            LOG.warn(
                    "Begin transaction with a lower level {} than the current one {} for object {}, this could bring to unexpected behavior",
                    objectEnabler.getId(), currentLevel, level);
            currentLevel = level;
        }
        // else if level > currentLevel
        // there is nothing to do has this transaction is inner a another one.
    }

    public void endTransaction(byte level) {
        if (currentLevel == level) {
            try {
                fireStoredEvents();
            } catch (Exception e) {
                LOG.warn("Exception raised when we fired Event about object {}", objectEnabler.getId(), e);
            }
            instancesAdded.clear();
            instancesRemoved.clear();
            resourcesChanged.clear();
            currentLevel = 0;
        }
    }

    protected boolean inTransaction() {
        return currentLevel > 0;
    }

    protected void fireStoredEvents() {
        if (!instancesAdded.isEmpty())
            fireObjectInstancesAdded(toIntArray(instancesAdded));
        if (!instancesRemoved.isEmpty())
            fireObjectInstancesRemoved(toIntArray(instancesRemoved));
        if (!resourcesChanged.isEmpty())
            fireResourcesChanged(resourcesChanged.toArray(new LwM2mPath[resourcesChanged.size()]));
    }

    @Override
    public void objectInstancesAdded(LwM2mObjectEnabler object, int... instanceIds) {
        if (!inTransaction()) {
            fireObjectInstancesAdded(instanceIds);
        } else {
            // store additions
            for (int instanceId : instanceIds) {
                if (instancesRemoved.contains(instanceId)) {
                    instancesRemoved.remove(instanceId);
                } else if (!instancesAdded.contains(instanceId)) {
                    instancesAdded.add(instanceId);
                }
            }
        }
    }

    @Override
    public void objectInstancesRemoved(LwM2mObjectEnabler object, int... instanceIds) {
        if (!inTransaction()) {
            fireObjectInstancesRemoved(instanceIds);
        } else {
            // store deletion
            for (int instanceId : instanceIds) {
                if (instancesAdded.contains(instanceId)) {
                    instancesAdded.remove(instanceId);
                } else if (!instancesRemoved.contains(instanceId)) {
                    instancesRemoved.add(instanceId);
                }
            }
        }
    }

    @Override
    public void resourceChanged(LwM2mPath... paths) {
        if (!inTransaction()) {
            fireResourcesChanged(paths);
        } else {
            for (LwM2mPath path : paths) {
                if (!resourcesChanged.contains(path)) {
                    resourcesChanged.add(path);
                }
            }
        }
    }

    protected int[] toIntArray(List<Integer> list) {
        int[] ret = new int[list.size()];
        int i = 0;
        for (Integer e : list)
            ret[i++] = e;
        return ret;
    }

    protected void fireObjectInstancesAdded(int... instanceIds) {
        for (ObjectListener listener : innerListeners) {
            listener.objectInstancesAdded(objectEnabler, instanceIds);
        }
    }

    protected void fireObjectInstancesRemoved(int... instanceIds) {
        for (ObjectListener listener : innerListeners) {
            listener.objectInstancesRemoved(objectEnabler, instanceIds);
        }
    }

    protected void fireResourcesChanged(LwM2mPath... path) {
        for (ObjectListener listener : innerListeners) {
            listener.resourceChanged(path);
        }
    }
}
