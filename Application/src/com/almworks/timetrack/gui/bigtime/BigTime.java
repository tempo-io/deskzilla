package com.almworks.timetrack.gui.bigtime;

import com.almworks.util.collections.ChangeListener;
import com.almworks.util.commons.Procedure;
import org.jetbrains.annotations.*;

/**
 * The interface responsible for the "Big Time"
 * value in the Time Tracker.
 */
public interface BigTime {
  String EMPTY_VALUE = "--:--";

  /**
   * @return The displayed name of this kind of Big Time.
   */
  String getName();

  /**
   * @return The ID of this kind of Big Time for saving
   * and restoring.
   */
  String getId();

  /**
   * @return The description of this kind of Big Time
   * for the user.
   */
  String getDescription();

  /**
   * The main method used to receive the "Big Time".
   * When the value is ready it is passed to {@code proc}.
   * Implementors must always call {@code proc} in
   * the AWT thread.
   * @param proc The procedure that would receive
   * the value as soon as it's ready.
   */
  void getBigTimeText(Procedure<String> proc);

  /**
   * Lifecycle method called by the client
   * upon obtaining the instance.
   * @param listener The listener that this instance
   * should use to tell its client to update.
   */
  void attach(@Nullable ChangeListener listener);

  /**
   * Lifcycle method called by the client
   * when this instance is no longer needed.
   */
  void detach();
}
