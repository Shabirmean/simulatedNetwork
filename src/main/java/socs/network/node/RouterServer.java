package socs.network.node;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import socs.network.message.SOSPFPacket;
import socs.network.util.RouterUtils;
import socs.network.util.RouterConstants;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Class for Router when acting as server. This class starts the server that listens for all incoming connections.
 * For every incoming connection spawns a new thread from the pool of pre-initiated threads to process the request.
 */
class RouterServer {
    private final Log log = LogFactory.getLog(RouterServer.class);

    // reference to the Router class instance to which this Server components is attached to.
    // this is essential to access the router attributes like router-description, LSD etc.
    private final Router myRouter;
    // the socket in which this server is listening for incoming connections.
    private ServerSocket serverSocket;
    // the port of this ServerSocket.
    private short serverPort;
    // executor service with a thread pool to assign each incoming request to.
    private final ExecutorService clientProcessingPool = Executors.
            newFixedThreadPool(RouterConstants.SERVER_THREAD_POOL_COUNT);

    /**
     * Constructor for this class. Takes in the associated Router instance as argument.
     * @param router the Router instance to which this server is attached to and is listening for connections.
     */
    RouterServer(Router router) {
        this.myRouter = router;
    }

    /**
     * The initiator method for this class. Creates the ServerSocket from the port corresponding to its Router
     * instance. Sets up the
     */
    void startRouterServer() {
        Runnable serverTask = new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(myRouter.getRd().processPortNumber);
                    serverPort = (short) serverSocket.getLocalPort();

                    log.info("This Router is listening on PORT: " + serverPort);
                    log.info("Waiting for clients to connect...");

                    // listen for incoming connection continuously
                    // for every incoming request, create a new socket and assign it to a new thread in the pool.
                    while (true) {
                        Socket clientSocket = serverSocket.accept();
                        clientProcessingPool.submit(new ClientRequest(clientSocket));
                    }
                } catch (IOException e) {
                    log.error("Unable to process client request");
                    e.printStackTrace();
                }
            }
        };
        Thread serverThread = new Thread(serverTask);
        serverThread.start();
    }

    /**
     * Utility method to shorten the code-line length when printing out logs.
     * @param string the string to be printed/logged.
     */
    private void prntStr(String string) {
        System.out.println(string);
    }

    // ---------------------------------------------------------------------------------------------------------------
    /**
     * An inner class which holds all the methods related to handling a new incoming request. Implements "Runnable"
     * whose run method is called by the parent class upon receiving a new incoming request. Consists of separate
     * methods to handle different types of incoming requests.
     */
    private class ClientRequest implements Runnable {
        // the unique socket allocated for this new request instance via which future communications happen.
        private final Socket clientSocket;
        // the OutputStream used to write messages out into the socket.
        private ObjectOutputStream socketWriter;
        // the InputStream from which incoming messages are read from.
        private ObjectInputStream socketReader;

        /**
         * Constructor for the request handler class. Expects the socket via which it should serve the request moving
         * forward.
         * @param clientSocket the socket which is to be used in serving the request allocated to this instance.
         */
        private ClientRequest(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        /**
         * Overridden "run" method from Runnable that initiates new-request handling procedures. Sets up the socket
         * reader and writer objects and invokes appropriate method according to the incoming message type.
         */
        @Override
        public void run() {
            try {
                this.socketWriter = new ObjectOutputStream(clientSocket.getOutputStream());
                this.socketReader = new ObjectInputStream(clientSocket.getInputStream());
            } catch (IOException e) {
                log.error("An IO error occurred whilst trying to open Input/Output stream on the " +
                        "socket connection for READ/WRITE.");
                return;
            }

            try {
                SOSPFPacket sospfPacket = (SOSPFPacket) socketReader.readObject();
                short messageType = sospfPacket.sospfType;

                switch (messageType) {
                    case RouterConstants.ATTACH_PACKET:
                        handleAttach(sospfPacket);
                        break;
                    case RouterConstants.HELLO_PACKET:
                        handleHelloExchange(sospfPacket);
                        break;
                    case RouterConstants.LSUPDATE_PACKET:
                        processLSUPDATE(sospfPacket);
                        break;
                    case RouterConstants.DISCONNECT_PACKET:
                        processNodeExitOrDisconnect(sospfPacket);
                        break;
                    case RouterConstants.EXIT_PACKET:
                        processNodeExitOrDisconnect(sospfPacket);
                        break;
                }

            } catch (IOException e) {
                log.error("An IO error occurred whilst trying to READ [SOSPFPacket] object from socket stream.", e);
            } catch (ClassNotFoundException e) {
                log.error("An object type other than [SOSPFPacket] was recieved over the socket connection", e);
            } finally {
                RouterUtils.releaseSocket(clientSocket);
                RouterUtils.releaseWriter(socketWriter);
                RouterUtils.releaseReader(socketReader);
            }
        }

        /**
         * Method to handle a message type that denotes an "ATTACH" event. This type of message is sent when the
         * ATTACH command is run on another router to attach to this router. Checks if this router has any-more free
         * ports and that the attempting router is not already attached. If both checks - PASS, then a new link
         * description is added to a free port with the information of the router that sent the "ATTACH" message.
         *
         * @param sospfPacket the incoming message-packet that carries information of the event.
         */
        private void handleAttach(SOSPFPacket sospfPacket) {
            String connectedSimIP = sospfPacket.srcIP;
            String packetDestIP = sospfPacket.dstIP;

            if (myRouter.printFlag) {
                prntStr("received an ATTACH request from " + connectedSimIP + ";");
            }

            // checks if the message was intended for this router by checking the destination IP in the packet.
            if (!packetDestIP.equals(myRouter.getRd().simulatedIPAddress)) {
                prntStr("[WARN] The destination IP " + packetDestIP + " of incoming HELLO packet " +
                        "does not match mine.");
                return;
            }

            // ensure that there is NO other already existing link to the invoking router.
            short linkIndex = myRouter.checkIfLinkExists(connectedSimIP);
            RouterDescription myRouterDesc = myRouter.getRd();
            SOSPFPacket sospfReplyPacket =
                    RouterUtils.createNewPacket(myRouterDesc, connectedSimIP, RouterConstants.ATTACH_PACKET);

            try {
                if (linkIndex != -1) {
                    // if a link to the calling router already exists then set PACKET-TYPE to "-1" and reply.
                    prntStr("\n[WARN] This Router already has a link to router [" + connectedSimIP + "]" +
                            " on port [" + linkIndex + "]\n");
                    sospfReplyPacket.sospfType = -1;
                    socketWriter.writeObject(sospfReplyPacket);

                } else if (myRouter.noOfExistingLinks == RouterConstants.MAXIMUM_NO_OF_PORTS) {
                    // if this router has already reached its maximum connection count then
                    // set the PACKET-TYPE to "-1" and reply.
                    prntStr("\n[WARN] This Router has already reached its maximum link-limit: " +
                            RouterConstants.MAXIMUM_NO_OF_PORTS + "\nCannot add any more links.\n");
                    sospfReplyPacket.sospfType = -1;
                    socketWriter.writeObject(sospfReplyPacket);

                } else {
                    // If there exists no previous-link to the calling router and there are available ports in this
                    // router, then create a new link; add it to this router's ports array and reply with PACKET-TYPE
                    // set to "ATTACH_PACKET"[2].
                    RouterDescription newRouterDescription = new RouterDescription();
                    newRouterDescription.processIPAddress = sospfPacket.srcProcessIP;
                    newRouterDescription.processPortNumber = sospfPacket.srcProcessPort;
                    newRouterDescription.simulatedIPAddress = connectedSimIP;
                    Link newLink = new Link(myRouterDesc, newRouterDescription);

                    myRouter.addToPorts(newLink);
                    socketWriter.writeObject(sospfReplyPacket);
                }
            } catch (IOException e) {
                log.error("An IO error occurred whilst trying to reply for [ATTACH] request to HOST " +
                        "[" + connectedSimIP + "] at PORT [" + sospfPacket.srcProcessPort + "].");
                myRouter.removeFromPorts(connectedSimIP, false);
            }
        }

        /**
         * Method to handle a HELLO_EXCHANGE packet. Validates whether the message was intended for this router,
         * checks if a link to the sending router already exists and invokes the HELLO_EXCHANGE methods.
         *
         * @param sospfPacket the incoming message-packet that carries information of the event.
         */
        private void handleHelloExchange(SOSPFPacket sospfPacket) {
            String connectedSimIP = sospfPacket.srcIP;
            String packetDestIP = sospfPacket.dstIP;

            // checks if the message was intended for this router by checking the destination IP in the packet.
            if (!packetDestIP.equals(myRouter.getRd().simulatedIPAddress)) {
                prntStr("[WARN] The destination IP " + packetDestIP + " of incoming HELLO packet " +
                        "does not match mine.");
                return;
            }

            // print receipt of a "HELLO" message from the sending router.
            prntStr("received HELLO from " + connectedSimIP + ";");
            // check if a link exists to the router who sent the HELLO message.
            short linkIndex = myRouter.checkIfLinkExists(connectedSimIP);

            try {
                if (linkIndex == -1) {
                    // if there is no link to the router that sent a HELLO message, then print error and exit.
                    prntStr("\n[ERROR] This router has not been properly attached to [" + connectedSimIP + "]");
                } else {
                    // if a link exists to the sending router, then call method to handle the first HELLO message.
                    boolean status = handleFirstHello(sospfPacket, linkIndex);
                    if (status) {
                        // if the handling of first HELLO message was successful then read response from other end and
                        // invoke method to handle second HELLO message.
                        SOSPFPacket sospfPacket_2 = (SOSPFPacket) socketReader.readObject();
                        handleSecondHello(sospfPacket_2);
                        myRouter.broadcastLSUPDATE();
                    }
                }
            } catch (IOException e) {
                log.error("An IO error occurred whilst trying to READ 2nd [SOSPFPacket] object from socket stream.");
                myRouter.removeFromPorts(connectedSimIP, false);
            } catch (ClassNotFoundException e) {
                log.error("An object type other than [SOSPFPacket] was recieved over the socket connection", e);
                myRouter.removeFromPorts(connectedSimIP, false);
            }
        }


        /**
         * Method to handle any initial/first HELLO message from a connected router. Sets the STATUS of the calling
         * router to "INIT" and replies a HELLO packet to the calling router.
         *
         * @param sospfPacket the incoming HELLO message packet to be processed.
         * @param portNumber the port of this router on to which the calling router is connected to.
         * @return true, if the successfully replied to calling router with HELLO packet; else false.
         */
        private boolean handleFirstHello(SOSPFPacket sospfPacket, short portNumber) {
            String connectedSimIP = sospfPacket.srcIP;
            RouterDescription myRouterDesc = myRouter.getRd();

            Link attachedLink = myRouter.ports[portNumber];

            // set status of the calling router to "INIT".
            attachedLink.getDestinationRouterDesc().status = RouterStatus.INIT;
            prntStr("set " + connectedSimIP + " state to INIT;");

            // reply with a HELLO message.
            SOSPFPacket sospfReplyPacket =
                    RouterUtils.createNewPacket(myRouterDesc, connectedSimIP, RouterConstants.HELLO_PACKET);

            try {
                socketWriter.writeObject(sospfReplyPacket);
            } catch (IOException e) {
                log.error("An IO error occurred whilst trying to reply back [HELLO] to HOST " +
                        "[" + connectedSimIP + "] at PORT [" + sospfPacket.srcProcessPort + "].");
                myRouter.removeFromPorts(connectedSimIP, false);
                return false;
            }
            return true;
        }

        /**
         * Method that handles the second HELLO message in the HELLO_EXCHANGE sequence. Sets the calling router
         * status to "TWO_WAY".
         *
         * @param sospfPacket_2 the incoming packet with the second HELLO message.
         */
        private void handleSecondHello(SOSPFPacket sospfPacket_2) {
            String connectedSimIP = sospfPacket_2.srcIP;
            prntStr("received HELLO from " + connectedSimIP + ";");

            synchronized (myRouter) {
                Link[] routerPorts = myRouter.ports;
                int noOfExistingLinks = myRouter.noOfExistingLinks;

                for (short linkIndex = 0; linkIndex < noOfExistingLinks; linkIndex++) {
                    Link link = routerPorts[linkIndex];
                    RouterDescription connectingRouter = link.getDestinationRouterDesc();

                    if (connectingRouter.simulatedIPAddress.equals(connectedSimIP)) {
                        // set status of the communicating router to TWO_WAY.
                        connectingRouter.status = RouterStatus.TWO_WAY;
                        System.out.println("set " + connectedSimIP + " state to TWO_WAY;");
                        break;
                    }
                }
            }
        }

        /**
         * Method that handles an LSUPDATE (Link-State Update) packet received from any of the connected routers.
         * Loops through all the Link-State-Advertisements (LSA) in the message. Verifies whether each of them has a
         * newer sequence-number than what's already held in this router's local Link-State-Database (LSD). If an LSA
         * with newer sequence-number is found updates LSD else disregards LSA. Also, checks if LSA denotes a node
         * that EXIT the network. If so, removes LSA of that node from local LSD.
         *
         * Updates the local Topology Graph that maintains all connections in the network and runs Djiskstra's Algo
         * to deduce the new routing table after the LSD updates. Finally forwards the LSUPDATE packet to all
         * neighbours except the one from which it was received.
         *
         * @param sospfPacket the incoming LSUPDATE packet with the list of LSA's to be updated.
         */
        private void processLSUPDATE(SOSPFPacket sospfPacket) {
            String sourceIP = sospfPacket.srcIP;
            if (myRouter.printFlag) {
                prntStr("[LSUPDATE] received lsupdate from: " + sourceIP);
            }

            Vector<LSA> lsaVector = sospfPacket.lsaArray;
            String mySimulatedIP = myRouter.getRd().simulatedIPAddress;

            // check if this LSUPDATE packet was one that was initiated by this router itself.
            if (!sospfPacket.routerID.equals(mySimulatedIP)) {
                // check if the TTL of this LSUPDATE packet has expired.
                if (sospfPacket.timeToLive > System.currentTimeMillis()) {
                    synchronized (myRouter) {
                        // loop through each LSA in the LSUPDATE packet
                        for (LSA lsa : lsaVector) {
                            String lsaLinkID = lsa.linkStateID;
                            // check if the LSA in the LSUPDATE corresponds this router; process only if it's not.
                            if (!lsaLinkID.equals(mySimulatedIP)) {
                                LSA oldLSA = myRouter.lsd._store.get(lsaLinkID);
                                // check if the sequence number of current LSA is greater than whats already in the LSD
                                // update only if sequence number is greater than what's already there.
                                if (oldLSA == null || oldLSA.lsaSeqNumber < lsa.lsaSeqNumber) {
                                    if (lsa.hasQuitNetwork) {
                                        // if lsa is marked as that of a node that QUIT the network, then remove its
                                        // LSA from the local LSD.
                                        myRouter.lsd._store.remove(lsaLinkID);
                                    } else {
                                        myRouter.lsd._store.put(lsaLinkID, lsa);

                                        int myIndexInNewLSA = getLinkIndex(lsa.links, mySimulatedIP);
                                        int hisIndexInMyLSA =
                                                getLinkIndex(myRouter.lsd._store.get(mySimulatedIP).links, lsaLinkID);

                                        if (myIndexInNewLSA != -1) {
                                            int linkWeight = lsa.links.get(myIndexInNewLSA).tosMetrics;
                                            myRouter.lsd._store.get(mySimulatedIP).
                                                    links.get(hisIndexInMyLSA).tosMetrics = linkWeight;

                                            int portNumber = myRouter.checkIfLinkExists(lsaLinkID);
                                            if (portNumber != -1) {
                                                myRouter.ports[portNumber].setLinkWeight((short) linkWeight);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (myRouter.printFlag) {
                        prntStr("updated local LinkStateDatabase;");
                    }

                    // once the LSUPDATE message is processed, update the Topology Graph with the new updates and run
                    // Djisktra's algorithm to calculate the shortest path routing table for this router.
                    myRouter.lsd.updateTopologyAndRoutingTable();
                    // broadcast LSUPDATE to neighbouring nodes.
                    broadcastLSUPDATE(sospfPacket);
//                    myRouter.broadcastLSUPDATE(sospfPacket);

                } else {
                    if (myRouter.printFlag) {
                        prntStr("terminating LSUPDATE broadcast [TTL Expired]");
                    }
                }
            } else {
                if (myRouter.printFlag) {
                    prntStr("terminating LSUPDATE broadcast [This update packet was initiated by me]");
                }
            }
        }

        /**
         * Method to broadcast the received LSUPDATE message to all the neighbouring nodes except to the one from which
         * the UPDATE was received.
         *
         * @param lsUpdatePacket the received LSUPDATE message that needs to be broadcast.
         */
        void broadcastLSUPDATE(SOSPFPacket lsUpdatePacket) {
            String ipOfLsupdater = lsUpdatePacket.srcIP;
            for (short linkIndex = 0; linkIndex < myRouter.noOfExistingLinks; linkIndex++) {
                Link link = myRouter.ports[linkIndex];
                final String simulatedIP = link.getDestinationRouterDesc().simulatedIPAddress;
                if (!simulatedIP.equals(ipOfLsupdater)) {
                    RouterDescription destRouterDesc = link.getDestinationRouterDesc();
                    final String destinationRouterHostIP = destRouterDesc.processIPAddress;
                    final short destinationRouterHostPort = destRouterDesc.processPortNumber;
                    final SOSPFPacket sospfPacket =
                            RouterUtils.updatePacket(myRouter.getRd(), destRouterDesc.simulatedIPAddress,
                                    lsUpdatePacket);

                    Socket aNewSocket;
                    ObjectOutputStream socketWriter = null;
                    String packetType = RouterConstants.LSUPDATE_STRING;

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

                        if (myRouter.printFlag) {
                            prntStr("A [" + packetType + "] message sent to router with IP: " +
                                    sospfPacket.dstIP);
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
            }
        }

        /**
         * Method to process an EXIT or DISCONNECT message from a neighbouring node. Calls the corresponding method
         * to remove the EXIT / DISCONNECTED node from the local ports array.
         *
         * @param sospfPacket the incoming EXIT/DISCONNECT message from the communicating device.
         */
        private void processNodeExitOrDisconnect(SOSPFPacket sospfPacket) {
            String nodeSimulatedIP = sospfPacket.srcIP;
            switch(sospfPacket.sospfType){
                case RouterConstants.DISCONNECT_PACKET:
                    myRouter.removeFromPorts(nodeSimulatedIP, !(RouterConstants.QUITTER));
                    break;

                case RouterConstants.EXIT_PACKET:
                    myRouter.removeFromPorts(nodeSimulatedIP, RouterConstants.QUITTER);
                    break;
            }
            prntStr("removed node: " + nodeSimulatedIP + " and updated local LinkStateDatabase;");
        }

        /**
         * Utility method to get the index of the link-description of a given device in a Linked-List of
         * link-descriptions.
         *
         * @param links Linked-List of LinkDescriptions from which the index of the given device is to be found.
         * @param ipOfRouter the IP of the device whose index is to be found in the Linked-List.
         * @return the index of the given device's LinkDescription in the Linked-List.
         */
        private int getLinkIndex(LinkedList<LinkDescription> links, String ipOfRouter) {
            for (int index = 0; index < links.size(); index++) {
                if (links.get(index).linkID.equals(ipOfRouter)) {
                    return index;
                }
            }
            return -1;
        }
    }
}
