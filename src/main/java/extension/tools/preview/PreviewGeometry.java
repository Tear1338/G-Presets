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
   public static double depth(double x,double y,int turns){return project(x,y,0,turns,false).y();}
}
