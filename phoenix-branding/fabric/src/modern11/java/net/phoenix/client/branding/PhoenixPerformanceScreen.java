package net.phoenix.client.branding;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.text.Text;
/** Applies client-side FPS presets through vanilla Minecraft graphics options. */
public final class PhoenixPerformanceScreen extends Screen {
 private final Screen parent; public PhoenixPerformanceScreen(Screen parent){super(Text.literal("Phoenix Performance"));this.parent=parent;}
 @Override protected void init(){int cx=width/2,y=Math.max(68,height/2-78),w=Math.min(260,width-24);
  addDrawableChild(ButtonWidget.builder(Text.literal("BALANCED PRESET"),b->applyBalanced()).dimensions(cx-w/2,y,w,22).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("PERFORMANCE PRESET"),b->applyPerformance()).dimensions(cx-w/2,y+29,w,22).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("MAXIMUM FPS PRESET"),b->applyMaximum()).dimensions(cx-w/2,y+58,w,22).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("SAVE CURRENT OPTIONS"),b->client.options.write()).dimensions(cx-w/2,y+91,w,22).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("RETURN"),b->close()).dimensions(cx-w/2,y+120,w,22).build());
 }
 private void setParticles(String name){var option=client.options.getParticles();Object current=option.getValue();if(current instanceof Enum<?> value){@SuppressWarnings({"rawtypes","unchecked"}) Enum<?> selected=Enum.valueOf((Class)value.getDeclaringClass(),name);((net.minecraft.client.option.SimpleOption)option).setValue(selected);}}
 private void applyBalanced(){var o=client.options;o.getPreset().setValue(GraphicsMode.FANCY);setParticles("ALL");o.getCloudRenderMode().setValue(CloudRenderMode.FAST);o.getEntityShadows().setValue(true);o.getEntityDistanceScaling().setValue(1.0);o.getViewDistance().setValue(Math.min(12,o.getViewDistance().getValue()));o.getSimulationDistance().setValue(Math.min(8,o.getSimulationDistance().getValue()));o.write();}
 private void applyPerformance(){var o=client.options;o.getPreset().setValue(GraphicsMode.FAST);setParticles("DECREASED");o.getCloudRenderMode().setValue(CloudRenderMode.FAST);o.getEntityShadows().setValue(false);o.getEntityDistanceScaling().setValue(0.75);o.getViewDistance().setValue(Math.min(8,o.getViewDistance().getValue()));o.getSimulationDistance().setValue(Math.min(5,o.getSimulationDistance().getValue()));o.write();}
 private void applyMaximum(){var o=client.options;o.getPreset().setValue(GraphicsMode.FAST);setParticles("MINIMAL");o.getCloudRenderMode().setValue(CloudRenderMode.OFF);o.getEntityShadows().setValue(false);o.getEntityDistanceScaling().setValue(0.5);o.getViewDistance().setValue(Math.min(5,o.getViewDistance().getValue()));o.getSimulationDistance().setValue(Math.min(4,o.getSimulationDistance().getValue()));o.getBiomeBlendRadius().setValue(0);o.getMipmapLevels().setValue(0);o.write();}
 @Override public void renderBackground(DrawContext d,int mx,int my,float delta){d.fillGradient(0,0,width,height,0xFF09070D,0xFF25111B);d.drawCenteredTextWithShadow(textRenderer,Text.literal("PHOENIX  /  PERFORMANCE CONTROL"),width/2,28,0xFFFF8547);}
 @Override public void render(DrawContext d,int mx,int my,float delta){renderBackground(d,mx,my,delta);d.drawCenteredTextWithShadow(textRenderer,Text.literal("Presets adjust Minecraft graphics options locally."),width/2,height-35,0xFFB8AEBB);super.render(d,mx,my,delta);}
 @Override public void close(){if(client!=null)client.setScreen(parent);}
}