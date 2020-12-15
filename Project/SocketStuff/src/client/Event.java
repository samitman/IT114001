package client;


public interface Event {
    void onClientConnect(String clientName, String message);

    void onClientDisconnect(String clientName, String message);

    void onMessageReceive(String clientName, String message);

    void onChangeRoom();

    void onGetRoom(String roomName);
    
<<<<<<< HEAD
    void onIsMuted(String clientName, boolean isMuted);
    
=======
    void onIsMuted(String clientName);
>>>>>>> 05f2d0bc98513b3ab831d11223f62dce729dcb2f
}