package socs.network.node;

public class Link {
  private RouterDescription thisRouter;
  private RouterDescription endpointRouter;
  private short linkWeight;

  public Link(RouterDescription thisRouterDesc, RouterDescription endpointRouterDesc, short linkWeight) {
    this.thisRouter = thisRouterDesc;
    this.endpointRouter = endpointRouterDesc;
    this.linkWeight = linkWeight;
  }

  public RouterDescription getThisRouter() {
    return thisRouter;
  }

  public RouterDescription getEndpointRouter() {
    return endpointRouter;
  }

  public short getLinkWeight() {
    return linkWeight;
  }
}
