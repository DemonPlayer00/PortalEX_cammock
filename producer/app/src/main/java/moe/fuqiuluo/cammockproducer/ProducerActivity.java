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
    }

    @Override protected void onDestroy() { running = false; super.onDestroy(); }

    String findVideo(String requested) {
        if (requested != null && new File(requested).isFile()) return requested;
        File dir = new File("/data/local/tmp/portalex/cammock/library");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".mp4"));
        if (files != null && files.length > 0) return files[0].getAbsolutePath();
        return null;
    }

    void publishPhoto(ByteBuffer map, Image img, int w, int h, int seq) {
        try {
            byte[] nv21 = new byte[w * h * 3 / 2];
            Image.Plane[] planes = img.getPlanes();
            Image.Plane yp = planes[0], up = planes[1], vp = planes[2];
            ByteBuffer yb = yp.getBuffer(), ub = up.getBuffer(), vb = vp.getBuffer();
            int yPos = 0;
            for (int y = 0; y < h; y++) {
                int row = y * yp.getRowStride();
                for (int x = 0; x < w; x++) nv21[yPos++] = yb.get(row + x * yp.getPixelStride());
            }
            for (int y = 0; y < h / 2; y++) {
                int rowU = y * up.getRowStride(), rowV = y * vp.getRowStride();
                for (int x = 0; x < w / 2; x++) {
                    nv21[yPos++] = vb.get(rowV + x * vp.getPixelStride());
                    nv21[yPos++] = ub.get(rowU + x * up.getPixelStride());
                }
            }
            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, w, h, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, w, h), 90, out);
            byte[] jpeg = out.toByteArray();
            int photoOff = HDR + SLOTS * SLOT_BYTES;
            if (jpeg.length <= 0 || photoOff + jpeg.length > map.capacity()) return;
            map.position(photoOff); map.put(jpeg);
            map.putInt(80, w); map.putInt(84, h);
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
                        int ys = align(iw, 16), uvs = align(iw, 16);
                        if (yuv == null || yuv.capacity() < SLOT_BYTES) yuv = ByteBuffer.allocate(SLOT_BYTES);
                        yuv.clear();
                        Image.Plane[] planes = img.getPlanes();
                        copyPlane(planes[0], iw, ih, ys, yuv);
                        copyPlane(planes[2], iw / 2, ih / 2, uvs, yuv);
                        copyPlane(planes[1], iw / 2, ih / 2, uvs, yuv);
                        // NV21 interleave: v/u from separate planes
                        int base = ys * ih;
                        ByteBuffer uvDst = yuv;
                        for (int y = 0; y < ih / 2; y++) {
                            for (int x = 0; x < iw / 2; x++) {
                                int off = base + y * uvs + x * 2;
                                uvDst.put(off, planes[2].getBuffer().get(y * planes[2].getRowStride() + x * planes[2].getPixelStride()));
                                uvDst.put(off + 1, planes[1].getBuffer().get(y * planes[1].getRowStride() + x * planes[1].getPixelStride()));
                            }
                        }
                        int off = HDR + slot * SLOT_BYTES;
                        byte[] data = new byte[ys * ih + uvs * (ih / 2)];
                        yuv.position(0); yuv.get(data);
                        map.position(off); map.put(data);
                        map.putInt(0, MAGIC); map.putInt(4, VERSION);
                        map.putInt(8, iw); map.putInt(12, ih);
                        map.putInt(16, ys); map.putInt(20, uvs); map.putInt(24, 0);
                        map.putInt(28, SLOT_BYTES); map.putInt(32, SLOTS);
                        map.putInt(36, 1); map.putInt(44, slot);
                        map.putInt(40, ++seq);
                        int req = map.getInt(104);
                        if (req > 0 && req != photoReq) {
                            photoReq = req;
                            publishPhoto(map, img, iw, ih, ++photoSeq);
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
