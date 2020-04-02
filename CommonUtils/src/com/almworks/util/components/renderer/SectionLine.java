package com.almworks.util.components.renderer;

import com.almworks.util.Env;
import com.almworks.util.collections.ObjectArray;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.CanvasSection;

import java.awt.*;

/**
 * @author dyoma
 */
class SectionLine implements Canvas.Line {
  private static final int SECTION_GAP = 5;
  private final CanvasElement myParent;
  private final ObjectArray<Section> mySections = ObjectArray.create();

  public SectionLine(CanvasElement parent) {
    myParent = parent;
    mySections.add(new Section(myParent));
  }

  public void appendText(String text) {
    getCurrentSection().appendText(text);
  }

  public void copyTo(Canvas canvas) {
    for (int i = 0; i < mySections.size(); i++) {
      Section section = mySections.get(i);
      section.copyTo(canvas);
    }
  }

  public Section newSection() {
    Section result = new Section(myParent);
    mySections.add(result);
    return result;
  }

  public Section getCurrentSection() {
    assert!mySections.isEmpty();
    return mySections.get(mySections.size() - 1);
  }

  public CanvasSection[] getSections() {
    return mySections.toArray(new Section[mySections.size()]);
  }

  public void getSize(Dimension result, CanvasComponent component) {
    result.width = 0;
    result.height = 0;
    for (int i = 0; i < mySections.size(); i++) {
      Section section = mySections.get(i);
      Dimension sectionDim = section.getCachedSize(component);
      result.width += sectionDim.width;
      result.height = Math.max(result.height, sectionDim.height);
    }
  }

  public int paint(int x, int y, Graphics g, int width, int height, CanvasComponent component) {
    Rectangle sectionRect = new Rectangle();
    sectionRect.x = x;
    sectionRect.y = y;
    sectionRect.height = height;
    for (int i = 0; i < mySections.size() - 1; i++) {
      Section section = mySections.get(i);
      Dimension sectionSize = section.getCachedSize(component);
      sectionRect.width = sectionSize.width;
      Graphics graphics = Env.isMac() ? g.create() : g;
      try {
        section.paint(sectionRect, graphics, component);
      } finally {
        if (graphics != g)
          graphics.dispose();
      }
      sectionRect.x += sectionRect.width;
    }
    if (!mySections.isEmpty()) {
      Section lastSection = mySections.get(mySections.size() - 1);
      sectionRect.width = width - sectionRect.x + x;
      lastSection.paint(sectionRect, g, component);
    }
    return height;
  }

  public void clear() {
    if (mySections.size() == 1) {
      mySections.get(0).clear();
    } else {
      mySections.clear();
      mySections.add(new Section(myParent));
    }
  }
}
