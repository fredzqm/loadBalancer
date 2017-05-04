import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DistributedHashTable {
	private Set<String> addresses;
	private Map<String, String> map;

	private RequestParser requestParser;

	private InetAddress left;
	private InetAddress right;

	private DistributedHashTable() {
		this.addresses = new HashSet<>();
		this.map = new HashMap<>();
	}

	public InetAddress getLeft() {
		return left;
	}

	public void setLeft(InetAddress left) {
		this.left = left;
	}

	public InetAddress getRight() {
		return right;
	}

	public void setRight(InetAddress right) {
		this.right = right;
	}

	public void joinCluster(InetAddress entryNode) {
		requestParser = new RequestParser();
		JoinRequest.sendJoinRequest(entryNode);
	}

	public String get(String fileName) {
		if (map.containsKey(fileName)) {
			return map.get(fileName);
		}
		return null;
	}

	public String put(String fileName, String content) {
		if (map.containsKey(fileName)) {
			return "File already exists!";
		}
		map.put(fileName, content);
		return "File successfully added!";
	}

	public String remove(String fileName) {
		return map.remove(fileName);
	}

	private static DistributedHashTable table;

	public static DistributedHashTable getIntance() {
		if (table == null) {
			synchronized (DistributedHashTable.class) {
				if (table == null) {
					table = new DistributedHashTable();
				}
			}
		}
		return table;
	}

	public void checkNeighbor() {
		UDPServer.sendObject(new CheckAliveMessage(), right, RequestParser.PORT);
		UDPServer.sendObject(new CheckAliveMessage(), left, RequestParser.PORT);
		try {
			UDPServer.recieveBytes(right, CheckAliveMessage.CHECK_ALIVE_ACK_PORT, 100);
			UDPServer.recieveBytes(left, CheckAliveMessage.CHECK_ALIVE_ACK_PORT, 100);
			System.out.println("[INFO]: Both neight are up, left: " + this.left.getHostAddress() + " right: "
					+ this.right.getHostAddress());
		} catch (SocketTimeoutException e) {
			throw new RuntimeException(e);
		}
	}

}
