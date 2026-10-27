package extension.logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javafx.application.Platform;
import javafx.scene.layout.BorderPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.StyleSpansBuilder;

public class Logger {
   private static volatile boolean verboseConsole = false;
   private static volatile PrintWriter fileWriter = null;
   private static final Object fileLock = new Object();
   private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
   private StyleClassedTextArea area;
   private volatile boolean initialized = false;
   private final List<Element> appendOnLoad = new ArrayList();

   public static void setVerboseConsole(boolean enabled) {
      verboseConsole = enabled;
   }

   public static void enableFileOutput(Path file) {
      synchronized(fileLock) {
         try {
            if (file.getParent() != null) {
               Files.createDirectories(file.getParent());
            }

            if (fileWriter != null) {
               try {
                  fileWriter.close();
               } catch (Exception var4) {
               }
            }

            fileWriter = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND), true);
            writeFileLine("=== Log started ===");
         } catch (IOException e) {
            System.err.println("[Logger] failed to open log file " + file + ": " + e.getMessage());
            fileWriter = null;
         }

      }
   }

   private static void writeFileLine(String line) {
      synchronized(fileLock) {
         if (fileWriter != null) {
            fileWriter.println("[" + TIMESTAMP_FORMAT.format(new Date()) + "] " + line);
         }

      }
   }

   private static void mirror(String tag, String message) {
      String line = (tag != null ? "[" + tag + "] " : "") + message;
      if (verboseConsole) {
         System.out.println(line);
      }

      writeFileLine(line);
   }

   public void initialize(BorderPane borderPane) {
      this.area = new StyleClassedTextArea();
      this.area.getStyleClass().add("themed-background");
      this.area.setEditable(false);
      VirtualizedScrollPane<StyleClassedTextArea> vsPane = new VirtualizedScrollPane<StyleClassedTextArea>(this.area);
      borderPane.setCenter(vsPane);
      synchronized(this.appendOnLoad) {
         this.initialized = true;
         if (!this.appendOnLoad.isEmpty()) {
            this.appendLog(this.appendOnLoad);
            this.appendOnLoad.clear();
         }

      }
   }

   private synchronized void appendLog(List<Element> elements) {
      Platform.runLater(() -> {
         StringBuilder sb = new StringBuilder();
         StyleSpansBuilder<Collection<String>> styleSpansBuilder = new StyleSpansBuilder<Collection<String>>(0);

         for(Element element : elements) {
            sb.append(element.text);
            styleSpansBuilder.add(Collections.singleton(element.className), element.text.length());
         }

         int oldLen = this.area.getLength();
         this.area.appendText(sb.toString());
         this.area.setStyleSpans(oldLen, styleSpansBuilder.create());
         this.area.moveTo(this.area.getLength());
         this.area.requestFollowCaret();
      });
   }

   public void log(String s, String className) {
      this.logNoNewline(s + "\n", className);
   }

   public void logNoNewline(String s, String className) {
      s = cleanTextContent(s);
      String trimmed = s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
      if (!trimmed.isEmpty()) {
         mirror(className, trimmed);
      }

      List<Element> elements = new ArrayList();
      elements.add(new Element(s, className.toLowerCase()));
      synchronized(this.appendOnLoad) {
         if (this.initialized) {
            this.appendLog(elements);
         } else {
            this.appendOnLoad.addAll(elements);
         }

      }
   }

   public void verbose(String message) {
      this.verbose("verbose", message);
   }

   public void verbose(String tag, String message) {
      mirror(tag, message);
   }

   private static String cleanTextContent(String text) {
      text = text.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");
      return text;
   }
}
