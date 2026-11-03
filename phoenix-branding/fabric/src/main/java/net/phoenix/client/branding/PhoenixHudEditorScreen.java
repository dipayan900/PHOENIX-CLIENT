package net.phoenix.client.branding;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Drag the live Phoenix HUD panel to position it on screen. */
public final class PhoenixHudEditorScreen extends Screen {
    private final Screen parent;
    private boolean dragging;
    private double dragX, dragY;
    private static final int PANEL_W=230, PANEL_H=112;

    public PhoenixHudEditorScreen(Screen parent){super(Text.literal("Phoenix HUD Editor"));this.parent=parent;}

    @Override protected void init(){
        int y=height-34;
        addDrawableChild(ButtonWidget.builder(Text.literal("RESET POSITION"),b->{PhoenixHud.setHudPosition(10,10);})
            .dimensions(width/2-154,y,100,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("SAVE & RETURN"),b->{PhoenixHud.saveConfig();close();})
            .dimensions(width/2-49,y,100,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("CANCEL"),b->close())
            .dimensions(width/2+56,y,98,20).build());
    }

    @Override public void render(DrawContext d,int mx,int my,float delta){
        d.fillGradient(0,0,width,height,0xF509070D,0xF525111B);
        d.drawCenteredTextWithShadow(textRenderer,title,width/2,18,0xFFFF8547);
        d.drawCenteredTextWithShadow(textRenderer,Text.literal("DRAG THE PHOENIX HUD PANEL TO MOVE IT"),width/2,34,0xFFB8AEBB);
        int x=PhoenixHud.getHudX(),y=PhoenixHud.getHudY();
        int h=Math.min(PANEL_H,height-y-50);
        d.fill(x,y,x+PANEL_W,y+Math.max(70,h),0xD5100D14);
        d.fill(x,y,x+3,y+Math.max(70,h),0xFFFF702E);
        d.drawTextWithShadow(textRenderer,Text.literal("PHOENIX  /  HUD PREVIEW"),x+10,y+8,0xFFFF8A4C);
        d.drawTextWithShadow(textRenderer,Text.literal("FPS       144"),x+10,y+28,0xFFF4EDF4);
        d.drawTextWithShadow(textRenderer,Text.literal("XYZ       120  64  -32"),x+10,y+42,0xFFF4EDF4);
        d.drawTextWithShadow(textRenderer,Text.literal("FACING    NORTH"),x+10,y+56,0xFFF4EDF4);
        d.drawTextWithShadow(textRenderer,Text.literal("PING      42 ms"),x+10,y+70,0xFFF4EDF4);
        d.drawCenteredTextWithShadow(textRenderer,Text.literal("Position: "+x+", "+y),width/2,height-46,0xFFB8AEBB);
        super.render(d,mx,my,delta);
    }

    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){
        if(button==0){int x=PhoenixHud.getHudX(),y=PhoenixHud.getHudY();if(mouseX>=x&&mouseX<=x+PANEL_W&&mouseY>=y&&mouseY<=y+PANEL_H){dragging=true;dragX=mouseX-x;dragY=mouseY-y;return true;}}
        return super.mouseClicked(mouseX,mouseY,button);
    }
    @Override public boolean mouseDragged(double mouseX,double mouseY,int button,double deltaX,double deltaY){
        if(dragging&&button==0){PhoenixHud.setHudPosition((int)(mouseX-dragX),(int)(mouseY-dragY));return true;}
        return super.mouseDragged(mouseX,mouseY,button,deltaX,deltaY);
    }
    @Override public boolean mouseReleased(double mouseX,double mouseY,int button){
        if(button==0&&dragging){dragging=false;PhoenixHud.saveConfig();return true;}
        return super.mouseReleased(mouseX,my,button);
    }
    @Override public void close(){if(client!=null)client.setScreen(parent);}
}
