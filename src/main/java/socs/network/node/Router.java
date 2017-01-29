package socs.network.node;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;
import socs.network.util.RouterUtils;
import socs.network.util.RouterConstants;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;
import java.util.concurrent.*;


public class Router {
    private final Log log = LogFactory.getLog(Router.class);
    private RouterServer routerServer;

    private RouterDescription rd = new RouterDescription();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    //assuming that all routers are with 4 ports
    //TODO:: ports should be thread-safe Make it volatile?
    LinkStateDatabase lsd;
    volatile Link[] ports = new Link[RouterConstants.MAXIMUM_NO_OF_PORTS];
    volatile int noOfExistingLinks = 0;

    Router(Configuration config, String ipAddress) {
        this.routerServer = new RouterServer(this);
        this.rd.processIPAddress = ipAddress;
        this.rd.simulatedIPAddress = config.getString("socs.network.router.ip");
        this.lsd = new LinkStateDatabase(rd);
    }

    RouterDescription getRd() {
        return rd;
    }

    /**
     * attach the link to the remote router, which is identified by the given simulated ip;
     * to establish the connection via socket, you need to indentify the process IP and process Port;
     * additionally, weight is the cost to transmitting data through the link
     * <p/>
     * NOTE: this command should not trigger link database synchronization
     */
    private void processAttach(String processIP, short processPort, String simulatedIP, short weight) {
        if (noOfExistingLinks == RouterConstants.MAXIMUM_NO_OF_PORTS) {
            log.info("This Router has already reached its maximum link-limit: " + RouterConstants
                    .MAXIMUM_NO_OF_PORTS +
                    "\nCannot add any more links.\n");
        } else {
            RouterDescription newRouterDescription = new RouterDescription();
            newRouterDescription.processIPAddress = processIP;
            newRouterDescription.processPortNumber = processPort;
            newRouterDescription.simulatedIPAddress = simulatedIP;

            Link newLink = new Link(this.rd, newRouterDescription, weight);
            addToPorts(newLink);
        }
    }

    /**
     * broadcast Hello to neighbors
     */
    private synchronized void processStart() {
        Future[] arrayOfFuture = new Future[this.noOfExistingLinks];

        for (int linkIndex = 0; linkIndex < this.noOfExistingLinks; linkIndex++) {
            final int finalLinkIndex = linkIndex;
            Link newLink = ports[linkIndex];
            RouterDescription destinationRouterDesc = newLink.getDestinationRouterDesc();
            final String destinationRouterHostIP = destinationRouterDesc.processIPAddress;
            final short destinationRouterHostPort = destinationRouterDesc.processPortNumber;
            final SOSPFPacket sospfPacket = RouterUtils.prepareHELLOPacket(this.rd, destinationRouterDesc);

            Callable<String> callable = new Callable<String>() {
                @Override
                public String call() {
                    Socket helloTransferSocket;
                    ObjectOutputStream socketWriter = null;
                    ObjectInputStream socketReader = null;
                    String connectedSimIP = null;

                    try {
                        helloTransferSocket = new Socket(destinationRouterHostIP, destinationRouterHostPort);
                    } catch (IOException e) {
                        log.error("An error occurred whilst trying to establish Socket connection to " +
                                "HOST [" + destinationRouterHostIP + "] at " +
                                "PORT [" + destinationRouterHostPort + "]", e);
                        return null;
                    }

                    try {
                        socketWriter = new ObjectOutputStream(helloTransferSocket.getOutputStream());
                        socketReader = new ObjectInputStream(helloTransferSocket.getInputStream());

                        socketWriter.writeObject(sospfPacket);
                        SOSPFPacket sospfPacket_2 = (SOSPFPacket) socketReader.readObject();
                        connectedSimIP = sospfPacket_2.srcIP;
                        System.out.println(">> received HELLO from " + connectedSimIP + ";");

                        RouterDescription connectedRouterDesc = ports[finalLinkIndex].getDestinationRouterDesc();
                        if (connectedRouterDesc.simulatedIPAddress.equals(connectedSimIP)) {
                            connectedRouterDesc.status = RouterStatus.TWO_WAY;
                            ports[finalLinkIndex] = new Link(rd, connectedRouterDesc);
                            System.out.println(">> set " + connectedSimIP + " state to TWO_WAY;");
                            socketWriter.writeObject(sospfPacket);
                        }

                    } catch (IOException e) {
                        log.error("An error occurred whilst trying to READ/WRITE to Socket connection at " +
                                "HOST [" + destinationRouterHostIP + "] on " +
                                "PORT [" + destinationRouterHostPort + "]", e);
                        return null;
                    } catch (ClassNotFoundException e) {
                        log.error("An object type other than [SOSPFPacket] was recieved over the socket connection", e);
                        return null;
                    } finally {
                        RouterUtils.releaseSocket(helloTransferSocket);
                        RouterUtils.releaseWriter(socketWriter);
                        RouterUtils.releaseReader(socketReader);
                    }

                    return connectedSimIP;
                }
            };

            Future<String> future = executor.submit(callable);
            arrayOfFuture[linkIndex] = future;
        }

        int count = 0;
        int futureIndex = -1;
        Vector<Integer> portsVector = new Vector<>();
        for (int index = 0; index < this.noOfExistingLinks; index++) {
            portsVector.add(index);
        }

        try {
            while (count != this.noOfExistingLinks) {
                for (int checkIndex = 0; checkIndex < portsVector.size(); checkIndex++) {
                    futureIndex = portsVector.get(checkIndex);
                    if (arrayOfFuture[futureIndex].isDone()) {

                        final String helloFinishedRouterIP = (String) arrayOfFuture[futureIndex].get();
                        if (helloFinishedRouterIP == null) {
                            log.warn("[HELLO EXCHANGE] to router connected to link-port [" + futureIndex + "] " +
                                    "failed. Re-run [attach] re-connect device.");
                        } else {
                            log.info("Initiating LSUPDATE for router with IP: " + helloFinishedRouterIP);
                            Thread lsupdateThread = new Thread() {
                                public void run() {
                                    processLSUPDATE(helloFinishedRouterIP);
                                }
                            };
                            lsupdateThread.start();
                        }
                        portsVector.removeElement(futureIndex);
                        count++;
                    }
                }
            }

        } catch (InterruptedException | ExecutionException e) {
            log.error("An error occurred whilst trying to get the return from [HELLO EXCHANGE] to router at PORT " +
                    "[" + futureIndex + "] with IP: " +
                    this.ports[futureIndex].getDestinationRouterDesc().simulatedIPAddress, e);
        }
    }


    private void processLSUPDATE(String routerIP) {

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
     * disconnect with the router identified by the given destination ip address
     * Notice: this command should trigger the synchronization of database
     *
     * @param portNumber the port number which the link attaches at
     */
    private void processDisconnect(short portNumber) {
        removeFromPorts(portNumber);
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
     * output the neighbors of the routers
     */
    private void processNeighbors() {

    }

    /**
     * disconnect with all neighbors and quit the program
     */
    private void processQuit() {
        executor.shutdown();

    }

    synchronized void addToPorts(Link newLink) {
        this.ports[noOfExistingLinks++] = newLink;

        LinkDescription newLinkDescription = new LinkDescription();
        // same as sospfPacket.srcIP
        newLinkDescription.linkID = newLink.getDestinationRouterDesc().simulatedIPAddress;
        newLinkDescription.portNum = newLink.getDestinationRouterDesc().processPortNumber;
        //TODO:: Need to verify what's tosMetrics is....
        newLinkDescription.tosMetrics = newLink.getLinkWeight();
        addNewLinkDescriptionToLSD(newLinkDescription);
    }

    synchronized void updatePorts(Link newLink, short portNum) {
        this.ports[portNum] = newLink;

        LinkDescription newLinkDescription = new LinkDescription();
        // same as sospfPacket.srcIP
        newLinkDescription.linkID = newLink.getDestinationRouterDesc().simulatedIPAddress;
        newLinkDescription.portNum = newLink.getDestinationRouterDesc().processPortNumber;
        //TODO:: Need to verify what's tosMetrics is....
        newLinkDescription.tosMetrics = newLink.getLinkWeight();

        removeLinkDescriptionFromLSD(newLinkDescription.linkID);
        addNewLinkDescriptionToLSD(newLinkDescription);
    }

    synchronized void removeFromPorts(String connectedSimIP) {
        short portIndex = checkIfLinkExists(connectedSimIP);
        if (portIndex != -1) {
            Link link = ports[portIndex];
            if (link.getDestinationRouterDesc().simulatedIPAddress.equals(connectedSimIP)) {
                removeFromPorts(portIndex);
            }
        }
    }

    private synchronized void removeFromPorts(short portToDetach) {
        if (portToDetach < 4) {
            Link linkToRemove = ports[portToDetach];
            if (linkToRemove != null) {
                ports[portToDetach] = null;
                noOfExistingLinks--;

                for (int pos = portToDetach; pos < noOfExistingLinks; pos++) {
                    ports[pos] = ports[pos + 1];
                    ports[pos + 1] = null;
                }

                removeLinkDescriptionFromLSD(linkToRemove.getDestinationRouterDesc().simulatedIPAddress);
                System.out.println(">> Link on port " + portToDetach + " was successfully detached.");

            } else {
                log.error("Link-port [" + portToDetach + "] does not have any device attached to it.");
            }
        } else {
            log.error("Invalid link-port provided. Link-Port must be between 0-3.");
        }
    }

    private synchronized void addNewLinkDescriptionToLSD(LinkDescription newLinkDescription) {
        LSA currentLSA = this.lsd._store.get(rd.simulatedIPAddress);
        if (currentLSA == null) {
            log.error("LinkStateDatabase not initialized properly. Local router LSA entry not found.");
            System.exit(0);
        }
        currentLSA.links.add(newLinkDescription);
    }

    private synchronized void removeLinkDescriptionFromLSD(String simIPAddLinkDestination) {
        LSA currentLSA = this.lsd._store.get(rd.simulatedIPAddress);
        for (LinkDescription linkDesc : currentLSA.links) {
            if (linkDesc.linkID.equals(simIPAddLinkDestination)) {
                currentLSA.links.remove(linkDesc);
                break;
            }
        }
    }

    synchronized short checkIfLinkExists(String connectedSimIP) {
        for (short linkIndex = 0; linkIndex < noOfExistingLinks; linkIndex++) {
            Link existingLink = ports[linkIndex];
            if (existingLink.getDestinationRouterDesc().simulatedIPAddress.equals(connectedSimIP)) {
                return linkIndex;
            }
        }
        return -1;
    }

    void terminal() {
        try {
            InputStreamReader isReader = new InputStreamReader(System.in);
            BufferedReader br = new BufferedReader(isReader);
            System.out.print("\n>> ");
            String command = br.readLine();
            while (true) {
                if (command.startsWith("attach ")) {
                    String[] cmdLine = command.split(" ");
                    processAttach(cmdLine[1], Short.parseShort(cmdLine[2]),
                            cmdLine[3], Short.parseShort(cmdLine[4]));

                } else if (command.equals("start")) {
                    processStart();

                } else if (command.equals("connect ")) {
                    String[] cmdLine = command.split(" ");
                    processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                            cmdLine[3], Short.parseShort(cmdLine[4]));

                } else if (command.startsWith("disconnect ")) {
                    String[] cmdLine = command.split(" ");
                    processDisconnect(Short.parseShort(cmdLine[1]));

                } else if (command.startsWith("detect ")) {
                    String[] cmdLine = command.split(" ");
                    processDetect(cmdLine[1]);

                } else if (command.equals("neighbors")) {
                    //output neighbors
                    processNeighbors();

                } else if (command.startsWith("ports")) {
                    // print information about the ports
                    printPortInfo();

                } else if (command.startsWith("quit")) {
                    processQuit();

                } else {
                    System.out.println("Invalid Command.");
                    //invalid command
                    break;
                }
                System.out.print(">> ");
                command = br.readLine();
            }
            isReader.close();
            br.close();
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    private void printPortInfo() {
        for (int portNo = 0; portNo < 4; portNo++) {
            Link linkOnPort = ports[portNo];
            if (linkOnPort != null) {
                RouterDescription linkedRouter = linkOnPort.getDestinationRouterDesc();
                String processIPAddress = linkedRouter.processIPAddress;
                short processPortNumber = linkedRouter.processPortNumber;
                String simulatedIPAddress = linkedRouter.simulatedIPAddress;
                RouterStatus status = linkedRouter.status;
                String statusString = "NULL";

                if (status == RouterStatus.INIT) {
                    statusString = "INIT";
                } else if (status == RouterStatus.TWO_WAY) {
                    statusString = "TWO_WAY";
                }
                short linkWeight = linkOnPort.getLinkWeight();

                System.out.println(">> -------------------------------------------");
                System.out.println("    ON ROUTER PORT: " + portNo);
                System.out.println("    PROCESS IP: " + processIPAddress);
                System.out.println("    PROCESS PORT: " + processPortNumber);
                System.out.println("    SIMULATED IP: " + simulatedIPAddress);
                System.out.println("    STATUS: " + statusString);
                System.out.println("    LINK WEIGHT: " + linkWeight);
                System.out.println(">> -------------------------------------------");
            } else {
                System.out.println(">> PORT " + portNo + " is free.");
            }
        }
    }

    void startServer() {
        short port = nextFreeHostPort(RouterConstants.MIN_PORT_NUMBER, RouterConstants.MAX_PORT_NUMBER);
        this.routerServer.startRouterServer(port);
        rd.processPortNumber = port;
    }

    private short nextFreeHostPort(short min, short max) {
        short port = (short) ThreadLocalRandom.current().nextInt(min, max + 1);
        while (true) {
            if (isLocalHostPortFree(port)) {
                return port;
            } else {
                port = (short) ThreadLocalRandom.current().nextInt(min, max + 1);
            }
        }
    }

    private boolean isLocalHostPortFree(short port) {
        try {
            new ServerSocket(port).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
