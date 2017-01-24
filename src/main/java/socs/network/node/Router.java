package socs.network.node;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import socs.network.util.Configuration;
import socs.network.util.RouterConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.concurrent.ThreadLocalRandom;


public class Router {
    private final Log log = LogFactory.getLog(Router.class);
    private RouterServer routerServer;

    protected LinkStateDatabase lsd;
    private RouterDescription rd = new RouterDescription();

    //assuming that all routers are with 4 ports
    private Link[] ports = new Link[RouterConstants.MAXIMUM_NO_OF_PORTS];
    private int currentLinks = 0;

    public Router(Configuration config, String ipAddress) {
        this.routerServer = new RouterServer();
        this.rd.processIPAddress = ipAddress;
        this.rd.simulatedIPAddress = config.getString("socs.network.router.ip");
        this.lsd = new LinkStateDatabase(rd);
    }

    /**
     * output the shortest path to the given destination ip
     * <p/>
     * format: source ip address  -> ip address -> ... -> destination ip
     *
     * @param destinationIP the ip adderss of the destination simulated router
     */
    private void processDetect(String destinationIP) {

    }

    /**
     * disconnect with the router identified by the given destination ip address
     * Notice: this command should trigger the synchronization of database
     *
     * @param portNumber the port number which the link attaches at
     */
    private void processDisconnect(short portNumber) {


    }

    /**
     * attach the link to the remote router, which is identified by the given simulated ip;
     * to establish the connection via socket, you need to indentify the process IP and process Port;
     * additionally, weight is the cost to transmitting data through the link
     * <p/>
     * NOTE: this command should not trigger link database synchronization
     */
    private void processAttach(String processIP, short processPort, String simulatedIP, short weight) {
        if (currentLinks == RouterConstants.MAXIMUM_NO_OF_PORTS) {
            log.info("This Router has already reached its maximum link-limit: " + RouterConstants.MAXIMUM_NO_OF_PORTS +
                    "\nCannot add any more links.");
        } else {
            RouterDescription newRouterDescription = new RouterDescription();
            newRouterDescription.processIPAddress = processIP;
            newRouterDescription.processPortNumber = processPort;
            newRouterDescription.simulatedIPAddress = simulatedIP;

            Link newLink = new Link(this.rd, newRouterDescription, weight);
            this.ports[currentLinks++] = newLink;

            LinkDescription newLinkDescription = new LinkDescription();
            newLinkDescription.linkID = simulatedIP;
            newLinkDescription.portNum = processPort;
            //TODO:: Need to verufy what tosMetrics is....
            newLinkDescription.tosMetrics = 0;

            LSA currentLSA =  this.lsd._store.get(rd.simulatedIPAddress);
            if (currentLSA == null){
                log.error("LinkStateDatabase not initialized properly. Local router LSA entry not found.");
                System.exit(0);
            }
            currentLSA.links.add(newLinkDescription);
            this.lsd._store.put(rd.simulatedIPAddress, currentLSA);
        }
    }

    /**
     * broadcast Hello to neighbors
     */
    private void processStart() {

    }

    /**
     * attach the link to the remote router, which is identified by the given simulated ip;
     * to establish the connection via socket, you need to indentify the process IP and process Port;
     * additionally, weight is the cost to transmitting data through the link
     * <p/>
     * This command does trigger the link database synchronization
     */
    private void processConnect(String processIP, short processPort,
                                String simulatedIP, short weight) {

    }

    /**
     * output the neighbors of the routers
     */
    private void processNeighbors() {

    }

    /**
     * disconnect with all neighbors and quit the program
     */
    private void processQuit() {

    }

    public void terminal() {
        try {
            InputStreamReader isReader = new InputStreamReader(System.in);
            BufferedReader br = new BufferedReader(isReader);
            System.out.print("\n>> ");
            String command = br.readLine();
            while (true) {
                if (command.startsWith("detect ")) {
                    String[] cmdLine = command.split(" ");
                    processDetect(cmdLine[1]);

                } else if (command.startsWith("disconnect ")) {
                    String[] cmdLine = command.split(" ");
                    processDisconnect(Short.parseShort(cmdLine[1]));

                } else if (command.startsWith("quit")) {
                    processQuit();

                } else if (command.startsWith("attach ")) {
                    String[] cmdLine = command.split(" ");
                    processAttach(cmdLine[1], Short.parseShort(cmdLine[2]),
                            cmdLine[3], Short.parseShort(cmdLine[4]));

                } else if (command.equals("start")) {
                    processStart();

                } else if (command.equals("connect ")) {
                    String[] cmdLine = command.split(" ");
                    processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                            cmdLine[3], Short.parseShort(cmdLine[4]));

                } else if (command.equals("neighbors")) {
                    //output neighbors
                    processNeighbors();
                } else {
                    //invalid command
                    break;
                }
                System.out.print(">> ");
                command = br.readLine();
            }
            isReader.close();
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startServer() {
        short port = nextFreePort(RouterConstants.MIN_PORT_NUMBER, RouterConstants.MAX_PORT_NUMBER);
        this.routerServer.startRouterServer(port);
        rd.processPortNumber = port;
    }

    public RouterDescription getRd() {
        return rd;
    }


    private short nextFreePort(short min, short max) {
        short port = (short) ThreadLocalRandom.current().nextInt(min, max + 1);
        while (true) {
            if (isLocalPortFree(port)) {
                return port;
            } else {
                port = (short) ThreadLocalRandom.current().nextInt(min, max + 1);
            }
        }
    }

    private boolean isLocalPortFree(short port) {
        try {
            new ServerSocket(port).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
