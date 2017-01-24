package socs.network.node;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * Created by shabirmean on 2017-01-19.
 * Class for Router when acting as client.
 */
public class RouterClient {
    private final Log log = LogFactory.getLog(RouterClient.class);
    private String hostIPAddress;
    private short hostPortNumber;
    private Socket clientSocket;

    public RouterClient(String processIPAddress, short processPortNumber) {
        this.hostIPAddress = processIPAddress;
        this.hostPortNumber = processPortNumber;
    }

    public Socket socket() {
        if (clientSocket == null) {
            try {
                clientSocket = new Socket(this.hostIPAddress, this.hostPortNumber);
            } catch (IOException e) {
                log.error("An error occurred whilst trying to establish connection to host at: " +
                        hostIPAddress + ":" + hostPortNumber);
            }
        }
        return this.clientSocket;
    }

    public void connect() throws IOException {
    }

    public void recv() {
    }

    public void send() {
    }

    public void close() {
    }

}
