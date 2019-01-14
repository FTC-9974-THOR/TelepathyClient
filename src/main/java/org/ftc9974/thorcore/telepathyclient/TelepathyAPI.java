package org.ftc9974.thorcore.telepathyclient;

import org.apache.commons.lang3.ArrayUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TelepathyAPI {

    public enum Type {
        STRING(0, String.class),
        BYTE(1, byte.class),
        CHAR(2, char.class),
        SHORT(3, short.class),
        INT(4, int.class),
        FLOAT(5, float.class),
        LONG(6, long.class),
        DOUBLE(7, double.class);

        public byte typeKey;
        private Class clazz;

        Type(int typeKey, Class clazz) {
            this.typeKey = (byte) typeKey;
            this.clazz = clazz;
        }

        Class classForType() {
            return clazz;
        }

        static Type forByte(byte theByte) {
            switch (theByte) {
                case 0:
                    return STRING;
                case 1:
                    return BYTE;
                case 2:
                    return CHAR;
                case 3:
                    return SHORT;
                case 4:
                    return INT;
                case 5:
                    return FLOAT;
                case 6:
                    return LONG;
                case 7:
                default:
                    return DOUBLE;
            }
        }
    }

    public static class Message<T> {
        String key;
        Type type;
        T message;
        byte[] raw;

        Message(String key, Type type, T message, byte[] raw) {
            this.key = key;
            this.type = type;
            this.message = message;
            this.raw = raw;
        }
    }

    @FunctionalInterface
    public interface DataListener {

        void onNewMessage(Message<?> message);
    }

    private static Socket socket;

    private static Thread dataListener, keepAliveHandler;

    private static boolean hasFiredDCListeners;

    private static AtomicBoolean inUse;

    private static List<DataListener> listeners;
    private static List<Runnable> disconnectedListeners;

    private static AtomicBoolean connected;

    private static final Object listenerLock = new Object();

    public static void initialize(String ip, int port) throws IOException {
        socket = new Socket(ip, port);
        inUse = new AtomicBoolean(true);
        listeners = new ArrayList<>();
        disconnectedListeners = new ArrayList<>();
        connected = new AtomicBoolean(true);
        dataListener = new Thread(TelepathyAPI::serve, "TelepathyListener");
        dataListener.start();
        keepAliveHandler = new Thread(TelepathyAPI::handleKeepAlive, "TelepathyKeepAlive");
        keepAliveHandler.start();
    }

    public static boolean connected() {
        return socket != null && connected.get();
    }

    public static void addDisconnectionListener(Runnable listener) {
        disconnectedListeners.add(listener);
    }

    private static void serve() {
        try (BufferedInputStream inputStream = new BufferedInputStream(socket.getInputStream())) {
            List<List<Byte>> packets = new ArrayList<>();
            while (inUse.get()) {
                if (!connected() && !hasFiredDCListeners) {
                    for (Runnable disconnectedListener : disconnectedListeners) {
                        disconnectedListener.run();
                    }
                    hasFiredDCListeners = true;
                } else if (connected()) {
                    hasFiredDCListeners = false;
                }
                if (inputStream.available() > 0) {
                    byte b = (byte) inputStream.read();
                    if (b == 0) {
                        packets.add(new ArrayList<>());
                    }
                    for (List<Byte> packet : packets) {
                        packet.add(b);
                    }
                }
                List<List<Byte>> trimmedPackets = new ArrayList<>();
                for (int i = 0; i < packets.size() - 1; i++) {
                    if (validatePacket(packets.get(i))) {
                        List<Byte> packet = packets.get(i);
                        Message message = deserializeMessage(packet.subList(1, packet.size()));
                        synchronized (listenerLock) {
                            for (DataListener listener : listeners) {
                                listener.onNewMessage(message);
                            }
                        }
                    } else if (packets.get(i).stream().filter(b -> b == 0).count() < 20) {
                        trimmedPackets.add(packets.get(i));
                    }
                }
                if (!packets.isEmpty()) {
                    trimmedPackets.add(packets.get(packets.size() - 1));
                }
                packets.clear();
                packets.addAll(trimmedPackets);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleKeepAlive() {
        try (OutputStream outputStream = socket.getOutputStream()) {
            while (inUse.get()) {
                outputStream.write(0);
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            // keep alive refused, connection lost
            connected.set(false);
        }
    }

    public static void addNewMessageListener(DataListener listener) {
        synchronized (listenerLock) {
            listeners.add(listener);
        }
    }

    public static void shutdown() {
        inUse.set(false);
    }

    private static Message<?> deserializeMessage(List<Byte> bytes) {
        List<Byte> keyBytes = new ArrayList<>();
        for (byte aByte : bytes) {
            if (aByte < 32) {
                // check for control characters
                if (aByte < 9 || aByte > 13) {
                    break;
                }
            }
            keyBytes.add(aByte);
        }
        String key = new String(ArrayUtils.toPrimitive(keyBytes.toArray(new Byte[0])));
        ByteBuffer buffer = ByteBuffer.wrap(ArrayUtils.toPrimitive(bytes.subList(keyBytes.size(), bytes.size()).toArray(new Byte[0])));
        byte[] raw = ArrayUtils.toPrimitive(bytes.toArray(new Byte[0]));
        Type type = Type.forByte(buffer.get());
        switch (type) {
            case STRING:
                return new Message<>(key, type, new String(ArrayUtils.toPrimitive(bytes.subList(keyBytes.size() + 5, bytes.size()).toArray(new Byte[0]))), raw);
            case BYTE:
                buffer.getInt();
                return new Message<>(key, type, buffer.get(), raw);
            case CHAR:
                buffer.getInt();
                return new Message<>(key, type, buffer.getChar(), raw);
            case SHORT:
                buffer.getInt();
                return new Message<>(key, type, buffer.getShort(), raw);
            case INT:
                buffer.getInt();
                return new Message<>(key, type, buffer.getInt(), raw);
            case FLOAT:
                buffer.getInt();
                return new Message<>(key, type, buffer.getFloat(), raw);
            case LONG:
                buffer.getInt();
                return new Message<>(key, type, buffer.getLong(), raw);
            case DOUBLE:
                buffer.getInt();
                return new Message<>(key, type, buffer.getDouble(), raw);
            default:
                throw new RuntimeException("Invalid type received (this error should never happen)");
        }
    }

    static boolean validatePacket(List<Byte> bytes) {
        try {
            if (bytes.get(0) != 0) {
                // invalid header
                return false;
            }

            int keyLen;
            {
                List<Byte> keyBytes = new ArrayList<>();
                for (Byte aByte : bytes.subList(1, bytes.size())) {
                    if (aByte < 32) {
                        if (aByte < 9 || aByte > 13) {
                            break;
                        }
                    }
                    keyBytes.add(aByte);
                }
                if (keyBytes.isEmpty()) {
                    // empty key
                    return false;
                }
                keyLen = keyBytes.size();
            }

            Type type;
            int valueLen;
            {
                List<Byte> typeBytes = bytes.subList(keyLen + 1, bytes.size());
                if (typeBytes.get(0) <= 7) {
                    type = Type.forByte(typeBytes.get(0));
                } else {
                    // invalid type
                    return false;
                }

                ByteBuffer buffer = ByteBuffer.wrap(ArrayUtils.toPrimitive(typeBytes.toArray(new Byte[0])));
                buffer.get();
                valueLen = buffer.getInt();
            }

            {
                List<Byte> valueBytes = bytes.subList(1 + keyLen + 5, bytes.size());
                if (valueBytes.size() != valueLen) {
                    // invalid value
                    return false;
                }

                if (type == Type.STRING) {
                    for (Byte valueByte : valueBytes) {
                        if (valueByte < 32 && (valueByte < 9 || valueByte > 13)) {
                            // invalid characters
                            return false;
                        }
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
