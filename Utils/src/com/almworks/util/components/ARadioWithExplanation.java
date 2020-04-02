package com.almworks.util.components;

import javax.swing.*;

public class ARadioWithExplanation extends AComponentWithExplanation<JRadioButton>{
  public ARadioWithExplanation() {
    super(new JRadioButton());
  }

  @Override
  public void setText(String text) {
    getMain().setText(text);
  }
}
