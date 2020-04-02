package com.almworks.util.tags;

import com.almworks.util.files.FileUtil;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

import static com.almworks.util.collections.Functional.next;
import static org.almworks.util.Collections15.arrayList;

/** Defines the format of intermediate (database independent) file-based tag storage.
 * Implements the storage. */
public class TagFileStorage {
  public static final String DEFAULT_FILE_NAME = "tags.txt";
  public static final String HEADER = "# This file contains tags extracted from a JIRA Client / Deskzilla workspace.";
  public static final String TAG_ICON_PATH_PREFIX = "iconPath=";

  public static void write(Iterable<TagInfo> tags, @NotNull PrintStream ps, @NotNull String originalWorkspace) throws IOException {
    ps.println(HEADER);
    ps.append("# Original workspace: ").println(originalWorkspace);
    ps.append("# ").println(new Date());
    for (TagInfo tag : tags) {
      ps.println(tag.getName());
      ps.append(TAG_ICON_PATH_PREFIX).println(tag.getIconPath());
      for (String url : tag.getItemUrls()) {
        ps.println(url);
      }
      ps.println();
    }
  }

  public static List<TagInfo> read(@NotNull File f) throws IOException {
    Iterator<String> content = Arrays.asList(FileUtil.readFile(f.getAbsolutePath(), "Unicode").split("\n|\r\n")).iterator();
    // skip three first lines, check constant header
    String line = next(content);
    if (!HEADER.equals(line)) throw new IOException("Format is not supported");
    eatCommentsLine(content);
    eatCommentsLine(content);
    List<TagInfo> tagInfos = arrayList();
    while (true) {
      String tagName = next(content);
      if (tagName == null) return tagInfos;
      String iconPathWithPrefix = next(content);
      if (iconPathWithPrefix == null || !iconPathWithPrefix.startsWith(TAG_ICON_PATH_PREFIX))
        throw new IOException("Format is not supported");
      String iconPath = iconPathWithPrefix.substring(TAG_ICON_PATH_PREFIX.length());
      List<String> urls = arrayList();
      for (line = next(content); line != null && !line.isEmpty(); line = next(content)) {
        urls.add(line);
      }
      tagInfos.add(new TagInfo(tagName, iconPath, urls));
    }
  }

  private static void eatCommentsLine(Iterator<String> content) throws IOException {
    String line = next(content);
    if (line == null || !line.startsWith("# "))
      throw new IOException("Format is not supported");
  }

  public static final class TagInfo {
    private final String myName;
    private final String myIconPath;
    private final List<String> myItemUrls;

    public TagInfo(String name, String iconPath, List<String> itemUrls) {
      myName = name;
      myIconPath = iconPath;
      myItemUrls = itemUrls;
    }

    public String getName() {
      return myName;
    }

    public String getIconPath() {
      return myIconPath;
    }

    public List<String> getItemUrls() {
      return Collections.unmodifiableList(myItemUrls);
    }
  }
}
