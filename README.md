# JavaServerless

**Overview**

Serverless Computing is a model of cloud computing in which the user of the service does not manage the computer / virtual machine on which their code runs. Instead, the user simply uploads his code to the cloud service, which takes care of all the setup and management necessary to run the code. Commercial examples of serverless include AWS Lambda, Microsoft Azure Functions, Google Cloud Functions and App Engine, and Cloudflare Workers.

This project is a service which provides serverless execution of Java code.

**Project Background**

This iteration of the project consists of two previous iterations combined, these two iterations were: 

 1. Build a simple client and server where the client sends Java code to the server, the server processes the code and returns the result.
 2. Implementing the Zookeeper Leader Election algorithm. This algorithm is needed for consensus & coordination among nodes in a distributed system. Example below:
 <img width="1213" alt="Screen Shot 2022-10-26 at 3 02 31 PM" src="https://user-images.githubusercontent.com/38955508/198113917-1cef2500-53d8-4367-9ec8-94e0952e2213.png">
 
 **Project Breakdown**

This iteration of the project will create a cluster of ZooKeeperPeerServers that provide a service which allows clients to submit Java source code for “serverless” execution. The cluster will elect a leader, and the elected leader will coordinate cluster activity in order to fulfill client requests.

The cluster of nodes will use the Master-Worker architecture. The server that wins the ZooKeeper election to be the leader will function as the master, and the other servers will function as workers. The leader/master assigns tasks to the workers on a round-robin basis.  The leader/master is the only server that:
- accepts requests from clients
- sends replies to clients
- assigns client requests to worker nodes that can do the work necessary to fulfill the requests.

<img width="464" alt="Screen Shot 2022-10-26 at 3 10 07 PM" src="https://user-images.githubusercontent.com/38955508/198115370-ec69a0fd-299f-4060-9b2e-510932bbe52f.png">
