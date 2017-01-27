package socs.network.util;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import socs.network.message.SOSPFPacket;
import socs.network.node.RouterDescription;

import java.io.*;
import java.net.Socket;

public class RouterUtils {
    private static final Log log = LogFactory.getLog(RouterUtils.class);

    public static SOSPFPacket prepareHELLOPacket(RouterDescription rd, RouterDescription destinationRouterDesc){
        SOSPFPacket sospfPacket = new SOSPFPacket();
        sospfPacket.srcProcessIP = rd.processIPAddress;
        sospfPacket.srcProcessPort = rd.processPortNumber;
        sospfPacket.srcIP = rd.simulatedIPAddress;
        sospfPacket.dstIP = destinationRouterDesc.simulatedIPAddress;
        sospfPacket.sospfType = 0;
        //TODO:: Check what routerID is
        sospfPacket.routerID = "DONT_KNOW";
        sospfPacket.neighborID = rd.simulatedIPAddress;
        return sospfPacket;
    }

    public static void releaseSocket(Socket socket){
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                log.error("Error occurred when attempted to close socket.");
            }
        }
    }

    public static void releaseWriter(PrintWriter socketWriter){
        if (socketWriter != null) {
            socketWriter.close();
        }
    }

    public static void releaseWriter(ObjectOutputStream socketWriter){
        if (socketWriter != null) {
            try {
                socketWriter.close();
            } catch (IOException e) {
                log.error("Error occurred when attempted to close ObjectOutputStream of socket.");
            }
        }
    }

    public static void releaseReader(BufferedReader socketReader){
        if (socketReader != null) {
            try {
                socketReader.close();
            } catch (IOException e) {
                log.error("Error occurred when attempted to close BufferedReader of socket.");
            }
        }

    }

    public static void releaseReader(ObjectInputStream socketReader){
        if (socketReader != null) {
            try {
                socketReader.close();
            } catch (IOException e) {
                log.error("Error occurred when attempted to close ObjectInputStream of socket.");
            }
        }

    }
}
