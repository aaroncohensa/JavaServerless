package runners;

import runners.helpers.*;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;


public class ZooKeeperPeerServerImpl implements ZooKeeperPeerServer{
    private final InetSocketAddress myAddress;
    private final int myPort;
    private ServerState state;
    volatile boolean shutdown;
    private LinkedBlockingQueue<Message> outgoingMessages;
    private LinkedBlockingQueue<Message> incomingMessages;
    private LinkedBlockingQueue<Message> messageToClient;
    private Long id;
    private long peerEpoch;
    private volatile Vote currentLeader;
    private Map<Long,InetSocketAddress> peerIDtoAddress;

    private UDPMessageSender senderWorker;
    private UDPMessageReceiver receiverWorker;
    private RoundRobinLeader rr;
    JavaRunnerFollower jr;
    Logger logger = Logger.getLogger("ServerLogger");
    private FileHandler fh;



    public ZooKeeperPeerServerImpl(int myPort, long peerEpoch, Long id, Map<Long,InetSocketAddress> peerIDtoAddress){

        this.myPort = myPort;
        this.peerEpoch = peerEpoch;
        this.id = id;
        this.peerIDtoAddress = peerIDtoAddress;
        this.myAddress = new InetSocketAddress(myPort);


        this.messageToClient = new LinkedBlockingQueue<>();

        //code here...

        this.state = ServerState.LOOKING;
        this.outgoingMessages = new LinkedBlockingQueue<>();
        this.incomingMessages = new LinkedBlockingQueue<>();
        try {
            fh = new FileHandler(System.getProperty("user.dir") + File.separator + Thread.currentThread() + "" +
                    "logfile.log");
        } catch (IOException e) {
            e.printStackTrace();
        }

        fh.setLevel(Level.INFO);
        logger.addHandler(fh);
        SimpleFormatter formatter = new SimpleFormatter();
        fh.setFormatter(formatter);

    }

    @Override
    public void shutdown(){
        this.shutdown = true;
        this.senderWorker.shutdown();
        this.receiverWorker.shutdown();
    }



    @Override
    public void setCurrentLeader(Vote v) throws IOException {
        this.currentLeader = v;
    }

    @Override
    public Vote getCurrentLeader() {
        return this.currentLeader;
    }

    @Override
    public void sendMessage(Message.MessageType type, byte[] messageContents, InetSocketAddress target) throws IllegalArgumentException {

        //meessage contains your message type which woll bee election and also current vote
        Message msg = new Message(type , messageContents , "localhost" , myPort, target.getHostName() , target.getPort());
        outgoingMessages.add(msg);




    }


    public void sendMessageToClient(Message.MessageType type, byte[] messageContents, InetSocketAddress target, long requId) throws IllegalArgumentException {

        //meessage contains your message type which woll bee election and also current vote
        Message msg = new Message(type , messageContents , "localhost" , myPort, target.getHostName() , target.getPort(), requId);
        outgoingMessages.add(msg);




    }


    public void sendToClient(Message msg){

        this.messageToClient.add(msg);
    }

    @Override
    public void sendBroadcast(Message.MessageType type, byte[] messageContents) {

        //this will loop through the queue to sendmessage() to each queue meember
        // each server has its own internal queue
        for(Map.Entry<Long , InetSocketAddress> entry : peerIDtoAddress.entrySet()){
            InetSocketAddress sock = entry.getValue();
            sendMessage(type , messageContents , sock);
        }

    }

    @Override
    public ServerState getPeerState() {
        return this.state;
    }

    @Override
    public void setPeerState(ServerState newState) {
        this.state = newState;

    }

    @Override
    public Long getServerId() {
        return this.id;
    }

    @Override
    public long getPeerEpoch() {
        return this.peerEpoch;
    }

    @Override
    public InetSocketAddress getAddress() {
        InetSocketAddress ad = peerIDtoAddress.get(this.id);
        return ad;
    }

    @Override
    public int getUdpPort() {

        return this.myPort;
    }

    @Override
    public InetSocketAddress getPeerByID(long peerId) {

        InetSocketAddress ad = peerIDtoAddress.get(peerId);
        return ad;
    }

    @Override
    public int getQuorumSize() {
        return (peerIDtoAddress.size()/2);
    }

    public void run(){
        //step 1: create and run thread that sends broadcast messages
        //step 2: create and run thread that listens for messages sent to this server
        //step 3: main server loop
        logger.fine("Started run in ZKPSI");
        try{
            senderWorker = new UDPMessageSender(outgoingMessages , myPort);
            receiverWorker = new UDPMessageReceiver(incomingMessages , myAddress , myPort , this);
            senderWorker.start();
            receiverWorker.start();
            while (!this.shutdown){
                ZooKeeperLeaderElection election = new ZooKeeperLeaderElection(this , this.incomingMessages);
                switch (getPeerState()){
                    case LOOKING:
                        //start leader election, set leader to the election winner
                        setCurrentLeader(election.lookForLeader());
                        logger.fine("finished election");

                        break;
                    case LEADING:
                        //every time it is ccreating a new round robin guy
                        if (rr == null) {
                            logger.info("I'm a follower");


                            rr = new RoundRobinLeader(this, peerIDtoAddress, this.incomingMessages, this.outgoingMessages, this.senderWorker, this.receiverWorker);
                            rr.start();
                        }
                        break;
                    case FOLLOWING:
                        //every time it is ccreating a new follower


                        if(jr== null) {
                            logger.info("I'm a follower");
                            jr = new JavaRunnerFollower(this, this.incomingMessages, this.outgoingMessages);
                            jr.start();
                        }

                        break;
                    case OBSERVER:
                        break;
                }
            }
        }
        catch (Exception e) {
            //code...
            try {
                throw e;
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

}