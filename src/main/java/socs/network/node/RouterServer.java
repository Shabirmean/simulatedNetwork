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

    public short getServerPort() {
        return this.serverPort;
    }

    public void bind() {
    }

    public void listen() {
    }

    public void accept() {
    }

    public void recv() {
    }

    public void send() {
    }

    public void close() {
    }

    void startRouterServer(final short port) {
        final ExecutorService clientProcessingPool = Executors.newFixedThreadPool(10);

        Runnable serverTask = new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(port);
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
            System.out.println("Got a new client!");
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
                    case 0:
                        handleHelloExchange(sospfPacket);
                        break;
                    case 1:
                        handleLSUPDATE(sospfPacket);
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
                System.out.print(">> ");
            }
        }

        private void handleHelloExchange(SOSPFPacket sospfPacket) {
            String connectedSimIP = sospfPacket.srcIP;
            System.out.println(">> received HELLO from " + connectedSimIP + ";");
            short linkIndex = myRouter.checkIfLinkExists(connectedSimIP);

            try {
                if (linkIndex == -1 && myRouter.noOfExistingLinks == RouterConstants.MAXIMUM_NO_OF_PORTS) {
                    log.info("\nThis Router has already reached its maximum link-limit: " + RouterConstants
                            .MAXIMUM_NO_OF_PORTS +
                            "\nCannot add any more links.\n");
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

            System.out.println(">> set " + connectedSimIP + " state to INIT;");

            Link newLink = new Link(myRouterDesc, newRouterDescription);
            if (portNumber == -1) {
                myRouter.addToPorts(newLink);
            } else {
                myRouter.updatePorts(newLink, portNumber);
            }
//                -------------------------------------------------
//                    Reply back with HELLO and wait for TWO_WAY
//                -------------------------------------------------
            SOSPFPacket sospfReplyPacket = RouterUtils.prepareHELLOPacket(myRouterDesc, newRouterDescription);

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
            System.out.println(">> received HELLO from " + connectedSimIP + ";");

            RouterDescription myRouterDesc = myRouter.getRd();
            Link[] routerPorts;
            int noOfExistingLinks;

            routerPorts = myRouter.ports;
            noOfExistingLinks = myRouter.noOfExistingLinks;

            for (short linkIndex = 0; linkIndex < noOfExistingLinks; linkIndex++) {
                Link oldLink = routerPorts[linkIndex];
                RouterDescription connectingRouter = oldLink.getDestinationRouterDesc();

                if (connectingRouter.simulatedIPAddress.equals(connectedSimIP)) {
                    connectingRouter.status = RouterStatus.TWO_WAY;

                    Link newLinkDesc = new Link(myRouterDesc, connectingRouter);
                    myRouter.updatePorts(newLinkDesc, linkIndex);

                    System.out.println(">> set " + connectedSimIP + " state to TWO_WAY;");
                    break;
                }
            }
        }


        private void handleLSUPDATE(SOSPFPacket sospfPacket) {
            log.info("LSUPDATE" + sospfPacket);
        }


    }


}
