package org.eclipse.leshan.client.object;

import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.client.servers.ServerIdentity;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.response.ReadResponse;

import java.util.Arrays;
import java.util.List;

public class ConnectivityMonitoring extends BaseInstanceEnabler {

    private static final List<Integer> supportedResources = Arrays.asList(0, 1);

    private final Integer networkBearer = 22;
    private final Integer availableNetworkBearer = 0;

    public ConnectivityMonitoring() {

    }

    @Override
    public ReadResponse read(ServerIdentity identity, int resourceid) {
        switch (resourceid) {
            case 0:
                return ReadResponse.success(resourceid, networkBearer);
            case 1:
                return ReadResponse.success(resourceid, availableNetworkBearer);
            default:
                return super.read(identity, resourceid);
        }
    }


    @Override
    public List<Integer> getAvailableResourceIds(ObjectModel model) {
        return supportedResources;
    }
}
