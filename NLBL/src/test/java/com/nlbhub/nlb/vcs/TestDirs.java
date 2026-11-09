package com.nlbhub.nlb.vcs;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Удаление временного каталога теста с репозиториями git. Пока тест завершается, в каталоге ещё
 * может работать фон (автоматический gc JGit, фоновое чтение статуса): файлы вроде
 * .git/gc.log.lock появляются и исчезают во время обхода. Files.walk на исчезнувшем файле
 * бросает NoSuchFileException, и тест падал в tearDown. Здесь исчезнувшие файлы пропускаются,
 * а появившиеся после обхода — удаляются повторным проходом.
 */
public final class TestDirs {
    private static final int ATTEMPTS = 5;

    private TestDirs() {
    }

    public static void delete(Path dir) throws IOException {
        if (dir == null) {
            return;
        }
        for (int attempt = 0; attempt < ATTEMPTS && Files.exists(dir); attempt++) {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    file.toFile().delete();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException e) {
                    d.toFile().delete();
                    return FileVisitResult.CONTINUE;
                }
            });
            if (Files.exists(dir)) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
