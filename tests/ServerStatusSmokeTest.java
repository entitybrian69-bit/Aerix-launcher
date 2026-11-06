package net.kdt.pojavlaunch.aerix.servers;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class ServerStatusSmokeTest {
    static void check(boolean condition, String msg) { if (!condition) throw new AssertionError(msg); }
    static void valid(String value) { check(ServerAddress.parse(value) != null, "should accept " + value); }
    static void invalid(String value) { check(ServerAddress.parse(value) == null, "should reject " + value); }
    public static void main(String[] args) throws Exception {
        valid("play.example.com"); valid("localhost:25565"); valid("127.0.0.1:1234"); valid("[::1]:25566");
        invalid("https://example.com"); invalid("a:0"); invalid("a:65536"); invalid("a:"); invalid("::1"); invalid("[:::1]"); invalid("a/path"); invalid("a b");
        check(ServerAddress.parse("play.example.com").shouldResolveSrv(), "hostname SRV");
        check(!ServerAddress.parse("play.example.com:25565").shouldResolveSrv(), "explicit port bypasses SRV");
        check(!ServerAddress.parse("127.0.0.1").shouldResolveSrv(), "IP bypasses SRV");
        ServerStatusClient.Status parsed = ServerStatusClient.parse("{\"version\":{\"name\":\"1.8 - 1.21\",\"protocol\":767},\"players\":{\"online\":5,\"max\":20,\"sample\":[{\"name\":\"Alex\"}]},\"description\":{\"text\":\"§aHi \",\"extra\":[{\"text\":\"there\"}]}}");
        check("Hi there".equals(parsed.motd), "component/formatting");
        check("1.8 - 1.21".equals(parsed.version) && parsed.online == 5 && parsed.max == 20, "reported fields");
        check(parsed.sample.size() == 1 && "Alex".equals(parsed.sample.get(0)), "sample");
        try { ServerStatusClient.parse("[".repeat(70) + "]".repeat(70)); throw new AssertionError("depth not rejected"); } catch (IOException expected) { }
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            final Throwable[] failure = new Throwable[1];
            Thread worker = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(2000);
                    DataInputStream in = new DataInputStream(socket.getInputStream()); DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    int n = ServerStatusClient.readVarInt(in); byte[] hs = new byte[n]; in.readFully(hs);
                    DataInputStream handshake = new DataInputStream(new ByteArrayInputStream(hs));
                    check(ServerStatusClient.readVarInt(handshake) == 0, "handshake id");
                    ServerStatusClient.readVarInt(handshake); int hostLen = ServerStatusClient.readVarInt(handshake); byte[] host = new byte[hostLen]; handshake.readFully(host);
                    check("127.0.0.1".equals(new String(host, StandardCharsets.UTF_8)), "original host in handshake");
                    check(handshake.readUnsignedShort() == server.getLocalPort(), "handshake port");
                    check(ServerStatusClient.readVarInt(handshake) == 1, "status state");
                    check(ServerStatusClient.readVarInt(in) == 1 && ServerStatusClient.readVarInt(in) == 0, "status request");
                    byte[] json = "{\"version\":{\"name\":\"custom\"},\"players\":{\"online\":2,\"max\":10},\"description\":\"Hello\"}".getBytes(StandardCharsets.UTF_8);
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream(); DataOutputStream packet = new DataOutputStream(buffer);
                    ServerStatusClient.writeVarInt(packet, 0); ServerStatusClient.writeVarInt(packet, json.length); packet.write(json);
                    ServerStatusClient.writeVarInt(out, buffer.size()); out.write(buffer.toByteArray()); out.flush();
                    check(ServerStatusClient.readVarInt(in) == 9 && ServerStatusClient.readVarInt(in) == 1, "ping request");
                    long token = in.readLong(); out.writeByte(9); out.writeByte(1); out.writeLong(token); out.flush();
                } catch (Throwable e) { failure[0] = e; }
            }); worker.start();
            ServerAddress address = ServerAddress.parse("127.0.0.1:" + server.getLocalPort());
            ServerStatusClient.Status status = ServerStatusClient.ping(address, null, 2000);
            worker.join(3000);
            check(!worker.isAlive(), "server thread completed");
            if (failure[0] != null) throw new AssertionError(failure[0]);
            check("custom".equals(status.version) && "Hello".equals(status.motd), "live status");
            check(status.online == 2 && status.max == 10 && status.pingMs != null, "live players and pong");
        }
        ByteArrayOutputStream b = new ByteArrayOutputStream(); DataOutputStream d = new DataOutputStream(b);
        d.writeShort(42); d.writeShort(0x8180); d.writeShort(1); d.writeShort(1); d.writeShort(0); d.writeShort(0);
        d.writeByte(10); d.writeBytes("_minecraft"); d.writeByte(4); d.writeBytes("_tcp"); d.writeByte(7); d.writeBytes("example"); d.writeByte(3); d.writeBytes("com"); d.writeByte(0); d.writeShort(33); d.writeShort(1);
        d.writeShort(0xc00c); d.writeShort(33); d.writeShort(1); d.writeInt(60); d.writeShort(8); d.writeShort(0); d.writeShort(10); d.writeShort(25570); d.writeShort(0xc01c);
        ServerStatusClient.Target target = SrvLookup.parse(b.toByteArray(),42);
        check(target != null && target.port == 25570 && "example.com".equals(target.host), "compressed SRV lookup");
        check(SrvLookup.parse(b.toByteArray(),43) == null, "DNS transaction id");
        System.out.println("Server status, handshake, pong, address and SRV tests passed");
    }
}
