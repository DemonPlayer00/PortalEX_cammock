package moe.fuqiuluo.cammockproducer;

import android.app.Activity;
import android.content.Intent;
import android.media.Image;
import android.media.MediaCodec;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import java.io.ByteArrayOutputStream;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Arrays;

/** cammock 扩展：将库中第一个 MP4 解码成 cammock.frames 的 NV21 槽位。 */
public class ProducerActivity extends Activity {
    static final String TAG = "CammockProducer";
    static final String PATH = "/data/local/tmp/portalex/cammock.frames";
    static final int HDR = 4096, SLOTS = 3, SLOT_BYTES = 4 * 1024 * 1024;
    static final int MAGIC = 0x46584550, VERSION = 1;
    volatile boolean running = true;
    Thread worker;
    int photoReq = 0, photoSeq = 0;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        String supplied = getIntent().getStringExtra("token");
        String expected = "";
        try { expected = new String(java.nio.file.Files.readAllBytes(new File(getFilesDir(), "auth.token").toPath())).trim(); } catch (Throwable ignored) {}
        if (supplied == null || expected.isEmpty() || !supplied.equals(expected)) {
            Log.e(TAG, "producer auth rejected");
            finish();
            return;
        }
        String video = getIntent().getStringExtra("video");
        worker = new Thread(() -> decodeLoop(video), "cammock-producer");
        worker.start();
        // 不抢占 PortalEX UI；解码线程继续运行，Activity 退到后台。
        moveTaskToBack(true);
    }

    @Override protected void onDestroy() { running = false; super.onDestroy(); }

    String findVideo(String requested) {
        if (requested != null && new File(requested).isFile()) return requested;
        File dir = new File("/data/local/tmp/portalex/cammock/library");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".mp4"));
        if (files != null && files.length > 0) return files[0].getAbsolutePath();
        return null;
    }

    static byte[] rotate90(byte[] src, int w, int h) {
        int nw = h, nh = w;
        byte[] out = new byte[src.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dx = h - 1 - y, dy = x;
                out[dy * nw + dx] = src[y * w + x];
            }
        }
        int suv = w * h, duv = nw * nh;
        int uw = w / 2, uh = h / 2;
        for (int y = 0; y < uh; y++) {
            for (int x = 0; x < uw; x++) {
                int dx = uh - 1 - y, dy = x;
                int si = suv + y * w + x * 2;
                int di = duv + dy * nw + dx * 2;
                out[di] = src[si];
                out[di + 1] = src[si + 1];
            }
        }
        return out;
    }

    static byte[] toNv21(Image img) {
        int w = img.getWidth(), h = img.getHeight();
        byte[] nv21 = new byte[w * h * 3 / 2];
        Image.Plane[] planes = img.getPlanes();
        Image.Plane yp = planes[0], up = planes[1], vp = planes[2];
        ByteBuffer yb = yp.getBuffer(), ub = up.getBuffer(), vb = vp.getBuffer();
        int pos = 0;
        for (int y = 0; y < h; y++) {
            int row = y * yp.getRowStride();
            for (int x = 0; x < w; x++) nv21[pos++] = yb.get(row + x * yp.getPixelStride());
        }
        for (int y = 0; y < h / 2; y++) {
            int rowU = y * up.getRowStride(), rowV = y * vp.getRowStride();
            for (int x = 0; x < w / 2; x++) {
                nv21[pos++] = vb.get(rowV + x * vp.getPixelStride());
                nv21[pos++] = ub.get(rowU + x * up.getPixelStride());
            }
        }
        return nv21;
    }

    static byte[] rotate180(byte[] src, int w, int h) {
        int ySize = w * h, uvSize = w * h / 2;
        byte[] out = new byte[src.length];
        for (int y = 0; y < h; y++) {
            int srcRow = (h - 1 - y) * w;
            int dstRow = y * w;
            for (int x = 0; x < w; x++) out[dstRow + x] = src[srcRow + (w - 1 - x)];
        }
        int uv = ySize;
        for (int y = 0; y < h / 2; y++) {
            int srcRow = uv + (h / 2 - 1 - y) * w;
            int dstRow = uv + y * w;
            for (int x = 0; x < w / 2; x++) {
                out[dstRow + x * 2] = src[srcRow + (w / 2 - 1 - x) * 2];
                out[dstRow + x * 2 + 1] = src[srcRow + (w / 2 - 1 - x) * 2 + 1];
            }
        }
        return out;
    }

    static byte[] mirrorHorizontal(byte[] src, int w, int h) {
        byte[] out = new byte[src.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) out[y * w + x] = src[y * w + (w - 1 - x)];
        }
        int uv = w * h;
        for (int y = 0; y < h / 2; y++) {
            for (int x = 0; x < w / 2; x++) {
                int si = uv + y * w + (w / 2 - 1 - x) * 2;
                int di = uv + y * w + x * 2;
                out[di] = src[si];
                out[di + 1] = src[si + 1];
            }
        }
        return out;
    }

    void publishPhoto(ByteBuffer map, byte[] nv21, int w, int h, int seq) {
        try {
            if ((map.getInt(76) & 1) != 0) nv21 = mirrorHorizontal(nv21, w, h);
            nv21 = rotate90(nv21, w, h);
            int rw = h, rh = w;
            nv21 = mirrorHorizontal(nv21, rw, rh);
            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, rw, rh, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, rw, rh), 90, out);
            byte[] jpeg = out.toByteArray();
            int photoOff = HDR + SLOTS * SLOT_BYTES;
            if (jpeg.length <= 0 || photoOff + jpeg.length > map.capacity()) return;
            map.position(photoOff); map.put(jpeg);
            map.putInt(80, rw); map.putInt(84, rh);
            map.putInt(88, jpeg.length); map.putInt(92, seq);
            Log.i(TAG, "photo published " + w + "x" + h + " " + jpeg.length + " bytes seq=" + seq);
        } catch (Throwable t) { Log.e(TAG, "photo encode failed", t); }
    }

    void decodeLoop(String requested) {
        String path = findVideo(requested);
        if (path == null) { Log.e(TAG, "no video in cammock library"); finish(); return; }
        Log.i(TAG, "decode " + path);
        try (RandomAccessFile raf = new RandomAccessFile(PATH, "rw");
             FileChannel ch = raf.getChannel()) {
            long total = raf.length();
            if (total < HDR + (long) SLOTS * SLOT_BYTES) { Log.e(TAG, "frame file too small " + total); finish(); return; }
            ByteBuffer map = ch.map(FileChannel.MapMode.READ_WRITE, 0, total);
            map.order(ByteOrder.LITTLE_ENDIAN);
            MediaExtractor ex = new MediaExtractor();
            ex.setDataSource(path);
            int track = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                if (f.getString(MediaFormat.KEY_MIME).startsWith("video/")) { track = i; fmt = f; break; }
            }
            if (track < 0) { Log.e(TAG, "no video track"); finish(); return; }
            ex.selectTrack(track);
            MediaCodec codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            codec.configure(fmt, null, null, 0);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int seq = 0, slot = 0, w = fmt.getInteger(MediaFormat.KEY_WIDTH), h = fmt.getInteger(MediaFormat.KEY_HEIGHT);
            ByteBuffer yuv = null;
            while (running) {
                int in = codec.dequeueInputBuffer(10000);
                if (in >= 0) {
                    ByteBuffer ib = codec.getInputBuffer(in);
                    int sz = ex.readSampleData(ib, 0);
                    if (sz < 0) { codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); }
                    else { codec.queueInputBuffer(in, 0, sz, ex.getSampleTime(), 0); ex.advance(); }
                }
                int out = codec.dequeueOutputBuffer(info, 10000);
                if (out >= 0) {
                    Image img = codec.getOutputImage(out);
                    if (img != null) {
                        int iw = img.getWidth(), ih = img.getHeight();
                        byte[] data = toNv21(img);
                        int off = HDR + slot * SLOT_BYTES;
                        map.position(off); map.put(data);
                        map.putInt(0, MAGIC); map.putInt(4, VERSION);
                        map.putInt(8, iw); map.putInt(12, ih);
                        map.putInt(16, iw); map.putInt(20, iw); map.putInt(24, 0);
                        map.putInt(28, SLOT_BYTES); map.putInt(32, SLOTS);
                        map.putInt(36, 1); map.putInt(44, slot);
                        map.putInt(40, ++seq);
                        int req = map.getInt(104);
                        if (req > 0 && req != photoReq) {
                            photoReq = req;
                            publishPhoto(map, data, iw, ih, ++photoSeq);
                        }
                        slot = (slot + 1) % SLOTS;
                        if (seq == 1 || seq % 30 == 0) Log.i(TAG, "frame " + seq + " " + iw + "x" + ih);
                    }
                    codec.releaseOutputBuffer(out, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        ex.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                        codec.flush();
                    }
                }
            }
            codec.stop(); codec.release(); ex.release();
        } catch (Throwable t) { Log.e(TAG, "decode failed", t); }
        finish();
    }

    static int align(int v, int a) { return (v + a - 1) / a * a; }
    static void copyPlane(Image.Plane p, int w, int h, int stride, ByteBuffer dst) {
        ByteBuffer src = p.getBuffer();
        int ps = p.getPixelStride(), rs = p.getRowStride();
        int base = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            dst.put(base + y * stride + x, src.get(y * rs + x * ps));
        }
    }
}
