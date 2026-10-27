package extension.tools.preview;

import java.io.*;
import java.util.*;
import java.util.zip.InflaterInputStream;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Read geometry only from official SWF assets; never load or execute ActionScript. */
public final class FurnitureAssetGeometry {
   public record Anchor(double x,double y,boolean background) { }
   private record Size(int width,int height) { }
   private record Asset(String name,String source,int x,int y,boolean flipX,boolean flipY) { }
   private final Map<String,Asset> assets=new HashMap<>();
   private final Map<String,Size> sizes=new HashMap<>();
   private Element visualization;
   private String classname;
   private FurnitureAssetGeometry() { }
   private static int u16(byte[] b,int i){return (b[i]&255)|((b[i+1]&255)<<8);}
   private static int u32(byte[] b,int i){return u16(b,i)|(u16(b,i+2)<<16);}
   private static List<Element> children(Element parent,String tag) {
      List<Element> result=new ArrayList<>();if(parent!=null)for(Node n=parent.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e && e.getTagName().equals(tag))result.add(e);return result;
   }
   private static Element child(Element parent,String tag){return children(parent,tag).stream().findFirst().orElse(null);}
   private static int number(Element e,String key,int fallback){return e==null || !e.hasAttribute(key)?fallback:Integer.parseInt(e.getAttribute(key));}
   private static Element identified(Element parent,String tag,int id){return children(parent,tag).stream().filter(e->number(e,"id",-1)==id).findFirst().orElse(null);}
   public static FurnitureAssetGeometry parse(String classname,byte[] input) throws Exception {
      if(input.length<12 || input.length>8_000_000 || input[1]!='W' || input[2]!='S')throw new IOException("Invalid furniture SWF");
      int declared=u32(input,4);if(declared<12 || declared>24_000_000)throw new IOException("Furniture SWF exceeds geometry limits");
      byte[] data;
      if(input[0]=='C') {
         try(var stream=new InflaterInputStream(new ByteArrayInputStream(input,8,input.length-8));var out=new ByteArrayOutputStream()) {
            out.write(input,0,8);byte[] body=stream.readNBytes(declared-7);if(body.length!=declared-8)throw new IOException("Invalid compressed geometry size");out.write(body);data=out.toByteArray();
         }
      }else if(input[0]=='F' && input.length==declared)data=input;else throw new IOException("Unsupported furniture compression");
      int rectBits=(data[8]&255)>>3,offset=8+(5+4*rectBits+7)/8+4;
      Map<Integer,List<String>> symbols=new HashMap<>();Map<Integer,Size> bitmapSizes=new HashMap<>();List<byte[]> documents=new ArrayList<>();int tags=0;
      while(offset+2<=data.length && ++tags<50000) {
         int header=u16(data,offset);offset+=2;int tag=header>>6,length=header&63;
         if(length==63){if(offset+4>data.length)throw new IOException("Truncated SWF tag");length=u32(data,offset);offset+=4;}
         if(length<0 || length>data.length-offset)throw new IOException("Invalid SWF tag length");int end=offset+length;
         if((tag==76 || tag==56) && length>=2) {
            int count=u16(data,offset),cursor=offset+2;
            for(int i=0;i<count;i++) {
               if(cursor+2>end)throw new IOException("Invalid asset symbols");int id=u16(data,cursor);cursor+=2;int start=cursor;
               while(cursor<end && data[cursor]!=0)cursor++;if(cursor==end)throw new IOException("Unterminated asset symbol");
               symbols.computeIfAbsent(id,k->new ArrayList<>()).add(new String(data,start,cursor-start,java.nio.charset.StandardCharsets.UTF_8));cursor++;
            }
         }else if((tag==20 || tag==36) && length>=7)bitmapSizes.put(u16(data,offset),new Size(u16(data,offset+3),u16(data,offset+5)));
         else if((tag==21 || tag==35 || tag==90) && length>=8) {
            int start=offset+(tag==21?2:tag==35?6:8),imageEnd=tag==21?end:start+u32(data,offset+2);
            if(imageEnd<=end && imageEnd>start)try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(data,start,imageEnd-start))) {
               var readers=ImageIO.getImageReaders(stream);if(readers.hasNext()){var reader=readers.next();try{reader.setInput(stream);bitmapSizes.put(u16(data,offset),new Size(reader.getWidth(0),reader.getHeight(0)));}finally{reader.dispose();}}
            }catch(IOException ignored){ }
         }else if(tag==87 && length>6) {
            byte[] payload=Arrays.copyOfRange(data,offset+6,end);
            if(payload.length<2_000_000 && payload[0]=='<')documents.add(payload);
         }
         offset=end;if(tag==0)break;
      }
      FurnitureAssetGeometry geometry=new FurnitureAssetGeometry();geometry.classname=classname;
      for(var entry:bitmapSizes.entrySet())for(String symbol:symbols.getOrDefault(entry.getKey(),List.of())) {
         String name=symbol,prefix=classname+"_";if(name.startsWith(prefix))name=name.substring(prefix.length());geometry.sizes.put(name,entry.getValue());
      }

      DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
      for(byte[] xml:documents) {
         Element root=factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
         if(root.getTagName().equals("assets"))for(Element asset:children(root,"asset")) {
            String name=asset.getAttribute("name");geometry.assets.put(name,new Asset(name,asset.getAttribute("source"),number(asset,"x",0),number(asset,"y",0),number(asset,"flipH",0)==1,number(asset,"flipV",0)==1));
         }
         if(root.getTagName().equals("visualizationData"))for(Element view:children(child(root,"graphics"),"visualization"))if(number(view,"size",0)==64)geometry.visualization=view;
      }
      return geometry;
   }
   private Size assetSize(Asset asset,Set<String> seen) {
      if(asset==null || !seen.add(asset.name()))return null;Size size=sizes.get(asset.name());return size!=null?size:assetSize(assets.get(asset.source()),seen);
   }
   private static boolean visible(Element global,Element directional) {return number(global,"alpha",255)>0 && number(directional,"alpha",number(global,"alpha",255))>0;}
   public Optional<Anchor> anchor(int requestedDirection,int state,int imageWidth,int imageHeight) {
      if(visualization==null)return Optional.empty();
      int direction=Math.floorMod(requestedDirection,8);Element directions=child(visualization,"directions");
      Element dir=identified(directions,"direction",direction);if(dir==null){direction=0;dir=identified(directions,"direction",0);}
      Element animation=identified(child(visualization,"animations"),"animation",state);if(animation==null)animation=identified(child(visualization,"animations"),"animation",0);
      int left=Integer.MAX_VALUE,top=Integer.MAX_VALUE,right=Integer.MIN_VALUE,bottom=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
      List<String> layers=new ArrayList<>();layers.add("sd");for(int i=0;i<number(visualization,"layerCount",0) && i<26;i++)layers.add(Character.toString((char)('a'+i)));
      for(String name:layers) {
         int layer=name.equals("sd")?-1:name.charAt(0)-'a';Element global=identified(child(visualization,"layers"),"layer",layer),directional=identified(dir,"layer",layer);
         if(!visible(global,directional))continue;
         Element animated=identified(animation,"animationLayer",layer),sequence=child(animated,"frameSequence"),frame=child(sequence,"frame");int frameId=number(frame,"id",0);
         Asset asset=assets.get(classname+"_64_"+name+"_"+direction+"_"+frameId);
         if(asset==null)asset=assets.get(classname+"_64_"+name+"_"+direction+"_0");
         if(asset==null)continue;Size size=assetSize(asset,new HashSet<>());if(size==null || size.width()<1 || size.height()<1)return Optional.empty();
         int x=asset.flipX()?size.width()-asset.x():asset.x(),y=asset.flipY()?size.height()-asset.y():asset.y();
         left=Math.min(left,-x);top=Math.min(top,-y);right=Math.max(right,size.width()-x);bottom=Math.max(bottom,size.height()-y);
         if(layer>=0)maxZ=Math.max(maxZ,number(directional,"z",number(global,"z",0)));
      }
      // Cropped, unsupported or animated variants with different bounds stay explicitly approximate.
      if(left==Integer.MAX_VALUE || right-left!=imageWidth || bottom-top!=imageHeight)return Optional.empty();
      return Optional.of(new Anchor(-left,-top,maxZ<0));
   }
}
