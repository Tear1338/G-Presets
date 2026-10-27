package extension.tools.postconfig;

import gearth.extensions.parsers.HPoint;
import java.util.HashMap;
import java.util.Map;

public class FurniPostConfig {
   private final String furniIdentifier;
   private final boolean useExistingFurni;
   private final int existingFurniId;
   private final String overrideClassName;
   private final HPoint overrideLocation;
   private final Integer overrideRotation;
   private final String overrideState;
   private final Map<Integer, Integer> wiredRotationMapping;
   private final Map<String, String> wiredStateMapping;

   public FurniPostConfig(String furniIdentifier, boolean useExistingFurni, int existingFurniId, String overrideClassName, HPoint overrideLocation, Integer overrideRotation, String overrideState, Map<Integer, Integer> wiredRotationMapping, Map<String, String> wiredStateMapping) {
      this.furniIdentifier = furniIdentifier;
      this.useExistingFurni = useExistingFurni;
      this.existingFurniId = existingFurniId;
      this.overrideClassName = overrideClassName;
      this.overrideLocation = overrideLocation;
      this.overrideRotation = overrideRotation;
      this.overrideState = overrideState;
      this.wiredRotationMapping = wiredRotationMapping;
      this.wiredStateMapping = wiredStateMapping;
   }

   public FurniPostConfig(FurniPostConfig source) {
      this(source.furniIdentifier,source.useExistingFurni,source.existingFurniId,source.overrideClassName,
         source.overrideLocation==null?null:new HPoint(source.overrideLocation.getX(),source.overrideLocation.getY(),source.overrideLocation.getZ()),
         source.overrideRotation,source.overrideState,new HashMap<>(source.wiredRotationMapping),new HashMap<>(source.wiredStateMapping));
   }

   public FurniPostConfig(String furniIdentifier) {
      this.furniIdentifier = furniIdentifier;
      this.useExistingFurni = false;
      this.existingFurniId = -1;
      this.overrideClassName = null;
      this.overrideLocation = null;
      this.overrideRotation = null;
      this.overrideState = null;
      this.wiredRotationMapping = new HashMap();
      this.wiredStateMapping = new HashMap();
   }

   public String getFurniIdentifier() {
      return this.furniIdentifier;
   }

   public boolean isUseExistingFurni() {
      return this.useExistingFurni;
   }

   public int getExistingFurniId() {
      return this.existingFurniId;
   }

   public String getOverrideClassName() {
      return this.overrideClassName;
   }

   public HPoint getOverrideLocation() {
      return this.overrideLocation;
   }

   public Integer getOverrideRotation() {
      return this.overrideRotation;
   }

   public String getOverrideState() {
      return this.overrideState;
   }

   public Map<Integer, Integer> getWiredRotationMapping() {
      return this.wiredRotationMapping;
   }

   public Map<String, String> getWiredStateMapping() {
      return this.wiredStateMapping;
   }
}
