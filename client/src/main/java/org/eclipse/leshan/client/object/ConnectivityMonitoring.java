package org.eclipse.leshan.client.object;

import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.client.servers.ServerIdentity;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.response.ReadResponse;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

public class ConnectivityMonitoring extends BaseInstanceEnabler {

    private static final List<Integer> supportedResources = Arrays.asList(0, 1);

    private final Integer networkBearer = 21;
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
            case 4:
                return ReadResponse.success(resourceid, getIpAddress());
            default:
                return super.read(identity, resourceid);
        }
    }

    private String getIpAddress() {
        String ipAddress = "";
        InetAddress localhost;
        try {
            localhost = InetAddress.getLocalHost();
            ipAddress = localhost.getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return ipAddress;
    }

    @Override
    public List<Integer> getAvailableResourceIds(ObjectModel model) {
        return supportedResources;
    }
}
