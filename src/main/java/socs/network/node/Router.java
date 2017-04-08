package socs.network.node;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;
import socs.network.util.RouterConstants;
import socs.network.util.RouterUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.Vector;
import java.util.concurrent.*;

/**
 *
 */
public class Router {
    private final Log log = LogFactory.getLog(Router.class);
    private RouterServer routerServer;

    private RouterDescription rd = new RouterDescription();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static boolean WAS_START_CALLED = false;

    //assuming that all routers are with 4 ports
    volatile LinkStateDatabase lsd;
    volatile Link[] ports = new Link[RouterConstants.MAXIMUM_NO_OF_PORTS];
    volatile int noOfExistingLinks = 0;

    boolean printFlag = false;

    Router(Configuration config, String ipAddress) {
        this.routerServer = new RouterServer(this);
        this.rd.processIPAddress = ipAddress;
        this.rd.processPortNumber = Short.parseShort(config.getString("socs.network.router.port"));
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
    private int processAttach(String processIP, short processPort, String simulatedIP, short weight) {
        if (noOfExistingLinks == RouterConstants.MAXIMUM_NO_OF_PORTS) {
            prnt("[WARN] This Router has already reached its maximum link-limit: " +
                    RouterConstants.MAXIMUM_NO_OF_PORTS + "\nCannot add any more links.\n");
        } else {
            RouterDescription newRouterDescription = new RouterDescription();
            newRouterDescription.processIPAddress = processIP;
            newRouterDescription.processPortNumber = processPort;
            newRouterDescription.simulatedIPAddress = simulatedIP;
            Link newLink = new Link(this.rd, newRouterDescription, weight);

            Socket attachSocket;
            ObjectOutputStream socketWriter = null;
            ObjectInputStream socketReader = null;
            String packetType = RouterConstants.ATTACH_STRING;

            try {
                attachSocket = new Socket(processIP, processPort);
            } catch (IOException e) {
                log.error("[" + packetType + "] An error occurred whilst trying to establish Socket " +
                        "connection to HOST [" + processIP + "] at PORT [" + processPort + "]", e);
                return -1;
            }

            try {
                socketWriter = new ObjectOutputStream(attachSocket.getOutputStream());
                socketReader = new ObjectInputStream(attachSocket.getInputStream());

                SOSPFPacket sospfPacket = RouterUtils.
                        createNewPacket(this.rd, simulatedIP, RouterConstants.ATTACH_PACKET);
                socketWriter.writeObject(sospfPacket);

                if (printFlag) {
                    prnt("A [" + packetType + "] message sent to router with IP: " + sospfPacket.dstIP);
                }

                SOSPFPacket sospfPacket_2 = (SOSPFPacket) socketReader.readObject();
                if (sospfPacket_2.sospfType == RouterConstants.ATTACH_PACKET) {
                    return addToPorts(newLink);
                } else {
                    prnt("Attach [" + processIP + "] to this router failed.");
                }

            } catch (IOException e) {
                log.error("[" + packetType + "] An error occurred whilst trying to READ/WRITE to Socket " +
                        "connection at HOST [" + processIP + "] on PORT [" + processPort + "]", e);
                return -1;
            } catch (ClassNotFoundException e) {
                log.error("[HELLO] An object type other than [SOSPFPacket] was recieved over the socket " +
                        "connection", e);
                return -1;
            } finally {
                RouterUtils.releaseSocket(attachSocket);
                RouterUtils.releaseWriter(socketWriter);
                RouterUtils.releaseReader(socketReader);
            }
        }
        return -1;
    }

    /**
     * broadcast Hello to neighbors
     */
    private synchronized void processStart() {
        if (!WAS_START_CALLED) {
            WAS_START_CALLED = true;
        }

        Future[] arrayOfFuture = new Future[this.noOfExistingLinks];
        for (int linkIndex = 0; linkIndex < this.noOfExistingLinks; linkIndex++) {
            arrayOfFuture[linkIndex] = doHELLOExchange(linkIndex);
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
                    String routerSimIP = ports[futureIndex].getDestinationRouterDesc().simulatedIPAddress;

                    if (arrayOfFuture[futureIndex].isDone()) {
                        final String helloFinishedRouterIP = (String) arrayOfFuture[futureIndex].get();
                        if (helloFinishedRouterIP != null && helloFinishedRouterIP.equals(routerSimIP)) {
                            prnt("[HELLO EXCHANGE] completed for router with IP: " + helloFinishedRouterIP);
                        } else {
                            prnt("[WARN] HELLO to router connected to link-port [" + futureIndex + "] / IP [" +
                                    routerSimIP + "] failed. Run [connect] to re-connect device.");
                        }
                        portsVector.removeElement(futureIndex);
                        count++;
                    }
                }
            }

            //TODO:: No LSUPDATE if Hello fails
            if (printFlag) {
                prnt("[LSUPDATE] Sending LSUPDATE to all connected routers.");
            }

            Thread lsupdateThread = new Thread() {
                public void run() {
                    broadcastLSUPDATE();
                }
            };
            lsupdateThread.start();

        } catch (InterruptedException | ExecutionException e) {
            log.error("An error occurred whilst trying to get the return from [HELLO EXCHANGE] to router at PORT " +
                    "[" + futureIndex + "] with IP: " +
                    this.ports[futureIndex].getDestinationRouterDesc().simulatedIPAddress);
        }
    }

    /**
     * @param linkIndex
     * @return
     */
    private Future<String> doHELLOExchange(int linkIndex) {
        final int finalLinkIndex = linkIndex;
        Link newLink = ports[linkIndex];
        RouterDescription destinationRouterDesc = newLink.getDestinationRouterDesc();
        final String destinationRouterHostIP = destinationRouterDesc.processIPAddress;
        final short destinationRouterHostPort = destinationRouterDesc.processPortNumber;
        final SOSPFPacket sospfPacket =
                RouterUtils.createNewPacket(
                        this.rd, destinationRouterDesc.simulatedIPAddress, RouterConstants.HELLO_PACKET);
        Vector<LSA> lsaVector = new Vector<>();
        lsaVector.add(this.lsd._store.get(rd.simulatedIPAddress));
        sospfPacket.lsaArray = lsaVector;

        Callable<String> callable = new Callable<String>() {
            @Override
            public String call() {
                Socket helloTransferSocket;
                ObjectOutputStream socketWriter = null;
                ObjectInputStream socketReader = null;
                String connectedSimIP;

                try {
                    helloTransferSocket = new Socket(destinationRouterHostIP, destinationRouterHostPort);
                } catch (IOException e) {
                    log.error("[HELLO] An error occurred whilst trying to establish Socket connection to " +
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
                    // TODO:: Check for message type???
                    prnt("received HELLO from " + connectedSimIP + ";");

                    Link routerLink = ports[finalLinkIndex];
                    RouterDescription connectedRouterDesc = routerLink.getDestinationRouterDesc();
                    String incomingSimIP = connectedRouterDesc.simulatedIPAddress;
                    if (incomingSimIP.equals(connectedSimIP)) {
                        connectedRouterDesc.status = RouterStatus.TWO_WAY;
                        prnt("set " + connectedSimIP + " state to TWO_WAY;");
                        socketWriter.writeObject(sospfPacket);
                    } else {
                        prnt("[WARN] HELLO EXCHANGE failed with router: " + incomingSimIP +
                                ". The Source IP of incoming message was: " + connectedSimIP);
                    }
                } catch (IOException e) {
                    log.error("[HELLO] An error occurred whilst trying to READ/WRITE to Socket connection at " +
                            "HOST [" + destinationRouterHostIP + "] on " +
                            "PORT [" + destinationRouterHostPort + "]", e);
                    return null;
                } catch (ClassNotFoundException e) {
                    log.error("[HELLO] An object type other than [SOSPFPacket] was recieved over the socket " +
                            "connection", e);
                    return null;
                } finally {
                    RouterUtils.releaseSocket(helloTransferSocket);
                    RouterUtils.releaseWriter(socketWriter);
                    RouterUtils.releaseReader(socketReader);
                }
                return connectedSimIP;
            }
        };

        return executor.submit(callable);
    }


    private synchronized void broadcastLSUPDATE(LSA lsaOfQuitter) {
        SOSPFPacket sospfPacket = RouterUtils.createNewPacket(this.rd, "", RouterConstants.LSUPDATE_PACKET);
        Vector<LSA> lsaVector = new Vector<>();
        Collection<LSA> lsaCollection = lsd._store.values();
        for (LSA lsa : lsaCollection) {
            lsaVector.add(lsa);
        }
        lsaVector.add(lsaOfQuitter);
        sospfPacket.lsaArray = lsaVector;
        broadcastLSUPDATE(sospfPacket);
    }

    /**
     *
     */
    synchronized void broadcastLSUPDATE() {
        SOSPFPacket sospfPacket = RouterUtils.createNewPacket(this.rd, "", RouterConstants.LSUPDATE_PACKET);
        Vector<LSA> lsaVector = new Vector<>();
        Collection<LSA> lsaCollection = lsd._store.values();
        for (LSA lsa : lsaCollection) {
            lsaVector.add(lsa);
        }
        sospfPacket.lsaArray = lsaVector;
        broadcastLSUPDATE(sospfPacket);
    }

    /**
     * @param lsUpdatePacket
     */
    synchronized void broadcastLSUPDATE(SOSPFPacket lsUpdatePacket) {
        this.lsd.updateTopologyAndRoutingTable();
        String ipOfLsupdater = lsUpdatePacket.srcIP;

        for (short linkIndex = 0; linkIndex < noOfExistingLinks; linkIndex++) {
            Link link = ports[linkIndex];
            final String simulatedIP = link.getDestinationRouterDesc().simulatedIPAddress;
            if (!simulatedIP.equals(ipOfLsupdater)) {
                RouterDescription destRouterDesc = link.getDestinationRouterDesc();
                final String destinationRouterHostIP = destRouterDesc.processIPAddress;
                final short destinationRouterHostPort = destRouterDesc.processPortNumber;
                final SOSPFPacket sospfPacket =
                        RouterUtils.updatePacket(rd, destRouterDesc.simulatedIPAddress, lsUpdatePacket);

                Runnable lsupdateRunnable = getRunnable(destinationRouterHostIP,
                        destinationRouterHostPort, sospfPacket, RouterConstants.LSUPDATE_STRING);
                Thread lsupdateThread = new Thread(lsupdateRunnable);
                lsupdateThread.start();
            }
        }
    }

    /**
     * attach the link to the remote router, which is identified by the given simulated ip;
     * to establish the connection via socket, you need to indentify the process IP and process Port;
     * additionally, weight is the cost to transmitting data through the link
     * <p/>
     * This command does trigger the link database synchronization
     */
    private void processConnect(String processIP, short processPort, String simulatedIP, short weight) {
        if (WAS_START_CALLED) {
            int existingLinkPortNumber = checkIfLinkExists(simulatedIP);
            if (existingLinkPortNumber == -1) {
                int linkIndex = processAttach(processIP, processPort, simulatedIP, weight);

                // check if attach was successful, if not probably the router has reached max-4 connections
                if (linkIndex != -1) {
                    Future<String> exchangeState = doHELLOExchange(linkIndex);
                    while (true) {
                        if (exchangeState.isDone()) {
                            String helloFinishedRouterIP;
                            try {
                                helloFinishedRouterIP = exchangeState.get();
                                if (helloFinishedRouterIP.equals(simulatedIP)) {
                                    prnt("[HELLO EXCHANGE] completed for router with IP: " + helloFinishedRouterIP);
                                    prnt("[LSUPDATE] Sending LSUPDATE to all connected routers.");

                                    Thread lsupdateThread = new Thread() {
                                        public void run() {
                                            broadcastLSUPDATE();
                                        }
                                    };
                                    lsupdateThread.start();
                                    break;
                                } else {
                                    prnt("[WARN] HELLO EXCHANGE to router connected to link-port " +
                                            "[" + linkIndex + "] failed. The Source IP [" + helloFinishedRouterIP +
                                            "] " +
                                            "of the incoming message is invalid. Re-run [connect] to try again");
                                }

                            } catch (InterruptedException | ExecutionException e) {
                                log.error("An error occurred whilst trying to get the return from [HELLO EXCHANGE] " +
                                        "to router at PORT [" + linkIndex + "] with IP: " +
                                        this.ports[linkIndex].getDestinationRouterDesc().simulatedIPAddress, e);
                            }
                        }
                    }
                }
            } else {
                prnt("A link to [ " + processIP + ":" + processPort + " - " + simulatedIP + " ] " +
                        "already exists on port '" + existingLinkPortNumber + "' of this router.");
            }
        } else {
            prnt("[ATTACH] & [START] needs to be called before [CONNECT] is called.");
        }
    }

    /**
     * disconnect with the router identified by the given destination ip address
     * Notice: this command should trigger the synchronization of database
     *
     * @param portNumber the port number which the link attaches at
     */
    private void processDisconnect(short portNumber) {
        //TODO:: sometimes there is an error
        Link link = ports[portNumber];
        final String simulatedIP = link.getDestinationRouterDesc().simulatedIPAddress;
        final String destinationRouterHostIP = link.getDestinationRouterDesc().processIPAddress;
        final short destinationRouterHostPort = link.getDestinationRouterDesc().processPortNumber;
        final SOSPFPacket sospfPacket =
                RouterUtils.createNewPacket(this.rd, simulatedIP, RouterConstants.DISCONNECT_PACKET);

        Runnable disconnectRunnable = getRunnable(
                destinationRouterHostIP, destinationRouterHostPort, sospfPacket, RouterConstants.DISCONNECT_STRING);
        Thread disconnectTriggerThread = new Thread(disconnectRunnable);
        disconnectTriggerThread.start();

        removeFromPorts(portNumber, !(RouterConstants.QUITTER));
//        broadcastLSUPDATE();
    }

    /**
     * output the shortest path to the given destination ip
     * <p/>
     * format: source ip address  -> ip address -> ... -> destination ip
     *
     * @param destinationIP the ip adderss of the destination simulated router
     */
    private void processDetect(String destinationIP) {
        System.out.println(this.lsd.getShortestPath(destinationIP));
    }


    /**
     * output the neighbors of the routers
     */
    private void processNeighbors() {
        for (int portNo = 0; portNo < 4; portNo++) {
            Link linkOnPort = ports[portNo];
            if (linkOnPort != null) {
                RouterDescription linkedRouter = linkOnPort.getDestinationRouterDesc();
                String simulatedIPAddress = linkedRouter.simulatedIPAddress;
                prnt("Neighbour " + portNo + " - " + simulatedIPAddress);
            }
        }
    }

    /**
     * disconnect with all neighbors and quit the program
     */
    private void processQuit() {
        Thread[] quitThreadArray = new Thread[noOfExistingLinks];
        for (short linkIndex = 0; linkIndex < noOfExistingLinks; linkIndex++) {
            Link link = ports[linkIndex];
            final String simulatedIP = link.getDestinationRouterDesc().simulatedIPAddress;
            final String destinationRouterHostIP = link.getDestinationRouterDesc().processIPAddress;
            final short destinationRouterHostPort = link.getDestinationRouterDesc().processPortNumber;
            final SOSPFPacket sospfPacket =
                    RouterUtils.createNewPacket(this.rd, simulatedIP, RouterConstants.EXIT_PACKET);

            Runnable quitRunnable = getRunnable(
                    destinationRouterHostIP, destinationRouterHostPort, sospfPacket, RouterConstants.EXIT_STRING);
            Thread quitTriggerThread = new Thread(quitRunnable);
            quitTriggerThread.setDaemon(true);
            quitTriggerThread.start();
            quitThreadArray[linkIndex] = quitTriggerThread;
        }

        try {
            for (Thread quitThread : quitThreadArray) {
                quitThread.join();
            }
        } catch (InterruptedException e) {
            prnt("[QUIT] An error occurred whilst waiting for a Quit Thread to complete.");
        }

        executor.shutdown();
        System.exit(0);
    }

    /**
     * @param newLink
     * @return
     */
    synchronized int addToPorts(Link newLink) {
        if (!WAS_START_CALLED) {
            WAS_START_CALLED = true;
        }

        int linkIndex = noOfExistingLinks;
        this.ports[noOfExistingLinks++] = newLink;

        LinkDescription newLinkDescription = new LinkDescription();
        // same as sospfPacket.srcIP
        newLinkDescription.linkID = newLink.getDestinationRouterDesc().simulatedIPAddress;
        newLinkDescription.portNum = newLink.getDestinationRouterDesc().processPortNumber;
        newLinkDescription.tosMetrics = newLink.getLinkWeight();
        addNewLinkDescriptionToLSD(newLinkDescription);
        return linkIndex;
    }

    /**
     * @param newLink
     * @param portNum
     */
    synchronized void updatePorts(Link newLink, short portNum) {
        this.ports[portNum] = newLink;

        LinkDescription newLinkDescription = new LinkDescription();
        // same as sospfPacket.srcIP
        newLinkDescription.linkID = newLink.getDestinationRouterDesc().simulatedIPAddress;
        newLinkDescription.portNum = newLink.getDestinationRouterDesc().processPortNumber;
        newLinkDescription.tosMetrics = newLink.getLinkWeight();

        removeLinkDescriptionFromLSD(newLinkDescription.linkID, false);
        addNewLinkDescriptionToLSD(newLinkDescription);
    }

    /**
     * @param connectedSimIP
     */
    synchronized void removeFromPorts(String connectedSimIP, boolean isQuitter) {
        short portIndex = checkIfLinkExists(connectedSimIP);
        if (portIndex != -1) {
            Link link = ports[portIndex];
            if (link.getDestinationRouterDesc().simulatedIPAddress.equals(connectedSimIP)) {
                removeFromPorts(portIndex, isQuitter);
            }
        }
    }

    /**
     * @param portToDetach
     */
    private synchronized void removeFromPorts(short portToDetach, boolean isQuitter) {
        if (portToDetach < 4) {
            Link linkToRemove = ports[portToDetach];
            if (linkToRemove != null) {
                ports[portToDetach] = null;
                noOfExistingLinks--;

                for (int pos = portToDetach; pos < noOfExistingLinks; pos++) {
                    ports[pos] = ports[pos + 1];
                    ports[pos + 1] = null;
                }

                removeLinkDescriptionFromLSD(linkToRemove.getDestinationRouterDesc().simulatedIPAddress, isQuitter);
                prnt("Link on port " + portToDetach + " was successfully detached.");

            } else {
                log.error("Link-port [" + portToDetach + "] does not have any device attached to it.");
            }
        } else {
            log.error("Invalid link-port provided. Link-Port must be between 0-3.");
        }
    }

    /**
     * @param newLinkDescription
     */
    private synchronized void addNewLinkDescriptionToLSD(LinkDescription newLinkDescription) {
        LSA currentLSA = this.lsd._store.get(rd.simulatedIPAddress);
        if (currentLSA == null) {
            log.error("LinkStateDatabase not initialized properly. Local router LSA entry not found.");
            System.exit(0);
        }
        currentLSA.links.add(newLinkDescription);
        currentLSA.lsaSeqNumber++;
    }

    /**
     * @param simIPAddOfLinkDestination
     */
    private synchronized void removeLinkDescriptionFromLSD(String simIPAddOfLinkDestination, boolean isQuitter) {
        LSA currentLSA = this.lsd._store.get(rd.simulatedIPAddress);
        for (LinkDescription linkDesc : currentLSA.links) {
            if (linkDesc.linkID.equals(simIPAddOfLinkDestination)) {
                currentLSA.links.remove(linkDesc);
                currentLSA.lsaSeqNumber++;
                break;
            }
        }

        LSA lsaOfRemovedDevice = this.lsd._store.remove(simIPAddOfLinkDestination);
        if (isQuitter) {
            lsaOfRemovedDevice.hasQuitNetwork = true;
            lsaOfRemovedDevice.lsaSeqNumber++;
            broadcastLSUPDATE(lsaOfRemovedDevice);
        } else {
            broadcastLSUPDATE();
        }
    }

    /**
     * @param connectedSimIP
     * @return
     */
    synchronized short checkIfLinkExists(String connectedSimIP) {
        for (short linkIndex = 0; linkIndex < noOfExistingLinks; linkIndex++) {
            Link existingLink = ports[linkIndex];
            if (existingLink.getDestinationRouterDesc().simulatedIPAddress.equals(connectedSimIP)) {
                return linkIndex;
            }
        }
        return -1;
    }


    /**
     * @param destinationRouterHostIP
     * @param destinationRouterHostPort
     * @param sospfPacket
     * @param packetType
     * @return
     */
    private Runnable getRunnable(final String destinationRouterHostIP, final short
            destinationRouterHostPort, final SOSPFPacket sospfPacket, final String packetType) {
        return new Runnable() {
            @Override
            public void run() {
                Socket aNewSocket;
                ObjectOutputStream socketWriter = null;

                try {
                    aNewSocket = new Socket(destinationRouterHostIP, destinationRouterHostPort);
                } catch (IOException e) {
                    log.error("[" + packetType + "] An error occurred whilst trying to establish Socket " +
                            "connection to HOST [" + destinationRouterHostIP + "] at " +
                            "PORT [" + destinationRouterHostPort + "]", e);
                    return;
                }

                try {
                    socketWriter = new ObjectOutputStream(aNewSocket.getOutputStream());
                    socketWriter.writeObject(sospfPacket);

                    if (printFlag) {
                        prnt("A [" + packetType + "] message sent to router with IP: " + sospfPacket.dstIP);
                    }
                } catch (IOException e) {
                    log.error("[" + packetType + "] An error occurred whilst trying to READ/WRITE to Socket " +
                            "connection at HOST [" + destinationRouterHostIP + "] on " +
                            "PORT [" + destinationRouterHostPort + "]", e);
                } finally {
                    RouterUtils.releaseSocket(aNewSocket);
                    RouterUtils.releaseWriter(socketWriter);
                }
            }
        };
    }

    /**
     *
     */
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
                    System.out.println("");
                    processStart();

                } else if (command.startsWith("connect ")) {
                    System.out.println("");
                    String[] cmdLine = command.split(" ");
                    processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                            cmdLine[3], Short.parseShort(cmdLine[4]));

                } else if (command.startsWith("disconnect ")) {
                    System.out.println("");
                    String[] cmdLine = command.split(" ");
                    processDisconnect(Short.parseShort(cmdLine[1]));

                } else if (command.startsWith("detect ")) {
                    System.out.println("");
                    String[] cmdLine = command.split(" ");
                    processDetect(cmdLine[1]);

                } else if (command.equals("neighbors")) {
                    System.out.println("");
                    //output neighbors
                    processNeighbors();

                } else if (command.equals("ports")) {
                    System.out.println("");
                    // print information about the ports
                    printPortInfo();

                } else if (command.equals("topology")) {
                    System.out.println("");
                    // print information about the topology
                    this.lsd.printTopologyAndRoutingTable();

                } else if (command.startsWith("debug")) {
                    System.out.println("");
                    // switch on/off debug
                    String[] cmdLine = command.split(" ");
                    switchOnDebug(cmdLine[1]);

                } else if (command.startsWith("quit")) {
                    System.out.println("");
                    processQuit();

                } else if (command.startsWith("lsd")) {
                    System.out.println("");
                    printLSD();

                } else {
                    System.out.println("Invalid Command.");
                    //invalid command
                    break;
                }
                System.out.println("");
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

    /**
     *
     */
    void startServer() {
//        short port = nextFreeHostPort(RouterConstants.MIN_PORT_NUMBER, RouterConstants.MAX_PORT_NUMBER);
//        rd.processPortNumber = port;
        this.routerServer.startRouterServer();
    }


    private void printLSD() {
        for (String lsaEntry : this.lsd._store.keySet()) {
            LSA lsa = this.lsd._store.get(lsaEntry);
            System.out.println("--------------------------------------------------");
            System.out.println("       RouterIP      :   " + lsaEntry);
            System.out.println("       OriginatorIP  :   " + lsa.linkStateID);
            System.out.println("..................................................");

            for (LinkDescription linkDes : lsa.links) {
                System.out.println("LinkID [" + linkDes.linkID + "] - " +
                        "Port [" + linkDes.portNum + "] - WEIGHT [" + linkDes.tosMetrics + "]");
            }
            System.out.println("--------------------------------------------------");
        }
    }

    /**
     * @param on_off
     */
    private void switchOnDebug(String on_off) {
        switch (on_off) {
            case "on":
                this.printFlag = true;
                break;
            case "off":
                this.printFlag = false;
                break;
        }
    }

    /**
     *
     */
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

                System.out.println("-------------------------------------------");
                System.out.println("    ON ROUTER PORT: " + portNo);
                System.out.println("    PROCESS IP: " + processIPAddress);
                System.out.println("    PROCESS PORT: " + processPortNumber);
                System.out.println("    SIMULATED IP: " + simulatedIPAddress);
                System.out.println("    STATUS: " + statusString);
                System.out.println("    LINK WEIGHT: " + linkWeight);
                System.out.println("-------------------------------------------");
            } else {
                System.out.println("PORT " + portNo + " is free.");
            }
        }
    }

    /**
     * @param min
     * @param max
     * @return
     */
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

    /**
     * @param port
     * @return
     */
    private boolean isLocalHostPortFree(short port) {
        try {
            new ServerSocket(port).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * @param string
     */
    private void prnt(String string) {
        System.out.println(string);
    }
}
