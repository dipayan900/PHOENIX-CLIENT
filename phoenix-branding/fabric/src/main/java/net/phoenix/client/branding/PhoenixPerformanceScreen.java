package net.phoenix.client.branding;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.text.Text;

/** Device-conscious controls that change real Minecraft graphics options. */
public final class PhoenixPerformanceScreen extends Screen {
 private final Screen parent;
 public PhoenixPerformanceScreen(Screen parent){super(Text.literal("Phoenix Performance"));this.parent=parent;}
 @Override protected void init(){PhoenixScreenStyle.beginTransition();
  int w=Math.min(270,width-24),x=(width-w)/2,y=Math.max(22,height/2-112);
  add("BALANCED · 8 CHUNKS",x,y,w,()->applyBalanced());
  add("PERFORMANCE · 6 CHUNKS",x,y+23,w,()->applyPerformance());
  add("LOW LOAD · 4 CHUNKS",x,y+46,w,()->applyMaximum());
  add("VIEW DISTANCE  −",x,y+74,w/2-2,()->adjustView(-1));
  add("VIEW DISTANCE  +",x+w/2+2,y+74,w/2-2,()->adjustView(1));
  add("SIMULATION  −",x,y+98,w/2-2,()->adjustSimulation(-1));
  add("SIMULATION  +",x+w/2+2,y+98,w/2-2,()->adjustSimulation(1));
  add("ENTITY DETAIL  −",x,y+122,w/2-2,()->adjustEntity(-0.1));
  add("ENTITY DETAIL  +",x+w/2+2,y+122,w/2-2,()->adjustEntity(0.1));
  add("ENTITY SHADOWS: "+(client.options.getEntityShadows().getValue()?"ON":"OFF"),x,y+146,w,()->{client.options.getEntityShadows().setValue(!client.options.getEntityShadows().getValue());refresh();});
  add("CLOUDS: "+client.options.getCloudRenderMode().getValue(),x,y+170,w,()->{client.options.getCloudRenderMode().setValue(client.options.getCloudRenderMode().getValue()==CloudRenderMode.OFF?CloudRenderMode.FAST:CloudRenderMode.OFF);refresh();});
  add("SAVE & RETURN",x,y+194,w,()->{client.options.write();close();});
 }
 private void add(String label,int x,int y,int w,Runnable action){addDrawableChild(ButtonWidget.builder(Text.literal(label),b->action.run()).dimensions(x,y,w,20).build());}
 private void refresh(){client.options.write();client.setScreen(new PhoenixPerformanceScreen(parent));}
 private void adjustView(int delta){var o=client.options;int v=o.getViewDistance().getValue();o.getViewDistance().setValue(Math.max(2,Math.min(32,v+delta)));refresh();}
 private void adjustSimulation(int delta){var o=client.options;int v=o.getSimulationDistance().getValue();o.getSimulationDistance().setValue(Math.max(2,Math.min(32,v+delta)));refresh();}
 private void adjustEntity(double delta){var o=client.options;double v=o.getEntityDistanceScaling().getValue();o.getEntityDistanceScaling().setValue(Math.max(0.5,Math.min(1.0,v+delta)));refresh();}
 private void setParticles(String name){var option=client.options.getParticles();Object current=option.getValue();if(current instanceof Enum<?> value){@SuppressWarnings({"rawtypes","unchecked"}) Enum<?> selected=Enum.valueOf((Class)value.getDeclaringClass(),name);((net.minecraft.client.option.SimpleOption)option).setValue(selected);}}
 private void applyBalanced(){var o=client.options;setParticles("DECREASED");o.getCloudRenderMode().setValue(CloudRenderMode.FAST);o.getEntityShadows().setValue(false);o.getEntityDistanceScaling().setValue(0.8);o.getViewDistance().setValue(Math.min(8,o.getViewDistance().getValue()));o.getSimulationDistance().setValue(Math.min(5,o.getSimulationDistance().getValue()));refresh();}
 private void applyPerformance(){var o=client.options;setParticles("DECREASED");o.getCloudRenderMode().setValue(CloudRenderMode.OFF);o.getEntityShadows().setValue(false);o.getEntityDistanceScaling().setValue(0.7);o.getViewDistance().setValue(Math.min(6,o.getViewDistance().getValue()));o.getSimulationDistance().setValue(Math.min(4,o.getSimulationDistance().getValue()));refresh();}
 private void applyMaximum(){var o=client.options;setParticles("MINIMAL");o.getCloudRenderMode().setValue(CloudRenderMode.OFF);o.getEntityShadows().setValue(false);o.getEntityDistanceScaling().setValue(0.5);o.getViewDistance().setValue(Math.min(4,o.getViewDistance().getValue()));o.getSimulationDistance().setValue(Math.min(3,o.getSimulationDistance().getValue()));o.getBiomeBlendRadius().setValue(0);o.getMipmapLevels().setValue(0);refresh();}
 @Override public void renderBackground(DrawContext d,int mx,int my,float delta){PhoenixScreenStyle.background(d,width,height);}
 @Override public void render(DrawContext d,int mx,int my,float delta){renderBackground(d,mx,my,delta);d.drawCenteredTextWithShadow(textRenderer,Text.literal("PHOENIX  /  PERFORMANCE CONTROL"),width/2,8,PhoenixScreenStyle.accent());d.drawCenteredTextWithShadow(textRenderer,Text.literal("Presets are conservative starting points; tune per world."),width/2,height-10,PhoenixScreenStyle.mutedText());super.render(d,mx,my,delta);PhoenixScreenStyle.renderChrome(this,d);}
 @Override public void close(){if(client!=null)client.setScreen(parent);}
}