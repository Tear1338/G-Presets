package tests;

import extension.tools.preview.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.DeflaterOutputStream;

/** Synthetic SWF envelopes with measured official asset geometry, no client code or private presets. */
public final class PreviewGeometryTests {
 private static int checks;
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
 private static void u16(ByteArrayOutputStream b,int value){b.write(value&255);b.write(value>>8&255);}
 private static void u32(ByteArrayOutputStream b,int value){u16(b,value);u16(b,value>>16);}
 private static void tag(ByteArrayOutputStream b,int type,byte[] data)throws IOException{u16(b,(type<<6)|63);u32(b,data.length);b.write(data);}
 private static byte[] bitmap(int id,int w,int h){var b=new ByteArrayOutputStream();u16(b,id);b.write(5);u16(b,w);u16(b,h);return b.toByteArray();}
 private static byte[] xml(int id,String text)throws IOException{var b=new ByteArrayOutputStream();u16(b,id);u32(b,0);b.write(text.getBytes(StandardCharsets.UTF_8));return b.toByteArray();}
 static byte[] fixture(String cls,int w,int h,int ax,int ay,boolean background,boolean alias,boolean compressed)throws IOException {
  var body=new ByteArrayOutputStream();body.write(new byte[5]);tag(body,36,bitmap(1,w,h));
  var symbols=new ByteArrayOutputStream();u16(symbols,alias?2:1);u16(symbols,1);symbols.write((cls+"_"+cls+"_64_a_0_0").getBytes(StandardCharsets.UTF_8));symbols.write(0);
  if(alias){u16(symbols,1);symbols.write((cls+"_"+cls+"_64_a_4_0").getBytes(StandardCharsets.UTF_8));symbols.write(0);}tag(body,76,symbols.toByteArray());
  String assets="<assets><asset name='"+cls+"_64_a_0_0' x='"+ax+"' y='"+ay+"'/><asset name='"+cls+"_64_a_2_0' source='"+cls+"_64_a_0_0' flipH='1' x='"+ax+"' y='"+ay+"'/><asset name='"+cls+"_64_a_4_0' x='"+ax+"' y='"+ay+"'/></assets>";
  tag(body,87,xml(2,assets));tag(body,87,xml(3,"<visualizationData><graphics><visualization size='64' layerCount='1'><layers><layer id='0' z='"+(background?-9000:0)+"'/></layers><directions><direction id='0'/><direction id='2'/><direction id='4'/></directions></visualization></graphics></visualizationData>"));tag(body,0,new byte[0]);
  var swf=new ByteArrayOutputStream();swf.write((compressed?"CWS":"FWS").getBytes(StandardCharsets.US_ASCII));swf.write(10);u32(swf,body.size()+8);
  if(compressed){try(var zip=new DeflaterOutputStream(swf)){zip.write(body.toByteArray());}}else swf.write(body.toByteArray());return swf.toByteArray();
 }
 public static int run()throws Exception {
  checks=0;
  var sand=FurnitureAssetGeometry.parse("sand",fixture("sand",129,70,64,21,true,true,true));
  check(sand.anchor(0,0,129,70).orElseThrow().equals(new FurnitureAssetGeometry.Anchor(64,21,true)),"2x2 sand uses its native anchor rather than constant bottom offset");
  check(sand.anchor(4,0,129,70).isPresent(),"shared bitmap symbols retain every direction alias");
  check(sand.anchor(0,0,128,70).isEmpty(),"different/cropped image bounds cannot claim exact alignment");
  var water=FurnitureAssetGeometry.parse("water",fixture("water",209,105,104,37,true,false,false));
  check(water.anchor(0,0,209,105).orElseThrow().y()==37,"water overhang is positioned from official image origin");
  var tree=FurnitureAssetGeometry.parse("tree",fixture("tree",82,163,41,146,false,false,true));
  check(tree.anchor(0,0,82,163).orElseThrow().y()==146,"tall furniture is not placed using the image bottom");
  var stage=FurnitureAssetGeometry.parse("stage",fixture("stage",98,59,65,26,false,false,false));
  check(stage.anchor(2,0,98,59).orElseThrow().x()==33,"horizontal aliases mirror the native anchor around bitmap width");
  check(stage.anchor(6,0,98,59).orElseThrow().x()==65,"unsupported imager direction uses the documented direction-zero fallback");
  for(int turn=0;turn<4;turn++) {
   int w=1,d=2;var origin=PreviewGeometry.assetOrigin(3,4,2,w,d,turn,true);
   // Rotate the first-cell centers and choose their minimum X/Y to get the room object's new origin.
   double a=3,b=4;if(turn==1){a=-5;b=3;}if(turn==2){a=-3;b=-5;}if(turn==3){a=4;b=-3;}
   var expected=PreviewGeometry.project(a,b,2,0,true);
   check(origin.equals(expected),"rectangular footprint keeps native origin in camera quarter-turn "+turn);
  }
  var base=PreviewGeometry.assetOrigin(0,0,0,2,2,0,true);var higher=PreviewGeometry.assetOrigin(0,0,1.5,2,2,0,true);
  check(base.y()-higher.y()==48,"stacked item height uses the same room projection as its footprint");
  check(PreviewGeometry.assetOrigin(0,0,1.5,2,2,0,false).equals(base),"hiding heights removes only the height shift");
  byte[] broken=fixture("bad",1,1,0,0,false,false,false);broken[4]=(byte)255;
  try{FurnitureAssetGeometry.parse("bad",broken);throw new AssertionError("malformed declared length accepted");}catch(IOException expected){checks++;}
  return checks;
 }
}
