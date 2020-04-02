package com.almworks.bugzilla.provider.datalink.flags2.columns;

import com.almworks.bugzilla.provider.datalink.flags2.*;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.*;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.Log;

import java.awt.*;
import java.util.List;

class FlagsRenderer implements CanvasRenderer<PropertyMap> {
  private final FlagTypeItem myType;

  FlagsRenderer(FlagTypeItem type) {
    myType = type;
  }

  public void renderStateOn(CellState state, Canvas canvas, PropertyMap item) {
    List<FlagVersion> all = FlagsModelKey.getAllFlags(item, false);
    int plus = 0;
    int minus = 0;
    int ask = 0;
    int me = 0;
    FlagVersion pFlag = null;
    FlagVersion mFlag = null;
    FlagVersion meFlag = null;
    FlagVersion aFlag = null;
    for (FlagVersion flag : all) {
      if (flag.getTypeItem() != myType.getResolvedItem()) continue;
      switch (flag.getStatusChar()) {
      case '+': plus++; pFlag = flag; break;
      case '-': minus++; mFlag = flag; break;
      case '?':
        if (flag.isThisUserRequested()) me++;
        else ask++;
        aFlag = flag;
        break;
      default: Log.error("Unknown status " + flag.getStatusChar());
      }
    }
    if (plus + minus + ask + me <= 1) renderSingleFlag(canvas, pFlag, mFlag, aFlag, meFlag);
    else {
      if (me > 0) appendText(canvas, me + "? ", true);
      if (ask > 0) canvas.appendText(ask + "? ");
      if (plus > 0) canvas.appendText(plus + "+ ");
      if (minus > 0) canvas.appendText(minus + FlagStatus.MINUS.getDisplayPresentation() + " ");
    }
  }

  private void renderSingleFlag(Canvas canvas, FlagVersion pFlag, FlagVersion mFlag, FlagVersion aFlag, FlagVersion meFlag) {
    FlagVersion flag = pFlag;
    if (flag == null) flag = mFlag;
    if (flag == null) flag = meFlag;
    if (flag == null) flag = aFlag;
    if (flag == null) return;
    appendText(canvas, FlagStatus.fromChar(flag.getStatusChar()).getDisplayPresentation(), meFlag != null);
  }

  static void appendText(Canvas canvas, String text, boolean me) {
    if (me) {
      CanvasSection section = canvas.getCurrentSection();
      if (!section.isEmpty()) section = canvas.newSection();
      section.setFontStyle(Font.BOLD);
    }
    canvas.appendText(text);
    if (me) {
      canvas.newSection();
    }
  }
}
