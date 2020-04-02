package com.almworks.export.pdf.itext;

import com.almworks.api.application.*;
import com.almworks.api.application.viewer.Comment;
import com.almworks.api.engine.Connection;
import com.almworks.api.misc.WorkArea;
import com.almworks.api.platform.ProductInformation;
import com.almworks.export.ExportParameters;
import com.almworks.export.ExportedData;
import com.almworks.export.pdf.PDFParams;
import com.almworks.util.Pair;
import com.almworks.util.progress.Progress;
import com.almworks.util.properties.PropertyMap;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.PdfWriter;
import org.almworks.util.Log;
import util.concurrent.SynchronizedBoolean;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * This file is main collector interface. It contains some hacks to
 * prevent dependency of tracker type.
 *
 * @author Alex
 */
public class PrintFormatRecord {
  private final List<PrintElement<Document>> myPrintList;
  private final ExportedData myData;
  private final ExportParameters myParameters;
  private final WorkArea myWorkArea;

  public PrintFormatRecord(ExportedData data, ExportParameters parameters, WorkArea workArea) {
    myData = data;
    myParameters = parameters;
    myWorkArea = workArea;
    myPrintList = buildList();
  }

  public List<PrintElement<Document>> buildList() {
    final PrintBuilder builder = new PrintBuilder();

    final Set<ModelKey<?>> exportkeySet = new LinkedHashSet<ModelKey<?>>(myParameters.getKeys());
    exportkeySet.remove(getIssueKey());
    exportkeySet.remove(getIssueSummary());

    final Collection<Connection> conns = myData.getConnections();

    for (ModelKey<?> modelKey : exportkeySet) {
      if (modelKey.isExportable(conns)) {
        ExportValueType type = getExportValueType(modelKey);
        assert isValueTypeForAllRecords(modelKey, type) : type + " " + modelKey;
        if (type == ExportValueType.LARGE_STRING) {
          builder.addLargeField(modelKey);
        } else {
          builder.addAttribute(modelKey);
        }
      }
    }

    if (myParameters.getBoolean(PDFParams.COMMENTS)) {
      final ModelKey<?> modelKey = findKey("Comments");
      if (modelKey != null) {
        builder.addComments((ModelKey<Collection<Comment>>) modelKey);
      }
    }

    Set<ModelKey<?>> keySet = myParameters.getKeys();
    builder.setHeader(getIssueKey(), getIssueSummary());
    builder.setTableCompact(myParameters.getBoolean(PDFParams.COMPACT_TABLE));
    builder.setNewFromBlank(myParameters.getBoolean(PDFParams.ON_NEW_PAGE));
    builder.setAttaches((ModelKey<Collection<Attachment>>) findKey("Attachments"),
      myParameters.getBoolean(PDFParams.ATTACH_GRAPH), myParameters.getBoolean(PDFParams.ATTACH_TEXT));
    return builder.createList();
  }

  private boolean isValueTypeForAllRecords(ModelKey<?> modelKey, ExportValueType type) {
    for (ExportedData.ArtifactRecord artifactRecord : myData.getRecords()) {
      Pair<String, ExportValueType> pair = modelKey.formatForExport(artifactRecord.getValues(),
        myParameters.getNumberFormat(), myParameters.getDateFormat(), false);
      if (pair == null)
        continue;
      ExportValueType t = pair.getSecond();
      if (type != null && t != type) {
        Log.warn("export format: " + modelKey.getDisplayableName() + " " + type + " " + t);
      }
    }
    return true;
  }

  private ExportValueType getExportValueType(ModelKey<?> modelKey) {
    List<ExportedData.ArtifactRecord> records = myData.getRecords();
    if (records.isEmpty())
      return ExportValueType.STRING;
    ExportedData.ArtifactRecord first = records.get(0);
    Pair<String,ExportValueType> pair =
      modelKey.formatForExport(first.getValues(), myParameters.getNumberFormat(), myParameters.getDateFormat(), false);
    if (pair == null)
      return ExportValueType.STRING;
    return pair.getSecond();
  }

  private ModelKey<?> getIssueSummary() {
    return findKey("Summary");
  }

  //Hack
  private ModelKey<?> getIssueKey() {
    ModelKey<?> key = findKey("ID");
    key = key == null ? findKey("KEY") : key;
    return key;
  }

  private ModelKey<?> find(String what, Collection<ModelKey<?>> where) {
    for (ModelKey<?> modelKey : where) {
      if (isModelKeyByName(what, modelKey)) {
        return modelKey;
      }
    }
    return null;
  }

  private boolean isModelKeyByName(String what, ModelKey<?> modelKey) {
    return modelKey.getDisplayableName().equalsIgnoreCase(what) ||
      modelKey.getName().equalsIgnoreCase(what);
  }

  private ModelKey<?> findKey(String s) {
    return find(s, myData.getKeys());
  }

  public void writePdf(OutputStream outStream, Progress progress, SynchronizedBoolean cancelled, ProductInformation productInfo) {
    Document document = null;
    try {
      document = new Document();
      PdfWriter writer = PdfWriter.getInstance(document, outStream);
      ReportMetrics metrics = new ReportMetrics(myWorkArea.getEtcFile(WorkArea.ETC_EXPORT_PROPERTIES));
      writer.setPageEvent(new ReportPageEvent(metrics, myData, myParameters, productInfo));
      try {
        document.open();
      } catch (Exception e) {
        throw new IOException(e.getMessage());
      }

      double p = 0.1;
      double step = 0.9 / myData.getRecords().size();

      NumberFormat numberFormat = myParameters.getNumberFormat();
      DateFormat dateFormat = myParameters.getDateFormat();
      for (ExportedData.ArtifactRecord record : myData.getRecords()) {
        PropertyMap values = record.getValues();
        for (PrintElement<Document> printElement : myPrintList) {
          if (cancelled.get()) {
            throw new IOException("Export cancelled");
          }
          printElement.setContext(record.getConnection(), values, numberFormat, dateFormat, false);
          printElement.appendPrintElement(document, metrics, writer);
        }
        p += step;
        progress.setProgress(p);
      }

      try {
        document.close();
      } catch (Exception e) {
        throw new IOException(e.getMessage());
      }
    } catch (DocumentException e) {
      progress.addError(e.getMessage());
    } catch (IOException e) {
      progress.addError(e.getMessage());
    }

    progress.setProgress(1F);
  }
}
