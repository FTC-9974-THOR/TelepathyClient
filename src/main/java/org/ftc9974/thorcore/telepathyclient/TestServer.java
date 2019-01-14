package org.ftc9974.thorcore.telepathyclient;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class TestServer {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting server");
        ServerSocket serverSocket = new ServerSocket(6387);
        Socket socket = serverSocket.accept();
        System.out.println("Connected to Telepathy client");
        System.out.println(socket.getInetAddress().toString());

        OutputStream outputStream = socket.getOutputStream();
        System.out.println("Sending message");
        outputStream.write(0);
        outputStream.write(prepareForTransmit("TestKey", TelepathyAPI.Type.DOUBLE, ByteBuffer.allocate(8).putDouble(12.7896).array()));
        outputStream.write(0);
        outputStream.write(prepareForTransmit("Another key", TelepathyAPI.Type.STRING, "Hello, World!".getBytes()));
        outputStream.write(0);
        outputStream.write(prepareForTransmit("Time", TelepathyAPI.Type.STRING, Date.from(Instant.now()).toString().getBytes()));
        outputStream.write(0);
        outputStream.write(prepareForTransmit("Another key", TelepathyAPI.Type.STRING, "Now I'm different".getBytes()));
        outputStream.flush();
        Scanner scanner = new Scanner(System.in);
        double value = scanner.nextDouble();
        while (value >= 0) {
            outputStream.write(0);
            outputStream.write(prepareForTransmit("TestKey", TelepathyAPI.Type.DOUBLE, ByteBuffer.allocate(8).putDouble(value).array()));
            value = scanner.nextDouble();
        }
        outputStream.flush();
        socket.close();
    }

    private static byte[] prepareForTransmit(String key, TelepathyAPI.Type type, byte[] value) {
        byte[] keyBytes = key.getBytes();
        ByteBuffer messageBuffer = ByteBuffer.allocate(keyBytes.length + 1 + 4 + value.length);
        messageBuffer.put(keyBytes);
        //messageBuffer.put((byte) 0);
        messageBuffer.put(type.typeKey);
        messageBuffer.putInt(value.length);
        messageBuffer.put(value);
        return messageBuffer.array();
    }
}
