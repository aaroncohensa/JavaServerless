package runners.helpers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static runners.helpers.ZooKeeperPeerServer.ServerState.*;

public class ZooKeeperLeaderElection
{
    /**
     * time to wait once we believe we've reached the end of leader election.
     */
    private final static int finalizeWait = 200;

    /**
     * Upper bound on the amount of time between two consecutive notification checks.
     * This impacts the amount of time to get the system up again after long partitions. Currently 60 seconds.
     */
    private final static int maxNotificationInterval = 60000;


    ZooKeeperPeerServer myPeerServer;
    LinkedBlockingQueue<Message> incomingMessages;
     HashMap<Long , Integer> voteToVotes = new HashMap<>();
    private long proposedLeader;
    private long proposedEpoch;

    public ZooKeeperLeaderElection(ZooKeeperPeerServer server, LinkedBlockingQueue<Message> incomingMessages)
    {
        this.incomingMessages = incomingMessages;
        this.myPeerServer = server;
        this.proposedLeader = server.getServerId();
        this.proposedEpoch = server.getPeerEpoch();

    }

     synchronized static ElectionNotification getNotificationFromMessage(Message received) {

        ByteBuffer msgBytes = ByteBuffer.wrap(received.getMessageContents());
        long leader = msgBytes.getLong();
        char stateChar = msgBytes.getChar();
        long senderID = msgBytes.getLong();
        long peerEpoch = msgBytes.getLong();



        ElectionNotification not = new ElectionNotification(leader, ZooKeeperPeerServer.ServerState.getServerState(stateChar) , senderID, peerEpoch);

        return not;


    }
//
    public static byte[] buildMsgContent(ElectionNotification notification) {


        long leader = notification.getProposedLeaderID();
        long sender = notification.getSenderID();
        long epoch = notification.getPeerEpoch();
        ByteBuffer buf = ByteBuffer.allocate(Long.BYTES * 3 + Character.BYTES);
        buf.putLong(0, leader);
        buf.putChar(8,notification.getState().getChar());
        buf.putLong(10, sender);
        buf.putLong(18, epoch);
        return buf.array();

    }





    private synchronized Vote getCurrentVote() {
        return new Vote(this.proposedLeader, this.proposedEpoch);
    }

    public synchronized Vote lookForLeader()
    {
        //send initial notifications to other peers to get things started
        ElectionNotification notification = new ElectionNotification(myPeerServer.getServerId() , myPeerServer.getPeerState() , myPeerServer.getServerId() , myPeerServer.getPeerEpoch());

        myPeerServer.sendBroadcast(Message.MessageType.ELECTION , buildMsgContent(notification));
        //Loop, exchanging notifications with other servers until we find a leader
        Message message = null;

        int tTime = finalizeWait;
        while (this.myPeerServer.getPeerState() == LOOKING) {
            //Remove next notification from queue, timing out after 2 times the termination time


            try {
                message = incomingMessages.poll(tTime , TimeUnit.MILLISECONDS);
                tTime *= tTime;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //if no notifications received..
            //..resend notifications to prompt a reply from others..
            //.and implement exponential back-off when notifications not received..
            //if/when we get a message and it's from a valid server and for a valid server..
            //switch on the state of the sender:


            if (message != null) {

                tTime = 200;
                ElectionNotification not = getNotificationFromMessage(message);





                if (not.getState() == LOOKING) {


                    assert not != null;

                    if (supersedesCurrentVote(not.getProposedLeaderID(), not.getPeerEpoch())) {

                        voteToVotes.putIfAbsent(not.getProposedLeaderID() , 0);
                        voteToVotes.put(not.getProposedLeaderID(), voteToVotes.get(not.getProposedLeaderID()) + 1);


                        this.proposedLeader = not.getProposedLeaderID();

                        ElectionNotification newVote = new ElectionNotification(not.getProposedLeaderID(), myPeerServer.getPeerState(), myPeerServer.getServerId(), myPeerServer.getPeerEpoch());

                        myPeerServer.sendBroadcast(Message.MessageType.ELECTION, buildMsgContent(newVote));


                    }
                    else if((not.getProposedLeaderID() == this.proposedLeader)) {
                        voteToVotes.merge(not.getProposedLeaderID(), 1, (a, b) -> a + b);


                        if (incomingMessages.isEmpty() && voteToVotes.get(not.getProposedLeaderID()) != null && voteToVotes.get(this.proposedLeader) > myPeerServer.getQuorumSize()) {

                            return acceptElectionWinner(not);
                        }

                    }

                } else if (not.getState() == FOLLOWING || not.getState() == LEADING) {

                    return acceptElectionWinner(not);
                }

            }


        }

        return  null;

        }




    private Vote acceptElectionWinner(ElectionNotification not)
    {
        //set my state to either LEADING or FOLLOWING
        //clear out the incoming queue before returning

        if (not.getProposedLeaderID() == this.myPeerServer.getServerId()){
            this.myPeerServer.setPeerState(ZooKeeperPeerServer.ServerState.LEADING);
            Vote vt = new Vote(this.proposedLeader , this.proposedEpoch);
            //TODO need to call roundrobinjava because this server is the leader and needs to start the roundrobin process
            return vt;
        }else {
            this.myPeerServer.setPeerState(ZooKeeperPeerServer.ServerState.FOLLOWING);
            //TODO need to add this to thee workers list
            Vote v = new Vote(not.getProposedLeaderID() , not.getPeerEpoch());
            try {
                myPeerServer.setCurrentLeader(v);
            } catch (IOException e) {
                e.printStackTrace();
            }

            Vote vt = new Vote(not.getProposedLeaderID() , not.getPeerEpoch());
            return vt;

        }
    }

    /*
     * We return true if one of the following three cases hold:
     * 1- New epoch is higher
     * 2- New epoch is the same as current epoch, but server id is higher.
     */
    protected boolean supersedesCurrentVote(long newId, long newEpoch) {
        return (newEpoch > this.proposedEpoch) || ((newEpoch == this.proposedEpoch) && (newId > this.proposedLeader));
    }
    /**
     * Termination predicate. Given a set of votes, determines if have sufficient support for the proposal to declare the end of the election round.
     * Who voted for who isn't relevant, we only care that each server has one current vote
     */
    protected boolean haveEnoughVotes(Map<Long, ElectionNotification > votes, Vote proposal)
    {
        //is the number of votes for the proposal > the size of my peer server’s quorum?

        return false;
    }
}