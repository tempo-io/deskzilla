package com.almworks.util.components;

import org.jetbrains.annotations.*;

import java.util.Set;

/**
 * @author dyoma
 */
public interface TreeStructure<E, K, N extends TreeModelBridge<? extends E>> {
  K getNodeKey(E element);

  @Nullable
  K getNodeParentKey(E element);

  N createTreeNode(E element);

  class FlatTree<T> implements TreeStructure<T, T, TreeModelBridge<T>> {
    @SuppressWarnings({"RawUseOfParameterizedType"})
    private static final FlatTree INSTANCE = new FlatTree<Object>(); 

    public T getNodeKey(T element) {
      return element;
    }

    public T getNodeParentKey(T element) {
      return null;
    }

    public TreeModelBridge<T> createTreeNode(T element) {
      return new TreeModelBridge<T>(element);
    }

    public static <T, K> TreeStructure<T, K, TreeModelBridge<T>> instance() {
      return INSTANCE;
    }
  }

  interface MultiParent<E, K, N extends TreeModelBridge<? extends E>> extends TreeStructure<E, K, N> {
    Set<K> getNodeParentKeys(E element);
  }
}
