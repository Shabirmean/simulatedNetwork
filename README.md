# Simulated Network

The source can be build and run in several terminal windows to initiate multiple router instances. The program requires a configuration file as an argument.

Sample configuration file can be found at inside the [conf](https://github.com/Shabirmean/simulatedNetwork/blob/master/conf/router1.conf) directory. The program expects two specific configs from the configuration file:
```configuration
socs.network.router.ip = <THE SIMULATED IP ADDRESS TO BE GIVEN TO IDENTIFY THIS ROUTER>
socs.network.router.port = <THE PORT IN WHICH THIS ROUTER IS TO BE STARTED>
```

_Once the router is started the following commands can be issued to do create different network topologies:_

* **attach [Process IP] [Process Port] [IP Address] [Link Weight]:** 
```configuration
this command establishs a link to the remote router which is identified by [IP Address]. After you start a router program instance, the first thing you have to do is to run attach command to establish the new links to the other routers. This operation is implemented by adding the new Link object in the ports array. In this command, besides the Process IP/Port and simulated IP Address, you also need to specify the "Link Weight" which is the cost for transmitting data through this link and is useful when you calculate the shortest path to the certain destination.
```

* **start:**
```configuration
start this router and initialize the database synchronization process. After you establish the links by running attach , you will run start command to send HELLO messages and LSAUPDATE to all connected routers for the Link State Database synchronization. This operation will be illustrated in the next section.
```

* **connect [Process IP] [Process Port] [IP Address] [Link Weight]:**
```configuration
similar to attach command, but it directly triggers the database synchronization without the necessary to run start (this command can only be run after start ).
```

* **disconnect [Port Number]:**
```configuration
remove the link between this router and the remote one which is connected at port [Port Number] (port number is between 0 - 3, i.e. four links in the router). Through this command, you are triggering the synchronization of Link State Database by sending LSAUPDATE (Link State Advertisement Update) message to all neighbors in the topology. This process will also be illustrated in the next section.
```

* **detect [IP Address]:**
```configuration
output the routing path from this router to the destination router which is identified by [IP Address].
```

* **neighbors:**
```configuration
output the IP Addresses of all neighbors of the router where you run this command.
```

* **quit:**
```configuration
exit the program. NOTE, this will trigger the synchronization of link state database.
```

* **ports:**
```configuration
prints the details of each connection in all of its ports (denotes the ports of the simulated router).
```

* **lsd:**
```configuration
prints the Link State Database entries of this simulated router.
```

* **topology:**
```configuration
prints the topology graph that shows every connection in the simulated network and their weights. Also prints the routing table (calculated by running the Dijkstra's algorithm) for this router. 
```

This was done as part of the course requirement for **COMP535 - Computer Networks** at McGill University.

* Author - [Shabir Mohamed Abdul Samadh](https://www.linkedin.com/in/shabirmean/)
* Email - shabir_tck09@hotmail.com
