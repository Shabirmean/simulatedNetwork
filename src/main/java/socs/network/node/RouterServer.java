package socs.network.node;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import socs.network.message.LSA;
import socs.network.message.SOSPFPacket;
import socs.network.util.RouterUtils;
import socs.network.util.RouterConstants;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Class for Router when acting as server.
 */
public class RouterServer {
    private final Log log = LogFactory.getLog(RouterServer.class);
    private final Router myRouter;
    private ServerSocket serverSocket;
    private short serverPort;

    RouterServer(Router router) {
        this.myRouter = router;
    }

    public ServerSocket socket() throws IOException {
        return this.serverSocket;
    }

//    void startRouterServer(final short port) {
    void startRouterServer() {
        final ExecutorService clientProcessingPool = Executors.newFixedThreadPool(10);

        Runnable serverTask = new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(myRouter.getRd().processPortNumber);
                    serverPort = (short) serverSocket.getLocalPort();

                    log.info("This Router is listening on PORT: " + serverPort);
                    log.info("Waiting for clients to connect...");

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


    private class ClientRequest implements Runnable {
        private final Socket clientSocket;
        private ObjectOutputStream socketWriter;
        private ObjectInputStream socketReader;

        private ClientRequest(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
//            prntStr("Got a new client!");
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
                    case RouterConstants.HELLO_PACKET:
                        handleHelloExchange(sospfPacket);
                        break;
                    case RouterConstants.LSUPDATE_PACKET:
                        processLSUPDATE(sospfPacket);
                        break;
                    case RouterConstants.EXIT_PACKET:
                        processNodeExit(sospfPacket);
                        break;
                }

            } catch (IOException e) {
                log.error("An IO error occurred whilst trying to READ [SOSPFPacket] object from socket stream.");
            } catch (ClassNotFoundException e) {
                log.error("An object type other than [SOSPFPacket] was recieved over the socket connection", e);
            } finally {
                RouterUtils.releaseSocket(clientSocket);
                RouterUtils.releaseWriter(socketWriter);
                RouterUtils.releaseReader(socketReader);
//                System.out.print(">> ");
            }
        }

        private void handleHelloExchange(SOSPFPacket sospfPacket) {
            String connectedSimIP = sospfPacket.srcIP;
            String packetDestIP = sospfPacket.dstIP;

            prntStr("received HELLO from " + connectedSimIP + ";");
            short linkIndex = myRouter.checkIfLinkExists(connectedSimIP);

            try {
                if (linkIndex == -1 && myRouter.noOfExistingLinks == RouterConstants.MAXIMUM_NO_OF_PORTS) {
                    prntStr("\n[WARN] This Router has already reached its maximum link-limit: " +
                            RouterConstants.MAXIMUM_NO_OF_PORTS + "\nCannot add any more links.\n");
                } else if (!packetDestIP.equals(myRouter.getRd().simulatedIPAddress)) {
                    prntStr("[WARN] The destination IP " + packetDestIP + " of incoming HELLO packet does not " +
                            "match mine.");
                } else {
                    boolean status = handleFirstHello(sospfPacket, linkIndex);
                    if (status) {
                        SOSPFPacket sospfPacket_2 = (SOSPFPacket) socketReader.readObject();
                        handleSecondHello(sospfPacket_2);
                    }
                }
            } catch (IOException e) {
                log.error("An IO error occurred whilst trying to READ 2nd [SOSPFPacket] object from socket stream.");
                myRouter.removeFromPorts(connectedSimIP);
            } catch (ClassNotFoundException e) {
                log.error("An object type other than [SOSPFPacket] was recieved over the socket connection", e);
                myRouter.removeFromPorts(connectedSimIP);
            }
        }


        private boolean handleFirstHello(SOSPFPacket sospfPacket, short portNumber) {
            String connectedSimIP = sospfPacket.srcIP;
            RouterDescription myRouterDesc = myRouter.getRd();
//                -------------------------------------------------
//                        Updates its own ports array & LSD
//                -------------------------------------------------
            RouterDescription newRouterDescription = new RouterDescription();
            newRouterDescription.processIPAddress = sospfPacket.srcProcessIP;
            newRouterDescription.processPortNumber = sospfPacket.srcProcessPort;
            newRouterDescription.simulatedIPAddress = connectedSimIP;
            newRouterDescription.status = RouterStatus.INIT;

            prntStr("set " + connectedSimIP + " state to INIT;");

            Link newLink = new Link(myRouterDesc, newRouterDescription);
            if (portNumber == -1) {
                myRouter.addToPorts(newLink);
            } else {
                myRouter.updatePorts(newLink, portNumber);
            }
//                -------------------------------------------------
//                    Reply back with HELLO and wait for TWO_WAY
//                -------------------------------------------------
            SOSPFPacket sospfReplyPacket =
                    RouterUtils.createNewPacket(myRouterDesc, connectedSimIP, RouterConstants.HELLO_PACKET);

            try {
                socketWriter.writeObject(sospfReplyPacket);
            } catch (IOException e) {
                log.error("An IO error occurred whilst trying to reply back [HELLO] to HOST " +
                        "[" + connectedSimIP + "] at PORT [" + sospfPacket.srcProcessPort + "].");
                myRouter.removeFromPorts(connectedSimIP);
                return false;
            }
            return true;
        }


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
                        connectingRouter.status = RouterStatus.TWO_WAY;
                        System.out.println("set " + connectedSimIP + " state to TWO_WAY;");
                        break;
                    }
                }
            }
        }


        private void processLSUPDATE(SOSPFPacket sospfPacket) {
            prntStr("[LSUPDATE] received lsupdate from: " + sospfPacket.srcIP);
            Vector<LSA> lsaVector = sospfPacket.lsaArray;
            String mySimulatedIP = myRouter.getRd().simulatedIPAddress;

            if (!sospfPacket.routerID.equals(mySimulatedIP) || sospfPacket.timeToLive > 0) {
                synchronized (myRouter) {
                    for (LSA lsa : lsaVector) {
                        String lsaLinkID = lsa.linkStateID;
                        if (!lsaLinkID.equals(mySimulatedIP)) {
                            LSA oldLSA = myRouter.lsd._store.get(lsaLinkID);
                            if (oldLSA == null || oldLSA.lsaSeqNumber < lsa.lsaSeqNumber) {
                                myRouter.lsd._store.put(lsaLinkID, lsa);
                            } else {
                                lsaVector.remove(lsa);
                            }
                        }
                    }
                }
                prntStr("updated local LinkStateDatabase;");
                myRouter.broadcastLSUPDATE(sospfPacket);
            } else {
                prntStr("terminating LSUPDATE broadcast [TTL - " + sospfPacket.timeToLive + " secs]");
            }
        }

        private void processNodeExit(SOSPFPacket sospfPacket) {
            String nodeSimulatedIP = sospfPacket.srcIP;
            myRouter.removeFromPorts(nodeSimulatedIP);
            prntStr("removed node: " + nodeSimulatedIP + " and updated local LinkStateDatabase;");
        }
    }

    private void prntStr(String string) {
        System.out.println(string);
    }
}
