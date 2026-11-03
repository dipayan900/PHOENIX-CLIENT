package net.phoenix.client.branding.mixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.item.Item;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.entity.player.PlayerEntity;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ItemStack.class)
abstract class ContainerPreviewTooltipMixin {
 @Inject(method="getTooltip",at=@At("RETURN"),cancellable=true)
 private void phoenix$addContainerPreview(Item.TooltipContext context,PlayerEntity player,TooltipType type,CallbackInfoReturnable<List<Text>> cir){
  ItemStack stack=(ItemStack)(Object)this;List<Text> lines=new ArrayList<>(cir.getReturnValue());
  if(stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock){
   lines.add(Text.literal("Phoenix Shulker Preview").formatted(Formatting.LIGHT_PURPLE,Formatting.BOLD));
   ContainerComponent contents=stack.get(DataComponentTypes.CONTAINER);
   if(contents==null){lines.add(Text.literal("Empty").formatted(Formatting.GRAY));}
   else{int[] shown={0};int[] total={0};contents.iterateNonEmpty().forEach(item->{total[0]++;if(shown[0]<9){lines.add(Text.literal(item.getCount()+"× "+item.getName().getString()).formatted(Formatting.GRAY));shown[0]++;}});if(total[0]==0)lines.add(Text.literal("Empty").formatted(Formatting.GRAY));else if(total[0]>9)lines.add(Text.literal("and "+(total[0]-9)+" more stacks").formatted(Formatting.DARK_GRAY));}
  }
  if(stack.isOf(Items.ENDER_CHEST)){
   lines.add(Text.literal("Phoenix Ender Chest Preview").formatted(Formatting.AQUA,Formatting.BOLD));
   MinecraftClient client=MinecraftClient.getInstance();
   if(client.player==null){lines.add(Text.literal("Join a world to preview").formatted(Formatting.GRAY));}
   else{var inventory=client.player.getEnderChestInventory();int shown=0,total=0;for(int i=0;i<inventory.size();i++){ItemStack item=inventory.getStack(i);if(!item.isEmpty()){total++;if(shown++<9)lines.add(Text.literal(item.getCount()+"× "+item.getName().getString()).formatted(Formatting.GRAY));}}if(total==0)lines.add(Text.literal("Empty").formatted(Formatting.GRAY));else if(total>9)lines.add(Text.literal("and "+(total-9)+" more stacks").formatted(Formatting.DARK_GRAY));}
  }
  cir.setReturnValue(lines);
 }
}