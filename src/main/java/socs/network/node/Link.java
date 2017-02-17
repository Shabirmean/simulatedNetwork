package socs.network.node;

public class Link {
    private RouterDescription thisRouterDesc;
    private RouterDescription destinationRouterDesc;
    private short linkWeight;
    private int lastLSASeqNum;

    public Link(RouterDescription thisRouterDesc, RouterDescription destinationRouterDesc) {
        this.thisRouterDesc = thisRouterDesc;
        this.destinationRouterDesc = destinationRouterDesc;
        this.lastLSASeqNum = -1;
    }

    public Link(RouterDescription thisRouterDesc, RouterDescription destinationRouterDesc, short linkWeight) {
        this.thisRouterDesc = thisRouterDesc;
        this.destinationRouterDesc = destinationRouterDesc;
        this.linkWeight = linkWeight;
        this.lastLSASeqNum = -1;
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

    public int getLastLSASeqNum() {
        return lastLSASeqNum;
    }

    public void setLastLSASeqNum(int lastLSASeqNum) {
        this.lastLSASeqNum = lastLSASeqNum;
    }

    public void setLinkWeight(short linkWeight) {
        this.linkWeight = linkWeight;
    }

}
