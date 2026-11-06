package net.kdt.pojavlaunch.aerix.servers;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Queries the device's configured DNS server for Minecraft SRV records. */
public final class SrvLookup {
    private SrvLookup() {}
    public static ServerStatusClient.Target resolve(String host, List<InetAddress> dnsServers, int timeoutMs) {
        if (dnsServers == null || dnsServers.isEmpty()) return null;
        try {
            int id = new Random().nextInt(65536);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeShort(id); out.writeShort(0x100); out.writeShort(1); out.writeShort(0); out.writeShort(0); out.writeShort(0);
            for (String part : ("_minecraft._tcp." + host).split("\\.")) {
                byte[] label = part.getBytes(StandardCharsets.US_ASCII);
                if (label.length == 0 || label.length > 63) return null;
                out.writeByte(label.length); out.write(label);
            }
            out.writeByte(0); out.writeShort(33); out.writeShort(1);
            byte[] query = bytes.toByteArray();
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.connect(new InetSocketAddress(dnsServers.get(0), 53));
                socket.setSoTimeout(timeoutMs);
                socket.send(new DatagramPacket(query, query.length));
                byte[] reply = new byte[4096]; DatagramPacket packet = new DatagramPacket(reply, reply.length);
                socket.receive(packet);
                return parse(Arrays.copyOf(reply, packet.getLength()), id);
            }
        } catch (IOException | RuntimeException ignored) { return null; }
    }
    static ServerStatusClient.Target parse(byte[] b, int id) throws IOException {
        if (b.length < 12 || u16(b,0) != id || (u16(b,2) & 0x820f) != 0x8000) return null;
        int questions=u16(b,4), answers=u16(b,6);
        if (questions != 1 || answers > 100) return null;
        int offset = skipName(b,12) + 4;
        int bestPriority = Integer.MAX_VALUE;
        List<ServerStatusClient.Target> targets = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        for (int i=0; i<answers; i++) {
            offset = skipName(b,offset);
            if (offset + 10 > b.length) throw new IOException("Bad DNS record");
            int type=u16(b,offset), clazz=u16(b,offset+2), len=u16(b,offset+8);
            int data=offset+10, end=data+len;
            if (end > b.length) throw new IOException("Bad DNS record length");
            if (type == 33 && clazz == 1 && len >= 7) {
                int priority=u16(b,data), weight=u16(b,data+2), port=u16(b,data+4);
                if (skipName(b, data+6) > end) throw new IOException("Bad SRV target");
                String target = readName(b,data+6);
                ServerAddress parsed = ServerAddress.parse(target + ":" + port);
                if (parsed != null && priority <= bestPriority) {
                    if (priority < bestPriority) { targets.clear(); weights.clear(); bestPriority=priority; }
                    targets.add(new ServerStatusClient.Target(parsed.host, parsed.port)); weights.add(weight);
                }
            }
            offset=end;
        }
        if (targets.isEmpty()) return null;
        int sum=0; for (int weight : weights) sum += weight;
        Random random = new Random();
        if (sum == 0) return targets.get(random.nextInt(targets.size()));
        int pick = random.nextInt(sum);
        for (int i=0; i<targets.size(); i++) { pick -= weights.get(i); if (pick < 0) return targets.get(i); }
        return targets.get(targets.size()-1);
    }
    private static int u16(byte[] b, int n) throws IOException {
        if (n < 0 || n+1 >= b.length) throw new IOException("Bad DNS offset");
        return ((b[n]&255)<<8) | (b[n+1]&255);
    }
    private static int skipName(byte[] b, int offset) throws IOException {
        for (int i=0; i<128; i++) {
            if (offset >= b.length) throw new IOException("Bad DNS name");
            int len=b[offset++]&255;
            if (len==0) return offset;
            if ((len&0xc0)==0xc0) { if (offset >= b.length) throw new IOException("Bad DNS pointer"); return offset+1; }
            if ((len&0xc0)!=0 || offset+len>b.length) throw new IOException("Bad DNS label");
            offset+=len;
        }
        throw new IOException("DNS name too long");
    }
    private static String readName(byte[] b, int offset) throws IOException {
        StringBuilder result=new StringBuilder();
        for (int i=0; i<128; i++) {
            if (offset >= b.length) throw new IOException("Bad DNS name");
            int len=b[offset++]&255;
            if (len==0) return result.toString();
            if ((len&0xc0)==0xc0) { if (offset>=b.length) throw new IOException("Bad DNS pointer"); offset=((len&0x3f)<<8)|(b[offset]&255); continue; }
            if ((len&0xc0)!=0 || offset+len>b.length || result.length()+len>253) throw new IOException("Bad DNS label");
            if (result.length()>0) result.append('.');
            result.append(new String(b,offset,len,StandardCharsets.US_ASCII)); offset+=len;
        }
        throw new IOException("DNS pointer loop");
    }
}
