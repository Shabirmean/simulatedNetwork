package socs.network.util;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import socs.network.message.SOSPFPacket;
import socs.network.node.Link;
import socs.network.node.RouterDescription;

import java.io.*;
import java.net.Socket;
import java.sql.Time;

public class RouterUtils {
    private static final Log log = LogFactory.getLog(RouterUtils.class);

    public static SOSPFPacket createNewPacket(RouterDescription rd, String dstIP, short packetType) {
        SOSPFPacket sospfPacket = new SOSPFPacket();

        sospfPacket.timeToLive = RouterConstants.TIME_TO_LIVE_MILLIS;
        sospfPacket.srcProcessIP = rd.processIPAddress;
        sospfPacket.srcProcessPort = rd.processPortNumber;
        sospfPacket.srcIP = rd.simulatedIPAddress;

        sospfPacket.dstIP = dstIP;
        sospfPacket.sospfType = packetType;

        switch (packetType) {
            case RouterConstants.HELLO_PACKET:
                sospfPacket.neighborID = rd.simulatedIPAddress;
                break;
            case RouterConstants.LSUPDATE_PACKET:
                sospfPacket.routerID = rd.simulatedIPAddress;
                break;
        }
        return sospfPacket;
    }

//    public static SOSPFPacket preparePacket(String dstIP,  SOSPFPacket sospfPacket){
//        sospfPacket.dstIP = dstIP;
//        return sospfPacket;
//    }

    public static SOSPFPacket updatePacket(RouterDescription rd, String dstIP, SOSPFPacket sospfPacket){
        sospfPacket.srcProcessIP = rd.processIPAddress;
        sospfPacket.srcProcessPort = rd.processPortNumber;
        sospfPacket.srcIP = rd.simulatedIPAddress;
        sospfPacket.dstIP = dstIP;
        sospfPacket.neighborID = rd.simulatedIPAddress;
        sospfPacket.timeToLive -= ((System.currentTimeMillis() - sospfPacket.makeTime)/1000);
        return sospfPacket;
    }

    public static void releaseSocket(Socket socket){
        if (socket != null) {
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                log.error("Error occurred when attempted to close socket.");
            }
        }
    }

    public static void releaseWriter(PrintWriter socketWriter){
        if (socketWriter != null) {
            socketWriter.flush();
            socketWriter.close();
        }
    }

    public static void releaseWriter(ObjectOutputStream socketWriter){
        if (socketWriter != null) {
            try {
                socketWriter.flush();
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
