package org.eclipse.leshan.client.object;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.net.SocketException;

import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.client.servers.ServerIdentity;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.request.argument.Arguments;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.response.WriteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyWlanConnectivity extends BaseInstanceEnabler {

    private static final Logger LOG = LoggerFactory.getLogger(MyWlanConnectivity.class);

    private static final List<Integer> supportedResources = Arrays.asList(0, 1, 2, 3, 9, 10, 11, 13, 14, 15, 16, 17, 18,
            19, 20, 21);

    public MyWlanConnectivity() {

    }

    @Override
    public ReadResponse read(ServerIdentity identity, int resourceid) {

        if (!identity.isSystem())
            LOG.info("Read on Device resource /{}/{}/{}", getModel().id, getId(), resourceid);
        switch (resourceid) {
            case 0:
                return ReadResponse.success(resourceid, getInterfaceName());
            case 1:
                return ReadResponse.success(resourceid, true);
            case 2:
                return ReadResponse.success(resourceid, true);
            case 5:
                return ReadResponse.success(resourceid, getSSIDName());

            default:
                return super.read(identity, resourceid);
        }
    }

    @Override
    public ExecuteResponse execute(ServerIdentity identity, int resourceid, Arguments arguments) {
        String withArguments = "";
        if (!arguments.isEmpty())
            withArguments = " with arguments " + arguments;
        LOG.info("Execute on Device resource /{}/{}/{} {}", getModel().id, getId(), resourceid, withArguments);

        if (resourceid == 4) {
            new Timer("Reboot Lwm2mClient").schedule(new TimerTask() {
                @Override
                public void run() {
                    getLwM2mClient().stop(true);
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                    }
                    getLwM2mClient().start();
                }
            }, 500);
        }
        return ExecuteResponse.success();
    }

    @Override
    public WriteResponse write(ServerIdentity identity, boolean replace, int resourceid, LwM2mResource value) {
        LOG.info("Write on Device resource /{}/{}/{}", getModel().id, getId(), resourceid);

        switch (resourceid) {
            case 13:
                return WriteResponse.notFound();
            case 14:

                fireResourceChange(resourceid);
                return WriteResponse.success();
            case 15:

                fireResourceChange(resourceid);
                return WriteResponse.success();
            default:
                return super.write(identity, replace, resourceid, value);
        }
    }

    public String getSSIDName() {
        String ssid = null;

        List<NetworkInterface> interfaces;
        try {
            interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());

            for (NetworkInterface iface : interfaces) {
                byte[] ssidBytes = iface.getHardwareAddress();
                if (ssidBytes == null || ssidBytes.length < 1) {
                    continue;
                }
                ssid = new String(ssidBytes);
                break;
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return ssid;
    }

    public String getInterfaceName() {

        String os = System.getProperty("os.name");
        String interfaceName = null;
        if (os.startsWith("Mac")) {
            Process process;
            try {
                process = Runtime.getRuntime().exec("networksetup -listallhardwareports");

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Wi-Fi")) {
                        interfaceName = line.split("\\s+")[1];
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (os.startsWith("Linux")) {
            try {
                Process process = Runtime.getRuntime().exec("nmcli device");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("wifi")) {
                        interfaceName = line.split("\\s+")[0];
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Error: Unsupported operating system");

        }
        return interfaceName;

    }

    @Override
    public List<Integer> getAvailableResourceIds(ObjectModel model) {
        return supportedResources;
    }

}
