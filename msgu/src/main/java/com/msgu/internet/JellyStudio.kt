package com.msgu.internet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Reaction(val id: Int, val label: String, val value: String, val tags: String, val color: Color)
private val reactions = listOf(
    Reaction(1,"Happy","😄","happy खुश मुस्कान",Color(0xFF3DC8EC)),
    Reaction(2,"Laugh","😂","laugh हंसी",Color(0xFFFFBB4F)),
    Reaction(3,"Love","😍","love प्यार",Color(0xFFFF77B2)),
    Reaction(4,"Wow","😮","wow आश्चर्य",Color(0xFF689EFC)),
    Reaction(5,"Shy","😊","shy शर्म",Color(0xFFBB82ED)),
    Reaction(6,"Cool","😎","cool स्टाइल",Color(0xFF9C73ED)),
    Reaction(7,"Wink","😉","wink आँख",Color(0xFF5ACFDC)),
    Reaction(8,"Sad","😢","sad दुख उदास",Color(0xFF639DF7)),
    Reaction(9,"Angry","😠","angry गुस्सा",Color(0xFFFF8C84)),
    Reaction(10,"Sleepy","🥱","sleep नींद",Color(0xFFF9BC5E)),
    Reaction(11,"Thinking","🤔","think सोच",Color(0xFFF4A6C7)),
    Reaction(12,"Hug","🤗","hug गले",Color(0xFFFFA86D)),
    Reaction(13,"Hello","👋","hi hello नमस्ते",Color(0xFF81D6AD)),
    Reaction(14,"Thanks","🙏","thanks धन्यवाद",Color(0xFF88B6FD)),
    Reaction(15,"Namaste","🙏","namaste प्रणाम",Color(0xFFB699F2)),
    Reaction(16,"Party","🥳","party जश्न जन्मदिन",Color(0xFF8DC0F8)),
    Reaction(17,"Good Luck","👍","luck शुभकामना",Color(0xFFFFC251)),
    Reaction(18,"Friends","🥰","friend दोस्त",Color(0xFFF690B9)),
    Reaction(19,"Excited","🤩","excited उत्साह",Color(0xFF48C6D9)),
    Reaction(20,"Bye","👋","bye अलविदा",Color(0xFFFFA1A7))
)

@Composable private fun ReactionTile(reaction: Reaction, modifier: Modifier = Modifier, big: Boolean = false) {
    val size=if(big) 118.dp else 65.dp
    val textSize=if(big) 59.sp else 32.sp
    Column(modifier, horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.size(size).clip(RoundedCornerShape(if(big)46.dp else 26.dp))
            .background(Brush.verticalGradient(listOf(
                reaction.color.copy(alpha=0.55f),reaction.color,
                reaction.color.copy(alpha=0.85f)))),
            contentAlignment=Alignment.Center) {
            Box(Modifier.align(Alignment.TopCenter).padding(top=7.dp)
                .fillMaxWidth(.56f).height(if(big)13.dp else 7.dp)
                .clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha=.46f)))
            Text(reaction.value,fontSize=textSize)
        }
        Spacer(Modifier.height(6.dp))
        Text(reaction.label,fontSize=11.sp,fontWeight=FontWeight.Medium,
            maxLines=1,textAlign=TextAlign.Center)
    }
}

@Composable
fun JellyStudio(modifier: Modifier = Modifier) {
    var search by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var chosen by remember { mutableStateOf<Reaction?>(null) }
    var favs by remember { mutableStateOf(setOf<Int>()) }
    val filtered = reactions.filter {
        (tab==0 || tab==1 && it.id in favs) &&
        (search.isBlank() || it.label.contains(search,ignoreCase=true) || it.tags.contains(search,ignoreCase=true))
    }
    Column(modifier) {
        Text("💎 MsgU Jelly Studio",style=MaterialTheme.typography.titleLarge,
            fontWeight=FontWeight.ExtraBold,color=MaterialTheme.colorScheme.primary)
        Text("20 colourful offline reaction previews • tap to inspect",
            style=MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(search,{search=it.take(60)},Modifier.fillMaxWidth(),singleLine=true,
            label={Text("Search · खुश, love, नमस्ते...")})
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            FilterChip(selected=tab==0,onClick={tab=0},label={Text("All")})
            FilterChip(selected=tab==1,onClick={tab=1},label={Text("★ Favourites")})
        }
        if(filtered.isEmpty()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
            Text("No matching reactions")
        } else LazyVerticalGrid(columns=GridCells.Adaptive(100.dp),
            contentPadding=PaddingValues(7.dp),modifier=Modifier.fillMaxSize(),
            horizontalArrangement=Arrangement.spacedBy(8.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(filtered,key={it.id}) { reaction ->
                ElevatedCard(onClick={chosen=reaction}) {
                    ReactionTile(reaction,Modifier.fillMaxWidth().padding(8.dp))
                }
            }
        }
    }
    chosen?.let { reaction ->
        AlertDialog(onDismissRequest={chosen=null},
            title={Text("MsgU " + reaction.label)},
            text={Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
                ReactionTile(reaction,big=true)
                Spacer(Modifier.height(10.dp))
                Text("This is a colourful Unicode reaction preview, not a custom image sticker.",
                    style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center)
            }},
            confirmButton={TextButton(onClick={chosen=null}){Text("Done")}},
            dismissButton={TextButton(onClick={
                favs=if(reaction.id in favs)favs-reaction.id else favs+reaction.id
                chosen=null
            }) {Text(if(reaction.id in favs)"★ Remove favourite" else "☆ Favourite")}}
        )
    }
}

@Composable
fun EmojiQuickPicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("Choose an emoji")},
        text={Column(Modifier.fillMaxWidth().heightIn(max=350.dp)) {
            LazyVerticalGrid(columns=GridCells.Fixed(5),
                horizontalArrangement=Arrangement.spacedBy(5.dp),
                verticalArrangement=Arrangement.spacedBy(5.dp)) {
                items(reactions,key={it.id}) { r ->
                    Surface(onClick={onPick(r.value)},shape=RoundedCornerShape(12.dp),
                        color=MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) {
                            Text(r.value,fontSize=23.sp)
                        }
                    }
                }
            }
        }},confirmButton={TextButton(onClick=onDismiss){Text("Close")}}
    )
}
