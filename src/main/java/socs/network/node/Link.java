package socs.network.node;

public class Link {
  private RouterDescription thisRouterDesc;
  private RouterDescription destinationRouterDesc;
  private short linkWeight;

  public Link(RouterDescription thisRouterDesc, RouterDescription destinationRouterDesc) {
    this.thisRouterDesc = thisRouterDesc;
    this.destinationRouterDesc = destinationRouterDesc;
  }

  public Link(RouterDescription thisRouterDesc, RouterDescription destinationRouterDesc, short linkWeight) {
    this.thisRouterDesc = thisRouterDesc;
    this.destinationRouterDesc = destinationRouterDesc;
    this.linkWeight = linkWeight;
  }

  public RouterDescription getThisRouterDesc() {
    return thisRouterDesc;
  }

  public RouterDescription getDestinationRouterDesc() {
    return destinationRouterDesc;
  }

  public short getLinkWeight() {
    return linkWeight;
  }
}
