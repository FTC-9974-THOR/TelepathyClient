package org.ftc9974.thorcore.telepathyclient;


public class TestClient {

    public static void main(String[] args) throws Exception {
        //Socket socket = new Socket("127.0.0.1", 6387);
        TelepathyAPI.initialize("127.0.0.1", 6387);
        System.out.println("Waiting for message");

        /*BufferedInputStream inputStream = new BufferedInputStream(socket.getInputStream());
        while (true) {
            if (inputStream.available() > 0) {
                int i = inputStream.read();
                System.out.print(i);
                if ((i >= 32 && i <= 126) || (i >= 9 && i <= 13)) {
                    System.out.println("\t" + (char) i);
                } else {
                    System.out.println();
                }
            }
        }*/

        TelepathyAPI.addNewMessageListener(message -> {
            System.out.println("Message received");
            System.out.println(message.key);
            System.out.println(message.type);
            System.out.println(message.message);
            TelepathyAPI.shutdown();
        });
    }
}
