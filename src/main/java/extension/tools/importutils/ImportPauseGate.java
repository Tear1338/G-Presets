package extension.tools.importutils;

import java.util.function.BooleanSupplier;

/** Pauses an import worker between operations without blocking network callbacks. */
public final class ImportPauseGate {
   private boolean paused;
   private long epoch;
   public synchronized void requestPause() { paused=true; }
   public synchronized boolean isPaused() { return paused; }
   public synchronized void resume() { paused=false;notifyAll(); }
   public synchronized void cancel() { paused=false;epoch++;notifyAll(); }
   public synchronized boolean checkpoint(BooleanSupplier stillValid) {
      long current=epoch;
      try {
         while(paused && current==epoch && stillValid.getAsBoolean())wait(200);
      } catch(InterruptedException error) {Thread.currentThread().interrupt();return false;}
      return current==epoch && stillValid.getAsBoolean();
   }
}
