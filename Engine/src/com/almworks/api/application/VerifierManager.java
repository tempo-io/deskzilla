package com.almworks.api.application;

import com.almworks.util.commons.Procedure;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.Log;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.*;

import static org.almworks.util.Collections15.hashMap;
import static org.almworks.util.Collections15.hashSet;

/**
 * Use from one thread.
 */
public interface VerifierManager {
  VerifierManager TO_BE_IMPLEMENTED = new Dummy();

  /**
   * @return unmodifiable collection of verifiers for model keys
   */
  @NotNull
  Collection<ModelKeyVerifier> getVerifiers();

  @Nullable
  ModelKeyVerifier getVerifier(@NotNull ModelKey<?> key);

  /**
   * Sets context for calls to {@link ModelKeyVerifier#verify}.
   * During the specified lifetime, calls to {@link #callContextFunc} will trigger calls of {@link ModelKeyVerifier#verify},
   * and if the result is an error, it will be reported to the specified errors collector.
   * If the context already exists, its lifetime will be effectively forced to end, context function will be set to {@link ModelKeyVerifier#verify},
   * and error collector will be replaced by the specified one.
   * @param life verification context lifespan. If ended, does nothing
   * @param context context key
   * @param errorsCollector error reports go here
   */
  void setVerifyContext(@NotNull Lifespan life, @NotNull Object context, @NotNull Procedure<String> errorsCollector);

  /**
   * Sets context for calls to {@link ModelKeyVerifier#verifyEdit}.
   * During the specified lifetime, calls to {@link #callContextFunc} will trigger calls of {ModelKeyVerifier#verifyEdit},
   * and if the result is an error, it will be reported to the specified errors collector.
   * If the context already exists, its lifetime will be effectively forced to end, context function will be set to {@link ModelKeyVerifier#verifyEdit},
   * and error collector will be replaced by the specified one.
   * @param life verification context lifespan. If ended, the method does nothing
   * @param context context key
   * @param errorsCollector error reports go here
   */
  void setVerifyEditContext(@NotNull Lifespan life, @NotNull Object context, @NotNull Procedure<String> errorsCollector);

  /**
   * Calls verification function that was set for the specified context, passing the specified values to it.
   * If verification fails with an error description, it is fed to error collector and returned.
   * If the specified context is not active, returns null and does nothing.
   * @see {@link #setVerifyContext}
   * @see {@link #setVerifyEditContext}
   * @param key
   * @param modelKey model key for which to perform verification
   * @param oldValues this parameter goes the the context function
   * @param newValues this parameter goes the the context function    @return null if context is not active or verification succeeds, error description otherwise
   */
  @Nullable
  String callContextFunc(@NotNull Object key, ModelKey<?> modelKey,
    @Nullable PropertyMap oldValues, @NotNull PropertyMap newValues);

  /**
   * Calls verifiers for keys that have not been yet verified in the current context.
   * Error reports go into errors collector.
   * If context is not active, does nothing.
   */
  void callUnusedVerifiers(@NotNull Object key, @Nullable PropertyMap oldValues, @NotNull PropertyMap newValues);

  abstract class AbstractVerifierManager implements VerifierManager {
    private final Map<Object, VerificationContext> myContexts = hashMap();

    @Override
    public void setVerifyContext(@NotNull Lifespan life, @NotNull Object context, @NotNull final Procedure<String> errorsCollector) {
      if (life.isEnded()) return;
      setContext(life, context, new VerificationContext(false, errorsCollector));
    }

    @Override
    public void setVerifyEditContext(@NotNull Lifespan life, @NotNull Object context, @NotNull final Procedure<String> errorsCollector) {
      if (life.isEnded()) return;
      setContext(life, context, new VerificationContext(true, errorsCollector));
    }

    private void setContext(Lifespan life, final Object key, VerificationContext context) {
      if (myContexts.put(key, context) != null) {
        Log.warn("VerMan: replacing context function for " + key);
      }
      life.add(new Detach() {
        @Override
        protected void doDetach() throws Exception {
          myContexts.remove(key);
        }
      });
    }

    private String reportError(String error, Procedure<String> errorCollector) {
      if (error != null) {
        errorCollector.invoke(error);
      }
      return error;
    }

    @Override
    public String callContextFunc(@NotNull Object key, ModelKey<?> mk, @Nullable PropertyMap oldValues, @NotNull PropertyMap newValues) {
      ModelKeyVerifier ver = getVerifier(mk);
      if (ver == null) return null;
      VerificationContext context = myContexts.get(key);
      if (context == null) return null;
      return context.call(mk, oldValues, newValues);
    }

    @Override
    public void callUnusedVerifiers(@NotNull Object key, @Nullable PropertyMap oldValues, @NotNull PropertyMap newValues) {
      VerificationContext context = myContexts.get(key);
      if (context != null) {
        context.callUnusedVerifiers(oldValues, newValues);
      }
    }

    protected abstract Set<ModelKey<?>> verifiedKeys();

    private class VerificationContext {
      private final boolean myVerifyEdit;
      private final Set<ModelKey<?>> myUnverifiedKeys;
      private final Procedure<String> myErrorsCollector;

      public VerificationContext(boolean verifyEdit, Procedure<String> errorsCollector) {
        myVerifyEdit = verifyEdit;
        myErrorsCollector = errorsCollector;
        myUnverifiedKeys = hashSet(verifiedKeys());
      }

      public String call(ModelKey<?> mk, PropertyMap oldValues, PropertyMap newValues) {
        ModelKeyVerifier verifier = getVerifier(mk);
        if (verifier == null) return null;
        boolean unused = myUnverifiedKeys.remove(mk);
        assert unused;
        return callContextFunc(verifier, oldValues, newValues);
      }

      public void callUnusedVerifiers(PropertyMap oldValues, PropertyMap newValues) {
        for (Iterator<ModelKey<?>> i = myUnverifiedKeys.iterator(); i.hasNext();) {
          ModelKey<?> mk = i.next();
          i.remove();
          ModelKeyVerifier ver = getVerifier(mk);
          if (ver == null) {
            assert false;
            continue;
          }
          callContextFunc(ver, oldValues, newValues);
        }
      }

      private String callContextFunc(ModelKeyVerifier verifier, PropertyMap oldValues, PropertyMap newValues) {
        // todo :think: carefully whether to call verify() instead of verifyEdit(): see JCO-696, it's better to always check last edit
        String error = myVerifyEdit ? verifier.verifyEdit(oldValues, newValues) : verifier.verify(newValues);
        return reportError(error, myErrorsCollector);
      }
    }
  }

  class Dummy implements VerifierManager {
    private Dummy() {}

    @NotNull
    @Override
    public Collection<ModelKeyVerifier> getVerifiers() {
      return Collections.EMPTY_LIST;
    }

    @Override
    public ModelKeyVerifier getVerifier(ModelKey<?> key) {
      return null;
    }

    @Override
    public void setVerifyEditContext(@NotNull Lifespan life, @NotNull Object context, @NotNull Procedure<String> errorsCollector)
    {}

    @Override
    public void setVerifyContext(@NotNull Lifespan life, @NotNull Object context, @NotNull Procedure<String> errorsCollector)
    {}

    @Override
    public String callContextFunc(@NotNull Object key, ModelKey<?> modelKey, @Nullable PropertyMap oldValues, @NotNull PropertyMap newValues) {
      return null;
    }

    @Override
    public void callUnusedVerifiers(@NotNull Object key, @Nullable PropertyMap oldValues, @NotNull PropertyMap newValues)
    {}

    @Override
    public String toString() {
      return "Dummy verifier";
    }
  }
}
