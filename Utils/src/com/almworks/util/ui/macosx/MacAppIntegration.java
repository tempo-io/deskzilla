package com.almworks.util.ui.macosx;

import com.almworks.util.commons.Procedure;
import com.almworks.util.ui.actions.CantPerformException;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;

public abstract class MacAppIntegration {
  protected static final Class<?>[] NO_ARGS = {};

  /**
   * Get a {@link Class} instance by name or throw a {@link com.almworks.util.ui.actions.CantPerformException}.
   * @param className Class name.
   * @return {@link Class} instance.
   * @throws com.almworks.util.ui.actions.CantPerformException when the class is not found.
   */
  protected static Class<?> getClass(String className) throws CantPerformException {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new CantPerformException(e);
    } catch (LinkageError e) {
      throw new CantPerformException(e);
    }
  }

  /**
   * Invoke a method reflectively.
   * @param className Class name, {@code null} to use {@code target}'s class.
   * @param target Target object; {@code null} for static methods, must be not-{@code null}
   * when {@code className} is {@code null}.
   * @param methodName Method name.
   * @param argClasses Method argument classes.
   * @param args Method arguments.
   * @return Method return value.
   * @throws CantPerformException If unable to find class or method, or unable to invoke.
   */
  protected static Object reflectivelyCall(
    String className, Object target, String methodName,
    Class<?>[] argClasses, Object... args) throws CantPerformException
  {
    final Class<?> clazz;
    if(className == null) {
      if(target == null) {
        throw new IllegalArgumentException();
      }
      clazz = target.getClass();
    } else {
      clazz = getClass(className);
    }

    final Method method;
    try {
      method = clazz.getMethod(methodName, argClasses);
    } catch (NoSuchMethodException e) {
      throw new CantPerformException(e);
    } catch (SecurityException e) {
      throw new CantPerformException(e);
    }

    final Object result;
    try {
      result = method.invoke(target, args);
    } catch (IllegalAccessException e) {
      throw new CantPerformException(e);
    } catch (InvocationTargetException e) {
      throw new CantPerformException(e.getCause());
    }

    return result;
  }


  protected static Object getEawtApplication() throws CantPerformException {
    return reflectivelyCall("com.apple.eawt.Application", null, "getApplication", NO_ARGS);
  }

  public abstract boolean isAvailable();

  public abstract void installMacHandlers();

  public abstract void setQuitHandler(Runnable quitHandler);

  public abstract void setAboutHandler(Runnable quitHandler);

  public abstract void setReopenHandler(Runnable quitHandler);

  public abstract void setOpenUriHandler(Procedure<URI> uriHandler);

  public abstract void setDefaultMenuBar(JMenuBar menuBar);
}
