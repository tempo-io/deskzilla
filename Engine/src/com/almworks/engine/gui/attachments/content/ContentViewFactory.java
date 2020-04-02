package com.almworks.engine.gui.attachments.content;

import com.almworks.engine.gui.attachments.FileData;
import com.almworks.util.bmp.BMPException;
import com.almworks.util.bmp.BMPReader;
import com.almworks.util.components.ScrollablePanel;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.swing.AwtUtil;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

/**
 * This class is used to create approprite ContentView (decision makes by mime-type)
 */

public abstract class ContentViewFactory {
  private static final String DEFAULT_MIME_TYPE = "application/octet-stream";

  private static Map<String, ContentViewFactory> myViewFactories;

  public abstract ContentView create(FileData data) throws IOException;

  public synchronized static Map<String, ContentViewFactory> getViewFactories() {
    if (myViewFactories == null) {
      myViewFactories = Collections15.hashMap();

      TextViewFactory text = new TextViewFactory();
      myViewFactories.put("text", text);
      myViewFactories.put("application/xml", text);

      ImageViewFactory image = new ImageViewFactory();
      myViewFactories.put("image/png", image);
      myViewFactories.put("image/x-png", image);
      myViewFactories.put("image/gif", image);
      myViewFactories.put("image/x-gif", image);
      myViewFactories.put("image/jpeg", image);
      myViewFactories.put("image/x-jpeg", image);
      myViewFactories.put("image/pjpeg", image);

      myViewFactories.put("image/bmp", new BmpImageViewFactory());
    }
    return myViewFactories;
  }

  public static ContentViewFactory getFactoryByMimeType(String mimeType) {
    if (mimeType == null)
      mimeType = DEFAULT_MIME_TYPE;
    Map<String, ContentViewFactory> map = ContentViewFactory.getViewFactories();
    ContentViewFactory factory = map.get(mimeType);
    if (factory != null)
      return factory;
    int k = mimeType.indexOf('/');
    if (k >= 0) {
      mimeType = mimeType.substring(0, k);
      factory = map.get(mimeType);
    }
    if (factory == null)
      factory = map.get("*");
    return factory;
  }

  static class TextViewFactory extends ContentViewFactory {
    public ContentView create(FileData data) {
      // todo let the user choose encoding
      final JTextArea text = new JTextArea();
      text.setText(new String(data.getBytesInternal()));
      text.setEditable(false);
      text.setOpaque(true);
      text.setMargin(new Insets(2, 2, 2, 2));
      text.setBackground(Color.WHITE);
      UIUtil.setCaretAlwaysVisible(Lifespan.FOREVER, text);
      Font font = text.getFont();
      int size = font == null ? 11 : font.getSize();
      font = Font.decode("Monospaced-PLAIN-" + size);
      if (font != null)
        text.setFont(font);
      return new TextContentView(text);
    }
  }

  static class ImageViewFactory extends ContentViewFactory {
    private final TexturePaint myPaint;

    public ImageViewFactory() {
      myPaint = UIUtil.createChequeredPaint();
    }

    public ContentView create(FileData data) throws IOException {
      final ImageIcon icon = createIcon(data);

      JLabel label = new JLabel(icon) {
        protected void paintComponent(Graphics g) {
          AwtUtil.applyRenderingHints(g);
          UIUtil.fillPaint(g, this, myPaint, true);
          super.paintComponent(g);
        }
      };

      label.setOpaque(false);

      // Moved from AttachmentContent
      final JScrollPane content = new JScrollPane(new ScrollablePanel(label));
      Aqua.cleanScrollPaneBorder(content);
      Aqua.cleanScrollPaneResizeCorner(content);
      JViewport viewport = content.getViewport();
      viewport.setOpaque(true);
      viewport.setBackground(UIUtil.getEditorBackground());

      return new ContentView() {
        public JComponent getComponent() {
          return content;
        }

        public void copyToClipboard() {
          UIUtil.copyToClipboard(icon.getImage());
        }
      };
    }

    protected ImageIcon createIcon(FileData data) throws IOException {
      return new ImageIcon(data.getBytesInternal());
    }
  }

  static class BmpImageViewFactory extends ImageViewFactory {
    protected ImageIcon createIcon(FileData data) throws IOException {
      try {
        Image image = BMPReader.loadBMP(new ByteArrayInputStream(data.getBytesInternal()));
        return new ImageIcon(image);
      } catch (BMPException e) {
        throw new IOException("Cannot load bmp image: " + e);
      }
    }
  }

}

