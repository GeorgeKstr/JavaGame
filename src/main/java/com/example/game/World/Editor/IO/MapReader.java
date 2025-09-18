package com.example.game.World.Editor.IO;

import java.io.*;
import java.util.zip.*;

/**
 * Helper for reading layered data from a .map zip file.
 */
public class MapReader implements Closeable {
  private final ZipFile zip;

  public MapReader(String path) throws IOException {
    this.zip = new ZipFile(path);
  }

  /** Read entry as UTF-8 string (for JSON, text, etc). Returns null if not found. */
  public String readJson(String entryPath) throws IOException {
    ZipEntry e = zip.getEntry(entryPath);
    if (e == null) return null;
    try (InputStream in = zip.getInputStream(e)) {
      return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
  }

  /** Read entry as raw bytes. Returns null if not found. */
  public byte[] readBytes(String entryPath) throws IOException {
    ZipEntry e = zip.getEntry(entryPath);
    if (e == null) return null;
    try (InputStream in = zip.getInputStream(e)) {
      return in.readAllBytes();
    }
  }

  /** Check if an entry exists in the archive. */
  public boolean has(String entryPath) {
    return zip.getEntry(entryPath) != null;
  }

  @Override
  public void close() throws IOException {
    zip.close();
  }
}
