package net.phoenix.client.branding;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
/** Phoenix-branded Minecraft entry screen. */
public final class PhoenixTitleScreen extends Screen {
 public PhoenixTitleScreen(){super(Text.literal("Phoenix Client"));}
 @Override protected void init(){
  int w=Math.min(270,width-28),x=width/2-w/2,y=Math.max(88,height/2-76),h=23,g=7;
  addDrawableChild(ButtonWidget.builder(Text.literal("SINGLEPLAYER"),b->client.setScreen(new SelectWorldScreen(this))).dimensions(x,y,w,h).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("MULTIPLAYER"),b->client.setScreen(new MultiplayerScreen(this))).dimensions(x,y+h+g,w,h).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("PHOENIX MODULES"),b->client.setScreen(new PhoenixClientScreen(this))).dimensions(x,y+(h+g)*2,w,h).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("OPTIONS"),b->client.setScreen(new OptionsScreen(this,client.options))).dimensions(x,y+(h+g)*3,w,h).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("QUIT GAME"),b->client.scheduleStop()).dimensions(x,y+(h+g)*4,w,h).build());
 }
 @Override public void renderBackground(DrawContext d,int mx,int my,float delta){
  d.fillGradient(0,0,width,height,0xFF07060C,0xFF24101B);
  d.fill(width/2-150,Math.max(35,height/2-115),width/2+150,Math.max(36,height/2-114),0xAAFF702E);
  d.drawCenteredTextWithShadow(textRenderer,Text.literal("PHOENIX"),width/2,Math.max(42,height/2-105),0xFFFF8547);
  d.drawCenteredTextWithShadow(textRenderer,Text.literal("CLIENT  /  BUILT FOR YOUR GAME"),width/2,Math.max(58,height/2-88),0xFFB8AEBB);
  d.drawCenteredTextWithShadow(textRenderer,Text.literal("FABRIC  •  PHOENIX CLIENT"),width/2,height-18,0xFF857B89);
  super.renderBackground(d,mx,my,delta);
 }
 @Override public boolean shouldPause(){return false;}
}
