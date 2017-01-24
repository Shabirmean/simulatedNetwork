package socs.network.node;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import socs.network.util.Configuration;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.Vector;


public class RouterManager {
    private static final Log log = LogFactory.getLog(RouterManager.class);
    private static RouterManager routerManager;

    private RouterManager() {
    }

    public static RouterManager getInstance() {
        if (routerManager == null) {
            routerManager = new RouterManager();
        }
        return routerManager;
    }

    public void init(String[] args) {
        if (args.length != 1) {
            log.info("USAGE: <JAR_FILE> <CONF_FILE_PATH>");
            System.exit(1);
        }

        try {
            String routerIP = determineIPAddress();

            Router router = new Router(new Configuration(args[0]), routerIP);
            log.info("The simulated address of this router is: " + router.getRd().simulatedIPAddress);

            router.startServer();
            Thread.sleep(1000);
            router.terminal();

        } catch (SocketException e) {
            log.error("An error occurred whilst trying to get the IP address of the network device.", e);
        } catch (InterruptedException e) {
            log.error("An error occurred whilst attempting to sleep thread.", e);
        }
    }

    private static String determineIPAddress() throws SocketException {
        int ipCount = 0;
        Vector<String> ipVector = new Vector<>();
        Enumeration e = NetworkInterface.getNetworkInterfaces();

        log.info("---------------------------------------------");
        log.info("IP Addresses of all interfaces on this device");
        log.info("---------------------------------------------");

        while (e.hasMoreElements()) {
            NetworkInterface n = (NetworkInterface) e.nextElement();
            Enumeration ee = n.getInetAddresses();
            while (ee.hasMoreElements()) {
                InetAddress i = (InetAddress) ee.nextElement();
                String ipAddress = i.getHostAddress();
                ipVector.add(ipAddress);

                log.info((ipCount++) + " : " + n.getDisplayName() + " -->   " + ipAddress);
            }
        }
        log.info("---------------------------------------------");
        System.out.print("\nPlease select the IP address to be used: ");

        Scanner scanner = new Scanner(System.in);
        int ipIndex = scanner.nextInt();
        String selectedIP = ipVector.get(ipIndex);

        System.out.print("\n");
        log.info("You selected: " + selectedIP);
        log.info("---------------------------------------------");

        return selectedIP;
    }
}
