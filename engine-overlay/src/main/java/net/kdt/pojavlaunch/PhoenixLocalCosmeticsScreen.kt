package net.kdt.pojavlaunch

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private data class PhoenixLook(val id:String,val name:String,val kind:String,val file:String)
private fun lookRoot(dir:File)=File(dir,"phoenix/cosmetics").canonicalFile
private fun lookList(dir:File):MutableList<PhoenixLook>{
 val f=File(lookRoot(dir),"inventory.json"); if(!f.isFile||f.length()>200000)return mutableListOf()
 return runCatching { val a=JSONArray(f.readText()); MutableList(a.length()){i->val o=a.getJSONObject(i);PhoenixLook(o.getString("id"),o.getString("name"),o.getString("kind"),o.getString("file"))} }.getOrDefault(mutableListOf())
}
private fun saveLooks(dir:File,items:List<PhoenixLook>){
 val root=lookRoot(dir); if(!root.exists()&&!root.mkdirs())throw java.io.IOException("Cannot create Phoenix cosmetics folder.")
 val a=JSONArray();items.forEach{a.put(JSONObject().put("id",it.id).put("name",it.name).put("kind",it.kind).put("file",it.file))}
 val t=File(root,"inventory.tmp");t.writeText(a.toString());val f=File(root,"inventory.json");if(f.exists())f.delete();if(!t.renameTo(f))throw java.io.IOException("Cannot save wardrobe.")
}
private fun equippedId(dir:File,kind:String)=File(File(lookRoot(dir),"equipped"),kind+".id").takeIf{it.isFile}?.readText()?.trim()
private fun equipLook(dir:File,item:PhoenixLook){
 val root=lookRoot(dir);val folder=File(root,if(item.kind=="skin")"skins" else "capes")
 val source=File(folder,item.file).canonicalFile
 if(source.parentFile!=folder.canonicalFile||!source.isFile)throw java.io.IOException("Cosmetic file is missing.")
 val targetDir=File(root,"equipped");if(!targetDir.exists()&&!targetDir.mkdirs())throw java.io.IOException("Cannot create equipped folder.")
 val target=File(targetDir,item.kind+".png");val temp=File(targetDir,item.kind+".tmp");source.copyTo(temp,true);if(target.exists())target.delete();if(!temp.renameTo(target))throw java.io.IOException("Could not equip cosmetic.")
 File(targetDir,item.kind+".id").writeText(item.id)
}
private fun unequipLook(dir:File,kind:String){val d=File(lookRoot(dir),"equipped");File(d,kind+".png").delete();File(d,kind+".id").delete()}
private fun importLook(context:Context,dir:File,uri:Uri,kind:String):PhoenixLook{
 val root=lookRoot(dir)
 val folder=File(root,if(kind=="skin")"skins" else "capes")
 if(!folder.exists()&&!folder.mkdirs())throw java.io.IOException("Cannot create upload folder.")
 val id=UUID.randomUUID().toString()
 val target=File(folder,id+".png")
 val temp=File(folder,id+".tmp")
 try{
  var bytes=0L
  context.contentResolver.openInputStream(uri)?.use{input->
   FileOutputStream(temp).use{out->
    val buf=ByteArray(8192)
    while(true){
     val n=input.read(buf)
     if(n<0)break
     bytes+=n
     if(bytes>2097152)throw java.io.IOException("PNG exceeds 2 MB.")
     out.write(buf,0,n)
    }
    out.fd.sync()
   }
  }?:throw java.io.IOException("Android could not read the selected file. Try choosing it again from Downloads or Files.")
  if(bytes<8)throw java.io.IOException("The selected file is empty or not a valid PNG.")
  val signature=ByteArray(8)
  java.io.FileInputStream(temp).use{if(it.read(signature)!=8)throw java.io.IOException("Could not read PNG header.")}
  val pngSignature=byteArrayOf(0x89.toByte(),0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A)
  if(!signature.contentEquals(pngSignature))throw java.io.IOException("Please select a real PNG file, not JPG or WEBP.")
  val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
  BitmapFactory.decodeFile(temp.absolutePath,bounds)
  val valid=if(kind=="skin")bounds.outWidth==64&&(bounds.outHeight==64||bounds.outHeight==32) else bounds.outWidth==64&&bounds.outHeight==32
  if(!valid)throw java.io.IOException(if(kind=="skin")"Skin PNG must be exactly 64×64 or legacy 64×32 pixels." else "Cape PNG must be exactly 64×32 pixels.")
  if(!temp.renameTo(target))throw java.io.IOException("PNG was read but could not be saved to the Phoenix wardrobe.")
  return PhoenixLook(id,if(kind=="skin")"Custom Skin" else "Custom Cape",kind,target.name)
 }catch(e:Exception){
  temp.delete()
  target.delete()
  throw e
 }
}

@Composable
fun PhoenixLocalCosmeticsScreen(context:Context,gameDir:File,accent:Color,panel:Color,muted:Color,textColor:Color){
 val items=remember(gameDir.absolutePath){mutableStateListOf<PhoenixLook>().apply{addAll(lookList(gameDir))}}
 var kind by remember{mutableStateOf("skin")};var notice by remember{mutableStateOf("Upload PNG files to this Minecraft profile.")}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)runCatching{val item=importLook(context,gameDir,uri,kind);val next=items.toList()+item;saveLooks(gameDir,next);items.clear();items.addAll(next);notice="Saved "+item.name+"."}.onFailure{notice=it.message?:"Upload failed."}}
 Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("MY LOOKS",color=textColor,fontSize=24.sp,fontWeight=FontWeight.Black)
  Text("Custom skins and capes · saved per Minecraft profile",color=muted,fontSize=12.sp)
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
   Button(onClick={kind="skin";picker.launch(arrayOf("image/*"))},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=accent)){Text("＋ UPLOAD SKIN",color=Color.Black)}
   Button(onClick={kind="cape";picker.launch(arrayOf("image/png"))},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=accent)){Text("＋ UPLOAD CAPE",color=Color.Black)}
  }
  listOf("skin" to "SKINS","cape" to "CAPES").forEach{(type,label)->
   Text(label,color=accent,fontWeight=FontWeight.Bold,fontSize=12.sp)
   val list=items.filter{it.kind==type}
   if(list.isEmpty())Text("No uploads yet.",color=muted,fontSize=12.sp)
   list.forEach{item->
    val active=equippedId(gameDir,type)==item.id
    Row(Modifier.fillMaxWidth().background(panel,RoundedCornerShape(14.dp)).padding(10.dp),verticalAlignment=Alignment.CenterVertically){
     Column(Modifier.weight(1f)){Text(item.name,color=textColor,fontWeight=FontWeight.Bold);Text(if(active)"EQUIPPED" else if(type=="skin")"64×64 / legacy 64×32" else "64×32 PNG",color=muted,fontSize=10.sp)}
     TextButton(onClick={runCatching{if(active)unequipLook(gameDir,type) else equipLook(gameDir,item);notice=if(active)"Unequipped." else "Equipped "+item.name+"."}.onFailure{notice=it.message?:"Action failed."}}){Text(if(active)"UNEQUIP" else "EQUIP",color=accent)}
     TextButton(onClick={runCatching{if(active)unequipLook(gameDir,type);File(File(lookRoot(gameDir),if(type=="skin")"skins" else "capes"),item.file).delete();val next=items.filterNot{it.id==item.id};saveLooks(gameDir,next);items.clear();items.addAll(next);notice="Deleted "+item.name+"."}.onFailure{notice=it.message?:"Delete failed."}}){Text("DELETE",color=muted)}
    }
   }
  }
  Text(notice,color=muted,fontSize=11.sp)
  Text("One skin and one cape can be equipped at once. Appearance is local to this Phoenix client; server plugins and account changes are not required.",color=muted,fontSize=11.sp)
 }
}
