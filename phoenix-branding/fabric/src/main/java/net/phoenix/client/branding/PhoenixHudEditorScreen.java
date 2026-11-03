package net.phoenix.client.branding;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
/** Individually draggable Phoenix HUD elements with persistent per-element positions. */
public final class PhoenixHudEditorScreen extends Screen {
 private final Screen parent; private String draggingKey; private double dragX,dragY;
 public PhoenixHudEditorScreen(Screen parent){super(Text.literal("Phoenix HUD Editor"));this.parent=parent;}
 @Override protected void init(){int y=height-28;
  addDrawableChild(ButtonWidget.builder(Text.literal("RESET ALL"),b->PhoenixHud.resetElementPositions()).dimensions(width/2-154,y,96,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("SAVE & RETURN"),b->{PhoenixHud.saveConfig();close();}).dimensions(width/2-53,y,106,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("CANCEL"),b->close()).dimensions(width/2+62,y,92,20).build());
 }
 @Override public void render(DrawContext d,int mx,int my,float delta){
  d.fillGradient(0,0,width,height,0xF509070D,0xF525111B);
  d.drawCenteredTextWithShadow(textRenderer,title,width/2,12,0xFFFF8547);
  d.drawCenteredTextWithShadow(textRenderer,Text.literal("DRAG EACH HUD WIDGET SEPARATELY • POSITIONS SAVE AUTOMATICALLY"),width/2,27,0xFFB8AEBB);
  for(String key:PhoenixHud.getElementKeys()){int x=PhoenixHud.getElementX(key),y=PhoenixHud.getElementY(key),w=Math.max(92,textRenderer.getWidth(key)+22);
   d.fill(x,y,x+w,y+18,0xD5100D14);d.fill(x,y,x+2,y+18,0xFFFF702E);
   d.drawTextWithShadow(textRenderer,Text.literal(key),x+7,y+5,0xFFFFA06C);
   d.drawTextWithShadow(textRenderer,Text.literal("≡"),x+w-12,y+5,0xFFB8AEBB);
  }
  d.drawCenteredTextWithShadow(textRenderer,Text.literal("Move widgets away from screen edges if they overlap."),width/2,height-42,0xFFB8AEBB);
  super.render(d,mx,my,delta);
 }
 @Override public boolean mouseClicked(double mx,double my,int button){if(button==0){String[] keys=PhoenixHud.getElementKeys();
  for(int i=keys.length-1;i>=0;i--){String key=keys[i];int x=PhoenixHud.getElementX(key),y=PhoenixHud.getElementY(key),w=Math.max(92,textRenderer.getWidth(key)+22);
   if(mx>=x&&mx<=x+w&&my>=y&&my<=y+18){draggingKey=key;dragX=mx-x;dragY=my-y;return true;}}
  }return super.mouseClicked(mx,my,button);}
 @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(button==0&&draggingKey!=null){PhoenixHud.setElementPosition(draggingKey,(int)(mx-dragX),(int)(my-dragY));return true;}return super.mouseDragged(mx,my,button,dx,dy);}
 @Override public boolean mouseReleased(double mx,double my,int button){if(button==0&&draggingKey!=null){draggingKey=null;PhoenixHud.saveConfig();return true;}return super.mouseReleased(mx,my,button);}
 @Override public void close(){if(client!=null)client.setScreen(parent);}
}