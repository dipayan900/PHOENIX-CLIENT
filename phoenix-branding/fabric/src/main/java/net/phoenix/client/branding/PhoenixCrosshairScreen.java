package net.phoenix.client.branding;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
public final class PhoenixCrosshairScreen extends Screen {
 private final Screen parent;
 public PhoenixCrosshairScreen(Screen parent){super(Text.literal("Crosshair Studio"));this.parent=parent;}
 @Override protected void init(){
  int cx=width/2,y=Math.max(72,height/2-65);
  addDrawableChild(ButtonWidget.builder(Text.literal("STYLE: "+styleName()),b->{PhoenixHud.cycleCrosshairStyle();b.setMessage(Text.literal("STYLE: "+styleName()));}).dimensions(cx-105,y,210,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("CYCLE COLOR"),b->PhoenixHud.cycleCrosshairColor()).dimensions(cx-105,y+25,210,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("SIZE  −"),b->PhoenixHud.adjustCrosshairSize(-1)).dimensions(cx-105,y+50,100,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("SIZE  +"),b->PhoenixHud.adjustCrosshairSize(1)).dimensions(cx+5,y+50,100,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("GAP  −"),b->PhoenixHud.adjustCrosshairGap(-1)).dimensions(cx-105,y+75,100,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("GAP  +"),b->PhoenixHud.adjustCrosshairGap(1)).dimensions(cx+5,y+75,100,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("THICKNESS  −"),b->PhoenixHud.adjustCrosshairThickness(-1)).dimensions(cx-105,y+100,100,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("THICKNESS  +"),b->PhoenixHud.adjustCrosshairThickness(1)).dimensions(cx+5,y+100,100,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("CENTER DOT: "+(PhoenixHud.hasCrosshairDot()?"ON":"OFF")),b->{PhoenixHud.toggleCrosshairDot();b.setMessage(Text.literal("CENTER DOT: "+(PhoenixHud.hasCrosshairDot()?"ON":"OFF")));}).dimensions(cx-105,y+125,210,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("CUSTOM CROSSHAIR: "+(PhoenixHud.isCustomCrosshairEnabled()?"ON":"OFF")),b->{PhoenixHud.toggleCustomCrosshair();b.setMessage(Text.literal("CUSTOM CROSSHAIR: "+(PhoenixHud.isCustomCrosshairEnabled()?"ON":"OFF")));}).dimensions(cx-105,y+150,210,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("SAVE & RETURN"),b->close()).dimensions(cx-105,y+180,210,20).build());
 }
 private static String styleName(){return switch(PhoenixHud.getCrosshairStyle()){case 1->"PLUS";case 2->"SQUARE";case 3->"HORIZONTAL";case 4->"BRACKET";default->"CLASSIC";};}
 @Override public void render(DrawContext d,int mx,int my,float delta){renderBackground(d,mx,my,delta);d.drawCenteredTextWithShadow(textRenderer,title,width/2,24,0xFFFF8547);d.drawCenteredTextWithShadow(textRenderer,Text.literal("LIVE PREVIEW"),width/2,50,0xFFB8AEBB);PhoenixHud.renderCrosshair(d,width/2,height/2-4);d.drawCenteredTextWithShadow(textRenderer,Text.literal("Size "+PhoenixHud.getCrosshairSize()+" · Gap "+PhoenixHud.getCrosshairGap()+" · Thickness "+PhoenixHud.getCrosshairThickness()),width/2,height-35,0xFFE8DFE8);super.render(d,mx,my,delta);}
 @Override public void renderBackground(DrawContext d,int mx,int my,float delta){d.fillGradient(0,0,width,height,0xFF09070D,0xFF25111B);}
 @Override public void close(){if(client!=null)client.setScreen(parent);}
}