package runners;

import runners.helpers.Message;
import runners.helpers.UDPMessageReceiver;
import runners.helpers.UDPMessageSender;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;


public class RoundRobinLeader extends Thread {


    private ZooKeeperPeerServerImpl leaderServer;
    private Map<Long, InetSocketAddress> allServers;
    private Map<Long, InetSocketAddress> workerMap;

    private Map<Long, Integer > clientToRequestId;
    private  LinkedBlockingQueue<Message> outgoingMessages;
    private  LinkedBlockingQueue<Message> incomingMessage;
    long [] workers;
    private int workerNum;
    private long requestId = 0L;
    Logger logger = Logger.getLogger("ServerLogger");




    public RoundRobinLeader(ZooKeeperPeerServerImpl server, Map<Long, InetSocketAddress> peerIDtoAddress, LinkedBlockingQueue<Message> incomingMessages, LinkedBlockingQueue<Message> outgoingMessages, UDPMessageSender senderWorker, UDPMessageReceiver receiverWorker) {
        this.leaderServer = server;
        this.allServers = peerIDtoAddress;
        this.clientToRequestId = new HashMap<>();
        this.incomingMessage = incomingMessages;
        this.outgoingMessages = outgoingMessages;

        this.setDaemon(true);

        this.workerMap = new HashMap<>(peerIDtoAddress);

        this.workerMap.remove(leaderServer.getServerId());

        makeWorkersList(workerMap);

        logger.info("RRL created");





    }

    private long getNextWorker() {
        long worker = this.workers[workerNum];
        workerNum = (workerNum + 1) % this.workers.length;

        return worker;//this is the point at which the word "worker" starts to look weird
    }

    private void makeWorkersList(Map<Long, InetSocketAddress> map) {
        this.workers = new long[map.keySet().size()];
        int i = 0;
        for (long l : map.keySet())//looks weird with the 1 outside but i needed 2 changing variables and this is easier
            this.workers[i++] = l;
        Arrays.sort(this.workers);//one time cost per epoch, helps with testing and organization

    }

    private Message changeMessageID(Message oldMessage, long id){
        Message newMessage = new Message(oldMessage.getMessageType(), oldMessage.getMessageContents(),
                oldMessage.getSenderHost(), oldMessage.getSenderPort(),
                oldMessage.getReceiverHost(), oldMessage.getReceiverPort(),
                id);
        return newMessage;
    }



    public void run(){

        Message message = null;
        logger.fine("started run in leader");

        while (!this.leaderServer.shutdown) {



            message = incomingMessage.poll();


            if (message != null) {
                logger.fine("incoming message received");
                if (message.getMessageType() == Message.MessageType.WORK && message.getRequestID() == -1) {

                    long nextServer = getNextWorker();
                    InetSocketAddress server = this.allServers.get(nextServer);
                        requestId+=1L;
                        clientToRequestId.put(requestId, message.getSenderPort());
                        Message msg = changeMessageID(message ,requestId );
                       leaderServer.sendMessageToClient(msg.getMessageType(), msg.getMessageContents(), server , msg.getRequestID());
                    logger.fine("sent message to next worker on port " + nextServer);




                } else if (message.getMessageType() == Message.MessageType.COMPLETED_WORK) {
                    InetSocketAddress in = new InetSocketAddress(message.getSenderHost(), clientToRequestId.get(message.getRequestID())); //need to see how to get inetsocketaddr out of message sender
                    leaderServer.sendMessage(message.getMessageType(), message.getMessageContents(), in);


                }
            }
        }




    }



}
