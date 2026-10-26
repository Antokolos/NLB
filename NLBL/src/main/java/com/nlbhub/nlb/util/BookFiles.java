package com.nlbhub.nlb.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Файлы книги при загрузке: кэш книги одним файлом.
 * <p>
 * Каталог книги — десятки тысяч мелких файлов («Фронтир» — 26 тысяч), и открывать каждый на
 * Windows дорого (антивирус проверяет файл при открытии): холодное открытие книги — минуты.
 * Поэтому, пока кэш включён ({@link #setCacheDir}), загрузка книги ({@link #load}):
 * <ol>
 * <li>один раз обходит дерево книги (без .git) — только перечисление каталогов, файлы не
 * открываются — и запоминает каждый файл с размером и временем изменения, каталоги — с
 * содержимым в том же порядке, что вернул бы File.list;</li>
 * <li>на exists/isDirectory/listFiles отвечает по этому снимку;</li>
 * <li>содержимое файла берёт из кэша, если размер и время изменения совпали с записанными в
 * кэше, иначе читает с диска;</li>
 * <li>после загрузки, если что-то читалось с диска, переписывает кэш.</li>
 * </ol>
 * Кэш — только ускорение: снимок каталогов всегда свежий, содержимое берётся из кэша лишь для
 * неизменившихся файлов, а испорченный или чужой кэш не используется. Снимок — на поток, так что
 * одновременные загрузки разных книг не мешают друг другу. Пока кэш выключен (по умолчанию) или
 * нет активной загрузки, все методы обращаются к диску как обычно.
 */
public final class BookFiles {
    private static final Logger LOGGER = LoggerFactory.getLogger(BookFiles.class);
    private static final String MAGIC = "NLBCACHE";
    private static final int VERSION = 1;
    private static final String CACHE_EXT = ".nlbcache";
    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();
    private static volatile File s_cacheDir = null;
    private static final ThreadLocal<String> LAST_LOAD = new ThreadLocal<>();

    private BookFiles() {
    }

    /**
     * Каталог кэша книг; null — кэш выключен
     */
    public static void setCacheDir(File cacheDir) {
        s_cacheDir = cacheDir;
    }

    public static File getCacheDir() {
        return s_cacheDir;
    }

    /**
     * Каталог кэша по умолчанию: %LOCALAPPDATA%\NLB\cache на Windows, иначе
     * $XDG_CACHE_HOME/nlb или ~/.cache/nlb
     */
    public static File defaultCacheDir() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isEmpty()) {
            return new File(localAppData, "NLB" + File.separator + "cache");
        }
        String xdg = System.getenv("XDG_CACHE_HOME");
        if (xdg != null && !xdg.isEmpty()) {
            return new File(xdg, "nlb");
        }
        return new File(System.getProperty("user.home"), ".cache" + File.separator + "nlb");
    }

    public interface Loader<T, E extends Exception> {
        T load() throws E;
    }

    /**
     * Загрузка книги из rootDir со снимком и кэшем (если кэш включён и это не вложенная загрузка
     * модуля той же книги); иначе — просто loader.load()
     */
    public static <T, E extends Exception> T load(final File rootDir, final Loader<T, E> loader) throws E {
        final File cacheDir = s_cacheDir;
        if (CURRENT.get() != null) {
            return loader.load();  // модуль внутри уже загружаемой книги
        }
        LAST_LOAD.remove();
        if (cacheDir == null || !rootDir.isDirectory()) {
            return loader.load();
        }
        Snapshot snapshot;
        try {
            snapshot = new Snapshot(rootDir.getCanonicalFile(), rootDir.getAbsolutePath(), cacheDir);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Book cache is not used for {}: {}", rootDir, e.toString());
            return loader.load();
        }
        CURRENT.set(snapshot);
        final T result;
        try {
            result = loader.load();
        } finally {
            CURRENT.remove();
        }
        LAST_LOAD.set(snapshot.summary());
        snapshot.writeCacheIfChanged();
        return result;
    }

    /**
     * Итог последней загрузки книги в этом потоке, для лога; null — кэш не использовался
     */
    public static String lastLoadSummary() {
        return LAST_LOAD.get();
    }

    public static boolean exists(File file) {
        Snapshot snapshot = CURRENT.get();
        if (snapshot != null) {
            Boolean known = snapshot.exists(file);
            if (known != null) {
                return known;
            }
        }
        return file.exists();
    }

    public static boolean isDirectory(File file) {
        Snapshot snapshot = CURRENT.get();
        if (snapshot != null) {
            Boolean known = snapshot.isDirectory(file);
            if (known != null) {
                return known;
            }
        }
        return file.isDirectory();
    }

    public static String[] list(File dir) {
        Snapshot snapshot = CURRENT.get();
        if (snapshot != null) {
            Optional<String[]> known = snapshot.list(dir);
            if (known != null) {
                return known.orElse(null);
            }
        }
        return dir.list();
    }

    public static File[] listFiles(File dir) {
        return listFiles(dir, null);
    }

    public static File[] listFiles(File dir, FilenameFilter filter) {
        Snapshot snapshot = CURRENT.get();
        if (snapshot == null) {
            return filter == null ? dir.listFiles() : dir.listFiles(filter);
        }
        String[] names = list(dir);
        if (names == null) {
            return null;
        }
        List<File> result = new ArrayList<>(names.length);
        for (String name : names) {
            if (filter == null || filter.accept(dir, name)) {
                result.add(new File(dir, name));
            }
        }
        return result.toArray(new File[0]);
    }

    /**
     * Содержимое файла: из кэша, если файл не менялся, иначе с диска
     */
    public static byte[] read(File file) throws IOException {
        Snapshot snapshot = CURRENT.get();
        if (snapshot != null) {
            byte[] content = snapshot.read(file);
            if (content != null) {
                return content;
            }
        }
        return Files.readAllBytes(file.toPath());
    }

    private static final class Entry {
        final boolean m_directory;
        final long m_size;
        final long m_mtime;
        final List<String> m_children;

        Entry(boolean directory, long size, long mtime) {
            m_directory = directory;
            m_size = size;
            m_mtime = mtime;
            m_children = directory ? new ArrayList<>() : null;
        }
    }

    private static final class Cached {
        final long m_size;
        final long m_mtime;
        final byte[] m_content;

        Cached(long size, long mtime, byte[] content) {
            m_size = size;
            m_mtime = mtime;
            m_content = content;
        }
    }

    private static final class Snapshot {
        private final File m_root;
        private final String m_rootPath;
        /**
         * Путь корня в том виде, в каком его передали (может отличаться от канонического:
         * ссылки, регистр букв на Windows); внутренние модули грузятся по каноническому
         */
        private final String m_aliasPath;
        private final File m_cacheFile;
        private final Map<String, Entry> m_entries = new HashMap<>();
        private final Map<String, Cached> m_cached;
        /**
         * Прочитанное при этой загрузке — будущее содержимое кэша
         */
        private final Map<String, Cached> m_read = new LinkedHashMap<>();
        private int m_hits = 0;
        private int m_misses = 0;
        private final long m_walkMs;

        Snapshot(File root, String aliasPath, File cacheDir) throws IOException {
            m_root = root;
            m_rootPath = root.getPath();
            m_aliasPath = aliasPath;
            m_cacheFile = new File(cacheDir, hash(m_rootPath) + CACHE_EXT);
            final long start = System.nanoTime();
            walk();
            m_walkMs = (System.nanoTime() - start) / 1000000;
            m_cached = readCache();
        }

        private void walk() throws IOException {
            final Path rootPath = m_root.toPath();
            Files.walkFileTree(rootPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(rootPath) && dir.getFileName().toString().equals(".git")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    String rel = relative(rootPath, dir);
                    m_entries.put(rel, new Entry(true, 0, 0));
                    addToParent(rel);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().equals(".git")) {
                        return FileVisitResult.CONTINUE;  // .git подмодуля — файл со ссылкой
                    }
                    String rel = relative(rootPath, file);
                    m_entries.put(rel, new Entry(attrs.isDirectory(), attrs.size(), attrs.lastModifiedTime().to(TimeUnit.NANOSECONDS)));
                    addToParent(rel);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    throw exc;  // недоступный файл — снимок неполный, кэш не используется
                }
            });
        }

        private void addToParent(String rel) {
            if (rel.isEmpty()) {
                return;
            }
            int slash = rel.lastIndexOf('/');
            Entry parent = m_entries.get(slash < 0 ? "" : rel.substring(0, slash));
            if (parent != null && parent.m_children != null) {
                parent.m_children.add(slash < 0 ? rel : rel.substring(slash + 1));
            }
        }

        private static String relative(Path root, Path path) {
            return root.relativize(path).toString().replace(File.separatorChar, '/');
        }

        /**
         * Путь внутри книги или null, если файл вне её (или в .git)
         */
        private String key(File file) {
            String path = file.getAbsolutePath();
            String rel = under(path, m_rootPath);
            if (rel == null) {
                rel = under(path, m_aliasPath);
            }
            if (rel == null) {
                return null;
            }
            if (rel.equals(".git") || rel.startsWith(".git/") || rel.contains("/.git/") || rel.endsWith("/.git")) {
                return null;
            }
            return rel;
        }

        private static String under(String path, String root) {
            if (path.equals(root)) {
                return "";
            }
            if (!path.startsWith(root) || path.charAt(root.length()) != File.separatorChar) {
                return null;
            }
            return path.substring(root.length() + 1).replace(File.separatorChar, '/');
        }

        Boolean exists(File file) {
            String key = key(file);
            return key == null ? null : m_entries.containsKey(key);
        }

        Boolean isDirectory(File file) {
            String key = key(file);
            if (key == null) {
                return null;
            }
            Entry entry = m_entries.get(key);
            return entry != null && entry.m_directory;
        }

        Optional<String[]> list(File dir) {
            String key = key(dir);
            if (key == null) {
                return null;
            }
            Entry entry = m_entries.get(key);
            if (entry == null || !entry.m_directory) {
                return Optional.empty();
            }
            return Optional.of(entry.m_children.toArray(new String[0]));
        }

        byte[] read(File file) throws IOException {
            String key = key(file);
            if (key == null) {
                return null;
            }
            Entry entry = m_entries.get(key);
            if (entry == null || entry.m_directory) {
                throw new FileNotFoundException(file.getPath());
            }
            Cached cached = m_cached.get(key);
            byte[] content;
            if (cached != null && cached.m_size == entry.m_size && cached.m_mtime == entry.m_mtime) {
                content = cached.m_content;
                m_hits++;
            } else {
                content = Files.readAllBytes(file.toPath());
                m_misses++;
            }
            m_read.put(key, new Cached(entry.m_size, entry.m_mtime, content));
            return content;
        }

        String summary() {
            return String.format(
                    "book cache: %d files from cache, %d read from disk, directory scan %d ms",
                    m_hits, m_misses, m_walkMs
            );
        }

        /**
         * Кэш прежней загрузки: путь -> размер, время изменения, содержимое. Нет, испорчен,
         * другой версии или другой книги — пустой
         */
        private Map<String, Cached> readCache() {
            Map<String, Cached> result = new HashMap<>();
            if (!m_cacheFile.isFile()) {
                return result;
            }
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(m_cacheFile), 1 << 16))) {
                if (!MAGIC.equals(in.readUTF()) || in.readInt() != VERSION || !m_rootPath.equals(in.readUTF())) {
                    return result;
                }
                int count = in.readInt();
                for (int i = 0; i < count; i++) {
                    String key = in.readUTF();
                    long size = in.readLong();
                    long mtime = in.readLong();
                    byte[] content = new byte[in.readInt()];
                    in.readFully(content);
                    result.put(key, new Cached(size, mtime, content));
                }
                if (in.readInt() != count) {
                    return new HashMap<>();
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Book cache {} is ignored: {}", m_cacheFile, e.toString());
                return new HashMap<>();
            }
            return result;
        }

        /**
         * Записать кэш, если при загрузке что-то читалось с диска или из кэша ушли файлы,
         * которых больше нет; через временный файл, чтобы прерванная запись не испортила кэш
         */
        void writeCacheIfChanged() {
            if (m_misses == 0 && m_read.size() == m_cached.size()) {
                return;
            }
            File dir = m_cacheFile.getParentFile();
            File temp = new File(dir, m_cacheFile.getName() + ".tmp");
            try {
                Files.createDirectories(dir.toPath());
                try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp), 1 << 16))) {
                    out.writeUTF(MAGIC);
                    out.writeInt(VERSION);
                    out.writeUTF(m_rootPath);
                    out.writeInt(m_read.size());
                    for (Map.Entry<String, Cached> e : m_read.entrySet()) {
                        out.writeUTF(e.getKey());
                        out.writeLong(e.getValue().m_size);
                        out.writeLong(e.getValue().m_mtime);
                        out.writeInt(e.getValue().m_content.length);
                        out.write(e.getValue().m_content);
                    }
                    out.writeInt(m_read.size());
                }
                Files.move(temp.toPath(), m_cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Book cache {} is not written: {}", m_cacheFile, e.toString());
                temp.delete();
            }
        }

        private static String hash(String text) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 12; i++) {
                    sb.append(String.format("%02x", digest[i]));
                }
                return sb.toString();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
