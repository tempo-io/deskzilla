package com.almworks.api.application.util;

import com.almworks.api.application.LoadedItemServices;
import com.almworks.api.application.UserChanges;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.ItemVersion;
import org.almworks.util.Const;

import java.util.Date;

public class DateSimpleIO extends BaseSimpleIO<Date, Date> {
  public DateSimpleIO(DBAttribute<Date> attribute) {
    super(attribute);
  }

  protected Date extractValue(Date dbValue, ItemVersion version, LoadedItemServices itemServices) {
    return dbValue != null && dbValue.getTime() < Const.DAY ? null : dbValue;
  }

  protected Date toDatabaseValue(UserChanges changes, Date userInput) {
    if (userInput == null || userInput.getTime() < Const.DAY)
      return null;
    else
      return userInput;
  }
}
