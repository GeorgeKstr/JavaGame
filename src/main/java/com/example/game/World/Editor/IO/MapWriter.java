package com.example.game.World.Editor.IO;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/**
 * Helper for writing layered data into a .map zip file.
 */
public class MapWriter implements Closeable {
  private final ZipOutputStream zip;

  public MapWriter(String path) throws IOException {
    Files.createDirectories(Paths.get(path).getParent());
    zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
  }

  /** Write a JSON string into the archive under the given entry path. */
  public void writeJson(String entryPath, String json) throws IOException {
    writeBytes(entryPath, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  /** Write raw bytes into the archive under the given entry path. */
  public void writeBytes(String entryPath, byte[] data) throws IOException {
    ZipEntry entry = new ZipEntry(entryPath);
    zip.putNextEntry(entry);
    zip.write(data);
    zip.closeEntry();
  }

  @Override public void close() throws IOException {
    zip.close();
  }
}
