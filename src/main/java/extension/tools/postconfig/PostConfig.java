package extension.tools.postconfig;

import java.util.HashMap;
import java.util.Map;

public class PostConfig {
   private ItemSource itemSource;
   private Map<String, String> globalClassNameMapper;
   private Map<String, FurniPostConfig> furniPostConfigs;

   public PostConfig() {
      this.itemSource = ItemSource.PREFER_BC;
      this.globalClassNameMapper = new HashMap();
      this.furniPostConfigs = new HashMap();
   }

   public PostConfig(PostConfig source) {
      this.itemSource=source.itemSource;
      this.globalClassNameMapper=new HashMap<>(source.globalClassNameMapper);
      this.furniPostConfigs=new HashMap<>();
      source.furniPostConfigs.forEach((name,value)->this.furniPostConfigs.put(name,new FurniPostConfig(value)));
   }

   public void removeClassNameMapping(String className) {
      this.globalClassNameMapper.remove(className);
   }

   public void addClassNameMapping(String oldClass, String newClass) {
      this.globalClassNameMapper.put(oldClass, newClass);
   }

   public void addFurniPostConfig(FurniPostConfig config) {
      this.furniPostConfigs.put(config.getFurniIdentifier(), config);
   }

   public void removePostConfig(String furniIdentifier) {
      this.furniPostConfigs.remove(furniIdentifier);
   }

   public String getClassMapping(String className) {
      return (String)this.globalClassNameMapper.get(className);
   }

   public FurniPostConfig getFurniPostConfig(String furniIdentifier) {
      return (FurniPostConfig)this.furniPostConfigs.get(furniIdentifier);
   }

   public ItemSource getItemSource() {
      return this.itemSource;
   }

   public void setItemSource(ItemSource itemSource) {
      this.itemSource = itemSource;
   }
}
