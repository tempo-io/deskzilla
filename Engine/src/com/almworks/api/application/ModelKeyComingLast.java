package com.almworks.api.application;

/**
 * This is a marker interface for model keys that should operate after all other non-coming-last keys.
 * See com.almworks.explorer.loader.UserChangesImpl#createUserChanges
 */
public interface ModelKeyComingLast<V> extends ModelKey<V>{
}
