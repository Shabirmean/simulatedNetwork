package socs.network.node;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by shabirmean on 2017-01-19.
 * Class for Router when acting as server.
 */
public class RouterServer{
    private final Log log = LogFactory.getLog(RouterServer.class);
    private ServerSocket serverSocket;
    private short serverPort;

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

    public void startRouterServer(final short port) {
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

        private ClientRequest(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            System.out.println("Got a new client!");

            // Do whatever required to process the client's request

            try {
                clientSocket.close();
                System.out.print(">> ");
            } catch (IOException e) {
                log.error("An error occurred whilst trying to close socket after serving request.");
            }
        }
    }
}
