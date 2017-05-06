package request;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import networkUtility.IDatagramPacketListener;
import networkUtility.UDPServer;
import util.Logger;

/**
 * The centralized communication handler for Our nodes Through this module, each
 * node can send and receive UDP {@link Message}. Each message needs to define
 * whether it requires acknowledgement {@link Message#requireACK()} If this is
 * true, then {@link CommunicationHandler} should tracks whether the ACK is
 * received, and trigger a timeout when necessary
 * 
 * @author fredzqm
 *
 */
public class CommunicationHandler implements IDatagramPacketListener, Runnable {
	public static final int REQUEST_PARSER_PORT = 4444;

	private static CommunicationHandler instance;

	private final int PORT;
	private UDPServer server;
	private Map<Integer, Message> ackWaiting;
	private Random random;
	private PriorityQueue<UnACKeDMessage> unACKedMessages;

	/**
	 * constructs a communication handler at
	 * 
	 * @param PORT
	 */
	private CommunicationHandler() {
		this.PORT = REQUEST_PARSER_PORT;
		this.ackWaiting = new ConcurrentHashMap<>();
		this.unACKedMessages = new PriorityQueue<>();
		this.random = new Random();
		this.server = new UDPServer(PORT, this);
	}

	/**
	 * start to listen at port {@link CommunicationHandler#PORT}
	 */
	public void start() {
		this.server.start();
		new Thread(this).start();
	}

	public static CommunicationHandler getInstance() {
		if (instance == null) {
			synchronized (CommunicationHandler.class) {
				if (instance == null) {
					instance = new CommunicationHandler();
				}
			}
		}
		return instance;
	}

	@Override
	public void onRecieved(DatagramPacket packet) {
		InetAddress addr = packet.getAddress();
		Message request = UDPServer.deSerializeObject(packet.getData(), Message.class);
		Logger.logInfo("recieving message %s from %s", request, packet.getAddress().getHostAddress());
		Message acknowledged = null;
		if (request.getACKID() != 0) {
			acknowledged = ackWaiting.remove(request.getACKID());
			if (acknowledged == null)
				Logger.logError("recieved ack for requestID %d but is not in the ackWaiting pool", request.getACKID());
		}
		request.handleRequest(addr, acknowledged);
	}

	/**
	 * send a message to an address. This message should be received by the
	 * {@link CommunicationHandler} there, and
	 * {@link Message#handleRequest(InetAddress)} should be called there
	 * 
	 * It this message requires ACK{@link Message#requireACK()}, and no ACK is
	 * received after certain time {@link Message#getTimeOut()},
	 * {@link Message#timeOut(InetAddress)} will be called
	 * 
	 * @param message
	 *            the message to be send
	 * @param address
	 *            the address to send it to
	 */
	public static void sendMessage(Message message, InetAddress address) {
		getInstance()._sendMessage(message, address);
	}

	/**
	 * Same as {@link CommunicationHandler#sendMessage(Message, InetAddress)}
	 * excepts it takes an IP and convert it into {@link InetAddress}
	 * 
	 * @param message
	 * @param IP
	 */
	public static void sendMessage(Message message, String IP) {
		try {
			sendMessage(message, InetAddress.getByName(IP));
		} catch (UnknownHostException e) {
			Logger.logError("IP %s was not found when sending messages", IP);
			e.printStackTrace();
		}
	}

	private void _sendMessage(Message message, InetAddress address) {
		if (message.requireACK()) {
			addToACKQueue(message, address);
		}
		Logger.logInfo("send message %s to %s", message, address.getHostAddress());
		UDPServer.sendObject(message, address, PORT);
	}

	private synchronized void addToACKQueue(Message message, InetAddress address) {
		int requestID = 0;
		while (requestID == 0 || ackWaiting.containsKey(requestID)) {
			requestID = random.nextInt();
		}
		message.setRequestID(requestID);
		this.ackWaiting.put(requestID, message);
		this.unACKedMessages.add(new UnACKeDMessage(message, address));
		this.notifyAll();
	}

	@Override
	public synchronized void run() {
		while (true) {
			while (this.unACKedMessages.isEmpty()) {
				try {
					this.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			long left = this.unACKedMessages.peek().getTime() - System.currentTimeMillis();
			if (left > 0) {
				try {
					this.wait(left);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			while (!this.unACKedMessages.isEmpty()) {
				UnACKeDMessage next = this.unACKedMessages.peek();
				if (!next.checkTimeOut(this.ackWaiting.keySet()))
					break;
				this.unACKedMessages.poll();
			}
		}
	}

	private static class UnACKeDMessage implements Comparable<UnACKeDMessage> {
		private Message message;
		private long time;
		private InetAddress address;

		public UnACKeDMessage(Message message, InetAddress address) {
			this.message = message;
			this.address = address;
			this.time = System.currentTimeMillis() + message.getTimeOut();
		}

		/**
		 * 
		 * @param ackWaiting
		 * @return true if this message's time is up and processed and should be
		 *         removed from the queue, false if the time is not up yet
		 */
		public boolean checkTimeOut(Set<Integer> ackWaiting) {
			if (this.time < System.currentTimeMillis()) {
				if (ackWaiting.contains(this.message.getRequestID())) {
					this.message.timeOut(address);
				}
				return true;
			}
			return false;
		}

		public long getTime() {
			return time;
		}

		@Override
		public int compareTo(UnACKeDMessage o) {
			return (int) (time - o.time);
		}

	}

}
