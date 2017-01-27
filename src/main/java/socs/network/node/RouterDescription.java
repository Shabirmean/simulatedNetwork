package socs.network.node;

public class RouterDescription {
  //used to socket communication
  public String processIPAddress;
  public short processPortNumber;
  //used to identify the router in the simulated network space
  public String simulatedIPAddress;
  //status of the router
  public RouterStatus status;
}
