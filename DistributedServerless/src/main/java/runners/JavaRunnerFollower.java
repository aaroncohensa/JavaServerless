package runners;

import runners.helpers.JavaRunner;
import runners.helpers.Message;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

public class JavaRunnerFollower extends Thread {


    private ZooKeeperPeerServerImpl worker;
    private LinkedBlockingQueue<Message> outgoingMessages;
    private LinkedBlockingQueue<Message> incomingMessages;
    Logger logger = Logger.getLogger("ServerLogger");

    public JavaRunnerFollower(ZooKeeperPeerServerImpl server, LinkedBlockingQueue<Message> incomingMessages, LinkedBlockingQueue<Message> outgoingMessages) {

        this.worker = server;
        this.incomingMessages = incomingMessages;
        this.outgoingMessages = outgoingMessages;
        logger.info("created JRF");


    }


    public void run() {



        logger.info("entered JRF run method");



        Message message = null;

        while (!this.worker.shutdown) {

            assert incomingMessages != null;

            message = incomingMessages.poll();


            if (message != null) {
                if (message.getMessageType() == Message.MessageType.WORK) {

                    logger.info("MESSAGE RECIEVED:\n" + message.toString());


                    InputStream targetStream = new ByteArrayInputStream(message.getMessageContents());

                    try {
                        JavaRunner jr = new JavaRunner();

                        String response = jr.compileAndRun(targetStream);

                        byte[] messageContents = response.getBytes();


                        InetSocketAddress leaderAddy = new InetSocketAddress(message.getSenderHost(), message.getSenderPort());

                        worker.sendMessageToClient(Message.MessageType.COMPLETED_WORK, messageContents, leaderAddy , message.getRequestID());

                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (ReflectiveOperationException e) {
                        e.printStackTrace();
                    }

                }


            }
        }
    }
}
