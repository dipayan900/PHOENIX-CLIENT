package com.phoenix.client
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Cosmetic(val id:String,val name:String,val category:String,val rarity:String,val price:Int,val icon:String,val style:String,val detail:String)
private val phoenixItems=listOf(
 Cosmetic("ember_cape","Ember Cape","Capes","Common",0,"🔥","Minecraft","Starter flame cape"),
 Cosmetic("frost_cape","Frostline","Capes","Rare",250,"❄️","Minecraft","Icy blue cape"),
 Cosmetic("nebula_cape","Nebula Drift","Capes","Epic",650,"🌌","Fantasy 3D","Cosmic animated cape concept"),
 Cosmetic("royal_cape","Royal Phoenix","Capes","Legendary",1200,"👑","Hybrid","Phoenix insignia cape"),
 Cosmetic("celestial_wings","Celestial Wings","Wings","Epic",700,"🪽","Fantasy 3D","Feathered back wings"),
 Cosmetic("inferno_wings","Inferno Wings","Wings","Legendary",1100,"🔥","Hybrid","Glowing ember wings"),
 Cosmetic("pixel_glider","Pixel Glider","Wings","Rare",350,"🪶","Minecraft","Block-built wings"),
 Cosmetic("phoenix_crown","Phoenix Crown","Accessories","Epic",500,"👑","Hybrid","Phoenix head accessory"),
 Cosmetic("starlight_halo","Starlight Halo","Accessories","Rare",300,"✨","Fantasy 3D","Floating halo"),
 Cosmetic("cyber_visor","Cyber Visor","Accessories","Common",100,"🥽","Minecraft","Futuristic face accessory"),
 Cosmetic("explorer_pack","Explorer Pack","Accessories","Common",150,"🎒","Minecraft","Player backpack"),
 Cosmetic("wave","Wave","Emotes","Common",0,"👋","Minecraft","Friendly wave animation"),
 Cosmetic("victory_pose","Victory Pose","Emotes","Rare",250,"🏆","Hybrid","Victory animation"),
 Cosmetic("pixel_dance","Pixel Dance","Emotes","Epic",450,"💃","Minecraft","Looping dance animation"),
 Cosmetic("salute","Salute","Emotes","Common",100,"🫡","Minecraft","Quick salute"),
 Cosmetic("ember_aura","Ember Aura","Effects","Rare",300,"🔥","Hybrid","Ember particle aura"),
 Cosmetic("void_trail","Void Trail","Effects","Epic",550,"💜","Fantasy 3D","Purple movement trail"),
 Cosmetic("storm_pulse","Storm Pulse","Effects","Legendary",900,"⚡","Fantasy 3D","Lightning particle effect")
)
private val shopCategories=listOf("All","Capes","Wings","Accessories","Emotes","Effects")
private val shopStyles=listOf("All styles","Minecraft","Hybrid","Fantasy 3D")
private val shopPanel=Color(0xFF17171F)
private val shopMuted=Color(0xFFAAA8B2)

@Composable
fun CosmeticsScreen(context:Context,accent:Color,onNotice:(String)->Unit){
 val prefs=remember{context.getSharedPreferences("phoenix_cosmetics",Context.MODE_PRIVATE)}
 var coins by remember{mutableIntStateOf(prefs.getInt("demo_coins",500))}
 val owned=remember{mutableStateListOf<String>().apply{addAll(prefs.getStringSet("owned",setOf("ember_cape","wave")).orEmpty())}}
 val equipped=remember{mutableStateMapOf<String,String>().apply{
  prefs.getString("equipped","").orEmpty().split("|").filter{it.contains("=")}.forEach{val p=it.split("=",limit=2);this[p[0]]=p[1]}
 }}
 var category by remember{mutableStateOf("All")}
 var style by remember{mutableStateOf("All styles")}
 var selected by remember{mutableStateOf("ember_cape")}
 var wallet by remember{mutableStateOf(false)}
 var info by remember{mutableStateOf(false)}
 fun save(){prefs.edit().putInt("demo_coins",coins).putStringSet("owned",owned.toSet()).putString("equipped",equipped.entries.joinToString("|"){"${it.key}=${it.value}"}).apply()}
 val preview=phoenixItems.firstOrNull{it.id==selected}?:phoenixItems.first()
 val visible=phoenixItems.filter{(category=="All"||it.category==category)&&(style=="All styles"||it.style==style)}
 Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
  Column(verticalArrangement=Arrangement.spacedBy(4.dp)){Text("PHOENIX COSMETICS",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Black);Text("Build your look. Make your entrance.",color=shopMuted,fontSize=12.sp)}
  Row(Modifier.fillMaxWidth().background(shopPanel,RoundedCornerShape(17.dp)).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
   Column{Text("PHOENIX COINS",color=accent,fontSize=10.sp,fontWeight=FontWeight.Bold);Text("🪙 $coins",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Black);Text("Local development wallet",color=shopMuted,fontSize=10.sp)}
   Button(onClick={wallet=true},colors=ButtonDefaults.buttonColors(containerColor=accent)){Text("COIN STORE",color=Color.Black,fontWeight=FontWeight.Bold,fontSize=10.sp)}
  }
  Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF46231B),Color(0xFF211923),shopPanel)),RoundedCornerShape(17.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
   Text("PHOENIX OWNER COLLECTION",color=accent,fontSize=10.sp,fontWeight=FontWeight.Bold)
   Text("Unlimited cosmetics for the creator account",color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Bold)
   Text("Reserved for a server-verified owner account. Secure sign-in and signed owner entitlements are not implemented yet; local app data cannot grant owner privileges.",color=shopMuted,fontSize=11.sp)
  }
  Text("STYLE",color=shopMuted,fontSize=10.sp,fontWeight=FontWeight.Bold)
  Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){shopStyles.forEach{FilterChip(selected=style==it,onClick={style=it},label={Text(it,fontSize=9.sp)})}}
  Text("CATEGORY",color=shopMuted,fontSize=10.sp,fontWeight=FontWeight.Bold)
  Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){shopCategories.forEach{FilterChip(selected=category==it,onClick={category=it},label={Text(it,fontSize=9.sp)})}}
  Column(Modifier.fillMaxWidth().background(shopPanel,RoundedCornerShape(17.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
   Text("COSMETIC PREVIEW",color=accent,fontSize=10.sp,fontWeight=FontWeight.Bold)
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
    Box(Modifier.size(70.dp).background(Color(0xFF28232D),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){Text(preview.icon,fontSize=34.sp)}
    Column(Modifier.weight(1f)){Text(preview.name,color=Color.White,fontWeight=FontWeight.Bold);Text("${preview.category} · ${preview.style}",color=shopMuted,fontSize=10.sp);Text(preview.detail,color=shopMuted,fontSize=11.sp)}
   }
   Text("Catalogue preview only. 3D player rendering and live emotes require the integrated Minecraft renderer.",color=shopMuted,fontSize=10.sp)
  }
  Text("CATALOGUE · ${visible.size} ITEMS",color=Color.White,fontWeight=FontWeight.Bold,fontSize=13.sp)
  visible.forEach{item->
   val has=owned.contains(item.id);val active=equipped[item.category]==item.id
   Row(Modifier.fillMaxWidth().background(shopPanel,RoundedCornerShape(14.dp)).clickable{selected=item.id}.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
    Box(Modifier.size(45.dp).background(Color(0xFF28232D),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Text(item.icon,fontSize=23.sp)}
    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){Text(item.name,color=Color.White,fontSize=12.sp,fontWeight=FontWeight.SemiBold);Text("${item.rarity} · ${item.style}",color=shopMuted,fontSize=9.sp);Text(if(active)"EQUIPPED" else if(has)"OWNED" else if(item.price==0)"FREE" else "🪙 ${item.price}",color=accent,fontSize=9.sp,fontWeight=FontWeight.Bold)}
    if(has) OutlinedButton(onClick={equipped[item.category]=item.id;save();onNotice("${item.name} saved to local wardrobe; in-game rendering is pending.")},contentPadding=PaddingValues(horizontal=7.dp,vertical=3.dp)){Text(if(active)"ON" else "EQUIP",fontSize=9.sp)}
    else Button(onClick={if(coins>=item.price){coins-=item.price;owned.add(item.id);save();onNotice("${item.name} added to local inventory.")}else onNotice("Not enough demo coins. Real-money checkout is disabled.")},contentPadding=PaddingValues(horizontal=7.dp,vertical=3.dp),colors=ButtonDefaults.buttonColors(containerColor=accent)){Text(if(item.price==0)"CLAIM" else "UNLOCK",color=Color.Black,fontSize=9.sp,fontWeight=FontWeight.Bold)}
   }
  }
  TextButton(onClick={info=true}){Text("Compatibility and account information",color=accent)}
 }
 if(wallet)AlertDialog(onDismissRequest={wallet=false},containerColor=shopPanel,title={Text("Phoenix Coin Store",color=Color.White)},text={
  Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Planned coin bundles",color=shopMuted,fontSize=11.sp);listOf("Starter · 100 coins","Explorer · 300 coins","Advanced · 750 coins","Elite · 1,500 coins","Legendary · 3,500 coins","Ultimate · 7,500 coins").forEach{Text(it,color=Color.White,fontSize=13.sp)};Text("Payment checkout is disabled. These are catalogue placeholders; no money is collected.",color=shopMuted,fontSize=10.sp)}
 },confirmButton={TextButton(onClick={wallet=false}){Text("CLOSE",color=accent)}})
 if(info)AlertDialog(onDismissRequest={info=false},containerColor=shopPanel,title={Text("Cosmetic compatibility",color=Color.White)},text={Text("This launcher foundation contains a local catalogue, filters, preview selection, inventory, equip slots and demo coin wallet. Actual capes, wings, accessories, emotes and particles need a runtime renderer. Other players see cosmetics only when their client or server supports the same protocol. Server-backed owner entitlements, cloud inventory and payment processing remain future milestones.",color=shopMuted,fontSize=11.sp)},confirmButton={TextButton(onClick={info=false}){Text("GOT IT",color=accent)}})
}
