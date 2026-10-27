package extension.tools.preview;

/** Habbo large-room projection: 64px tiles, 32px per height unit. */
public final class PreviewGeometry {
   private PreviewGeometry() { }
   public record Point(double x,double y) { }
   public static Point project(double x,double y,double z,int turns,boolean heights) {
      double a=x,b=y;
      switch(Math.floorMod(turns,4)){case 1:a=-y;b=x;break;case 2:a=-x;b=-y;break;case 3:a=y;b=-x;break;default:break;}
      return new Point((a-b)*32,(a+b)*16-(heights?z*32:0));
   }
   /** Native image origin is the first cell center of the rotated footprint. */
   public static Point assetOrigin(double x,double y,double z,int width,int depth,int turns,boolean heights) {
      double cx=x+(width-1)/2.0,cy=y+(depth-1)/2.0;
      Point center=project(cx,cy,z,turns,heights);
      int w=Math.floorMod(turns,2)==0?width:depth,d=Math.floorMod(turns,2)==0?depth:width;
      Point delta=project((w-1)/2.0,(d-1)/2.0,0,0,false);
      return new Point(center.x()-delta.x(),center.y()-delta.y());
   }
   public static double depth(double x,double y,int turns){return project(x,y,0,turns,false).y();}
}
