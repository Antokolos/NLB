package com.nlbhub.nlb.web.preview;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/** Распаковка tar пакетов npm: каталог package/, без выхода за целевой каталог */
public class TarTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    private static void entry(ByteArrayOutputStream out, String name, String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        byte[] header = new byte[512];
        byte[] n = name.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(n, 0, header, 0, n.length);
        byte[] size = String.format("%011o", data.length).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(size, 0, header, 124, size.length);
        header[156] = '0';
        System.arraycopy("ustar".getBytes(StandardCharsets.US_ASCII), 0, header, 257, 5);
        out.writeBytes(header);
        out.writeBytes(data);
        out.writeBytes(new byte[(512 - data.length % 512) % 512]);
    }

    @Test
    public void extractsPackageOnly() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        entry(out, "package/dist/index.js", "export {};");
        entry(out, "package/package.json", "{}");
        entry(out, "other/x.txt", "x");
        entry(out, "package/../../evil.txt", "evil");
        out.writeBytes(new byte[1024]);
        Path target = m_temp.getRoot().toPath().resolve("a/b");
        Tar.extract(new ByteArrayInputStream(out.toByteArray()), "package/", target);
        assertEquals("export {};", Files.readString(target.resolve("dist/index.js")));
        assertEquals("{}", Files.readString(target.resolve("package.json")));
        assertFalse(Files.exists(target.resolve("x.txt")));
        assertFalse(Files.exists(m_temp.getRoot().toPath().resolve("evil.txt")));
        assertFalse(Files.exists(m_temp.getRoot().toPath().resolve("a/evil.txt")));
    }
}
