package net.kdt.pojavlaunch
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
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
 Cosmetic("storm_pulse","Storm Pulse","Effects","Legendary",900,"⚡","Fantasy 3D","Lightning particle effect"),
 Cosmetic("aurora_cape","Aurora Veil","Capes","Epic",800,"🌈","Fantasy 3D","Iridescent aurora cape"),
 Cosmetic("royal_guard_cape","Royal Guard","Capes","Rare",400,"🛡️","Hybrid","Gold-trimmed royal cape"),
 Cosmetic("shadow_cape","Shadow Mantle","Capes","Epic",700,"🌑","Fantasy 3D","Dark flowing mantle"),
 Cosmetic("ocean_cape","Tidal Cape","Capes","Rare",350,"🌊","Minecraft","Ocean pixel cape"),
 Cosmetic("galaxy_cape","Galaxy Core","Capes","Legendary",1400,"🌌","Fantasy 3D","Starfield cape"),
 Cosmetic("crimson_cape","Crimson Circuit","Capes","Epic",650,"🟥","Hybrid","Red tech cape"),
 Cosmetic("moss_cape","Mossbound","Capes","Common",100,"🌿","Minecraft","Nature pixel cape"),
 Cosmetic("dragon_wings","Dragon Wings","Wings","Legendary",1500,"🐉","Fantasy 3D","Scaled dragon wings"),
 Cosmetic("void_wings","Void Wings","Wings","Epic",850,"🟣","Fantasy 3D","Dark energy wings"),
 Cosmetic("frost_wings","Glacier Wings","Wings","Rare",500,"🧊","Hybrid","Ice crystal wings"),
 Cosmetic("mechanical_wings","Aero Mech","Wings","Epic",900,"⚙️","Hybrid","Mechanical wing rig"),
 Cosmetic("fairy_wings","Pixie Wings","Wings","Rare",450,"🧚","Fantasy 3D","Translucent fairy wings"),
 Cosmetic("bat_wings","Night Bat","Wings","Common",200,"🦇","Minecraft","Pixel bat wings"),
 Cosmetic("samurai_helmet","Samurai Kabuto","Helmets","Epic",700,"⛑️","Hybrid","Samurai helmet"),
 Cosmetic("crown_ice","Frost Crown","Headwear","Rare",350,"❄️","Fantasy 3D","Ice crown"),
 Cosmetic("wizard_hat","Arcane Hat","Headwear","Common",150,"🧙","Minecraft","Wizard hat"),
 Cosmetic("cat_ears","Cat Ears","Headwear","Common",120,"🐱","Hybrid","Cosmetic cat ears"),
 Cosmetic("horns","Demon Horns","Headwear","Rare",300,"😈","Fantasy 3D","Curved horns"),
 Cosmetic("astronaut_helmet","Astronaut Helmet","Helmets","Epic",650,"🚀","Hybrid","Space helmet"),
 Cosmetic("fox_mask","Fox Mask","Masks","Rare",300,"🦊","Fantasy 3D","Fox face mask"),
 Cosmetic("oni_mask","Oni Mask","Masks","Epic",550,"👹","Fantasy 3D","Oni-inspired mask"),
 Cosmetic("pixel_shades","Pixel Shades","Face","Common",100,"🕶️","Minecraft","Pixel sunglasses"),
 Cosmetic("gold_monocle","Golden Monocle","Face","Rare",250,"🧐","Hybrid","Gold monocle"),
 Cosmetic("respirator","Neon Respirator","Masks","Rare",350,"😷","Hybrid","Cyber respirator"),
 Cosmetic("dragon_tail","Dragon Tail","Tails","Epic",500,"🐲","Fantasy 3D","Animated tail concept"),
 Cosmetic("fox_tail","Fox Tail","Tails","Rare",300,"🦊","Fantasy 3D","Fluffy tail concept"),
 Cosmetic("robot_arm","Mech Arm","Arms","Epic",650,"🦾","Hybrid","Mechanical arm overlay"),
 Cosmetic("crystal_bracers","Crystal Bracers","Arms","Rare",300,"💎","Fantasy 3D","Crystal wrist gear"),
 Cosmetic("shoulder_dragon","Pocket Dragon","Shoulder Pets","Legendary",950,"🐉","Fantasy 3D","Perched dragon companion"),
 Cosmetic("shoulder_cat","Pixel Cat","Shoulder Pets","Rare",350,"🐈","Minecraft","Tiny shoulder cat"),
 Cosmetic("shoulder_owl","Moon Owl","Shoulder Pets","Epic",600,"🦉","Fantasy 3D","Floating owl companion"),
 Cosmetic("backpack_jet","Jetpack","Back Items","Epic",750,"🚀","Hybrid","Sci-fi jetpack"),
 Cosmetic("backpack_turtle","Turtle Shell Pack","Back Items","Common",150,"🐢","Minecraft","Turtle-shell backpack"),
 Cosmetic("shield_royal","Royal Shield","Offhand","Rare",400,"🛡️","Hybrid","Decorative shield"),
 Cosmetic("shield_void","Void Guard","Offhand","Epic",650,"🟪","Fantasy 3D","Void energy shield"),
 Cosmetic("sword_sheath","Blade Sheath","Back Items","Rare",350,"⚔️","Hybrid","Cosmetic sword sheath"),
 Cosmetic("halo_solar","Solar Halo","Accessories","Epic",600,"☀️","Fantasy 3D","Sun halo"),
 Cosmetic("halo_lunar","Lunar Halo","Accessories","Rare",350,"🌙","Fantasy 3D","Moon halo"),
 Cosmetic("earrings_star","Star Earrings","Accessories","Common",100,"⭐","Hybrid","Star earrings"),
 Cosmetic("scarf_red","Crimson Scarf","Accessories","Common",120,"🧣","Minecraft","Pixel scarf"),
 Cosmetic("floating_orbs","Orbiting Orbs","Effects","Epic",700,"🔮","Fantasy 3D","Orbiting energy orbs"),
 Cosmetic("snowfall_aura","Snowfall Aura","Effects","Rare",300,"❄️","Fantasy 3D","Soft snow particles"),
 Cosmetic("leaf_aura","Verdant Aura","Effects","Rare",280,"🍃","Hybrid","Leaf particle aura"),
 Cosmetic("electric_aura","Voltage Aura","Effects","Legendary",1000,"⚡","Fantasy 3D","Electric aura"),
 Cosmetic("heart_aura","Heart Spark","Effects","Common",100,"💖","Hybrid","Small heart particles"),
 Cosmetic("flame_footsteps","Flame Steps","Footsteps","Epic",500,"🔥","Fantasy 3D","Flame footsteps"),
 Cosmetic("frost_footsteps","Frost Steps","Footsteps","Rare",300,"❄️","Fantasy 3D","Frost footsteps"),
 Cosmetic("star_trail","Stardust Trail","Trails","Epic",650,"✨","Fantasy 3D","Stardust movement trail"),
 Cosmetic("rainbow_trail","Spectrum Trail","Trails","Rare",400,"🌈","Hybrid","Rainbow movement trail"),
 Cosmetic("shadow_trail","Shadow Trail","Trails","Rare",350,"🌑","Fantasy 3D","Dark movement trail"),
 Cosmetic("spark_burst","Spark Burst","Particles","Common",100,"🎇","Minecraft","Small spark burst"),
 Cosmetic("portal_burst","Portal Arrival","Particles","Epic",750,"🌀","Fantasy 3D","Portal entry effect"),
 Cosmetic("confetti","Confetti Pop","Particles","Common",120,"🎊","Hybrid","Confetti burst"),
 Cosmetic("wave2","Friendly Hello","Emotes","Common",0,"🙋","Minecraft","Alternate hello animation"),
 Cosmetic("clap","Applause","Emotes","Common",100,"👏","Minecraft","Clapping animation"),
 Cosmetic("laugh","Laugh","Emotes","Rare",200,"😂","Hybrid","Laughing animation"),
 Cosmetic("shrug","Shrug","Emotes","Common",100,"🤷","Minecraft","Shrug animation"),
 Cosmetic("bow","Respectful Bow","Emotes","Rare",220,"🙇","Hybrid","Bow animation"),
 Cosmetic("spin","Victory Spin","Emotes","Epic",400,"🌀","Fantasy 3D","Full-body spin animation"),
 Cosmetic("moonwalk","Moonwalk","Emotes","Epic",450,"🕺","Hybrid","Stylized moonwalk"),
 Cosmetic("guitar","Air Guitar","Emotes","Rare",280,"🎸","Hybrid","Air guitar animation"),
 Cosmetic("sit","Sit Down","Emotes","Common",100,"🪑","Minecraft","Seated pose"),
 Cosmetic("meditate","Meditation","Emotes","Epic",420,"🧘","Fantasy 3D","Meditation loop"),
 Cosmetic("dance2","Neon Shuffle","Emotes","Epic",480,"💫","Hybrid","Neon shuffle dance"),
 Cosmetic("pose_star","Star Pose","Emotes","Rare",250,"🌟","Fantasy 3D","Star pose"),
 Cosmetic("title_veteran","Veteran Title","Titles","Rare",250,"🏅","Minecraft","Profile title cosmetic"),
 Cosmetic("title_stargazer","Stargazer Title","Titles","Epic",400,"🌠","Fantasy 3D","Profile title cosmetic"),
 Cosmetic("badge_builder","Master Builder Badge","Badges","Common",100,"🧱","Minecraft","Profile badge"),
 Cosmetic("badge_explorer","Explorer Badge","Badges","Rare",180,"🧭","Hybrid","Profile badge"),
 Cosmetic("gadget_firework","Pocket Firework","Gadgets","Epic",500,"🎆","Fantasy 3D","Cosmetic firework prop"),
 Cosmetic("gadget_camera","Retro Camera","Gadgets","Rare",300,"📷","Hybrid","Handheld camera prop")
)
private val shopCategories=listOf("All","Capes","Wings","Headwear","Helmets","Masks","Face","Accessories","Arms","Back Items","Shoulder Pets","Tails","Offhand","Emotes","Effects","Footsteps","Trails","Particles","Titles","Badges","Gadgets")
private val shopStyles=listOf("All styles","Minecraft","Hybrid","Fantasy 3D")
private val shopPanel=Color(0xFF17171F)
private val shopMuted=Color(0xFFAAA8B2)

@Composable
private fun PhoenixCosmeticArtwork(item: Cosmetic, modifier: Modifier = Modifier) {
 val tint = when {
  item.id.contains("frost") || item.id.contains("ice") || item.id.contains("glacier") || item.id.contains("ocean") -> Color(0xFF68C9F5)
  item.id.contains("void") || item.id.contains("nebula") || item.id.contains("galaxy") || item.id.contains("shadow") -> Color(0xFFAA83F5)
  item.id.contains("royal") || item.id.contains("crown") || item.id.contains("solar") -> Color(0xFFFFC45E)
  item.id.contains("moss") || item.id.contains("leaf") || item.id.contains("verdant") -> Color(0xFF79D49A)
  item.id.contains("crimson") || item.id.contains("samurai") -> Color(0xFFFF6D66)
  else -> Color(0xFFFF8A43)
 }
 Canvas(modifier = modifier) {
  val w=size.width; val h=size.height
  drawRoundRect(Color(0xFF28232F),cornerRadius=CornerRadius(w*0.18f))
  // A small original low-poly player mannequin gives each catalogue entry a real visual preview.
  if(item.category=="Capes" || item.category=="Back Items" || item.category=="Tails") {
   val p=Path().apply { moveTo(w*.30f,h*.34f); lineTo(w*.70f,h*.34f); lineTo(w*.82f,h*.90f); lineTo(w*.18f,h*.90f); close() }
   drawPath(p,tint.copy(alpha=.9f))
   drawRoundRect(tint.copy(alpha=.45f),Offset(w*.22f,h*.42f),androidx.compose.ui.geometry.Size(w*.56f,h*.42f),CornerRadius(w*.06f))
  }
  if(item.category=="Wings") {
   val left=Path().apply { moveTo(w*.42f,h*.42f); lineTo(w*.05f,h*.18f); lineTo(w*.12f,h*.72f); lineTo(w*.42f,h*.64f); close() }
   val right=Path().apply { moveTo(w*.58f,h*.42f); lineTo(w*.95f,h*.18f); lineTo(w*.88f,h*.72f); lineTo(w*.58f,h*.64f); close() }
   drawPath(left,tint); drawPath(right,tint)
   drawLine(Color.White.copy(alpha=.55f),Offset(w*.12f,h*.28f),Offset(w*.39f,h*.55f),w*.035f)
   drawLine(Color.White.copy(alpha=.55f),Offset(w*.88f,h*.28f),Offset(w*.61f,h*.55f),w*.035f)
  }
  // Head, torso, arms and legs.
  drawRoundRect(Color(0xFFEDC49B),Offset(w*.36f,h*.12f),androidx.compose.ui.geometry.Size(w*.28f,h*.25f),CornerRadius(w*.05f))
  drawRoundRect(tint,Offset(w*.29f,h*.39f),androidx.compose.ui.geometry.Size(w*.42f,h*.33f),CornerRadius(w*.06f))
  drawRoundRect(Color(0xFFB9A7A0),Offset(w*.30f,h*.69f),androidx.compose.ui.geometry.Size(w*.16f,h*.23f),CornerRadius(w*.04f))
  drawRoundRect(Color(0xFFB9A7A0),Offset(w*.54f,h*.69f),androidx.compose.ui.geometry.Size(w*.16f,h*.23f),CornerRadius(w*.04f))
  when(item.category) {
   "Headwear","Helmets","Masks","Face","Accessories" -> {
    if(item.id.contains("halo")) drawCircle(tint, w*.25f, Offset(w*.5f,h*.12f),style=androidx.compose.ui.graphics.drawscope.Stroke(width=w*.055f))
    else drawRoundRect(tint,Offset(w*.29f,h*.07f),androidx.compose.ui.geometry.Size(w*.42f,h*.12f),CornerRadius(w*.06f))
   }
   "Emotes" -> {
    drawCircle(Color.White, w*.045f, Offset(w*.80f,h*.22f))
    drawCircle(tint, w*.065f, Offset(w*.86f,h*.37f))
    drawLine(tint,Offset(w*.74f,h*.52f),Offset(w*.92f,h*.38f),w*.045f)
   }
   "Effects","Footsteps","Trails","Particles" -> {
    drawCircle(tint.copy(alpha=.75f),w*.09f,Offset(w*.18f,h*.22f))
    drawCircle(Color.White.copy(alpha=.8f),w*.045f,Offset(w*.82f,h*.18f))
    drawCircle(tint.copy(alpha=.55f),w*.07f,Offset(w*.83f,h*.76f))
   }
  }
  drawRoundRect(Color.White.copy(alpha=.16f),Offset(w*.08f,h*.06f),androidx.compose.ui.geometry.Size(w*.84f,h*.025f),CornerRadius(w))
 }
}

@Composable
fun PhoenixCosmeticsScreen(context:Context,accent:Color,onNotice:(String)->Unit){
 val prefs=remember{context.getSharedPreferences("phoenix_cosmetics",Context.MODE_PRIVATE)}
 var coins by remember{mutableIntStateOf(prefs.getInt("demo_coins",500))}
 val today=remember{java.time.LocalDate.now().toEpochDay()}
 var lastDailyReward by remember{mutableLongStateOf(prefs.getLong("last_daily_reward_day",-1L))}
 val owned=remember{mutableStateListOf<String>().apply{addAll(prefs.getStringSet("owned",setOf("ember_cape","wave")).orEmpty())}}
 val equipped=remember{mutableStateMapOf<String,String>().apply{
  prefs.getString("equipped","").orEmpty().split("|").filter{it.contains("=")}.forEach{val p=it.split("=",limit=2);this[p[0]]=p[1]}
 }}
 var category by remember{mutableStateOf("All")}
 var style by remember{mutableStateOf("All styles")}
 var selected by remember{mutableStateOf("ember_cape")}
 var wallet by remember{mutableStateOf(false)}
 var info by remember{mutableStateOf(false)}
 fun save(){prefs.edit().putInt("demo_coins",coins).putLong("last_daily_reward_day",lastDailyReward).putStringSet("owned",owned.toSet()).putString("equipped",equipped.entries.joinToString("|"){"${it.key}=${it.value}"}).apply()}
 val preview=phoenixItems.firstOrNull{it.id==selected}?:phoenixItems.first()
 val visible=phoenixItems.filter{(category=="All"||it.category==category)&&(style=="All styles"||it.style==style)}
 Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
  Column(verticalArrangement=Arrangement.spacedBy(4.dp)){Text("PHOENIX COSMETICS",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Black);Text("Build your look. Make your entrance.",color=shopMuted,fontSize=12.sp)}
  Row(Modifier.fillMaxWidth().background(shopPanel,RoundedCornerShape(17.dp)).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
   Column{Text("PHOENIX COINS",color=accent,fontSize=10.sp,fontWeight=FontWeight.Bold);Text("🪙 $coins",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Black);Text("Local development wallet",color=shopMuted,fontSize=10.sp)}
   Button(onClick={wallet=true},colors=ButtonDefaults.buttonColors(containerColor=accent)){Text("COIN STORE",color=Color.Black,fontWeight=FontWeight.Bold,fontSize=10.sp)}
  }
  Row(Modifier.fillMaxWidth().background(shopPanel,RoundedCornerShape(14.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
   Column(Modifier.weight(1f)){Text("DAILY FREE COINS",color=accent,fontSize=10.sp,fontWeight=FontWeight.Bold);Text("Claim 100 demo coins once per day",color=Color.White,fontSize=12.sp);Text("Local test balance only · no payment",color=shopMuted,fontSize=9.sp)}
   Button(onClick={if(lastDailyReward!=today){coins+=100;lastDailyReward=today;save();onNotice("100 free demo coins added to your local test wallet.")}},enabled=lastDailyReward!=today,colors=ButtonDefaults.buttonColors(containerColor=accent,disabledContainerColor=Color(0xFF39313D))){Text(if(lastDailyReward==today)"CLAIMED" else "CLAIM",color=if(lastDailyReward==today)shopMuted else Color.Black,fontSize=10.sp,fontWeight=FontWeight.Bold)}
  }
  Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF46231B),Color(0xFF211923),shopPanel)),RoundedCornerShape(17.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
   Text("PHOENIX OWNER COLLECTION",color=accent,fontSize=10.sp,fontWeight=FontWeight.Bold)
   Text("Unlimited cosmetics for the creator account",color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Bold)
   Text("Owner access is checked by the compatible Paper server against its configured Minecraft UUID. The launcher wallet and local preferences cannot grant owner privileges.",color=shopMuted,fontSize=11.sp)
  }
  Text("STYLE",color=shopMuted,fontSize=10.sp,fontWeight=FontWeight.Bold)
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){shopStyles.forEach{FilterChip(selected=style==it,onClick={style=it},label={Text(it,fontSize=9.sp)})}}
  Text("CATEGORY",color=shopMuted,fontSize=10.sp,fontWeight=FontWeight.Bold)
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){shopCategories.forEach{FilterChip(selected=category==it,onClick={category=it},label={Text(it,fontSize=9.sp)})}}
  Column(Modifier.fillMaxWidth().background(shopPanel,RoundedCornerShape(17.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
   Text("COSMETIC PREVIEW",color=accent,fontSize=10.sp,fontWeight=FontWeight.Bold)
   Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
    PhoenixCharacterPreview(preview.id,Modifier.fillMaxWidth().height(250.dp))
    Text("DRAG TO ROTATE · OPENGL 3D PREVIEW",color=accent,fontSize=9.sp,fontWeight=FontWeight.Bold)
   }
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
    PhoenixCosmeticArtwork(preview,Modifier.size(58.dp))
    Column(Modifier.weight(1f)){Text(preview.name,color=Color.White,fontWeight=FontWeight.Bold);Text("${preview.category} · ${preview.style}",color=shopMuted,fontSize=10.sp);Text(preview.detail,color=shopMuted,fontSize=11.sp)}
   }
   Text("Drag the 3D player to rotate. The preview uses original procedural meshes; supported cosmetics are rendered by the bundled Fabric add-on in Minecraft Java 1.21.1.",color=shopMuted,fontSize=10.sp)
  }
  Text("CATALOGUE · ${visible.size} ITEMS",color=Color.White,fontWeight=FontWeight.Bold,fontSize=13.sp)
  visible.forEach{item->
   val has=owned.contains(item.id);val active=equipped[item.category]==item.id
   Row(Modifier.fillMaxWidth().background(shopPanel,RoundedCornerShape(14.dp)).clickable{selected=item.id}.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
    PhoenixCosmeticArtwork(item,Modifier.size(45.dp))
    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){Text(item.name,color=Color.White,fontSize=12.sp,fontWeight=FontWeight.SemiBold);Text("${item.rarity} · ${item.style}",color=shopMuted,fontSize=9.sp);Text(if(active)"EQUIPPED" else if(has)"OWNED" else if(item.price==0)"FREE" else "🪙 ${item.price}",color=accent,fontSize=9.sp,fontWeight=FontWeight.Bold)}
    if(has) OutlinedButton(onClick={equipped[item.category]=item.id;save();onNotice("${item.name} saved to the local wardrobe. Equip it on a compatible server with /phoenix equip ${item.id}.")},contentPadding=PaddingValues(horizontal=7.dp,vertical=3.dp)){Text(if(active)"ON" else "EQUIP",fontSize=9.sp)}
    else Button(onClick={if(coins>=item.price){coins-=item.price;owned.add(item.id);save();onNotice("${item.name} added to local inventory.")}else onNotice("Not enough demo coins. Real-money checkout is disabled.")},contentPadding=PaddingValues(horizontal=7.dp,vertical=3.dp),colors=ButtonDefaults.buttonColors(containerColor=accent)){Text(if(item.price==0)"CLAIM" else "UNLOCK",color=Color.Black,fontSize=9.sp,fontWeight=FontWeight.Bold)}
   }
  }
  TextButton(onClick={info=true}){Text("Compatibility and account information",color=accent)}
 }
 if(wallet)AlertDialog(onDismissRequest={wallet=false},containerColor=shopPanel,title={Text("Phoenix Coin Store",color=Color.White)},text={
  Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text("COIN BUNDLES · PREVIEW",color=shopMuted,fontSize=11.sp);listOf("Starter · 100 coins","Explorer · 300 coins","Advanced · 750 coins","Elite · 1,500 coins","Legendary · 3,500 coins","Ultimate · 7,500 coins").forEach{Row(Modifier.fillMaxWidth().background(Color(0xFF28232D),RoundedCornerShape(9.dp)).padding(9.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(it,color=Color.White,fontSize=12.sp);Text("COMING LATER",color=accent,fontSize=9.sp,fontWeight=FontWeight.Bold)}};Text("Real-money checkout is intentionally disabled. This wallet uses local demo coins only; clearing app data can reset it.",color=shopMuted,fontSize=10.sp)}
 },confirmButton={TextButton(onClick={wallet=false}){Text("CLOSE",color=accent)}})
 if(info)AlertDialog(onDismissRequest={info=false},containerColor=shopPanel,title={Text("Cosmetic compatibility",color=Color.White)},text={Text("Phoenix uses original illustrated previews in this store and a first-pass procedural renderer in its bundled Fabric 1.21.1 add-on. Compatible players see supported attachments when connected to a Paper server running the Phoenix plugin. Some catalogue concepts are not rendered yet. The launcher wallet remains local demo data; cross-server cloud inventory is not deployed and real-money checkout is disabled.",color=shopMuted,fontSize=11.sp)},confirmButton={TextButton(onClick={info=false}){Text("GOT IT",color=accent)}})
}

/**
 * Runtime integration contracts. The current Amethyst engine checkout does not
 * expose these hooks yet; the launcher must treat a missing provider as
 * unsupported rather than pretending cosmetics are rendered in-game.
 */
data class PhoenixCosmeticAssetLicense(
    val assetId: String,
    val sourceUrl: String,
    val licenseSpdx: String,
    val attribution: String,
    val assetSha256: String,
    val redistributionAllowed: Boolean
)

data class PhoenixCosmeticLoadout(
    val playerUuid: String,
    val capeId: String? = null,
    val wingId: String? = null,
    val headId: String? = null,
    val faceId: String? = null,
    val backId: String? = null,
    val effectIds: List<String> = emptyList()
)

data class PhoenixCosmeticEmote(
    val id: String,
    val animationAssetId: String,
    val durationMillis: Long,
    val loop: Boolean = false
)

interface PhoenixCosmeticRenderer {
    fun isAvailable(): Boolean
    fun showPlayerPreview(loadout: PhoenixCosmeticLoadout): Boolean
    fun applyLoadout(loadout: PhoenixCosmeticLoadout): Boolean
    fun playEmote(playerUuid: String, emote: PhoenixCosmeticEmote): Boolean
    fun applyRemoteLoadout(loadout: PhoenixCosmeticLoadout): Boolean
}

data class PhoenixCloudCosmeticInventory(
    val revision: Long,
    val coinBalance: Long,
    val ownedCosmeticIds: Set<String>,
    val equippedLoadout: PhoenixCosmeticLoadout
)

data class PhoenixVerifiedOwnerEntitlement(
    val accountSubject: String,
    val expiresAtEpochSeconds: Long,
    val signedEntitlement: String
)

interface PhoenixCosmeticCloudApi {
    suspend fun fetchInventory(accessToken: String): PhoenixCloudCosmeticInventory
    suspend fun syncLoadout(accessToken: String, loadout: PhoenixCosmeticLoadout): PhoenixCloudCosmeticInventory
    suspend fun verifyOwnerEntitlement(accessToken: String): PhoenixVerifiedOwnerEntitlement?
}
