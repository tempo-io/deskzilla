package com.almworks.util.collections;

import java.util.*;

/**
 * @author : Dyoma
 */
public class ConvertingList <D, R> extends AbstractList<R> {
  // TODO[dyoma] implement listIterator
  private List<D> mySource;
  private Convertor<D, R> myConvertor;

  protected ConvertingList(List<D> source, Convertor<D, R> convertor) {
    setSource(source);
    setConvertor(convertor);
  }

  public static <D, R> List<R> create(List<D> source, Convertor<D, R> convertor) {
    return createConverting(source, convertor);
  }

  public static <D, R> ConvertingList<D, R> createConverting(List<D> source, Convertor<D, R> convertor) {
    return new ConvertingList<D, R>(source, convertor);
  }

  private void setSource(List<D> source) {
    if (source == null) throw new NullPointerException();
    if (mySource != null && mySource.size() != source.size()) modCount++;
    mySource = source;
  }

  public void setConvertor(Convertor<D, R> convertor) {
    if (convertor == null) throw new NullPointerException();
    myConvertor = convertor;
  }

  public R get(int index) {
    return myConvertor.convert(mySource.get(index));
  }

  public int size() {
    return mySource.size();
  }

  public R remove(int index) {
    R result = myConvertor.convert(mySource.remove(index));
    modCount++;
    return result;
  }

  public Iterator<R> iterator() {
    return ConvertingIterator.create(mySource.iterator(), myConvertor);
  }
}
