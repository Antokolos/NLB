package com.nlbhub.nlb.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Пакет книги .nlbpack: чтение формата NLBNavigator, распаковка без крупных медиафайлов
 * и чтение их содержимого из пакета
 */
public class BookPackTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    /**
     * Запись пакета в том же формате, что BookPack::write NLBNavigator
     */
    private static final class Writer {
        private final ByteArrayOutputStream m_body = new ByteArrayOutputStream();
        private int m_count = 0;

        private static void u32(ByteArrayOutputStream out, long v) {
            for (int i = 0; i < 4; i++) {
                out.write((int) (v >> (8 * i)) & 0xFF);
            }
        }

        private static void u64(ByteArrayOutputStream out, long v) {
            for (int i = 0; i < 8; i++) {
                out.write((int) (v >> (8 * i)) & 0xFF);
            }
        }

        private static void str(ByteArrayOutputStream out, String s) {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            u32(out, bytes.length);
            out.writeBytes(bytes);
        }

        Writer dir(String path) {
            m_body.write(0);
            str(m_body, path);
            u64(m_body, 0);
            u64(m_body, 1);
            m_count++;
            return this;
        }

        Writer file(String path, byte[] content) {
            m_body.write(1);
            str(m_body, path);
            u64(m_body, content.length);
            u64(m_body, 2);
            u32(m_body, content.length);
            m_body.writeBytes(content);
            m_count++;
            return this;
        }

        Writer media(String path, long size) {
            m_body.write(2);
            str(m_body, path);
            u64(m_body, size);
            u64(m_body, 3);
            m_count++;
            return this;
        }

        File write(File file) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.writeBytes("NLBPACK\n".getBytes(StandardCharsets.US_ASCII));
            u32(out, 2);
            str(out, "/книги/тест");
            u64(out, m_count);
            out.writeBytes(m_body.toByteArray());
            Files.write(file.toPath(), out.toByteArray());
            return file;
        }
    }

    private static byte[] bytes(int size, int seed) {
        byte[] result = new byte[size];
        for (int i = 0; i < size; i++) {
            result[i] = (byte) (i * 31 + seed);
        }
        return result;
    }

    private static byte[] read(BookPack pack, String path) throws IOException {
        try (InputStream in = pack.open(pack.find(path))) {
            return in.readAllBytes();
        }
    }

    @Test
    public void readsAndUnpacks() throws Exception {
        byte[] big = bytes(200 * 1024, 7);
        byte[] small = bytes(100, 3);
        byte[] bigText = bytes(100 * 1024, 5);
        File file = new Writer()
                .dir("")
                .file("startpoint", "p1".getBytes(StandardCharsets.UTF_8))
                .dir("images")
                .file("images/big.png", big)
                .file("images/big.png.constrid", "v1".getBytes(StandardCharsets.UTF_8))
                .file("images/small.png", small)
                .dir("pages")
                .dir("pages/p1")
                .file("pages/p1/text", bigText)
                .dir("sound")
                .media("sound/name-only.ogg", 5000)
                .write(m_temp.newFile("book.nlbpack"));
        BookPack pack = BookPack.read(file);
        assertEquals("/книги/тест", pack.getSourceDir());
        assertEquals(11, pack.entries().size());
        assertEquals(BookPack.Type.MEDIA_FILE, pack.find("sound/name-only.ogg").getType());
        assertEquals(5000, pack.find("sound/name-only.ogg").getSize());
        assertArrayEquals(big, read(pack, "images/big.png"));
        assertArrayEquals(small, read(pack, "images/small.png"));

        File cache = m_temp.newFolder("cache");
        File dir = pack.unpack(cache);
        assertEquals("p1", Files.readString(new File(dir, "startpoint").toPath()));
        // крупная картинка — пустой файл (содержимое — в пакете), мелкие и служебные — как есть
        assertEquals(0, new File(dir, "images/big.png").length());
        assertEquals("v1", Files.readString(new File(dir, "images/big.png.constrid").toPath()));
        assertArrayEquals(small, Files.readAllBytes(new File(dir, "images/small.png").toPath()));
        // крупный файл книги (не медиа) распаковывается целиком
        assertArrayEquals(bigText, Files.readAllBytes(new File(dir, "pages/p1/text").toPath()));
        assertTrue(new File(dir, "sound/name-only.ogg").isFile());
        assertEquals(1, cache.list().length);
        // повторная распаковка — тот же каталог
        assertEquals(dir, BookPack.read(file).unpack(cache));
        assertEquals(1, cache.list().length);
    }

    @Test
    public void rejectsBrokenPacks() throws Exception {
        File notPack = m_temp.newFile("a.nlbpack");
        Files.write(notPack.toPath(), "просто текст".getBytes(StandardCharsets.UTF_8));
        expectFailure(notPack);
        expectFailure(new Writer().dir("").file("../outside", bytes(4, 1)).write(m_temp.newFile("b.nlbpack")));
        expectFailure(new Writer().dir("").file("/abs", bytes(4, 1)).write(m_temp.newFile("c.nlbpack")));
        expectFailure(new Writer().dir("").file("a\\b", bytes(4, 1)).write(m_temp.newFile("d.nlbpack")));
        File cut = new Writer().dir("").file("startpoint", bytes(1000, 1)).write(m_temp.newFile("e.nlbpack"));
        byte[] content = Files.readAllBytes(cut.toPath());
        Files.write(cut.toPath(), Arrays.copyOf(content, content.length - 10));
        expectFailure(cut);
        assertNotNull(BookPack.read(new Writer().dir("").write(m_temp.newFile("f.nlbpack"))));
        assertNull(BookPack.read(new Writer().dir("").write(m_temp.newFile("g.nlbpack"))).find("startpoint"));
    }

    private static void expectFailure(File file) {
        try {
            BookPack.read(file);
            fail("пакет прочитан: " + file.getName());
        } catch (IOException expected) {
            // ожидаемо
        }
    }
}
