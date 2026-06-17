package org.aiincubator.ilmai.ai.ingestion.reader;

import org.aiincubator.ilmai.ai.ingestion.IngestionProperties;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AudioReaderTest {

    private static final int MP3_FRAME_LENGTH = 417;
    private static final double MP3_FRAME_MS = 1152.0 * 1000.0 / 44100.0;

    @Test
    void supports_acceptsConfiguredAudioTypesAndDropsMp4() {
        AudioReader reader = new AudioReader(new IngestionProperties());

        assertThat(reader.supports("audio/mpeg")).isTrue();
        assertThat(reader.supports("audio/mp3")).isTrue();
        assertThat(reader.supports("audio/wav")).isTrue();
        assertThat(reader.supports("AUDIO/WAV")).isTrue();
        assertThat(reader.supports("audio/wav; charset=binary")).isTrue();
        assertThat(reader.supports("audio/mp4")).isFalse();
        assertThat(reader.supports("audio/ogg")).isFalse();
        assertThat(reader.supports("application/pdf")).isFalse();
        assertThat(reader.supports(null)).isFalse();
    }

    @Test
    void read_emptyBytes_returnsEmpty() throws IOException {
        AudioReader reader = new AudioReader(new IngestionProperties());

        List<MaterialPart> parts = reader.read(new ByteArrayInputStream(new byte[0]), newMaterial("audio/mpeg"));

        assertThat(parts).isEmpty();
    }

    @Test
    void read_shortWav_returnsSingleSegment() throws IOException {
        AudioReader reader = new AudioReader(new IngestionProperties());
        byte[] wav = buildWav(200);

        List<MaterialPart> parts = reader.read(new ByteArrayInputStream(wav), newMaterial("audio/wav"));

        assertThat(parts).hasSize(1);
        AudioSegmentPart segment = (AudioSegmentPart) parts.getFirst();
        assertThat(segment.getStartMs()).isZero();
        assertThat(segment.getEndMs()).isBetween(180L, 220L);
        assertThat(segment.getMimeType()).isEqualTo("audio/wav");
        assertThat(durationMs(segment.getSegmentBytes())).isBetween(180L, 220L);
    }

    @Test
    void read_longWav_emitsOverlappingPlayableSegments() throws IOException {
        IngestionProperties properties = new IngestionProperties();
        properties.getAudio().setWindowMs(300L);
        properties.getAudio().setOverlapMs(100L);
        AudioReader reader = new AudioReader(properties);
        byte[] wav = buildWav(1000);

        List<MaterialPart> parts = reader.read(new ByteArrayInputStream(wav), newMaterial("audio/wav"));

        assertThat(parts).hasSizeGreaterThan(1);
        assertThat(parts).allSatisfy(p -> assertThat(p).isInstanceOf(AudioSegmentPart.class));

        AudioSegmentPart first = (AudioSegmentPart) parts.get(0);
        AudioSegmentPart second = (AudioSegmentPart) parts.get(1);
        assertThat(first.getStartMs()).isZero();
        assertThat(first.getEndMs() - first.getStartMs()).isBetween(280L, 320L);
        assertThat(second.getStartMs()).isEqualTo(200L);
        assertThat(second.getStartMs()).isLessThan(first.getEndMs());

        for (MaterialPart part : parts) {
            AudioSegmentPart segment = (AudioSegmentPart) part;
            assertThat(durationMs(segment.getSegmentBytes()))
                    .isCloseTo(segment.getEndMs() - segment.getStartMs(), org.assertj.core.data.Offset.offset(5L));
        }
        AudioSegmentPart last = (AudioSegmentPart) parts.getLast();
        assertThat(last.getEndMs()).isLessThanOrEqualTo(1000L);
    }

    @Test
    void read_shortMp3_returnsSingleSegment() throws IOException {
        AudioReader reader = new AudioReader(new IngestionProperties());
        int frames = 10;
        byte[] mp3 = buildMp3(frames);

        List<MaterialPart> parts = reader.read(new ByteArrayInputStream(mp3), newMaterial("audio/mpeg"));

        assertThat(parts).hasSize(1);
        AudioSegmentPart segment = (AudioSegmentPart) parts.getFirst();
        assertThat(segment.getStartMs()).isZero();
        assertThat(segment.getMimeType()).isEqualTo("audio/mpeg");
        assertThat(segment.getSegmentBytes()).isEqualTo(mp3);
        assertThat(countMp3Frames(segment.getSegmentBytes())).isEqualTo(frames);
    }

    @Test
    void read_longMp3_emitsFrameAlignedOverlappingSegments() throws IOException {
        IngestionProperties properties = new IngestionProperties();
        properties.getAudio().setWindowMs(300L);
        properties.getAudio().setOverlapMs(100L);
        AudioReader reader = new AudioReader(properties);
        int frames = 60;
        byte[] mp3 = buildMp3(frames);

        List<MaterialPart> parts = reader.read(new ByteArrayInputStream(mp3), newMaterial("audio/mp3"));

        assertThat(parts).hasSizeGreaterThan(1);
        AudioSegmentPart first = (AudioSegmentPart) parts.get(0);
        AudioSegmentPart second = (AudioSegmentPart) parts.get(1);
        assertThat(first.getStartMs()).isZero();
        assertThat(second.getStartMs()).isLessThan(first.getEndMs());

        for (MaterialPart part : parts) {
            byte[] segment = ((AudioSegmentPart) part).getSegmentBytes();
            assertThat(segment[0] & 0xFF).isEqualTo(0xFF);
            assertThat(segment[1] & 0xFF).isEqualTo(0xFB);
            assertThat(countMp3Frames(segment)).isGreaterThan(0);
        }
        AudioSegmentPart last = (AudioSegmentPart) parts.getLast();
        assertThat(last.getEndMs()).isLessThanOrEqualTo(Math.round(frames * MP3_FRAME_MS) + 1);
    }

    private MaterialDto newMaterial(String contentType) {
        return new MaterialDto(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "test", contentType, 0L, null, 0, null, null);
    }

    private byte[] buildWav(int millis) throws IOException {
        float sampleRate = 8000f;
        int frames = (int) (sampleRate * millis / 1000.0);
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        byte[] pcm = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            short value = (short) (Math.sin(i * 0.05) * 8000);
            pcm[2 * i] = (byte) (value & 0xFF);
            pcm[2 * i + 1] = (byte) ((value >> 8) & 0xFF);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(pcm), format, frames)) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, out);
        }
        return out.toByteArray();
    }

    private long durationMs(byte[] wav) throws IOException {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            long frames = in.getFrameLength();
            float frameRate = in.getFormat().getFrameRate();
            return Math.round(frames / (double) frameRate * 1000.0);
        } catch (UnsupportedAudioFileException e) {
            throw new IOException(e);
        }
    }

    private byte[] buildMp3(int frames) {
        byte[] bytes = new byte[frames * MP3_FRAME_LENGTH];
        for (int f = 0; f < frames; f++) {
            int base = f * MP3_FRAME_LENGTH;
            bytes[base] = (byte) 0xFF;
            bytes[base + 1] = (byte) 0xFB;
            bytes[base + 2] = (byte) 0x90;
            bytes[base + 3] = (byte) 0x00;
        }
        return bytes;
    }

    private int countMp3Frames(byte[] bytes) {
        int count = 0;
        int pos = 0;
        while (pos + 4 <= bytes.length) {
            if ((bytes[pos] & 0xFF) == 0xFF && (bytes[pos + 1] & 0xE0) == 0xE0) {
                count++;
                pos += MP3_FRAME_LENGTH;
            } else {
                pos++;
            }
        }
        return count;
    }
}
