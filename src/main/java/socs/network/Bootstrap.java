package socs.network;

import socs.network.node.RouterManager;

public class Bootstrap {

    public static void main(String[] args) {
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
        System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "info");
        System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
        System.setProperty("org.apache.commons.logging.simplelog.dateTimeFormat", "HH:mm:ss");

        RouterManager.getInstance().init(args);
    }
}
