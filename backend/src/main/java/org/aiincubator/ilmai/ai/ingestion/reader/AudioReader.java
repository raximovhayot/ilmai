package org.aiincubator.ilmai.ai.ingestion.reader;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.ai.ingestion.IngestionProperties;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.springframework.stereotype.Component;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AudioReader implements MaterialReader {

    private static final Set<String> SUPPORTED = Set.of("audio/mpeg", "audio/mp3", "audio/wav");
    private static final String WAV = "audio/wav";

    private static final int[][] SAMPLE_RATES = {
            {11025, 12000, 8000, 0},
            {0, 0, 0, 0},
            {22050, 24000, 16000, 0},
            {44100, 48000, 32000, 0}
    };
    private static final int[] BR_V1_L1 = {0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448, 0};
    private static final int[] BR_V1_L2 = {0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, 0};
    private static final int[] BR_V1_L3 = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0};
    private static final int[] BR_V2_L1 = {0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256, 0};
    private static final int[] BR_V2_L23 = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0};

    private final IngestionProperties properties;

    @Override
    public boolean supports(String contentType) {
        return SUPPORTED.contains(normalize(contentType));
    }

    @Override
    public List<MaterialPart> read(InputStream blob, MaterialDto material) throws IOException {
        byte[] bytes = blob.readAllBytes();
        if (bytes.length == 0) {
            return List.of();
        }
        String mime = normalize(material == null ? null : material.getContentType());
        if (!SUPPORTED.contains(mime)) {
            mime = "audio/mpeg";
        }
        long windowMs = Math.max(1L, properties.getAudio().getWindowMs());
        long overlapMs = Math.min(Math.max(0L, properties.getAudio().getOverlapMs()), windowMs - 1);
        if (WAV.equals(mime)) {
            return sliceWav(bytes, windowMs, overlapMs);
        }
        return sliceMp3(bytes, mime, windowMs, overlapMs);
    }

    private List<MaterialPart> sliceWav(byte[] bytes, long windowMs, long overlapMs) throws IOException {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
            AudioFormat format = in.getFormat();
            int frameSize = format.getFrameSize();
            float frameRate = format.getFrameRate();
            byte[] pcm = in.readAllBytes();
            if (frameSize <= 0 || frameRate <= 0f || pcm.length < frameSize) {
                return List.of(new AudioSegmentPart(0L, 0L, WAV, bytes));
            }
            long totalFrames = pcm.length / frameSize;
            long totalMs = Math.round(totalFrames / (double) frameRate * 1000.0);
            if (totalMs <= windowMs) {
                return List.of(new AudioSegmentPart(0L, totalMs, WAV, wrapWav(pcm, format, totalFrames)));
            }
            long stepMs = windowMs - overlapMs;
            List<MaterialPart> parts = new ArrayList<>();
            for (long startMs = 0L; startMs < totalMs; startMs += stepMs) {
                long endMs = Math.min(startMs + windowMs, totalMs);
                int from = (int) Math.min(pcm.length, Math.round(startMs / 1000.0 * frameRate) * (long) frameSize);
                int to = (int) Math.min(pcm.length, Math.round(endMs / 1000.0 * frameRate) * (long) frameSize);
                if (to > from) {
                    byte[] slice = new byte[to - from];
                    System.arraycopy(pcm, from, slice, 0, slice.length);
                    parts.add(new AudioSegmentPart(startMs, endMs, WAV, wrapWav(slice, format, slice.length / frameSize)));
                }
                if (endMs >= totalMs) {
                    break;
                }
            }
            return parts;
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("Unsupported WAV stream", e);
        }
    }

    private byte[] wrapWav(byte[] pcm, AudioFormat format, long frames) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (AudioInputStream slice = new AudioInputStream(new ByteArrayInputStream(pcm), format, frames)) {
            AudioSystem.write(slice, AudioFileFormat.Type.WAVE, out);
        }
        return out.toByteArray();
    }

    private List<MaterialPart> sliceMp3(byte[] bytes, String mime, long windowMs, long overlapMs) {
        byte[] id3 = extractId3(bytes);
        List<Integer> offsets = new ArrayList<>();
        List<Integer> lengths = new ArrayList<>();
        List<Double> starts = new ArrayList<>();
        double cursorMs = 0.0;
        int pos = id3.length;
        while (pos + 4 <= bytes.length) {
            int b0 = bytes[pos] & 0xFF;
            int b1 = bytes[pos + 1] & 0xFF;
            if (b0 != 0xFF || (b1 & 0xE0) != 0xE0) {
                pos++;
                continue;
            }
            int b2 = bytes[pos + 2] & 0xFF;
            int versionBits = (b1 >> 3) & 0x03;
            int layerBits = (b1 >> 1) & 0x03;
            int bitrateIndex = (b2 >> 4) & 0x0F;
            int sampleRateIndex = (b2 >> 2) & 0x03;
            int padding = (b2 >> 1) & 0x01;
            if (versionBits == 1 || layerBits == 0 || bitrateIndex == 0 || bitrateIndex == 15 || sampleRateIndex == 3) {
                pos++;
                continue;
            }
            int sampleRate = SAMPLE_RATES[versionBits][sampleRateIndex];
            int bitrate = bitrate(versionBits, layerBits, bitrateIndex) * 1000;
            int samplesPerFrame = samplesPerFrame(versionBits, layerBits);
            int slotSize = layerBits == 3 ? 4 : 1;
            int frameLength = (samplesPerFrame / 8) * bitrate / sampleRate + padding * slotSize;
            if (frameLength <= 0 || pos + frameLength > bytes.length) {
                break;
            }
            offsets.add(pos);
            lengths.add(frameLength);
            starts.add(cursorMs);
            cursorMs += samplesPerFrame * 1000.0 / sampleRate;
            pos += frameLength;
        }
        if (offsets.isEmpty()) {
            return List.of(new AudioSegmentPart(0L, 0L, mime, bytes));
        }
        int frameCount = offsets.size();
        long totalMs = Math.round(cursorMs);
        if (totalMs <= windowMs) {
            return List.of(new AudioSegmentPart(0L, totalMs, mime, concat(bytes, id3, offsets, lengths, 0, frameCount)));
        }
        long stepMs = windowMs - overlapMs;
        List<MaterialPart> parts = new ArrayList<>();
        for (long windowStart = 0L; windowStart < totalMs; windowStart += stepMs) {
            long windowEnd = Math.min(windowStart + windowMs, totalMs);
            int from = firstFrameAtOrAfter(starts, windowStart);
            if (from >= frameCount) {
                break;
            }
            int to = firstFrameAtOrAfter(starts, windowEnd);
            if (to <= from) {
                to = from + 1;
            }
            long actualStart = Math.round(starts.get(from));
            long actualEnd = to < frameCount ? Math.round(starts.get(to)) : totalMs;
            parts.add(new AudioSegmentPart(actualStart, actualEnd, mime, concat(bytes, id3, offsets, lengths, from, to)));
            if (windowEnd >= totalMs) {
                break;
            }
        }
        return parts;
    }

    private int firstFrameAtOrAfter(List<Double> starts, long ms) {
        for (int i = 0; i < starts.size(); i++) {
            if (starts.get(i) >= ms) {
                return i;
            }
        }
        return starts.size();
    }

    private byte[] concat(byte[] src, byte[] id3, List<Integer> offsets, List<Integer> lengths, int from, int to) {
        int size = id3.length;
        for (int i = from; i < to; i++) {
            size += lengths.get(i);
        }
        byte[] out = new byte[size];
        System.arraycopy(id3, 0, out, 0, id3.length);
        int p = id3.length;
        for (int i = from; i < to; i++) {
            int off = offsets.get(i);
            int len = lengths.get(i);
            System.arraycopy(src, off, out, p, len);
            p += len;
        }
        return out;
    }

    private byte[] extractId3(byte[] bytes) {
        if (bytes.length >= 10 && bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3') {
            int size = ((bytes[6] & 0x7F) << 21) | ((bytes[7] & 0x7F) << 14)
                    | ((bytes[8] & 0x7F) << 7) | (bytes[9] & 0x7F);
            int total = 10 + size;
            if (total > 0 && total <= bytes.length) {
                byte[] id3 = new byte[total];
                System.arraycopy(bytes, 0, id3, 0, total);
                return id3;
            }
        }
        return new byte[0];
    }

    private int samplesPerFrame(int versionBits, int layerBits) {
        if (layerBits == 3) {
            return 384;
        }
        if (layerBits == 2) {
            return 1152;
        }
        return versionBits == 3 ? 1152 : 576;
    }

    private int bitrate(int versionBits, int layerBits, int bitrateIndex) {
        if (versionBits == 3) {
            return switch (layerBits) {
                case 3 -> BR_V1_L1[bitrateIndex];
                case 2 -> BR_V1_L2[bitrateIndex];
                default -> BR_V1_L3[bitrateIndex];
            };
        }
        return layerBits == 3 ? BR_V2_L1[bitrateIndex] : BR_V2_L23[bitrateIndex];
    }

    private String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        String bare = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
        return bare.trim().toLowerCase();
    }
}
