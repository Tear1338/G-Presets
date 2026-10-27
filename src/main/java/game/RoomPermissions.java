package game;

import extension.logger.Logger;
import gearth.extensions.IExtension;
import gearth.protocol.HMessage;
import utils.Callback;

public class RoomPermissions {
   private final IExtension extension;
   private final Logger logger;
   private final Callback stateChangeCallback;
   private boolean canModifyWired;
   private boolean canMoveFurni;

   public RoomPermissions(IExtension extension, Logger logger, Callback stateChangeCallback) {
      this.extension = extension;
      this.logger = logger;
      this.stateChangeCallback = stateChangeCallback;
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredPermissions", this::onWiredPermissions);
      extension.intercept(HMessage.Direction.TOCLIENT, "YouAreController", this::onYouAreController);
      extension.intercept(HMessage.Direction.TOCLIENT, "YouAreNotController", this::onYouAreNotController);
      extension.intercept(HMessage.Direction.TOCLIENT, "CloseConnection", (m) -> this.clear());
      extension.intercept(HMessage.Direction.TOSERVER, "Quit", (m) -> this.clear());
      extension.intercept(HMessage.Direction.TOCLIENT, "RoomReady", (m) -> this.clear());
   }

   private void onWiredPermissions(HMessage msg) {
      this.canModifyWired = msg.getPacket().readBoolean();
      this.stateChangeCallback.call();
   }

   private void onYouAreController(HMessage msg) {
      this.canMoveFurni = true;
      this.stateChangeCallback.call();
   }

   private void onYouAreNotController(HMessage msg) {
      this.canMoveFurni = false;
      this.stateChangeCallback.call();
   }

   public boolean canModifyWired() {
      return this.canModifyWired;
   }

   public boolean canMoveFurni() {
      return this.canMoveFurni;
   }

   public void clear() {
      this.canModifyWired = false;
      this.canMoveFurni = false;
      this.stateChangeCallback.call();
   }
}
