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

    protected LinkStateDatabase lsd;
    private RouterDescription rd = new RouterDescription();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    //assuming that all routers are with 4 ports
    private Link[] ports = new Link[RouterConstants.MAXIMUM_NO_OF_PORTS];

    private int noOfExistingLinks = 0;

    public Router(Configuration config, String ipAddress) {
        this.routerServer = new RouterServer(this);
        this.rd.processIPAddress = ipAddress;
        this.rd.simulatedIPAddress = config.getString("socs.network.router.ip");
        this.lsd = new LinkStateDatabase(rd);
    }

    public RouterDescription getRd() {
        return rd;
    }

    public Link[] getPorts() {
        return ports;
    }

    public int getNoOfExistingLinks() {
        return noOfExistingLinks;
    }

    public void setPorts(Link[] ports) {
        this.ports = ports;
    }

    public void setNoOfExistingLinks(int noOfExistingLinks) {
        this.noOfExistingLinks = noOfExistingLinks;
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
        if (noOfExistingLinks == RouterConstants.MAXIMUM_NO_OF_PORTS) {
            log.info("\nThis Router has already reached its maximum link-limit: " + RouterConstants
                    .MAXIMUM_NO_OF_PORTS +
                    "\nCannot add any more links.\n");
        } else {
            RouterDescription newRouterDescription = new RouterDescription();
            newRouterDescription.processIPAddress = processIP;
            newRouterDescription.processPortNumber = processPort;
            newRouterDescription.simulatedIPAddress = simulatedIP;

            Link newLink = new Link(this.rd, newRouterDescription, weight);
            this.ports[noOfExistingLinks++] = newLink;

            LinkDescription newLinkDescription = new LinkDescription();
            newLinkDescription.linkID = simulatedIP;
            newLinkDescription.portNum = processPort;
            //TODO:: Need to verify what's tosMetrics is....
            newLinkDescription.tosMetrics = 0;

            LSA currentLSA = this.lsd._store.get(rd.simulatedIPAddress);
            if (currentLSA == null) {
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
        Future[] arrayOfFuture = new Future[this.noOfExistingLinks];

        for (int linkIndex = 0; linkIndex < this.noOfExistingLinks; linkIndex++) {
            Link newLink = ports[linkIndex];
            RouterDescription destinationRouterDesc = newLink.getDestinationRouterDesc();
            final String destinationRouterHostIP = destinationRouterDesc.processIPAddress;
            final short destinationRouterHostPort = destinationRouterDesc.processPortNumber;
            final SOSPFPacket sospfPacket = RouterUtils.prepareHELLOPacket(this.rd, destinationRouterDesc);

            Callable<String> callable = new Callable<String>() {
                @Override
                public String call() {
                    //TODO:: Transfer hello messages...
                    Socket helloTransferSocket;
                    ObjectOutputStream socketWriter = null;
                    ObjectInputStream socketReader = null;

                    try {
                        helloTransferSocket = new Socket(destinationRouterHostIP, destinationRouterHostPort);
                    } catch (IOException e) {
                        log.error("An error occurred whilst trying to estanlish Socket connection to " +
                                "HOST [" + destinationRouterHostIP + "] at " +
                                "PORT [" + destinationRouterHostPort + "]", e);
                        return null;
                    }

                    try {
//                        socketWriter = new PrintWriter(helloTransferSocket.getOutputStream(), true);
//                        socketReader = new BufferedReader(
//                                new InputStreamReader(helloTransferSocket.getInputStream()));
                        socketWriter = new ObjectOutputStream(helloTransferSocket.getOutputStream());
                        socketReader = new ObjectInputStream(helloTransferSocket.getInputStream());

                        socketWriter.writeObject(sospfPacket);

                    } catch (IOException e) {
                        log.error("An error occurred whilst trying to READ/WRITE to Socket connection at " +
                                "HOST [" + destinationRouterHostIP + "] on " +
                                "PORT [" + destinationRouterHostPort + "]", e);
                    } finally {
                        RouterUtils.releaseSocket(helloTransferSocket);
                        RouterUtils.releaseWriter(socketWriter);
                        RouterUtils.releaseReader(socketReader);
                    }

                    return "destinationRouterIP";
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
                            log.warn("[HELLO EXCHANGE] to router connected to PORT [" + futureIndex + "] failed");
                        } else {
                            log.info("[HELLO EXCHANGE] completed with router connected to PORT " +
                                    "[" + futureIndex + "] with IP: " + helloFinishedRouterIP);

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
        executor.shutdown();
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

                } else if (command.startsWith("disconnect ")) {
                    String[] cmdLine = command.split(" ");
                    processDisconnect(Short.parseShort(cmdLine[1]));

                } else if (command.equals("neighbors")) {
                    //output neighbors
                    processNeighbors();

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
        }
    }

    public void startServer() {
        short port = nextFreePort(RouterConstants.MIN_PORT_NUMBER, RouterConstants.MAX_PORT_NUMBER);
        this.routerServer.startRouterServer(port);
        rd.processPortNumber = port;
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
