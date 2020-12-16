package server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerThread extends Thread {
    private Socket client;
    private ObjectInputStream in;// from client
    private ObjectOutputStream out;// to client
    private boolean isRunning = false;
    private Room currentRoom;// what room we are in, should be lobby by default
    private String clientName;
    private final static Logger log = Logger.getLogger(ServerThread.class.getName());
    public List<String> mutedList = new ArrayList<String>();

    public boolean isMuted(String clientName) {
    	for(String name: mutedList) {
    		if (name.equals(clientName)){
    			return true;
    		}
    	}
    	return false;
    }
    
    //will create a mute file and write the client names from the muted list
    protected synchronized void createMuteFile(String name) {
    	try {
    		String fileName = name+"MuteFile.txt";
			File f = new File(fileName);
			f.createNewFile();
			FileWriter fw = new FileWriter(fileName);
			
			if(!mutedList.isEmpty()) {
				for(String clientName : mutedList) {
					fw.write(clientName + " ");
				}
			}
			fw.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    //will try to read from the muted file and add the names to the muted list
    protected synchronized void readMuteFile(String name) {
    	try {
    		String fileName = name+"MuteFile.txt";
    		File f = new File(fileName);
    		Scanner scan = new Scanner(f);
    		while (scan.hasNextLine()) {
				String[] mutedClients = scan.nextLine().split(" ");
				for(String client : mutedClients) {
					mutedList.add(client);
				}
    		}
    		scan.close();
				
    	}catch (FileNotFoundException e) {
			e.printStackTrace();
		}
    }
    
    public String getClientName() {
	return clientName;
    }

    protected synchronized Room getCurrentRoom() {
	return currentRoom;
    }

    protected synchronized void setCurrentRoom(Room room) {
	if (room != null) {
	    currentRoom = room;
	}
	else {
	    log.log(Level.INFO, "Passed in room was null, this shouldn't happen");
	}
    }

    public ServerThread(Socket myClient, Room room) throws IOException {
	this.client = myClient;
	this.currentRoom = room;
	out = new ObjectOutputStream(client.getOutputStream());
	in = new ObjectInputStream(client.getInputStream());
    }

    /***
     * Sends the message to the client represented by this ServerThread
     * 
     * @param message
     * @return
     */
    @Deprecated
    protected boolean send(String message) {
	// added a boolean so we can see if the send was successful
	try {
	    out.writeObject(message);
	    return true;
	}
	catch (IOException e) {
	    log.log(Level.INFO, "Error sending message to client (most likely disconnected)");
	    e.printStackTrace();
	    cleanup();
	    return false;
	}
    }

    /***
     * Replacement for send(message) that takes the client name and message and
     * converts it into a payload
     * 
     * @param clientName
     * @param message
     * @return
     */
    protected boolean send(String clientName, String message) {
    	//checking if there are multiple text style triggers in the message
		int boldCount = 0;
		int italicsCount = 0;
		int underLineCount = 0;
		
		for(int i=0;i<message.length();i++) {
			if(message.charAt(i)=='*') {
				boldCount++;
			}else if(message.charAt(i)=='~') {
				italicsCount++;
			}else if(message.charAt(i)=='_') {
				underLineCount++;
			}
		}
		
		//pairs of triggers replaced with respective html tags
		if(boldCount>=2) {
			message = message+" ";
			message = message.replace("*", "<b>");
			message = message.replace("<b> ","</b> ");
		}
		if(italicsCount>=2) {
			message = message+" ";
			message = message.replace("~", "<i>");
			message = message.replace("<i> ","</i> ");
		}
		if(underLineCount>=2) {
			message = message+" ";
			message = message.replace("_", "<u>");
			message = message.replace("<u> ","</u> ");
		}
		
		
	//colors
		int colorCount = 0;
	    for(int i=0;i<message.length();i++) {
				if(message.charAt(i)=='%') {
					colorCount++;
				}
			}
			
			//pairs of color triggers replaced with appropriate html
			if(colorCount%2==0) {
				message = message+" ";
				message = message.replace("% ", "</font> ");
				
				String[] words = message.split(" ");
				message = "";
				for(String word : words){
		
				    if(word.contains("%")){
				        int trigger = word.indexOf('%');
				        String color = word.substring(0,trigger);
				        String colorStyle = "<font color="+color+">";
				        String replace = word.substring(0,trigger+1);
				        word = word.replace(replace,colorStyle);
				    }
				
				message = message + word+" ";
				}
			}
		
	Payload payload = new Payload();
	payload.setPayloadType(PayloadType.MESSAGE);
	payload.setClientName(clientName);
	payload.setMessage(message);

	return sendPayload(payload);
    }

    protected boolean sendConnectionStatus(String clientName, boolean isConnect, String message) {
	Payload payload = new Payload();
	if (isConnect) {
	    payload.setPayloadType(PayloadType.CONNECT);
	    payload.setMessage(message);
	    //reads muted file if exists
	    readMuteFile(clientName);
	}
	else {
		//creates the muted file for the client
	    createMuteFile(clientName);
	    payload.setPayloadType(PayloadType.DISCONNECT);
	    payload.setMessage(message);
	    
	}
	payload.setClientName(clientName);
	return sendPayload(payload);
    }

	protected boolean sendClearList() {
		Payload payload = new Payload();
		payload.setPayloadType(PayloadType.CLEAR_PLAYERS);
		return sendPayload(payload);
	}
    
    protected boolean sendRoom(String room) {
	Payload payload = new Payload();
	// using same payload type as a response trigger
	payload.setPayloadType(PayloadType.GET_ROOMS);
	payload.setMessage(room);
	return sendPayload(payload);
    }

    private boolean sendPayload(Payload p) {
	try {
	    out.writeObject(p);
	    return true;
	}
	catch (IOException e) {
	    log.log(Level.INFO, "Error sending message to client (most likely disconnected)");
	    e.printStackTrace();
	    cleanup();
	    return false;
	}
    }

    /***
     * Process payloads we receive from our client
     * 
     * @param p
     */
    private void processPayload(Payload p) {
	switch (p.getPayloadType()) {
	case CONNECT:
	    // here we'll fetch a clientName from our client
	    String n = p.getClientName();
	    if (n != null) {
		clientName = n;
		
		//tries to read contents of a muted file based on client name
		readMuteFile(n);
		
		log.log(Level.INFO, "Set our name to " + clientName);
		if (currentRoom != null) {
		    currentRoom.joinLobby(this);
		}
	    }
	    break;
	case DISCONNECT:
		//when a client disconnects, their muted list will be saved in a file
		createMuteFile(p.getClientName());
		
	    isRunning = false;// this will break the while loop in run() and clean everything up
	    break;
	case MESSAGE:
	    currentRoom.sendMessage(this, p.getMessage());
	    break;
	case GET_ROOMS:
	    // far from efficient but it works for example sake
	    List<String> roomNames = currentRoom.getRooms();
	    Iterator<String> iter = roomNames.iterator();
	    while (iter.hasNext()) {
		String room = iter.next();
		if (room != null && !room.equalsIgnoreCase(currentRoom.getName())) {
		    if (!sendRoom(room)) {
			// if an error occurs stop spamming
			break;
		    }
		}
	    }
	    break;
	case JOIN_ROOM:
	    currentRoom.joinRoom(p.getMessage(), this);
	    break;
	default:
	    log.log(Level.INFO, "Unhandled payload on server: " + p);
	    break;
	}
    }

    @Override
    public void run() {
	try {
	    isRunning = true;
	    Payload fromClient;
	    while (isRunning && // flag to let us easily control the loop
		    !client.isClosed() // breaks the loop if our connection closes
		    && (fromClient = (Payload) in.readObject()) != null // reads an object from inputStream (null would
									// likely mean a disconnect)
	    ) {
		System.out.println("Received from client: " + fromClient);
		processPayload(fromClient);
	    } // close while loop
	}
	catch (Exception e) {
	    // happens when client disconnects
	    e.printStackTrace();
	    log.log(Level.INFO, "Client Disconnected");
	}
	finally {
	    isRunning = false;
	    log.log(Level.INFO, "Cleaning up connection for ServerThread");
	    cleanup();
	}
    }

    private void cleanup() {
	if (currentRoom != null) {
	    log.log(Level.INFO, getName() + " removing self from room " + currentRoom.getName());
	    currentRoom.removeClient(this);
	}
	if (in != null) {
	    try {
		in.close();
	    }
	    catch (IOException e) {
		log.log(Level.INFO, "Input already closed");
	    }
	}
	if (out != null) {
	    try {
		out.close();
	    }
	    catch (IOException e) {
		log.log(Level.INFO, "Client already closed");
	    }
	}
	if (client != null && !client.isClosed()) {
	    try {
		client.shutdownInput();
	    }
	    catch (IOException e) {
		log.log(Level.INFO, "Socket/Input already closed");
	    }
	    try {
		client.shutdownOutput();
	    }
	    catch (IOException e) {
		log.log(Level.INFO, "Socket/Output already closed");
	    }
	    try {
		client.close();
	    }
	    catch (IOException e) {
		log.log(Level.INFO, "Client already closed");
	    }
	}
    }
}