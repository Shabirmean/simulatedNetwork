package socs.network.util;


public class RouterConstants {
    public static final short MAXIMUM_NO_OF_PORTS = 4;
    public static final short MIN_PORT_NUMBER = 2000;
    public static final short MAX_PORT_NUMBER = Short.MAX_VALUE;
    public static final long TIME_TO_LIVE_MILLIS = 1500;

    public static final short HELLO_PACKET = 0;
    public static final short LSUPDATE_PACKET = 1;
    public static final short EXIT_PACKET = 2;

    public static final String EXIT_STRING = "EXIT";
    public static final String LSUPDATE_STRING = "LSUPDATE";
    public static final String HELLO_STRING = "HELLO";
}
