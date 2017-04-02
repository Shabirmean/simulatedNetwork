package socs.network.util;


public class RouterConstants {
    public static final short MAXIMUM_NO_OF_PORTS = 4;
    public static final short MIN_PORT_NUMBER = 2000;
    public static final short MAX_PORT_NUMBER = Short.MAX_VALUE;
    public static final long TIME_TO_LIVE_MILLIS = 2000;
    public static final int SERVER_THREAD_POOL_COUNT = 100;

    public static final boolean QUITTER = true;

    public static final short HELLO_PACKET = 0;
    public static final short LSUPDATE_PACKET = 1;
    public static final short ATTACH_PACKET = 2;
    public static final short DISCONNECT_PACKET = 3;
    public static final short EXIT_PACKET = 4;

    public static final String EXIT_STRING = "EXIT";
    public static final String DISCONNECT_STRING = "DISCONNECT";
    public static final String LSUPDATE_STRING = "LSUPDATE";
    public static final String HELLO_STRING = "HELLO";
    public static final String ATTACH_STRING = "ATTACH";
}
