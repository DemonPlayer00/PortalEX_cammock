package moe.fuqiuluo.cammockproducer;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.FileDescriptor;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 标准选片接口：向 system_server 请求一次 SELECT，接收选中的视频 fd 与起播时间。
 * 与 OnePlus provider 使用同一套 CameraMockSelectionServer 协议。
 */
final class SelectionClient {
    static final String SOCKET = "portalex_camera_mock";

    static final class Result {
        FileDescriptor fd;
        long startMs;
        String videoId = "";
        String path = "";
    }

    static Result request() {
        LocalSocket socket = new LocalSocket();
        try {
            socket.connect(new LocalSocketAddress(
                    SOCKET, LocalSocketAddress.Namespace.ABSTRACT));
            socket.setSoTimeout(3000);
            String session = "mi6-producer-" + android.os.Process.myPid();
            String req = "SELECT\tmoe.fuqiuluo.cammockproducer\t\t\t"
                    + System.currentTimeMillis() + "\t\t" + session + "\n";
            OutputStream out = socket.getOutputStream();
            out.write(req.getBytes("UTF-8"));
            out.flush();

            byte[] buf = new byte[2048];
            InputStream in = socket.getInputStream();
            int n = in.read(buf);
            if (n <= 0) return null;
            String line = new String(buf, 0, n, "UTF-8").trim();
            if (!line.startsWith("OK\t")) return null;

            FileDescriptor[] fds = socket.getAncillaryFileDescriptors();
            if (fds == null || fds.length == 0 || fds[0] == null) return null;

            String[] parts = line.split("\t");
            Result r = new Result();
            r.fd = fds[0];
            r.videoId = parts.length > 2 ? parts[2] : "";
            r.startMs = parts.length > 4 ? parseLong(parts[4]) : 0L;
            r.path = parts.length > 6 ? parts[6] : "";
            return r;
        } catch (Throwable ignored) {
            return null;
        } finally {
            try { socket.close(); } catch (Throwable ignored) {}
        }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Throwable ignored) { return 0L; }
    }
}
