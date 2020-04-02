package com.almworks.api.application;

import com.almworks.util.properties.PropertyMap;

/**
 * @author dyoma
 */
public interface DataPromotionPolicy {
  <T> boolean canPromote(ModelKey<T> key, PropertyMap fromValues, PropertyMap toValues);

  DataPromotionPolicy STANDARD = new DataPromotionPolicy() {
    public <T> boolean canPromote(ModelKey<T> key, PropertyMap fromValues, PropertyMap toValues) {
      return key.isEqualValue(fromValues, toValues);
    }
  };

  DataPromotionPolicy ALWAYS = new DataPromotionPolicy() {
    public <T> boolean canPromote(ModelKey<T> key, PropertyMap fromValues, PropertyMap toValues) {
      return true;
    }
  };

  DataPromotionPolicy NEVER = new DataPromotionPolicy() {
    public <T> boolean canPromote(ModelKey<T> key, PropertyMap fromValues, PropertyMap toValues) {
      return false;
    }
  };
}
