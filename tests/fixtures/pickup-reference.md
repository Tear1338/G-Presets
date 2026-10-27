# Native pickup packet reference

The local undo payload fixture follows the native Flash composer:
[PickupObjectMessageComposer.as](https://github.com/habbo-cc/Habbo-Sky/blob/feature-air/src/com/sulake/habbo/communication/messages/outgoing/room/engine/PickupObjectMessageComposer.as).
Its message array is category (2 floor / 1 wall), then object ID, both integers.
The extension resolves the current packet ID by the G-Earth name `PickupObject`; no numeric header is assumed.
The connected identity guard reads only the first integer of the named `UserObject` packet, restoring the read index afterward.
These references validate the wire shape, not acceptance or game behavior on every current hotel.
