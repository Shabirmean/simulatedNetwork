package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class LinkStateDatabase {

    //linkID => LSAInstance
    HashMap<String, LSA> _store = new HashMap<String, LSA>();
    private RouterDescription rd = null;
    // a 2d array that constructs the existing topology with the weights
    private int[][] topologyArray;
    // an index map to get the index of a node in the topology array above
    Map<String, Integer> graphIndex;
    // shortest path array constructed from Dijkstra's algorithm
    private String[][] shortestDistanceArray;

    private static final String DISTANCE_TO_SELF = "" + 0;
    private static final String MAX_DISTANCE = "" + Short.MAX_VALUE;

    LinkStateDatabase(RouterDescription routerDescription) {
        rd = routerDescription;
        LSA l = initLinkStateDatabase();
        _store.put(l.linkStateID, l);
    }

    /**
     * output the shortest path from this router to the destination with the given IP address
     */
    String getShortestPath(String destinationIP) {
        //TODO: fill the implementation here
        return null;
    }

    //initialize the linkstate database by adding an entry about the router itself
    private LSA initLinkStateDatabase() {
        LSA lsa = new LSA();
        lsa.linkStateID = rd.simulatedIPAddress;
        lsa.lsaSeqNumber = Integer.MIN_VALUE;
        LinkDescription ld = new LinkDescription();
        ld.linkID = rd.simulatedIPAddress;
        ld.portNum = -1;
        ld.tosMetrics = 0;
        lsa.links.add(ld);
        return lsa;
    }


    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (LSA lsa : _store.values()) {
            sb.append(lsa.linkStateID).append("(" + lsa.lsaSeqNumber + ")").append(":\t");
            for (LinkDescription ld : lsa.links) {
                sb.append(ld.linkID).append(",").append(ld.portNum).append(",").
                        append(ld.tosMetrics).append("\t");
            }
            sb.append("\n");
        }
        return sb.toString();
    }


    void printLSD() {
        graphIndex = new HashMap<>();
        int indexCount = 0;

        // create a map of IPs of all nodes in the LinkStateDatabase and
        // their index in the topology array we are about to construct
        for (LSA lsa : _store.values()) {
            String routerIP = lsa.linkStateID;
            if (graphIndex.get(routerIP) == null) {
                graphIndex.put(routerIP, indexCount++);
            }

            for (LinkDescription linkDesc : lsa.links) {
                String linkRouterIP = linkDesc.linkID;
                if (graphIndex.get(linkRouterIP) == null) {
                    graphIndex.put(linkRouterIP, indexCount++);
                }
            }
        }

        // a 2d array that constructs the existing topology with the weights
        this.topologyArray = new int[indexCount][indexCount];

        // initilize all weights to -1 first (Hence -1 denotes no direct path exists between two nodes)
        for (int a = 0; a < topologyArray.length; a++) {
            for (int b = 0; b < topologyArray.length; b++) {
                topologyArray[a][b] = -1;
            }
        }

        // fill the topologyArray with the edge weights
        for (String routerId : graphIndex.keySet()) {
            int routerIndex = graphIndex.get(routerId);

            // set the distance to itself as zero
            topologyArray[routerIndex][routerIndex] = 0;
            LSA router_sLSA = _store.get(routerId);

            if (router_sLSA != null) {
                for (LinkDescription linkDesc : router_sLSA.links) {
                    String linkID = linkDesc.linkID;
                    int linkRouterIndex = graphIndex.get(linkID);
                    // set the weight from a node to another and vice versa.
                    topologyArray[routerIndex][linkRouterIndex] = linkDesc.tosMetrics;
                    topologyArray[linkRouterIndex][routerIndex] = linkDesc.tosMetrics;
                }
            }

        }

        // print the topology
        for (int a = 0; a < topologyArray.length; a++) {
            for (String keyString : graphIndex.keySet()) {
                if (graphIndex.get(keyString) == a) {
                    System.out.print(keyString + "    | ");
                }
            }

            for (int b = 0; b < topologyArray.length; b++) {
                System.out.print(topologyArray[a][b] + " | ");
            }
            System.out.println();
        }

        System.out.println();
        runDijkstraAlgo();
    }

    void runDijkstraAlgo() {
        shortestDistanceArray = new String[topologyArray.length][3];
        Vector<Integer> unvisitedVector = new Vector<>();
        Vector<Integer> visitedVector = new Vector<>();

        // initial setting up of the ShortestPath array
        for (String node : graphIndex.keySet()) {
            int nodeIndex = graphIndex.get(node);
            shortestDistanceArray[nodeIndex][0] = node;

            if (node.equals(rd.simulatedIPAddress)) {
                shortestDistanceArray[nodeIndex][1] = DISTANCE_TO_SELF;
            } else {
                shortestDistanceArray[nodeIndex][1] = MAX_DISTANCE;
            }
            unvisitedVector.add(nodeIndex);
        }

        int myIndex = graphIndex.get(rd.simulatedIPAddress);
        int[] myArray = topologyArray[myIndex];
        int currentIndex = myIndex;
        int[] currentArray = myArray;

        while (unvisitedVector.size() > 0) {
            for (int a = 0; a < currentArray.length; a++) {
                if (a != currentIndex && currentArray[a] != -1 && unvisitedVector.contains(a)) {
                    int weight = currentArray[a];
                    int currentDistance = Integer.parseInt(shortestDistanceArray[a][1]);
                    int newDistance = Integer.parseInt(shortestDistanceArray[currentIndex][1]) + weight;

                    if (newDistance < currentDistance) {
                        shortestDistanceArray[a][1] = "" + newDistance;
                        shortestDistanceArray[a][2] = shortestDistanceArray[currentIndex][0];
                    }
                }
            }

            int indexOfClosestVertex = -1;
            int shortestDistance = Short.MAX_VALUE;

            for (int b = 0; b < shortestDistanceArray.length; b++) {
                if (b != currentIndex && !visitedVector.contains(b)) {
                    if (Integer.parseInt(shortestDistanceArray[b][1]) < shortestDistance) {
                        shortestDistance = Integer.parseInt(shortestDistanceArray[b][1]);
                        indexOfClosestVertex = b;
                    }
                }
            }

            if (indexOfClosestVertex == -1) {
                break;
            }

            visitedVector.add(currentIndex);
            unvisitedVector.remove(new Integer(currentIndex));
            currentIndex = graphIndex.get(shortestDistanceArray[indexOfClosestVertex][0]);
            currentArray = topologyArray[currentIndex];
        }

        for (String[] aShortestDistanceArray : shortestDistanceArray) {
            System.out.println(
                    aShortestDistanceArray[0] + " | " +
                            aShortestDistanceArray[1] + " | " + aShortestDistanceArray[2]);
        }

    }

}

